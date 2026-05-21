package com.cricket.scorer.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;

import java.util.ArrayList;
import java.util.List;

/**
 * ChartBitmapRenderer.java
 *
 * Renders charts to Bitmaps for export (XLSX embedded images and PNG composites).
 * Charts match exactly what DeepStatsActivity shows on screen:
 *
 *   1. renderRunRateChart()  — "Run Rate Progression (Average Run Rate per Over)"
 *      Y-axis = rolling average run rate at the END of each over.
 *      Formula: (cumulative runs / cumulative valid balls) * 6
 *      This matches the line chart in the app's in-depth statistics screen.
 *      NOT cumulative runs — that was wrong in the previous version.
 *
 *   2. renderOverBarChart()  — "Over-by-over runs" bar chart.
 *      One bar per over, height = runs scored in that over.
 *      Red dot marker when a wicket fell in that over.
 */
public final class ChartBitmapRenderer {

    private ChartBitmapRenderer() {}

    private static final int TITLE_TS = 32;
    private static final int LABEL_TS = 24;
    private static final int AXIS_TS  = 20;
    private static final int CHART_W  = 1100;
    private static final int CHART_H  = 600;

    private static final int COLOR_GREEN = Color.parseColor("#0F4E3D");
    private static final int COLOR_TEAL  = Color.parseColor("#00BFA5");
    private static final int COLOR_RED   = Color.parseColor("#C44536");
    private static final int COLOR_GRID  = Color.parseColor("#EEEEEE");
    private static final int COLOR_AXIS  = Color.parseColor("#888888");
    private static final int COLOR_LBL   = Color.parseColor("#555555");
    private static final int COLOR_TEXT  = Color.parseColor("#333333");

    // ─── 1. Run Rate Progression chart ──────────────────────────────────────────

