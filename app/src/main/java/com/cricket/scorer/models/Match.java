package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Match.java
 * Core model representing a full cricket match.
 *
 * CHANGE: Added singleBatsmanMode (boolean).
 *   true  → only the striker bats; no non-striker exists.
 *           Strike never rotates. Only one batsman shown in the batting table.
 *   false → standard: striker + non-striker. Strike rotates on odd runs
 *           and at the end of each over.
 */
public class Match implements Serializable {

    // ─── Match metadata ───────────────────────────────────────────────────────
    private String homeTeamName;
    private String awayTeamName;
    private int    maxOvers;
    private String battingFirstTeam;    // "home" or "away"
    private int    currentInnings;      // 1 or 2

    /**
     * SINGLE BATSMAN MODE flag.
     * true  → only one batsman at the crease (striker only).
     * false → two batsmen (striker + non-striker), standard cricket.
     * Set once in SetupActivity and never changed during the match.
     */
    private boolean singleBatsmanMode;

    // ─── Players ──────────────────────────────────────────────────────────────
    private List<Player> homePlayers;
    private List<Player> awayPlayers;

    // ─── Innings data ─────────────────────────────────────────────────────────
    private Innings firstInnings;
    private Innings secondInnings;

    // ─── Match result ─────────────────────────────────────────────────────────
    private boolean matchCompleted;
    private String  winnerTeam;
    private String  resultDescription;

    public Match() {
        homePlayers       = new ArrayList<>();
        awayPlayers       = new ArrayList<>();
        currentInnings    = 1;
        matchCompleted    = false;
        singleBatsmanMode = false; // default: standard two-batsman mode
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getHomeTeamName()  { return homeTeamName; }
    public void setHomeTeamName(String v) { homeTeamName = v; }

    public String getAwayTeamName()  { return awayTeamName; }
    public void setAwayTeamName(String v) { awayTeamName = v; }

    public int getMaxOvers()         { return maxOvers; }
    public void setMaxOvers(int v)   { maxOvers = v; }

    public String getBattingFirstTeam()       { return battingFirstTeam; }
    public void setBattingFirstTeam(String v) { battingFirstTeam = v; }

    public int getCurrentInnings()       { return currentInnings; }
    public void setCurrentInnings(int v) { currentInnings = v; }

    public boolean isSingleBatsmanMode()         { return singleBatsmanMode; }
    public void setSingleBatsmanMode(boolean v)  { singleBatsmanMode = v; }

    public List<Player> getHomePlayers()         { return homePlayers; }
    public void setHomePlayers(List<Player> v)   { homePlayers = v; }

    public List<Player> getAwayPlayers()         { return awayPlayers; }
    public void setAwayPlayers(List<Player> v)   { awayPlayers = v; }

    public Innings getFirstInnings()             { return firstInnings; }
    public void setFirstInnings(Innings v)       { firstInnings = v; }

    public Innings getSecondInnings()            { return secondInnings; }
    public void setSecondInnings(Innings v)      { secondInnings = v; }

    public boolean isMatchCompleted()            { return matchCompleted; }
    public void setMatchCompleted(boolean v)     { matchCompleted = v; }

    public String getWinnerTeam()                { return winnerTeam; }
    public void setWinnerTeam(String v)          { winnerTeam = v; }

    public String getResultDescription()         { return resultDescription; }
    public void setResultDescription(String v)   { resultDescription = v; }

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
}
