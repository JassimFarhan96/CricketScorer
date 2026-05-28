package com.cricket.scorer.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.view.View;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * BugReportUtils.java
 *
 * One-tap bug reporting. When the user reports a bug we want everything
 * we'd need to reproduce it without asking follow-up questions:
 *
 *   - A screenshot of the screen they were on
 *   - The current logs (app_log.txt + rotated app_log.1.txt)
 *   - Whatever live state is on disk:
 *       live_match.json           (active match, if any)
 *       tournament_tracker.json   (active tournament, if any)
 *   - Device info (model, Android version, app version) in the email body
 *
 * Everything is bundled into the app's external cache directory under a
 * timestamped folder, then handed off to the OS via ACTION_SEND_MULTIPLE.
 * The user picks their email app and the attachments are pre-filled.
 *
 * Why external cache:
 * FileProvider requires files to be inside paths declared in xml/file_paths.xml.
 * External cache (getExternalCacheDir) is accessible to other apps via
 * FileProvider URIs without runtime permissions and is auto-cleaned by
 * the OS if storage runs low.
 *
 * Email destination:
 * Set REPORT_EMAIL below to your support address. Users can still choose
 * any app — the intent is just a default pre-fill.
 */
public final class BugReportUtils {

    /** Change this to your real support email before shipping. */
    private static final String REPORT_EMAIL = "cricketscorer.support@gmail.com";

    /** FileProvider authority — must match the one declared in AndroidManifest. */
    private static final String FP_AUTHORITY = "com.cricket.scorer.fileprovider";

    private BugReportUtils() {}

    /**
     * Builds the bug report bundle and launches an email chooser.
     * Call this from any Activity context.
     *
     * Attachments sent:
     *   screenshot.png          — plain image (readable, intentional)
     *   bug_report_<ts>.csclog  — AES-256-GCM encrypted zip containing:
     *                               app_log.txt, app_log.1.txt,
     *                               live_match.json, tournament_tracker.json
     *
     * The .csclog file is opaque to the user. Use DecryptLogTool.java
     * on your PC to decrypt and read the contents.
     */
    public static void launch(Activity activity) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File bundleDir = new File(activity.getExternalCacheDir(), "bug_reports/" + ts);
            if (!bundleDir.mkdirs() && !bundleDir.exists()) {
                throw new IOException("Failed to create bundle dir");
            }

            ArrayList<Uri> attachments = new ArrayList<>();

            // 1. Screenshot — sent as plain PNG (dev needs to see the screen)
            File screenshot = new File(bundleDir, "screenshot.png");
            if (captureScreenshot(activity, screenshot)) {
                attachments.add(toFpUri(activity, screenshot));
            }

            // 2. Collect all log and state files into a plain zip in cache,
            //    then encrypt it into a .csclog file and attach that.
            //    The plain zip is deleted immediately after encryption.
            File tempZip = new File(bundleDir, "logs_plain.zip");
            int logFileCount = bundleLogsToZip(activity, tempZip);

            if (logFileCount > 0) {
                File encLog = new File(bundleDir, "bug_report_" + ts + ".csclog");
                try {
                    BackupCrypto.encryptToFile(tempZip, encLog);
                    attachments.add(toFpUri(activity, encLog));
                } catch (IOException e) {
                    AppLogger.e("BugReportUtils", "log encryption failed", e);
                    // Fall back: attach plain zip if encryption fails unexpectedly
                    attachments.add(toFpUri(activity, tempZip));
                } finally {
                    tempZip.delete(); // always delete unencrypted zip
                }
            }

            launchEmailIntent(activity, attachments, ts);

        } catch (Exception e) {
            AppLogger.e("BugReportUtils", "launch failed", e);
            android.widget.Toast.makeText(activity,
                    "Failed to prepare bug report: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Zips all log and live-state files into a single plain zip.
     * Returns the number of files added.
     */
    private static int bundleLogsToZip(Activity activity, File outZip) {
        int count = 0;
        try (java.util.zip.ZipOutputStream zos =
                     new java.util.zip.ZipOutputStream(
                             new java.io.BufferedOutputStream(
                                     new java.io.FileOutputStream(outZip)))) {

            String[] candidates = {
                    "app_log.txt",
                    "app_log.1.txt",
                    "live_match.json",
                    "tournament_tracker.json"
            };
            for (String name : candidates) {
                File src = new File(activity.getFilesDir(), name);
                if (!src.isFile()) continue;
                zos.putNextEntry(new java.util.zip.ZipEntry(name));
                try (java.io.FileInputStream fis = new java.io.FileInputStream(src)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) != -1) zos.write(buf, 0, n);
                }
                zos.closeEntry();
                count++;
            }
        } catch (IOException e) {
            AppLogger.e("BugReportUtils", "bundleLogsToZip failed", e);
        }
        return count;
    }

    // ─── Screenshot ───────────────────────────────────────────────────────────

    /**
     * Captures the current activity's root view (decorView) to a PNG.
     * Works for any standard view hierarchy — including any dialogs that
     * are part of the activity. Returns true on success.
     *
     * Note: this won't capture SurfaceView / MediaCodec content (we don't
     * use any) or system UI like the status bar (intentional — the
     * status bar isn't part of decorView's drawn surface).
     */
    private static boolean captureScreenshot(Activity activity, File out) {
        try {
            View root = activity.getWindow().getDecorView().getRootView();
            // Ensure the view has been laid out
            if (root.getWidth() == 0 || root.getHeight() == 0) return false;
            Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            root.draw(canvas);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
            bmp.recycle();
            return true;
        } catch (Exception e) {
            AppLogger.e("BugReportUtils", "screenshot failed", e);
            return false;
        }
    }

    // ─── File helpers ────────────────────────────────────────────────────────

    private static Uri toFpUri(Context ctx, File f) {
        return FileProvider.getUriForFile(ctx, FP_AUTHORITY, f);
    }

    // ─── Email intent ────────────────────────────────────────────────────────

    private static void launchEmailIntent(Activity activity, ArrayList<Uri> attachments,
                                            String ts) {
        String subject = "Cricket Scorer bug report — " + ts;
        String body    = buildEmailBody(activity);

        Intent intent;
        if (attachments.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, attachments.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments);
        }
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_EMAIL,   new String[]{ REPORT_EMAIL });
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT,    body);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            activity.startActivity(Intent.createChooser(intent, "Send bug report via…"));
        } catch (Exception e) {
            AppLogger.e("BugReportUtils", "intent dispatch failed", e);
            android.widget.Toast.makeText(activity,
                    "No email app installed",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static String buildEmailBody(Activity activity) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please describe the bug below this line:\n\n\n");
        sb.append("---\n");
        sb.append("Device:      ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android:     ").append(Build.VERSION.RELEASE)
          .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("App version: ").append(appVersion(activity)).append("\n");
        sb.append("Screen:      ").append(activity.getClass().getSimpleName()).append("\n");
        sb.append("Time:        ").append(new Date()).append("\n");
        return sb.toString();
    }

    private static String appVersion(Context ctx) {
        try {
            return ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
