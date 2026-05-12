package com.cricket.scorer.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AppLogger.java
 *
 * Persistent logger. Mirrors every call to android.util.Log (so logcat
 * still works during development) AND appends a timestamped line to
 * app_log.txt in the app's private filesDir.
 *
 * Why this exists:
 * Logcat dies the moment the app process dies, and ordinary users can't
 * access it anyway. When a tester reports "the app did the wrong thing
 * yesterday at 6pm", we need a file we can open after the fact. The
 * Export Data ZIP picks this file up automatically.
 *
 * Rotation:
 * When app_log.txt exceeds MAX_BYTES (~500KB) it's renamed to app_log.1.txt
 * (overwriting any previous .1) and a fresh app_log.txt is started. This
 * caps disk usage at ~1MB while keeping the most recent ~1000-2000 entries
 * across two files.
 *
 * Thread safety:
 * Writes are synchronized on the class lock. The volume is small (a few
 * lines per second at most) so contention isn't a concern.
 *
 * Initialization:
 * AppLogger.init(context) must be called once at app startup (CricketApp
 * .onCreate). Before init, all calls degrade gracefully to logcat-only —
 * no crashes, just no file output.
 */
public final class AppLogger {

    private static final String  FILE_NAME    = "app_log.txt";
    private static final String  ROTATED_NAME = "app_log.1.txt";
    private static final long    MAX_BYTES    = 500L * 1024;

    private static final SimpleDateFormat TS_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File logFile;          // null until init()
    private static final Object LOCK = new Object();

    private AppLogger() {}

    /** Call once from CricketApp.onCreate. Safe to call multiple times. */
    public static void init(Context ctx) {
        synchronized (LOCK) {
            if (logFile != null) return;
            logFile = new File(ctx.getFilesDir(), FILE_NAME);
            // Stamp a session header so it's easy to tell sessions apart
            // when reading a long log file.
            writeLine("====== Session start: " + TS_FMT.format(new Date()) + " ======");
        }
    }

    public static void d(String tag, String msg)              { log("D", tag, msg, null); Log.d(tag, msg); }
    public static void i(String tag, String msg)              { log("I", tag, msg, null); Log.i(tag, msg); }
    public static void w(String tag, String msg)              { log("W", tag, msg, null); Log.w(tag, msg); }
    public static void w(String tag, String msg, Throwable t) { log("W", tag, msg, t);    Log.w(tag, msg, t); }
    public static void e(String tag, String msg)              { log("E", tag, msg, null); Log.e(tag, msg); }
    public static void e(String tag, String msg, Throwable t) { log("E", tag, msg, t);    Log.e(tag, msg, t); }

    /**
     * Called by CrashHandler to record a fatal uncaught exception.
     * Marked separately so it stands out in the file.
     */
    public static void fatal(String tag, Throwable t) {
        log("FATAL", tag, "uncaught exception", t);
    }

    /** File the logger is currently writing to. Null if init wasn't called. */
    public static File currentFile() { return logFile; }

    /** Rotated file (may not exist). */
    public static File rotatedFile(Context ctx) {
        return new File(ctx.getFilesDir(), ROTATED_NAME);
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private static void log(String level, String tag, String msg, Throwable t) {
        if (logFile == null) return;
        String stack = t != null ? "\n" + stackTrace(t) : "";
        String line  = TS_FMT.format(new Date()) + " " + level + "/" + tag + ": " + msg + stack;
        writeLine(line);
    }

    private static void writeLine(String line) {
        synchronized (LOCK) {
            if (logFile == null) return;
            try {
                rotateIfNeeded();
                try (FileWriter fw = new FileWriter(logFile, /*append=*/ true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(line);
                }
            } catch (Exception ignored) {
                // Never let logging itself crash the app
            }
        }
    }

    private static void rotateIfNeeded() {
        if (logFile == null || !logFile.exists()) return;
        if (logFile.length() < MAX_BYTES) return;
        File rotated = new File(logFile.getParentFile(), ROTATED_NAME);
        if (rotated.exists()) rotated.delete();
        logFile.renameTo(rotated);
        // After rename, logFile reference still points to the old path;
        // the next FileWriter call will create a fresh empty file there.
    }

    private static String stackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
