package com.cricket.scorer.activities;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.models.TournamentMatch;
import com.cricket.scorer.models.TournamentTeam;
import com.cricket.scorer.utils.TournamentStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TournamentDetailsActivity
 *
 * Loads an archived tournament by file name and shows match-by-match details.
 * One match per page. Page 0 is the standings overview; pages 1..N are
 * each individual match (league fixtures, then semis, then final).
 */
public class TournamentDetailsActivity extends BaseNavActivity {

    public static final String EXTRA_FILE_NAME = "file_name";

    private TextView     tvTitle, tvPageIndicator;
    private LinearLayout pageContent;
    private Button       btnPrev, btnNext;

    private Tournament t;
    private List<TournamentMatch> allMatches;
    private int                   currentPage = 0;

    @Override protected int getCurrentNavItem() { return R.id.nav_recent; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournament_details);
        tvTitle         = findViewById(R.id.tv_title);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);
        pageContent     = findViewById(R.id.page_content);
        btnPrev         = findViewById(R.id.btn_prev);
        btnNext         = findViewById(R.id.btn_next);

        String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (fileName == null) { finish(); return; }
        t = TournamentStorage.loadArchivedByName(this, fileName);
        if (t == null) { finish(); return; }

        // Build flat list of completed matches in chronological order
        allMatches = new ArrayList<>();
        for (TournamentMatch m : t.getLeagueFixtures()) if (m.isCompleted()) allMatches.add(m);
        for (TournamentMatch m : t.getSemiFixtures())   if (m.isCompleted()) allMatches.add(m);
        if (t.getFinalFixture() != null && t.getFinalFixture().isCompleted()) {
            allMatches.add(t.getFinalFixture());
        }

        tvTitle.setText("🏆 Champion: " + (t.getChampionName() != null ? t.getChampionName() : "—"));
        renderPage();

        btnPrev.setOnClickListener(v -> { if (currentPage > 0) { currentPage--; renderPage(); }});
        btnNext.setOnClickListener(v -> { if (currentPage < totalPages() - 1) { currentPage++; renderPage(); }});
    }

    /** Total pages = 1 (standings) + N (one per completed match). */
    private int totalPages() { return 1 + allMatches.size(); }

    private void renderPage() {
        pageContent.removeAllViews();
        if (currentPage == 0) {
            renderStandings();
        } else {
            renderMatch(allMatches.get(currentPage - 1), currentPage);
        }
        tvPageIndicator.setText("Page " + (currentPage + 1) + " / " + totalPages());
        btnPrev.setEnabled(currentPage > 0);
        btnNext.setEnabled(currentPage < totalPages() - 1);
    }

    private void renderStandings() {
        TextView heading = new TextView(this);
        heading.setText("FINAL STANDINGS");
        heading.setTextSize(13f);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(16), dp(12), dp(16), dp(8));
        heading.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        pageContent.addView(heading);

        TableLayout tbl = new TableLayout(this);
        tbl.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tbl.setPadding(dp(8), 0, dp(8), 0);
        addRow(tbl, new String[]{"#", "Team", "P", "W", "L", "Pts", "NRR"}, true);
        List<TournamentTeam> standings = t.getStandings();
        for (int i = 0; i < standings.size(); i++) {
            TournamentTeam team = standings.get(i);
            addRow(tbl, new String[]{
                    String.valueOf(i + 1),
                    team.getName(),
                    String.valueOf(team.getPlayed()),
                    String.valueOf(team.getWins()),
                    String.valueOf(team.getLosses()),
                    String.valueOf(team.getPoints()),
                    String.format(Locale.US, "%.2f", team.getNetRunRate())
            }, false);
        }
        pageContent.addView(tbl);
    }

    private void renderMatch(TournamentMatch m, int matchNumber) {
        TextView heading = new TextView(this);
        heading.setText("MATCH " + matchNumber);
        heading.setTextSize(13f);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(16), dp(12), dp(16), dp(8));
        heading.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        pageContent.addView(heading);

        // Match label
        TextView tvLabel = new TextView(this);
        tvLabel.setText(m.getLabel());
        tvLabel.setTextSize(22f);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setPadding(dp(16), dp(4), dp(16), dp(8));
        tvLabel.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        pageContent.addView(tvLabel);

        // Result banner
        TextView tvResult = new TextView(this);
        String result = m.getResultDescription();
        if (result == null || result.isEmpty()) result = "Winner: " + m.getWinnerName();
        tvResult.setText("🏆 " + result);
        tvResult.setTextSize(16f);
        tvResult.setTypeface(null, Typeface.BOLD);
        tvResult.setPadding(dp(16), dp(8), dp(16), dp(16));
        tvResult.setTextColor(getResources().getColor(R.color.green_mid, getTheme()));
        pageContent.addView(tvResult);

        // "Open full scorecard" button — launches StatsActivity in disk-view mode
        // for this match's saved file. StatsActivity already has the "View
        // In-Depth Statistics" button, so the user can drill down from there.
        if (m.getSavedMatchFile() != null && !m.getSavedMatchFile().isEmpty()) {
            android.widget.Button btnOpen = new android.widget.Button(this);
            btnOpen.setText("📊 Open full match scorecard");
            btnOpen.setTextColor(0xFFFFFFFF);
            btnOpen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.green_dark, getTheme())));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            blp.setMargins(dp(16), dp(16), dp(16), dp(8));
            btnOpen.setLayoutParams(blp);
            btnOpen.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(this, StatsActivity.class);
                i.putExtra(StatsActivity.EXTRA_SAVED_FILE_NAME, m.getSavedMatchFile());
                startActivity(i);
            });
            pageContent.addView(btnOpen);

            TextView hint = new TextView(this);
            hint.setText("Opens the full scorecard with batting / bowling tables. "
                    + "From there you can also view in-depth statistics.");
            hint.setTextSize(12f);
            hint.setPadding(dp(20), dp(4), dp(20), dp(16));
            hint.setTextColor(getResources().getColor(R.color.c_text_secondary, getTheme()));
            pageContent.addView(hint);
        } else {
            // Fall back to brief score lines if no saved file (legacy data)
            TextView tvScoreA = new TextView(this);
            tvScoreA.setText(m.getTeamAName() + ": " + m.getTeamAScore());
            tvScoreA.setTextSize(15f);
            tvScoreA.setPadding(dp(16), dp(4), dp(16), dp(2));
            tvScoreA.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
            pageContent.addView(tvScoreA);

            TextView tvScoreB = new TextView(this);
            tvScoreB.setText(m.getTeamBName() + ": " + m.getTeamBScore());
            tvScoreB.setTextSize(15f);
            tvScoreB.setPadding(dp(16), dp(2), dp(16), dp(8));
            tvScoreB.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
            pageContent.addView(tvScoreB);

            TextView fallbackNote = new TextView(this);
            fallbackNote.setText("(Detailed scorecard not available for this match)");
            fallbackNote.setTextSize(11f);
            fallbackNote.setPadding(dp(20), dp(8), dp(20), dp(16));
            fallbackNote.setTextColor(getResources().getColor(R.color.c_text_hint, getTheme()));
            pageContent.addView(fallbackNote);
        }
    }

    private void addRow(TableLayout tbl, String[] cells, boolean header) {
        TableRow row = new TableRow(this);
        if (header) row.setBackgroundColor(getResources().getColor(R.color.c_row_header_bg, getTheme()));
        int[] widths = {30, 130, 40, 40, 40, 50, 60};
        for (int i = 0; i < cells.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(cells[i]);
            tv.setPadding(dp(8), dp(8), dp(8), dp(8));
            tv.setTextSize(12f);
            tv.setTextColor(getResources().getColor(
                    header ? R.color.c_row_header_text : R.color.c_text_primary, getTheme()));
            if (header) tv.setTypeface(null, Typeface.BOLD);
            tv.setLayoutParams(new TableRow.LayoutParams(dp(widths[i]),
                    TableRow.LayoutParams.WRAP_CONTENT));
            row.addView(tv);
        }
        tbl.addView(row);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
