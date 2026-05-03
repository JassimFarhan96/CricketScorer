package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Over.java
 *
 * CHANGE: Added baby-over (shared over) support.
 *
 * A baby over is when two bowlers split one 6-ball over — typically
 * the first bowler delivers balls 1–3 and the second delivers 4–6.
 *
 * New fields:
 *   isBabyOver          — true if this over was shared between two bowlers
 *   secondBowlerIndex   — index of the second bowler in the bowling team
 *   secondBowlerName    — display name of the second bowler
 *   secondBowlerFromBall — the ball number (1-based) from which the second
 *                          bowler took over (always 4 in standard baby over)
 */
public class Over implements Serializable {

    private int        overNumber;
    private List<Ball> balls;

    // First bowler (set at start of over — existing fields)
    private int    bowlerIndex  = -1;
    private String bowlerName   = "";

    // Baby over — second bowler fields
    private boolean isBabyOver         = false;
    private int     secondBowlerIndex  = -1;
    private String  secondBowlerName   = "";
    private int     secondBowlerFromBall = 4;  // always ball 4 in a baby over

    public Over(int overNumber) {
        this.overNumber = overNumber;
        this.balls      = new ArrayList<>();
    }

    // ─── Ball management ──────────────────────────────────────────────────────

    public void addBall(Ball ball)    { balls.add(ball); }

    public Ball removeLastBall() {
        if (!balls.isEmpty()) return balls.remove(balls.size() - 1);
        return null;
    }

    // ─── Computed ─────────────────────────────────────────────────────────────

    public int getValidBallCount() {
        int count = 0;
        for (Ball b : balls) if (b.isValid()) count++;
        return count;
    }

    public int getBallsRemaining() { return 6 - getValidBallCount(); }
    public boolean isComplete()    { return getValidBallCount() >= 6; }

    public int getTotalRuns() {
        int total = 0;
        for (Ball b : balls) total += b.getRuns();
        return total;
    }

    public int getWickets() {
        int count = 0;
        for (Ball b : balls) if (b.getType() == Ball.BallType.WICKET) count++;
        return count;
    }

    public boolean hasBowler() { return bowlerIndex >= 0 && !bowlerName.isEmpty(); }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        for (Ball b : balls) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(b.getDisplayLabel());
        }
        return sb.toString();
    }

    public boolean hasSecondBowler() {
        return isBabyOver && secondBowlerIndex >= 0 && !secondBowlerName.isEmpty();
    }

    /**
     * Returns the effective bowler name for a given 1-based ball position.
     * Balls 1–(secondBowlerFromBall-1) = first bowler.
     * Balls secondBowlerFromBall–6     = second bowler (if baby over).
     */
    public String getBowlerForBall(int ballPosition) {
        if (isBabyOver && ballPosition >= secondBowlerFromBall && hasSecondBowler()) {
            return secondBowlerName;
        }
        return bowlerName;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int        getOverNumber()                  { return overNumber; }
    public void       setOverNumber(int v)             { overNumber = v; }
    public List<Ball> getBalls()                       { return balls; }
    public void       setBalls(List<Ball> v)           { balls = v; }
    public int        getBowlerIndex()                 { return bowlerIndex; }
    public void       setBowlerIndex(int v)            { bowlerIndex = v; }
    public String     getBowlerName()                  { return bowlerName; }
    public void       setBowlerName(String v)          { bowlerName = v != null ? v : ""; }
    public boolean    isBabyOver()                     { return isBabyOver; }
    public void       setBabyOver(boolean v)           { isBabyOver = v; }
    public int        getSecondBowlerIndex()           { return secondBowlerIndex; }
    public void       setSecondBowlerIndex(int v)      { secondBowlerIndex = v; }
    public String     getSecondBowlerName()            { return secondBowlerName; }
    public void       setSecondBowlerName(String v)    { secondBowlerName = v != null ? v : ""; }
    public int        getSecondBowlerFromBall()        { return secondBowlerFromBall; }
    public void       setSecondBowlerFromBall(int v)   { secondBowlerFromBall = v; }
}
