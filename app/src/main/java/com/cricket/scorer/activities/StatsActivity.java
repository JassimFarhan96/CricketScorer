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

/**
 * StatsActivity.java
 *
 * CHANGE: Section headers now include the actual team names so the user
 * can clearly see which team batted and which team bowled in each innings.
 *
 * Labels are set dynamically in populateStats() using the team names
 * from the match data. Example output:
 *
 *   1ST INNINGS — JSJB BATTING
 *   1ST INNINGS — LDN BOWLING
 *   2ND INNINGS — LDN BATTING
 *   2ND INNINGS — JSJB BOWLING
 */
public class StatsActivity extends AppCompatActivity {

    public static final String EXTRA_SAVED_FILE_NAME = "saved_file_name";

    // ── Section label views (IDs match activity_stats.xml) ───────────────────
    private TextView tvLabelFirstBatting;
    private TextView tvLabelFirstBowling;
    private TextView tvLabelSecondBatting;
    private TextView tvLabelSecondBowling;

    // ── Score summary ─────────────────────────────────────────────────────────
    private LinearLayout layoutWinnerBanner;
    private TextView     tvWinnerText, tvWinnerSub;
    private TextView     tvTeam1Name, tvTeam1Score, tvTeam1Crr;
    private TextView     tvTeam2Name, tvTeam2Score, tvTeam2Crr;

    // ── Tables ────────────────────────────────────────────────────────────────
    private TableLayout tableFirstBatting, tableSecondBatting;
    private TableLayout tableFirstBowling, tableSecondBowling;

    // ── Share / action buttons ────────────────────────────────────────────────
    private EditText etWhatsappNumber;
    private Button   btnSaveMatch, btnShareWhatsApp, btnShareGeneral, btnNewMatch;

