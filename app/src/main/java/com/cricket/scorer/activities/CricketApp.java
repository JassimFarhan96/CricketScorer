package com.cricket.scorer.activities;

import android.app.Application;

import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.utils.MatchEngine;

/**
 * CricketApp.java
 * Custom Application class — holds singletons for the active match and
 * the active tournament (if any).
 *
 * CHANGE: Added tournament holder. When a tournament is active,
 * matches are part of the tournament and on completion return to
 * TournamentDashboardActivity instead of StatsActivity.
 */
public class CricketApp extends Application {

    private Match       currentMatch;
    private MatchEngine matchEngine;
    private Tournament  currentTournament;

    @Override
    public void onCreate() { super.onCreate(); }

    // ─── Match lifecycle ──────────────────────────────────────────────────────

    public void startNewMatch(Match match) {
        this.currentMatch = match;
        this.matchEngine  = new MatchEngine(match);
    }

    public void clearMatch() {
        currentMatch = null;
        matchEngine  = null;
    }

    public Match       getCurrentMatch() { return currentMatch; }
    public MatchEngine getMatchEngine()  { return matchEngine; }

    // ─── Tournament lifecycle ─────────────────────────────────────────────────

    public void       startNewTournament(Tournament t) { this.currentTournament = t; }
    public void       clearTournament()                { this.currentTournament = null; }
    public Tournament getCurrentTournament()           { return currentTournament; }
    public boolean    isTournamentActive()             { return currentTournament != null; }
}
