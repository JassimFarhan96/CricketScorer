package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.utils.MatchStorage;

import java.util.List;
import java.util.Locale;

/**
 * RecentMatchesActivity.java
 *
 * Displays saved matches from the recent_matches/ directory.
 * Shows 5 matches per page with Prev / Next pagination buttons.
 *
 * Each match card shows:
 *   - Teams & result
 *   - Score summary
 *   - Date (from filename timestamp)
 *   - "View Stats" button → StatsActivity
 *   - "Delete" button → removes file, refreshes list
 *
 * Layout: activity_recent_matches.xml
 */
public class RecentMatchesActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 5;

    // ─── Views ────────────────────────────────────────────────────────────────
    private LinearLayout containerMatches;
    private TextView     tvPageInfo;
    private TextView     tvEmptyState;
    private Button       btnPrev;
    private Button       btnNext;

    // ─── State ────────────────────────────────────────────────────────────────
    private List<Match> allMatches;
    private int         currentPage = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_matches);
        bindViews();
        loadAndDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh after returning from StatsActivity (in case a match was saved)
        loadAndDisplay();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        containerMatches = findViewById(R.id.container_matches);
        tvPageInfo       = findViewById(R.id.tv_page_info);
        tvEmptyState     = findViewById(R.id.tv_empty_state);
        btnPrev          = findViewById(R.id.btn_prev);
        btnNext          = findViewById(R.id.btn_next);

        btnPrev.setOnClickListener(v -> {
            if (currentPage > 1) { currentPage--; displayPage(); }
        });
        btnNext.setOnClickListener(v -> {
            int total = MatchStorage.getTotalPages(allMatches, PAGE_SIZE);
            if (currentPage < total) { currentPage++; displayPage(); }
        });
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    private void loadAndDisplay() {
        allMatches = MatchStorage.loadAllMatches(this);
        currentPage = 1;
        displayPage();
    }

    // ─── Page rendering ───────────────────────────────────────────────────────

    private void displayPage() {
        containerMatches.removeAllViews();
        int totalPages = MatchStorage.getTotalPages(allMatches, PAGE_SIZE);

        if (allMatches.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvPageInfo.setVisibility(View.GONE);
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        tvPageInfo.setVisibility(View.VISIBLE);
        btnPrev.setVisibility(View.VISIBLE);
        btnNext.setVisibility(View.VISIBLE);

        List<Match> pageMatches = MatchStorage.getPage(allMatches, currentPage, PAGE_SIZE);
        for (Match m : pageMatches) {
            containerMatches.addView(buildMatchCard(m));
        }

        tvPageInfo.setText(String.format(Locale.US,
                "Page %d of %d  (%d matches total)", currentPage, totalPages, allMatches.size()));
        btnPrev.setEnabled(currentPage > 1);
        btnPrev.setAlpha(currentPage > 1 ? 1f : 0.4f);
        btnNext.setEnabled(currentPage < totalPages);
        btnNext.setAlpha(currentPage < totalPages ? 1f : 0.4f);
    }

    // ─── Match card builder ───────────────────────────────────────────────────

    private View buildMatchCard(Match m) {
        // Root card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.bg_match_card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setElevation(dp(2));

        // ── Teams header ─────────────────────────────────────────────────
        TextView tvTeams = new TextView(this);
        tvTeams.setText(m.getHomeTeamName() + " vs " + m.getAwayTeamName());
        tvTeams.setTextSize(16f);
        tvTeams.setTextColor(Color.parseColor("#085041"));
        tvTeams.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvTeams);

        // ── Format badge ─────────────────────────────────────────────────
        String modeLabel = m.isSingleBatsmanMode() ? "Single bat" : "Two bat";
        TextView tvFormat = new TextView(this);
        tvFormat.setText(m.getMaxOvers() + " overs · " + modeLabel);
        tvFormat.setTextSize(12f);
        tvFormat.setTextColor(Color.parseColor("#888780"));
        LinearLayout.LayoutParams fmtParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fmtParams.setMargins(0, 4, 0, 8);
        tvFormat.setLayoutParams(fmtParams);
        card.addView(tvFormat);

        // ── Divider ───────────────────────────────────────────────────────
        card.addView(makeDivider());

        // ── Innings scores ────────────────────────────────────────────────
        if (m.getFirstInnings() != null) {
            String bat1 = m.getBattingFirstTeam().equals("home")
                    ? m.getHomeTeamName() : m.getAwayTeamName();
            card.addView(makeScoreLine(bat1,
                    m.getFirstInnings().getScoreString(),
                    m.getFirstInnings().getOversString()));
        }
        if (m.getSecondInnings() != null) {
            String bat2 = m.getBattingFirstTeam().equals("home")
                    ? m.getAwayTeamName() : m.getHomeTeamName();
            card.addView(makeScoreLine(bat2,
                    m.getSecondInnings().getScoreString(),
                    m.getSecondInnings().getOversString()));
        }

        // ── Result ────────────────────────────────────────────────────────
        if (m.getResultDescription() != null && !m.getResultDescription().isEmpty()) {
            card.addView(makeDivider());
            TextView tvResult = new TextView(this);
            tvResult.setText("🏆 " + m.getResultDescription());
            tvResult.setTextSize(13f);
            tvResult.setTextColor(Color.parseColor("#085041"));
            tvResult.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 8, 0, 8);
            tvResult.setLayoutParams(rp);
            card.addView(tvResult);
        }

        // ── Date (from filename) ──────────────────────────────────────────
        if (m.getSavedFileName() != null) {
            TextView tvDate = new TextView(this);
            tvDate.setText("Saved: " + formatDateFromFileName(m.getSavedFileName()));
            tvDate.setTextSize(11f);
            tvDate.setTextColor(Color.parseColor("#AAAAAA"));
            card.addView(tvDate);
        }

        // ── Action buttons row ────────────────────────────────────────────
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        brp.setMargins(0, 12, 0, 0);
        btnRow.setLayoutParams(brp);

        // View Stats button
        Button btnView = new Button(this);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        vp.setMargins(0, 0, 6, 0);
        btnView.setLayoutParams(vp);
        btnView.setText("View Stats");
        btnView.setTextColor(Color.WHITE);
        btnView.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#085041")));
        btnView.setAllCaps(false);
        btnView.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatsActivity.class);
            intent.putExtra(StatsActivity.EXTRA_SAVED_FILE_NAME, m.getSavedFileName());
            startActivity(intent);
        });
        btnRow.addView(btnView);

        // Delete button
        Button btnDelete = new Button(this);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dp2.setMargins(6, 0, 0, 0);
        btnDelete.setLayoutParams(dp2);
        btnDelete.setText("Delete");
        btnDelete.setTextColor(Color.parseColor("#E24B4A"));
        btnDelete.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#FCEBEB")));
        btnDelete.setAllCaps(false);
        btnDelete.setOnClickListener(v -> confirmDelete(m));
        btnRow.addView(btnDelete);

        card.addView(btnRow);
        return card;
    }

    // ─── Helper views ─────────────────────────────────────────────────────────

    private View makeScoreLine(String team, String score, String overs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 6, 0, 0);
        row.setLayoutParams(rp);

        TextView tvTeam = new TextView(this);
        tvTeam.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvTeam.setText(team);
        tvTeam.setTextSize(13f);
        tvTeam.setTextColor(Color.parseColor("#333333"));
        row.addView(tvTeam);

        TextView tvScore = new TextView(this);
        tvScore.setText(score + "  (" + overs + " ov)");
        tvScore.setTextSize(13f);
        tvScore.setTextColor(Color.parseColor("#111111"));
        tvScore.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tvScore);

        return row;
    }

    private View makeDivider() {
        View div = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dp.setMargins(0, 8, 0, 4);
        div.setLayoutParams(dp);
        div.setBackgroundColor(Color.parseColor("#EEEEEE"));
        return div;
    }

    // ─── Delete confirmation ──────────────────────────────────────────────────

    private void confirmDelete(Match m) {
        new AlertDialog.Builder(this)
                .setTitle("Delete match?")
                .setMessage(m.getHomeTeamName() + " vs " + m.getAwayTeamName()
                        + "\nThis cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (MatchStorage.deleteMatch(this, m.getSavedFileName())) {
                        Toast.makeText(this, "Match deleted", Toast.LENGTH_SHORT).show();
                        loadAndDisplay();
                    } else {
                        Toast.makeText(this, "Could not delete match", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Converts filename "20260423_193045_Mumbai_vs_Delhi.json" → "23 Apr 2026, 19:30" */
    private String formatDateFromFileName(String fileName) {
        try {
            // filename starts with yyyyMMdd_HHmmss
            String ts = fileName.substring(0, 15); // "20260423_193045"
            String yy = ts.substring(0, 4);
            String mm = ts.substring(4, 6);
            String dd = ts.substring(6, 8);
            String hh = ts.substring(9, 11);
            String mn = ts.substring(11, 13);
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            String month = months[Integer.parseInt(mm) - 1];
            return dd + " " + month + " " + yy + ", " + hh + ":" + mn;
        } catch (Exception e) {
            return fileName;
        }
    }

    public static String formatDateFromFileName2(String fileName) {
        try {
            String ts = fileName.substring(0, 15);
            String yy = ts.substring(0, 4);
            String mm = ts.substring(4, 6);
            String dd = ts.substring(6, 8);
            String hh = ts.substring(9, 11);
            String mn = ts.substring(11, 13);
            String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            String month = months[Integer.parseInt(mm) - 1];
            return dd + " " + month + " " + yy + ", " + hh + ":" + mn;
        } catch (Exception e) {
            return fileName;
        }
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }
}
