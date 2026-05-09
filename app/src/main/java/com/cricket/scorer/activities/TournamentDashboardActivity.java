package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.models.TournamentMatch;
import com.cricket.scorer.models.TournamentTeam;
import com.cricket.scorer.utils.LiveMatchState;
import com.cricket.scorer.utils.TournamentStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * TournamentDashboardActivity
 *
 * Main hub of an active tournament. Shows:
 *   - Standings table (W/L/Pts/NRR)
 *   - Current stage and next match
 *   - "Start next match" button → launches InningsActivity for that fixture
 *   - When league complete → "Start Semifinals" button (top 4 → 1v4 + 2v3)
 *   - When semis complete → "Start Final" button
 *   - When final complete → champion banner + "Finish Tournament" button
 */
public class TournamentDashboardActivity extends BaseNavActivity {

    private TextView     tvStage, tvNextMatch, tvChampion;
    private TableLayout  tableStandings;
    private LinearLayout layoutSemiTeams;
    private Button       btnStartNext, btnStartSemis, btnStartFinal, btnFinish, btnSaveTournament, btnShareTournament;

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournament_dashboard);
        tvStage         = findViewById(R.id.tv_stage);
        tvNextMatch     = findViewById(R.id.tv_next_match);
        tvChampion      = findViewById(R.id.tv_champion);
        tableStandings  = findViewById(R.id.table_standings);
        layoutSemiTeams = findViewById(R.id.layout_semi_teams);
        btnStartNext    = findViewById(R.id.btn_start_next);
        btnStartSemis   = findViewById(R.id.btn_start_semis);
        btnStartFinal   = findViewById(R.id.btn_start_final);
        btnFinish       = findViewById(R.id.btn_finish);
        btnSaveTournament  = findViewById(R.id.btn_save_tournament);
        btnShareTournament = findViewById(R.id.btn_share_tournament);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Tournament t = ((CricketApp) getApplication()).getCurrentTournament();
        if (t == null) {
            t = TournamentStorage.load(this);
            if (t != null) ((CricketApp) getApplication()).startNewTournament(t);
        }
        if (t == null) { finish(); return; }
        render(t);
    }

    private void render(Tournament t) {
        // Stage label
        tvStage.setText("Stage: " + t.getStage().name());

        // Standings
        renderStandings(t);

        // Hide everything by default; enable per stage
        btnStartNext.setVisibility(View.GONE);
        btnStartSemis.setVisibility(View.GONE);
        btnStartFinal.setVisibility(View.GONE);
        btnFinish.setVisibility(View.GONE);
        btnSaveTournament.setVisibility(View.GONE);
        btnShareTournament.setVisibility(View.GONE);
        tvChampion.setVisibility(View.GONE);
        layoutSemiTeams.setVisibility(View.GONE);
        tvNextMatch.setVisibility(View.GONE);

        switch (t.getStage()) {
            case LEAGUE:
                // 2-team series: check if a team has clinched the majority.
                // If so, declare champion and skip straight to COMPLETED.
                if (t.isBestOfSeries()) {
                    TournamentTeam clincher = t.getSeriesWinner();
                    if (clincher != null) {
                        t.setChampionName(clincher.getName());
                        t.setStage(Tournament.Stage.COMPLETED);
                        TournamentStorage.save(this, t);
                        render(t);  // re-render in COMPLETED state
                        return;
                    }
                    // Series still in progress — show next match in the series
                    if (!t.isCurrentStageComplete()) {
                        showNextMatch(t);
                    } else {
                        // All N matches played without majority (only possible if
                        // somehow N was even, but we validate odd at setup so this
                        // is just a safety net — declare a tie / finish anyway)
                        showSeriesTie(t);
                    }
                    break;
                }

                if (t.isCurrentStageComplete()) {
                    // League finished — depending on team count, either:
                    //   3 or 4 teams → skip semis, top 2 → final
                    //   5+ teams     → top 4 → semis
                    int teamCount = t.getTeams().size();
                    if (teamCount == 3 || teamCount == 4) {
                        showTopTwo(t);
                        btnStartSemis.setVisibility(View.VISIBLE);
                        btnStartSemis.setText("Start Final (Top 2)");
                        btnStartSemis.setOnClickListener(v -> startSemifinals(t));
                    } else {
                        showTopFour(t);
                        btnStartSemis.setVisibility(View.VISIBLE);
                        btnStartSemis.setText("Start Semifinals");
                        btnStartSemis.setOnClickListener(v -> startSemifinals(t));
                    }
                } else {
                    showNextMatch(t);
                }
                break;
            case SEMIFINAL:
                if (t.isCurrentStageComplete()) {
                    btnStartFinal.setVisibility(View.VISIBLE);
                    btnStartFinal.setOnClickListener(v -> startFinal(t));
                    showFinalists(t);
                } else {
                    showNextMatch(t);
                }
                break;
            case FINAL:
                if (t.isCurrentStageComplete()) {
                    declareChampion(t);
                } else {
                    showNextMatch(t);
                }
                break;
            case COMPLETED:
                tvChampion.setVisibility(View.VISIBLE);
                tvChampion.setText("🏆 Champion: " + t.getChampionName());
                btnSaveTournament.setVisibility(View.VISIBLE);
                btnSaveTournament.setOnClickListener(v -> saveTournament(t));
                btnShareTournament.setVisibility(View.VISIBLE);
                btnShareTournament.setOnClickListener(v -> shareTournament(t));
                break;
        }
    }

    private void showNextMatch(Tournament t) {
        TournamentMatch next = t.getCurrentMatch();
        if (next == null) return;
        tvNextMatch.setVisibility(View.VISIBLE);
        // For 2-team series, also show the series score (e.g. "Match 2 — Lions lead 1-0")
        if (t.isBestOfSeries() && t.getTeams().size() == 2) {
            int currentMatchNum = t.getCurrentMatchIndex() + 1;
            int aWins = t.getTeams().get(0).getWins();
            int bWins = t.getTeams().get(1).getWins();
            int needed = (t.getBestOfMatches() + 1) / 2;
            tvNextMatch.setText("Match " + currentMatchNum + " of "
                    + t.getBestOfMatches() + "  ·  Series: "
                    + t.getTeams().get(0).getName() + " " + aWins + "-" + bWins + " "
                    + t.getTeams().get(1).getName() + "  (best of " + t.getBestOfMatches()
                    + ", first to " + needed + ")");
        } else {
            tvNextMatch.setText("Next match: " + next.getLabel());
        }
        btnStartNext.setVisibility(View.VISIBLE);
        btnStartNext.setText("Start: " + next.getLabel());
        btnStartNext.setOnClickListener(v -> launchMatch(t, next));
    }

    /**
     * Edge case: best-of-N series fully played without a majority winner.
     * (Should not happen with odd N, but kept as a safety net.)
     */
    private void showSeriesTie(Tournament t) {
        layoutSemiTeams.setVisibility(View.VISIBLE);
        layoutSemiTeams.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("Series ended — no majority winner");
        tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        layoutSemiTeams.addView(tv);
    }

    private void showTopFour(Tournament t) {
        layoutSemiTeams.setVisibility(View.VISIBLE);
        layoutSemiTeams.removeAllViews();
        TextView title = new TextView(this);
        title.setText("LEAGUE COMPLETE — TOP 4");
        title.setTextSize(13f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(16), dp(8), dp(16), dp(4));
        title.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        layoutSemiTeams.addView(title);
        List<TournamentTeam> top4 = t.getTopTeams(4);
        for (int i = 0; i < top4.size(); i++) {
            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + top4.get(i).getName()
                    + "  (" + top4.get(i).getWins() + "W " + top4.get(i).getLosses() + "L)");
            tv.setPadding(dp(20), dp(4), dp(16), dp(4));
            layoutSemiTeams.addView(tv);
        }
    }

    /**
     * Shown when exactly 4 teams played the league and we skip semifinals.
     * Top 2 teams advance directly to the final.
     */
    private void showTopTwo(Tournament t) {
        layoutSemiTeams.setVisibility(View.VISIBLE);
        layoutSemiTeams.removeAllViews();
        TextView title = new TextView(this);
        title.setText("LEAGUE COMPLETE — TOP 2 ADVANCE TO FINAL");
        title.setTextSize(13f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(16), dp(8), dp(16), dp(4));
        title.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        layoutSemiTeams.addView(title);

        TextView note = new TextView(this);
        note.setText("(4-team tournament — semifinals are skipped)");
        note.setTextSize(11f);
        note.setTextColor(getResources().getColor(R.color.c_text_secondary, getTheme()));
        note.setPadding(dp(16), 0, dp(16), dp(8));
        layoutSemiTeams.addView(note);

        List<TournamentTeam> top2 = t.getTopTeams(2);
        for (int i = 0; i < top2.size(); i++) {
            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + top2.get(i).getName()
                    + "  (" + top2.get(i).getWins() + "W " + top2.get(i).getLosses() + "L)");
            tv.setPadding(dp(20), dp(4), dp(16), dp(4));
            layoutSemiTeams.addView(tv);
        }
    }

    private void showFinalists(Tournament t) {
        layoutSemiTeams.setVisibility(View.VISIBLE);
        layoutSemiTeams.removeAllViews();
        TextView title = new TextView(this);
        title.setText("SEMIFINALS COMPLETE — FINALISTS");
        title.setTextSize(13f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(16), dp(8), dp(16), dp(4));
        title.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        layoutSemiTeams.addView(title);
        for (TournamentMatch sm : t.getSemiFixtures()) {
            TextView tv = new TextView(this);
            tv.setText("• " + sm.getWinnerName());
            tv.setPadding(dp(20), dp(4), dp(16), dp(4));
            layoutSemiTeams.addView(tv);
        }
    }

    private void declareChampion(Tournament t) {
        TournamentMatch f = t.getFinalFixture();
        if (f != null && f.getWinnerName() != null) {
            t.setChampionName(f.getWinnerName());
            t.setStage(Tournament.Stage.COMPLETED);
            TournamentStorage.save(this, t);
            tvChampion.setVisibility(View.VISIBLE);
            tvChampion.setText("🏆 Champion: " + t.getChampionName());
            btnSaveTournament.setVisibility(View.VISIBLE);
            btnSaveTournament.setOnClickListener(v -> saveTournament(t));
            btnShareTournament.setVisibility(View.VISIBLE);
            btnShareTournament.setOnClickListener(v -> shareTournament(t));
        }
    }

    private void startSemifinals(Tournament t) {
        // RULE: with 3 or 4 teams, skip the semifinals and go straight
        // to a final between the top 2 teams.
        int teamCount = t.getTeams().size();
        if (teamCount == 3 || teamCount == 4) {
            startFinalDirectly(t);
            return;
        }

        // Standard semifinals: top 4 teams → 1v4 + 2v3
        List<TournamentTeam> top4 = t.getTopTeams(4);
        if (top4.size() < 4) {
            Toast.makeText(this, "Need at least 4 teams for semis", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TournamentMatch> semis = new ArrayList<>();
        semis.add(new TournamentMatch(top4.get(0).getName(), top4.get(3).getName())); // 1 vs 4
        semis.add(new TournamentMatch(top4.get(1).getName(), top4.get(2).getName())); // 2 vs 3
        t.setSemiFixtures(semis);
        t.setStage(Tournament.Stage.SEMIFINAL);
        t.setCurrentMatchIndex(0);
        TournamentStorage.save(this, t);
        render(t);
    }

    /**
     * Sets up final directly between top 2 teams (used when only 4 teams
     * are in the tournament, so semifinals are skipped).
     */
    private void startFinalDirectly(Tournament t) {
        List<TournamentTeam> top2 = t.getTopTeams(2);
        if (top2.size() < 2) {
            Toast.makeText(this, "Need at least 2 teams for final", Toast.LENGTH_SHORT).show();
            return;
        }
        TournamentMatch finalMatch = new TournamentMatch(
                top2.get(0).getName(), top2.get(1).getName());
        t.setFinalFixture(finalMatch);
        t.setStage(Tournament.Stage.FINAL);
        t.setCurrentMatchIndex(0);
        TournamentStorage.save(this, t);
        Toast.makeText(this,
                t.getTeams().size() + "-team tournament: skipping semis — Top 2 advance to Final!",
                Toast.LENGTH_LONG).show();
        render(t);
    }

    private void startFinal(Tournament t) {
        List<TournamentMatch> semis = t.getSemiFixtures();
        if (semis.size() < 2 || semis.get(0).getWinnerName() == null
                || semis.get(1).getWinnerName() == null) {
            Toast.makeText(this, "Both semis must have winners", Toast.LENGTH_SHORT).show();
            return;
        }
        TournamentMatch finalMatch = new TournamentMatch(
                semis.get(0).getWinnerName(),
                semis.get(1).getWinnerName());
        t.setFinalFixture(finalMatch);
        t.setStage(Tournament.Stage.FINAL);
        t.setCurrentMatchIndex(0);
        TournamentStorage.save(this, t);
        render(t);
    }

    /**
     * Saves the completed tournament to recent_tournaments/, clears the live
     * tracker so the user is NOT prompted to resume next launch, and clears
     * the currentTournament singleton so a fresh tournament can be started.
     */
    private void saveTournament(Tournament t) {
        java.io.File saved = com.cricket.scorer.utils.TournamentStorage.archiveCompleted(this, t);
        if (saved != null) {
            // Tournament is fully done — wipe the live tracker and singleton.
            // Resume dialog on Home will no longer fire for this tournament.
            com.cricket.scorer.utils.TournamentStorage.clear(this);
            ((CricketApp) getApplication()).clearTournament();
            Toast.makeText(this, "Tournament saved", Toast.LENGTH_SHORT).show();
            btnSaveTournament.setEnabled(false);
            btnSaveTournament.setText("Saved \u2713");
        } else {
            Toast.makeText(this, "Failed to save tournament", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shares a text summary of the final tournament standings + champion.
     */
    private void shareTournament(Tournament t) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 Tournament complete\n\n");
        sb.append("Champion: ").append(t.getChampionName()).append("\n\n");
        sb.append("Final standings:\n");
        java.util.List<TournamentTeam> standings = t.getStandings();
        for (int i = 0; i < standings.size(); i++) {
            TournamentTeam team = standings.get(i);
            sb.append(i + 1).append(". ").append(team.getName())
              .append(" — ").append(team.getWins()).append("W ")
              .append(team.getLosses()).append("L · Pts ").append(team.getPoints())
              .append(" · NRR ").append(String.format(java.util.Locale.US, "%.2f", team.getNetRunRate()))
              .append("\n");
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Tournament results");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, "Share tournament results"));
    }

    private void finishTournament() {
        ((CricketApp) getApplication()).clearTournament();
        TournamentStorage.clear(this);
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    /**
     * Launches an InningsActivity to play the given fixture.
     * Builds a Match using both teams' player lists from the tournament.
     */
    private void launchMatch(Tournament t, TournamentMatch fixture) {
        TournamentTeam teamA = t.findTeamByName(fixture.getTeamAName());
        TournamentTeam teamB = t.findTeamByName(fixture.getTeamBName());
        if (teamA == null || teamB == null) {
            Toast.makeText(this, "Team data missing", Toast.LENGTH_SHORT).show();
            return;
        }
        // Toss first, then start the match
        showTossDialog(t, fixture, teamA, teamB);
    }

    /**
     * Two-step toss dialog before each tournament match.
     *
     *   Step 1: Which team won the toss?  [TeamA] [TeamB]
     *   Step 2: Toss winner chose to ...  [Bat]   [Bowl]
     *
     * The combination determines battingFirstTeam:
     *   winner=A, chose Bat   → A bats first (home)
     *   winner=A, chose Bowl  → B bats first (away first)
     *   winner=B, chose Bat   → B bats first
     *   winner=B, chose Bowl  → A bats first
     */
    private void showTossDialog(Tournament t, TournamentMatch fixture,
                                 TournamentTeam teamA, TournamentTeam teamB) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Toss — " + fixture.getLabel())
                .setMessage("Which team won the toss?")
                .setCancelable(false)
                .setPositiveButton(teamA.getName(), (d, w) ->
                        showTossChoiceDialog(t, fixture, teamA, teamB, /*aWon=*/true))
                .setNegativeButton(teamB.getName(), (d, w) ->
                        showTossChoiceDialog(t, fixture, teamA, teamB, /*aWon=*/false))
                .show();
    }

    private void showTossChoiceDialog(Tournament t, TournamentMatch fixture,
                                       TournamentTeam teamA, TournamentTeam teamB,
                                       boolean aWonToss) {
        String winnerName = aWonToss ? teamA.getName() : teamB.getName();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(winnerName + " won the toss")
                .setMessage(winnerName + " chose to:")
                .setCancelable(false)
                .setPositiveButton("Bat first", (d, w) ->
                        startMatch(t, fixture, teamA, teamB, /*aBatsFirst=*/aWonToss,
                                winnerName + " won toss, chose to bat"))
                .setNegativeButton("Bowl first", (d, w) ->
                        startMatch(t, fixture, teamA, teamB, /*aBatsFirst=*/!aWonToss,
                                winnerName + " won toss, chose to bowl"))
                .show();
    }

    /**
     * Builds the Match with the chosen batting order and launches InningsActivity.
     */
    private void startMatch(Tournament t, TournamentMatch fixture,
                             TournamentTeam teamA, TournamentTeam teamB,
                             boolean aBatsFirst, String tossSummary) {
        Match m = new Match();
        m.setHomeTeamName(teamA.getName());
        m.setAwayTeamName(teamB.getName());
        m.setMaxOvers(t.getMaxOversPerMatch());
        // "home" = teamA in our convention (set above). If A bats first → "home", else "away"
        m.setBattingFirstTeam(aBatsFirst ? "home" : "away");
        m.setSingleBatsmanMode(t.isSingleBatsmanMode());
        // Clone players so per-match stats don't mutate tournament rosters
        List<Player> homePlayers = new ArrayList<>();
        for (Player p : teamA.getPlayers()) homePlayers.add(new Player(p.getName()));
        List<Player> awayPlayers = new ArrayList<>();
        for (Player p : teamB.getPlayers()) awayPlayers.add(new Player(p.getName()));
        m.setHomePlayers(homePlayers);
        m.setAwayPlayers(awayPlayers);

        Innings i1 = new Innings(1, t.isSingleBatsmanMode());
        m.setFirstInnings(i1);
        m.setCurrentInnings(1);

        ((CricketApp) getApplication()).startNewMatch(m);
        Toast.makeText(this, tossSummary, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, InningsActivity.class));
    }

    // ─── Standings table ──────────────────────────────────────────────────────

    private void renderStandings(Tournament t) {
        tableStandings.removeAllViews();
        addRow(new String[]{"Team", "P", "W", "L", "Pts", "NRR"}, true);
        for (TournamentTeam team : t.getStandings()) {
            addRow(new String[]{
                    team.getName(),
                    String.valueOf(team.getPlayed()),
                    String.valueOf(team.getWins()),
                    String.valueOf(team.getLosses()),
                    String.valueOf(team.getPoints()),
                    String.format(java.util.Locale.US, "%.2f", team.getNetRunRate())
            }, false);
        }
    }

    private void addRow(String[] cells, boolean header) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (header) row.setBackgroundColor(getResources().getColor(R.color.c_row_header_bg, getTheme()));
        int[] widths = {180, 50, 50, 50, 60, 60};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(cells[i]);
            tv.setPadding(dp(10), dp(10), dp(10), dp(10));
            tv.setTextSize(13f);
            tv.setTextColor(getResources().getColor(
                    header ? R.color.c_row_header_text : R.color.c_text_primary, getTheme()));
            if (header) tv.setTypeface(null, Typeface.BOLD);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    dp(widths[i]), TableRow.LayoutParams.WRAP_CONTENT));
            row.addView(tv);
        }
        tableStandings.addView(row);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    /**
     * If the tournament is fully complete and the user navigates away, clear
     * the live tracker so the resume dialog doesn't reappear next launch.
     * (Save button does this explicitly; this is a safety net for users who
     * back out without tapping Save.)
     */
    @Override
    public void onBackPressed() {
        Tournament t = ((CricketApp) getApplication()).getCurrentTournament();
        if (t != null && t.getStage() == Tournament.Stage.COMPLETED) {
            com.cricket.scorer.utils.TournamentStorage.clear(this);
            ((CricketApp) getApplication()).clearTournament();
        }
        super.onBackPressed();
    }
}
