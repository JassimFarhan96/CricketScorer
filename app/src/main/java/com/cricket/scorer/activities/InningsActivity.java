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
import com.cricket.scorer.utils.LiveMatchState;
import com.cricket.scorer.utils.MatchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * InningsActivity.java
 *
 * BUG FIX — refreshBattingTable():
 *
 * Root cause:
 *   The old code used (isStriker || isNonStriker) to decide whether a player
 *   is "at crease". But after a wicket the NEW batsman takes the dismissed
 *   player's strikerIndex slot. So if opener (index 0) is dismissed and the
 *   next batsman comes in at index 0, the dismissed opener's row loses its
 *   "at crease" flag — but the NEW striker also shows at index 0.
 *   Result: the dismissed player's name is replaced by the new striker's name
 *   in the same row instead of appearing as a struck-through entry above it.
 *
 * Fix:
 *   Determine each player's display state purely from their own fields:
 *     - isOut == true            → show struck-through (dismissed, no matter what index)
 *     - index == strikerIndex
 *       AND isOut == false       → show as active striker (⚡ prefix, green highlight)
 *     - index == nonStrikerIdx
 *       AND isOut == false       → show as active non-striker (indent, green highlight)
 *     - hasNotBatted == true     → skip (not yet come to bat)
 *
 *   This makes every dismissed player always render as struck-through,
 *   independent of whether a later batsman happens to occupy the same
 *   list index position.
 */
public class InningsActivity extends AppCompatActivity {

    private CricketApp  app;
    private Match       match;
    private MatchEngine engine;

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
    private TextView     tvCurrentOverLabel;
    private TextView     tvBallsRemaining;
    private RecyclerView rvCurrentOverBalls;
    private RecyclerView rvOverHistory;

    // ── Ball input buttons ─────────────────────────────────────────────────────
    private Button btnDot, btn1, btn2, btn3, btn4, btn6;
    private Button btnWide, btnNoBall, btnWicket, btnUndo;

    // ── Adapters ───────────────────────────────────────────────────────────────
    private BallAdapter        ballAdapter;
    private OverHistoryAdapter overHistoryAdapter;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

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

    @Override
    protected void onPause() {
        super.onPause();
        if (match != null && !match.isMatchCompleted()) {
            LiveMatchState.persist(this, match);
        }
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
        btnDot.setOnClickListener(v    -> handleBall(0));
        btn1.setOnClickListener(v      -> handleBall(1));
        btn2.setOnClickListener(v      -> handleBall(2));
        btn3.setOnClickListener(v      -> handleBall(3));
        btn4.setOnClickListener(v      -> handleBall(4));
        btn6.setOnClickListener(v      -> handleBall(6));
        btnWide.setOnClickListener(v   -> handleMatchState(engine.deliverWide()));
        btnNoBall.setOnClickListener(v -> handleMatchState(engine.deliverNoBall()));
        btnWicket.setOnClickListener(v -> showWicketDialog());
        btnUndo.setOnClickListener(v   -> {
            if (!engine.undoLastBall()) {
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            } else {
                LiveMatchState.persist(this, match);
                refreshUI();
            }
        });
    }

    // ─── Delivery handlers ────────────────────────────────────────────────────

    private void handleBall(int runs) {
        handleMatchState(engine.deliverNormalBall(runs));
    }

