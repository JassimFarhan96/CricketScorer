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
import com.cricket.scorer.utils.LiveMatchState;
import com.cricket.scorer.utils.MatchStorage;
import com.cricket.scorer.utils.ShareUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * StatsActivity.java
 *
 * CHANGE: When opened after a live match (not from disk), calls
 * LiveMatchState.clear() immediately — the match is complete so the
 * auto-save slot must be emptied for the next match.
 */
public class StatsActivity extends AppCompatActivity {

    public static final String EXTRA_SAVED_FILE_NAME = "saved_file_name";

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

    private Match   match;
    private boolean isViewingFromDisk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        bindViews();

        String savedFile = getIntent().getStringExtra(EXTRA_SAVED_FILE_NAME);
        if (savedFile != null) {
            isViewingFromDisk = true;
            List<Match> all = MatchStorage.loadAllMatches(this);
            for (Match m : all) {
                if (savedFile.equals(m.getSavedFileName())) { match = m; break; }
            }
        } else {
            CricketApp app = (CricketApp) getApplication();
            match = app.getCurrentMatch();

            // ── CLEAR the live auto-save slot — match is over ─────────────
            // Done here (not in MatchEngine) so it happens exactly once,
            // only when the user actually reaches the stats screen.
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

    private void populateStats() {
        Innings i1   = match.getFirstInnings();
        Innings i2   = match.getSecondInnings();
        String  bat1 = match.getBattingFirstTeam().equals("home")
                ? match.getHomeTeamName() : match.getAwayTeamName();
        String  bat2 = match.getBattingFirstTeam().equals("home")
                ? match.getAwayTeamName() : match.getHomeTeamName();

        layoutWinnerBanner.setVisibility(View.VISIBLE);
        tvWinnerText.setText(match.getResultDescription() != null
                ? match.getResultDescription() : "Match complete");
        tvWinnerSub.setText(match.getHomeTeamName() + " vs " + match.getAwayTeamName()
                + " · " + match.getMaxOvers() + " overs");

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

        List<Player> bat1Players = match.getBattingFirstTeam().equals("home")
                ? match.getHomePlayers() : match.getAwayPlayers();
        List<Player> bat2Players = match.getBattingFirstTeam().equals("home")
                ? match.getAwayPlayers() : match.getHomePlayers();

        buildBattingTable(tableFirstInnings, bat1Players);
        buildBattingTable(tableSecondInnings, bat2Players);

        btnSaveMatch.setVisibility(isViewingFromDisk ? View.GONE : View.VISIBLE);
    }

    private void buildBattingTable(TableLayout table, List<Player> players) {
        table.removeAllViews();
        addTableRow(table, new String[]{"Batsman","R","B","4s","6s","SR","Status"}, true);
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
        int[] weights = {3,1,1,1,1,1,2};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(
                    0, TableRow.LayoutParams.WRAP_CONTENT, weights[i]));
            tv.setText(cells[i]);
            tv.setPadding(10, 8, 10, 8);
            tv.setTextSize(11.5f);
            tv.setTextColor(isHeader
                    ? Color.parseColor("#666666") : Color.parseColor("#111111"));
            if (isHeader) tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);
        }
        table.addView(row);
    }

    private void setClickListeners() {
        btnSaveMatch.setOnClickListener(v -> {
            File saved = MatchStorage.saveMatch(this, match);
            if (saved != null) {
                btnSaveMatch.setEnabled(false);
                btnSaveMatch.setText("Saved ✓");
                btnSaveMatch.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#1D9E75")));
                Toast.makeText(this,
                        "Match saved to recent_matches/", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to save match", Toast.LENGTH_SHORT).show();
            }
        });

        btnShareWhatsApp.setOnClickListener(v -> {
            String number = etWhatsappNumber.getText().toString().trim();
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a WhatsApp number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!number.startsWith("+")) number = "+" + number;
            ShareUtils.shareViaWhatsApp(this, number, match);
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
        if (!isViewingFromDisk) {
            ((CricketApp) getApplication()).clearMatch();
        }
        super.onBackPressed();
    }
}
