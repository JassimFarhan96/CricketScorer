package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;

/**
 * HomeActivity.java
 * The launcher / home screen of the Cricket Scorer app.
 *
 * CHANGE: "Track new match" now navigates to PlayerCountActivity first,
 * which collects the number of players before showing SetupActivity.
 *
 * Flow:
 *   HomeActivity
 *     → PlayerCountActivity  (choose number of players)
 *       → SetupActivity      (team names, overs, player names)
 *         → InningsActivity  (live tracking)
 *
 * Layout: activity_home.xml
 */
public class HomeActivity extends AppCompatActivity {

    private LinearLayout layoutTrackMatch;
    private LinearLayout layoutRecentMatches;
    private LinearLayout layoutStatistics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        bindViews();
        setClickListeners();
    }

    private void bindViews() {
        layoutTrackMatch    = findViewById(R.id.layout_track_match);
        layoutRecentMatches = findViewById(R.id.layout_recent_matches);
        layoutStatistics    = findViewById(R.id.layout_statistics);
    }

    private void setClickListeners() {

        // ── Track new match → PlayerCountActivity first ────────────────
        layoutTrackMatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomeActivity.this, PlayerCountActivity.class);
                startActivity(intent);
            }
        });

        // ── Recent matches (stub) ──────────────────────────────────────
        layoutRecentMatches.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(HomeActivity.this,
                        "No recent matches saved.", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Statistics ─────────────────────────────────────────────────
        layoutStatistics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CricketApp app = (CricketApp) getApplication();
                if (app.getCurrentMatch() == null || !app.getCurrentMatch().isMatchCompleted()) {
                    Toast.makeText(HomeActivity.this,
                            "No completed match to show.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(HomeActivity.this, StatsActivity.class);
                startActivity(intent);
            }
        });
    }
}
