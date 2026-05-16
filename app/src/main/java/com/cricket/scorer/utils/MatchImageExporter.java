package com.cricket.scorer.utils;

import android.content.Context;
import android.graphics.Bitmap;

import com.cricket.scorer.models.Match;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MatchImageExporter.java
 *
 * Produces the 2 PNGs for the PNG share format.
 *
 *   Image 1 — summary.png:
 *       Match details, batting + bowling cards for both innings,
 *       drawn as a single tall image (not a screenshot of the app).
 *
 *   Image 2 — indepth.png:
 *       Cumulative-runs chart + over-by-over bar charts +
 *       over-by-over data tables, stacked vertically into one tall image.
 *
 * Files are written to externalCacheDir/exports/ alongside any XLSX
 * export so a single sharing flow can pick them all up via FileProvider.
 */
public final class MatchImageExporter {

    private MatchImageExporter() {}

    /**
     * Bundles the summary into a single PNG and writes to disk.
     * @return File reference to the written PNG.
     */
    public static File exportSummary(Context ctx, Match match, String outFileName)
            throws IOException {
        File dir = ensureDir(ctx);
        File out = new File(dir, outFileName);

        List<Bitmap> parts = new ArrayList<>();

        // Big title bar
        parts.add(makeBanner("Match Summary —  "
                + safe(match.getHomeTeamName()) + " vs " + safe(match.getAwayTeamName())));

        // Match details table
        parts.add(TableBitmapRenderer.render("Match details",
                MatchScorecardBuilder.summaryTable(match)));

        // Innings 1 batting + bowling
        parts.add(TableBitmapRenderer.render(
                MatchScorecardBuilder.inningsHeading(match, 1) + " — Batting",
                MatchScorecardBuilder.battingTable(match, 1)));
        parts.add(TableBitmapRenderer.render(
                MatchScorecardBuilder.inningsHeading(match, 1) + " — Bowling",
                MatchScorecardBuilder.bowlingTable(match, 1)));

        if (match.getSecondInnings() != null) {
            parts.add(TableBitmapRenderer.render(
                    MatchScorecardBuilder.inningsHeading(match, 2) + " — Batting",
                    MatchScorecardBuilder.battingTable(match, 2)));
            parts.add(TableBitmapRenderer.render(
                    MatchScorecardBuilder.inningsHeading(match, 2) + " — Bowling",
                    MatchScorecardBuilder.bowlingTable(match, 2)));
        }

        Bitmap composite = TableBitmapRenderer.stack(parts.toArray(new Bitmap[0]));
        writeBitmap(composite, out);
        recycleAll(parts);
        composite.recycle();
        return out;
    }

    /**
     * Bundles in-depth stats (charts + tables) into a single tall PNG.
     */
    public static File exportInDepth(Context ctx, Match match, String outFileName)
            throws IOException {
        File dir = ensureDir(ctx);
        File out = new File(dir, outFileName);

        List<Bitmap> parts = new ArrayList<>();

        parts.add(makeBanner("In-depth Statistics —  "
                + safe(match.getHomeTeamName()) + " vs " + safe(match.getAwayTeamName())));

        // Cumulative runs line chart
        parts.add(ChartBitmapRenderer.renderRunRateChart(ctx, match));

        // Innings 1: over-by-over chart then data table
        parts.add(ChartBitmapRenderer.renderOverBarChart(ctx, match, 1));
        parts.add(TableBitmapRenderer.render(
                MatchScorecardBuilder.inningsHeading(match, 1) + " — Over by over",
                MatchScorecardBuilder.overByOverTable(match, 1)));

        if (match.getSecondInnings() != null) {
            parts.add(ChartBitmapRenderer.renderOverBarChart(ctx, match, 2));
            parts.add(TableBitmapRenderer.render(
                    MatchScorecardBuilder.inningsHeading(match, 2) + " — Over by over",
                    MatchScorecardBuilder.overByOverTable(match, 2)));
        }

        Bitmap composite = TableBitmapRenderer.stack(parts.toArray(new Bitmap[0]));
        writeBitmap(composite, out);
        recycleAll(parts);
        composite.recycle();
        return out;
    }

    /**
     * Produces the same 2 chart bitmaps that go into in-depth.png, but as
     * standalone files. Used by the XLSX-format share so the user gets the
     * charts as separate attachments alongside the data-only XLSX.
     *
     * @return [runRateChart.png, overByOver.png] — second-innings bar gets
     *         combined into the same over-by-over image so the share is
     *         always exactly 2 chart files.
     */
    public static File[] exportChartsForExcel(Context ctx, Match match,
                                                String runRateName,
                                                String overByOverName) throws IOException {
        File dir = ensureDir(ctx);

        // 1. Run rate chart
        File rr = new File(dir, runRateName);
        Bitmap rrBmp = ChartBitmapRenderer.renderRunRateChart(ctx, match);
        writeBitmap(rrBmp, rr);
        rrBmp.recycle();

        // 2. Over-by-over (combine innings 1 + innings 2 if present)
        File obo = new File(dir, overByOverName);
        Bitmap b1 = ChartBitmapRenderer.renderOverBarChart(ctx, match, 1);
        Bitmap composite;
        if (match.getSecondInnings() != null) {
            Bitmap b2 = ChartBitmapRenderer.renderOverBarChart(ctx, match, 2);
            composite = TableBitmapRenderer.stack(b1, b2);
            b1.recycle(); b2.recycle();
        } else {
            composite = b1;
        }
        writeBitmap(composite, obo);
        composite.recycle();

        return new File[]{ rr, obo };
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static File ensureDir(Context ctx) throws IOException {
        File dir = new File(ctx.getExternalCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create export dir: " + dir);
        }
        return dir;
    }

    private static void writeBitmap(Bitmap bmp, File out) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
        }
    }

    /** Title banner bitmap — green strip with white bold text. */
    private static Bitmap makeBanner(String text) {
        int w = 1200, h = 90;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawColor(0xFF0F4E3D);
        android.graphics.Paint p = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(40);
        p.setFakeBoldText(true);
        c.drawText(text, 36, 58, p);
        return bmp;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static void recycleAll(List<Bitmap> bmps) {
        for (Bitmap b : bmps) if (b != null && !b.isRecycled()) b.recycle();
    }
}
