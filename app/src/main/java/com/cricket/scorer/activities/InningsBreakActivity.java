package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;

import java.util.Locale;

/**
 * InningsBreakActivity.java
 * Displayed between the 1st and 2nd innings.
 *
 * Shows:
 *  - 1st innings team name and score
 *  - Target the chasing team needs
 *  - Required run rate
 *  - "Start 2nd Innings" button
 *
 * Layout: activity_innings_break.xml
 */
public class InningsBreakActivity extends AppCompatActivity {

    private TextView tvBattingTeam;
    private TextView tvFirstInningsScore;
    private TextView tvOversPlayed;
    private TextView tvChasingTeam;
    private TextView tvTarget;
    private TextView tvRequiredRR;
    private Button btnStart2ndInnings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_innings_break);

        bindViews();
        populateData();
        setClickListeners();
    }

    private void bindViews() {
        tvBattingTeam        = findViewById(R.id.tv_batting_team);
        tvFirstInningsScore  = findViewById(R.id.tv_first_innings_score);
        tvOversPlayed        = findViewById(R.id.tv_overs_played);
        tvChasingTeam        = findViewById(R.id.tv_chasing_team);
        tvTarget             = findViewById(R.id.tv_target);
        tvRequiredRR         = findViewById(R.id.tv_required_rr);
        btnStart2ndInnings   = findViewById(R.id.btn_start_2nd_innings);
    }

    private void populateData() {
        CricketApp app = (CricketApp) getApplication();
        Match match = app.getCurrentMatch();
        if (match == null) { finish(); return; }

        Innings i1 = match.getFirstInnings();

        // Batting first team info
        String battingFirstTeam = match.getBattingFirstTeam().equals("home")
                ? match.getHomeTeamName() : match.getAwayTeamName();
        String chasingTeam = match.getBattingFirstTeam().equals("home")
                ? match.getAwayTeamName() : match.getHomeTeamName();

        tvBattingTeam.setText(battingFirstTeam);
        tvFirstInningsScore.setText(i1.getScoreString());
        tvOversPlayed.setText("in " + i1.getOversString() + " overs");
        tvChasingTeam.setText(chasingTeam + " need:");
        tvTarget.setText(match.getTarget() + " runs");

        // Required run rate = target / max overs
        float rrr = (float) match.getTarget() / match.getMaxOvers();
        tvRequiredRR.setText(String.format(Locale.US, "Required run rate: %.2f", rrr));
    }

    private void setClickListeners() {
        btnStart2ndInnings.setOnClickListener(v -> {
            // MatchEngine has already set up the 2nd innings in INNINGS_COMPLETE state
            Intent intent = new Intent(InningsBreakActivity.this, InningsActivity.class);
            startActivity(intent);
            finish();
        });
    }

    /** Prevent accidental back navigation to the completed innings */
    @Override
    public void onBackPressed() {
        // Do nothing — user must tap "Start 2nd Innings"
    }
}
