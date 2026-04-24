package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cricket.scorer.R;
import com.cricket.scorer.adapters.BallAdapter;
import com.cricket.scorer.adapters.OverHistoryAdapter;
import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.utils.MatchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * InningsActivity.java
 *
 * CHANGES for batting mode:
 *
 * Batting table:
 *   - Single mode: only the striker row is shown (no non-striker row).
 *   - Two mode: both striker and non-striker rows shown as before.
 *     The striker row is highlighted green; ⚡ prefix marks the striker.
 *
 * Wicket dialog:
 *   - Single mode: "Next batsman" dialog still appears to choose who comes in.
 *   - Two mode: same as before.
 *
 * Mode badge:
 *   - A small label in the scoreboard shows "Single bat" or "Two bat"
 *     so the scorer always knows which mode is active.
 */
public class InningsActivity extends AppCompatActivity {

    private CricketApp   app;
    private Match        match;
    private MatchEngine  engine;

    // ── Scoreboard ─────────────────────────────────────────────────────────────
    private TextView     tvInningsTitle;
    private TextView     tvScore;
    private TextView     tvOversInfo;
    private TextView     tvCRR;
    private TextView     tvRRR;
    private TextView     tvModeBadge;
    private LinearLayout layoutTargetBanner;
    private TextView     tvTargetInfo;
    private TextView     tvRequiredBalls;

    // ── Batting table ──────────────────────────────────────────────────────────
    private TableLayout tableBatsmen;

    // ── Over tracker ──────────────────────────────────────────────────────────
    private TextView    tvCurrentOverLabel;
    private TextView    tvBallsRemaining;
    private RecyclerView rvCurrentOverBalls;
    private RecyclerView rvOverHistory;

    // ── Ball input buttons ─────────────────────────────────────────────────────
    private Button btnDot, btn1, btn2, btn3, btn4, btn6;
    private Button btnWide, btnNoBall, btnWicket, btnUndo;

