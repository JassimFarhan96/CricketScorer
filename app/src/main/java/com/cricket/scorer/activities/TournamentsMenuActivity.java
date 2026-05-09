package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.cricket.scorer.R;

/**
 * TournamentsMenuActivity
 *
 * Submenu reached from Home → Tournaments.
 * Shows two options:
 *   - Track a tournament  → TournamentSetupActivity
 *   - Recent tournaments  → RecentTournamentsActivity
 */
public class TournamentsMenuActivity extends BaseNavActivity {

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournaments_menu);

        LinearLayout trackTournament    = findViewById(R.id.layout_track_tournament);
        LinearLayout recentTournaments  = findViewById(R.id.layout_recent_tournaments);

        trackTournament.setOnClickListener(v ->
                startActivity(new Intent(this, TournamentSetupActivity.class)));
        recentTournaments.setOnClickListener(v ->
                startActivity(new Intent(this, RecentTournamentsActivity.class)));
    }
}
