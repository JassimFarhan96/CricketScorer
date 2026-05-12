package com.cricket.scorer.activities;
import android.content.Intent; import android.os.Bundle; import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.cricket.scorer.R; import com.cricket.scorer.models.Match; import com.cricket.scorer.models.Tournament; import com.cricket.scorer.utils.LiveMatchState;

/**
 * HomeActivity
 *
 * Top-level menu with just two options:
 *   - Matches      → MatchesMenuActivity (track new, recent, stats)
 *   - Tournaments  → TournamentsMenuActivity (track tournament, recent)
 *
 * Resume dialogs (active match / active tournament) still trigger here on
 * launch so users land back in the right place.
 */
public class HomeActivity extends BaseNavActivity {
    private LinearLayout layoutMatches, layoutTournaments, layoutExport, layoutReportBug;
    private boolean resumeDialogShown = false;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s); setNavContentView(R.layout.activity_home);
        layoutMatches     = findViewById(R.id.layout_matches);
        layoutTournaments = findViewById(R.id.layout_tournaments);
        layoutExport      = findViewById(R.id.layout_export);
        layoutReportBug   = findViewById(R.id.layout_report_bug);
        setClickListeners();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!resumeDialogShown) {
            // Check tournament first — if active, take precedence
            if (com.cricket.scorer.utils.TournamentStorage.exists(this)) {
                checkForResumeableTournament();
            } else {
                checkForResumeableMatch();
            }
        }
        resumeDialogShown = false;
    }

    private void checkForResumeableTournament() {
        com.cricket.scorer.models.Tournament t =
                com.cricket.scorer.utils.TournamentStorage.load(this);
        if (t == null) {
            com.cricket.scorer.utils.TournamentStorage.clear(this);
            return;
        }
        resumeDialogShown = true;
        String teamCount = t.getTeams().size() + " teams";
        String stage     = t.getStage().name();

        // If a tournament match is mid-tracking, the live match state file
        // also exists. Mention this so the user knows resuming will pick up
        // exactly where they left off (ball-by-ball), not restart the match.
        boolean midMatch = LiveMatchState.hasSavedState(this);
        String message   = teamCount + " · Stage: " + stage;
        if (midMatch) {
            Match liveMatch = LiveMatchState.restore(this);
            if (liveMatch != null) {
                String inn   = liveMatch.getCurrentInnings() == 1 ? "1st innings" : "2nd innings";
                String score = liveMatch.getCurrentInningsData() != null
                        ? liveMatch.getCurrentInningsData().getScoreString() : "0/0";
                String overs = liveMatch.getCurrentInningsData() != null
                        ? liveMatch.getCurrentInningsData().getOversString() : "0.0";
                message += "\n\n" + liveMatch.getHomeTeamName() + " vs "
                        + liveMatch.getAwayTeamName() + " — in progress\n"
                        + inn + " · " + score + " (" + overs + " ov)";
            }
        }
        message += "\n\nWould you like to resume?";

        final Tournament fT = t;
        new AlertDialog.Builder(this).setTitle("Tournament in progress")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Resume", (d, w) -> resumeTournament(fT))
            .setNegativeButton("Discard", (d, w) -> {
                com.cricket.scorer.utils.TournamentStorage.clear(this);
                LiveMatchState.clear(this);
                resumeDialogShown = false;
            })
            .show();
    }

    /**
     * Resumes an active tournament. If a tournament match is mid-tracking
     * (LiveMatchState present), restores that match and lands directly in
     * InningsActivity at the exact ball where the user left off. Otherwise
     * goes to TournamentDashboardActivity.
     */
    private void resumeTournament(com.cricket.scorer.models.Tournament t) {
        ((CricketApp) getApplication()).startNewTournament(t);
        if (LiveMatchState.hasSavedState(this)) {
            Match saved = LiveMatchState.restore(this);
            if (saved != null) {
                resumeMatch(saved); // goes to InningsActivity / InningsBreakActivity
                return;
            }
        }
        // No mid-match state — go to dashboard so user can pick the next fixture
        startActivity(new Intent(this, TournamentDashboardActivity.class));
    }

    private void checkForResumeableMatch() {
        if (!LiveMatchState.hasSavedState(this)) return;
        Match saved = LiveMatchState.restore(this);
        if (saved == null) { LiveMatchState.clear(this); return; }
        String teams = saved.getHomeTeamName() + " vs " + saved.getAwayTeamName();
        String inn   = saved.getCurrentInnings() == 1 ? "1st innings" : "2nd innings";
        String score = saved.getCurrentInningsData() != null ? saved.getCurrentInningsData().getScoreString() : "0/0";
        String overs = saved.getCurrentInningsData() != null ? saved.getCurrentInningsData().getOversString() : "0.0";
        resumeDialogShown = true;
        new AlertDialog.Builder(this).setTitle("Match in progress")
            .setMessage(teams + "\n" + inn + " — " + score + " (" + overs + " ov)\n\nWould you like to resume this match?")
            .setCancelable(false)
            .setPositiveButton("Resume", (d, w) -> resumeMatch(saved))
            .setNegativeButton("Discard", (d, w) -> { LiveMatchState.clear(this); resumeDialogShown = false; })
            .show();
    }

    private void resumeMatch(Match match) {
        ((CricketApp)getApplication()).startNewMatch(match);
        boolean atBreak = match.getCurrentInnings() == 2 && match.getSecondInnings() != null
            && match.getSecondInnings().getTotalValidBalls() == 0
            && match.getFirstInnings() != null && match.getFirstInnings().isComplete();
        startActivity(new Intent(this, atBreak ? InningsBreakActivity.class : InningsActivity.class));
    }

    private void setClickListeners() {
        layoutMatches.setOnClickListener(v ->
                startActivity(new Intent(this, MatchesMenuActivity.class)));
        layoutTournaments.setOnClickListener(v ->
                startActivity(new Intent(this, TournamentsMenuActivity.class)));
        layoutExport.setOnClickListener(v -> exportAllData());
        layoutReportBug.setOnClickListener(v ->
                com.cricket.scorer.utils.BugReportUtils.launch(this));
    }

    /**
     * Bundles every JSON file in the app's private storage into a single
     * ZIP and writes it to the device's public Downloads folder. Useful
     * for testers who need to share their data, and for developer
     * debugging on devices that aren't plugged into Android Studio.
     *
     * The ZIP preserves the on-device folder layout:
     *   live_match.json
     *   tournament_tracker.json
     *   recent_matches/<timestamp>_HomeVsAway.json
     *   recent_tournaments/<timestamp>_Champion.json
     *   recent_tournaments/matches/<timestamp>_HomeVsAway.json
     */
    private void exportAllData() {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setMessage("Exporting data…");
        progress.setCancelable(false);
        progress.show();

        // File sizes are tiny, but use a background thread to be safe
        new Thread(() -> {
            com.cricket.scorer.utils.DataExportUtils.Result result =
                    com.cricket.scorer.utils.DataExportUtils.exportAll(this);
            runOnUiThread(() -> {
                progress.dismiss();
                if (result.success) {
                    new AlertDialog.Builder(this)
                            .setTitle("Export complete")
                            .setMessage("Bundled " + result.fileCount + " file"
                                    + (result.fileCount == 1 ? "" : "s") + " into:\n\n"
                                    + result.displayPath
                                    + "\n\nOpen any file manager → Downloads to find it.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Export failed")
                            .setMessage(result.errorMessage != null
                                    ? result.errorMessage : "Unknown error.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    @Override
    protected int getCurrentNavItem() { return R.id.nav_home; }
}