    /**
     * Renders "Run Rate Progression (Average Run Rate per Over)" matching the
     * line chart in DeepStatsActivity.
     *
     * Each point = rolling average RR at end of that over:
     *   RR = (total_runs_so_far / total_valid_balls_so_far) * 6
     */
    public static Bitmap renderRunRateChart(Context ctx, Match match) {
        Bitmap bmp = Bitmap.createBitmap(CHART_W, CHART_H, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        c.drawText("Run Rate Progression (Average Run Rate per Over)", 40, 50,
                makePaint(COLOR_GREEN, TITLE_TS, true));

        List<float[]> series     = new ArrayList<>();
        List<String>  labels     = new ArrayList<>();
        List<Integer> lineColors = new ArrayList<>();

        if (match.getFirstInnings() != null) {
            series.add(buildRunRateSeries(match.getFirstInnings()));
            labels.add(inningsLabel(match, 1));
            lineColors.add(COLOR_GREEN);
        }
        if (match.getSecondInnings() != null) {
            series.add(buildRunRateSeries(match.getSecondInnings()));
            labels.add(inningsLabel(match, 2));
            lineColors.add(COLOR_TEAL);
        }
        if (series.isEmpty()) return bmp;

        int axisL = 110, axisR = CHART_W - 60, axisT = 90, axisB = CHART_H - 80;

        // Y scale — find max RR, round up nicely
        float maxRR = 6f;
        int maxOvs = 1;
        for (float[] s : series) {
            if (s.length > maxOvs) maxOvs = s.length;
            for (float v : s) if (v > maxRR) maxRR = v;
        }
        maxRR = (float) (Math.ceil(maxRR / 2f) * 2 + 2);

        // Axes
        Paint axisPaint = makePaint(COLOR_AXIS, 2, false);
        Paint gridPaint = makePaint(COLOR_GRID, 1, false);
        c.drawLine(axisL, axisB, axisR, axisB, axisPaint);
        c.drawLine(axisL, axisT, axisL, axisB, axisPaint);

        // Y grid + labels
        Paint yLbl = makePaint(COLOR_LBL, AXIS_TS, false);
        yLbl.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 6; i++) {
            int y = axisB - (int) ((axisB - axisT) * (i / 6f));
            c.drawLine(axisL, y, axisR, y, gridPaint);
            c.drawText(String.format("%.1f", maxRR * i / 6f), axisL - 8, y + 8, yLbl);
        }

        // X axis labels
        // X axis: over 1 starts at axisL (left edge), last over ends at axisR (right edge).
        // Matches the app chart which begins the line at the Y-axis, not offset from it.
        Paint xLbl = makePaint(COLOR_LBL, AXIS_TS, false);
        xLbl.setTextAlign(Paint.Align.CENTER);
        int xStep = maxOvs <= 10 ? 1 : (maxOvs <= 20 ? 2 : 5);
        for (int i = 1; i <= maxOvs; i++) {
            if (i % xStep == 0 || i == 1 || i == maxOvs) {
                float x = maxOvs == 1 ? (axisL + axisR) / 2f
                        : axisL + (axisR - axisL) * ((i - 1) / (float) (maxOvs - 1));
                c.drawText(String.valueOf(i), x, axisB + 32, xLbl);
                c.drawLine(x, axisB, x, axisB + 6, axisPaint);
            }
        }
        c.drawText("Overs", (axisL + axisR) / 2f, CHART_H - 20, xLbl);

        // Draw lines + dots + value labels
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(4);
        linePaint.setStyle(Paint.Style.STROKE);

        for (int idx = 0; idx < series.size(); idx++) {
            int col = lineColors.get(idx);
            linePaint.setColor(col);
            Paint dotPaint = makePaint(col, 0, false);
            float[] s = series.get(idx);
            float prevX = -1, prevY = -1;

            for (int i = 0; i < s.length; i++) {
                // i=0 → over 1 → axisL; i=maxOvs-1 → last over → axisR
                float x = s.length == 1 ? (axisL + axisR) / 2f
                        : axisL + (axisR - axisL) * (i / (float) (maxOvs - 1));
                float y = axisB - (axisB - axisT) * (s[i] / maxRR);
                if (prevX >= 0) c.drawLine(prevX, prevY, x, y, linePaint);
                c.drawCircle(x, y, 8, dotPaint);

                // Value label above each dot (like the app does)
                Paint valLbl = makePaint(col, AXIS_TS - 2, true);
                valLbl.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.format("%.1f", s[i]), x, y - 16, valLbl);

                prevX = x; prevY = y;
            }

            // Legend entry
            int lx = axisR - 220, ly = axisT + 24 + idx * 38;
            c.drawCircle(lx, ly, 9, dotPaint);
            Paint legTxt = makePaint(COLOR_TEXT, LABEL_TS, false);
            legTxt.setTextAlign(Paint.Align.LEFT);
            c.drawText(labels.get(idx), lx + 22, ly + 9, legTxt);
        }

