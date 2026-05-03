package com.cricket.scorer.activities;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.cricket.scorer.R;
import com.cricket.scorer.models.BowlerStat;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.utils.LiveMatchState;

import java.util.List;
import java.util.Locale;

/**
 * InningsBreakActivity.java
 *
 * CHANGE: Three tappable stat cards added below the score summary,
 * each opening a full-screen popup dialog:
 *
 *  1. Run Rate Progression — over-by-over run rate line chart
 *  2. 1st Innings Batting  — full batting scorecard table
 *  3. 1st Innings Bowling  — full bowling figures table
 *
 * Each popup:
 *   - Dark overlay behind a rounded content card
 *   - ✕ close button top-right
 *   - "Close" button at the bottom
 *   - Tap outside the card to dismiss
 *   - Back button dismisses the popup (setCancelable = true)
 */
public class InningsBreakActivity extends AppCompatActivity {

    private TextView tvBattingTeam, tvFirstInningsScore;
    private TextView tvOversPlayed, tvChasingTeam, tvTarget, tvRequiredRR;
    private TextView tvBattingCardTitle, tvBowlingCardTitle;
    private LinearLayout cardRunRate, cardBattingStats, cardBowlingStats;
    private Button btnStart2ndInnings;

    private Match  match;
    private String bat1Team, bowl1Team;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_innings_break);
        bindViews();
        match = ((CricketApp) getApplication()).getCurrentMatch();
        if (match == null) { finish(); return; }
        deriveTeams();
        populateData();
        setClickListeners();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (match != null && !match.isMatchCompleted())
            LiveMatchState.persist(this, match);
    }

    @Override public void onBackPressed() { /* blocked */ }

    // ─── Teams ────────────────────────────────────────────────────────────────

    private void deriveTeams() {
        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        bat1Team  = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        bowl1Team = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();
    }

    // ─── Bind ─────────────────────────────────────────────────────────────────

    private void bindViews() {
        tvBattingTeam       = findViewById(R.id.tv_batting_team);
        tvFirstInningsScore = findViewById(R.id.tv_first_innings_score);
        tvOversPlayed       = findViewById(R.id.tv_overs_played);
        tvChasingTeam       = findViewById(R.id.tv_chasing_team);
        tvTarget            = findViewById(R.id.tv_target);
        tvRequiredRR        = findViewById(R.id.tv_required_rr);
        tvBattingCardTitle  = findViewById(R.id.tv_batting_card_title);
        tvBowlingCardTitle  = findViewById(R.id.tv_bowling_card_title);
        cardRunRate         = findViewById(R.id.card_run_rate);
        cardBattingStats    = findViewById(R.id.card_batting_stats);
        cardBowlingStats    = findViewById(R.id.card_bowling_stats);
        btnStart2ndInnings  = findViewById(R.id.btn_start_2nd_innings);
    }

    // ─── Populate ─────────────────────────────────────────────────────────────

    private void populateData() {
        Innings i1 = match.getFirstInnings();

        tvBattingTeam.setText(bat1Team);
        tvFirstInningsScore.setText(i1.getScoreString());
        tvOversPlayed.setText("in " + i1.getOversString() + " overs");
        tvChasingTeam.setText(bowl1Team + " need:");
        tvTarget.setText(match.getTarget() + " runs");
        float rrr = (float) match.getTarget() / match.getMaxOvers();
        tvRequiredRR.setText(String.format(Locale.US, "Required run rate: %.2f", rrr));

        // Update card titles with team names
        tvBattingCardTitle.setText("1st Innings — " + bat1Team + " Batting");
        tvBowlingCardTitle.setText("1st Innings — " + bowl1Team + " Bowling");
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {
        cardRunRate.setOnClickListener(v -> showRunRatePopup());
        cardBattingStats.setOnClickListener(v -> showBattingPopup());
        cardBowlingStats.setOnClickListener(v -> showBowlingPopup());

        btnStart2ndInnings.setOnClickListener(v -> {
            startActivity(new Intent(this, InningsActivity.class));
            finish();
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Popup 1 — Run Rate Progression Chart
    // ═════════════════════════════════════════════════════════════════════════

    private void showRunRatePopup() {
        Innings i1 = match.getFirstInnings();
        Dialog dialog = makeDialog();

        // Chart view
        RunRateChartView chart = new RunRateChartView(this, i1,
                col(R.color.green_mid));
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, dp(8), 0, dp(8));
        legend.addView(legendDot(bat1Team, col(R.color.green_mid)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(4));

        // Info row: total runs / overs
        String info = i1.getScoreString() + "  ·  " + i1.getOversString() + " ov  ·  CRR "
                + String.format(Locale.US, "%.2f", i1.getCurrentRunRate());
        TextView tvInfo = makeTv(info, 12f, col(R.color.c_text_secondary), false);
        tvInfo.setPadding(0, 0, 0, dp(8));
        body.addView(tvInfo);
        body.addView(wrapInCard(chart));
        body.addView(legend);

        dialog.setContentView(makePopupRoot(
                "Run Rate Progression",
                bat1Team.toUpperCase() + " — " + i1.getOversString() + " overs",
                body, dialog));
        styleAndShow(dialog);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Popup 2 — Batting scorecard
    // ═════════════════════════════════════════════════════════════════════════

    private void showBattingPopup() {
        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        List<Player> players = homeFirst ? match.getHomePlayers() : match.getAwayPlayers();
        Innings i1 = match.getFirstInnings();

        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(true);
        addBattingHeader(table);
        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0 && p.getRunsScored() == 0) continue;
            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";
            addBattingRow(table, p.getName(),
                    String.valueOf(p.getRunsScored()),
                    String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()),
                    String.valueOf(p.getSixes()),
                    sr,
                    p.isOut() ? "Out" : (p.isRetiredHurt() ? "Ret.Hurt" : "Not out"),
                    p.isOut(), p.isRetiredHurt());
        }

        // Totals footer
        addBattingFooter(table, i1);

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(true);
        hScroll.addView(table);

        ScrollView vScroll = new ScrollView(this);
        LinearLayout.LayoutParams vsp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        vScroll.setLayoutParams(vsp);
        vScroll.addView(hScroll);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(4), dp(8), dp(4));
        body.addView(wrapInCard(vScroll));

        dialog(bat1Team + " Batting", "1st Innings  ·  " + i1.getScoreString()
                + "  (" + i1.getOversString() + " ov)", body);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Popup 3 — Bowling figures
    // ═════════════════════════════════════════════════════════════════════════

    private void showBowlingPopup() {
        Innings i1 = match.getFirstInnings();
        List<BowlerStat> stats = i1.getBowlerStats();

        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(true);
        addBowlingHeader(table);

        if (stats.isEmpty()) {
            addBowlingRow(table, "No bowling data", "—", "—", "—", "—", "—");
        } else {
            for (BowlerStat s : stats) {
                addBowlingRow(table,
                        s.getName(),
                        String.valueOf(s.getOvers()),
                        String.valueOf(s.getBalls()),
                        String.valueOf(s.getRuns()),
                        String.valueOf(s.getWickets()),
                        String.format(Locale.US, "%.2f", s.getEconomy()));
            }
        }

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(true);
        hScroll.addView(table);

        ScrollView vScroll = new ScrollView(this);
        LinearLayout.LayoutParams vsp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        vScroll.setLayoutParams(vsp);
        vScroll.addView(hScroll);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(4), dp(8), dp(4));
        body.addView(wrapInCard(vScroll));

        dialog(bowl1Team + " Bowling", "1st Innings  ·  " + i1.getOversString() + " overs", body);
    }

    // ─── Table helpers ────────────────────────────────────────────────────────

    private void addBattingHeader(TableLayout t) {
        String[] cols = {"Batsman","R","B","4s","6s","SR","Status"};
        TableRow row  = new TableRow(this);
        row.setBackgroundColor(col(R.color.c_row_header_bg));
        int[] widths = {240,55,55,55,55,75,90};
        for (int i = 0; i < cols.length; i++) {
            TextView tv = cell(cols[i], widths[i]);
            tv.setTextColor(col(R.color.c_row_header_text));
            tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
        }
        t.addView(row);
    }

    private void addBattingRow(TableLayout t, String name, String r, String b,
                                String fs, String sx, String sr, String status,
                                boolean isOut, boolean isRH) {
        TableRow row = new TableRow(this);
        String[] vals = {name, r, b, fs, sx, sr, status};
        int[] widths   = {240, 55, 55, 55, 55, 75, 90};
        for (int i = 0; i < vals.length; i++) {
            TextView tv = cell(vals[i], widths[i]);
            if (isOut) {
                tv.setTextColor(Color.parseColor("#AAAAAA"));
                if (i == 0) tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else if (isRH) {
                tv.setTextColor(Color.parseColor("#BA7517"));
            } else {
                tv.setTextColor(col(R.color.c_text_primary));
            }
            row.addView(tv);
        }
        t.addView(row);
    }

    private void addBattingFooter(TableLayout t, Innings inn) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(col(R.color.c_row_alt_bg));
        String[] vals = {
                "TOTAL",
                String.valueOf(inn.getTotalRuns()),
                "",
                "",
                "",
                "",
                inn.getScoreString() + " (" + inn.getOversString() + " ov)"
        };
        int[] widths = {240, 55, 55, 55, 55, 75, 90};
        for (int i = 0; i < vals.length; i++) {
            TextView tv = cell(vals[i], widths[i]);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(col(R.color.c_text_primary));
            row.addView(tv);
        }
        t.addView(row);
    }

    private void addBowlingHeader(TableLayout t) {
        String[] cols = {"Bowler","O","B","R","W","Econ"};
        TableRow row  = new TableRow(this);
        row.setBackgroundColor(col(R.color.c_row_header_bg));
        int[] widths = {200,55,55,55,55,80};
        for (int i = 0; i < cols.length; i++) {
            TextView tv = cell(cols[i], widths[i]);
            tv.setTextColor(col(R.color.c_row_header_text));
            tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
        }
        t.addView(row);
    }

    private void addBowlingRow(TableLayout t, String name, String o, String b,
                                String r, String w, String econ) {
        TableRow row = new TableRow(this);
        String[] vals = {name, o, b, r, w, econ};
        int[] widths   = {200, 55, 55, 55, 55, 80};
        for (String v : new String[]{}) {} // no-op to please compiler
        for (int i = 0; i < vals.length; i++) {
            TextView tv = cell(vals[i], widths[i]);
            tv.setTextColor(col(R.color.c_text_primary));
            row.addView(tv);
        }
        t.addView(row);
    }

    private TextView cell(String text, int widthDp) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new TableRow.LayoutParams(dp(widthDp), TableRow.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setPadding(dp(10), dp(9), dp(10), dp(9));
        tv.setTextSize(12f);
        return tv;
    }

    // ─── Popup infrastructure ─────────────────────────────────────────────────

    /** Shorthand: create dialog, build root with body, show. */
    private void dialog(String title, String subtitle, LinearLayout body) {
        Dialog d = makeDialog();
        d.setContentView(makePopupRoot(title, subtitle, body, d));
        styleAndShow(d);
    }

    private Dialog makeDialog() {
        Dialog d = new Dialog(this, R.style.Theme_CricketScorer);
        d.setCancelable(true);
        return d;
    }

    private void styleAndShow(Dialog d) {
        d.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        d.show();
    }

    /**
     * Builds the standard popup shell:
     *   dimmed overlay → rounded card
     *     green header (title + subtitle + ✕)
     *     body content
     *     Close button
     */
    private View makePopupRoot(String title, String subtitle,
                                LinearLayout body, Dialog dialog) {
        // Dimmed overlay — tap outside to dismiss
        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(Color.parseColor("#CC000000"));
        overlay.setOnClickListener(v -> dialog.dismiss());

        // Content card — consume click so overlay doesn't fire
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(dp(16), dp(60), dp(16), dp(60));
        cp.gravity = Gravity.CENTER_VERTICAL;
        card.setLayoutParams(cp);
        android.graphics.drawable.GradientDrawable cardBg =
                new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(col(R.color.c_bg_card));
        cardBg.setCornerRadius(dp(16));
        card.setBackground(cardBg);
        card.setOnClickListener(v -> { /* consume */ });

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(16), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable headerBg =
                new android.graphics.drawable.GradientDrawable();
        headerBg.setColor(col(R.color.green_dark));
        headerBg.setCornerRadii(new float[]{
                dp(16), dp(16), dp(16), dp(16), 0, 0, 0, 0
        });
        header.setBackground(headerBg);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleCol.addView(makeTv(title, 15f, Color.WHITE, true));
        TextView tvSub = makeTv(subtitle, 11f, Color.parseColor("#AADECE"), false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(2), 0, 0); tvSub.setLayoutParams(sp);
        titleCol.addView(tvSub);
        header.addView(titleCol);

        // ✕ button
        TextView btnX = new TextView(this);
        btnX.setText("✕");
        btnX.setTextSize(20f);
        btnX.setTextColor(Color.WHITE);
        btnX.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnX.setTypeface(null, Typeface.BOLD);
        btnX.setClickable(true);
        btnX.setFocusable(true);
        btnX.setOnClickListener(v -> dialog.dismiss());
        header.addView(btnX);

        card.addView(header);

        // Body content (scrollable within popup)
        ScrollView bodyScroll = new ScrollView(this);
        bodyScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bodyScroll.addView(body);
        card.addView(bodyScroll);

        // Close button
        Button btnClose = new Button(this);
        btnClose.setText("Close");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setAllCaps(false);
        btnClose.setTextSize(14f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        bp.setMargins(dp(16), dp(8), dp(16), dp(16));
        btnClose.setLayoutParams(bp);
        btnClose.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(col(R.color.green_dark)));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        card.addView(btnClose);

        overlay.addView(card);
        return overlay;
    }

    // ─── Run rate chart (same canvas logic as DeepStatsActivity) ─────────────

    private View legendDot(String label, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(dp(12), 0, dp(12), 0); row.setLayoutParams(rp);
        View dot = new View(this);
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(color);
        LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(dp(12), dp(12));
        dp_.setMargins(0, 0, dp(6), 0); dot.setLayoutParams(dp_); dot.setBackground(gd);
        row.addView(dot);
        row.addView(makeTv("  " + label, 12f, col(R.color.c_text_secondary), false));
        return row;
    }

    static class RunRateChartView extends View {
        private final Innings  innings;
        private final int      lineColor;
        private float  padL, padR, padT, padB, chartW, chartH;
        private float  maxRR;
        private int    maxOvers;
        private float[] rrData;

        RunRateChartView(android.content.Context ctx, Innings inn, int color) {
            super(ctx);
            this.innings   = inn;
            this.lineColor = color;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float d = getContext().getResources().getDisplayMetrics().density;
            padL = 54*d; padR = 20*d; padT = 24*d; padB = 40*d;
            chartW = w - padL - padR; chartH = h - padT - padB;
            rrData   = buildRR(innings);
            maxRR    = 2f;
            for (float r : rrData) maxRR = Math.max(maxRR, r);
            maxRR    = (float)(Math.ceil((maxRR+2)/2.0)*2);
            maxOvers = innings != null ? innings.getAllOvers().size() : 1;
            if (maxOvers == 0) maxOvers = 1;
        }

        @Override protected void onDraw(Canvas canvas) {
            if (rrData == null || rrData.length == 0) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.parseColor("#888888")); p.setTextSize(dp(13));
                p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("No overs completed", getWidth()/2f, getHeight()/2f, p);
                return;
            }
            drawGrid(canvas); drawAxes(canvas); drawLine(canvas); drawAxisLabels(canvas);
        }

        private void drawGrid(Canvas canvas) {
            Paint gp = new Paint(); gp.setColor(Color.parseColor("#22888888"));
            gp.setStrokeWidth(1f); gp.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.parseColor("#999999"));
            lp.setTextSize(dp(9)); lp.setTextAlign(Paint.Align.RIGHT);
            for (float v=0; v<=maxRR; v+=2) {
                float y = yFor(v);
                canvas.drawLine(padL,y,padL+chartW,y,gp);
                canvas.drawText(String.format(Locale.US,"%.0f",v), padL-dp(6), y+dp(4), lp);
            }
        }
        private void drawAxes(Canvas canvas) {
            Paint p = new Paint(); p.setColor(Color.parseColor("#AAAAAA")); p.setStrokeWidth(dp(1.5f));
            canvas.drawLine(padL,padT,padL,padT+chartH,p);
            canvas.drawLine(padL,padT+chartH,padL+chartW,padT+chartH,p);
        }
        private void drawLine(Canvas canvas) {
            Path line = new Path(), fill = new Path();
            fill.moveTo(padL, padT+chartH);
            for (int i=0; i<rrData.length; i++) {
                float x=xFor(i+1), y=yFor(rrData[i]);
                if (i==0) line.moveTo(x,y); else line.lineTo(x,y);
                fill.lineTo(x,y);
            }
            fill.lineTo(xFor(rrData.length), padT+chartH); fill.close();
            Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG); fp.setStyle(Paint.Style.FILL);
            fp.setShader(new LinearGradient(0,padT,0,padT+chartH,
                    Color.argb(90,Color.red(lineColor),Color.green(lineColor),Color.blue(lineColor)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(fill,fp);
            Paint lp2 = new Paint(Paint.ANTI_ALIAS_FLAG); lp2.setStyle(Paint.Style.STROKE);
            lp2.setColor(lineColor); lp2.setStrokeWidth(dp(2.5f));
            lp2.setStrokeJoin(Paint.Join.ROUND); lp2.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawPath(line,lp2);
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG); dot.setStyle(Paint.Style.FILL); dot.setColor(lineColor);
            Paint db  = new Paint(Paint.ANTI_ALIAS_FLAG); db.setStyle(Paint.Style.STROKE);
            db.setColor(Color.parseColor("#1A1A1A")); db.setStrokeWidth(dp(1.5f));
            Paint val = new Paint(Paint.ANTI_ALIAS_FLAG); val.setColor(lineColor);
            val.setTextSize(dp(8)); val.setTextAlign(Paint.Align.CENTER); val.setTypeface(Typeface.DEFAULT_BOLD);
            boolean showLbl = rrData.length<=10;
            for (int i=0; i<rrData.length; i++) {
                float x=xFor(i+1), y=yFor(rrData[i]);
                canvas.drawCircle(x,y,dp(4),dot); canvas.drawCircle(x,y,dp(4),db);
                if (showLbl) canvas.drawText(String.format(Locale.US,"%.1f",rrData[i]),x,y-dp(8),val);
            }
        }
        private void drawAxisLabels(Canvas canvas) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setColor(Color.parseColor("#BBBBBB"));
            t.setTextSize(dp(9)); t.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Run Rate", dp(2), padT-dp(6), t);
            Paint o = new Paint(Paint.ANTI_ALIAS_FLAG); o.setColor(Color.parseColor("#BBBBBB"));
            o.setTextSize(dp(10)); o.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Overs", padL+chartW/2, padT+chartH+dp(32), o);
            Paint x = new Paint(Paint.ANTI_ALIAS_FLAG); x.setColor(Color.parseColor("#999999"));
            x.setTextSize(dp(9)); x.setTextAlign(Paint.Align.CENTER);
            int step = Math.max(1, maxOvers/8);
            for (int ov=1; ov<=maxOvers; ov+=step) {
                float px = xFor(ov);
                canvas.drawText(String.valueOf(ov), px, padT+chartH+dp(16), x);
            }
        }
        private float xFor(int ov) {
            return padL + ((float)(ov-1)/(maxOvers-1==0?1:maxOvers-1))*chartW;
        }
        private float yFor(float rr) { return padT+chartH-(rr/maxRR)*chartH; }
        private float[] buildRR(Innings inn) {
            if (inn==null) return new float[0];
            List<Over> ovs = inn.getAllOvers();
            if (ovs.isEmpty()) return new float[0];
            float[] r = new float[ovs.size()]; int cum=0;
            int totalBalls = inn.getTotalValidBalls();
            for (int i=0;i<ovs.size();i++){
                cum += ovs.get(i).getTotalRuns();
                boolean isLast   = (i == ovs.size()-1);
                boolean isPartial = isLast && ovs.get(i).getValidBallCount() < 6;
                float ovsDecimal  = isPartial ? totalBalls/6f : (i+1);
                r[i] = ovsDecimal > 0 ? cum/ovsDecimal : 0f;
            }
            return r;
        }
        private int dp(float v) { return (int)(v*getContext().getResources().getDisplayMetrics().density); }
    }

    // ─── Generic helpers ──────────────────────────────────────────────────────

    private View wrapInCard(View child) {
        CardView card = new CardView(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setRadius(dp(12)); card.setCardElevation(dp(1));
        card.setCardBackgroundColor(col(R.color.c_bg_card));
        card.addView(child);
        return card;
    }

    private TextView makeTv(String text, float size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(size); tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private int col(int res) { return getResources().getColor(res, getTheme()); }
    private int dp(int val)  { return (int)(val * getResources().getDisplayMetrics().density); }

    /** isRetiredHurt() added to Player — stub for safe compilation if not patched */
    private boolean isRetiredHurt(Player p) {
        try { return p.isRetiredHurt(); } catch (Exception e) { return false; }
    }
}
