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
    private boolean runOutWicket;   // true if this ball (WICKET or WIDE) also had a run-out
    private boolean wideWithExtras;  // true if this WIDE ball had extra completed runs
    private boolean noBallWithExtras; // true if this NO_BALL had batsman runs or a run-out
    private boolean bye;    // ball passed bat and body — runs are extras, NOT credited to batsman
    private boolean legBye; // ball hit body — runs are extras, NOT credited to batsman

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

    /**
     * Regular (non-wide) wicket where some runs were completed before the dismissal.
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

    public static Ball noBall() {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1;      // 1 extra run for no-ball
        b.isValid = false;
        return b;
    }

    public static Ball noBallWithRuns(int batsmanRuns) {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1 + batsmanRuns;
        b.isValid = false;
        b.noBallWithExtras = true;
        return b;
    }

    public static Ball noBallRunOut(int batsmanRuns) {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1 + batsmanRuns;
        b.isValid = false;
        b.noBallWithExtras = true;
        b.runOutWicket = true;
        return b;
    }

    /** No-ball + bye runs (not credited to batsman). Total = 1 NB penalty + byeRuns. */
    public static Ball noBallBye(int byeRuns) {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1 + byeRuns;
        b.isValid = false;
        b.noBallWithExtras = byeRuns > 0;
        b.bye = true;
        return b;
    }

    /** No-ball + leg-bye runs (not credited to batsman). Total = 1 NB penalty + legByeRuns. */
    public static Ball noBallLegBye(int legByeRuns) {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1 + legByeRuns;
        b.isValid = false;
        b.noBallWithExtras = legByeRuns > 0;
        b.legBye = true;
        return b;
    }

    /** No-ball + bye runs + run-out. Total = 1 NB penalty + byeRuns. */
    public static Ball noBallByeRunOut(int byeRuns) {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1 + byeRuns;
        b.isValid = false;
        b.noBallWithExtras = true;
        b.bye = true;
        b.runOutWicket = true;
        return b;
    }

    /** No-ball + leg-bye runs + run-out. Total = 1 NB penalty + legByeRuns. */
    public static Ball noBallLegByeRunOut(int legByeRuns) {
        Ball b = new Ball();
        b.type = BallType.NO_BALL;
        b.runs = 1 + legByeRuns;
        b.isValid = false;
        b.noBallWithExtras = true;
        b.legBye = true;
        b.runOutWicket = true;
        return b;
    }

    /** Run-out wicket where runs before dismissal were byes (not batsman credit). Valid ball. */
    public static Ball runOutWicketBye(int byeRuns) {
        Ball b = new Ball();
        b.type = BallType.WICKET;
        b.runs = byeRuns;
        b.isValid = true;
        b.runOutWicket = true;
        b.bye = true;
        return b;
    }

    /** Run-out wicket where runs before dismissal were leg-byes (not batsman credit). Valid ball. */
    public static Ball runOutWicketLegBye(int legByeRuns) {
        Ball b = new Ball();
        b.type = BallType.WICKET;
        b.runs = legByeRuns;
        b.isValid = true;
        b.runOutWicket = true;
        b.legBye = true;
        return b;
    }

    /** Bye: valid ball, runs = extras only, striker gets ballsFaced++ but no runs credited. */
    public static Ball bye(int runs) {
        Ball b = new Ball(); b.type = BallType.NORMAL;
        b.runs = runs; b.isValid = true; b.bye = true; return b;
    }

    /** Leg-bye: same as bye but labelled LB. */
    public static Ball legBye(int runs) {
        Ball b = new Ball(); b.type = BallType.NORMAL;
        b.runs = runs; b.isValid = true; b.legBye = true; return b;
    }

    /** Bye + run-out: bye extras + wicket, valid ball. */
    public static Ball byeRunOut(int runs) {
        Ball b = new Ball(); b.type = BallType.NORMAL;
        b.runs = runs; b.isValid = true; b.bye = true; b.runOutWicket = true; return b;
    }

    /** Leg-bye + run-out: leg-bye extras + wicket, valid ball. */
    public static Ball legByeRunOut(int runs) {
        Ball b = new Ball(); b.type = BallType.NORMAL;
        b.runs = runs; b.isValid = true; b.legBye = true; b.runOutWicket = true; return b;
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
            case NO_BALL:
                int nbExtra = runs - 1; // additional runs beyond the NB penalty
                if (bye) {
                    if (runOutWicket) return "Nb+" + (nbExtra > 0 ? nbExtra + "B" : "") + "W";
                    return nbExtra > 0 ? "Nb+" + nbExtra + "B" : "Nb";
                }
                if (legBye) {
                    if (runOutWicket) return "Nb+" + (nbExtra > 0 ? nbExtra + "Lb" : "") + "W";
                    return nbExtra > 0 ? "Nb+" + nbExtra + "Lb" : "Nb";
                }
                if (runOutWicket) return runs + "Nb+W";
                if (noBallWithExtras) return runs + "Nb";
                return "Nb";
            case WICKET:
                if (runOutWicket && runs > 0) {
                    if (bye)    return runs + "B+W";
                    if (legBye) return runs + "Lb+W";
                    return runs + "+W";
                }
                return "W";
            case NORMAL:
                if (bye)    return runOutWicket ? runs + "B+W" : runs + "B";
                if (legBye) return runOutWicket ? runs + "LB+W" : runs + "LB";
                return runs == 0 ? "·" : String.valueOf(runs);
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
    public boolean isWideWithExtras()   { return wideWithExtras; }
    public boolean isNoBallWithExtras() { return noBallWithExtras; }
    public boolean isBye()         { return bye; }
    public boolean isLegBye()      { return legBye; }
    public boolean isByeOrLegBye() { return bye || legBye; }
    public void setValid(boolean valid) { isValid = valid; }
}