        return bmp;
    }

    /**
     * Rolling average run rate series for one innings.
     * float[i] = avg RR at end of over (i+1).
     * RR = (cumulative runs / cumulative valid balls) * 6
     */
    private static float[] buildRunRateSeries(Innings inn) {
        List<Over> overs = inn.getAllOvers();
        float[] rr = new float[overs.size()];
        int cumRuns = 0, cumBalls = 0;
        for (int i = 0; i < overs.size(); i++) {
            for (Ball b : overs.get(i).getBalls()) {
                cumRuns += b.getRuns();
                if (b.isValid()) cumBalls++;
            }
            rr[i] = cumBalls > 0 ? (cumRuns / (float) cumBalls) * 6f : 0f;
        }
        return rr;
    }

    // ─── 2. Over-by-over bar chart ───────────────────────────────────────────────

    /**
     * Over-by-over runs bar chart — one bar per over, height = runs that over.
     * Red dot above bar when a wicket fell. Matches DeepStatsActivity's bar section.
     */
    public static Bitmap renderOverBarChart(Context ctx, Match match, int innings) {
        Innings inn = innings == 1 ? match.getFirstInnings() : match.getSecondInnings();
        Bitmap bmp = Bitmap.createBitmap(CHART_W, CHART_H, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        c.drawText("Over-by-over runs \u2013 " + inningsLabel(match, innings), 40, 50,
                makePaint(COLOR_GREEN, TITLE_TS, true));

        if (inn == null) return bmp;
        List<Over> overs = inn.getAllOvers();
        int n = overs.size();
        if (n == 0) {
            c.drawText("No overs bowled yet", 40, CHART_H / 2,
                    makePaint(COLOR_LBL, LABEL_TS, false));
            return bmp;
        }

        int maxRuns = 1;
        int[] runsPerOver  = new int[n];
        boolean[] wktInOver = new boolean[n];
        for (int i = 0; i < n; i++) {
            int total = 0;
            for (Ball b : overs.get(i).getBalls()) {
                total += b.getRuns();
                if (b.getType() == Ball.BallType.WICKET) wktInOver[i] = true;
            }
            runsPerOver[i] = total;
            if (total > maxRuns) maxRuns = total;
        }

        int axisL = 110, axisR = CHART_W - 60, axisT = 90, axisB = CHART_H - 80;
        Paint axisPaint = makePaint(COLOR_AXIS, 2, false);
        Paint gridPaint = makePaint(COLOR_GRID, 1, false);
        c.drawLine(axisL, axisB, axisR, axisB, axisPaint);
        c.drawLine(axisL, axisT, axisL, axisB, axisPaint);

        // Y grid
        Paint yLbl = makePaint(COLOR_LBL, AXIS_TS, false);
        yLbl.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 5; i++) {
            int y = axisB - (int) ((axisB - axisT) * (i / 5f));
            c.drawLine(axisL, y, axisR, y, gridPaint);
            c.drawText(String.valueOf(Math.round(maxRuns * i / 5f)), axisL - 8, y + 8, yLbl);
        }

        // Bars — centres distributed from axisL to axisR so over 1 aligns with Y-axis
        float barSlot = n > 1 ? (axisR - axisL) / (float) (n - 1) : (axisR - axisL);
        float barW    = Math.min(barSlot * 0.65f, 60);
        Paint barPaint = makePaint(COLOR_GREEN, 0, false);
        Paint wktPaint = makePaint(COLOR_RED, 0, false);

        Paint xLbl = makePaint(COLOR_LBL, AXIS_TS, false);
        xLbl.setTextAlign(Paint.Align.CENTER);
        int xStep = n <= 10 ? 1 : (n <= 20 ? 2 : 5);

        for (int i = 0; i < n; i++) {
            float cx = n == 1 ? (axisL + axisR) / 2f
                    : axisL + (axisR - axisL) * (i / (float) (n - 1));
            float h  = (axisB - axisT) * (runsPerOver[i] / (float) maxRuns);
            RectF r  = new RectF(cx - barW / 2, axisB - h, cx + barW / 2, axisB);
            c.drawRoundRect(r, 4, 4, barPaint);

            // Runs count above bar
            Paint runLbl = makePaint(COLOR_GREEN, AXIS_TS - 2, true);
            runLbl.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.valueOf(runsPerOver[i]), cx, axisB - h - 10, runLbl);

            if (wktInOver[i]) {
                c.drawCircle(cx, axisB - h - 30, 9, wktPaint);
            }

            if (i % xStep == 0 || i == n - 1) {
                c.drawText(String.valueOf(i + 1), cx, axisB + 32, xLbl);
            }
        }
        c.drawText("Over", (axisL + axisR) / 2f, CHART_H - 20, xLbl);

        // Wicket legend
        Paint legTxt = makePaint(COLOR_TEXT, AXIS_TS, false);
        legTxt.setTextAlign(Paint.Align.LEFT);
        c.drawCircle(axisR - 240, axisT + 22, 9, wktPaint);
        c.drawText("Wicket in over", axisR - 220, axisT + 30, legTxt);

        return bmp;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static String inningsLabel(Match match, int innings) {
        String bf = match.getBattingFirstTeam();
        String hm = match.getHomeTeamName();
        String aw = match.getAwayTeamName();
        if (bf == null) return "Innings " + innings;
        boolean homeFirst = bf.equals(hm);
        if (innings == 1) return homeFirst ? hm : aw;
        return homeFirst ? aw : hm;
    }

    private static Paint makePaint(int color, float textSize, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        if (textSize > 0) p.setTextSize(textSize);
        if (bold) p.setFakeBoldText(true);
        return p;
    }
}
