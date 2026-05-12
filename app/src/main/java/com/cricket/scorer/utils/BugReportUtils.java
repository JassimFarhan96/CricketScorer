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
    private static final String REPORT_EMAIL = "your-support@example.com";

    /** FileProvider authority — must match the one declared in AndroidManifest. */
    private static final String FP_AUTHORITY = "com.cricket.scorer.fileprovider";

    private BugReportUtils() {}

    /**
     * Builds the bug report bundle and launches an email chooser.
     * Call this from any Activity context.
     */
    public static void launch(Activity activity) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File bundleDir = new File(activity.getExternalCacheDir(), "bug_reports/" + ts);
            if (!bundleDir.mkdirs() && !bundleDir.exists()) {
                throw new IOException("Failed to create bundle dir");
            }

            ArrayList<Uri> attachments = new ArrayList<>();

            // 1. Screenshot of the current screen
            File screenshot = new File(bundleDir, "screenshot.png");
            if (captureScreenshot(activity, screenshot)) {
                attachments.add(toFpUri(activity, screenshot));
            }

            // 2. Persistent logs (Layer 1's AppLogger output)
            File curLog = new File(activity.getFilesDir(), "app_log.txt");
            if (curLog.isFile()) {
                File copy = copyToBundle(curLog, new File(bundleDir, "app_log.txt"));
                if (copy != null) attachments.add(toFpUri(activity, copy));
            }
            File rotLog = new File(activity.getFilesDir(), "app_log.1.txt");
            if (rotLog.isFile()) {
                File copy = copyToBundle(rotLog, new File(bundleDir, "app_log.1.txt"));
                if (copy != null) attachments.add(toFpUri(activity, copy));
            }

            // 3. Live state JSONs (only present if a match/tournament is active)
            File liveMatch = new File(activity.getFilesDir(), "live_match.json");
            if (liveMatch.isFile()) {
                File copy = copyToBundle(liveMatch, new File(bundleDir, "live_match.json"));
                if (copy != null) attachments.add(toFpUri(activity, copy));
            }
            File tracker = new File(activity.getFilesDir(), "tournament_tracker.json");
            if (tracker.isFile()) {
                File copy = copyToBundle(tracker, new File(bundleDir, "tournament_tracker.json"));
                if (copy != null) attachments.add(toFpUri(activity, copy));
            }

            launchEmailIntent(activity, attachments, ts);

        } catch (Exception e) {
            AppLogger.e("BugReportUtils", "launch failed", e);
            android.widget.Toast.makeText(activity,
                    "Failed to prepare bug report: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
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

    private static File copyToBundle(File src, File dst) {
        try (java.io.FileInputStream in  = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return dst;
        } catch (IOException e) {
            AppLogger.e("BugReportUtils", "copy failed: " + src.getName(), e);
            return null;
        }
    }

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
