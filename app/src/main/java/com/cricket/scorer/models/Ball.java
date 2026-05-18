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
    private boolean runOutWicket;  // true if this WICKET ball also had completed runs (run out)

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

    /**
     * Run-out wicket where some runs were completed before the dismissal.
     * Example: batsmen take 2 then are run out attempting the 3rd → runsCompleted=2.
     * The completed runs count for the team and the on-strike batter.
     */
    public static Ball runOutWicket(int runsCompleted) {
        Ball b = new Ball();
        b.type = BallType.WICKET;
        b.runs = runsCompleted;
        b.isValid = true;
        b.runOutWicket = true;
        return b;
    }

    // ─── Display helpers ──────────────────────────────────────────────────────

    /** Short label displayed on the ball circle in the over tracker */
    public String getDisplayLabel() {
        switch (type) {
            case WIDE:    return "Wd";
            case NO_BALL: return "NB";
            case WICKET:  return runOutWicket && runs > 0 ? runs + "+W" : "W";
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
    public boolean isRunOutWicket() { return runOutWicket; }
    public void setRunOutWicket(boolean v) { runOutWicket = v; }
    public void setValid(boolean valid) { isValid = valid; }
}
