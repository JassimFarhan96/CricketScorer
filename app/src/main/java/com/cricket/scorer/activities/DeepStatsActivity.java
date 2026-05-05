package com.cricket.scorer.activities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.BowlerStat;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.utils.MatchStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DeepStatsActivity.java
 *
 * CHANGE — Run Rate Chart redesigned to match the reference image:
 *
 * The chart now shows CUMULATIVE RUNS scored after each over
 * (not per-over run rate). This is the standard cricket "wagon wheel"
 * / run progression chart used by broadcasters:
 *
 *   Y-axis: total runs (0 → max score + headroom)
 *   X-axis: overs (1, 2, 3 … N)
 *   Line:   smooth connected line through each over's cumulative total
 *   Dots:   filled circle at each over data point
 *   Both innings overlaid on same chart for direct comparison
 *
 * Design matches the reference:
 *   - Clean white axis lines
 *   - Horizontal dashed grid lines
 *   - Labelled dots at each over
 *   - Axis labels ("Overs", run values)
 *   - Legend box top-right
 */
public class DeepStatsActivity extends BaseNavActivity {

    public static final String EXTRA_SAVED_FILE_NAME = "deep_stats_file";

    private Match match;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String savedFile = getIntent().getStringExtra(EXTRA_SAVED_FILE_NAME);
        if (savedFile != null) {
            for (Match m : MatchStorage.loadAllMatches(this))
                if (savedFile.equals(m.getSavedFileName())) { match = m; break; }
        } else {
            match = ((CricketApp) getApplication()).getCurrentMatch();
        }

