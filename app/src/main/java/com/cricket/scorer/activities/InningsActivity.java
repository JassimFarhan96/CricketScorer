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
 * CHANGE — Bowler selection:
 *
 * At the start of every over (including over 1) a dialog prompts the
 * scorer to choose which bowling-team player will bowl that over.
 * Ball buttons are disabled until the bowler is confirmed.
 *
 * Undo behaviour:
 *   - If no balls have been bowled in the current over yet (over is empty)
 *     AND a bowler has already been selected for this over:
 *       → reset the bowler selection and reopen the bowler dialog
 *         (lets scorer change the bowler before the first ball).
 *   - If no balls AND no bowler selected AND innings just started:
 *       → reopen the opener selection dialog (original behaviour).
 *   - If balls have been bowled: normal undo of last delivery.
 *
 * Over history panel:
 *   Below the completed over rows, a "BOWLING" section lists each bowler
 *   and the number of complete overs they have bowled this innings.
 */
public class InningsActivity extends AppCompatActivity {

    private CricketApp  app;
    private Match       match;
    private MatchEngine engine;

    private TextView     tvInningsTitle, tvScore, tvOversInfo, tvCRR, tvRRR, tvModeBadge;
    private LinearLayout layoutTargetBanner;
    private TextView     tvTargetInfo, tvRequiredBalls;
    private TableLayout  tableBatsmen;
    private TextView     tvCurrentOverLabel, tvBallsRemaining;
    private TextView     tvCurrentBowler;       // shows "Bowling: PlayerName"
    private RecyclerView rvCurrentOverBalls, rvOverHistory;
    private TextView     tvBowlerSummary;        // bowler overs summary below history
    private Button       btnDot, btn1, btn2, btn3, btn4, btn6;
    private Button       btnWide, btnNoBall, btnWicket, btnUndo;

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
        setBallButtonsEnabled(false);
        refreshUI();

        Innings innings = match.getCurrentInningsData();

        if (isInningsJustStarted()) {
            // Brand new innings — pick openers first, then bowler
            showOpenerSelectionDialog();
        } else if (!innings.isBowlerSelected()) {
            // Restored mid-over but bowler not yet chosen (e.g. app killed just
            // after over ended before bowler dialog was confirmed)
            showBowlerSelectionDialog();
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

    // ─── State helpers ────────────────────────────────────────────────────────

    /** True when no balls at all have been bowled in this innings. */
    private boolean isInningsJustStarted() {
        Innings inn = match.getCurrentInningsData();
        Over    cur = inn.getCurrentOver();
        return (cur == null || cur.getBalls().isEmpty()) && inn.getCompletedOvers().isEmpty();
    }

    /** True when the current over has no balls yet (but may be 2nd over+). */
    private boolean isCurrentOverEmpty() {
        Over cur = match.getCurrentInningsData().getCurrentOver();
        return cur == null || cur.getBalls().isEmpty();
    }

    // ─── Opener selection dialogs (unchanged logic) ───────────────────────────

    private void showOpenerSelectionDialog() {
        List<Player> players = match.getCurrentBattingPlayers();
        Innings      innings = match.getCurrentInningsData();
        boolean      single  = match.isSingleBatsmanMode();
        String[]     names   = new String[players.size()];
        for (int i = 0; i < players.size(); i++) names[i] = (i+1) + ". " + players.get(i).getName();
        final int[] sc = {0};
        new AlertDialog.Builder(this)
                .setTitle("Select opening striker")
                .setCancelable(false)
                .setSingleChoiceItems(names, 0, (d, w) -> sc[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    int si = sc[0];
                    innings.setStrikerIndex(si);
                    players.get(si).setHasNotBatted(false);
                    if (single) {
                        innings.setNonStrikerIndex(-1);
                        innings.setNextBatsmanIndex(nextAvail(players, si, -1));
                        LiveMatchState.persist(this, match);
                        // After openers chosen, pick the bowler
                        showBowlerSelectionDialog();
                    } else {
                        showNonStrikerDialog(si);
                    }
                }).show();
    }

    private void showNonStrikerDialog(int strikerIdx) {
        List<Player>  players  = match.getCurrentBattingPlayers();
        Innings       innings  = match.getCurrentInningsData();
        List<Integer> ci       = new ArrayList<>();
        List<String>  cn       = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (i != strikerIdx) { ci.add(i); cn.add((i+1) + ". " + players.get(i).getName()); }
        }
        final int[] nc = {0};
        new AlertDialog.Builder(this)
                .setTitle("Select non-striker")
                .setCancelable(false)
                .setSingleChoiceItems(cn.toArray(new String[0]), 0, (d, w) -> nc[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    int ni = ci.get(nc[0]);
                    innings.setNonStrikerIndex(ni);
                    players.get(ni).setHasNotBatted(false);
                    innings.setNextBatsmanIndex(nextAvail(players, strikerIdx, ni));
                    LiveMatchState.persist(this, match);
                    // After openers, pick bowler
                    showBowlerSelectionDialog();
                }).show();
    }

