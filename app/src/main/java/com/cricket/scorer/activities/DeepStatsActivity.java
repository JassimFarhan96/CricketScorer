package com.cricket.scorer.activities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
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
 * In-depth match analytics screen. Launched from StatsActivity via
 * "View In-Depth Stats" button, receiving EXTRA_SAVED_FILE_NAME or
 * reading from CricketApp for a live just-completed match.
 *
 * Sections:
 *   1. Match summary banner
 *   2. Run rate progression chart (over-by-over line chart, both innings)
 *   3. Batting highlights:
 *      - Highest strike rate
 *      - Most sixes
 *      - Most fours
 *      - Top scorer
 *   4. Bowling highlights:
 *      - Most wickets
 *      - Best economy
 *      - Most overs bowled
 *   5. Over-by-over run breakdown (horizontal bar chart)
 */
public class DeepStatsActivity extends AppCompatActivity {

    public static final String EXTRA_SAVED_FILE_NAME = "deep_stats_file";

    private Match match;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load match data
        String savedFile = getIntent().getStringExtra(EXTRA_SAVED_FILE_NAME);
        if (savedFile != null) {
            for (Match m : MatchStorage.loadAllMatches(this)) {
                if (savedFile.equals(m.getSavedFileName())) { match = m; break; }
            }
        } else {
            match = ((CricketApp) getApplication()).getCurrentMatch();
        }

        if (match == null) { finish(); return; }

