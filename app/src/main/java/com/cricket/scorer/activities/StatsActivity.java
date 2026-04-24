package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.cricket.scorer.utils.ShareUtils;

import java.util.List;
import java.util.Locale;

/**
 * StatsActivity.java
 * Displays the full match scorecard and allows sharing via WhatsApp.
 *
 * Sections:
 *  1. Winner banner (team name + margin)
 *  2. Summary cards (runs/wickets for both teams)
 *  3. 1st innings batting table
 *  4. 2nd innings batting table
 *  5. WhatsApp share section (phone number + share button)
 *  6. New match button
 *
 * Layout: activity_stats.xml
 */
public class StatsActivity extends AppCompatActivity {

    // ─── Views ────────────────────────────────────────────────────────────────
    private LinearLayout layoutWinnerBanner;
    private TextView tvWinnerText;
    private TextView tvWinnerSub;

    private TextView tvTeam1Summary;
    private TextView tvTeam1Score;
    private TextView tvTeam1CRR;
    private TextView tvTeam2Summary;
    private TextView tvTeam2Score;
    private TextView tvTeam2CRR;

    private TableLayout tableFirstInnings;
    private TableLayout tableSecondInnings;

    private EditText etWhatsappNumber;
    private Button btnShareWhatsApp;
    private Button btnShareGeneral;
    private Button btnNewMatch;

    // ─── Data ─────────────────────────────────────────────────────────────────
    private Match match;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        CricketApp app = (CricketApp) getApplication();
        match = app.getCurrentMatch();

        if (match == null) { finish(); return; }

        bindViews();
        populateStats();
        setClickListeners();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        layoutWinnerBanner   = findViewById(R.id.layout_winner_banner);
        tvWinnerText         = findViewById(R.id.tv_winner_text);
        tvWinnerSub          = findViewById(R.id.tv_winner_sub);

        tvTeam1Summary       = findViewById(R.id.tv_team1_name);
        tvTeam1Score         = findViewById(R.id.tv_team1_score);
        tvTeam1CRR           = findViewById(R.id.tv_team1_crr);
        tvTeam2Summary       = findViewById(R.id.tv_team2_name);
        tvTeam2Score         = findViewById(R.id.tv_team2_score);
        tvTeam2CRR           = findViewById(R.id.tv_team2_crr);

        tableFirstInnings    = findViewById(R.id.table_first_innings);
        tableSecondInnings   = findViewById(R.id.table_second_innings);

        etWhatsappNumber     = findViewById(R.id.et_whatsapp_number);
        btnShareWhatsApp     = findViewById(R.id.btn_share_whatsapp);
        btnShareGeneral      = findViewById(R.id.btn_share_general);
        btnNewMatch          = findViewById(R.id.btn_new_match);
    }

    // ─── Data population ──────────────────────────────────────────────────────

    private void populateStats() {
        Innings i1 = match.getFirstInnings();
        Innings i2 = match.getSecondInnings();

        String bat1 = match.getBattingFirstTeam().equals("home")
                ? match.getHomeTeamName() : match.getAwayTeamName();
        String bat2 = match.getBattingFirstTeam().equals("home")
                ? match.getAwayTeamName() : match.getHomeTeamName();

        // ── Winner banner ──────────────────────────────────────────────────
        layoutWinnerBanner.setVisibility(View.VISIBLE);
        tvWinnerText.setText(match.getResultDescription());
        tvWinnerSub.setText(match.getHomeTeamName() + " vs " + match.getAwayTeamName()
                + " · " + match.getMaxOvers() + " overs");

        // ── Team summary cards ─────────────────────────────────────────────
        if (i1 != null) {
            tvTeam1Summary.setText(bat1);
            tvTeam1Score.setText(i1.getScoreString());
            tvTeam1CRR.setText(String.format(Locale.US,
                    "%s ov · CRR %.2f", i1.getOversString(), i1.getCurrentRunRate()));
        }
        if (i2 != null) {
            tvTeam2Summary.setText(bat2);
            tvTeam2Score.setText(i2.getScoreString());
            tvTeam2CRR.setText(String.format(Locale.US,
                    "%s ov · CRR %.2f", i2.getOversString(), i2.getCurrentRunRate()));
        }

        // ── Batting tables ─────────────────────────────────────────────────
        List<Player> bat1Players = match.getBattingFirstTeam().equals("home")
                ? match.getHomePlayers() : match.getAwayPlayers();
        List<Player> bat2Players = match.getBattingFirstTeam().equals("home")
                ? match.getAwayPlayers() : match.getHomePlayers();

        buildBattingTable(tableFirstInnings, bat1Players);
        buildBattingTable(tableSecondInnings, bat2Players);
    }

    /** Builds a full batting scorecard table for one innings */
    private void buildBattingTable(TableLayout table, List<Player> players) {
        table.removeAllViews();

        // Header
        addTableRow(table, new String[]{"Batsman", "R", "B", "4s", "6s", "SR", "Status"}, true);

        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0 && p.getRunsScored() == 0) continue;

            String sr = p.getBallsFaced() > 0
                    ? String.format(Locale.US, "%.1f", p.getStrikeRate()) : "-";
            String status = p.isOut() ? "Out" : "Not out";

            addTableRow(table, new String[]{
                    p.getName(),
                    String.valueOf(p.getRunsScored()),
                    String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()),
                    String.valueOf(p.getSixes()),
                    sr,
                    status
            }, false);
        }
    }

    private void addTableRow(TableLayout table, String[] cells, boolean isHeader) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT));

        if (isHeader) row.setBackgroundColor(Color.parseColor("#F1EFE8"));

        int[] weights = {3, 1, 1, 1, 1, 1, 2};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(0,
                    TableRow.LayoutParams.WRAP_CONTENT, weights[i]));
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

        // ── WhatsApp share ─────────────────────────────────────────────────
        btnShareWhatsApp.setOnClickListener(v -> {
            String number = etWhatsappNumber.getText().toString().trim();
            if (TextUtils.isEmpty(number)) {
                Toast.makeText(this, "Enter a WhatsApp number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!number.startsWith("+")) number = "+" + number;
            ShareUtils.shareViaWhatsApp(this, number, match);
        });

        // ── Generic share (any app) ────────────────────────────────────────
        btnShareGeneral.setOnClickListener(v -> ShareUtils.shareAsText(this, match));

        // ── New match ──────────────────────────────────────────────────────
        btnNewMatch.setOnClickListener(v -> {
            CricketApp app = (CricketApp) getApplication();
            app.clearMatch();
            Intent intent = new Intent(StatsActivity.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    /** Prevent navigating back into the completed innings */
    @Override
    public void onBackPressed() {
        // Let user go back to home if they want
        CricketApp app = (CricketApp) getApplication();
        app.clearMatch();
        super.onBackPressed();
    }
}
