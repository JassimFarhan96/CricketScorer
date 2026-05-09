package com.cricket.scorer.activities;
import android.content.Intent; import android.os.Bundle; import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.cricket.scorer.R; import com.cricket.scorer.models.Match; import com.cricket.scorer.utils.LiveMatchState;

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
    private LinearLayout layoutMatches, layoutTournaments;
    private boolean resumeDialogShown = false;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s); setNavContentView(R.layout.activity_home);
        layoutMatches     = findViewById(R.id.layout_matches);
        layoutTournaments = findViewById(R.id.layout_tournaments);
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
        new AlertDialog.Builder(this).setTitle("Tournament in progress")
            .setMessage(teamCount + " · Stage: " + stage
                    + "\n\nWould you like to resume this tournament?")
            .setCancelable(false)
            .setPositiveButton("Resume", (d, w) -> {
                ((CricketApp)getApplication()).startNewTournament(t);
                startActivity(new Intent(this, TournamentDashboardActivity.class));
            })
            .setNegativeButton("Discard", (d, w) -> {
                com.cricket.scorer.utils.TournamentStorage.clear(this);
                resumeDialogShown = false;
            })
            .show();
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
    }

    @Override
    protected int getCurrentNavItem() { return R.id.nav_home; }
}