        buildUI();
    }

    // ─── Build entire UI programmatically ────────────────────────────────────

    private void buildUI() {
        // Root scroll view
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(col(R.color.c_bg_page));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        // Derive team names
        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        String  bat1      = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        String  bat2      = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();
        Innings i1        = match.getFirstInnings();
        Innings i2        = match.getSecondInnings();

        // ── 1. Header banner ──────────────────────────────────────────────
        root.addView(buildHeader(bat1, bat2, i1, i2));

        // ── 2. Run rate chart ─────────────────────────────────────────────
        root.addView(sectionLabel("RUN RATE PROGRESSION"));
        root.addView(buildRunRateChart(bat1, bat2, i1, i2));

        // ── 3. Batting highlights ─────────────────────────────────────────
        root.addView(sectionLabel("BATTING HIGHLIGHTS"));
        List<Player> allBatters = mergeAllBatters(i1, i2, bat1, bat2);
        root.addView(buildBattingHighlights(allBatters));

        // ── 4. Bowling highlights ─────────────────────────────────────────
        root.addView(sectionLabel("BOWLING HIGHLIGHTS"));
        List<BowlerStatWithTeam> allBowlers = mergeAllBowlers(i1, i2, bat2, bat1);
        root.addView(buildBowlingHighlights(allBowlers));

        // ── 5. Over-by-over runs ──────────────────────────────────────────
        root.addView(sectionLabel("OVER-BY-OVER RUNS"));
        if (i1 != null) root.addView(buildOverChart(bat1, i1, col(R.color.green_mid)));
        if (i2 != null) root.addView(buildOverChart(bat2, i2, col(R.color.blue_mid)));

        // Bottom padding
        View pad = new View(this);
        pad.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        root.addView(pad);

        setContentView(scroll);
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private View buildHeader(String bat1, String bat2, Innings i1, Innings i2) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(col(R.color.green_dark));
        header.setPadding(dp(20), dp(36), dp(20), dp(20));
        header.setGravity(android.view.Gravity.CENTER);

        TextView tvTitle = tv("IN-DEPTH STATISTICS", 11f, col(R.color.green_light), false);
        tvTitle.setLetterSpacing(0.12f);
        header.addView(tvTitle);

        TextView tvMatch = tv(match.getHomeTeamName() + " vs " + match.getAwayTeamName(),
                20f, Color.WHITE, true);
        tvMatch.setLayoutParams(marginParams(0, 8, 0, 4));
        header.addView(tvMatch);

        if (match.getResultDescription() != null) {
            header.addView(tv(match.getResultDescription(), 13f, col(R.color.green_light), false));
        }

        // Score row
        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srp.setMargins(0, dp(16), 0, 0);
        scoreRow.setLayoutParams(srp);

        if (i1 != null) scoreRow.addView(miniScoreCard(bat1, i1.getScoreString(), i1.getOversString()));
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(40)));
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        scoreRow.addView(divider);
        if (i2 != null) scoreRow.addView(miniScoreCard(bat2, i2.getScoreString(), i2.getOversString()));

        header.addView(scoreRow);
        return header;
    }

    private View miniScoreCard(String team, String score, String overs) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.addView(tv(team.toUpperCase(), 10f, col(R.color.green_light), false));
        card.addView(tv(score, 22f, Color.WHITE, true));
        card.addView(tv(overs + " ov", 11f, Color.parseColor("#AADECE"), false));
        return card;
    }

    // ─── Run Rate Chart ───────────────────────────────────────────────────────

    private View buildRunRateChart(String bat1, String bat2, Innings i1, Innings i2) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(12), 0, dp(12), 0);

        // Chart card
        RunRateChartView chart = new RunRateChartView(this, i1, i2,
                col(R.color.green_mid), col(R.color.blue_mid));
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200)));
        chart.setBackgroundColor(col(R.color.c_bg_card));

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(12));
        legend.setLayoutParams(lp);
        if (i1 != null) legend.addView(legendDot(bat1, col(R.color.green_mid)));
        if (i2 != null) legend.addView(legendDot(bat2, col(R.color.blue_mid)));

        wrapper.addView(roundCard(chart));
        wrapper.addView(legend);
        return wrapper;
    }

    private View legendDot(String label, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(dp(12), 0, dp(12), 0);
        row.setLayoutParams(rp);

        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
        dot.setBackgroundColor(color);
        row.addView(dot);

        TextView t = tv("  " + label, 12f, col(R.color.c_text_secondary), false);
        row.addView(t);
        return row;
    }

    // ─── Batting highlights ───────────────────────────────────────────────────

    private View buildBattingHighlights(List<Player> players) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), 0, dp(12), 0);

        // Top scorer
        Player topScorer = null;
        Player mostSixes  = null;
        Player mostFours  = null;
        Player bestSR     = null;

        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0) continue;
            if (topScorer == null || p.getRunsScored() > topScorer.getRunsScored()) topScorer = p;
            if (mostSixes  == null || p.getSixes()      > mostSixes.getSixes())      mostSixes  = p;
            if (mostFours  == null || p.getFours()       > mostFours.getFours())      mostFours  = p;
            if (p.getBallsFaced() >= 3) {
                if (bestSR == null || p.getStrikeRate() > bestSR.getStrikeRate())     bestSR     = p;
            }
        }

        LinearLayout row1 = highlightRow();
        LinearLayout row2 = highlightRow();

        row1.addView(statHighlight("🏆", "Top Scorer",
                topScorer != null ? topScorer.getName() : "-",
                topScorer != null ? topScorer.getRunsScored() + " runs" : ""));
        row1.addView(statHighlight("⚡", "Best Strike Rate",
                bestSR != null ? bestSR.getName() : "-",
                bestSR != null ? String.format(Locale.US, "%.1f", bestSR.getStrikeRate()) : ""));

        row2.addView(statHighlight("6️⃣", "Most Sixes",
                mostSixes != null ? mostSixes.getName() : "-",
                mostSixes != null ? mostSixes.getSixes() + " sixes" : ""));
        row2.addView(statHighlight("4️⃣", "Most Fours",
                mostFours != null ? mostFours.getName() : "-",
                mostFours != null ? mostFours.getFours() + " fours" : ""));

        grid.addView(row1);
        grid.addView(row2);
        return grid;
    }

    // ─── Bowling highlights ───────────────────────────────────────────────────

    private View buildBowlingHighlights(List<BowlerStatWithTeam> bowlers) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), 0, dp(12), 0);

        BowlerStatWithTeam mostWickets  = null;
        BowlerStatWithTeam bestEconomy  = null;
        BowlerStatWithTeam mostOvers    = null;

        for (BowlerStatWithTeam b : bowlers) {
            if (mostWickets == null || b.stat.getWickets() > mostWickets.stat.getWickets())
                mostWickets = b;
            if (b.stat.getBalls() > 0) {
                if (bestEconomy == null || b.stat.getEconomy() < bestEconomy.stat.getEconomy())
                    bestEconomy = b;
            }
            if (mostOvers == null || b.stat.getBalls() > mostOvers.stat.getBalls())
                mostOvers = b;
        }

        LinearLayout row1 = highlightRow();
        LinearLayout row2 = highlightRow();

        row1.addView(statHighlight("🎳", "Most Wickets",
                mostWickets != null ? mostWickets.stat.getName() : "-",
                mostWickets != null ? mostWickets.stat.getWickets() + " wickets" : ""));
        row1.addView(statHighlight("💰", "Best Economy",
                bestEconomy != null ? bestEconomy.stat.getName() : "-",
                bestEconomy != null
                        ? String.format(Locale.US, "%.2f", bestEconomy.stat.getEconomy()) : ""));

        row2.addView(statHighlight("📋", "Most Overs",
                mostOvers != null ? mostOvers.stat.getName() : "-",
                mostOvers != null ? mostOvers.stat.getOversString() + " ov" : ""));
        // Empty slot for visual balance
        row2.addView(emptyHighlight());

        grid.addView(row1);
        grid.addView(row2);
        return grid;
    }

    // ─── Over-by-over bar chart ───────────────────────────────────────────────

    private View buildOverChart(String teamName, Innings innings, int barColor) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.setMargins(dp(12), 0, dp(12), dp(12));
        wrapper.setLayoutParams(wp);

        TextView label = tv(teamName.toUpperCase() + " — RUNS PER OVER",
                10f, col(R.color.c_text_secondary), false);
        label.setPadding(0, dp(4), 0, dp(6));
        wrapper.addView(label);

        OverBarChartView chart = new OverBarChartView(this, innings, barColor, teamName);
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(130)));
        chart.setBackgroundColor(col(R.color.c_bg_card));

        wrapper.addView(roundCard(chart));
        return wrapper;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private View statHighlight(String emoji, String title, String player, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(cp);
        card.setBackgroundColor(col(R.color.c_bg_card));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        // Rounded corners via background drawable created programmatically
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(col(R.color.c_bg_card));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), col(R.color.c_border));
        card.setBackground(bg);

        TextView tvEmoji = tv(emoji, 22f, 0, false);
        tvEmoji.setLayoutParams(marginParams(0, 0, 0, 6));
        card.addView(tvEmoji);

        card.addView(tv(title, 10f, col(R.color.c_text_secondary), false));
        TextView tvPlayer = tv(player, 14f, col(R.color.c_text_primary), true);
        tvPlayer.setLayoutParams(marginParams(0, 4, 0, 2));
        card.addView(tvPlayer);
        card.addView(tv(value, 12f, col(R.color.green_mid), false));
        return card;
    }

    private View emptyHighlight() {
        View v = new View(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout highlightRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 0, 0, 0);
        row.setLayoutParams(rp);
        return row;
    }

    private View sectionLabel(String text) {
        TextView tv = tv(text, 11f, col(R.color.c_text_secondary), false);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(16), dp(18), dp(16), dp(6));
        tv.setLayoutParams(p);
        return tv;
    }

    private View roundCard(View child) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setRadius(dp(12));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(col(R.color.c_bg_card));
        card.addView(child);
        return card;
    }

    private TextView tv(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        if (color != 0) t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams marginParams(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int col(int res) { return getResources().getColor(res, getTheme()); }
    private int dp(int val)  { return (int)(val * getResources().getDisplayMetrics().density); }

    // ─── Data helpers ─────────────────────────────────────────────────────────

    private List<Player> mergeAllBatters(Innings i1, Innings i2, String bat1, String bat2) {
        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        List<Player> all = new ArrayList<>();
        if (i1 != null) all.addAll(homeFirst ? match.getHomePlayers() : match.getAwayPlayers());
        if (i2 != null) all.addAll(homeFirst ? match.getAwayPlayers() : match.getHomePlayers());
        return all;
    }

    private List<BowlerStatWithTeam> mergeAllBowlers(Innings i1, Innings i2,
                                                      String bowl1, String bowl2) {
        List<BowlerStatWithTeam> all = new ArrayList<>();
        if (i1 != null) for (BowlerStat s : i1.getBowlerStats()) all.add(new BowlerStatWithTeam(s, bowl1));
        if (i2 != null) for (BowlerStat s : i2.getBowlerStats()) all.add(new BowlerStatWithTeam(s, bowl2));
        return all;
    }

    private static class BowlerStatWithTeam {
        final BowlerStat stat; final String team;
        BowlerStatWithTeam(BowlerStat s, String t) { stat = s; team = t; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Custom View: Run Rate Line Chart
    // ═════════════════════════════════════════════════════════════════════════

    static class RunRateChartView extends View {
        private final Innings i1, i2;
        private final int color1, color2;
        private final Paint paintLine1, paintLine2, paintFill1, paintFill2;
        private final Paint paintGrid, paintLabel;

        RunRateChartView(Context ctx, Innings i1, Innings i2, int color1, int color2) {
            super(ctx);
            this.i1 = i1; this.i2 = i2;
            this.color1 = color1; this.color2 = color2;

            paintLine1 = linePaint(color1, 3f);
            paintLine2 = linePaint(color2, 3f);
            paintFill1 = fillPaint(color1);
            paintFill2 = fillPaint(color2);
            paintGrid  = gridPaint();
            paintLabel = labelPaint(ctx);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float padL = dp(40), padR = dp(16), padT = dp(12), padB = dp(28);
            float chartW = w - padL - padR, chartH = h - padT - padB;

            // Background
            canvas.drawColor(Color.TRANSPARENT);

            // Grid lines (0, 6, 12, 18, 24, 30 runs/over)
            float maxRR = 40f;
            for (int rr = 0; rr <= 36; rr += 6) {
                float y = padT + chartH - (rr / maxRR) * chartH;
                canvas.drawLine(padL, y, padL + chartW, y, paintGrid);
                canvas.drawText(String.valueOf(rr), padL - dp(4), y + dp(4), paintLabel);
            }

            // Draw innings lines
            if (i1 != null) drawInningsLine(canvas, i1, padL, padT, chartW, chartH, maxRR,
                    paintLine1, paintFill1, color1);
            if (i2 != null) drawInningsLine(canvas, i2, padL, padT, chartW, chartH, maxRR,
                    paintLine2, paintFill2, color2);

            // Over labels on X axis
            int totalOvers = Math.max(
                    i1 != null ? i1.getCompletedOvers().size() : 0,
                    i2 != null ? i2.getCompletedOvers().size() : 0);
            if (totalOvers > 0) {
                float step = chartW / totalOvers;
                for (int ov = 1; ov <= totalOvers; ov += Math.max(1, totalOvers / 6)) {
                    float x = padL + (ov - 0.5f) * step;
                    canvas.drawText(String.valueOf(ov), x, padT + chartH + dp(14), paintLabel);
                }
            }
        }

        private void drawInningsLine(Canvas canvas, Innings inn,
                                     float padL, float padT, float chartW, float chartH,
                                     float maxRR, Paint linePaint, Paint fillPaint, int color) {
            List<Over> overs = inn.getCompletedOvers();
            if (overs.isEmpty()) return;

            float step = chartW / overs.size();
            Path linePath = new Path();
            Path fillPath = new Path();

            float startY = padT + chartH;
            fillPath.moveTo(padL, startY);

            for (int i = 0; i < overs.size(); i++) {
                float rr = Math.min(overs.get(i).getTotalRuns() * 6f, maxRR);
                // Clamp to max
                float x = padL + (i + 0.5f) * step;
                float y = padT + chartH - (rr / maxRR) * chartH;
                if (i == 0) { linePath.moveTo(x, y); fillPath.lineTo(x, y); }
                else         { linePath.lineTo(x, y); fillPath.lineTo(x, y); }

                // Dot
                Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                dotPaint.setColor(color); dotPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, dp(3), dotPaint);
            }
            float lastX = padL + (overs.size() - 0.5f) * step;
            fillPath.lineTo(lastX, padT + chartH);
            fillPath.close();

            // Gradient fill
            fillPaint.setShader(new LinearGradient(0, padT, 0, padT + chartH,
                    color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);
        }

        private Paint linePaint(int color, float width) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color); p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(width)); p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            return p;
        }
        private Paint fillPaint(int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color); p.setStyle(Paint.Style.FILL); p.setAlpha(60);
            return p;
        }
        private Paint gridPaint() {
            Paint p = new Paint();
            p.setColor(Color.parseColor("#22888888")); p.setStrokeWidth(1f);
            return p;
        }
        private Paint labelPaint(Context ctx) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.parseColor("#888888")); p.setTextSize(dp(9));
            p.setTextAlign(Paint.Align.RIGHT);
            return p;
        }
        private int dp(float v) {
            return (int)(v * getContext().getResources().getDisplayMetrics().density);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Custom View: Over-by-over horizontal bar chart
    // ═════════════════════════════════════════════════════════════════════════

    static class OverBarChartView extends View {
        private final Innings innings;
        private final int     barColor;
        private final String  teamName;

        OverBarChartView(Context ctx, Innings innings, int barColor, String teamName) {
            super(ctx);
            this.innings  = innings;
            this.barColor = barColor;
            this.teamName = teamName;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            List<Over> overs = innings.getCompletedOvers();
            if (overs.isEmpty()) return;

            float w = getWidth(), h = getHeight();
            float padL = dp(32), padR = dp(12), padT = dp(12), padB = dp(20);
            float chartW = w - padL - padR, chartH = h - padT - padB;

            // Find max runs in any over for scale
            int maxRuns = 1;
            for (Over ov : overs) maxRuns = Math.max(maxRuns, ov.getTotalRuns());

            float barH      = (chartH / overs.size()) * 0.65f;
            float barGap    = chartH / overs.size();

            Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            barPaint.setColor(barColor);

            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            labelPaint.setColor(Color.parseColor("#888888"));
            labelPaint.setTextSize(dp(9));
            labelPaint.setTextAlign(Paint.Align.RIGHT);

            Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valuePaint.setColor(barColor);
            valuePaint.setTextSize(dp(9));
            valuePaint.setTypeface(Typeface.DEFAULT_BOLD);

            for (int i = 0; i < overs.size(); i++) {
                Over  ov    = overs.get(i);
                float y     = padT + i * barGap + (barGap - barH) / 2f;
                float barW  = ((float) ov.getTotalRuns() / maxRuns) * chartW;

                // Bar background track
                Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                trackPaint.setColor(Color.parseColor("#11888888"));
                RectF track = new RectF(padL, y, padL + chartW, y + barH);
                canvas.drawRoundRect(track, dp(3), dp(3), trackPaint);

                // Actual bar
                if (barW > 0) {
                    RectF bar = new RectF(padL, y, padL + barW, y + barH);
                    barPaint.setAlpha(200);
                    canvas.drawRoundRect(bar, dp(3), dp(3), barPaint);
                }

                // Over label
                canvas.drawText("Ov " + ov.getOverNumber(),
                        padL - dp(4), y + barH / 2f + dp(4), labelPaint);

                // Runs value
                canvas.drawText(String.valueOf(ov.getTotalRuns()),
                        padL + barW + dp(6), y + barH / 2f + dp(4), valuePaint);
            }
        }

        private int dp(float v) {
            return (int)(v * getContext().getResources().getDisplayMetrics().density);
        }
    }
}
