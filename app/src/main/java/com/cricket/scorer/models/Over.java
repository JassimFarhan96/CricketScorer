package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Over.java
 *
 * CHANGE: Added bowlerName and bowlerIndex fields.
 *   bowlerIndex  → index into the bowling team's player list
 *   bowlerName   → display name (cached so it survives player-list edits)
 *
 * Both are set when the user confirms the bowler selection dialog at the
 * start of each over (including the very first over of the innings).
 */
public class Over implements Serializable {

    private int    overNumber;
    private List<Ball> balls;

    // ── Bowler info ───────────────────────────────────────────────────────────
    private int    bowlerIndex = -1;   // -1 = not yet assigned
    private String bowlerName  = "";

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

    // ─── Computed properties ──────────────────────────────────────────────────

    public int getValidBallCount() {
        int count = 0;
        for (Ball b : balls) if (b.isValid()) count++;
        return count;
    }

    public int getBallsRemaining() { return 6 - getValidBallCount(); }

    public boolean isComplete() { return getValidBallCount() >= 6; }

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

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        for (Ball b : balls) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(b.getDisplayLabel());
        }
        return sb.toString();
    }

    /** Whether a bowler has been assigned to this over */
    public boolean hasBowler() { return bowlerIndex >= 0 && !bowlerName.isEmpty(); }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int        getOverNumber()              { return overNumber; }
    public void       setOverNumber(int v)         { overNumber = v; }
    public List<Ball> getBalls()                   { return balls; }
    public void       setBalls(List<Ball> v)       { balls = v; }
    public int        getBowlerIndex()             { return bowlerIndex; }
    public void       setBowlerIndex(int v)        { bowlerIndex = v; }
    public String     getBowlerName()              { return bowlerName; }
    public void       setBowlerName(String v)      { bowlerName = v != null ? v : ""; }
}
