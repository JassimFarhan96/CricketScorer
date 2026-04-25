package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
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
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.utils.MatchStorage;
import com.cricket.scorer.utils.ShareUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * StatsActivity.java
 *
 * CHANGE: Added "Save Match" button at the top of the screen.
 * Tapping it calls MatchStorage.saveMatch() which writes a JSON file
 * to the app's internal recent_matches/ directory.
 * After saving, the button is disabled and labelled "Saved ✓".
 *
 * Can also be launched from MatchSelectActivity (Recent/Stats flow)
 * by passing EXTRA_SAVED_FILE_NAME — in that case the Match is loaded
 * from disk instead of from CricketApp, and the Save button is hidden.
 */
public class StatsActivity extends AppCompatActivity {

    /** When launched from MatchSelectActivity, this extra holds the filename. */
    public static final String EXTRA_SAVED_FILE_NAME = "saved_file_name";

    // ─── Views ────────────────────────────────────────────────────────────────
    private LinearLayout layoutWinnerBanner;
    private TextView     tvWinnerText;
    private TextView     tvWinnerSub;
    private TextView     tvTeam1Name, tvTeam1Score, tvTeam1Crr;
    private TextView     tvTeam2Name, tvTeam2Score, tvTeam2Crr;
    private TableLayout  tableFirstInnings;
    private TableLayout  tableSecondInnings;
    private EditText     etWhatsappNumber;
    private Button       btnSaveMatch;
    private Button       btnShareWhatsApp;
    private Button       btnShareGeneral;
    private Button       btnNewMatch;

    // ─── Data ─────────────────────────────────────────────────────────────────
    private Match   match;
    private boolean isViewingFromDisk = false; // true when opened from Recent/Stats menu

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        bindViews();

        // Determine source: live match just completed, or loaded from disk?
        String savedFile = getIntent().getStringExtra(EXTRA_SAVED_FILE_NAME);
        if (savedFile != null) {
            // Launched from MatchSelectActivity — load from disk
            isViewingFromDisk = true;
            List<Match> all = MatchStorage.loadAllMatches(this);
            for (Match m : all) {
                if (savedFile.equals(m.getSavedFileName())) {
                    match = m;
                    break;
                }
            }
        } else {
            // Launched after live match completed
            CricketApp app = (CricketApp) getApplication();
            match = app.getCurrentMatch();
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
        layoutWinnerBanner = findViewById(R.id.layout_winner_banner);
        tvWinnerText       = findViewById(R.id.tv_winner_text);
        tvWinnerSub        = findViewById(R.id.tv_winner_sub);
        tvTeam1Name        = findViewById(R.id.tv_team1_name);
        tvTeam1Score       = findViewById(R.id.tv_team1_score);
        tvTeam1Crr         = findViewById(R.id.tv_team1_crr);
        tvTeam2Name        = findViewById(R.id.tv_team2_name);
        tvTeam2Score       = findViewById(R.id.tv_team2_score);
        tvTeam2Crr         = findViewById(R.id.tv_team2_crr);
        tableFirstInnings  = findViewById(R.id.table_first_innings);
        tableSecondInnings = findViewById(R.id.table_second_innings);
        etWhatsappNumber   = findViewById(R.id.et_whatsapp_number);
        btnSaveMatch       = findViewById(R.id.btn_save_match);
        btnShareWhatsApp   = findViewById(R.id.btn_share_whatsapp);
        btnShareGeneral    = findViewById(R.id.btn_share_general);
        btnNewMatch        = findViewById(R.id.btn_new_match);
    }

    // ─── Data population ──────────────────────────────────────────────────────

