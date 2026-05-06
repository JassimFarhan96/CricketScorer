package com.cricket.scorer.models;

import java.io.Serializable;

/**
 * TournamentMatch.java
 * One fixture between two teams. Tracks completion + result.
 */
public class TournamentMatch implements Serializable {

    private String  teamAName;
    private String  teamBName;
    private boolean completed       = false;
    private String  winnerName;     // name of winning team, or "tie"
    private int     teamAScore      = 0;
    private int     teamBScore      = 0;
    private String  resultDescription;
    private String  savedMatchFile; // filename in recent_matches/ for replay

    public TournamentMatch() {}
    public TournamentMatch(String a, String b) { teamAName = a; teamBName = b; }

    public String  getTeamAName()             { return teamAName; }
    public void    setTeamAName(String v)     { teamAName = v; }
    public String  getTeamBName()             { return teamBName; }
    public void    setTeamBName(String v)     { teamBName = v; }
    public boolean isCompleted()              { return completed; }
    public void    setCompleted(boolean v)    { completed = v; }
    public String  getWinnerName()            { return winnerName; }
    public void    setWinnerName(String v)    { winnerName = v; }
    public int     getTeamAScore()            { return teamAScore; }
    public void    setTeamAScore(int v)       { teamAScore = v; }
    public int     getTeamBScore()            { return teamBScore; }
    public void    setTeamBScore(int v)       { teamBScore = v; }
    public String  getResultDescription()     { return resultDescription; }
    public void    setResultDescription(String v){ resultDescription = v; }
    public String  getSavedMatchFile()        { return savedMatchFile; }
    public void    setSavedMatchFile(String v){ savedMatchFile = v; }

    public String getLabel() { return teamAName + " vs " + teamBName; }
}