    private void showWicketDialog() {
        List<Player> available = engine.getAvailableBatsmen();

        if (available.isEmpty()) {
            handleMatchState(engine.deliverWicket(engine.getNextBatsmanIndex()));
            return;
        }

        String[]           names   = new String[available.size()];
        final List<Player> batters = match.getCurrentBattingPlayers();
        for (int i = 0; i < available.size(); i++) {
            names[i] = (i + 1) + ". " + available.get(i).getName();
        }
        final int[] chosen = {0};
        new AlertDialog.Builder(this)
                .setTitle("Wicket — choose next batsman")
                .setSingleChoiceItems(names, 0, (d, which) -> chosen[0] = which)
                .setPositiveButton("Confirm", (d, which) -> {
                    Player p   = available.get(chosen[0]);
                    int    idx = batters.indexOf(p);
                    handleMatchState(engine.deliverWicket(idx));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Match state routing ──────────────────────────────────────────────────

    private void handleMatchState(MatchEngine.MatchState state) {
        switch (state) {
            case BALL_RECORDED:
                LiveMatchState.persist(this, match);
                refreshUI();
                break;
            case OVER_COMPLETE:
                LiveMatchState.persist(this, match);
                refreshUI();
                Toast.makeText(this,
                        "Over " + match.getCurrentInningsData().getCompletedOvers().size()
                                + " complete!", Toast.LENGTH_SHORT).show();
                break;
            case INNINGS_COMPLETE:
                LiveMatchState.persist(this, match);
                startActivity(new Intent(this, InningsBreakActivity.class));
                finish();
                break;
            case MATCH_COMPLETE:
                LiveMatchState.clear(this);
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

        tvInningsTitle.setText(inningsNum == 1 ? "1st Innings" : "2nd Innings");
        tvScore.setText(innings.getScoreString());
        tvOversInfo.setText(String.format(Locale.US, "Ov %s / %d",
                innings.getOversString(), match.getMaxOvers()));
        tvCRR.setText(String.format(Locale.US, "CRR: %.2f", innings.getCurrentRunRate()));

        tvModeBadge.setText(isSingle ? "Single bat" : "Two bat");
        tvModeBadge.setVisibility(View.VISIBLE);

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

        refreshBattingTable(innings, isSingle);
        refreshCurrentOver(innings);
        overHistoryAdapter.updateData(innings.getCompletedOvers());
    }

    /**
     * Rebuilds the batting scorecard table.
     *
     * FIXED rendering logic — each player is classified independently:
     *
     *   isOut == true
     *     → always render as struck-through "dismissed" row
     *       regardless of which index they occupy in the list
     *
     *   index == strikerIndex AND isOut == false
     *     → active striker: ⚡ prefix, green highlight row
     *
     *   index == nonStrikerIndex AND isOut == false  (two-batsman mode only)
     *     → active non-striker: indent, green highlight row
     *
     *   hasNotBatted == true
     *     → skip entirely (not yet come to bat)
     *
     * This means the table always shows:
     *   - Every player who has batted (dismissed or still at crease)
     *   - Dismissed rows in their original position with strikethrough
     *   - Current batsmen highlighted green at their actual positions
     */
    private void refreshBattingTable(Innings innings, boolean isSingle) {
        tableBatsmen.removeAllViews();

        // Header row
        addTableRow(tableBatsmen,
                new String[]{"Batsman", "R", "B", "4s", "6s", "SR"},
                true, false, false);

        List<Player> players       = match.getCurrentBattingPlayers();
        int          strikerIdx    = innings.getStrikerIndex();
        int          nonStrikerIdx = innings.getNonStrikerIndex(); // -1 in single mode

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);

            // ── Skip players yet to bat ────────────────────────────────
            // A player has "yet to bat" only if hasNotBatted is true
            // AND they are not currently at the crease.
            // Check crease status by index (not by isOut) so we correctly
            // identify the current striker even after multiple wickets.
            boolean isCurrentStriker    = (i == strikerIdx)    && !p.isOut();
            boolean isCurrentNonStriker = (!isSingle)
                                          && (i == nonStrikerIdx) && !p.isOut();
            boolean isAtCrease          = isCurrentStriker || isCurrentNonStriker;

            if (p.isHasNotBatted() && !isAtCrease) continue;

            // ── Determine display state ────────────────────────────────
            // isOut drives struck-through display independently of index
            boolean showDismissed = p.isOut();

            String name;
            if (isCurrentStriker) {
                name = "⚡ " + p.getName();
            } else if (isCurrentNonStriker) {
                name = "  " + p.getName();
            } else {
                name = p.getName();
            }

            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";

            addTableRow(tableBatsmen,
                    new String[]{name,
                            String.valueOf(p.getRunsScored()),
                            String.valueOf(p.getBallsFaced()),
                            String.valueOf(p.getFours()),
                            String.valueOf(p.getSixes()),
                            sr},
                    false,
                    isAtCrease,    // green highlight
                    showDismissed  // strikethrough
            );
        }
    }

    /**
     * Creates and adds one row to the batting table.
     *
     * @param isActive    true → green background (batsman at crease)
     * @param isOut       true → grey text + strikethrough on name column
     */
    private void addTableRow(TableLayout table, String[] cells,
                             boolean isHeader, boolean isActive, boolean isOut) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        if (isActive) {
            row.setBackgroundColor(Color.parseColor("#E1F5EE"));
        }

        int[] weights = {3, 1, 1, 1, 1, 1};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, weights[i]));
            tv.setText(cells[i]);
            tv.setPadding(12, 10, 12, 10);
            tv.setTextSize(12f);

            if (isHeader) {
                tv.setTextColor(Color.parseColor("#888780"));
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (isOut) {
                // Dismissed: grey text, name column gets strikethrough
                tv.setTextColor(Color.parseColor("#AAAAAA"));
                if (i == 0) {
                    tv.setPaintFlags(
                            tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                }
            } else {
                tv.setTextColor(Color.parseColor("#111111"));
            }
            row.addView(tv);
        }
        table.addView(row);
    }

    // ─── Current over ─────────────────────────────────────────────────────────

    private void refreshCurrentOver(Innings innings) {
        Over currentOver      = innings.getCurrentOver();
        int  completedOverCnt = innings.getCompletedOvers().size();

        tvCurrentOverLabel.setText("Over " + (completedOverCnt + 1));

        int validCount = currentOver != null ? currentOver.getValidBallCount() : 0;
        int remaining  = 6 - validCount;
        tvBallsRemaining.setText(
                remaining + (remaining == 1 ? " ball left" : " balls left"));

        List<Ball> displayBalls = new ArrayList<>();
        if (currentOver != null) displayBalls.addAll(currentOver.getBalls());
        for (int i = 0; i < Math.max(0, 6 - validCount); i++) displayBalls.add(null);
        ballAdapter.updateData(displayBalls);
    }
}