    // ── Adapters ───────────────────────────────────────────────────────────────
    private BallAdapter        ballAdapter;
    private OverHistoryAdapter overHistoryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_innings);

        app    = (CricketApp) getApplication();
        match  = app.getCurrentMatch();
        engine = app.getMatchEngine();

        if (match == null) { finish(); return; }

        bindViews();
        setupAdapters();
        setClickListeners();
        refreshUI();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        tvInningsTitle     = findViewById(R.id.tv_innings_title);
        tvScore            = findViewById(R.id.tv_score);
        tvOversInfo        = findViewById(R.id.tv_overs_info);
        tvCRR              = findViewById(R.id.tv_crr);
        tvRRR              = findViewById(R.id.tv_rrr);
        tvModeBadge        = findViewById(R.id.tv_mode_badge);
        layoutTargetBanner = findViewById(R.id.layout_target_banner);
        tvTargetInfo       = findViewById(R.id.tv_target_info);
        tvRequiredBalls    = findViewById(R.id.tv_required_balls);
        tableBatsmen       = findViewById(R.id.table_batsmen);
        tvCurrentOverLabel = findViewById(R.id.tv_current_over_label);
        tvBallsRemaining   = findViewById(R.id.tv_balls_remaining);
        rvCurrentOverBalls = findViewById(R.id.rv_current_over_balls);
        rvOverHistory      = findViewById(R.id.rv_over_history);

        btnDot    = findViewById(R.id.btn_dot);
        btn1      = findViewById(R.id.btn_1);
        btn2      = findViewById(R.id.btn_2);
        btn3      = findViewById(R.id.btn_3);
        btn4      = findViewById(R.id.btn_4);
        btn6      = findViewById(R.id.btn_6);
        btnWide   = findViewById(R.id.btn_wide);
        btnNoBall = findViewById(R.id.btn_noball);
        btnWicket = findViewById(R.id.btn_wicket);
        btnUndo   = findViewById(R.id.btn_undo);
    }

    private void setupAdapters() {
        ballAdapter = new BallAdapter(new ArrayList<>());
        rvCurrentOverBalls.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCurrentOverBalls.setAdapter(ballAdapter);

        overHistoryAdapter = new OverHistoryAdapter(new ArrayList<>());
        rvOverHistory.setLayoutManager(new LinearLayoutManager(this));
        rvOverHistory.setAdapter(overHistoryAdapter);
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {
        btnDot.setOnClickListener(v -> handleBall(0));
        btn1.setOnClickListener(v -> handleBall(1));
        btn2.setOnClickListener(v -> handleBall(2));
        btn3.setOnClickListener(v -> handleBall(3));
        btn4.setOnClickListener(v -> handleBall(4));
        btn6.setOnClickListener(v -> handleBall(6));
        btnWide.setOnClickListener(v -> handleMatchState(engine.deliverWide()));
        btnNoBall.setOnClickListener(v -> handleMatchState(engine.deliverNoBall()));
        btnWicket.setOnClickListener(v -> showWicketDialog());
        btnUndo.setOnClickListener(v -> {
            if (!engine.undoLastBall())
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            else refreshUI();
        });
    }

    // ─── Delivery handlers ────────────────────────────────────────────────────

    private void handleBall(int runs) {
        handleMatchState(engine.deliverNormalBall(runs));
    }

    /**
     * Wicket dialog.
     *
     * Both modes: shows a list of available (not yet out, not at crease) players.
     * Single mode: "at crease" means only the striker — non-striker is never in the list.
     * Two mode: both striker and non-striker are excluded from the list.
     */
    private void showWicketDialog() {
        List<Player> available = engine.getAvailableBatsmen();

        if (available.isEmpty()) {
            // No one left — all out
            handleMatchState(engine.deliverWicket(engine.getNextBatsmanIndex()));
            return;
        }

        String[] names = new String[available.size()];
        final List<Player> batters = match.getCurrentBattingPlayers();
        for (int i = 0; i < available.size(); i++) {
            names[i] = (i + 1) + ". " + available.get(i).getName();
        }

        final int[] chosenIndex = {0};
        new AlertDialog.Builder(this)
                .setTitle("Wicket — choose next batsman")
                .setSingleChoiceItems(names, 0, (dialog, which) -> chosenIndex[0] = which)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    Player chosen    = available.get(chosenIndex[0]);
                    int    globalIdx = batters.indexOf(chosen);
                    handleMatchState(engine.deliverWicket(globalIdx));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Match state routing ──────────────────────────────────────────────────

    private void handleMatchState(MatchEngine.MatchState state) {
        switch (state) {
            case BALL_RECORDED:
                refreshUI();
                break;
            case OVER_COMPLETE:
                refreshUI();
                Toast.makeText(this,
                        "Over " + match.getCurrentInningsData().getCompletedOvers().size()
                                + " complete!", Toast.LENGTH_SHORT).show();
                break;
            case INNINGS_COMPLETE:
                startActivity(new Intent(this, InningsBreakActivity.class));
                finish();
                break;
            case MATCH_COMPLETE:
                startActivity(new Intent(this, StatsActivity.class));
                finish();
                break;
        }
    }

    // ─── UI refresh ───────────────────────────────────────────────────────────

    private void refreshUI() {
        Innings innings    = match.getCurrentInningsData();
        int     inningsNum = match.getCurrentInnings();
        boolean isSingle   = match.isSingleBatsmanMode();

        // ── Scoreboard ──────────────────────────────────────────────────
        tvInningsTitle.setText(inningsNum == 1 ? "1st Innings" : "2nd Innings");
        tvScore.setText(innings.getScoreString());
        tvOversInfo.setText(String.format(Locale.US, "Ov %s / %d",
                innings.getOversString(), match.getMaxOvers()));
        tvCRR.setText(String.format(Locale.US, "CRR: %.2f", innings.getCurrentRunRate()));

        // Mode badge — quick reminder for the scorer
        tvModeBadge.setText(isSingle ? "Single bat" : "Two bat");
        tvModeBadge.setVisibility(View.VISIBLE);

        // ── Target banner (2nd innings only) ────────────────────────────
        if (inningsNum == 2) {
            int   target     = match.getTarget();
            int   runsNeeded = innings.getRunsNeeded(target);
            int   ballsLeft  = innings.getBallsRemaining(match.getMaxOvers());
            float rrr        = innings.getRequiredRunRate(target, match.getMaxOvers());
            layoutTargetBanner.setVisibility(View.VISIBLE);
            tvRRR.setVisibility(View.VISIBLE);
            tvTargetInfo.setText("Target: " + target);
            tvRequiredBalls.setText("Need " + runsNeeded + " off " + ballsLeft + " balls");
            tvRRR.setText(String.format(Locale.US, "RRR: %.2f", rrr));
        } else {
            layoutTargetBanner.setVisibility(View.GONE);
            tvRRR.setVisibility(View.GONE);
        }

        // ── Batting table ────────────────────────────────────────────────
        refreshBattingTable(innings, isSingle);

        // ── Current over ─────────────────────────────────────────────────
        refreshCurrentOver(innings);

        // ── Over history ─────────────────────────────────────────────────
        overHistoryAdapter.updateData(innings.getCompletedOvers());
    }

    /**
     * Rebuilds the batting scorecard table.
     *
     * Single-batsman mode:
     *   Only the striker row is shown. No non-striker row.
     *   Previously out batsmen are also shown for historical reference.
     *
     * Two-batsman mode:
     *   Striker (highlighted green) + non-striker + previously out batsmen.
     */
    private void refreshBattingTable(Innings innings, boolean isSingle) {
        tableBatsmen.removeAllViews();

        // Header
        addTableRow(tableBatsmen,
                new String[]{"Batsman", "R", "B", "4s", "6s", "SR"},
                true, false, false);

        List<Player> players      = match.getCurrentBattingPlayers();
        int          strikerIdx   = innings.getStrikerIndex();
        int          nonStrikerIdx = innings.getNonStrikerIndex(); // -1 in single mode

        for (int i = 0; i < players.size(); i++) {
            Player  p            = players.get(i);
            boolean isStriker    = (i == strikerIdx);
            boolean isNonStriker = (!isSingle) && (i == nonStrikerIdx);
            boolean atCrease     = (isStriker || isNonStriker) && !p.isOut();

            // In single mode, skip non-striker slot entirely
            if (isSingle && isNonStriker) continue;

            // Skip players who haven't batted and aren't at the crease
            if (p.isHasNotBatted() && !atCrease) continue;

            String name = (isStriker ? "⚡ " : (isNonStriker ? "  " : "")) + p.getName();
            String sr   = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";

            addTableRow(tableBatsmen,
                    new String[]{name,
                            String.valueOf(p.getRunsScored()),
                            String.valueOf(p.getBallsFaced()),
                            String.valueOf(p.getFours()),
                            String.valueOf(p.getSixes()),
                            sr},
                    false, atCrease, p.isOut());
        }
    }

    private void addTableRow(TableLayout table, String[] cells,
                             boolean isHeader, boolean isActive, boolean isOut) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (isActive) row.setBackgroundColor(Color.parseColor("#E1F5EE"));

        int[] weights = {3, 1, 1, 1, 1, 1};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(0,
                    TableRow.LayoutParams.WRAP_CONTENT, weights[i]));
            tv.setText(cells[i]);
            tv.setPadding(12, 10, 12, 10);
            tv.setTextSize(12f);
            if (isHeader) {
                tv.setTextColor(Color.parseColor("#888780"));
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (isOut) {
                tv.setTextColor(Color.parseColor("#AAAAAA"));
                if (i == 0) tv.setPaintFlags(
                        tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tv.setTextColor(Color.parseColor("#111111"));
            }
            row.addView(tv);
        }
        table.addView(row);
    }

    private void refreshCurrentOver(Innings innings) {
        Over currentOver      = innings.getCurrentOver();
        int  completedOverCnt = innings.getCompletedOvers().size();

        tvCurrentOverLabel.setText("Over " + (completedOverCnt + 1));

        int validCount = currentOver != null ? currentOver.getValidBallCount() : 0;
        int remaining  = 6 - validCount;
        tvBallsRemaining.setText(remaining + (remaining == 1 ? " ball left" : " balls left"));

        List<Ball> displayBalls = new ArrayList<>();
        if (currentOver != null) displayBalls.addAll(currentOver.getBalls());
        // Fill remaining slots with null (empty circles)
        for (int i = 0; i < Math.max(0, 6 - validCount); i++) displayBalls.add(null);
        ballAdapter.updateData(displayBalls);
    }
}
