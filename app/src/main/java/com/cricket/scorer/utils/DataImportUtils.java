package com.cricket.scorer.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DataImportUtils.java
 *
 * Companion to {@link DataExportUtils}. Reads a previously-exported backup
 * zip (chosen by the user via the system file picker) and writes its JSON
 * entries back into the app's private files directory.
 *
 * The backup zip is plain (NOT encrypted) and mirrors the on-device layout:
 *   live_match.json
 *   tournament_tracker.json
 *   recent_matches/<file>.json
 *   recent_tournaments/<file>.json
 *   recent_tournaments/matches/<file>.json
 *   logs/app_log.txt              ← skipped on restore (diagnostic only)
 *   logs/app_log.1.txt            ← skipped on restore (diagnostic only)
 *
 * Restore policy:
 *   - JSON files are written into the equivalent app-private path.
 *   - Existing files with the same name are overwritten.
 *   - Files NOT in the backup remain untouched (merge semantics).
 *   - Non-JSON entries are skipped (defence against tampered zips).
 *   - Zip-slip is prevented: entries with absolute paths or ".." are rejected.
 *   - Files larger than {@link #MAX_FILE_BYTES} are skipped.
 */
public class DataImportUtils {

    /** Safety cap on individual entry size to defend against malicious zips. */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB / file

    public static class Result {
        public final boolean success;
        public final int     filesRestored;   // JSON files written
        public final int     filesSkipped;    // entries rejected (non-JSON, oversized, unsafe path)
        public final String  errorMessage;    // null on success

        Result(boolean success, int filesRestored, int filesSkipped, String errorMessage) {
            this.success       = success;
            this.filesRestored = filesRestored;
            this.filesSkipped  = filesSkipped;
            this.errorMessage  = errorMessage;
        }
    }

    /**
     * Restore JSON entries from the given encrypted backup Uri (.cscbak)
     * into the app's filesDir.
     *
     * Pipeline:
     *   1. Stream the picked file through the SAF ContentResolver.
     *   2. Decrypt via {@link BackupCrypto#decryptToStream} into a temp
     *      plain zip in cacheDir.
     *   3. Walk the zip and write each .json entry into filesDir.
     *   4. Delete the temp zip on the way out.
     *
     * Safe to call from a background thread.
     */
    public static Result restoreFromZip(Context ctx, Uri zipUri) {
        if (zipUri == null) {
            return new Result(false, 0, 0, "No file selected.");
        }
        ContentResolver resolver = ctx.getContentResolver();
        File filesDir = ctx.getFilesDir();

        // Step 1+2: decrypt the picked .cscbak into a temp plain zip
        File tempPlain = new File(ctx.getCacheDir(),
                "restore_plain_" + System.currentTimeMillis() + ".zip");
        try (InputStream in = resolver.openInputStream(zipUri)) {
            if (in == null) {
                return new Result(false, 0, 0, "Could not open the selected file.");
            }
            com.cricket.scorer.utils.BackupCrypto.decryptToFile(in, tempPlain);
        } catch (IOException e) {
            tempPlain.delete();
            return new Result(false, 0, 0,
                    "Could not read backup: " + e.getMessage());
        }

        // Step 3: walk the decrypted zip and restore JSON entries
        int restored = 0;
        int skipped  = 0;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(tempPlain);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Skip directories - we create them on demand below
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                // Only restore JSON files. Logs and other extras are skipped.
                if (!name.toLowerCase().endsWith(".json")) {
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                // Zip-slip defence: reject absolute paths and ".." traversal
                if (name.startsWith("/") || name.contains("..")) {
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                File outFile = new File(filesDir, name);
                // Final canonical-path check — ensures we never escape filesDir
                if (!outFile.getCanonicalPath().startsWith(
                        filesDir.getCanonicalPath() + File.separator)
                        && !outFile.getCanonicalPath().equals(filesDir.getCanonicalPath())) {
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        skipped++;
                        zis.closeEntry();
                        continue;
                    }
                }

                // Stream the entry to disk with an oversize guard
                long written = 0;
                try (BufferedOutputStream bos =
                             new BufferedOutputStream(new FileOutputStream(outFile))) {
                    byte[] buf = new byte[8192];
                    int read;
                    boolean tooLarge = false;
                    while ((read = zis.read(buf)) != -1) {
                        written += read;
                        if (written > MAX_FILE_BYTES) {
                            tooLarge = true;
                            break;
                        }
                        bos.write(buf, 0, read);
                    }
                    bos.flush();
                    if (tooLarge) {
                        outFile.delete();
                        skipped++;
                        zis.closeEntry();
                        continue;
                    }
                }

                restored++;
                zis.closeEntry();
            }
        } catch (IOException e) {
            tempPlain.delete();
            return new Result(false, restored, skipped,
                    "Failed to read decrypted backup: " + e.getMessage());
        } catch (Exception e) {
            tempPlain.delete();
            return new Result(false, restored, skipped,
                    "Unexpected error: " + e.getMessage());
        }

        // Always delete the decrypted temp zip
        tempPlain.delete();

        if (restored == 0) {
            return new Result(false, 0, skipped,
                    "The selected file decrypted successfully but contained no JSON entries.");
        }
        return new Result(true, restored, skipped, null);
    }
}
