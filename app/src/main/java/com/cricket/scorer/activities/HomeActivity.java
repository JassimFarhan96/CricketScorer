package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.utils.LiveMatchState;

/**
 * HomeActivity.java
 *
 * CHANGE: On every onCreate / onResume, checks live_match.json.
 * If it contains a match in progress the user is asked:
 *   "Resume match?" → [Resume] restores the state and navigates to
 *                     InningsActivity or InningsBreakActivity
 *                     [Discard] clears the file and stays on home
 *
 * The check is done in onResume (not only onCreate) so it also fires
 * when the user presses the system back button from a mid-match screen
 * back to Home — giving a second chance to resume.
 */
public class HomeActivity extends AppCompatActivity {

    private LinearLayout layoutTrackMatch;
    private LinearLayout layoutRecentMatches;
    private LinearLayout layoutStatistics;

    // Prevents showing the resume dialog twice on first launch
    private boolean resumeDialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        layoutTrackMatch    = findViewById(R.id.layout_track_match);
        layoutRecentMatches = findViewById(R.id.layout_recent_matches);
        layoutStatistics    = findViewById(R.id.layout_statistics);

        setClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check for an in-progress match every time this screen is visible
        if (!resumeDialogShown) {
            checkForResumeableMatch();
        }
        resumeDialogShown = false;
    }

    // ─── Resume check ─────────────────────────────────────────────────────────

    /**
     * If live_match.json is non-empty, shows a dialog asking the user
     * whether to resume or discard the interrupted match.
     */
    private void checkForResumeableMatch() {
        if (!LiveMatchState.hasSavedState(this)) return;

        Match saved = LiveMatchState.restore(this);
        if (saved == null) {
            LiveMatchState.clear(this);
            return;
        }

        String teams = saved.getHomeTeamName() + " vs " + saved.getAwayTeamName();
        String inn   = saved.getCurrentInnings() == 1 ? "1st innings" : "2nd innings";
        String score = saved.getCurrentInningsData() != null
                ? saved.getCurrentInningsData().getScoreString() : "0/0";
        String overs = saved.getCurrentInningsData() != null
                ? saved.getCurrentInningsData().getOversString() : "0.0";

        String message = teams + "\n" + inn + " — " + score
                + " (" + overs + " ov)\n\nWould you like to resume this match?";

        resumeDialogShown = true;

        new AlertDialog.Builder(this)
                .setTitle("Match in progress")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Resume", (dialog, which) -> resumeMatch(saved))
                .setNegativeButton("Discard", (dialog, which) -> {
                    LiveMatchState.clear(this);
                    resumeDialogShown = false;
                })
                .show();
    }

    /**
     * Loads the restored Match into CricketApp and navigates to the
     * correct screen based on which innings is active.
     *
     * If currentInnings == 2 and firstInnings.isComplete() == true but
     * secondInnings has just been created (0 balls bowled), that means
     * the app was closed exactly at the innings break — go to break screen.
     * Otherwise go straight to InningsActivity.
     */
    private void resumeMatch(Match match) {
        CricketApp app = (CricketApp) getApplication();
        app.startNewMatch(match);

        // Decide which screen to restore
        boolean atBreak = match.getCurrentInnings() == 2
                && match.getSecondInnings() != null
                && match.getSecondInnings().getTotalValidBalls() == 0
                && match.getFirstInnings() != null
                && match.getFirstInnings().isComplete();

        Intent intent = atBreak
                ? new Intent(this, InningsBreakActivity.class)
                : new Intent(this, InningsActivity.class);

        startActivity(intent);
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {
        layoutTrackMatch.setOnClickListener(v ->
                startActivity(new Intent(this, PlayerCountActivity.class)));

        layoutRecentMatches.setOnClickListener(v ->
                startActivity(new Intent(this, RecentMatchesActivity.class)));

        layoutStatistics.setOnClickListener(v ->
                startActivity(new Intent(this, MatchSelectActivity.class)));
    }
}
