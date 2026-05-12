package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.cricket.scorer.utils.ShakeDetector;
import com.cricket.scorer.utils.BugReportUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * InningsActivity.java
 *
 * CHANGE — Retired Hurt:
 *
 * New "Retired Hurt" button added to the ball input panel.
 * Tapping it shows a confirmation dialog (since it affects the current
 * striker), then asks who replaces them at the crease.
 *
 * When all active batsmen are dismissed (PROMPT_RETIRED_HURT state):
 *   For each retired-hurt player in order, a dialog appears:
 *   "[Player] — Do you want to return to bat?"
 *     [Return to bat] → player resumes at striker's end
 *     [Retire out]    → counted as dismissed (retired out)
 *   After ALL retired-hurt players are resolved, innings ends if no
 *   batsmen remain, or play continues if someone returns.
 *
 * Batting table: retired-hurt players shown with "Retired Hurt" status
 * in amber, distinguished from "Out" (strikethrough) and "Not out".
 */
public class InningsActivity extends AppCompatActivity {

    private CricketApp  app;
    private Match       match;
    private MatchEngine engine;
    private ShakeDetector shakeDetector;

    private TextView     tvInningsTitle, tvScore, tvOversInfo, tvCRR, tvRRR, tvModeBadge;
    private LinearLayout layoutTargetBanner;
    private TextView     tvTargetInfo, tvRequiredBalls;
    private TableLayout  tableBatsmen;
    private TextView     tvCurrentOverLabel, tvBallsRemaining, tvCurrentBowler;
    private RecyclerView rvCurrentOverBalls, rvOverHistory;
    private TextView     tvBowlerSummary;
    private Button       btnDot, btn1, btn2, btn3, btn4, btn6;
    private Button       btnWide, btnNoBall, btnWicket, btnRetiredHurt, btnBabyOver, btnUndo, btnEditOvers;

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
        setBallButtonsEnabled(false);
        refreshUI();

        Innings innings = match.getCurrentInningsData();
        if (isInningsJustStarted()) {
            showOpenerSelectionDialog();
        } else if (!innings.isBowlerSelected()) {
            showBowlerSelectionDialog();
        } else {
            setBallButtonsEnabled(true);
        }

        // Shake-to-report
        shakeDetector = new ShakeDetector(this, () -> BugReportUtils.launch(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shakeDetector != null) shakeDetector.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (shakeDetector != null) shakeDetector.stop();
        if (match != null && !match.isMatchCompleted())
            LiveMatchState.persist(this, match);
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    private boolean isInningsJustStarted() {
        Innings inn = match.getCurrentInningsData();
        Over    cur = inn.getCurrentOver();
        return (cur == null || cur.getBalls().isEmpty()) && inn.getCompletedOvers().isEmpty();
    }

    private boolean isCurrentOverEmpty() {
        Over cur = match.getCurrentInningsData().getCurrentOver();
        return cur == null || cur.getBalls().isEmpty();
    }

    // ─── Opener + Bowler dialogs (unchanged) ─────────────────────────────────

    private void showOpenerSelectionDialog() {
        List<Player> players = match.getCurrentBattingPlayers();
        Innings      innings = match.getCurrentInningsData();
        boolean      single  = match.isSingleBatsmanMode();
        String[]     names   = new String[players.size()];
        for (int i = 0; i < players.size(); i++) {
            boolean isJoker = match.hasJoker() && match.getJokerName().equals(players.get(i).getName());
            names[i] = (i+1) + ". " + players.get(i).getName() + (isJoker ? " ⚡ Joker" : "");
        }
        final int[] sc = {0};
        new AlertDialog.Builder(this)
                .setTitle("Select opening striker").setCancelable(false)
                .setSingleChoiceItems(names, 0, (d, w) -> sc[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    int si = sc[0]; innings.setStrikerIndex(si); players.get(si).setHasNotBatted(false);
                    // Mark joker as batting if selected
                    if (match.hasJoker() && match.getJokerName().equals(players.get(si).getName())) {
                        match.setJokerBatting();
                    }
                    if (single) { innings.setNonStrikerIndex(-1); innings.setNextBatsmanIndex(nextAvail(players, si, -1)); LiveMatchState.persist(this, match); showBowlerSelectionDialog(); }
                    else showNonStrikerDialog(si);
                }).show();
    }

    private void showNonStrikerDialog(int si) {
        List<Player>  players = match.getCurrentBattingPlayers(); Innings inn = match.getCurrentInningsData();
        List<Integer> ci = new ArrayList<>(); List<String> cn = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) if (i!=si){ci.add(i);cn.add((i+1)+". "+players.get(i).getName());}
        final int[] nc = {0};
        new AlertDialog.Builder(this).setTitle("Select non-striker").setCancelable(false)
                .setSingleChoiceItems(cn.toArray(new String[0]), 0, (d, w) -> nc[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    int ni = ci.get(nc[0]); inn.setNonStrikerIndex(ni); players.get(ni).setHasNotBatted(false);
                    inn.setNextBatsmanIndex(nextAvail(players, si, ni));
                    LiveMatchState.persist(this, match); showBowlerSelectionDialog();
                }).show();
    }

