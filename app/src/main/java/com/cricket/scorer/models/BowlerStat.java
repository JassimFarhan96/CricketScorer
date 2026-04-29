package com.cricket.scorer.models;

import java.io.Serializable;

/**
 * BowlerStat.java
 *
 * Holds one bowler's complete figures for a single innings.
 * Used by StatsActivity to build the bowling table.
 *
 * Fields:
 *   name     — player name
 *   overs    — complete overs bowled
 *   balls    — total valid balls (for partial over display: overs.balls)
 *   runs     — runs conceded (includes extras)
 *   wickets  — wickets taken
 *   economy  — runs per over (computed)
 */
public class BowlerStat implements Serializable {

    private final String name;
    private final int    overs;
    private final int    balls;
    private final int    runs;
    private final int    wickets;

    public BowlerStat(String name, int overs, int balls, int runs, int wickets) {
        this.name    = name;
        this.overs   = overs;
        this.balls   = balls;
        this.runs    = runs;
        this.wickets = wickets;
    }

    public String getName()    { return name; }
    public int    getOvers()   { return overs; }
    public int    getBalls()   { return balls; }
    public int    getRuns()    { return runs; }
    public int    getWickets() { return wickets; }

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
