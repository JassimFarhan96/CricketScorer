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
 * Renders the same charts that DeepStatsActivity draws on screen, but
 * straight to Bitmaps instead of a View. Used by both:
 *   - MatchExcelExporter (to embed PNGs in Sheet 2 of the XLSX)
 *   - MatchImageExporter (PNG composite for image share)
 *
 * Why not reuse the View classes directly?
 * The view classes are private inner classes of DeepStatsActivity tied to
 * its lifecycle. Reusing them means inflating the entire DeepStatsActivity
 * off-screen which is fragile. The chart data itself is just simple
 * cumulative-runs and over-by-over series — re-implementing them as
 * pure drawing logic is ~80 lines and keeps the export decoupled from
 * the activity.
 */
public final class ChartBitmapRenderer {

    private ChartBitmapRenderer() {}

    private static final int TITLE_TS    = 32;
    private static final int LABEL_TS    = 24;
    private static final int AXIS_TS     = 20;
    private static final int CHART_W     = 1100;
    private static final int CHART_H     = 600;

    /**
     * Cumulative-runs-vs-overs line chart. One line per innings, scaled to
     * the longer innings. White background, axis labels in dark gray.
     *
     * Returns a 1100×600 ARGB_8888 bitmap (caller is responsible for
     * recycle()).
     */
    public static Bitmap renderRunRateChart(Context ctx, Match match) {
        Bitmap bmp = Bitmap.createBitmap(CHART_W, CHART_H, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.parseColor("#0F4E3D"));
        title.setTextSize(TITLE_TS);
        title.setFakeBoldText(true);
        c.drawText("Cumulative runs by over", 40, 50, title);

        // Build cumulative-runs series for each innings present
        List<float[]> seriesPerInnings = new ArrayList<>();
        if (match.getFirstInnings() != null) {
            seriesPerInnings.add(buildCumulativeSeries(match.getFirstInnings()));
        }
        if (match.getSecondInnings() != null) {
            seriesPerInnings.add(buildCumulativeSeries(match.getSecondInnings()));
        }
        if (seriesPerInnings.isEmpty()) return bmp;

        // Axes
        int axisL = 100, axisR = CHART_W - 40, axisT = 90, axisB = CHART_H - 80;
        Paint axis = new Paint();
        axis.setColor(Color.parseColor("#888888"));
        axis.setStrokeWidth(2);
        c.drawLine(axisL, axisB, axisR, axisB, axis);
        c.drawLine(axisL, axisT, axisL, axisB, axis);

        // Max runs across all series for Y scale; max overs for X scale
        float maxRuns = 1;
        int   maxOvs  = 1;
        for (float[] s : seriesPerInnings) {
            if (s.length > maxOvs) maxOvs = s.length;
            for (float v : s) if (v > maxRuns) maxRuns = v;
        }
        maxOvs = Math.max(maxOvs - 1, 1);

        // Y grid lines + labels
        Paint grid = new Paint();
        grid.setColor(Color.parseColor("#EEEEEE"));
        Paint lbl  = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbl.setColor(Color.parseColor("#555555"));
        lbl.setTextSize(AXIS_TS);
        for (int i = 0; i <= 5; i++) {
            int y = axisB - (int) ((axisB - axisT) * (i / 5f));
            c.drawLine(axisL, y, axisR, y, grid);
            int val = Math.round(maxRuns * i / 5f);
            c.drawText(String.valueOf(val), 40, y + 8, lbl);
        }
        // X axis labels (over numbers, 0..maxOvs)
        for (int i = 0; i <= Math.min(maxOvs, 10); i++) {
            int x = axisL + (int) ((axisR - axisL) * (i / (float) maxOvs));
            int ovNum = Math.round(maxOvs * (i / (float) Math.min(maxOvs, 10)));
            c.drawText(String.valueOf(ovNum), x - 10, axisB + 32, lbl);
        }
        c.drawText("Overs", (axisL + axisR) / 2 - 30, CHART_H - 20, lbl);

        // Draw each series
        int[] colors = { Color.parseColor("#0F4E3D"), Color.parseColor("#C44536") };
        String[] names = {
                inningsLabel(match, 1),
                inningsLabel(match, 2)
        };
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStrokeWidth(4);
        line.setStyle(Paint.Style.STROKE);

        for (int idx = 0; idx < seriesPerInnings.size(); idx++) {
            line.setColor(colors[idx % colors.length]);
            float[] s = seriesPerInnings.get(idx);
            float prevX = axisL, prevY = axisB;
            for (int i = 0; i < s.length; i++) {
                float x = axisL + (axisR - axisL) * (i / (float) maxOvs);
                float y = axisB - (axisB - axisT) * (s[i] / maxRuns);
                if (i > 0) c.drawLine(prevX, prevY, x, y, line);
                prevX = x; prevY = y;
            }
            // Legend dot + label
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(colors[idx % colors.length]);
            int lx = axisR - 250, ly = axisT + 20 + idx * 32;
            c.drawCircle(lx, ly, 8, fill);
            Paint legTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
            legTxt.setColor(Color.parseColor("#333333"));
            legTxt.setTextSize(LABEL_TS);
            c.drawText(names[idx], lx + 20, ly + 8, legTxt);
        }
        return bmp;
    }

