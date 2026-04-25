package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.utils.MatchStorage;

import java.util.List;
import java.util.Locale;

/**
 * MatchSelectActivity.java
 *
 * Shown when the user taps "Match Statistics" from HomeActivity.
 * Lets the user choose a saved match from a Spinner, shows a brief
 * preview card, then navigates to StatsActivity on "View Statistics".
 *
 * Layout: activity_match_select.xml
 */
public class MatchSelectActivity extends AppCompatActivity {

    private Spinner      spinnerMatches;
    private LinearLayout layoutPreview;
    private TextView     tvPreviewTeams;
    private TextView     tvPreviewResult;
    private TextView     tvPreviewScores;
    private TextView     tvPreviewDate;
    private Button       btnViewStats;
    private TextView     tvEmpty;

    private List<Match> allMatches;
    private int         selectedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_select);
        bindViews();
        loadMatches();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMatches(); // refresh in case a match was just saved
    }

    private void bindViews() {
        spinnerMatches  = findViewById(R.id.spinner_matches);
        layoutPreview   = findViewById(R.id.layout_preview);
        tvPreviewTeams  = findViewById(R.id.tv_preview_teams);
        tvPreviewResult = findViewById(R.id.tv_preview_result);
        tvPreviewScores = findViewById(R.id.tv_preview_scores);
        tvPreviewDate   = findViewById(R.id.tv_preview_date);
        btnViewStats    = findViewById(R.id.btn_view_stats);
        tvEmpty         = findViewById(R.id.tv_empty);
    }

    private void loadMatches() {
        allMatches = MatchStorage.loadAllMatches(this);

        if (allMatches.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            spinnerMatches.setVisibility(View.GONE);
            layoutPreview.setVisibility(View.GONE);
            btnViewStats.setVisibility(View.GONE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        spinnerMatches.setVisibility(View.VISIBLE);
        btnViewStats.setVisibility(View.VISIBLE);

        // Build spinner labels: "Mumbai vs Delhi — 23 Apr 2026"
        String[] labels = new String[allMatches.size()];
        for (int i = 0; i < allMatches.size(); i++) {
            Match m = allMatches.get(i);
            String date = m.getSavedFileName() != null
                    ? RecentMatchesActivity.formatDateFromFileName2(m.getSavedFileName()) : "";
            labels[i] = m.getHomeTeamName() + " vs " + m.getAwayTeamName()
                    + "  (" + date + ")";
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getView(pos, cv, p);
                ((TextView) v).setTextColor(Color.parseColor("#111111"));
                ((TextView) v).setTextSize(13f);
                return v;
            }
            @Override
            public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getDropDownView(pos, cv, p);
                ((TextView) v).setTextColor(Color.parseColor("#111111"));
                ((TextView) v).setBackgroundColor(Color.WHITE);
                ((TextView) v).setPadding(28, 20, 28, 20);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMatches.setAdapter(adapter);
        spinnerMatches.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedIndex = pos;
                showPreview(allMatches.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Show preview for first match immediately
        showPreview(allMatches.get(0));

        btnViewStats.setOnClickListener(v -> {
            if (selectedIndex < allMatches.size()) {
                Match chosen = allMatches.get(selectedIndex);
                Intent intent = new Intent(this, StatsActivity.class);
                intent.putExtra(StatsActivity.EXTRA_SAVED_FILE_NAME, chosen.getSavedFileName());
                startActivity(intent);
            }
        });
    }

    /** Populates the preview card for the selected match. */
    private void showPreview(Match m) {
        layoutPreview.setVisibility(View.VISIBLE);
        tvPreviewTeams.setText(m.getHomeTeamName() + " vs " + m.getAwayTeamName());

        // Result
        String result = m.getResultDescription();
        tvPreviewResult.setText(result != null && !result.isEmpty() ? "🏆 " + result : "Result unavailable");

        // Scores
        StringBuilder scores = new StringBuilder();
        String bat1 = m.getBattingFirstTeam().equals("home")
                ? m.getHomeTeamName() : m.getAwayTeamName();
        String bat2 = m.getBattingFirstTeam().equals("home")
                ? m.getAwayTeamName() : m.getHomeTeamName();
        if (m.getFirstInnings() != null) {
            scores.append(bat1).append(": ")
                  .append(m.getFirstInnings().getScoreString())
                  .append(" (").append(m.getFirstInnings().getOversString()).append(" ov)");
        }
        if (m.getSecondInnings() != null) {
            scores.append("\n").append(bat2).append(": ")
                  .append(m.getSecondInnings().getScoreString())
                  .append(" (").append(m.getSecondInnings().getOversString()).append(" ov)");
        }
        tvPreviewScores.setText(scores.toString());

        // Date + format
        String modeLabel = m.isSingleBatsmanMode() ? "Single bat" : "Two bat";
        String date = m.getSavedFileName() != null
                ? RecentMatchesActivity.formatDateFromFileName2(m.getSavedFileName()) : "Unknown date";
        tvPreviewDate.setText(date + "  ·  " + m.getMaxOvers() + " overs  ·  " + modeLabel);
    }
}
