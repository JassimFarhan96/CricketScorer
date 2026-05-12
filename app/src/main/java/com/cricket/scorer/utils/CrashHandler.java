package com.cricket.scorer.utils;

import android.content.Context;
import android.os.Build;

/**
 * CrashHandler.java
 *
 * Catches every uncaught exception in the app, writes the full stack
 * trace + device info to app_log.txt via AppLogger, then delegates to
 * the original default handler so Android still shows its "App crashed"
 * dialog and kills the process — behaviour is unchanged from the user's
 * perspective.
 *
 * Install once at app startup from CricketApp.onCreate AFTER AppLogger.init.
 *
 * What's captured:
 *   - Time, thread name
 *   - Full stack trace (cause chain unwound by PrintWriter)
 *   - Device model, Android version, app version
 *
 * The user reports a crash; you ask them to tap Export Data and forward
 * the ZIP; you open app_log.txt inside and find a "FATAL" line with the
 * stack trace.
 */
public final class CrashHandler {

    private CrashHandler() {}

    public static void install(Context ctx) {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                AppLogger.e("CrashHandler",
                        "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                                + "  Android " + Build.VERSION.RELEASE
                                + " (SDK " + Build.VERSION.SDK_INT + ")"
                                + "  AppVersion: " + appVersion(ctx)
                                + "  Thread: " + thread.getName());
                AppLogger.fatal("CrashHandler", ex);
            } catch (Throwable ignored) {
                // If logging itself fails, don't swallow the original crash
            }
            if (previous != null) {
                previous.uncaughtException(thread, ex);
            } else {
                // Last-resort: kill the process so Android shows its dialog
                System.exit(2);
            }
        });
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
