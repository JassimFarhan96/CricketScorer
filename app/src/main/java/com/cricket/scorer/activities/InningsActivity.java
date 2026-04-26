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
 * BUG FIX — opener selection at innings start:
 *
 * Previously, the opening batsmen were always fixed to player indices 0 and 1
 * (set in SetupActivity / MatchEngine.endInnings). The user had no way to
 * choose who bats first — they could only pick batsmen AFTER a wicket fell.
 *
 * Fix:
 *   showOpenerSelectionDialog() is called from onCreate() whenever
 *   no balls have been bowled yet in the current innings
 *   (innings.getTotalValidBalls() == 0 AND completedOvers is empty).
 *
 *   Two-batsman mode: two sequential dialogs — first pick the striker,
 *   then pick the non-striker from the remaining players.
 *
 *   Single-batsman mode: one dialog — pick the striker only.
 *
 *   The ball input buttons are DISABLED until the opener dialog is confirmed,
 *   preventing scoring before openers are chosen.
 *
 *   If the match was restored from LiveMatchState (mid-innings, balls > 0),
 *   the dialog is skipped entirely — openers were already set.
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

        // Show opener selection only if no balls have been bowled yet
        Innings innings = match.getCurrentInningsData();
        boolean isInningsJustStarted = innings.getTotalValidBalls() == 0
                && innings.getCompletedOvers().isEmpty()
                && (innings.getCurrentOver() == null
                    || innings.getCurrentOver().getBalls().isEmpty());

        if (isInningsJustStarted) {
            showOpenerSelectionDialog();
        } else {
            // Mid-innings restore — openers already set, enable buttons
            setBallButtonsEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (match != null && !match.isMatchCompleted())
            LiveMatchState.persist(this, match);
    }

    // ─── Opener selection dialog ──────────────────────────────────────────────

    /**
     * Shows a dialog for the user to pick the opening striker.
     * In two-batsman mode, a second dialog immediately follows to pick
     * the non-striker from the remaining players.
     *
     * Ball buttons remain disabled until both selections are confirmed.
     */
    private void showOpenerSelectionDialog() {
        List<Player> players = match.getCurrentBattingPlayers();
        Innings      innings = match.getCurrentInningsData();
        boolean      isSingle = match.isSingleBatsmanMode();

        // Build name list for the striker picker
        String[] names = new String[players.size()];
        for (int i = 0; i < players.size(); i++) {
            names[i] = (i + 1) + ". " + players.get(i).getName();
        }

        final int[] strikerChoice = {0}; // default: first player

        new AlertDialog.Builder(this)
                .setTitle("Select opening striker")
                .setCancelable(false) // must choose before scoring
                .setSingleChoiceItems(names, 0, (d, which) -> strikerChoice[0] = which)
                .setPositiveButton("Confirm", (d, which) -> {
                    // Apply striker selection
                    int strikerIdx = strikerChoice[0];
                    innings.setStrikerIndex(strikerIdx);
                    players.get(strikerIdx).setHasNotBatted(false);

                    if (isSingle) {
                        // Single mode: only one opener needed
                        innings.setNonStrikerIndex(-1);
                        innings.setNextBatsmanIndex(nextAvailableIndex(players, strikerIdx, -1));
                        LiveMatchState.persist(this, match);
                        setBallButtonsEnabled(true);
                        refreshUI();
                    } else {
                        // Two-batsman mode: now pick the non-striker
                        showNonStrikerDialog(strikerIdx);
                    }
                })
                .show();
    }

    /**
     * Second dialog (two-batsman mode only) — picks the non-striker
     * from the players who were NOT chosen as striker.
     *
     * @param strikerIdx the index already confirmed as the striker
     */
    private void showNonStrikerDialog(int strikerIdx) {
        List<Player> players = match.getCurrentBattingPlayers();
        Innings      innings = match.getCurrentInningsData();

        // Build list excluding the chosen striker
        List<Integer> candidateIndices = new ArrayList<>();
        List<String>  candidateNames   = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (i != strikerIdx) {
                candidateIndices.add(i);
                candidateNames.add((i + 1) + ". " + players.get(i).getName());
            }
        }

        String[] names = candidateNames.toArray(new String[0]);
        final int[] nonStrikerChoice = {0}; // index into candidateIndices

        new AlertDialog.Builder(this)
                .setTitle("Select non-striker")
                .setCancelable(false)
                .setSingleChoiceItems(names, 0, (d, which) -> nonStrikerChoice[0] = which)
                .setPositiveButton("Confirm", (d, which) -> {
                    int nonStrikerIdx = candidateIndices.get(nonStrikerChoice[0]);
                    innings.setNonStrikerIndex(nonStrikerIdx);
                    players.get(nonStrikerIdx).setHasNotBatted(false);

                    // Next batsman is whoever comes after both openers
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
     * nor the non-striker — i.e., the next one to walk in after a wicket.
     */
    private int nextAvailableIndex(List<Player> players, int strikerIdx, int nonStrikerIdx) {
        for (int i = 0; i < players.size(); i++) {
            if (i != strikerIdx && i != nonStrikerIdx) return i;
        }
        return players.size(); // shouldn't happen with valid team sizes
    }

    // ─── Enable / disable ball input buttons ──────────────────────────────────

    /**
     * Enables or disables all ball input buttons.
     * Disabled during opener selection so the scorer cannot record
     * a delivery before batsmen have been chosen.
     */
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
        btnUndo.setEnabled(enabled);   btnUndo.setAlpha(alpha);
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
            if (!engine.undoLastBall())
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            else { LiveMatchState.persist(this, match); refreshUI(); }
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
            boolean isCurrentStriker    = (i == strikerIdx) && !p.isOut();
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
