package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Over.java
 * Represents one over in a cricket innings.
 *
 * An over is complete when exactly 6 VALID balls have been bowled.
 * Wides and No-balls do not count towards the 6-ball limit.
 * The over can therefore contain more than 6 Ball objects in total.
 */
public class Over implements Serializable {

    private int overNumber;          // 1-based over number
    private List<Ball> balls;        // all deliveries including extras

    public Over(int overNumber) {
        this.overNumber = overNumber;
        this.balls = new ArrayList<>();
    }

    // ─── Ball management ──────────────────────────────────────────────────────

    /** Adds a delivery to this over */
    public void addBall(Ball ball) {
        balls.add(ball);
    }

    /** Removes the last delivery (used by undo) */
    public Ball removeLastBall() {
        if (!balls.isEmpty()) {
            return balls.remove(balls.size() - 1);
        }
        return null;
    }

    // ─── Computed properties ──────────────────────────────────────────────────

    /** Number of valid (legal) deliveries bowled so far in this over */
    public int getValidBallCount() {
        int count = 0;
        for (Ball b : balls) {
            if (b.isValid()) count++;
        }
        return count;
    }

    /** Total legal deliveries remaining in this over */
    public int getBallsRemaining() {
        return 6 - getValidBallCount();
    }

    /** Whether this over is complete (6 valid balls bowled) */
    public boolean isComplete() {
        return getValidBallCount() >= 6;
    }

    /** Total runs scored in this over (including extras) */
    public int getTotalRuns() {
        int total = 0;
        for (Ball b : balls) {
            total += b.getRuns();
        }
        return total;
    }

    /** Number of wickets that fell in this over */
    public int getWickets() {
        int count = 0;
        for (Ball b : balls) {
            if (b.getType() == Ball.BallType.WICKET) count++;
        }
        return count;
    }

    /** Summary string for over history display, e.g. "1 · W 4 · 2" */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        for (Ball b : balls) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(b.getDisplayLabel());
        }
        return sb.toString();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int getOverNumber() { return overNumber; }
    public void setOverNumber(int overNumber) { this.overNumber = overNumber; }

    public List<Ball> getBalls() { return balls; }
    public void setBalls(List<Ball> balls) { this.balls = balls; }
}