    // ── Data ──────────────────────────────────────────────────────────────────
    private Match   match;
    private boolean isViewingFromDisk = false;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
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
            finish();
            return;
        }

        populateStats();
        setClickListeners();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

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
        btnShareWhatsApp       = findViewById(R.id.btn_share_whatsapp);
        btnShareGeneral        = findViewById(R.id.btn_share_general);
        btnNewMatch            = findViewById(R.id.btn_new_match);
    }

    // ─── Stats population ─────────────────────────────────────────────────────

    private void populateStats() {
        Innings i1      = match.getFirstInnings();
        Innings i2      = match.getSecondInnings();
        boolean homeFirst = match.getBattingFirstTeam().equals("home");

        // Team that batted first (innings 1 batting team)
        String bat1Team  = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        // Team that batted second (innings 2 batting team)
        String bat2Team  = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();
        // Innings 1 bowling team = innings 2 batting team (and vice-versa)
        String bowl1Team = bat2Team;
        String bowl2Team = bat1Team;

        // ── Winner banner ──────────────────────────────────────────────────
        layoutWinnerBanner.setVisibility(View.VISIBLE);
        tvWinnerText.setText(match.getResultDescription() != null
                ? match.getResultDescription() : "Match complete");
        tvWinnerSub.setText(match.getHomeTeamName() + " vs " + match.getAwayTeamName()
                + " \u00b7 " + match.getMaxOvers() + " overs");

        // ── Score cards ────────────────────────────────────────────────────
        if (i1 != null) {
            tvTeam1Name.setText(bat1Team);
            tvTeam1Score.setText(i1.getScoreString());
            tvTeam1Crr.setText(String.format(Locale.US, "%s ov \u00b7 CRR %.2f",
                    i1.getOversString(), i1.getCurrentRunRate()));
        }
        if (i2 != null) {
            tvTeam2Name.setText(bat2Team);
            tvTeam2Score.setText(i2.getScoreString());
            tvTeam2Crr.setText(String.format(Locale.US, "%s ov \u00b7 CRR %.2f",
                    i2.getOversString(), i2.getCurrentRunRate()));
        }

        // ── Section headers with team names ────────────────────────────────
        // Format: "1ST INNINGS — JSJB BATTING"
        tvLabelFirstBatting.setText(
                "1ST INNINGS \u2014 " + bat1Team.toUpperCase() + " BATTING");
        tvLabelFirstBowling.setText(
                "1ST INNINGS \u2014 " + bowl1Team.toUpperCase() + " BOWLING");
        tvLabelSecondBatting.setText(
                "2ND INNINGS \u2014 " + bat2Team.toUpperCase() + " BATTING");
        tvLabelSecondBowling.setText(
                "2ND INNINGS \u2014 " + bowl2Team.toUpperCase() + " BOWLING");

        // ── Player lists ───────────────────────────────────────────────────
        List<Player> bat1Players  = homeFirst ? match.getHomePlayers() : match.getAwayPlayers();
        List<Player> bat2Players  = homeFirst ? match.getAwayPlayers() : match.getHomePlayers();

        // ── Build tables ───────────────────────────────────────────────────
        buildBattingTable(tableFirstBatting,  bat1Players);
        buildBattingTable(tableSecondBatting, bat2Players);

        if (i1 != null) buildBowlingTable(tableFirstBowling,  i1.getBowlerStats());
        if (i2 != null) buildBowlingTable(tableSecondBowling, i2.getBowlerStats());

        btnSaveMatch.setVisibility(isViewingFromDisk ? View.GONE : View.VISIBLE);
    }

    // ─── Table builders ───────────────────────────────────────────────────────

    private void buildBattingTable(TableLayout table, List<Player> players) {
        table.removeAllViews();
        addBattingRow(table,
                new String[]{"Batsman", "R", "B", "4s", "6s", "SR", "Status"}, true);
        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0 && p.getRunsScored() == 0) continue;
            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";
            addBattingRow(table, new String[]{
                    p.getName(),
                    String.valueOf(p.getRunsScored()),
                    String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()),
                    String.valueOf(p.getSixes()),
                    sr,
                    p.isOut() ? "Out" : "Not out"
            }, false);
        }
    }

    private void addBattingRow(TableLayout table, String[] cells, boolean isHeader) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (isHeader) row.setBackgroundColor(col(R.color.c_row_header_bg));
        int[] widths = {280, 60, 60, 60, 60, 80, 100};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    dp(widths[i]), TableRow.LayoutParams.WRAP_CONTENT));
            tv.setText(cells[i]);
            tv.setPadding(dp(10), dp(8), dp(10), dp(8));
            tv.setTextSize(12f);
            tv.setTextColor(isHeader
                    ? col(R.color.c_row_header_text) : col(R.color.c_text_primary));
            if (isHeader) tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
        }
        table.addView(row);
    }

    private void buildBowlingTable(TableLayout table, List<BowlerStat> stats) {
        table.removeAllViews();
        addBowlingRow(table, new String[]{"Bowler", "O", "B", "R", "W", "Econ"}, true);
        if (stats.isEmpty()) {
            addBowlingRow(table, new String[]{"—", "—", "—", "—", "—", "—"}, false);
            return;
        }
        for (BowlerStat s : stats) {
            addBowlingRow(table, new String[]{
                    s.getName(),
                    String.valueOf(s.getOvers()),
                    String.valueOf(s.getBalls()),
                    String.valueOf(s.getRuns()),
                    String.valueOf(s.getWickets()),
                    String.format(Locale.US, "%.2f", s.getEconomy())
            }, false);
        }
    }

    private void addBowlingRow(TableLayout table, String[] cells, boolean isHeader) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (isHeader) row.setBackgroundColor(col(R.color.c_row_header_bg));
        int[] widths = {220, 60, 60, 60, 60, 80};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    dp(widths[i]), TableRow.LayoutParams.WRAP_CONTENT));
            tv.setText(cells[i]);
            tv.setPadding(dp(10), dp(8), dp(10), dp(8));
            tv.setTextSize(12f);
            tv.setTextColor(isHeader
                    ? col(R.color.c_row_header_text) : col(R.color.c_text_primary));
            if (isHeader) tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
        }
        table.addView(row);
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {
        btnSaveMatch.setOnClickListener(v -> {
            File saved = MatchStorage.saveMatch(this, match);
            if (saved != null) {
                btnSaveMatch.setEnabled(false);
                btnSaveMatch.setText("Saved \u2713");
                btnSaveMatch.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(col(R.color.green_mid)));
                Toast.makeText(this, "Match saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to save match", Toast.LENGTH_SHORT).show();
            }
        });

        btnShareWhatsApp.setOnClickListener(v -> {
            String n = etWhatsappNumber.getText().toString().trim();
            if (n.isEmpty()) {
                Toast.makeText(this, "Enter a WhatsApp number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!n.startsWith("+")) n = "+" + n;
            ShareUtils.shareViaWhatsApp(this, n, match);
        });

        btnShareGeneral.setOnClickListener(v -> ShareUtils.shareAsText(this, match));

        btnNewMatch.setOnClickListener(v -> {
            ((CricketApp) getApplication()).clearMatch();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        if (!isViewingFromDisk) ((CricketApp) getApplication()).clearMatch();
        super.onBackPressed();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int col(int colorRes) { return getResources().getColor(colorRes, getTheme()); }
    private int dp(int val) { return (int)(val * getResources().getDisplayMetrics().density); }
}
