package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * TournamentTeam.java
 *
 * CHANGE: NRR is now computed using the IPL formula:
 *
 *   NRR = (Total Runs Scored / Total Overs Faced)
 *       - (Total Runs Conceded / Total Overs Bowled)
 *
 * For accuracy we now track:
 *   totalRunsFor       — runs the team scored across all matches
 *   totalRunsAgainst   — runs the team conceded across all matches
 *   totalBallsFaced    — balls the team batted (used as overs decimal)
 *   totalBallsBowled   — balls the team bowled (used as overs decimal)
 *
 * If a team is bowled out for less than the full quota (e.g. all out in 8.2
 * of a 10-over match), the IPL convention is to use the FULL allotted overs
 * for that side's NRR calculation. Caller passes oversAllottedFaced /
 * oversAllottedBowled to recordWin/recordLoss to handle this.
 */
public class TournamentTeam implements Serializable {

    private String       name;
    private List<Player> players          = new ArrayList<>();
    private int          played           = 0;
    private int          wins             = 0;
    private int          losses           = 0;

    // Cumulative scoring used for NRR
    private int          totalRunsFor     = 0;
    private int          totalRunsAgainst = 0;
    private int          totalBallsFaced  = 0;  // balls when batting
    private int          totalBallsBowled = 0;  // balls when bowling

    public TournamentTeam() {}
    public TournamentTeam(String name) { this.name = name; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String       getName()                   { return name; }
    public void         setName(String v)           { name = v; }
    public List<Player> getPlayers()                { return players; }
    public void         setPlayers(List<Player> v)  { players = v != null ? v : new ArrayList<>(); }
    public int          getPlayed()                 { return played; }
    public void         setPlayed(int v)            { played = v; }
    public int          getWins()                   { return wins; }
    public void         setWins(int v)              { wins = v; }
    public int          getLosses()                 { return losses; }
    public void         setLosses(int v)            { losses = v; }
    public int          getTotalRunsFor()           { return totalRunsFor; }
    public void         setTotalRunsFor(int v)      { totalRunsFor = v; }
    public int          getTotalRunsAgainst()       { return totalRunsAgainst; }
    public void         setTotalRunsAgainst(int v)  { totalRunsAgainst = v; }
    public int          getTotalBallsFaced()        { return totalBallsFaced; }
    public void         setTotalBallsFaced(int v)   { totalBallsFaced = v; }
    public int          getTotalBallsBowled()       { return totalBallsBowled; }
    public void         setTotalBallsBowled(int v)  { totalBallsBowled = v; }

    public int    getPoints()             { return wins * 2; }
    /** Legacy run-difference, kept for back-compat in case anything reads it. */
    public int    getNetRunsScored()      { return totalRunsFor - totalRunsAgainst; }

    /**
     * Net Run Rate as per IPL formula:
     *   NRR = (runsFor / oversFaced) - (runsAgainst / oversBowled)
     * Returns 0 if either side has not yet faced/bowled any balls.
     */
    public float getNetRunRate() {
        if (totalBallsFaced <= 0 || totalBallsBowled <= 0) return 0f;
        float oversFaced  = totalBallsFaced  / 6f;
        float oversBowled = totalBallsBowled / 6f;
        return (totalRunsFor / oversFaced) - (totalRunsAgainst / oversBowled);
    }

    // ── Match recording ───────────────────────────────────────────────────────

    /**
     * Records a match result for this team.
     *
     * @param won           true if this team won
     * @param runsFor       runs this team scored
     * @param runsAgainst   runs this team conceded
     * @param ballsFaced    balls this team batted (or full allotted balls
     *                      if all out — see IPL rule comment above)
     * @param ballsBowled   balls this team bowled (or full allotted balls
     *                      if opposition was all out)
     */
    public void recordMatch(boolean won, int runsFor, int runsAgainst,
                             int ballsFaced, int ballsBowled) {
        played++;
        if (won) wins++;
        else     losses++;
        totalRunsFor     += runsFor;
        totalRunsAgainst += runsAgainst;
        totalBallsFaced  += ballsFaced;
        totalBallsBowled += ballsBowled;
    }

    /** Tie / no-result: counts as played but no W/L change. */
    public void recordTie(int runsFor, int runsAgainst, int ballsFaced, int ballsBowled) {
        played++;
        totalRunsFor     += runsFor;
        totalRunsAgainst += runsAgainst;
        totalBallsFaced  += ballsFaced;
        totalBallsBowled += ballsBowled;
    }

    // ── Legacy compatibility (older code paths) ──────────────────────────────

    /** Legacy — does not update overs. Prefer recordMatch(...). */
    @Deprecated
    public void recordWin(int runsFor, int runsAgainst) {
        played++; wins++;
        totalRunsFor += runsFor; totalRunsAgainst += runsAgainst;
    }
    @Deprecated
    public void recordLoss(int runsFor, int runsAgainst) {
        played++; losses++;
        totalRunsFor += runsFor; totalRunsAgainst += runsAgainst;
    }
}