        if (match == null) { finish(); return; }
        buildUI();
    }

    // ─── Build UI ─────────────────────────────────────────────────────────────

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(col(R.color.c_bg_page));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        String  bat1      = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        String  bat2      = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();
        Innings i1        = match.getFirstInnings();
        Innings i2        = match.getSecondInnings();

        // Colours for the two innings lines
        int color1 = Color.parseColor("#1D9E75"); // green for innings 1
        int color2 = Color.parseColor("#378ADD"); // blue for innings 2

        // 1. Header
        root.addView(buildHeader(bat1, bat2, i1, i2));

        // 2. Run rate progression chart
        root.addView(sectionLabel("RUN RATE PROGRESSION (Average Run Rate per Over)"));
        root.addView(buildCumulativeChart(bat1, bat2, i1, i2, color1, color2));

        // 3. Batting highlights
        root.addView(sectionLabel("BATTING HIGHLIGHTS"));
        root.addView(buildBattingHighlights(mergeAllBatters()));

        // 4. Bowling highlights
        root.addView(sectionLabel("BOWLING HIGHLIGHTS"));
        root.addView(buildBowlingHighlights(mergeAllBowlers(bat2, bat1, i1, i2)));

        // 5. Over-by-over run bars
        root.addView(sectionLabel("OVER-BY-OVER RUNS"));
        if (i1 != null) root.addView(buildOverChart(bat1, i1, color1));
        if (i2 != null) root.addView(buildOverChart(bat2, i2, color2));

        View pad = new View(this);
        pad.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        root.addView(pad);

        setNavContentView(R.layout.activity_deep_stats_placeholder);
        // DeepStatsActivity builds its UI programmatically.
        // Replace the placeholder frame content with our scroll view.
        android.widget.FrameLayout frame = findViewById(R.id.nav_content_frame);
        frame.removeAllViews();
        frame.addView(scroll);
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private View buildHeader(String bat1, String bat2, Innings i1, Innings i2) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackgroundColor(col(R.color.green_dark));
        h.setPadding(dp(20), dp(36), dp(20), dp(20));
        h.setGravity(Gravity.CENTER);

        TextView lbl = tv("IN-DEPTH STATISTICS", 10f, col(R.color.green_light), false);
        lbl.setLetterSpacing(0.12f); h.addView(lbl);

        TextView match_ = tv(match.getHomeTeamName() + " vs " + match.getAwayTeamName(),
                20f, Color.WHITE, true);
        match_.setLayoutParams(mpTop(8)); h.addView(match_);

        if (match.getResultDescription() != null)
            h.addView(tv(match.getResultDescription(), 13f, col(R.color.green_light), false));

        // Score row
        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams srp = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srp.setMargins(0, dp(16), 0, 0); scoreRow.setLayoutParams(srp);

        if (i1 != null) scoreRow.addView(miniScore(bat1, i1.getScoreString(), i1.getOversString()));
        View div = new View(this); div.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(40)));
        div.setBackgroundColor(Color.parseColor("#33FFFFFF")); scoreRow.addView(div);
        if (i2 != null) scoreRow.addView(miniScore(bat2, i2.getScoreString(), i2.getOversString()));

        h.addView(scoreRow);
        return h;
    }

    private View miniScore(String team, String score, String overs) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.setPadding(dp(8), dp(8), dp(8), dp(8));
        c.addView(tv(team.toUpperCase(), 10f, col(R.color.green_light), false));
        c.addView(tv(score, 22f, Color.WHITE, true));
        c.addView(tv(overs + " ov", 11f, Color.parseColor("#AADECE"), false));
        return c;
    }

    // ─── Cumulative Runs Chart ────────────────────────────────────────────────

    /**
     * Builds the main chart card — a CumulativeRunsChartView inside a
     * CardView, with a legend below it.
     */
    private View buildCumulativeChart(String bat1, String bat2,
                                       Innings i1, Innings i2,
                                       int color1, int color2) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.setMargins(dp(12), 0, dp(12), 0); wrapper.setLayoutParams(wp);

        // Chart canvas
        CumulativeRunsChartView chart = new CumulativeRunsChartView(
                this, i1, i2, color1, color2, bat1, bat2);
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        wrapper.addView(roundCard(chart));

        // Legend row below chart
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(12)); legend.setLayoutParams(lp);
        if (i1 != null) legend.addView(legendItem(bat1, color1));
        if (i2 != null) legend.addView(legendItem(bat2, color2));

        wrapper.addView(legend);
        return wrapper;
    }

    private View legendItem(String label, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(dp(16), 0, dp(16), 0); row.setLayoutParams(rp);

        // Coloured dot
        View dot = new View(this);
        LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(dp(12), dp(12));
        dp_.setMargins(0, 0, dp(6), 0); dot.setLayoutParams(dp_);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(color); dot.setBackground(gd);
        row.addView(dot);

        row.addView(tv(label, 12f, col(R.color.c_text_secondary), false));
        return row;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CumulativeRunsChartView — plots AVERAGE RUN RATE after each over
    //
    // Y-axis: Run rate (total runs ÷ overs completed so far)
    //         e.g. after over 3 with 36 runs → run rate = 36/3 = 12.0
    // X-axis: Overs (1, 2, 3 … N)
    // Both innings overlaid. Dots at each over, value labels above dots.
    // ═════════════════════════════════════════════════════════════════════════

    static class CumulativeRunsChartView extends View {

        private final Innings i1, i2;
        private final int     c1, c2;
        private final String  name1, name2;

        private float   padL, padR, padT, padB;
        private float   chartW, chartH;
        private float   maxRR;   // max run rate across both innings
        private int     maxOvers;
        private float[] rrData1, rrData2; // run rate at each over

        CumulativeRunsChartView(Context ctx, Innings i1, Innings i2,
                                 int c1, int c2, String n1, String n2) {
            super(ctx);
            this.i1 = i1; this.i2 = i2;
            this.c1 = c1; this.c2 = c2;
            this.name1 = n1; this.name2 = n2;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float d = getContext().getResources().getDisplayMetrics().density;
            padL = 54 * d; padR = 20 * d; padT = 24 * d; padB = 40 * d;
            chartW = w - padL - padR;
            chartH = h - padT - padB;

            rrData1 = buildRunRates(i1);
            rrData2 = buildRunRates(i2);

            // Find max run rate — round up to nearest 2 for clean grid
            maxRR = 2f;
            for (float r : rrData1) maxRR = Math.max(maxRR, r);
            for (float r : rrData2) maxRR = Math.max(maxRR, r);
            maxRR = (float)(Math.ceil((maxRR + 2) / 2.0) * 2); // round to next even

            maxOvers = Math.max(
                    i1 != null ? i1.getAllOvers().size() : 0,
                    i2 != null ? i2.getAllOvers().size() : 0);
            if (maxOvers == 0) maxOvers = 1;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (maxOvers == 0) return;
            canvas.drawColor(col(R.color.c_bg_card));
            drawGrid(canvas);
            drawAxes(canvas);
            if (rrData2 != null && rrData2.length > 0) drawLine(canvas, rrData2, c2);
            if (rrData1 != null && rrData1.length > 0) drawLine(canvas, rrData1, c1);
            drawAxisLabels(canvas);
        }

        /** Dashed horizontal grid lines with Y run-rate labels */
        private void drawGrid(Canvas canvas) {
            Paint gridPaint = new Paint();
            gridPaint.setColor(Color.parseColor("#22FFFFFF"));
            gridPaint.setStrokeWidth(1f);
            gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));

            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(Color.parseColor("#999999"));
            labelPaint.setTextSize(dp(9));
            labelPaint.setTextAlign(Paint.Align.RIGHT);

            // Grid every 2 run-rate units (e.g. 0, 2, 4, 6 … maxRR)
            float step = 2f;
            for (float val = 0; val <= maxRR; val += step) {
                float y = yFor(val);
                canvas.drawLine(padL, y, padL + chartW, y, gridPaint);
                // Label: e.g. "6.0"
                canvas.drawText(String.format(Locale.US, "%.0f", val),
                        padL - dp(6), y + dp(4), labelPaint);
            }
        }

        /** X and Y axis lines */
        private void drawAxes(Canvas canvas) {
            Paint p = new Paint();
            p.setColor(Color.parseColor("#AAAAAA"));
            p.setStrokeWidth(dp(1.5f));
            canvas.drawLine(padL, padT, padL, padT + chartH, p);         // Y
            canvas.drawLine(padL, padT + chartH, padL + chartW, padT + chartH, p); // X
        }

        /** "Overs" label + numbered over ticks on X-axis */
        private void drawAxisLabels(Canvas canvas) {
            // "Run Rate" Y-axis title (rotated) — drawn as plain label at top-left
            Paint rrTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
            rrTitle.setColor(Color.parseColor("#BBBBBB"));
            rrTitle.setTextSize(dp(9));
            rrTitle.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Run Rate", dp(2), padT - dp(6), rrTitle);

            // "Overs" centred under X axis
            Paint overTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
            overTitle.setColor(Color.parseColor("#BBBBBB"));
            overTitle.setTextSize(dp(10));
            overTitle.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Overs", padL + chartW / 2, padT + chartH + dp(32), overTitle);

            // Individual over numbers
            Paint xLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
            xLabel.setColor(Color.parseColor("#999999"));
            xLabel.setTextSize(dp(9));
            xLabel.setTextAlign(Paint.Align.CENTER);

            int labelStep = Math.max(1, maxOvers / 8);
            for (int ov = 1; ov <= maxOvers; ov += labelStep) {
                float x = xFor(ov);
                canvas.drawText(String.valueOf(ov), x, padT + chartH + dp(16), xLabel);
                // Tick
                Paint tick = new Paint();
                tick.setColor(Color.parseColor("#555555")); tick.setStrokeWidth(dp(1));
                canvas.drawLine(x, padT + chartH, x, padT + chartH + dp(4), tick);
            }
        }

        /**
         * Draws one innings run-rate line with:
         *   - Gradient fill below
         *   - Solid coloured line
         *   - Filled dots at each over
         *   - Run-rate value above each dot (hidden for long innings to avoid clutter)
         */
        private void drawLine(Canvas canvas, float[] data, int color) {
            if (data.length == 0) return;

            Path linePath = new Path();
            Path fillPath = new Path();
            fillPath.moveTo(padL, padT + chartH);

            for (int i = 0; i < data.length; i++) {
                float x = xFor(i + 1);
                float y = yFor(data[i]);
                if (i == 0) linePath.moveTo(x, y); else linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            fillPath.lineTo(xFor(data.length), padT + chartH);
            fillPath.close();

            // Gradient fill
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setShader(new LinearGradient(0, padT, 0, padT + chartH,
                    adjustAlpha(color, 90), adjustAlpha(color, 0), Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);

            // Line
            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setColor(color);
            linePaint.setStrokeWidth(dp(2.5f));
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawPath(linePath, linePaint);

            // Dots
            Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotFill.setStyle(Paint.Style.FILL);
            dotFill.setColor(color);
            Paint dotBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotBorder.setStyle(Paint.Style.STROKE);
            dotBorder.setColor(Color.parseColor("#1A1A1A"));
            dotBorder.setStrokeWidth(dp(1.5f));

            Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valPaint.setColor(color);
            valPaint.setTextSize(dp(8));
            valPaint.setTextAlign(Paint.Align.CENTER);
            valPaint.setTypeface(Typeface.DEFAULT_BOLD);

            // Show value labels only when overs are few enough to avoid overlap
            boolean showLabels = data.length <= 10;

            for (int i = 0; i < data.length; i++) {
                float x = xFor(i + 1);
                float y = yFor(data[i]);
                canvas.drawCircle(x, y, dp(4), dotFill);
                canvas.drawCircle(x, y, dp(4), dotBorder);
                if (showLabels) {
                    // Format: "12.0" — one decimal place
                    canvas.drawText(String.format(Locale.US, "%.1f", data[i]),
                            x, y - dp(8), valPaint);
                }
            }
        }

        // ── Coordinate helpers ────────────────────────────────────────────────

        /** X pixel for over number (1-based), centred in each over slot */
        private float xFor(int over) {
            return padL + ((float)(over - 1) / (maxOvers - 1 == 0 ? 1 : maxOvers - 1)) * chartW;
        }

        /** Y pixel for a given run rate value */
        private float yFor(float runRate) {
            return padT + chartH - (runRate / maxRR) * chartH;
        }

        /**
         * Builds the average run rate at the END of each completed over.
         *
         * Formula: runRate[i] = totalRuns after over (i+1) ÷ (i+1)
         *
         * Example — 3 overs scoring 6, 12, 18 runs:
         *   over 1: 6 runs total ÷ 1 = 6.0 rpo
         *   over 2: 18 runs total ÷ 2 = 9.0 rpo
         *   over 3: 36 runs total ÷ 3 = 12.0 rpo
         */
        private float[] buildRunRates(Innings inn) {
            if (inn == null) return new float[0];
            // getAllOvers() includes the partial current over so matches that end
            // mid-over (e.g. 2nd innings chase completed on ball 5) still show data
            List<Over> overs = inn.getAllOvers();
            if (overs.isEmpty()) return new float[0];
            float[] rates  = new float[overs.size()];
            int     cumRuns = 0;
            int     totalValidBalls = inn.getTotalValidBalls();
            for (int i = 0; i < overs.size(); i++) {
                cumRuns += overs.get(i).getTotalRuns();
                // For completed overs use full over count; for partial last over
                // use actual balls faced so run rate is accurate, not inflated
                boolean isLastOver   = (i == overs.size() - 1);
                boolean isPartial    = isLastOver && overs.get(i).getValidBallCount() < 6;
                float   oversDecimal = isPartial
                        ? totalValidBalls / 6f          // e.g. 5 balls = 0.833 overs
                        : (i + 1);                      // completed: 1, 2, 3...
                rates[i] = oversDecimal > 0 ? cumRuns / oversDecimal : 0f;
            }
            return rates;
        }

        private int adjustAlpha(int color, int alpha) {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }
        private int col(int res) {
            return getContext().getResources().getColor(res, getContext().getTheme());
        }
        private int dp(float v) {
            return (int)(v * getContext().getResources().getDisplayMetrics().density);
        }
    }

    // ─── Batting highlights ───────────────────────────────────────────────────

    private View buildBattingHighlights(List<Player> players) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), 0, dp(12), 0);

        Player topScorer = null, mostSixes = null, mostFours = null, bestSR = null;
        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0) continue;
            if (topScorer == null || p.getRunsScored() > topScorer.getRunsScored()) topScorer = p;
            if (mostSixes  == null || p.getSixes()      > mostSixes.getSixes())      mostSixes  = p;
            if (mostFours  == null || p.getFours()       > mostFours.getFours())      mostFours  = p;
            if (p.getBallsFaced() >= 3 && (bestSR == null || p.getStrikeRate() > bestSR.getStrikeRate())) bestSR = p;
        }

        LinearLayout r1 = row(), r2 = row();
        r1.addView(highlight("🏆","Top Scorer",      topScorer,  topScorer  != null ? topScorer.getRunsScored()+" runs" : ""));
        r1.addView(highlight("⚡","Best Strike Rate", bestSR,     bestSR     != null ? String.format(Locale.US,"%.1f",bestSR.getStrikeRate()) : ""));
        r2.addView(highlight("6️⃣","Most Sixes",       mostSixes,  mostSixes  != null ? mostSixes.getSixes()+" sixes" : ""));
        r2.addView(highlight("4️⃣","Most Fours",       mostFours,  mostFours  != null ? mostFours.getFours()+" fours" : ""));
        grid.addView(r1); grid.addView(r2);
        return grid;
    }

    private View highlight(String emoji, String title, Player p, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(4), dp(4), dp(4), dp(4)); card.setLayoutParams(cp);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(col(R.color.c_bg_card)); bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), col(R.color.c_border)); card.setBackground(bg);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(tv(emoji, 22f, 0, false));
        TextView lbl = tv(title, 10f, col(R.color.c_text_secondary), false); lbl.setLayoutParams(mpTop(6)); card.addView(lbl);
        TextView nm  = tv(p != null ? p.getName() : "-", 14f, col(R.color.c_text_primary), true); nm.setLayoutParams(mpTop(4)); card.addView(nm);
        card.addView(tv(value, 12f, col(R.color.green_mid), false));
        return card;
    }

    // ─── Bowling highlights ───────────────────────────────────────────────────

    private View buildBowlingHighlights(List<BowlerStatWithTeam> bowlers) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), 0, dp(12), 0);

        BowlerStatWithTeam mostWkts = null, bestEcon = null, mostOv = null;
        for (BowlerStatWithTeam b : bowlers) {
            if (mostWkts == null || b.stat.getWickets() > mostWkts.stat.getWickets()) mostWkts = b;
            if (b.stat.getBalls() > 0 && (bestEcon == null || b.stat.getEconomy() < bestEcon.stat.getEconomy())) bestEcon = b;
            if (mostOv  == null || b.stat.getBalls()  > mostOv.stat.getBalls())  mostOv  = b;
        }
        LinearLayout r1 = row(), r2 = row();
        r1.addView(bowlHighlight("🎳","Most Wickets", mostWkts, mostWkts != null ? mostWkts.stat.getWickets()+" wkts" : ""));
        r1.addView(bowlHighlight("💰","Best Economy",  bestEcon, bestEcon != null ? String.format(Locale.US,"%.2f",bestEcon.stat.getEconomy()) : ""));
        r2.addView(bowlHighlight("📋","Most Overs",    mostOv,  mostOv  != null ? mostOv.stat.getOversString()+" ov" : ""));
        r2.addView(emptyCard());
        grid.addView(r1); grid.addView(r2);
        return grid;
    }

    private View bowlHighlight(String emoji, String title, BowlerStatWithTeam b, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(4), dp(4), dp(4), dp(4)); card.setLayoutParams(cp);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(col(R.color.c_bg_card)); bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), col(R.color.c_border)); card.setBackground(bg);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(tv(emoji, 22f, 0, false));
        TextView lbl = tv(title, 10f, col(R.color.c_text_secondary), false); lbl.setLayoutParams(mpTop(6)); card.addView(lbl);
        TextView nm  = tv(b != null ? b.stat.getName() : "-", 14f, col(R.color.c_text_primary), true); nm.setLayoutParams(mpTop(4)); card.addView(nm);
        card.addView(tv(value, 12f, col(R.color.green_mid), false));
        return card;
    }

    private View emptyCard() {
        View v = new View(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
        p.setMargins(dp(4),dp(4),dp(4),dp(4)); v.setLayoutParams(p); return v;
    }

    // ─── Over-by-over bar chart ───────────────────────────────────────────────

    /**
     * Builds a tappable summary card for one team's over-by-over runs.
     * Shows the first 3 overs as a preview. Tapping the card (or the
     * "View all N overs →" button) opens a full-screen dialog with the
     * complete list, both vertically and horizontally scrollable, plus
     * a close button.
     */
    private View buildOverChart(String teamName, Innings innings, int barColor) {
        // getAllOvers() includes the partial current over so a chase completed
        // mid-over still appears in the bar chart
        List<Over> overs = innings.getAllOvers();

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = lp(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.setMargins(dp(12), 0, dp(12), dp(12));
        wrapper.setLayoutParams(wp);

        // Sub-label
        TextView lbl = tv(teamName.toUpperCase() + " — RUNS PER OVER",
                10f, col(R.color.c_text_secondary), false);
        lbl.setPadding(0, dp(4), 0, dp(6));
        wrapper.addView(lbl);

        if (overs.isEmpty()) {
            wrapper.addView(tv("No overs recorded", 12f, col(R.color.c_text_hint), false));
            return wrapper;
        }

        // Find max runs for bar scaling
        int maxRuns = 1;
        for (Over ov : overs) maxRuns = Math.max(maxRuns, ov.getTotalRuns());
        final int maxRunsFinal = maxRuns;

        // Card that holds the preview + tap target
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setRadius(dp(12));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(col(R.color.c_bg_card));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cardContent.setPadding(dp(12), dp(10), dp(12), dp(10));

        // Preview: show first 4 overs (or all if ≤4)
        int previewCount = Math.min(overs.size(), 4);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int trackW = screenW - dp(52) - dp(56) - dp(40); // label + value + card padding

        for (int idx = 0; idx < previewCount; idx++) {
            cardContent.addView(buildOverRow(overs.get(idx), idx, maxRunsFinal, trackW, barColor));
        }

        // "View all" row
        LinearLayout viewAllRow = new LinearLayout(this);
        viewAllRow.setOrientation(LinearLayout.HORIZONTAL);
        viewAllRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams varp = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        varp.setMargins(0, dp(8), 0, dp(2));
        viewAllRow.setLayoutParams(varp);

        View divLine = new View(this);
        LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(0, dp(1), 1f);
        divLine.setLayoutParams(divP);
        divLine.setBackgroundColor(col(R.color.c_divider));
        viewAllRow.addView(divLine);

        String btnLabel = overs.size() <= 4
                ? "Tap to view full details"
                : "View all " + overs.size() + " overs  →";
        TextView tvViewAll = tv(btnLabel, 12f, col(R.color.green_mid), true);
        LinearLayout.LayoutParams tvP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvP.setMargins(dp(12), 0, 0, 0);
        tvViewAll.setLayoutParams(tvP);
        viewAllRow.addView(tvViewAll);

        cardContent.addView(viewAllRow);
        card.addView(cardContent);
        wrapper.addView(card);

        // Tap anywhere on card to open full popup
        card.setOnClickListener(v ->
                showOverPopup(teamName, overs, maxRunsFinal, barColor));

        return wrapper;
    }

    /**
     * Builds a single over row: [Ov N] [====bar====] [runs]
     * Reused in both the preview card and the full popup.
     */
    private View buildOverRow(Over ov, int index, int maxRuns, int trackW, int barColor) {
        int rowH = dp(42);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Use WRAP_CONTENT so the row can be wider than the viewport
        // (required for HorizontalScrollView to actually scroll)
        LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, rowH);
        rowP.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowP);
        if (index % 2 == 0) row.setBackgroundColor(col(R.color.c_row_alt_bg));
        // Minimum width = label + bar + runs so background fills the card in preview
        row.setMinimumWidth(dp(52) + trackW + dp(56));

        // Over label
        TextView tvOv = tv("Ov " + ov.getOverNumber(), 11f, col(R.color.c_text_secondary), false);
        tvOv.setLayoutParams(new LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
        tvOv.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(tvOv);

        // Calculate filled bar width
        int barFilledW = maxRuns > 0
                ? (int)((float) ov.getTotalRuns() / maxRuns * trackW) : 0;

        // Custom Canvas view: draws track + bar + wicket circles
        OverBarView barView = new OverBarView(this, ov, barColor, trackW, barFilledW);
        LinearLayout.LayoutParams bfP = new LinearLayout.LayoutParams(trackW, dp(30));
        bfP.setMargins(dp(4), 0, dp(8), 0);
        barView.setLayoutParams(bfP);
        row.addView(barView);

        // Runs value + wicket count if any
        int wkts = ov.getWickets();
        String runsLabel = String.valueOf(ov.getTotalRuns());
        if (wkts > 0) runsLabel += "\n" + wkts + "W";
        TextView tvRuns = tv(runsLabel, 12f, barColor, true);
        tvRuns.setLayoutParams(new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));
        tvRuns.setGravity(Gravity.CENTER_VERTICAL);
        tvRuns.setLineSpacing(0, 0.85f);
        row.addView(tvRuns);

        return row;
    }

    /**
     * Custom View that draws the bar track, coloured fill, and red wicket circles.
     * Each wicket circle is positioned proportionally along the bar at the ball
     * where the wicket fell, with a "W" label inside.
     */
    private class OverBarView extends View {
        private final Over over;
        private final int  barColor, trackW, barFilledW;
        private final Paint trackP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bordP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textP  = new Paint(Paint.ANTI_ALIAS_FLAG);

        OverBarView(android.content.Context ctx, Over over, int barColor, int trackW, int barFilledW) {
            super(ctx);
            this.over = over; this.barColor = barColor;
            this.trackW = trackW; this.barFilledW = barFilledW;
            trackP.setColor(Color.parseColor("#15888888")); trackP.setStyle(android.graphics.Paint.Style.FILL);
            fillP.setColor(barColor); fillP.setAlpha(210); fillP.setStyle(android.graphics.Paint.Style.FILL);
            circP.setColor(Color.WHITE); circP.setStyle(android.graphics.Paint.Style.FILL);
            bordP.setColor(Color.parseColor("#CC2200")); bordP.setStyle(android.graphics.Paint.Style.STROKE);
            bordP.setStrokeWidth(dp(1.5f));
            textP.setColor(Color.parseColor("#CC2200")); textP.setTextAlign(android.graphics.Paint.Align.CENTER);
            textP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float barTop = h * 0.28f, barBot = h * 0.72f, r = (barBot - barTop) / 2f;
            float circR = h * 0.40f;
            textP.setTextSize(h * 0.34f);

            // Track
            canvas.drawRoundRect(new android.graphics.RectF(0, barTop, trackW, barBot), r, r, trackP);
            // Fill
            if (barFilledW > 0)
                canvas.drawRoundRect(new android.graphics.RectF(0, barTop, barFilledW, barBot), r, r, fillP);

            // Wicket circles — one circle per wicket, positioned by valid ball index.
            // The circle is clamped to the end of the filled bar so it never
            // appears in the grey track area beyond where runs were scored.
            List<com.cricket.scorer.models.Ball> balls = over.getBalls();
            int totalValidBalls = over.getValidBallCount();
            if (balls.isEmpty() || totalValidBalls == 0) return;

            // Max x = right edge of the filled bar (or a minimum if no runs scored)
            float maxCx = barFilledW > 0 ? barFilledW : circR * 2.2f;

            int validBallIndex = 0;
            for (com.cricket.scorer.models.Ball b : balls) {
                if (b.isValid()) validBallIndex++;
                if (b.getType() == com.cricket.scorer.models.Ball.BallType.WICKET) {
                    // Position across the track proportionally to ball index
                    float fraction = (float) validBallIndex / totalValidBalls;
                    float rawCx    = fraction * trackW;
                    // Clamp: circle must not go beyond the bar's right edge
                    // and must not go below circR (left bound)
                    float cx = Math.max(circR, Math.min(maxCx, rawCx));
                    float cy = h / 2f;
                    canvas.drawCircle(cx, cy, circR, circP);
                    canvas.drawCircle(cx, cy, circR, bordP);
                    canvas.drawText("W", cx, cy + textP.getTextSize() * 0.36f, textP);
                }
            }
        }

        private int dp(float v) {
            return (int)(v * getContext().getResources().getDisplayMetrics().density);
        }
    }

    /**
     * Shows a full-screen dialog popup with ALL overs for one team,
     * both vertically and horizontally scrollable.
     * Includes a close (✕) button at the top-right and a "Close" button
     * at the bottom. Tapping outside the content or pressing Back also
     * closes it.
     */
    private void showOverPopup(String teamName, List<Over> overs, int maxRuns, int barColor) {
        // Dialog
        android.app.Dialog dialog = new android.app.Dialog(this, R.style.Theme_CricketScorer);
        dialog.setContentView(buildPopupContent(teamName, overs, maxRuns, barColor, dialog));
        dialog.getWindow().setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setCancelable(true); // back button closes
        dialog.show();
    }

    private View buildPopupContent(String teamName, List<Over> overs,
                                    int maxRuns, int barColor,
                                    android.app.Dialog dialog) {
        // Root: dark semi-transparent overlay → centred card
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.parseColor("#CC000000")); // dim overlay
        root.setOnClickListener(v -> dialog.dismiss()); // tap outside → close

        // Content card (does NOT propagate click to overlay)
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        android.widget.FrameLayout.LayoutParams cp =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(dp(16), dp(60), dp(16), dp(60));
        cp.gravity = Gravity.CENTER_VERTICAL;
        content.setLayoutParams(cp);
        android.graphics.drawable.GradientDrawable contentBg =
                new android.graphics.drawable.GradientDrawable();
        contentBg.setColor(col(R.color.c_bg_card));
        contentBg.setCornerRadius(dp(16));
        content.setBackground(contentBg);
        content.setOnClickListener(v -> { /* consume — don't dismiss */ });

        // ── Header row: title + ✕ close button ────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(16), dp(12), dp(12));
        header.setBackgroundColor(col(R.color.green_dark));
        // Round only top corners
        android.graphics.drawable.GradientDrawable headerBg =
                new android.graphics.drawable.GradientDrawable();
        headerBg.setColor(col(R.color.green_dark));
        headerBg.setCornerRadii(new float[]{
                dp(16), dp(16), dp(16), dp(16), // top-left, top-right
                0, 0, 0, 0                        // bottom-right, bottom-left
        });
        header.setBackground(headerBg);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = tv(teamName.toUpperCase() + " — RUNS PER OVER",
                14f, Color.WHITE, true);
        titleCol.addView(tvTitle);
        TextView tvSub = tv("All " + overs.size() + " over" + (overs.size() == 1 ? "" : "s"),
                11f, Color.parseColor("#AADECE"), false);
        LinearLayout.LayoutParams subP = lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subP.setMargins(0, dp(2), 0, 0); tvSub.setLayoutParams(subP);
        titleCol.addView(tvSub);

        header.addView(titleCol);

        // ✕ close button
        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextSize(20f);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        btnClose.setBackground(null);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        header.addView(btnClose);

        content.addView(header);

        // ── Summary stats row ──────────────────────────────────────────────
        int totalRuns = 0, maxOvRuns = 0, minOvRuns = Integer.MAX_VALUE;
        int maxOvNum = 1, minOvNum = 1;
        for (Over ov : overs) {
            totalRuns += ov.getTotalRuns();
            if (ov.getTotalRuns() > maxOvRuns) { maxOvRuns = ov.getTotalRuns(); maxOvNum = ov.getOverNumber(); }
            if (ov.getTotalRuns() < minOvRuns) { minOvRuns = ov.getTotalRuns(); minOvNum = ov.getOverNumber(); }
        }
        float avg = (float) totalRuns / overs.size();

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(dp(8), dp(10), dp(8), dp(10));
        statsRow.setBackgroundColor(col(R.color.c_bg_card_alt));
        statsRow.addView(miniStat("Total", String.valueOf(totalRuns) + " runs", barColor));
        statsRow.addView(miniStat("Average", String.format(Locale.US, "%.1f / ov", avg), barColor));
        statsRow.addView(miniStat("Best over", "Ov " + maxOvNum + "  (" + maxOvRuns + ")", barColor));
        statsRow.addView(miniStat("Least", "Ov " + minOvNum + "  (" + (minOvRuns == Integer.MAX_VALUE ? 0 : minOvRuns) + ")", barColor));
        content.addView(statsRow);

        // Divider
        View div = new View(this); div.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        div.setBackgroundColor(col(R.color.c_divider)); content.addView(div);

        // ── Scrollable over list (both directions) ─────────────────────────
        int screenW = getResources().getDisplayMetrics().widthPixels;
        // Make bar wider than screen width so horizontal scroll is meaningful.
        // The row = [52dp label] + [barTrackW] + [56dp runs] + [16dp padding]
        // Use 1.5× screen so the bar extends well past the viewport
        int popupTrackW = (int)(screenW * 1.2f) - dp(52) - dp(56) - dp(40);

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(true);
        hScroll.setFillViewport(false);  // must be false for horizontal scroll to work
        hScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Height: show up to 10 rows, then vertical scroll
        int maxRows = Math.min(overs.size(), 10);
        int rowH    = dp(42); // matches updated buildOverRow height
        int listH   = maxRows * rowH + dp(16);

        ScrollView vScroll = new ScrollView(this);
        vScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, listH));
        vScroll.setVerticalScrollBarEnabled(true);

        LinearLayout rowContainer = new LinearLayout(this);
        rowContainer.setOrientation(LinearLayout.VERTICAL);
        rowContainer.setPadding(dp(8), dp(4), dp(8), dp(4));

        for (int i = 0; i < overs.size(); i++) {
            rowContainer.addView(buildOverRow(overs.get(i), i, maxRuns, popupTrackW, barColor));
        }

        vScroll.addView(rowContainer);
        hScroll.addView(vScroll);
        content.addView(hScroll);

        // ── Close button at bottom ─────────────────────────────────────────
        android.widget.Button btnBottom = new android.widget.Button(this);
        btnBottom.setText("Close");
        btnBottom.setTextSize(14f);
        btnBottom.setTextColor(Color.WHITE);
        btnBottom.setTypeface(null, Typeface.BOLD);
        btnBottom.setAllCaps(false);
        LinearLayout.LayoutParams btnP = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnP.setMargins(dp(16), dp(8), dp(16), dp(16));
        btnBottom.setLayoutParams(btnP);
        btnBottom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(col(R.color.green_dark)));
        btnBottom.setOnClickListener(v -> dialog.dismiss());
        content.addView(btnBottom);

        root.addView(content);
        return root;
    }

    /** Small 3-line stat pill used in the popup summary row */
    private View miniStat(String label, String value, int accentColor) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.setPadding(dp(4), dp(4), dp(4), dp(4));
        col.addView(tv(label, 9f, col(R.color.c_text_hint), false));
        TextView tvVal = tv(value, 11f, accentColor, true);
        LinearLayout.LayoutParams vp = lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, dp(2), 0, 0); tvVal.setLayoutParams(vp); tvVal.setGravity(Gravity.CENTER);
        col.addView(tvVal);
        return col;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private View sectionLabel(String text) {
        TextView t = tv(text, 11f, col(R.color.c_text_secondary), false);
        t.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams p = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(16), dp(18), dp(16), dp(6)); t.setLayoutParams(p); return t;
    }

    private View roundCard(View child) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setRadius(dp(12)); card.setCardElevation(dp(1));
        card.setCardBackgroundColor(col(R.color.c_bg_card));
        card.addView(child); return card;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size);
        if (color != 0) t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams mpTop(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(topDp), 0, 0); return p;
    }

    private int col(int res) { return getResources().getColor(res, getTheme()); }
    private int dp(int val)  { return (int)(val * getResources().getDisplayMetrics().density); }

    // ─── Data helpers ─────────────────────────────────────────────────────────

    private List<Player> mergeAllBatters() {
        boolean h = match.getBattingFirstTeam().equals("home");
        List<Player> all = new ArrayList<>();
        if (match.getFirstInnings()  != null) all.addAll(h ? match.getHomePlayers() : match.getAwayPlayers());
        if (match.getSecondInnings() != null) all.addAll(h ? match.getAwayPlayers() : match.getHomePlayers());
        return all;
    }

    private List<BowlerStatWithTeam> mergeAllBowlers(String b1, String b2, Innings i1, Innings i2) {
        List<BowlerStatWithTeam> all = new ArrayList<>();
        if (i1 != null) for (BowlerStat s : i1.getBowlerStats()) all.add(new BowlerStatWithTeam(s, b1));
        if (i2 != null) for (BowlerStat s : i2.getBowlerStats()) all.add(new BowlerStatWithTeam(s, b2));
        return all;
    }

    static class BowlerStatWithTeam {
        final BowlerStat stat; final String team;
        BowlerStatWithTeam(BowlerStat s, String t) { stat = s; team = t; }
    }

    @Override
    protected int getCurrentNavItem() {
        return R.id.nav_stats;
    }

}
