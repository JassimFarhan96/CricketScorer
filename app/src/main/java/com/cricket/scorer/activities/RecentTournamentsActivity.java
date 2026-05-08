package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cricket.scorer.R;
import com.cricket.scorer.utils.TournamentStorage;
import com.cricket.scorer.utils.TournamentStorage.ArchivedTournament;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecentTournamentsActivity
 *
 * Lists all saved tournaments, paginated 5 per page.
 * Each row shows: champion, # teams, when saved.
 * Tapping "View" opens TournamentDetailsActivity for that tournament.
 */
public class RecentTournamentsActivity extends BaseNavActivity {

    private static final int PAGE_SIZE = 5;

    private LinearLayout listContainer;
    private TextView     tvEmpty, tvPageIndicator;
    private Button       btnPrev, btnNext;
    private List<ArchivedTournament> archived;
    private int          currentPage = 0;

    @Override protected int getCurrentNavItem() { return R.id.nav_recent; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_recent_tournaments);
        listContainer    = findViewById(R.id.list_container);
        tvEmpty          = findViewById(R.id.tv_empty);
        tvPageIndicator  = findViewById(R.id.tv_page_indicator);
        btnPrev          = findViewById(R.id.btn_prev);
        btnNext          = findViewById(R.id.btn_next);

        archived = TournamentStorage.loadArchived(this);
        renderPage();

        btnPrev.setOnClickListener(v -> { if (currentPage > 0) { currentPage--; renderPage(); }});
        btnNext.setOnClickListener(v -> {
            int maxPage = (archived.size() - 1) / PAGE_SIZE;
            if (currentPage < maxPage) { currentPage++; renderPage(); }
        });
    }

    private void renderPage() {
        listContainer.removeAllViews();
        if (archived.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            btnPrev.setEnabled(false); btnNext.setEnabled(false);
            tvPageIndicator.setText("");
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        int start = currentPage * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, archived.size());
        for (int i = start; i < end; i++) {
            listContainer.addView(buildRow(archived.get(i)));
        }
        int totalPages = (archived.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        tvPageIndicator.setText("Page " + (currentPage + 1) + " / " + totalPages);
        btnPrev.setEnabled(currentPage > 0);
        btnNext.setEnabled(currentPage < totalPages - 1);
    }

    private View buildRow(ArchivedTournament a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundColor(getResources().getColor(R.color.c_bg_card, getTheme()));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(dp(12), dp(4), dp(12), dp(4));
        row.setLayoutParams(rlp);

        // Left column: champion + meta
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🏆 " + (a.tournament.getChampionName() != null
                ? a.tournament.getChampionName() : "—"));
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
        col.addView(tvTitle);

        TextView tvMeta = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault());
        tvMeta.setText(a.tournament.getTeams().size() + " teams · "
                + sdf.format(new Date(a.savedAtMillis)));
        tvMeta.setTextSize(12f);
        tvMeta.setTextColor(getResources().getColor(R.color.c_text_secondary, getTheme()));
        col.addView(tvMeta);

        row.addView(col);

        // View button
        Button btnView = new Button(this);
        btnView.setText("View");
        btnView.setLayoutParams(new LinearLayout.LayoutParams(dp(80), dp(40)));
        btnView.setOnClickListener(v -> {
            Intent i = new Intent(this, TournamentDetailsActivity.class);
            i.putExtra(TournamentDetailsActivity.EXTRA_FILE_NAME, a.fileName);
            startActivity(i);
        });
        row.addView(btnView);

        return row;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
