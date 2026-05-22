package com.cricket.scorer.models;

import java.io.Serializable;

/**
 * BowlerStat.java
 *
 * Holds one bowler's complete figures for a single innings.
 * Used by StatsActivity to build the bowling table.
 *
 * CHANGE: extras is now split into wides and noBalls so callers can
 * display "4 (3Wd,1Nb)" instead of just "4".
 *
 * Fields:
 *   name     — player name
 *   overs    — complete overs bowled
 *   balls    — total valid balls (for partial over display: overs.balls)
 *   runs     — runs conceded (includes extras)
 *   wickets  — wickets taken
 *   wides    — wide runs conceded by this bowler (penalty + any completed runs)
 *   noBalls  — no-ball penalties conceded by this bowler (always 1 per no-ball)
 *   extras   — wides + noBalls (kept for backwards compatibility)
 *   economy  — runs per over (computed)
 */
public class BowlerStat implements Serializable {

    private final String name;
    private final int    overs;
    private final int    balls;
    private final int    runs;
    private final int    wickets;
    private final int    wides;
    private final int    noBalls;

    public BowlerStat(String name, int overs, int balls, int runs, int wickets,
                      int wides, int noBalls) {
        this.name    = name;
        this.overs   = overs;
        this.balls   = balls;
        this.runs    = runs;
        this.wickets = wickets;
        this.wides   = wides;
        this.noBalls = noBalls;
    }

    // ── Backwards-compatible constructor (extras = wides + noBalls, no breakdown) ──
    public BowlerStat(String name, int overs, int balls, int runs, int wickets, int extras) {
        this(name, overs, balls, runs, wickets, extras, 0);
    }

    public String getName()    { return name; }
    public int    getOvers()   { return overs; }
    public int    getBalls()   { return balls; }
    public int    getRuns()    { return runs; }
    public int    getWickets() { return wickets; }
    public int    getWides()   { return wides; }
    public int    getNoBalls() { return noBalls; }

    /** Total extras = wides + no-ball penalties. */
    public int getExtras() { return wides + noBalls; }

    /**
     * Returns the extras display string with breakdown in brackets.
     *
     * Examples:
     *   0 wides, 0 NB  → "0"
     *   3 wides, 0 NB  → "3 (3Wd)"
     *   0 wides, 1 NB  → "1 (1Nb)"
     *   4 wides, 2 NB  → "6 (4Wd,2Nb)"
     */
    public String getExtrasDisplay() {
        int total = getExtras();
        if (total == 0) return "0";
        if (wides > 0 && noBalls > 0) {
            return total + " (" + wides + "Wd," + noBalls + "Nb)";
        } else if (wides > 0) {
            return total + " (" + wides + "Wd)";
        } else {
            return total + " (" + noBalls + "Nb)";
        }
    }

    /**
     * Overs string in cricket notation: e.g. "3.2" means 3 complete overs + 2 balls.
     * balls % 6 gives the extra balls beyond complete overs.
     */
    public String getOversString() {
        int partialBalls = balls % 6;
        return overs + (partialBalls > 0 ? "." + partialBalls : ".0");
    }

    /** Economy rate = runs / overs bowled (as decimal overs). */
    public float getEconomy() {
        if (balls == 0) return 0f;
        float decimalOvers = balls / 6f;
        return runs / decimalOvers;
    }
}
