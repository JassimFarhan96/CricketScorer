package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * TournamentTeam.java
 * Holds a team's name, players, and tournament stats.
 */
public class TournamentTeam implements Serializable {

    private String       name;
    private List<Player> players       = new ArrayList<>();
    private int          played        = 0;
    private int          wins          = 0;
    private int          losses        = 0;
    private int          totalRunsFor  = 0;  // for NRR tiebreaker
    private int          totalRunsAgainst = 0;

    public TournamentTeam() {}
    public TournamentTeam(String name) { this.name = name; }

    public String       getName()                 { return name; }
    public void         setName(String v)         { name = v; }
    public List<Player> getPlayers()              { return players; }
    public void         setPlayers(List<Player> v){ players = v != null ? v : new ArrayList<>(); }
    public int          getPlayed()               { return played; }
    public void         setPlayed(int v)          { played = v; }
    public int          getWins()                 { return wins; }
    public void         setWins(int v)            { wins = v; }
    public int          getLosses()               { return losses; }
    public void         setLosses(int v)          { losses = v; }
    public int          getTotalRunsFor()         { return totalRunsFor; }
    public void         setTotalRunsFor(int v)    { totalRunsFor = v; }
    public int          getTotalRunsAgainst()     { return totalRunsAgainst; }
    public void         setTotalRunsAgainst(int v){ totalRunsAgainst = v; }

    public int  getPoints()        { return wins * 2; }
    public int  getNetRunsScored() { return totalRunsFor - totalRunsAgainst; }

    public void recordWin(int runsFor, int runsAgainst) {
        played++; wins++; totalRunsFor += runsFor; totalRunsAgainst += runsAgainst;
    }
    public void recordLoss(int runsFor, int runsAgainst) {
        played++; losses++; totalRunsFor += runsFor; totalRunsAgainst += runsAgainst;
    }
}
