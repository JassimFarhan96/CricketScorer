package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.BowlerStat;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.utils.LiveMatchState;
import com.cricket.scorer.utils.MatchStorage;
import com.cricket.scorer.utils.ShareUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends BaseNavActivity {

    public static final String EXTRA_SAVED_FILE_NAME = "saved_file_name";

    private TextView     tvLabelFirstBatting, tvLabelFirstBowling;
    private TextView     tvLabelSecondBatting, tvLabelSecondBowling;
    private LinearLayout layoutWinnerBanner;
    private TextView     tvWinnerText, tvWinnerSub;
    private TextView     tvTeam1Name, tvTeam1Score, tvTeam1Crr;
    private TextView     tvTeam2Name, tvTeam2Score, tvTeam2Crr;
    private TableLayout  tableFirstBatting, tableSecondBatting;
    private TableLayout  tableFirstBowling, tableSecondBowling;
    private EditText     etWhatsappNumber;
    private Button       btnSaveMatch, btnDeepStats, btnShareWhatsApp, btnShareGeneral, btnNewMatch;

    private Match   match;
    private boolean isViewingFromDisk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNavContentView(R.layout.activity_stats);
        bindViews();

        String savedFile = getIntent().getStringExtra(EXTRA_SAVED_FILE_NAME);
        if (savedFile != null) {
            isViewingFromDisk = true;
            for (Match m : MatchStorage.loadAllMatches(this))
                if (savedFile.equals(m.getSavedFileName())) { match = m; break; }
        } else {
            match = ((CricketApp) getApplication()).getCurrentMatch();
            LiveMatchState.clear(this);
        }

        if (match == null) {
            Toast.makeText(this, "Match data not found", Toast.LENGTH_SHORT).show();
            finish(); return;
        }
        populateStats();
        setClickListeners();
        // Tournament integration: if a tournament is active and the match
        // just finished (not viewing an old one), record the result on the
        // current tournament fixture and route New Match button to dashboard.
        recordMatchInTournamentIfActive();
    }

    private void recordMatchInTournamentIfActive() {
        if (isViewingFromDisk) return;
        com.cricket.scorer.models.Tournament t =
                ((CricketApp) getApplication()).getCurrentTournament();
        if (t == null) return;
        com.cricket.scorer.models.TournamentMatch fixture = t.getCurrentMatch();
        if (fixture == null || fixture.isCompleted()) return;
        if (!match.isMatchCompleted()) return;

        // Determine winner from match result
        String winner = match.getWinnerTeam();
        String winnerName;
        if ("home".equals(winner))      winnerName = match.getHomeTeamName();
        else if ("away".equals(winner)) winnerName = match.getAwayTeamName();
        else                            winnerName = "tie";

        // Per-innings runs and balls
        com.cricket.scorer.models.Innings firstInn  = match.getFirstInnings();
        com.cricket.scorer.models.Innings secondInn = match.getSecondInnings();
        int scoreA = firstInn  != null ? firstInn.getTotalRuns()        : 0;
        int scoreB = secondInn != null ? secondInn.getTotalRuns()       : 0;
        int ballsA = firstInn  != null ? firstInn.getTotalValidBalls()  : 0;
        int ballsB = secondInn != null ? secondInn.getTotalValidBalls() : 0;

        // IPL all-out rule: if a team is bowled out for less than full overs,
        // their FULL allotted overs is used for NRR (not the actual balls).
        // Detect "all out" by checking if the team finished with all wickets down.
        int fullBalls = match.getMaxOvers() * 6;
        boolean teamAAllOut = firstInn  != null && isAllOut(firstInn,  match);
        boolean teamBAllOut = secondInn != null && isAllOut(secondInn, match);
        int nrrBallsA = teamAAllOut ? fullBalls : ballsA;
        int nrrBallsB = teamBAllOut ? fullBalls : ballsB;

        fixture.setCompleted(true);
        fixture.setWinnerName(winnerName);
        fixture.setTeamAScore(scoreA);
        fixture.setTeamBScore(scoreB);
        fixture.setResultDescription(match.getResultDescription());

        // Auto-save the match to disk so TournamentDetailsActivity can later
        // reload and render the full StatsActivity view for it.
        java.io.File savedFile = com.cricket.scorer.utils.MatchStorage.saveMatch(this, match);
        if (savedFile != null) {
            fixture.setSavedMatchFile(savedFile.getName());
        }

        // Update standings using IPL-formula-aware recordMatch
        com.cricket.scorer.models.TournamentTeam tA = t.findTeamByName(fixture.getTeamAName());
        com.cricket.scorer.models.TournamentTeam tB = t.findTeamByName(fixture.getTeamBName());
        if (tA != null && tB != null) {
            // Team A: faced ballsA, bowled ballsB; runsFor = scoreA, runsAgainst = scoreB
            // Team B: faced ballsB, bowled ballsA; runsFor = scoreB, runsAgainst = scoreA
            if (fixture.getTeamAName().equals(winnerName)) {
                tA.recordMatch(true,  scoreA, scoreB, nrrBallsA, nrrBallsB);
                tB.recordMatch(false, scoreB, scoreA, nrrBallsB, nrrBallsA);
            } else if (fixture.getTeamBName().equals(winnerName)) {
                tA.recordMatch(false, scoreA, scoreB, nrrBallsA, nrrBallsB);
                tB.recordMatch(true,  scoreB, scoreA, nrrBallsB, nrrBallsA);
            } else {
                tA.recordTie(scoreA, scoreB, nrrBallsA, nrrBallsB);
                tB.recordTie(scoreB, scoreA, nrrBallsB, nrrBallsA);
            }
        }

        t.setCurrentMatchIndex(t.getCurrentMatchIndex() + 1);
        com.cricket.scorer.utils.TournamentStorage.save(this, t);
    }

    /**
     * Returns true if the given innings ended with the team all out
     * (i.e. ran out of batsmen). Handles single-batsman and two-batsman modes.
     */
    private boolean isAllOut(com.cricket.scorer.models.Innings inn,
                              com.cricket.scorer.models.Match m) {
        // Determine which team batted in this innings
        boolean homeFirst   = "home".equals(m.getBattingFirstTeam());
        boolean isFirstInn  = inn == m.getFirstInnings();
        java.util.List<com.cricket.scorer.models.Player> batters;
        if (isFirstInn) batters = homeFirst ? m.getHomePlayers() : m.getAwayPlayers();
        else            batters = homeFirst ? m.getAwayPlayers() : m.getHomePlayers();

        int retiredHurt = 0;
        for (com.cricket.scorer.models.Player p : batters) if (p.isRetiredHurt()) retiredHurt++;
        int active    = batters.size() - retiredHurt;
        int threshold = m.isSingleBatsmanMode() ? active : active - 1;
        return inn.getTotalWickets() >= threshold;
    }

    private void bindViews() {
        layoutWinnerBanner     = findViewById(R.id.layout_winner_banner);
        tvWinnerText           = findViewById(R.id.tv_winner_text);
        tvWinnerSub            = findViewById(R.id.tv_winner_sub);
        tvTeam1Name            = findViewById(R.id.tv_team1_name);
        tvTeam1Score           = findViewById(R.id.tv_team1_score);
        tvTeam1Crr             = findViewById(R.id.tv_team1_crr);
        tvTeam2Name            = findViewById(R.id.tv_team2_name);
        tvTeam2Score           = findViewById(R.id.tv_team2_score);
        tvTeam2Crr             = findViewById(R.id.tv_team2_crr);
        tvLabelFirstBatting    = findViewById(R.id.tv_label_first_batting);
        tvLabelFirstBowling    = findViewById(R.id.tv_label_first_bowling);
        tvLabelSecondBatting   = findViewById(R.id.tv_label_second_batting);
        tvLabelSecondBowling   = findViewById(R.id.tv_label_second_bowling);
        tableFirstBatting      = findViewById(R.id.table_first_batting);
        tableFirstBowling      = findViewById(R.id.table_first_bowling);
        tableSecondBatting     = findViewById(R.id.table_second_batting);
        tableSecondBowling     = findViewById(R.id.table_second_bowling);
        etWhatsappNumber       = findViewById(R.id.et_whatsapp_number);
        btnSaveMatch           = findViewById(R.id.btn_save_match);
        btnDeepStats           = findViewById(R.id.btn_deep_stats);
        btnShareWhatsApp       = findViewById(R.id.btn_share_whatsapp);
        btnShareGeneral        = findViewById(R.id.btn_share_general);
        btnNewMatch            = findViewById(R.id.btn_new_match);
    }

    private void populateStats() {
        Innings i1        = match.getFirstInnings();
        Innings i2        = match.getSecondInnings();
        boolean homeFirst = match.getBattingFirstTeam().equals("home");
        String  bat1      = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        String  bat2      = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();

        layoutWinnerBanner.setVisibility(View.VISIBLE);
        tvWinnerText.setText(match.getResultDescription() != null
                ? match.getResultDescription() : "Match complete");
        tvWinnerSub.setText(match.getHomeTeamName() + " vs " + match.getAwayTeamName()
                + " \u00b7 " + match.getMaxOvers() + " overs");

        if (i1 != null) {
            tvTeam1Name.setText(bat1); tvTeam1Score.setText(i1.getScoreString());
            tvTeam1Crr.setText(String.format(Locale.US, "%s ov \u00b7 CRR %.2f",
                    i1.getOversString(), i1.getCurrentRunRate()));
        }
        if (i2 != null) {
            tvTeam2Name.setText(bat2); tvTeam2Score.setText(i2.getScoreString());
            tvTeam2Crr.setText(String.format(Locale.US, "%s ov \u00b7 CRR %.2f",
                    i2.getOversString(), i2.getCurrentRunRate()));
        }

        tvLabelFirstBatting.setText("1ST INNINGS \u2014 " + bat1.toUpperCase() + " BATTING");
        tvLabelFirstBowling.setText("1ST INNINGS \u2014 " + bat2.toUpperCase() + " BOWLING");
        tvLabelSecondBatting.setText("2ND INNINGS \u2014 " + bat2.toUpperCase() + " BATTING");
        tvLabelSecondBowling.setText("2ND INNINGS \u2014 " + bat1.toUpperCase() + " BOWLING");

        List<Player> bat1P = homeFirst ? match.getHomePlayers() : match.getAwayPlayers();
        List<Player> bat2P = homeFirst ? match.getAwayPlayers() : match.getHomePlayers();

        buildBattingTable(tableFirstBatting,  bat1P);
        buildBattingTable(tableSecondBatting, bat2P);
        if (i1 != null) buildBowlingTable(tableFirstBowling,  i1.getBowlerStats());
        if (i2 != null) buildBowlingTable(tableSecondBowling, i2.getBowlerStats());

        btnSaveMatch.setVisibility(isViewingFromDisk ? View.GONE : View.VISIBLE);

        // Relabel "New Match" button + hide save/share when tournament is in progress.
        // The tournament owns saving (one save covers all matches) and sharing
        // (final standings) — per-match save/share would be redundant clutter.
        if (((CricketApp) getApplication()).isTournamentActive()) {
            btnNewMatch.setText("Back to Tournament");
            btnSaveMatch.setVisibility(View.GONE);
            // Hide the WhatsApp number input + both share buttons + their section label
            View shareLabel = findViewById(R.id.tv_label_share);
            if (shareLabel != null) shareLabel.setVisibility(View.GONE);
            if (etWhatsappNumber != null) etWhatsappNumber.setVisibility(View.GONE);
            if (btnShareWhatsApp  != null) btnShareWhatsApp.setVisibility(View.GONE);
            if (btnShareGeneral   != null) btnShareGeneral.setVisibility(View.GONE);
        }
    }

    private void buildBattingTable(TableLayout table, List<Player> players) {
        table.removeAllViews();
        addBattingRow(table, new String[]{"Batsman","R","B","4s","6s","SR","Status"}, true);
        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced()==0 && p.getRunsScored()==0) continue;
            String sr = p.getBallsFaced()>0
                    ? String.format(Locale.US,"%.1f",p.getStrikeRate()) : "-";
            addBattingRow(table, new String[]{p.getName(),
                    String.valueOf(p.getRunsScored()), String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()), String.valueOf(p.getSixes()),
                    sr, p.isOut()?"Out":"Not out"}, false);
        }
    }

    private void addBattingRow(TableLayout table, String[] cells, boolean hdr) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (hdr) row.setBackgroundColor(col(R.color.c_row_header_bg));
        int[] w = {280,60,60,60,60,80,100};
        for (int i=0;i<cells.length;i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(dp(w[i]),TableRow.LayoutParams.WRAP_CONTENT));
            tv.setText(cells[i]); tv.setPadding(dp(10),dp(8),dp(10),dp(8)); tv.setTextSize(12f);
            tv.setTextColor(hdr?col(R.color.c_row_header_text):col(R.color.c_text_primary));
            if (hdr) tv.setTypeface(null,Typeface.BOLD);
            row.addView(tv);
        }
        table.addView(row);
    }

    private void buildBowlingTable(TableLayout table, List<BowlerStat> stats) {
        table.removeAllViews();
        addBowlingRow(table, new String[]{"Bowler","O","B","R","W","Econ"}, true);
        if (stats.isEmpty()) { addBowlingRow(table,new String[]{"—","—","—","—","—","—"},false); return; }
        for (BowlerStat s : stats) {
            addBowlingRow(table, new String[]{s.getName(),
                    String.valueOf(s.getOvers()), String.valueOf(s.getBalls()),
                    String.valueOf(s.getRuns()), String.valueOf(s.getWickets()),
                    String.format(Locale.US,"%.2f",s.getEconomy())}, false);
        }
    }

    private void addBowlingRow(TableLayout table, String[] cells, boolean hdr) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (hdr) row.setBackgroundColor(col(R.color.c_row_header_bg));
        int[] w = {220,60,60,60,60,80};
        for (int i=0;i<cells.length;i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(dp(w[i]),TableRow.LayoutParams.WRAP_CONTENT));
            tv.setText(cells[i]); tv.setPadding(dp(10),dp(8),dp(10),dp(8)); tv.setTextSize(12f);
            tv.setTextColor(hdr?col(R.color.c_row_header_text):col(R.color.c_text_primary));
            if (hdr) tv.setTypeface(null,Typeface.BOLD);
            row.addView(tv);
        }
        table.addView(row);
    }

    private void setClickListeners() {
        btnSaveMatch.setOnClickListener(v -> {
            File saved = MatchStorage.saveMatch(this, match);
            if (saved != null) {
                btnSaveMatch.setEnabled(false); btnSaveMatch.setText("Saved \u2713");
                btnSaveMatch.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(col(R.color.green_mid)));
                Toast.makeText(this,"Match saved",Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this,"Failed to save",Toast.LENGTH_SHORT).show();
        });

        // ── In-depth stats ────────────────────────────────────────────────
        btnDeepStats.setOnClickListener(v -> {
            Intent intent = new Intent(this, DeepStatsActivity.class);
            // Pass the saved file name if viewing from disk, otherwise
            // DeepStatsActivity reads from CricketApp
            if (isViewingFromDisk && match.getSavedFileName() != null) {
                intent.putExtra(DeepStatsActivity.EXTRA_SAVED_FILE_NAME,
                        match.getSavedFileName());
            }
            startActivity(intent);
        });

        btnShareWhatsApp.setOnClickListener(v -> {
            String n = etWhatsappNumber.getText().toString().trim();
            if (n.isEmpty()) { Toast.makeText(this,"Enter a WhatsApp number",Toast.LENGTH_SHORT).show(); return; }
            if (!n.startsWith("+")) n = "+" + n;
            ShareUtils.shareViaWhatsApp(this, n, match);
        });
        btnShareGeneral.setOnClickListener(v -> ShareUtils.shareAsText(this, match));
        btnNewMatch.setOnClickListener(v -> {
            ((CricketApp)getApplication()).clearMatch();
            // If tournament active, return to dashboard instead of Home
            boolean tournamentActive =
                    ((CricketApp) getApplication()).isTournamentActive();
            Intent i = new Intent(this,
                    tournamentActive ? TournamentDashboardActivity.class : HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i); finish();
        });
    }

    @Override public void onBackPressed() {
        if (!isViewingFromDisk) ((CricketApp)getApplication()).clearMatch();
        super.onBackPressed();
    }

    private int col(int res) { return getResources().getColor(res,getTheme()); }
    private int dp(int v)    { return (int)(v*getResources().getDisplayMetrics().density); }
    @Override
    protected int getCurrentNavItem() {
        return R.id.nav_stats;
    }

}
