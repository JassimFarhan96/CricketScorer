package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.cricket.scorer.R;

/**
 * RecentMenuActivity
 *
 * Landing screen when the bottom-nav "Recent" tab is tapped.
 * Lets the user choose between viewing recent matches and
 * recent tournaments. The dedicated submenu prevents the old
 * behaviour where tapping Recent always opened matches, hiding
 * the tournaments archive behind a less-obvious path.
 */
public class RecentMenuActivity extends BaseNavActivity {

    @Override protected int getCurrentNavItem() { return R.id.nav_recent; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_recent_menu);

        LinearLayout recentMatches     = findViewById(R.id.layout_recent_matches);
        LinearLayout recentTournaments = findViewById(R.id.layout_recent_tournaments);

        recentMatches.setOnClickListener(v ->
                startActivity(new Intent(this, RecentMatchesActivity.class)));
        recentTournaments.setOnClickListener(v ->
                startActivity(new Intent(this, RecentTournamentsActivity.class)));
    }
}