    /**
     * Returns a formatted overs label for a bowler combining complete overs
     * (from bowlerOversMap) with partial balls (from bowlerBallsMap).
     * Examples:
     *   1 full over, 0 extra balls  → "1 ov"
     *   1 full over, 3 extra balls  → "1.3 ov"
     *   0 full overs, 3 extra balls → "0.3 ov"
     *   0 full overs, 0 balls       → "" (empty — haven't bowled yet)
     */
    private String getOversLabel(Innings inn, String bowlerName) {
        int completeOvers = (inn.getBowlerOversMap() != null
                && inn.getBowlerOversMap().containsKey(bowlerName))
                ? inn.getBowlerOversMap().get(bowlerName) : 0;
        int totalBalls = (inn.getBowlerBallsMap() != null
                && inn.getBowlerBallsMap().containsKey(bowlerName))
                ? inn.getBowlerBallsMap().get(bowlerName) : 0;
        int extraBalls = totalBalls % 6;

        if (completeOvers == 0 && extraBalls == 0) return "";
        if (extraBalls == 0) return completeOvers + " ov";
        return completeOvers + "." + extraBalls + " ov";
    }

    private void showBowlerSelectionDialog() {
        setBallButtonsEnabled(false);
        Innings inn = match.getCurrentInningsData();
        List<Player> teamBowlers = getBowlingTeamPlayers();
        int ovNum = inn.getCompletedOvers().size() + 1;

        // Build eligible bowler list:
        // - Exclude joker if they are currently BATTING (jokerRole == BATTING)
        // - Include joker if they are not batting (role == NONE or BOWLING already done)
        // - If joker is not in the bowling team list but is eligible, inject them
        List<Player> eligible = new ArrayList<>();
        boolean jokerFoundInTeam = false;
        for (Player p : teamBowlers) {
            if (match.hasJoker() && match.getJokerName().equals(p.getName())) {
                jokerFoundInTeam = true;
                if (!match.isJokerBatting()) eligible.add(p); // exclude only if batting
            } else {
                eligible.add(p);
            }
        }
        // Joker is in the batting team (added to both lists), so may not appear in
        // bowling team list depending on which team is bowling.
        // If not found there, inject explicitly when eligible.
        if (match.hasJoker() && !jokerFoundInTeam && !match.isJokerBatting()) {
            eligible.add(new com.cricket.scorer.models.Player(match.getJokerName()));
        }

        String[] names = new String[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            String ovLabel = getOversLabel(inn, eligible.get(i).getName());
            boolean isJoker = match.hasJoker() && match.getJokerName().equals(eligible.get(i).getName());
            names[i] = (i+1)+". "+eligible.get(i).getName()
                    +(!ovLabel.isEmpty()?" ("+ovLabel+")":"")
                    +(isJoker?" ⚡ Joker":"");
        }
        int def = 0; // default to first eligible bowler
        final int[] ch = {def};
        new AlertDialog.Builder(this).setTitle("Over "+ovNum+" — Select bowler").setCancelable(false)
                .setSingleChoiceItems(names, def, (d, w) -> ch[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    Player selected = eligible.get(ch[0]);
                    // Find index in the actual team list for inn.setCurrentOverBowler
                    int teamIdx = -1;
                    for (int i = 0; i < teamBowlers.size(); i++) {
                        if (teamBowlers.get(i).getName().equals(selected.getName())) {
                            teamIdx = i; break;
                        }
                    }
                    inn.setCurrentOverBowler(teamIdx, selected.getName());
                    // Mark joker as bowling if selected
                    if (match.hasJoker() && match.getJokerName().equals(selected.getName())) {
                        match.setJokerBowling();
                    }
                    LiveMatchState.persist(this, match); setBallButtonsEnabled(true); refreshUI();
                }).show();
    }

    private List<Player> getBowlingTeamPlayers() {
        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        if (match.getCurrentInnings() == 1) return homeFirst ? match.getAwayPlayers() : match.getHomePlayers();
        return homeFirst ? match.getHomePlayers() : match.getAwayPlayers();
    }

    // ─── Baby Over dialog ─────────────────────────────────────────────────────

    /**
     * Shows a dialog with a Spinner dropdown listing all available bowlers.
     * Uses a custom view (not setSingleChoiceItems) to guarantee the dropdown
     * renders correctly across all Android themes and versions.
     */
    private void showBabyOverDialog() {
        Innings      inn     = match.getCurrentInningsData();
        List<Player> bowlers = getBowlingTeamPlayers();

        // Build display labels: "1. PlayerName  (2 ov)" or "1. PlayerName ← current"
        String[] names = new String[bowlers.size()];
        for (int i = 0; i < bowlers.size(); i++) {
            Player p          = bowlers.get(i);
            boolean isCurrent = p.getName().equals(inn.getCurrentBowlerName());
            String ovLabel    = getOversLabel(inn, p.getName());
            names[i] = (i + 1) + ".  " + p.getName()
                    + (!ovLabel.isEmpty() ? "  (" + ovLabel + ")" : "")
                    + (isCurrent ? "  ← current" : "");
        }

        // ── Custom dialog view ────────────────────────────────────────────────
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, 0, pad, pad / 2);

