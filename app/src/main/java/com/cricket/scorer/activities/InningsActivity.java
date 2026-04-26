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
 * CHANGE — Undo on opener selection:
 *
 * When the user taps Undo and no balls have been bowled yet in the
 * current innings (current over is empty AND no completed overs exist),
 * instead of showing "Nothing to undo" the app:
 *
 *   1. Resets all opener state:
 *        - strikerIndex reset to 0 (arbitrary, will be overwritten)
 *        - nonStrikerIndex reset to -1 (single) or 1 (two)
 *        - hasNotBatted set back to true for ALL batting-team players
 *        - nextBatsmanIndex reset
 *   2. Disables ball input buttons
 *   3. Reopens showOpenerSelectionDialog() so the user can re-pick
 *
 * This works for both 1st and 2nd innings.
 * It also works in two-batsman mode — both the striker and non-striker
 * dialogs are shown again from scratch.
 *
 * If at least one ball has been bowled (even a wide or no-ball), normal
 * undo behaviour applies (engine.undoLastBall()).
 */
public class InningsActivity extends AppCompatActivity {

    private CricketApp  app;
    private Match       match;
    private MatchEngine engine;

    // ── Scoreboard ────────────────────────────────────────────────────────────
    private TextView     tvInningsTitle, tvScore, tvOversInfo, tvCRR, tvRRR, tvModeBadge;
    private LinearLayout layoutTargetBanner;
    private TextView     tvTargetInfo, tvRequiredBalls;

    // ── Batting table ─────────────────────────────────────────────────────────
    private TableLayout tableBatsmen;

    // ── Over tracker ──────────────────────────────────────────────────────────
    private TextView     tvCurrentOverLabel, tvBallsRemaining;
    private RecyclerView rvCurrentOverBalls, rvOverHistory;

    // ── Ball input buttons ────────────────────────────────────────────────────
    private Button btnDot, btn1, btn2, btn3, btn4, btn6;
    private Button btnWide, btnNoBall, btnWicket, btnUndo;

    // ── Adapters ──────────────────────────────────────────────────────────────
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

        // Disable ball buttons until openers are confirmed
        setBallButtonsEnabled(false);

        refreshUI();

        // Show opener dialog only if innings is brand new (no balls at all)
        if (isInningsJustStarted()) {
            showOpenerSelectionDialog();
        } else {
            setBallButtonsEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (match != null && !match.isMatchCompleted())
            LiveMatchState.persist(this, match);
    }

    // ─── Innings start check ──────────────────────────────────────────────────

    /**
     * Returns true when no deliveries have been recorded in this innings yet —
     * i.e. the current over is empty and no overs have been completed.
     * Used to decide whether to show the opener dialog or re-show it on undo.
     */
    private boolean isInningsJustStarted() {
        Innings innings     = match.getCurrentInningsData();
        Over    currentOver = innings.getCurrentOver();
        boolean overEmpty   = currentOver == null || currentOver.getBalls().isEmpty();
        return overEmpty && innings.getCompletedOvers().isEmpty();
    }

    // ─── Opener selection dialogs ─────────────────────────────────────────────

    /**
     * Resets all opener state for the current innings and re-shows the
     * opener selection dialog. Called by the Undo button when no balls
     * have been bowled yet.
     */
    private void resetAndReshowOpeners() {
        Innings      innings = match.getCurrentInningsData();
        List<Player> players = match.getCurrentBattingPlayers();
        boolean      single  = match.isSingleBatsmanMode();

        // Reset every player to hasNotBatted=true so the full list is shown
        for (Player p : players) {
            p.setHasNotBatted(true);
            p.setOut(false);
        }

        // Reset innings opener indices to defaults
        innings.setStrikerIndex(0);
        innings.setNonStrikerIndex(single ? -1 : 1);
        innings.setNextBatsmanIndex(single ? 1 : 2);

        LiveMatchState.persist(this, match);
        setBallButtonsEnabled(false);
        refreshUI();

        // Reopen the dialog
        showOpenerSelectionDialog();
    }

    /**
     * First opener dialog — picker for the striker.
     * Shows all players in the batting team.
     */
    private void showOpenerSelectionDialog() {
        List<Player> players = match.getCurrentBattingPlayers();
        boolean      single  = match.isSingleBatsmanMode();
        Innings      innings = match.getCurrentInningsData();

        String[] names = new String[players.size()];
        for (int i = 0; i < players.size(); i++) {
            names[i] = (i + 1) + ". " + players.get(i).getName();
        }

        final int[] strikerChoice = {0};

        new AlertDialog.Builder(this)
                .setTitle("Select opening striker")
                .setCancelable(false)
                .setSingleChoiceItems(names, 0, (d, which) -> strikerChoice[0] = which)
                .setPositiveButton("Confirm", (d, which) -> {
                    int strikerIdx = strikerChoice[0];
                    innings.setStrikerIndex(strikerIdx);
                    players.get(strikerIdx).setHasNotBatted(false);

                    if (single) {
                        innings.setNonStrikerIndex(-1);
                        innings.setNextBatsmanIndex(
                                nextAvailableIndex(players, strikerIdx, -1));
                        LiveMatchState.persist(this, match);
                        setBallButtonsEnabled(true);
                        refreshUI();
                    } else {
                        showNonStrikerDialog(strikerIdx);
                    }
                })
                .show();
    }

    /**
     * Second opener dialog (two-batsman mode only) — picks the non-striker.
     * Excludes the already-chosen striker from the list.
     */
    private void showNonStrikerDialog(int strikerIdx) {
        List<Player>  players          = match.getCurrentBattingPlayers();
        Innings       innings          = match.getCurrentInningsData();
        List<Integer> candidateIndices = new ArrayList<>();
        List<String>  candidateNames   = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            if (i != strikerIdx) {
                candidateIndices.add(i);
                candidateNames.add((i + 1) + ". " + players.get(i).getName());
            }
        }

