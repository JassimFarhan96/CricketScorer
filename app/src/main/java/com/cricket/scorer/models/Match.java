package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Match.java
 * CHANGE: Added savedFileName (transient — not persisted inside the JSON,
 * just tagged by MatchStorage after loading so Activities can reference
 * the file by name for deletion or selection).
 */
public class Match implements Serializable {

    private String  homeTeamName;
    private String  awayTeamName;
    private int     maxOvers;
    private String  battingFirstTeam;
    private int     currentInnings;
    private boolean singleBatsmanMode;

    private List<Player> homePlayers;
    private List<Player> awayPlayers;

    private Innings firstInnings;
    private Innings secondInnings;

    private boolean matchCompleted;
    private String  winnerTeam;
    private String  resultDescription;

    /** Set by MatchStorage after loading — the .json filename on disk. */
    private String savedFileName;

    public Match() {
        homePlayers       = new ArrayList<>();
        awayPlayers       = new ArrayList<>();
        currentInnings    = 1;
        matchCompleted    = false;
        singleBatsmanMode = false;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getHomeTeamName()              { return homeTeamName; }
    public void   setHomeTeamName(String v)      { homeTeamName = v; }

    public String getAwayTeamName()              { return awayTeamName; }
    public void   setAwayTeamName(String v)      { awayTeamName = v; }

    public int    getMaxOvers()                  { return maxOvers; }
    public void   setMaxOvers(int v)             { maxOvers = v; }

    public String getBattingFirstTeam()          { return battingFirstTeam; }
    public void   setBattingFirstTeam(String v)  { battingFirstTeam = v; }

    public int    getCurrentInnings()            { return currentInnings; }
    public void   setCurrentInnings(int v)       { currentInnings = v; }

    public boolean isSingleBatsmanMode()         { return singleBatsmanMode; }
    public void    setSingleBatsmanMode(boolean v){ singleBatsmanMode = v; }

    public List<Player> getHomePlayers()         { return homePlayers; }
    public void         setHomePlayers(List<Player> v) { homePlayers = v; }

    public List<Player> getAwayPlayers()         { return awayPlayers; }
    public void         setAwayPlayers(List<Player> v) { awayPlayers = v; }

    public Innings getFirstInnings()             { return firstInnings; }
    public void    setFirstInnings(Innings v)    { firstInnings = v; }

    public Innings getSecondInnings()            { return secondInnings; }
    public void    setSecondInnings(Innings v)   { secondInnings = v; }

    public boolean isMatchCompleted()            { return matchCompleted; }
    public void    setMatchCompleted(boolean v)  { matchCompleted = v; }

    public String getWinnerTeam()                { return winnerTeam; }
    public void   setWinnerTeam(String v)        { winnerTeam = v; }

    public String getResultDescription()         { return resultDescription; }
    public void   setResultDescription(String v) { resultDescription = v; }

    public String getSavedFileName()             { return savedFileName; }
    public void   setSavedFileName(String v)     { savedFileName = v; }

    // ─── Convenience helpers ──────────────────────────────────────────────────

    public String getCurrentBattingTeamName() {
        if (currentInnings == 1)
            return battingFirstTeam.equals("home") ? homeTeamName : awayTeamName;
        return battingFirstTeam.equals("home") ? awayTeamName : homeTeamName;
    }

    public String getCurrentBowlingTeamName() {
        if (currentInnings == 1)
            return battingFirstTeam.equals("home") ? awayTeamName : homeTeamName;
        return battingFirstTeam.equals("home") ? homeTeamName : awayTeamName;
    }

    public Innings getCurrentInningsData() {
        return currentInnings == 1 ? firstInnings : secondInnings;
    }

    public List<Player> getCurrentBattingPlayers() {
        if (currentInnings == 1)
            return battingFirstTeam.equals("home") ? homePlayers : awayPlayers;
        return battingFirstTeam.equals("home") ? awayPlayers : homePlayers;
    }

    public int getTarget() {
        return firstInnings == null ? 0 : firstInnings.getTotalRuns() + 1;
    }

    /** Short summary string for list items: "Mumbai vs Delhi — 23 Apr 2026" */
    public String getSummaryTitle() {
        return homeTeamName + " vs " + awayTeamName;
    }
}