        // Info text
        android.widget.TextView tvMsg = new android.widget.TextView(this);
        tvMsg.setText(inn.getCurrentBowlerName() + " has bowled 3 balls.\n"
                + "Select bowler for balls 4–6:");
        tvMsg.setTextSize(14f);
        tvMsg.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        android.widget.LinearLayout.LayoutParams msgP =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        msgP.setMargins(0, 0, 0, pad / 2);
        tvMsg.setLayoutParams(msgP);
        layout.addView(tvMsg);

        // Spinner
        android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(spinner);

        final int[] chosen = {0};
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p,
                                                  android.view.View v, int pos, long id) {
                chosen[0] = pos;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Baby Over — Switch Bowler")
                .setView(layout)
                .setCancelable(true)
                .setPositiveButton("Confirm", (d, w) -> {
                    String name = bowlers.get(chosen[0]).getName();
                    inn.setSecondBowlerForBabyOver(chosen[0], name);
                    LiveMatchState.persist(this, match);
                    refreshUI();
                    Toast.makeText(this,
                            name + " will bowl balls 4–6", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Edit Overs dialog ────────────────────────────────────────────────────

    /**
     * Lets the user adjust the total overs for the match mid-1st-innings.
     * The new value must be:
     *   - a positive integer
     *   - >= the number of overs the batting side has already completed
     *     (you can't shrink below already-played overs)
     *   - <= 50 (sanity cap)
     *
     * On confirm: updates Match.maxOvers, persists via LiveMatchState so
     * the change survives an app restart, and refreshes the UI so the
     * "Ov 0.0 / N" label and any over-dependent calculations reflect the
     * new total.
     *
     * The button itself is hidden in 2nd innings and during tournament
     * matches (see refreshUI).
     */
    private void showEditOversDialog() {
        Innings innings = match.getCurrentInningsData();
        int currentMax     = match.getMaxOvers();
        int totalBalls     = innings.getTotalValidBalls();
        int completedOvers = totalBalls / 6;
        boolean midOver    = (totalBalls % 6) != 0;

        // Minimum new value:
        //   - Nothing bowled yet (totalBalls = 0): min = 1.
        //   - Exactly between overs, e.g. 2.0 completed and 2.1 not delivered
        //     (totalBalls = 12, midOver = false): min = completedOvers = 2.
        //     User can set max to 2 → innings ends immediately at the over
        //     boundary they're sitting at, which is a valid choice.
        //   - Mid-over, e.g. 2.1 just delivered (totalBalls = 13, midOver = true):
        //     min = completedOvers + 1 = 3. Cannot reduce to 2 because over 3
        //     has already been started.
        int minAllowed;
        if (totalBalls == 0) {
            minAllowed = 1;
        } else if (midOver) {
            minAllowed = completedOvers + 1;
        } else {
            minAllowed = completedOvers;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentMax));
        input.setSelection(input.getText().length());
        input.requestFocus();

        String currentOverLabel = midOver
                ? "Currently in over " + (completedOvers + 1)
                  + ", ball " + ((totalBalls % 6) + 1)
                : (completedOvers > 0
                        ? completedOvers + " over"
                          + (completedOvers == 1 ? "" : "s") + " completed"
                        : "No balls bowled yet");

        new AlertDialog.Builder(this)
                .setTitle("Edit total overs")
                .setMessage("Current: " + currentMax + " overs"
                        + "\n" + currentOverLabel
                        + "\n\nNew value must be between " + minAllowed + " and 50.")
                .setView(input)
                .setPositiveButton("Update", (d, w) -> {
                    String txt = input.getText().toString().trim();
                    if (txt.isEmpty()) {
                        Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int newMax;
                    try { newMax = Integer.parseInt(txt); }
                    catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newMax > 50) {
                        Toast.makeText(this, "Overs cannot exceed 50",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newMax < minAllowed) {
                        Toast.makeText(this,
                                "New value must be at least " + minAllowed
                                        + " (cannot reduce to or below the current over)",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (newMax == currentMax) return;  // no-op

                    match.setMaxOvers(newMax);
                    LiveMatchState.persist(this, match);
                    refreshUI();
                    Toast.makeText(this, "Total overs updated to " + newMax,
                            Toast.LENGTH_SHORT).show();

                    // If the new max means the innings has already reached
                    // its limit (e.g. user is at 4.0 and sets max to 4),
                    // end the innings immediately instead of waiting for
                    // the next over to complete. handleMatchState routes
                    // INNINGS_COMPLETE to the break / target screen.
                    MatchEngine.MatchState afterEdit = engine.endInningsIfMaxReached();
                    if (afterEdit == MatchEngine.MatchState.INNINGS_COMPLETE
                            || afterEdit == MatchEngine.MatchState.MATCH_COMPLETE) {
                        LiveMatchState.persist(this, match);
                        handleMatchState(afterEdit);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Retired Hurt dialog ──────────────────────────────────────────────────

    /**
     * Shows a confirmation dialog then picks the replacement batsman.
     * The retiring player is NOT dismissed — they go to "Retired Hurt" state.
     */
    private void showRetiredHurtDialog() {
        Player striker = engine.getStriker();
        new AlertDialog.Builder(this)
                .setTitle("Retired Hurt")
                .setMessage(striker.getName() + " will retire hurt. Select the replacement batsman.")
                .setPositiveButton("Continue", (d, w) -> showRetiredHurtReplacementDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRetiredHurtReplacementDialog() {
        // Build the eligible list:
        //   - Available batsmen (haven't batted yet, not out, not currently retired hurt)
        //   - PLUS previously retired-hurt batsmen (they may have healed and want to return)
        // Excluded: players who are out, currently at the crease, or the striker
        // about to retire (handled before this dialog).
        List<Player> available  = engine.getAvailableBatsmen();
        List<Player> batters    = match.getCurrentBattingPlayers();
        Innings      innings    = match.getCurrentInningsData();
        int          si         = innings.getStrikerIndex();
        int          nsi        = innings.getNonStrikerIndex();

        List<Player> eligible = new ArrayList<>(available);
        // Add retired-hurt players (excluding the striker, who is the one retiring now)
        for (int i = 0; i < batters.size(); i++) {
            Player p = batters.get(i);
            if (p.isRetiredHurt() && i != si && i != nsi) {
                eligible.add(p);
            }
        }

        if (eligible.isEmpty()) {
            Toast.makeText(this, "No available batsmen to replace retired hurt player", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            Player  p       = eligible.get(i);
            boolean healed  = p.isRetiredHurt();
            names[i] = (i + 1) + ". " + p.getName() + (healed ? "  (returning from retired hurt)" : "");
        }
        final int[] chosen = {0};
        new AlertDialog.Builder(this)
                .setTitle("Select replacement batsman")
                .setSingleChoiceItems(names, 0, (d, w) -> chosen[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    Player p = eligible.get(chosen[0]);
                    // If a previously retired-hurt player is coming back, clear that
                    // flag first so the engine treats them as a fit batsman.
                    if (p.isRetiredHurt()) {
                        p.setRetiredHurt(false);
                        p.setDismissalInfo("");
                        // If returning player is the joker, restore batting role
                        if (match.hasJoker() && match.getJokerName().equals(p.getName())) {
                            match.setJokerBatting();
                        }
                    }
                    int idx = batters.indexOf(p);
                    handleMatchState(engine.deliverRetiredHurt(idx));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Return-to-bat prompt chain ───────────────────────────────────────────

    /**
     * Shows a SINGLE dialog with a dropdown listing ALL retired-hurt players.
     *
     * The user:
     *   1. Selects a player from the spinner
     *   2. Taps "Return to bat" or "Retire out"
     *
     * After each decision the dialog closes, the game state updates, and:
     *   - If more retired-hurt players remain → dialog reopens automatically
     *     with the updated list (chosen player removed)
     *   - If a player returns to bat → play resumes, dialog does not reopen
     *   - If all players retire out → innings ends
     */
    private void promptRetiredHurtPlayers() {
        List<Player> batters     = match.getCurrentBattingPlayers();
        List<Player> retiredHurt = engine.getRetiredHurtPlayers(batters);

        if (retiredHurt.isEmpty()) {
            handleMatchState(MatchEngine.MatchState.INNINGS_COMPLETE);
            return;
        }

        // Build spinner labels: "1. PlayerName"
        String[] names = new String[retiredHurt.size()];
        for (int i = 0; i < retiredHurt.size(); i++) {
            names[i] = (i + 1) + ".  " + retiredHurt.get(i).getName();
        }

        // Track which player is selected in the spinner
        final int[] selectedIndex = {0};

        // Build a custom view: spinner + explanatory sub-text
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(8), dp(24), dp(4));

        android.widget.TextView tvSub = new android.widget.TextView(this);
        tvSub.setText("Select a player and choose their action:");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(Color.parseColor("#666666"));
        tvSub.setPadding(0, 0, 0, dp(12));
        layout.addView(tvSub);

        android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                selectedIndex[0] = pos;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        android.widget.LinearLayout.LayoutParams spinnerParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        spinnerParams.setMargins(0, 0, 0, dp(8));
        spinner.setLayoutParams(spinnerParams);
        layout.addView(spinner);

        // Count label
        android.widget.TextView tvCount = new android.widget.TextView(this);
        tvCount.setText(retiredHurt.size() == 1
                ? "1 player remaining"
                : retiredHurt.size() + " players remaining");
        tvCount.setTextSize(11f);
        tvCount.setTextColor(Color.parseColor("#BA7517"));
        tvCount.setPadding(0, dp(4), 0, 0);
        layout.addView(tvCount);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Retired Hurt Players")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Return to bat", null)   // set below to prevent auto-dismiss
                .setNegativeButton("Retire out", null)
                .setNeutralButton("End Innings", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            // ── Return to bat ──────────────────────────────────────────
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Player chosen = retiredHurt.get(selectedIndex[0]);
                int playerIndex = batters.indexOf(chosen);
                MatchEngine.MatchState state = engine.returnFromRetiredHurt(playerIndex, true);
                LiveMatchState.persist(this, match);
                refreshUI();
                dialog.dismiss();
                setBallButtonsEnabled(true);
                // Player returning to bat — play continues, no more prompting
                if (state == MatchEngine.MatchState.PROMPT_RETIRED_HURT) {
                    // Edge case: shouldn't normally happen after a return
                    promptRetiredHurtPlayers();
                } else {
                    handleMatchState(state);
                }
            });

            // ── Retire out ─────────────────────────────────────────────
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                Player chosen = retiredHurt.get(selectedIndex[0]);
                int playerIndex = batters.indexOf(chosen);
                MatchEngine.MatchState state = engine.returnFromRetiredHurt(playerIndex, false);
                LiveMatchState.persist(this, match);
                refreshUI();
                dialog.dismiss();
                if (state == MatchEngine.MatchState.PROMPT_RETIRED_HURT) {
                    // More players to resolve — reopen with updated list
                    promptRetiredHurtPlayers();
                } else {
                    handleMatchState(state);
                }
            });

            // ── End innings immediately (all remaining retire out) ──────
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                // Retire out everyone still on the list
                List<Player> remaining = engine.getRetiredHurtPlayers(
                        match.getCurrentBattingPlayers());
                MatchEngine.MatchState lastState = MatchEngine.MatchState.BALL_RECORDED;
                for (Player p : remaining) {
                    int idx = match.getCurrentBattingPlayers().indexOf(p);
                    lastState = engine.returnFromRetiredHurt(idx, false);
                }
                LiveMatchState.persist(this, match);
                refreshUI();
                dialog.dismiss();
                // After retiring all out, force innings to end
                handleMatchState(MatchEngine.MatchState.INNINGS_COMPLETE);
            });

            // Colour the buttons for clarity
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#085041"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#E24B4A"));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#888888"));
        });

        dialog.show();
    }

    private int dp(int val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }

    // ─── Reset helpers ────────────────────────────────────────────────────────

    private void resetAndReshowOpeners() {
        Innings inn = match.getCurrentInningsData(); List<Player> pl = match.getCurrentBattingPlayers();
        for (Player p : pl) { p.setHasNotBatted(true); p.setOut(false); p.setRetiredHurt(false); }
        inn.setStrikerIndex(0); inn.setNonStrikerIndex(match.isSingleBatsmanMode()?-1:1);
        inn.setNextBatsmanIndex(match.isSingleBatsmanMode()?1:2); inn.setBowlerSelected(false);
        inn.setCurrentBowlerIndex(-1); inn.setCurrentBowlerName("");
        // Clear joker role — the opener selection is being redone, so joker's
        // previous batting/bowling role no longer applies
        if (match.hasJoker()) match.clearJokerRole();
        LiveMatchState.persist(this, match); setBallButtonsEnabled(false); refreshUI();
        showOpenerSelectionDialog();
    }

    private void resetAndReshowBowler() {
        Innings inn = match.getCurrentInningsData();
        inn.setBowlerSelected(false); inn.setCurrentBowlerIndex(-1); inn.setCurrentBowlerName("");
        if (inn.getCurrentOver() != null) { inn.getCurrentOver().setBowlerIndex(-1); inn.getCurrentOver().setBowlerName(""); }
        // Clear joker bowling role — bowler selection is being redone
        if (match.hasJoker() && match.isJokerBowling()) match.clearJokerRole();
        LiveMatchState.persist(this, match); refreshUI(); showBowlerSelectionDialog();
    }

    private int nextAvail(List<Player> pl, int si, int ni) {
        for (int i = 0; i < pl.size(); i++) if (i!=si&&i!=ni) return i; return pl.size();
    }

    // ─── Enable/disable buttons ───────────────────────────────────────────────

    private void setBallButtonsEnabled(boolean e) {
        float a = e ? 1f : 0.35f;
        btnDot.setEnabled(e); btnDot.setAlpha(a); btn1.setEnabled(e); btn1.setAlpha(a);
        btn2.setEnabled(e); btn2.setAlpha(a); btn3.setEnabled(e); btn3.setAlpha(a);
        btn4.setEnabled(e); btn4.setAlpha(a); btn6.setEnabled(e); btn6.setAlpha(a);
        btnWide.setEnabled(e); btnWide.setAlpha(a); btnNoBall.setEnabled(e); btnNoBall.setAlpha(a);
        btnWicket.setEnabled(e); btnWicket.setAlpha(a);
        btnRetiredHurt.setEnabled(e); btnRetiredHurt.setAlpha(a);
        // Baby over button visibility is managed separately in refreshBabyOverButton()
        btnUndo.setEnabled(true); btnUndo.setAlpha(1f);
    }

    /** Show baby over button only when exactly 3 valid balls have been bowled
     *  in the current over AND baby over hasn't already been activated. */
    private void refreshBabyOverButton() {
        Innings inn = match.getCurrentInningsData();
        Over    cur = inn.getCurrentOver();
        int validBalls = cur != null ? cur.getValidBallCount() : 0;
        boolean show = (validBalls == 3) && !inn.isBabyOverActivated();
        btnBabyOver.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        tvInningsTitle = findViewById(R.id.tv_innings_title); tvScore = findViewById(R.id.tv_score);
        tvOversInfo = findViewById(R.id.tv_overs_info); tvCRR = findViewById(R.id.tv_crr); tvRRR = findViewById(R.id.tv_rrr);
        tvModeBadge = findViewById(R.id.tv_mode_badge); layoutTargetBanner = findViewById(R.id.layout_target_banner);
        tvTargetInfo = findViewById(R.id.tv_target_info); tvRequiredBalls = findViewById(R.id.tv_required_balls);
        tableBatsmen = findViewById(R.id.table_batsmen); tvCurrentOverLabel = findViewById(R.id.tv_current_over_label);
        tvBallsRemaining = findViewById(R.id.tv_balls_remaining); tvCurrentBowler = findViewById(R.id.tv_current_bowler);
        rvCurrentOverBalls = findViewById(R.id.rv_current_over_balls); rvOverHistory = findViewById(R.id.rv_over_history);
        tvBowlerSummary = findViewById(R.id.tv_bowler_summary);
        btnDot = findViewById(R.id.btn_dot); btn1 = findViewById(R.id.btn_1); btn2 = findViewById(R.id.btn_2);
        btn3 = findViewById(R.id.btn_3); btn4 = findViewById(R.id.btn_4); btn6 = findViewById(R.id.btn_6);
        btnWide = findViewById(R.id.btn_wide); btnNoBall = findViewById(R.id.btn_noball);
        btnWicket = findViewById(R.id.btn_wicket); btnRetiredHurt = findViewById(R.id.btn_retired_hurt);
        btnBabyOver = findViewById(R.id.btn_baby_over);
        btnEditOvers = findViewById(R.id.btn_edit_overs);
        btnUndo = findViewById(R.id.btn_undo);
    }

    private void setupAdapters() {
        ballAdapter = new BallAdapter(new ArrayList<>());
        rvCurrentOverBalls.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCurrentOverBalls.setAdapter(ballAdapter);
        overHistoryAdapter = new OverHistoryAdapter(new ArrayList<>());
        rvOverHistory.setLayoutManager(new LinearLayoutManager(this));
        rvOverHistory.setAdapter(overHistoryAdapter);
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {
        btnDot.setOnClickListener(v -> handleBall(0));
        btn1.setOnClickListener(v -> handleBall(1)); btn2.setOnClickListener(v -> handleBall(2));
        btn3.setOnClickListener(v -> handleBall(3)); btn4.setOnClickListener(v -> handleBall(4));
        btn6.setOnClickListener(v -> handleBall(6));
        btnWide.setOnClickListener(v -> handleMatchState(engine.deliverWide()));
        btnNoBall.setOnClickListener(v -> handleMatchState(engine.deliverNoBall()));
        btnWicket.setOnClickListener(v -> showWicketDialog());
        btnRetiredHurt.setOnClickListener(v -> showRetiredHurtDialog());
        btnBabyOver.setOnClickListener(v -> showBabyOverDialog());
        btnEditOvers.setOnClickListener(v -> showEditOversDialog());
        btnUndo.setOnClickListener(v -> {
            if (isCurrentOverEmpty()) {
                if (isInningsJustStarted()) resetAndReshowOpeners();
                else resetAndReshowBowler();
            } else {
                if (!engine.undoLastBall()) Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
                else { LiveMatchState.persist(this, match); refreshUI(); }
            }
        });
    }

    private void handleBall(int runs) { handleMatchState(engine.deliverNormalBall(runs)); }

    private void showWicketDialog() {
        List<Player> available = engine.getAvailableBatsmen();
        // Exclude joker from batting list if they are currently bowling
        List<Player> filtered = new ArrayList<>();
        for (Player p : available) {
            if (match.hasJoker() && match.getJokerName().equals(p.getName())
                    && match.isJokerBowling()) continue;
            filtered.add(p);
        }
        if (filtered.isEmpty()) { handleMatchState(engine.deliverWicket(engine.getNextBatsmanIndex())); return; }
        String[] names = new String[filtered.size()];
        List<Player> batters = match.getCurrentBattingPlayers();
        for (int i = 0; i < filtered.size(); i++) {
            boolean isJoker = match.hasJoker() && match.getJokerName().equals(filtered.get(i).getName());
            names[i] = (i+1)+". "+filtered.get(i).getName()+(isJoker?" ⚡ Joker":"");
        }
        final int[] ch = {0};
        new AlertDialog.Builder(this).setTitle("Wicket — choose next batsman")
                .setSingleChoiceItems(names, 0, (d, w) -> ch[0] = w)
                .setPositiveButton("Confirm", (d, w) -> {
                    Player incoming = filtered.get(ch[0]);
                    // Mark joker as batting if they come in
                    if (match.hasJoker() && match.getJokerName().equals(incoming.getName())) {
                        match.setJokerBatting();
                    }
                    handleMatchState(engine.deliverWicket(batters.indexOf(incoming)));
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ─── Match state routing ──────────────────────────────────────────────────

    private void handleMatchState(MatchEngine.MatchState state) {
        switch (state) {
            case BALL_RECORDED:
                LiveMatchState.persist(this, match); refreshUI(); break;
            case OVER_COMPLETE:
                LiveMatchState.persist(this, match); refreshUI();
                Toast.makeText(this, "Over "+match.getCurrentInningsData().getCompletedOvers().size()+" complete!", Toast.LENGTH_SHORT).show();
                showBowlerSelectionDialog(); break;
            case INNINGS_COMPLETE:
                LiveMatchState.persist(this, match);
                startActivity(new Intent(this, InningsBreakActivity.class)); finish(); break;
            case MATCH_COMPLETE:
                LiveMatchState.clear(this);
                startActivity(new Intent(this, StatsActivity.class)); finish(); break;
            case PROMPT_RETIRED_HURT:
                LiveMatchState.persist(this, match);
                setBallButtonsEnabled(false);
                refreshUI();
                promptRetiredHurtPlayers(); break;
            case JOKER_MUST_STOP_BOWLING:
                // All non-joker batsmen dismissed while joker was bowling.
                // Joker must stop bowling and come in to bat.
                // Another bowler from the bowling team must finish the over.
                LiveMatchState.persist(this, match);
                setBallButtonsEnabled(false);
                refreshUI();
                showJokerMustStopBowlingDialog(); break;
        }
    }

    /**
     * Shown when all non-joker batsmen are out while joker is bowling.
     * Prompts the user to select a replacement bowler (excluding joker)
     * to complete the remaining balls of the current over.
     * After confirmation, joker comes in to bat as striker.
     */
    private void showJokerMustStopBowlingDialog() {
        Innings      inn         = match.getCurrentInningsData();
        List<Player> teamBowlers = getBowlingTeamPlayers();
        int          ballsLeft   = 6 - inn.getCurrentOver().getValidBallCount();

        // Available replacement bowlers — exclude the joker
        List<Player> replacements = new ArrayList<>();
        for (Player p : teamBowlers) {
            if (!match.getJokerName().equals(p.getName())) replacements.add(p);
        }

        if (replacements.isEmpty() || ballsLeft == 0) {
            // No replacement available or over already done — end innings
            handleMatchState(MatchEngine.MatchState.INNINGS_COMPLETE);
            return;
        }

        String[] names = new String[replacements.size()];
        Innings inn2 = match.getCurrentInningsData();
        for (int i = 0; i < replacements.size(); i++) {
            String ovLabel = getOversLabel(inn2, replacements.get(i).getName());
            names[i] = (i+1)+". "+replacements.get(i).getName()
                    +(!ovLabel.isEmpty() ? "  ("+ovLabel+")" : "");
        }
        final int[] ch = {0};

        // Custom view with Spinner (setSingleChoiceItems doesn't render in this theme)
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, 0, pad, pad / 2);

        android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { ch[0] = pos; }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        layout.addView(spinner);

        new AlertDialog.Builder(this)
                .setTitle("⚡ Joker Must Come In to Bat!")
                .setMessage(match.getJokerName() + " (Joker) was bowling but all batsmen are out.\n\n"
                        + match.getJokerName() + " must now bat.\n\n"
                        + "Select a bowler to complete the remaining "
                        + ballsLeft + " ball" + (ballsLeft == 1 ? "" : "s") + ":")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Confirm", (d, w) -> {
                    Player rep = replacements.get(ch[0]);
                    // Use baby-over mechanism to swap bowler mid-over
                    inn.setSecondBowlerForBabyOver(teamBowlers.indexOf(rep), rep.getName());
                    // Joker comes in to bat as striker
                    jokerComesInToBat();
                    LiveMatchState.persist(this, match);
                    setBallButtonsEnabled(true);
                    refreshUI();
                    Toast.makeText(this,
                            match.getJokerName() + " is batting! " + rep.getName() + " will bowl the remaining balls.",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    /** Places the joker at the striker's end as a new batsman. */
    private void jokerComesInToBat() {
        Innings      inn     = match.getCurrentInningsData();
        List<Player> batters = match.getCurrentBattingPlayers();
        // Find joker in batting team list
        for (int i = 0; i < batters.size(); i++) {
            if (match.getJokerName().equals(batters.get(i).getName())) {
                batters.get(i).setHasNotBatted(false);
                inn.setStrikerIndex(i);
                match.setJokerBatting();
                return;
            }
        }
    }

    // ─── UI refresh ───────────────────────────────────────────────────────────

    private void refreshUI() {
        Innings innings = match.getCurrentInningsData();
        int inum = match.getCurrentInnings(); boolean single = match.isSingleBatsmanMode();

        tvInningsTitle.setText(inum==1?"1st Innings":"2nd Innings");
        tvScore.setText(innings.getScoreString());
        tvOversInfo.setText(String.format(Locale.US,"Ov %s / %d",innings.getOversString(),match.getMaxOvers()));
        tvCRR.setText(String.format(Locale.US,"CRR: %.2f",innings.getCurrentRunRate()));
        tvModeBadge.setText(single?"Single bat":"Two bat"); tvModeBadge.setVisibility(View.VISIBLE);

        // Edit Overs button: 1st innings only, never during a tournament match.
        // 2nd innings inherits whatever overs the 1st innings finished with.
        boolean tournamentActive = ((CricketApp) getApplication()).isTournamentActive();
        boolean canEditOvers     = inum == 1 && !tournamentActive;
        btnEditOvers.setVisibility(canEditOvers ? View.VISIBLE : View.GONE);

        if (innings.isBowlerSelected() && !innings.getCurrentBowlerName().isEmpty()) {
            String bowlerLabel;
            if (innings.isBabyOverActivated() && innings.isSecondBowlerSelected()
                    && !innings.getCurrentSecondBowlerName().isEmpty()) {
                bowlerLabel = "Bowling: " + innings.getCurrentSecondBowlerName()
                        + "  (Baby over ½)";
            } else {
                bowlerLabel = "Bowling: " + innings.getCurrentBowlerName();
            }
            tvCurrentBowler.setText(bowlerLabel);
            tvCurrentBowler.setVisibility(View.VISIBLE);
        } else tvCurrentBowler.setVisibility(View.GONE);

        if (inum==2) {
            int t=match.getTarget(); layoutTargetBanner.setVisibility(View.VISIBLE); tvRRR.setVisibility(View.VISIBLE);
            tvTargetInfo.setText("Target: "+t);
            tvRequiredBalls.setText("Need "+innings.getRunsNeeded(t)+" off "+innings.getBallsRemaining(match.getMaxOvers())+" balls");
            tvRRR.setText(String.format(Locale.US,"RRR: %.2f",innings.getRequiredRunRate(t,match.getMaxOvers())));
        } else { layoutTargetBanner.setVisibility(View.GONE); tvRRR.setVisibility(View.GONE); }

        refreshBattingTable(innings, single);
        refreshCurrentOver(innings);
        overHistoryAdapter.updateData(innings.getCompletedOvers());

        String summary = innings.getBowlerSummary();
        if (!summary.isEmpty()) { tvBowlerSummary.setText(summary); tvBowlerSummary.setVisibility(View.VISIBLE); }
        else tvBowlerSummary.setVisibility(View.GONE);

        refreshBabyOverButton();
    }

    private void refreshBattingTable(Innings innings, boolean single) {
        tableBatsmen.removeAllViews();
        addTableRow(new String[]{"Batsman","R","B","4s","6s","SR"}, true, false, false, false);

        List<Player> players = match.getCurrentBattingPlayers();
        int si = innings.getStrikerIndex(), nsi = innings.getNonStrikerIndex();

        for (int i = 0; i < players.size(); i++) {
            Player  p = players.get(i);
            boolean isStriker    = (i==si)   && !p.isOut() && !p.isRetiredHurt();
            boolean isNonStriker = !single && (i==nsi) && !p.isOut() && !p.isRetiredHurt();
            boolean atCrease     = isStriker || isNonStriker;

            if (p.isHasNotBatted() && !atCrease) continue;

            String name = isStriker?"⚡ "+p.getName(): isNonStriker?"  "+p.getName():p.getName();
            String sr   = p.getBallsFaced()>0?String.format(Locale.US,"%.1f",p.getStrikeRate()):"-";
            addTableRow(new String[]{name,String.valueOf(p.getRunsScored()),String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()),String.valueOf(p.getSixes()),sr},
                    false, atCrease, p.isOut(), p.isRetiredHurt());
        }
    }

    /**
     * @param retiredHurt shows amber text + "Ret." suffix on name column
     */
    private void addTableRow(String[] cells, boolean hdr, boolean active,
                              boolean out, boolean retiredHurt) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (active)      row.setBackgroundColor(Color.parseColor("#E1F5EE"));
        else if (hdr)    row.setBackgroundColor(Color.parseColor("#F1EFE8"));
        else if (retiredHurt) row.setBackgroundColor(Color.parseColor("#FFF8E7")); // subtle amber tint

        int[] w = {3,1,1,1,1,1};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, w[i]));
            String cellText = cells[i];
            if (i==0 && retiredHurt) cellText = cellText + " †"; // dagger = retired hurt marker
            tv.setText(cellText); tv.setPadding(12,10,12,10); tv.setTextSize(12f);
            if (hdr) {
                tv.setTextColor(Color.parseColor("#888780")); tv.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (out) {
                tv.setTextColor(Color.parseColor("#AAAAAA"));
                if (i==0) tv.setPaintFlags(tv.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else if (retiredHurt) {
                tv.setTextColor(Color.parseColor("#BA7517")); // amber — "retired hurt" colour
            } else {
                tv.setTextColor(Color.parseColor("#111111"));
            }
            row.addView(tv);
        }
        tableBatsmen.addView(row);
    }

    private void refreshCurrentOver(Innings innings) {
        Over cur = innings.getCurrentOver(); int cnt = innings.getCompletedOvers().size();
        tvCurrentOverLabel.setText("Over "+(cnt+1));
        int valid = cur!=null?cur.getValidBallCount():0, rem=6-valid;
        tvBallsRemaining.setText(rem+(rem==1?" ball left":" balls left"));
        List<Ball> db = new ArrayList<>();
        if (cur!=null) db.addAll(cur.getBalls());
        for (int i=0;i<Math.max(0,6-valid);i++) db.add(null);
        ballAdapter.updateData(db);
    }
}
