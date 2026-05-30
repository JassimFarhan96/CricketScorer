package com.cricket.scorer.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * DataExportUtils.java
 *
 * Bundles all persisted JSON files into an AES-256-GCM encrypted .cscbak
 * archive and writes it to the device's public Downloads folder.
 */
public class DataExportUtils {

    private static final String TAG = "DataExportUtils";

    public static class Result {
        public final boolean success;
        public final String  fileName;
        public final String  displayPath;
        public final int     fileCount;
        public final String  errorMessage;

        Result(boolean success, String fileName, String displayPath,
               int fileCount, String errorMessage) {
            this.success      = success;
            this.fileName     = fileName;
            this.displayPath  = displayPath;
            this.fileCount    = fileCount;
            this.errorMessage = errorMessage;
        }
    }

    public static Result exportAll(Context ctx) {
        String ts       = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "CricketScorer_Backup_" + ts + ".cscbak";

        AppLogger.d(TAG, "exportAll: START — fileName=" + fileName);

        File tempZip = new File(ctx.getCacheDir(), "backup_plain_" + ts + ".zip");
        File tempEnc = new File(ctx.getCacheDir(), fileName);
        int fileCount;

        // ── Step 1: Build plain zip ───────────────────────────────────────────
        AppLogger.d(TAG, "exportAll: step 1 — building plain zip at "
                + tempZip.getAbsolutePath());
        try {
            fileCount = buildZip(ctx, tempZip);
            AppLogger.d(TAG, "exportAll: step 1 complete — zipped " + fileCount
                    + " JSON file(s), zip size=" + tempZip.length() + " bytes");
        } catch (IOException e) {
            AppLogger.e(TAG, "exportAll: step 1 FAILED — could not build zip: "
                    + e.getMessage(), e);
            tempZip.delete();
            return new Result(false, null, null, 0,
                    "Failed to build zip: " + e.getMessage());
        }

        // ── Empty-state guard ─────────────────────────────────────────────────
        File anyLog        = new File(ctx.getFilesDir(), "app_log.txt");
        File anyRotatedLog = new File(ctx.getFilesDir(), "app_log.1.txt");
        boolean hasLogs    = anyLog.isFile() || anyRotatedLog.isFile();
        if (fileCount == 0 && !hasLogs) {
            AppLogger.w(TAG, "exportAll: ABORTED — no JSON data and no logs to export");
            tempZip.delete();
            return new Result(false, null, null, 0,
                    "No data to export yet — play a match or tournament first.");
        }

        // ── Step 2: Encrypt zip → .cscbak ────────────────────────────────────
        AppLogger.d(TAG, "exportAll: step 2 — encrypting plain zip into "
                + tempEnc.getName());
        try {
            BackupCrypto.encryptToFile(tempZip, tempEnc);
            AppLogger.d(TAG, "exportAll: step 2 complete — encrypted file size="
                    + tempEnc.length() + " bytes");
        } catch (IOException e) {
            AppLogger.e(TAG, "exportAll: step 2 FAILED — encryption error: "
                    + e.getMessage(), e);
            tempZip.delete();
            tempEnc.delete();
            return new Result(false, null, null, fileCount,
                    "Failed to encrypt backup: " + e.getMessage());
        } finally {
            boolean plainDeleted = tempZip.delete();
            AppLogger.d(TAG, "exportAll: plain zip cleanup — deleted=" + plainDeleted);
        }

        // ── Step 3: Copy encrypted file to Downloads ──────────────────────────
        AppLogger.d(TAG, "exportAll: step 3 — copying encrypted backup to Downloads");
        try {
            String displayPath = copyToDownloads(ctx, tempEnc, fileName);
            tempEnc.delete();
            AppLogger.d(TAG, "exportAll: SUCCESS — backup saved to " + displayPath
                    + " [" + fileCount + " JSON file(s) bundled]");
            return new Result(true, fileName, displayPath, fileCount, null);
        } catch (IOException e) {
            AppLogger.e(TAG, "exportAll: step 3 FAILED — could not write to Downloads: "
                    + e.getMessage(), e);
            tempEnc.delete();
            return new Result(false, null, null, fileCount,
                    "Failed to write to Downloads: " + e.getMessage());
        }
    }

