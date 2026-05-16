package com.cricket.scorer.activities;

import android.app.Application;

import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.utils.AppLogger;
import com.cricket.scorer.utils.CrashHandler;
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
    public void onCreate() {
        super.onCreate();

        // poi-android (the Android-stripped POI fork) ships its own
        // javax.xml.stream shim that looks for an XMLEventFactory via
        // system properties. Without these three lines it tries to load
        // com.bea.xml.stream.EventFactory which doesn't exist on Android
        // and throws FactoryConfigurationError at the first XSSFWorkbook call.
        // These must be set BEFORE any POI class is loaded (i.e. before
        // MatchExcelExporter.export() is called). Placing them here in
        // Application.onCreate() guarantees that.
        System.setProperty(
            "org.apache.poi.javax.xml.stream.XMLInputFactory",
            "com.fasterxml.aalto.stax.InputFactoryImpl");
        System.setProperty(
            "org.apache.poi.javax.xml.stream.XMLOutputFactory",
            "com.fasterxml.aalto.stax.OutputFactoryImpl");
        System.setProperty(
            "org.apache.poi.javax.xml.stream.XMLEventFactory",
            "com.fasterxml.aalto.stax.EventFactoryImpl");

        // Persistent logging + crash capture. Init BEFORE anything else so
        // a crash during the rest of startup is still recorded to the file.
        AppLogger.init(this);
        CrashHandler.install(this);
        AppLogger.i("CricketApp", "Application launched");
    }

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
