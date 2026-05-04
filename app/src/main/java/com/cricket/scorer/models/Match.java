package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Match.java
 *
 * CHANGE: Added Joker player support.
 *
 * Joker is a special player shared by both teams in the same innings:
 *   - Can bat for the batting team
 *   - Can bowl for the bowling team
 *   - Cannot do both at the same time
 *
 * jokerRole tracks current activity:
 *   NONE    = joker is free (can bat or bowl)
 *   BATTING = joker is currently batting (blocked from bowling)
 *   BOWLING = joker is currently bowling (blocked from batting)
 *
 * Role transitions:
 *   → BATTING : when joker selected as opener or comes in after a wicket
 *   → BOWLING : when joker selected as bowler for an over
 *   BATTING → NONE : when joker is dismissed or retires hurt
 *   BOWLING → NONE : when joker's over completes, baby-over switches bowler,
 *                    or all batsmen are dismissed while joker is bowling
 */
public class Match implements Serializable {

    public enum JokerRole { NONE, BATTING, BOWLING }

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

    // ── Joker ─────────────────────────────────────────────────────────────────
    private boolean   hasJoker  = false;
    private String    jokerName = "";
    private JokerRole jokerRole = JokerRole.NONE;

    public Match() {
        homePlayers       = new ArrayList<>();
        awayPlayers       = new ArrayList<>();
        currentInnings    = 1;
        matchCompleted    = false;
        singleBatsmanMode = false;
    }

    // ── Joker helpers ─────────────────────────────────────────────────────────

    public boolean isJokerBatting() { return hasJoker && jokerRole == JokerRole.BATTING; }
    public boolean isJokerBowling() { return hasJoker && jokerRole == JokerRole.BOWLING; }
    public void    setJokerBatting(){ if (hasJoker) jokerRole = JokerRole.BATTING; }
    public void    setJokerBowling(){ if (hasJoker) jokerRole = JokerRole.BOWLING; }
    public void    clearJokerRole() { jokerRole = JokerRole.NONE; }

    /** Returns true if this player name is the joker. */
    public boolean isJoker(String name) {
        return hasJoker && jokerName != null && jokerName.equals(name);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getHomeTeamName()               { return homeTeamName; }
    public void   setHomeTeamName(String v)       { homeTeamName = v; }
    public String getAwayTeamName()               { return awayTeamName; }
    public void   setAwayTeamName(String v)       { awayTeamName = v; }
    public int    getMaxOvers()                   { return maxOvers; }
    public void   setMaxOvers(int v)              { maxOvers = v; }
    public String getBattingFirstTeam()           { return battingFirstTeam; }
    public void   setBattingFirstTeam(String v)   { battingFirstTeam = v; }
    public int    getCurrentInnings()             { return currentInnings; }
    public void   setCurrentInnings(int v)        { currentInnings = v; }
    public boolean isSingleBatsmanMode()          { return singleBatsmanMode; }
    public void    setSingleBatsmanMode(boolean v){ singleBatsmanMode = v; }
    public List<Player> getHomePlayers()          { return homePlayers; }
    public void setHomePlayers(List<Player> v)    { homePlayers = v; }
    public List<Player> getAwayPlayers()          { return awayPlayers; }
    public void setAwayPlayers(List<Player> v)    { awayPlayers = v; }
    public Innings getFirstInnings()              { return firstInnings; }
    public void    setFirstInnings(Innings v)     { firstInnings = v; }
    public Innings getSecondInnings()             { return secondInnings; }
    public void    setSecondInnings(Innings v)    { secondInnings = v; }
    public boolean isMatchCompleted()             { return matchCompleted; }
    public void    setMatchCompleted(boolean v)   { matchCompleted = v; }
    public String  getWinnerTeam()                { return winnerTeam; }
    public void    setWinnerTeam(String v)        { winnerTeam = v; }
    public String  getResultDescription()         { return resultDescription; }
    public void    setResultDescription(String v) { resultDescription = v; }
    public String  getSavedFileName()             { return savedFileName; }
    public void    setSavedFileName(String v)     { savedFileName = v; }

    public boolean   hasJoker()                   { return hasJoker; }
    public void      setHasJoker(boolean v)       { hasJoker = v; }
    public String    getJokerName()               { return jokerName != null ? jokerName : ""; }
    public void      setJokerName(String v)       { jokerName = v != null ? v : ""; }
    public JokerRole getJokerRole()               { return jokerRole != null ? jokerRole : JokerRole.NONE; }
    public void      setJokerRole(JokerRole v)    { jokerRole = v != null ? v : JokerRole.NONE; }

    // ── Convenience helpers ───────────────────────────────────────────────────

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

    public String getSummaryTitle() {
        return homeTeamName + " vs " + awayTeamName;
    }
}
