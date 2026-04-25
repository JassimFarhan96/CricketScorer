package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;

/**
 * HomeActivity.java
 *
 * CHANGE:
 *   "Recent Matches" → RecentMatchesActivity (paginated saved match list)
 *   "Match Statistics" → MatchSelectActivity (pick a match → view full stats)
 */
public class HomeActivity extends AppCompatActivity {

    private LinearLayout layoutTrackMatch;
    private LinearLayout layoutRecentMatches;
    private LinearLayout layoutStatistics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        layoutTrackMatch    = findViewById(R.id.layout_track_match);
        layoutRecentMatches = findViewById(R.id.layout_recent_matches);
        layoutStatistics    = findViewById(R.id.layout_statistics);

        // Track new match → PlayerCountActivity
        layoutTrackMatch.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerCountActivity.class)));

        // Recent matches → paginated list of saved matches
        layoutRecentMatches.setOnClickListener(v ->
                startActivity(new Intent(this, RecentMatchesActivity.class)));

        // Match statistics → pick a saved match, then view full scorecard
        layoutStatistics.setOnClickListener(v ->
                startActivity(new Intent(this, MatchSelectActivity.class)));
    }
}
