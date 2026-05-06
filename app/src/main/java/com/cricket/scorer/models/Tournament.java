package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tournament.java
 *
 * Top-level model for a multi-team tournament.
 *
 * Stages:
 *   LEAGUE     — round-robin: each team plays every other team once
 *   SEMIFINAL  — top 4 from league play 1v4 and 2v3
 *   FINAL      — winners of the two semifinals
 *   COMPLETED  — final played, champion declared
 *
 * Fixtures are ordered: leagueFixtures first, then semis (added after league
 * is complete and standings are known), then final (added after semis).
 *
 * currentMatchIndex points into the corresponding fixture list for the
 * current stage. When the current match completes, this advances by one.
 */
public class Tournament implements Serializable {

    public enum Stage { LEAGUE, SEMIFINAL, FINAL, COMPLETED }

    private int                    playersPerTeam;
    private int                    maxOversPerMatch  = 6;     // default; configurable later
    private boolean                singleBatsmanMode = false;

    private List<TournamentTeam>   teams             = new ArrayList<>();

    private List<TournamentMatch>  leagueFixtures    = new ArrayList<>();
    private List<TournamentMatch>  semiFixtures      = new ArrayList<>();
    private TournamentMatch        finalFixture      = null;

    private Stage                  stage             = Stage.LEAGUE;
    private int                    currentMatchIndex = 0;     // index in current stage's list

    private String                 championName      = null;

    // ── Construction / setters ────────────────────────────────────────────────

    public int     getPlayersPerTeam()                { return playersPerTeam; }
    public void    setPlayersPerTeam(int v)           { playersPerTeam = v; }
    public int     getMaxOversPerMatch()              { return maxOversPerMatch; }
    public void    setMaxOversPerMatch(int v)         { maxOversPerMatch = v; }
    public boolean isSingleBatsmanMode()              { return singleBatsmanMode; }
    public void    setSingleBatsmanMode(boolean v)    { singleBatsmanMode = v; }

    public List<TournamentTeam>  getTeams()                { return teams; }
    public void                  setTeams(List<TournamentTeam> v) { teams = v != null ? v : new ArrayList<>(); }

    public List<TournamentMatch> getLeagueFixtures()       { return leagueFixtures; }
    public void                  setLeagueFixtures(List<TournamentMatch> v) { leagueFixtures = v != null ? v : new ArrayList<>(); }

    public List<TournamentMatch> getSemiFixtures()         { return semiFixtures; }
    public void                  setSemiFixtures(List<TournamentMatch> v)   { semiFixtures = v != null ? v : new ArrayList<>(); }

    public TournamentMatch       getFinalFixture()         { return finalFixture; }
    public void                  setFinalFixture(TournamentMatch v)         { finalFixture = v; }

    public Stage   getStage()                  { return stage != null ? stage : Stage.LEAGUE; }
    public void    setStage(Stage v)           { stage = v; }
    public int     getCurrentMatchIndex()      { return currentMatchIndex; }
    public void    setCurrentMatchIndex(int v) { currentMatchIndex = v; }
    public String  getChampionName()           { return championName; }
    public void    setChampionName(String v)   { championName = v; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the fixture list for the current stage. */
    public List<TournamentMatch> getCurrentStageFixtures() {
        switch (getStage()) {
            case LEAGUE:    return leagueFixtures;
            case SEMIFINAL: return semiFixtures;
            case FINAL:
                List<TournamentMatch> l = new ArrayList<>();
                if (finalFixture != null) l.add(finalFixture);
                return l;
            default:        return new ArrayList<>();
        }
    }

    /** The match currently being / about to be played. null if stage complete. */
    public TournamentMatch getCurrentMatch() {
        List<TournamentMatch> list = getCurrentStageFixtures();
        if (currentMatchIndex < 0 || currentMatchIndex >= list.size()) return null;
        return list.get(currentMatchIndex);
    }

    public TournamentTeam findTeamByName(String name) {
        if (name == null) return null;
        for (TournamentTeam t : teams) if (name.equals(t.getName())) return t;
        return null;
    }

    /** True when every fixture in the current stage has been played. */
    public boolean isCurrentStageComplete() {
        List<TournamentMatch> list = getCurrentStageFixtures();
        for (TournamentMatch m : list) if (!m.isCompleted()) return false;
        return !list.isEmpty();
    }

    /** Standings sorted by wins desc, then NRR / runs scored could be added later. */
    public List<TournamentTeam> getStandings() {
        List<TournamentTeam> sorted = new ArrayList<>(teams);
        java.util.Collections.sort(sorted, (a, b) -> {
            if (a.getWins() != b.getWins()) return b.getWins() - a.getWins();
            return b.getNetRunsScored() - a.getNetRunsScored();
        });
        return sorted;
    }

    /** Top N teams from current standings (used to seed semifinals). */
    public List<TournamentTeam> getTopTeams(int n) {
        List<TournamentTeam> standings = getStandings();
        return standings.subList(0, Math.min(n, standings.size()));
    }
}
