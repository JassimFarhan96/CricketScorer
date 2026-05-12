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
 * Bundles all of the app's persisted JSON files into a single ZIP archive
 * and writes it to the device's public Downloads folder so the user (or
 * testers) can find it via any file manager and share / open it.
 *
 * What's included:
 *   recent_matches/                  — individually saved regular matches
 *   recent_tournaments/              — archived tournaments + matches/ subdir
 *   live_match.json                  — in-progress match tracker
 *   tournament_tracker.json          — in-progress tournament tracker
 *
 * Cross-version handling:
 *   - Android 10+ (Q, API 29+): writes via MediaStore.Downloads (scoped storage)
 *   - Android 7–9 (API 24–28):  writes directly to /storage/emulated/0/Download/
 *                                — requires no permission for the Downloads dir
 *                                  on most devices since targetSdk 34, but the
 *                                  fallback uses Environment.DIRECTORY_DOWNLOADS
 *                                  which is universally available.
 */
public class DataExportUtils {

    public static class Result {
        public final boolean success;
        public final String  fileName;     // e.g. CricketScorer_Export_20260511_173045.zip
        public final String  displayPath;  // human-readable location for the user
        public final int     fileCount;    // how many JSON files were bundled
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

    /**
     * Bundle and write the export. Runs on the caller's thread — the file
     * sizes are tiny (KBs total), so a background thread isn't necessary,
     * but feel free to wrap in AsyncTask / Executor if you prefer.
     */
    public static Result exportAll(Context ctx) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "CricketScorer_Export_" + ts + ".zip";

        // Build the ZIP into a temp file inside the app's cache dir first,
        // then copy it out to Downloads. This works the same way on every
        // Android version and avoids partial writes if zipping fails.
        File tempZip = new File(ctx.getCacheDir(), fileName);
        int fileCount;
        try {
            fileCount = buildZip(ctx, tempZip);
        } catch (IOException e) {
            return new Result(false, null, null, 0,
                    "Failed to build zip: " + e.getMessage());
        }

        // Empty-state guard: refuse only if BOTH no JSON files AND no logs.
        // Logs alone are still worth exporting for fresh-install bug reports.
        File anyLog        = new File(ctx.getFilesDir(), "app_log.txt");
        File anyRotatedLog = new File(ctx.getFilesDir(), "app_log.1.txt");
        boolean hasLogs    = anyLog.isFile() || anyRotatedLog.isFile();
        if (fileCount == 0 && !hasLogs) {
            tempZip.delete();
            return new Result(false, null, null, 0,
                    "No data to export yet — play a match or tournament first.");
        }

        // Move the zip into Downloads
        try {
            String displayPath = copyToDownloads(ctx, tempZip, fileName);
            tempZip.delete();
            return new Result(true, fileName, displayPath, fileCount, null);
        } catch (IOException e) {
            tempZip.delete();
            return new Result(false, null, null, fileCount,
                    "Failed to write to Downloads: " + e.getMessage());
        }
    }

    /** Builds the ZIP from app data dirs. Returns the count of JSON files added. */
    private static int buildZip(Context ctx, File outZip) throws IOException {
        File filesDir = ctx.getFilesDir();
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outZip))) {
            // Top-level loose files (live_match.json, tournament_tracker.json, etc.)
            File[] topLevel = filesDir.listFiles();
            if (topLevel != null) {
                for (File f : topLevel) {
                    if (f.isFile() && f.getName().endsWith(".json")) {
                        addToZip(zos, f, f.getName());
                        count++;
                    }
                }
            }
            // recent_matches/ — regular saved matches
            count += addDirToZip(zos, new File(filesDir, "recent_matches"),
                    "recent_matches/");
            // recent_tournaments/ — archives + matches subdir
            File tournDir = new File(filesDir, "recent_tournaments");
            count += addDirToZip(zos, tournDir, "recent_tournaments/");
            // recent_tournaments/matches/ — individual tournament match files
            File tournMatches = new File(tournDir, "matches");
            count += addDirToZip(zos, tournMatches, "recent_tournaments/matches/");

            // app_log.txt + app_log.1.txt — persistent diagnostic logs.
            // Always bundled even if no JSON data exists, since they're the
            // primary source of info for bug reports. Logs don't count
            // towards the data-file count (used for the empty-state check).
            File currentLog = new File(filesDir, "app_log.txt");
            if (currentLog.isFile()) addToZip(zos, currentLog, "logs/app_log.txt");
            File rotatedLog = new File(filesDir, "app_log.1.txt");
            if (rotatedLog.isFile()) addToZip(zos, rotatedLog, "logs/app_log.1.txt");
        }
        return count;
    }

    /**
     * Adds every .json file in `dir` to the zip under `prefix`.
     * Recursion is intentionally NOT used — recent_tournaments/matches/ is
     * handled separately above so the ZIP structure exactly mirrors the
     * on-device folder layout.
     */
    private static int addDirToZip(ZipOutputStream zos, File dir, String prefix)
            throws IOException {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
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

    /** Copies the zip to public Downloads. Returns human-readable path. */
    private static String copyToDownloads(Context ctx, File src, String fileName)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage path (Android 10+)
            ContentResolver resolver = ctx.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE,    "application/zip");
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
            return "Downloads/" + fileName;
        } else {
            // Pre-Q direct file path
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
            return out.getAbsolutePath();
        }
    }
}