    // ─── Bowler selection dialog ──────────────────────────────────────────────

    /**
     * Shows a dialog to select the bowler for the current over.
     * Lists all players from the BOWLING team.
     * Disables ball buttons until confirmed.
     * Called:
     *   - After openers are chosen (start of innings)
     *   - After each over completes
     *   - When undo is pressed with an empty over (change of bowler)
     */
    private void showBowlerSelectionDialog() {
        setBallButtonsEnabled(false);

        Innings      innings = match.getCurrentInningsData();
        List<Player> bowlers = getBowlingTeamPlayers();
        int          ovNum   = innings.getCompletedOvers().size() + 1;

        String[] names = new String[bowlers.size()];
        for (int i = 0; i < bowlers.size(); i++) {
            Player p        = bowlers.get(i);
            String oversStr = getOversForBowler(innings, p.getName());
            names[i]        = (i+1) + ". " + p.getName() + oversStr;
        }

        // Pre-select the last bowler if there was one, otherwise 0
        int defaultSelection = 0;
        if (innings.getCurrentBowlerIndex() >= 0
                && innings.getCurrentBowlerIndex() < bowlers.size()) {
            defaultSelection = innings.getCurrentBowlerIndex();
        }

        final int[] chosen = {defaultSelection};

        new AlertDialog.Builder(this)
                .setTitle("Over " + ovNum + " — Select bowler")
                .setCancelable(false)
                .setSingleChoiceItems(names, defaultSelection, (d, w) -> chosen[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    int    idx  = chosen[0];
                    String name = bowlers.get(idx).getName();
                    innings.setCurrentOverBowler(idx, name);
                    LiveMatchState.persist(this, match);
                    setBallButtonsEnabled(true);
                    refreshUI();
                }).show();
    }

    /**
     * Returns a formatted string showing how many overs a bowler has bowled.
     * E.g. "  (2 ov)" or "" if none yet.
     */
    private String getOversForBowler(Innings innings, String name) {
        int count = 0;
        if (innings.getBowlerOversMap() != null
                && innings.getBowlerOversMap().containsKey(name)) {
            count = innings.getBowlerOversMap().get(name);
        }
        return count > 0 ? "  (" + count + " ov)" : "";
    }

    /** Returns the list of players from the team currently bowling. */
    private List<Player> getBowlingTeamPlayers() {
        int     inningsNum = match.getCurrentInnings();
        boolean homeFirst  = match.getBattingFirstTeam().equals("home");
        if (inningsNum == 1) {
            return homeFirst ? match.getAwayPlayers() : match.getHomePlayers();
        } else {
            return homeFirst ? match.getHomePlayers() : match.getAwayPlayers();
        }
    }

    // ─── Reset helpers ────────────────────────────────────────────────────────

