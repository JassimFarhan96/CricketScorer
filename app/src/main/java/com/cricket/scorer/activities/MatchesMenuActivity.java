package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.cricket.scorer.R;

/**
 * MatchesMenuActivity
 *
 * Submenu reached from Home → Matches.
 * Shows three options:
 *   - Track new match    → PlayerCountActivity
 *   - Recent matches     → RecentMatchesActivity
 *   - Match statistics   → MatchSelectActivity
 */
public class MatchesMenuActivity extends BaseNavActivity {

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_matches_menu);

        LinearLayout trackNew    = findViewById(R.id.layout_track_match);
        LinearLayout recentMatch = findViewById(R.id.layout_recent_matches);
        LinearLayout statistics  = findViewById(R.id.layout_statistics);

        trackNew.setOnClickListener(v -> startActivity(new Intent(this, PlayerCountActivity.class)));
        recentMatch.setOnClickListener(v -> startActivity(new Intent(this, RecentMatchesActivity.class)));
        statistics.setOnClickListener(v -> startActivity(new Intent(this, MatchSelectActivity.class)));
    }
}
