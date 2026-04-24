package com.cricket.scorer.activities;

import android.app.Application;

import com.cricket.scorer.models.Match;
import com.cricket.scorer.utils.MatchEngine;

/**
 * CricketApp.java
 * Custom Application class.
 *
 * Holds the active Match and MatchEngine as singletons
 * so they can be accessed across all Activities without
 * passing large Serializable objects through Intents.
 *
 * Register in AndroidManifest.xml:
 *   <application android:name=".activities.CricketApp" ...>
 */
public class CricketApp extends Application {

    private Match currentMatch;
    private MatchEngine matchEngine;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    // ─── Match lifecycle ──────────────────────────────────────────────────────

    /** Called by SetupActivity when a new match begins */
    public void startNewMatch(Match match) {
        this.currentMatch = match;
        this.matchEngine = new MatchEngine(match);
    }

    /** Clears match data (e.g. when starting a fresh match from Home) */
    public void clearMatch() {
        currentMatch = null;
        matchEngine = null;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public Match getCurrentMatch() { return currentMatch; }

    public MatchEngine getMatchEngine() { return matchEngine; }
}