    private void populateStats() {
        Innings i1   = match.getFirstInnings();
        Innings i2   = match.getSecondInnings();
        String  bat1 = match.getBattingFirstTeam().equals("home")
                       ? match.getHomeTeamName() : match.getAwayTeamName();
        String  bat2 = match.getBattingFirstTeam().equals("home")
                       ? match.getAwayTeamName() : match.getHomeTeamName();

        // ── Winner banner ──────────────────────────────────────────────
        layoutWinnerBanner.setVisibility(View.VISIBLE);
        tvWinnerText.setText(match.getResultDescription() != null
                ? match.getResultDescription() : "Match in progress");
        tvWinnerSub.setText(match.getHomeTeamName() + " vs " + match.getAwayTeamName()
                + " · " + match.getMaxOvers() + " overs");

        // ── Summary cards ──────────────────────────────────────────────
        if (i1 != null) {
            tvTeam1Name.setText(bat1);
            tvTeam1Score.setText(i1.getScoreString());
            tvTeam1Crr.setText(String.format(Locale.US,
                    "%s ov · CRR %.2f", i1.getOversString(), i1.getCurrentRunRate()));
        }
        if (i2 != null) {
            tvTeam2Name.setText(bat2);
            tvTeam2Score.setText(i2.getScoreString());
            tvTeam2Crr.setText(String.format(Locale.US,
                    "%s ov · CRR %.2f", i2.getOversString(), i2.getCurrentRunRate()));
        }

        // ── Batting tables ─────────────────────────────────────────────
        List<Player> bat1Players = match.getBattingFirstTeam().equals("home")
                ? match.getHomePlayers() : match.getAwayPlayers();
        List<Player> bat2Players = match.getBattingFirstTeam().equals("home")
                ? match.getAwayPlayers() : match.getHomePlayers();

        buildBattingTable(tableFirstInnings, bat1Players);
        buildBattingTable(tableSecondInnings, bat2Players);

        // ── Save button visibility ─────────────────────────────────────
        if (isViewingFromDisk) {
            // Already on disk — hide save button
            btnSaveMatch.setVisibility(View.GONE);
        } else {
            btnSaveMatch.setVisibility(View.VISIBLE);
        }
    }

    private void buildBattingTable(TableLayout table, List<Player> players) {
        table.removeAllViews();
        addTableRow(table, new String[]{"Batsman", "R", "B", "4s", "6s", "SR", "Status"}, true);
        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0 && p.getRunsScored() == 0) continue;
            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";
            addTableRow(table, new String[]{
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

    private void addTableRow(TableLayout table, String[] cells, boolean isHeader) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        if (isHeader) row.setBackgroundColor(Color.parseColor("#F1EFE8"));
        int[] weights = {3, 1, 1, 1, 1, 1, 2};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, weights[i]));
            tv.setText(cells[i]);
            tv.setPadding(10, 8, 10, 8);
            tv.setTextSize(11.5f);
            tv.setTextColor(isHeader ? Color.parseColor("#666666") : Color.parseColor("#111111"));
            if (isHeader) tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
        }
        table.addView(row);
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {

        // ── Save match to disk ─────────────────────────────────────────
        btnSaveMatch.setOnClickListener(v -> {
            File saved = MatchStorage.saveMatch(this, match);
            if (saved != null) {
                btnSaveMatch.setEnabled(false);
                btnSaveMatch.setText("Saved ✓");
                btnSaveMatch.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#1D9E75")));
                Toast.makeText(this,
                        "Match saved to recent_matches/", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to save match", Toast.LENGTH_SHORT).show();
            }
        });

        // ── WhatsApp share ─────────────────────────────────────────────
        btnShareWhatsApp.setOnClickListener(v -> {
            String number = etWhatsappNumber.getText().toString().trim();
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a WhatsApp number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!number.startsWith("+")) number = "+" + number;
            ShareUtils.shareViaWhatsApp(this, number, match);
        });

        // ── Generic share ──────────────────────────────────────────────
        btnShareGeneral.setOnClickListener(v -> ShareUtils.shareAsText(this, match));

        // ── New match ──────────────────────────────────────────────────
        btnNewMatch.setOnClickListener(v -> {
            CricketApp app = (CricketApp) getApplication();
            app.clearMatch();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        if (isViewingFromDisk) {
            super.onBackPressed(); // allow normal back to MatchSelectActivity
        } else {
            CricketApp app = (CricketApp) getApplication();
            app.clearMatch();
            super.onBackPressed();
        }
    }
}