    /**
     * Over-by-over bar chart (similar to OverBarView in DeepStatsActivity).
     * One column per over, height = runs in that over. Red marker dot on
     * top of overs where at least one wicket fell.
     *
     * @param innings which innings to render (1 or 2)
     */
    public static Bitmap renderOverBarChart(Context ctx, Match match, int innings) {
        Innings inn = innings == 1 ? match.getFirstInnings() : match.getSecondInnings();
        Bitmap bmp = Bitmap.createBitmap(CHART_W, CHART_H, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.parseColor("#0F4E3D"));
        title.setTextSize(TITLE_TS);
        title.setFakeBoldText(true);
        String header = "Over-by-over runs — " + inningsLabel(match, innings);
        c.drawText(header, 40, 50, title);

        if (inn == null) return bmp;

        List<Over> overs = inn.getAllOvers();
        int n = overs.size();
        if (n == 0) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.parseColor("#888888"));
            p.setTextSize(LABEL_TS);
            c.drawText("No overs bowled yet", 40, CHART_H / 2, p);
            return bmp;
        }

        // Axes
        int axisL = 100, axisR = CHART_W - 40, axisT = 90, axisB = CHART_H - 80;
        int maxRuns = 1;
        int[] runsPerOver = new int[n];
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

        Paint axis = new Paint();
        axis.setColor(Color.parseColor("#888888"));
        axis.setStrokeWidth(2);
        c.drawLine(axisL, axisB, axisR, axisB, axis);
        c.drawLine(axisL, axisT, axisL, axisB, axis);

        Paint lbl = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbl.setColor(Color.parseColor("#555555"));
        lbl.setTextSize(AXIS_TS);
        // Y grid
        Paint grid = new Paint();
        grid.setColor(Color.parseColor("#EEEEEE"));
        for (int i = 0; i <= 5; i++) {
            int y = axisB - (int) ((axisB - axisT) * (i / 5f));
            c.drawLine(axisL, y, axisR, y, grid);
            int val = Math.round(maxRuns * i / 5f);
            c.drawText(String.valueOf(val), 40, y + 8, lbl);
        }

        // Bars
        float barSlot = (axisR - axisL) / (float) n;
        float barW = Math.min(barSlot * 0.7f, 50);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(Color.parseColor("#0F4E3D"));
        Paint wktDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        wktDot.setColor(Color.parseColor("#C44536"));
        for (int i = 0; i < n; i++) {
            float cx = axisL + barSlot * (i + 0.5f);
            float h = (axisB - axisT) * (runsPerOver[i] / (float) maxRuns);
            RectF r = new RectF(cx - barW / 2, axisB - h, cx + barW / 2, axisB);
            c.drawRoundRect(r, 4, 4, bar);
            if (wktInOver[i]) {
                c.drawCircle(cx, axisB - h - 16, 9, wktDot);
            }
            // Over number label (every over if <=10, else every 2-3 overs)
            int step = n <= 10 ? 1 : (n <= 20 ? 2 : 5);
            if (i % step == 0 || i == n - 1) {
                c.drawText(String.valueOf(i + 1), cx - 8, axisB + 32, lbl);
            }
        }
        c.drawText("Over", (axisL + axisR) / 2 - 30, CHART_H - 20, lbl);

        // Legend: wicket marker
        Paint legTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        legTxt.setColor(Color.parseColor("#333333"));
        legTxt.setTextSize(AXIS_TS);
        c.drawCircle(axisR - 200, axisT + 20, 9, wktDot);
        c.drawText("Wicket in over", axisR - 180, axisT + 28, legTxt);

        return bmp;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static float[] buildCumulativeSeries(Innings inn) {
        List<Over> overs = inn.getAllOvers();
        float[] s = new float[overs.size() + 1];
        s[0] = 0;
        int cumulative = 0;
        for (int i = 0; i < overs.size(); i++) {
            for (Ball b : overs.get(i).getBalls()) {
                cumulative += b.getRuns();
            }
            s[i + 1] = cumulative;
        }
        return s;
    }

    private static String inningsLabel(Match match, int innings) {
        String bf = match.getBattingFirstTeam();
        String hf = match.getHomeTeamName();
        String aw = match.getAwayTeamName();
        if (bf == null) return "Innings " + innings;
        boolean homeFirst = bf.equals(hf);
        if (innings == 1) return homeFirst ? hf : aw;
        return homeFirst ? aw : hf;
    }
}
