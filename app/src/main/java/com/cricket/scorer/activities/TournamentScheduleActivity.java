package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.models.TournamentMatch;
import com.cricket.scorer.utils.FixtureGenerator;
import com.cricket.scorer.utils.TournamentStorage;

import java.util.List;

/**
 * TournamentScheduleActivity
 *
 * Step 4: shows the generated round-robin schedule. User can reorder fixtures
 * via up/down arrows. Confirms with "Start Tournament" → goes to dashboard.
 */
public class TournamentScheduleActivity extends BaseNavActivity {

    private LinearLayout fixtureContainer;
    private Button       btnStart;

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournament_schedule);
        fixtureContainer = findViewById(R.id.fixture_container);
        btnStart         = findViewById(R.id.btn_start);

        Tournament t = ((CricketApp) getApplication()).getCurrentTournament();
        if (t == null) { finish(); return; }

        if (t.getLeagueFixtures().isEmpty()) {
            t.setLeagueFixtures(FixtureGenerator.buildRoundRobin(t.getTeams()));
        }
        renderFixtures(t);

        btnStart.setOnClickListener(v -> {
            TournamentStorage.save(this, t);
            Intent i = new Intent(this, TournamentDashboardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }

    private void renderFixtures(Tournament t) {
        fixtureContainer.removeAllViews();
        List<TournamentMatch> fixtures = t.getLeagueFixtures();
        for (int i = 0; i < fixtures.size(); i++) {
            TournamentMatch m = fixtures.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setBackgroundColor(getResources().getColor(
                    i % 2 == 0 ? R.color.c_row_alt_bg : R.color.c_bg_card, getTheme()));

            TextView tvNum = new TextView(this);
            tvNum.setText((i + 1) + ".");
            tvNum.setTextColor(getResources().getColor(R.color.c_text_secondary, getTheme()));
            tvNum.setLayoutParams(new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(tvNum);

            TextView tvLabel = new TextView(this);
            tvLabel.setText(m.getLabel());
            tvLabel.setTextSize(15f);
            tvLabel.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvLabel.setLayoutParams(lp);
            row.addView(tvLabel);

            // Up button
            Button btnUp = new Button(this);
            btnUp.setText("▲");
            btnUp.setEnabled(i > 0);
            btnUp.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(36)));
            final int idx = i;
            btnUp.setOnClickListener(v -> {
                java.util.Collections.swap(fixtures, idx, idx - 1);
                renderFixtures(t);
            });
            row.addView(btnUp);

            // Down button
            Button btnDown = new Button(this);
            btnDown.setText("▼");
            btnDown.setEnabled(i < fixtures.size() - 1);
            btnDown.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(36)));
            btnDown.setOnClickListener(v -> {
                java.util.Collections.swap(fixtures, idx, idx + 1);
                renderFixtures(t);
            });
            row.addView(btnDown);

            fixtureContainer.addView(row);
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
