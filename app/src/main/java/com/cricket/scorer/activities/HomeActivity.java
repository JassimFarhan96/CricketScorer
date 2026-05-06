package com.cricket.scorer.activities;
import android.content.Intent; import android.os.Bundle; import android.view.View; import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity;
import com.cricket.scorer.R; import com.cricket.scorer.models.Match; import com.cricket.scorer.utils.LiveMatchState;

public class HomeActivity extends BaseNavActivity {
    private LinearLayout layoutTrackMatch,layoutTrackTournament,layoutRecentMatches,layoutStatistics;
    private boolean resumeDialogShown=false;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s); setNavContentView(R.layout.activity_home);
        layoutTrackMatch=findViewById(R.id.layout_track_match);
        layoutTrackTournament=findViewById(R.id.layout_track_tournament);
        layoutRecentMatches=findViewById(R.id.layout_recent_matches);
        layoutStatistics=findViewById(R.id.layout_statistics);
        setClickListeners();
    }

    @Override protected void onResume(){
        super.onResume();
        if(!resumeDialogShown) {
            // Check tournament first — if active, take precedence
            if (com.cricket.scorer.utils.TournamentStorage.exists(this)) {
                checkForResumeableTournament();
            } else {
                checkForResumeableMatch();
            }
        }
        resumeDialogShown=false;
    }

    private void checkForResumeableTournament(){
        com.cricket.scorer.models.Tournament t =
                com.cricket.scorer.utils.TournamentStorage.load(this);
        if (t == null) {
            com.cricket.scorer.utils.TournamentStorage.clear(this);
            return;
        }
        resumeDialogShown=true;
        String teamCount = t.getTeams().size() + " teams";
        String stage     = t.getStage().name();
        new AlertDialog.Builder(this).setTitle("Tournament in progress")
            .setMessage(teamCount + " · Stage: " + stage
                    + "\n\nWould you like to resume this tournament?")
            .setCancelable(false)
            .setPositiveButton("Resume",(d,w)->{
                ((CricketApp)getApplication()).startNewTournament(t);
                startActivity(new Intent(this,
                        com.cricket.scorer.activities.TournamentDashboardActivity.class));
            })
            .setNegativeButton("Discard",(d,w)->{
                com.cricket.scorer.utils.TournamentStorage.clear(this);
                resumeDialogShown=false;
            })
            .show();
    }

    private void checkForResumeableMatch(){
        if(!LiveMatchState.hasSavedState(this)) return;
        Match saved=LiveMatchState.restore(this);
        if(saved==null){LiveMatchState.clear(this);return;}
        String teams=saved.getHomeTeamName()+" vs "+saved.getAwayTeamName();
        String inn=saved.getCurrentInnings()==1?"1st innings":"2nd innings";
        String score=saved.getCurrentInningsData()!=null?saved.getCurrentInningsData().getScoreString():"0/0";
        String overs=saved.getCurrentInningsData()!=null?saved.getCurrentInningsData().getOversString():"0.0";
        resumeDialogShown=true;
        new AlertDialog.Builder(this).setTitle("Match in progress")
            .setMessage(teams+"\n"+inn+" — "+score+" ("+overs+" ov)\n\nWould you like to resume this match?")
            .setCancelable(false)
            .setPositiveButton("Resume",(d,w)->resumeMatch(saved))
            .setNegativeButton("Discard",(d,w)->{LiveMatchState.clear(this);resumeDialogShown=false;})
            .show();
    }

    private void resumeMatch(Match match){
        ((CricketApp)getApplication()).startNewMatch(match);
        boolean atBreak=match.getCurrentInnings()==2&&match.getSecondInnings()!=null
            &&match.getSecondInnings().getTotalValidBalls()==0
            &&match.getFirstInnings()!=null&&match.getFirstInnings().isComplete();
        startActivity(new Intent(this,atBreak?InningsBreakActivity.class:InningsActivity.class));
    }

    private void setClickListeners(){
        layoutTrackMatch.setOnClickListener(v->startActivity(new Intent(this,PlayerCountActivity.class)));
        layoutTrackTournament.setOnClickListener(v->startActivity(new Intent(this,
                com.cricket.scorer.activities.TournamentSetupActivity.class)));
        layoutRecentMatches.setOnClickListener(v->startActivity(new Intent(this,RecentMatchesActivity.class)));
        layoutStatistics.setOnClickListener(v->startActivity(new Intent(this,MatchSelectActivity.class)));
    }
    @Override
    protected int getCurrentNavItem() {
        return R.id.nav_home;
    }

}