    private static int buildZip(Context ctx, File outZip) throws IOException {
        File filesDir = ctx.getFilesDir();
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outZip))) {
            File[] topLevel = filesDir.listFiles();
            if (topLevel != null) {
                for (File f : topLevel) {
                    if (f.isFile() && f.getName().endsWith(".json")) {
                        addToZip(zos, f, f.getName());
                        AppLogger.d(TAG, "buildZip: added top-level — " + f.getName());
                        count++;
                    }
                }
            }

            int matchCount = addDirToZip(zos, new File(filesDir, "recent_matches"),
                    "recent_matches/");
            AppLogger.d(TAG, "buildZip: added " + matchCount
                    + " file(s) from recent_matches/");
            count += matchCount;

            File tournDir = new File(filesDir, "recent_tournaments");
            int tournCount = addDirToZip(zos, tournDir, "recent_tournaments/");
            AppLogger.d(TAG, "buildZip: added " + tournCount
                    + " file(s) from recent_tournaments/");
            count += tournCount;

            File tournMatches = new File(tournDir, "matches");
            int tmCount = addDirToZip(zos, tournMatches, "recent_tournaments/matches/");
            AppLogger.d(TAG, "buildZip: added " + tmCount
                    + " file(s) from recent_tournaments/matches/");
            count += tmCount;

            File currentLog = new File(filesDir, "app_log.txt");
            if (currentLog.isFile()) {
                addToZip(zos, currentLog, "logs/app_log.txt");
                AppLogger.d(TAG, "buildZip: added logs/app_log.txt ("
                        + currentLog.length() + " bytes)");
            }
            File rotatedLog = new File(filesDir, "app_log.1.txt");
            if (rotatedLog.isFile()) {
                addToZip(zos, rotatedLog, "logs/app_log.1.txt");
                AppLogger.d(TAG, "buildZip: added logs/app_log.1.txt ("
                        + rotatedLog.length() + " bytes)");
            }
        }
        return count;
    }

    private static int addDirToZip(ZipOutputStream zos, File dir, String prefix)
            throws IOException {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            AppLogger.d(TAG, "addDirToZip: directory not found, skipping — " + prefix);
            return 0;
        }
        int n = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".json")) {
                addToZip(zos, f, prefix + f.getName());
                n++;
            }
        }
        return n;
    }

    private static void addToZip(ZipOutputStream zos, File f, String entryName)
            throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(f.lastModified());
        zos.putNextEntry(entry);
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = bis.read(buf)) != -1) zos.write(buf, 0, read);
        }
        zos.closeEntry();
    }

    private static String copyToDownloads(Context ctx, File src, String fileName)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = ctx.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE,    "application/octet-stream");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore insert returned null");
            try (OutputStream os = resolver.openOutputStream(uri);
                 BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src))) {
                if (os == null) throw new IOException("Failed to open output stream");
                byte[] buf = new byte[8192];
                int read;
                while ((read = bis.read(buf)) != -1) os.write(buf, 0, read);
            }
            AppLogger.d(TAG, "copyToDownloads: written via MediaStore (API 29+)");
            return "Downloads/" + fileName;
        } else {
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            if (!downloads.exists()) downloads.mkdirs();
            File out = new File(downloads, fileName);
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src));
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = bis.read(buf)) != -1) fos.write(buf, 0, read);
            }
            AppLogger.d(TAG, "copyToDownloads: written directly (pre-API 29) to "
                    + out.getAbsolutePath());
            return out.getAbsolutePath();
        }
    }
}
