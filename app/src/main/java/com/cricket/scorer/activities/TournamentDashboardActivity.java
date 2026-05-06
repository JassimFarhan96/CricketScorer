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
    private Button       btnStartNext, btnStartSemis, btnStartFinal, btnFinish;

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
        tvChampion.setVisibility(View.GONE);
        layoutSemiTeams.setVisibility(View.GONE);
        tvNextMatch.setVisibility(View.GONE);

        switch (t.getStage()) {
            case LEAGUE:
                if (t.isCurrentStageComplete()) {
                    // League finished — show top 4 and prompt for semis
                    showTopFour(t);
                    btnStartSemis.setVisibility(View.VISIBLE);
                    btnStartSemis.setOnClickListener(v -> startSemifinals(t));
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
                btnFinish.setVisibility(View.VISIBLE);
                btnFinish.setOnClickListener(v -> finishTournament());
                break;
        }
    }

    private void showNextMatch(Tournament t) {
        TournamentMatch next = t.getCurrentMatch();
        if (next == null) return;
        tvNextMatch.setVisibility(View.VISIBLE);
        tvNextMatch.setText("Next match: " + next.getLabel());
        btnStartNext.setVisibility(View.VISIBLE);
        btnStartNext.setText("Start: " + next.getLabel());
        btnStartNext.setOnClickListener(v -> launchMatch(t, next));
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
            btnFinish.setVisibility(View.VISIBLE);
            btnFinish.setOnClickListener(v -> finishTournament());
        }
    }

    private void startSemifinals(Tournament t) {
        List<TournamentTeam> top4 = t.getTopTeams(4);
        if (top4.size() < 4) {
            Toast.makeText(this, "Need at least 4 teams for semis", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TournamentMatch> semis = new ArrayList<>();
        // 1 vs 4
        semis.add(new TournamentMatch(top4.get(0).getName(), top4.get(3).getName()));
        // 2 vs 3
        semis.add(new TournamentMatch(top4.get(1).getName(), top4.get(2).getName()));
        t.setSemiFixtures(semis);
        t.setStage(Tournament.Stage.SEMIFINAL);
        t.setCurrentMatchIndex(0);
        TournamentStorage.save(this, t);
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

        Match m = new Match();
        m.setHomeTeamName(teamA.getName());
        m.setAwayTeamName(teamB.getName());
        m.setMaxOvers(t.getMaxOversPerMatch());
        m.setBattingFirstTeam("home"); // Team A bats first by default
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
                    String.valueOf(team.getNetRunsScored())
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
}