        final int[] nonStrikerChoice = {0};

        new AlertDialog.Builder(this)
                .setTitle("Select non-striker")
                .setCancelable(false)
                .setSingleChoiceItems(
                        candidateNames.toArray(new String[0]), 0,
                        (d, which) -> nonStrikerChoice[0] = which)
                .setPositiveButton("Confirm", (d, which) -> {
                    int nonStrikerIdx = candidateIndices.get(nonStrikerChoice[0]);
                    innings.setNonStrikerIndex(nonStrikerIdx);
                    players.get(nonStrikerIdx).setHasNotBatted(false);
                    innings.setNextBatsmanIndex(
                            nextAvailableIndex(players, strikerIdx, nonStrikerIdx));
                    LiveMatchState.persist(this, match);
                    setBallButtonsEnabled(true);
                    refreshUI();
                })
                .show();
    }

    /**
     * Returns the index of the first player who is neither the striker
     * nor the non-striker — the next one to walk in after a wicket.
     */
    private int nextAvailableIndex(List<Player> players, int strikerIdx, int nonStrikerIdx) {
        for (int i = 0; i < players.size(); i++) {
            if (i != strikerIdx && i != nonStrikerIdx) return i;
        }
        return players.size();
    }

    // ─── Enable / disable ball input buttons ──────────────────────────────────

    private void setBallButtonsEnabled(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.35f;
        btnDot.setEnabled(enabled);    btnDot.setAlpha(alpha);
        btn1.setEnabled(enabled);      btn1.setAlpha(alpha);
        btn2.setEnabled(enabled);      btn2.setAlpha(alpha);
        btn3.setEnabled(enabled);      btn3.setAlpha(alpha);
        btn4.setEnabled(enabled);      btn4.setAlpha(alpha);
        btn6.setEnabled(enabled);      btn6.setAlpha(alpha);
        btnWide.setEnabled(enabled);   btnWide.setAlpha(alpha);
        btnNoBall.setEnabled(enabled); btnNoBall.setAlpha(alpha);
        btnWicket.setEnabled(enabled); btnWicket.setAlpha(alpha);
        // Undo stays always enabled — it's the escape hatch back to opener selection
        btnUndo.setEnabled(true);
        btnUndo.setAlpha(1.0f);
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

        btnUndo.setOnClickListener(v -> {
            if (isInningsJustStarted()) {
                // ── No balls bowled yet — re-open opener selection ─────────
                // This lets the user change the opening batsman even after
                // having already confirmed the opener dialog once.
                resetAndReshowOpeners();
            } else {
                // ── Normal undo — reverse the last delivery ────────────────
                if (!engine.undoLastBall()) {
                    Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
                } else {
                    LiveMatchState.persist(this, match);

                    // If undo brought us back to zero balls in the innings,
                    // re-enable the escape hatch (buttons already handled by
                    // setBallButtonsEnabled, but no dialog needed here since
                    // at least one ball was played — the user can undo further
                    // if they tap undo again, which will hit isInningsJustStarted).
                    refreshUI();
                }
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
        String[] names = new String[available.size()];
        final List<Player> batters = match.getCurrentBattingPlayers();
        for (int i = 0; i < available.size(); i++)
            names[i] = (i + 1) + ". " + available.get(i).getName();
        final int[] chosen = {0};
        new AlertDialog.Builder(this)
                .setTitle("Wicket — choose next batsman")
                .setSingleChoiceItems(names, 0, (d, w) -> chosen[0] = w)
                .setPositiveButton("Confirm", (d, w) ->
                        handleMatchState(engine.deliverWicket(
                                batters.indexOf(available.get(chosen[0])))))
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

    private void refreshBattingTable(Innings innings, boolean isSingle) {
        tableBatsmen.removeAllViews();
        addTableRow(new String[]{"Batsman","R","B","4s","6s","SR"}, true, false, false);

        List<Player> players       = match.getCurrentBattingPlayers();
        int          strikerIdx    = innings.getStrikerIndex();
        int          nonStrikerIdx = innings.getNonStrikerIndex();

        for (int i = 0; i < players.size(); i++) {
            Player  p                   = players.get(i);
            boolean isCurrentStriker    = (i == strikerIdx)    && !p.isOut();
            boolean isCurrentNonStriker = !isSingle && (i == nonStrikerIdx) && !p.isOut();
            boolean isAtCrease          = isCurrentStriker || isCurrentNonStriker;

            if (p.isHasNotBatted() && !isAtCrease) continue;

            String name = isCurrentStriker    ? "⚡ " + p.getName()
                        : isCurrentNonStriker ? "  " + p.getName()
                        : p.getName();
            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";

            addTableRow(new String[]{name,
                    String.valueOf(p.getRunsScored()),
                    String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()),
                    String.valueOf(p.getSixes()), sr},
                    false, isAtCrease, p.isOut());
        }
    }

    private void addTableRow(String[] cells, boolean isHeader, boolean isActive, boolean isOut) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        if (isActive)      row.setBackgroundColor(Color.parseColor("#E1F5EE"));
        else if (isHeader) row.setBackgroundColor(Color.parseColor("#F1EFE8"));

        int[] weights = {3,1,1,1,1,1};
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
                tv.setTextColor(Color.parseColor("#AAAAAA"));
                if (i == 0) tv.setPaintFlags(
                        tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                tv.setTextColor(Color.parseColor("#111111"));
            }
            row.addView(tv);
        }
        tableBatsmen.addView(row);
    }

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
