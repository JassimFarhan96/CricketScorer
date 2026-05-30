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
 * Decrypts a .cscbak file chosen by the user and restores its JSON
 * entries into the app's private files directory.
 */
public class DataImportUtils {

    private static final String TAG          = "DataImportUtils";
    private static final long   MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB per entry

    public static class Result {
        public final boolean success;
        public final int     filesRestored;
        public final int     filesSkipped;
        public final String  errorMessage;

        Result(boolean success, int filesRestored, int filesSkipped, String errorMessage) {
            this.success       = success;
            this.filesRestored = filesRestored;
            this.filesSkipped  = filesSkipped;
            this.errorMessage  = errorMessage;
        }
    }

    public static Result restoreFromZip(Context ctx, Uri zipUri) {
        AppLogger.d(TAG, "restoreFromZip: START — uri=" + zipUri);

        if (zipUri == null) {
            AppLogger.e(TAG, "restoreFromZip: ABORTED — uri is null");
            return new Result(false, 0, 0, "No file selected.");
        }

        ContentResolver resolver = ctx.getContentResolver();
        File filesDir  = ctx.getFilesDir();
        File tempPlain = new File(ctx.getCacheDir(),
                "restore_plain_" + System.currentTimeMillis() + ".zip");

        // ── Step 1+2: open and decrypt the .cscbak ────────────────────────────
        AppLogger.d(TAG, "restoreFromZip: step 1 — opening and decrypting backup file");
        try (InputStream in = resolver.openInputStream(zipUri)) {
            if (in == null) {
                AppLogger.e(TAG, "restoreFromZip: step 1 FAILED — ContentResolver returned"
                        + " null InputStream for uri=" + zipUri);
                return new Result(false, 0, 0, "Could not open the selected file.");
            }
            BackupCrypto.decryptToFile(in, tempPlain);
            AppLogger.d(TAG, "restoreFromZip: step 1 complete — decrypted to "
                    + tempPlain.getAbsolutePath()
                    + " (" + tempPlain.length() + " bytes)");
        } catch (IOException e) {
            AppLogger.e(TAG, "restoreFromZip: step 1 FAILED — decryption error: "
                    + e.getMessage(), e);
            tempPlain.delete();
            return new Result(false, 0, 0,
                    "Could not read backup: " + e.getMessage());
        }

        // ── Step 3: walk the decrypted zip and restore entries ────────────────
        AppLogger.d(TAG, "restoreFromZip: step 2 — extracting JSON entries from"
                + " decrypted zip");
        int restored = 0;
        int skipped  = 0;

        try (java.io.FileInputStream fis = new java.io.FileInputStream(tempPlain);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (entry.isDirectory()) {
                    AppLogger.d(TAG, "restoreFromZip: skipping directory entry — " + name);
                    zis.closeEntry();
                    continue;
                }

                if (!name.toLowerCase().endsWith(".json")) {
                    AppLogger.d(TAG, "restoreFromZip: skipping non-JSON entry — " + name);
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                if (name.startsWith("/") || name.contains("..")) {
                    AppLogger.w(TAG, "restoreFromZip: REJECTED unsafe path — " + name);
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                File outFile = new File(filesDir, name);
                try {
                    if (!outFile.getCanonicalPath().startsWith(
                            filesDir.getCanonicalPath() + File.separator)
                            && !outFile.getCanonicalPath().equals(
                                    filesDir.getCanonicalPath())) {
                        AppLogger.w(TAG, "restoreFromZip: REJECTED zip-slip attempt — "
                                + name);
                        skipped++;
                        zis.closeEntry();
                        continue;
                    }
                } catch (IOException canonEx) {
                    AppLogger.e(TAG, "restoreFromZip: canonical path check FAILED for "
                            + name + " — " + canonEx.getMessage());
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    boolean created = parent.mkdirs();
                    if (!created) {
                        AppLogger.e(TAG, "restoreFromZip: FAILED to create directory "
                                + parent.getAbsolutePath()
                                + " — skipping entry " + name);
                        skipped++;
                        zis.closeEntry();
                        continue;
                    }
                    AppLogger.d(TAG, "restoreFromZip: created directory — "
                            + parent.getAbsolutePath());
                }

                // Write with oversize guard
                long written   = 0;
                boolean tooLarge = false;
                try (BufferedOutputStream bos =
                             new BufferedOutputStream(new FileOutputStream(outFile))) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = zis.read(buf)) != -1) {
                        written += read;
                        if (written > MAX_FILE_BYTES) {
                            tooLarge = true;
                            break;
                        }
                        bos.write(buf, 0, read);
                    }
                    bos.flush();
                }

                if (tooLarge) {
                    outFile.delete();
                    AppLogger.w(TAG, "restoreFromZip: SKIPPED oversized entry — "
                            + name + " (exceeded " + MAX_FILE_BYTES + " bytes)");
                    skipped++;
                    zis.closeEntry();
                    continue;
                }

                AppLogger.d(TAG, "restoreFromZip: restored — " + name
                        + " (" + written + " bytes)");
                restored++;
                zis.closeEntry();
            }

        } catch (IOException e) {
            AppLogger.e(TAG, "restoreFromZip: step 2 FAILED — IO error while reading"
                    + " decrypted zip: " + e.getMessage()
                    + " [restored so far=" + restored + ", skipped=" + skipped + "]", e);
            tempPlain.delete();
            return new Result(false, restored, skipped,
                    "Failed to read decrypted backup: " + e.getMessage());
        } catch (Exception e) {
            AppLogger.e(TAG, "restoreFromZip: step 2 FAILED — unexpected error: "
                    + e.getMessage()
                    + " [restored so far=" + restored + ", skipped=" + skipped + "]", e);
            tempPlain.delete();
            return new Result(false, restored, skipped,
                    "Unexpected error: " + e.getMessage());
        }

        // Cleanup decrypted temp file
        boolean tempDeleted = tempPlain.delete();
        AppLogger.d(TAG, "restoreFromZip: decrypted temp zip cleanup — deleted="
                + tempDeleted);

        if (restored == 0) {
            AppLogger.w(TAG, "restoreFromZip: FAILED — decryption succeeded but no JSON"
                    + " entries found in the archive [skipped=" + skipped + "]");
            return new Result(false, 0, skipped,
                    "The selected file decrypted successfully but contained no JSON entries.");
        }

        AppLogger.d(TAG, "restoreFromZip: SUCCESS — restored=" + restored
                + ", skipped=" + skipped);
        return new Result(true, restored, skipped, null);
    }
}