    private void resetAndReshowOpeners() {
        Innings      innings = match.getCurrentInningsData();
        List<Player> players = match.getCurrentBattingPlayers();
        boolean      single  = match.isSingleBatsmanMode();
        for (Player p : players) { p.setHasNotBatted(true); p.setOut(false); }
        innings.setStrikerIndex(0);
        innings.setNonStrikerIndex(single ? -1 : 1);
        innings.setNextBatsmanIndex(single ? 1 : 2);
        innings.setBowlerSelected(false);
        innings.setCurrentBowlerIndex(-1);
        innings.setCurrentBowlerName("");
        LiveMatchState.persist(this, match);
        setBallButtonsEnabled(false);
        refreshUI();
        showOpenerSelectionDialog();
    }

    private void resetAndReshowBowler() {
        Innings innings = match.getCurrentInningsData();
        innings.setBowlerSelected(false);
        innings.setCurrentBowlerIndex(-1);
        innings.setCurrentBowlerName("");
        if (innings.getCurrentOver() != null) {
            innings.getCurrentOver().setBowlerIndex(-1);
            innings.getCurrentOver().setBowlerName("");
        }
        LiveMatchState.persist(this, match);
        refreshUI();
        showBowlerSelectionDialog();
    }

    private int nextAvail(List<Player> pl, int si, int ni) {
        for (int i = 0; i < pl.size(); i++) if (i != si && i != ni) return i;
        return pl.size();
    }

    // ─── Enable/disable buttons ───────────────────────────────────────────────

