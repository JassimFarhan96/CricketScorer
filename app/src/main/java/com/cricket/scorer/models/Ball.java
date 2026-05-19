package com.cricket.scorer.models;

import java.io.Serializable;

/**
 * Ball.java
 * Represents a single delivery bowled in a cricket match.
 *
 * BallType:
 *   NORMAL  – a legal delivery (dot, 1, 2, 3, 4, 6)
 *   WIDE    – wide delivery; +1 run, does NOT count as a valid ball
 *   NO_BALL – no-ball; +1 run, does NOT count as a valid ball
 *   WICKET  – batsman dismissed; counts as a valid ball
 */
public class Ball implements Serializable {

    public enum BallType {
        NORMAL,
        WIDE,
        NO_BALL,
        WICKET
    }

    private BallType type;
    private int runs;           // runs scored off this ball (including extras)
    private boolean isValid;    // whether this counts as one of the 6 balls in an over
    private boolean runOutWicket;  // true if this ball (WICKET or WIDE) also had a run-out
    private boolean wideWithExtras; // true if this WIDE ball had extra completed runs

    // ─── Factory methods ──────────────────────────────────────────────────────

    public static Ball normal(int runs) {
        Ball b = new Ball();
        b.type = BallType.NORMAL;
        b.runs = runs;
        b.isValid = true;
        return b;
    }

    public static Ball wide() {
        Ball b = new Ball();
        b.type = BallType.WIDE;
        b.runs = 1;      // 1 extra run for wide
        b.isValid = false;
        return b;
    }

    /**
     * Wide delivery where the batters also completed extra runs.
     * @param totalRuns  1 (wide penalty) + completed runs, e.g. 3 if 2 extra runs taken
     */
    public static Ball wideWithRuns(int totalRuns) {
        Ball b = new Ball();
        b.type = BallType.WIDE;
        b.runs = totalRuns;
        b.isValid = false;
        b.wideWithExtras = true;
        return b;
    }

    /**
     * Wide delivery where batters completed extra runs AND one batter was run out.
     * @param totalRuns  1 (wide penalty) + completed runs before the wicket
     */
    public static Ball wideRunOut(int totalRuns) {
        Ball b = new Ball();
        b.type = BallType.WIDE;
        b.runs = totalRuns;
        b.isValid = false;
        b.wideWithExtras = true;
        b.runOutWicket = true;
        return b;
    }

    public static Ball noBall() {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1;      // 1 extra run for no-ball
        b.isValid = false;
        return b;
    }

    public static Ball wicket() {
        Ball b = new Ball();
        b.type = BallType.WICKET;
        b.runs = 0;
        b.isValid = true;
        return b;
    }

    // ─── Display helpers ──────────────────────────────────────────────────────

    /** Short label displayed on the ball circle in the over tracker */
    public String getDisplayLabel() {
        switch (type) {
            case WIDE:
                if (runOutWicket) return runs + "Wd+W";
                if (wideWithExtras) return runs + "Wd";
                return "Wd";
            case NO_BALL: return "NB";
            case WICKET:  return "W";
            case NORMAL:  return runs == 0 ? "·" : String.valueOf(runs);
            default:      return "?";
        }
    }

    /** Returns a color category string used by the UI adapter */
    public String getColorTag() {
        switch (type) {
            case WIDE:    return "wide";
            case NO_BALL: return "noball";
            case WICKET:  return "wicket";
            case NORMAL:
                if (runs == 0) return "dot";
                if (runs == 4) return "four";
                if (runs == 6) return "six";
                return "runs";
            default: return "dot";
        }
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public BallType getType() { return type; }
    public void setType(BallType type) { this.type = type; }

    public int getRuns() { return runs; }
    public void setRuns(int runs) { this.runs = runs; }

    public boolean isValid() { return isValid; }
    public boolean isRunOutWicket()    { return runOutWicket; }
    public void    setRunOutWicket(boolean v) { runOutWicket = v; }
    public boolean isWideWithExtras()  { return wideWithExtras; }
    public void setValid(boolean valid) { isValid = valid; }
}
