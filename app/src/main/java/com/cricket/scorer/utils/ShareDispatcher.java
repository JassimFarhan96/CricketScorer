package com.cricket.scorer.utils;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Match;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * ShareDispatcher.java
 *
 * Format-picker entry point for the "Share match data" flow.
 *
 *   1. Shows a dialog with a Spinner offering XLSX or PNG
 *   2. On confirm, runs the matching exporter on a background thread
 *      with a progress spinner
 *   3. Launches ACTION_SEND (or ACTION_SEND_MULTIPLE for PNG) so the user
 *      picks WhatsApp / Gmail / Drive etc. and the file(s) arrive pre-attached
 *
 * Format payloads:
 *   XLSX → [Match.xlsx]         (single file, charts embedded by POI)
 *   PNG  → [summary.png, indepth.png]
 *
 * FileProvider authority "com.cricket.scorer.fileprovider" is already
 * declared in the manifest (set up earlier for bug reports). The
 * file_paths.xml entry external-cache-path "exports/" covers our output.
 */
public final class ShareDispatcher {

    private static final String FP_AUTHORITY = "com.cricket.scorer.fileprovider";

    private ShareDispatcher() {}

    /** Called by StatsActivity when the user taps "Share match data". */
    public static void show(Activity activity, Match match) {
        // Use a themed context to ensure the Spinner and Dialog pick up the correct colors
        ContextThemeWrapper themedContext = new ContextThemeWrapper(activity, R.style.Theme_CricketScorer_Dialog);

        Spinner spinner = new Spinner(themedContext);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(themedContext,
                android.R.layout.simple_spinner_item,
                new String[]{ "Excel spreadsheet (.xlsx)", "Images (.png)" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        new AlertDialog.Builder(themedContext)
                .setTitle("Share match data")
                .setMessage("Choose a format:")
                .setView(spinner)
                .setPositiveButton("Share", (d, w) -> {
                    int pos = spinner.getSelectedItemPosition();
                    if (pos == 0) runXlsxShare(activity, match);
                    else          runPngShare(activity, match);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── XLSX path ──────────────────────────────────────────────────────────

    private static void runXlsxShare(Activity activity, Match match) {
        ProgressDialog progress = makeProgress(activity, "Building spreadsheet…");
        progress.show();

        new Thread(() -> {
            File xlsx = null;
            Exception err = null;
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String base = safeFileBase(match) + "_" + ts;
                xlsx = MatchExcelExporter.export(activity, match, base + ".xlsx");
            } catch (Throwable e) {
                // Apache POI throws Errors (e.g. NoClassDefFoundError) when
                // its xmlbeans wiring goes wrong on Android. Catch Throwable
                // here so a missing class doesn't take the whole app down.
                err = (e instanceof Exception) ? (Exception) e : new Exception(e);
                AppLogger.e("ShareDispatcher", "XLSX export failed", e);
            }
            final File xlsxFinal = xlsx;
            final Exception errFinal = err;
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (errFinal != null) {
                    Toast.makeText(activity,
                            "Failed to build XLSX: " + errFinal.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ArrayList<File> files = new ArrayList<>();
                files.add(xlsxFinal);
                launchShareIntent(activity, files,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        match);
            });
        }).start();
    }

    // ─── PNG path ───────────────────────────────────────────────────────────

    private static void runPngShare(Activity activity, Match match) {
        ProgressDialog progress = makeProgress(activity, "Rendering images…");
        progress.show();

        new Thread(() -> {
            File merged = null;
            Exception err = null;
            try {
                // Build both bitmaps in memory then merge side-by-side
                // → single file → single WhatsApp message (was two before)
                android.graphics.Bitmap summary =
                        MatchImageExporter.buildSummaryBitmap(match);
                android.graphics.Bitmap indepth =
                        MatchImageExporter.buildInDepthBitmap(activity, match);
                android.graphics.Bitmap combined =
                        TableBitmapRenderer.sideBySide(summary, indepth);
                summary.recycle();
                indepth.recycle();

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String name = safeFileBase(match) + "_" + ts + "_combined.png";
                File dir = new File(activity.getExternalCacheDir(), "exports");
                if (!dir.exists()) dir.mkdirs();
                merged = new File(dir, name);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(merged)) {
                    combined.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, fos);
                }
                combined.recycle();
            } catch (Exception e) {
                err = e;
                AppLogger.e("ShareDispatcher", "PNG export failed", e);
            }
            final File mergedFinal = merged;
            final Exception errFinal = err;
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (errFinal != null) {
                    Toast.makeText(activity,
                            "Failed to build images: " + errFinal.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ArrayList<File> files = new ArrayList<>();
                if (mergedFinal != null) files.add(mergedFinal);
                launchShareIntent(activity, files, "image/png", match);
            });
        }).start();
    }

    // ─── Common share intent ────────────────────────────────────────────────

    private static void launchShareIntent(Activity activity, ArrayList<File> files,
                                            String primaryMime, Match match) {
        if (files.isEmpty()) {
            Toast.makeText(activity, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) {
            uris.add(FileProvider.getUriForFile(activity, FP_AUTHORITY, f));
        }
        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            intent.setType(primaryMime);
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.setType(primaryMime);
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, "Match: "
                + safe(match.getHomeTeamName()) + " vs " + safe(match.getAwayTeamName()));
        intent.putExtra(Intent.EXTRA_TEXT, buildBodyText(match));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            activity.startActivity(Intent.createChooser(intent, "Share match data via…"));
        } catch (Exception e) {
            AppLogger.e("ShareDispatcher", "share chooser failed", e);
            Toast.makeText(activity, "No app available to share",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ─── Misc ───────────────────────────────────────────────────────────────

    private static String buildBodyText(Match match) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(match.getHomeTeamName()))
          .append(" vs ").append(safe(match.getAwayTeamName())).append("\n");
        if (match.getFirstInnings() != null) {
            sb.append(MatchScorecardBuilder.inningsHeading(match, 1))
              .append(": ").append(match.getFirstInnings().getScoreString())
              .append(" (").append(match.getFirstInnings().getOversString())
              .append(" ov)\n");
        }
        if (match.getSecondInnings() != null) {
            sb.append(MatchScorecardBuilder.inningsHeading(match, 2))
              .append(": ").append(match.getSecondInnings().getScoreString())
              .append(" (").append(match.getSecondInnings().getOversString())
              .append(" ov)\n");
        }
        if (match.isMatchCompleted() && match.getResultDescription() != null) {
            sb.append("\n").append(match.getResultDescription()).append("\n");
        }
        sb.append("\nShared via Cricket Scorer App");
        return sb.toString();
    }

    private static ProgressDialog makeProgress(Activity activity, String msg) {
        ProgressDialog p = new ProgressDialog(activity);
        p.setMessage(msg);
        p.setCancelable(false);
        return p;
    }

    private static String safeFileBase(Match match) {
        String home = safe(match.getHomeTeamName()).replaceAll("[^A-Za-z0-9]", "");
        String away = safe(match.getAwayTeamName()).replaceAll("[^A-Za-z0-9]", "");
        if (home.isEmpty()) home = "home";
        if (away.isEmpty()) away = "away";
        return home + "_vs_" + away;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