    private void setBallButtonsEnabled(boolean e) {
        float a = e ? 1f : 0.35f;
        btnDot.setEnabled(e);    btnDot.setAlpha(a);
        btn1.setEnabled(e);      btn1.setAlpha(a);
        btn2.setEnabled(e);      btn2.setAlpha(a);
        btn3.setEnabled(e);      btn3.setAlpha(a);
        btn4.setEnabled(e);      btn4.setAlpha(a);
        btn6.setEnabled(e);      btn6.setAlpha(a);
        btnWide.setEnabled(e);   btnWide.setAlpha(a);
        btnNoBall.setEnabled(e); btnNoBall.setAlpha(a);
        btnWicket.setEnabled(e); btnWicket.setAlpha(a);
        btnUndo.setEnabled(true); btnUndo.setAlpha(1f);
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
        tvCurrentBowler    = findViewById(R.id.tv_current_bowler);
        rvCurrentOverBalls = findViewById(R.id.rv_current_over_balls);
        rvOverHistory      = findViewById(R.id.rv_over_history);
        tvBowlerSummary    = findViewById(R.id.tv_bowler_summary);
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
            if (isCurrentOverEmpty()) {
                if (isInningsJustStarted()) {
                    // Very first ball of innings — reopen opener selection
                    resetAndReshowOpeners();
                } else {
                    // Start of a subsequent over — reopen bowler selection
                    resetAndReshowBowler();
                }
            } else {
                // Normal undo of last ball
                if (!engine.undoLastBall()) {
                    Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
                } else {
                    LiveMatchState.persist(this, match);
                    refreshUI();
                }
            }
        });
    }

    private void handleBall(int runs) { handleMatchState(engine.deliverNormalBall(runs)); }

    private void showWicketDialog() {
        List<Player> available = engine.getAvailableBatsmen();
        if (available.isEmpty()) {
            handleMatchState(engine.deliverWicket(engine.getNextBatsmanIndex()));
            return;
        }
        String[]           names   = new String[available.size()];
        final List<Player> batters = match.getCurrentBattingPlayers();
        for (int i = 0; i < available.size(); i++)
            names[i] = (i+1) + ". " + available.get(i).getName();
        final int[] chosen = {0};
        new AlertDialog.Builder(this)
                .setTitle("Wicket — choose next batsman")
                .setSingleChoiceItems(names, 0, (d, w) -> chosen[0] = w)
                .setPositiveButton("Confirm", (d, w) ->
                        handleMatchState(engine.deliverWicket(
                                batters.indexOf(available.get(chosen[0])))))
                .setNegativeButton("Cancel", null).show();
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
                int oversDone = match.getCurrentInningsData().getCompletedOvers().size();
                Toast.makeText(this, "Over " + oversDone + " complete!", Toast.LENGTH_SHORT).show();
                // Show bowler selection for the next over
                showBowlerSelectionDialog();
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

        // Current bowler label
        if (innings.isBowlerSelected() && !innings.getCurrentBowlerName().isEmpty()) {
            tvCurrentBowler.setText("Bowling: " + innings.getCurrentBowlerName());
            tvCurrentBowler.setVisibility(View.VISIBLE);
        } else {
            tvCurrentBowler.setVisibility(View.GONE);
        }

        if (inningsNum == 2) {
            int target = match.getTarget();
            layoutTargetBanner.setVisibility(View.VISIBLE);
            tvRRR.setVisibility(View.VISIBLE);
            tvTargetInfo.setText("Target: " + target);
            tvRequiredBalls.setText("Need " + innings.getRunsNeeded(target)
                    + " off " + innings.getBallsRemaining(match.getMaxOvers()) + " balls");
            tvRRR.setText(String.format(Locale.US, "RRR: %.2f",
                    innings.getRequiredRunRate(target, match.getMaxOvers())));
        } else {
            layoutTargetBanner.setVisibility(View.GONE);
            tvRRR.setVisibility(View.GONE);
        }

        refreshBattingTable(innings, isSingle);
        refreshCurrentOver(innings);
        overHistoryAdapter.updateData(innings.getCompletedOvers());

        // Bowler summary below over history
        String summary = innings.getBowlerSummary();
        if (!summary.isEmpty()) {
            tvBowlerSummary.setText(summary);
            tvBowlerSummary.setVisibility(View.VISIBLE);
        } else {
            tvBowlerSummary.setVisibility(View.GONE);
        }
    }

    private void refreshBattingTable(Innings innings, boolean isSingle) {
        tableBatsmen.removeAllViews();
        addTableRow(new String[]{"Batsman","R","B","4s","6s","SR"}, true, false, false);
        List<Player> players       = match.getCurrentBattingPlayers();
        int          strikerIdx    = innings.getStrikerIndex();
        int          nonStrikerIdx = innings.getNonStrikerIndex();
        for (int i = 0; i < players.size(); i++) {
            Player  p                   = players.get(i);
            boolean isStriker           = (i == strikerIdx) && !p.isOut();
            boolean isNonStriker        = !isSingle && (i == nonStrikerIdx) && !p.isOut();
            boolean atCrease            = isStriker || isNonStriker;
            if (p.isHasNotBatted() && !atCrease) continue;
            String name = isStriker    ? "⚡ " + p.getName()
                        : isNonStriker ? "  " + p.getName()
                        : p.getName();
            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";
            addTableRow(new String[]{name, String.valueOf(p.getRunsScored()),
                    String.valueOf(p.getBallsFaced()), String.valueOf(p.getFours()),
                    String.valueOf(p.getSixes()), sr},
                    false, atCrease, p.isOut());
        }
    }

    private void addTableRow(String[] cells, boolean hdr, boolean active, boolean out) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (active)    row.setBackgroundColor(Color.parseColor("#E1F5EE"));
        else if (hdr)  row.setBackgroundColor(Color.parseColor("#F1EFE8"));
        int[] w = {3,1,1,1,1,1};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, w[i]));
            tv.setText(cells[i]); tv.setPadding(12, 10, 12, 10); tv.setTextSize(12f);
            if (hdr) {
                tv.setTextColor(Color.parseColor("#888780"));
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (out) {
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
        tvBallsRemaining.setText(remaining + (remaining == 1 ? " ball left" : " balls left"));
        List<Ball> displayBalls = new ArrayList<>();
        if (currentOver != null) displayBalls.addAll(currentOver.getBalls());
        for (int i = 0; i < Math.max(0, 6 - validCount); i++) displayBalls.add(null);
        ballAdapter.updateData(displayBalls);
    }
}
