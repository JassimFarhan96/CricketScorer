package com.cricket.scorer.models;

import java.io.Serializable;

/**
 * Player.java
 * Represents a single player in a team.
 * Tracks batting statistics for a single innings.
 */
public class Player implements Serializable {

    private String name;
    private int runsScored;
    private int ballsFaced;
    private int fours;
    private int sixes;
    private boolean isOut;
    private boolean hasNotBatted; // true if player hasn't come to bat yet
    private String dismissalInfo; // e.g. "caught", "bowled", "run out"

    public Player(String name) {
        this.name = name;
        this.runsScored = 0;
        this.ballsFaced = 0;
        this.fours = 0;
        this.sixes = 0;
        this.isOut = false;
        this.hasNotBatted = true;
        this.dismissalInfo = "";
    }

    // ─── Batting stat updates ─────────────────────────────────────────────────

    public void addRuns(int runs) {
        this.runsScored += runs;
        this.ballsFaced++;
        if (runs == 4) this.fours++;
        if (runs == 6) this.sixes++;
    }

    public void addBallFaced() {
        this.ballsFaced++;
    }

    public void dismiss(String dismissalInfo) {
        this.isOut = true;
        this.dismissalInfo = dismissalInfo;
    }

    /** Resets stats for a new innings (used when second innings begins) */
    public void resetForNewInnings() {
        this.runsScored = 0;
        this.ballsFaced = 0;
        this.fours = 0;
        this.sixes = 0;
        this.isOut = false;
        this.hasNotBatted = true;
        this.dismissalInfo = "";
    }

    // ─── Computed stats ───────────────────────────────────────────────────────

    /** Strike rate = (runs / balls) * 100 */
    public float getStrikeRate() {
        if (ballsFaced == 0) return 0f;
        return ((float) runsScored / ballsFaced) * 100f;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRunsScored() { return runsScored; }
    public void setRunsScored(int runsScored) { this.runsScored = runsScored; }

    public int getBallsFaced() { return ballsFaced; }
    public void setBallsFaced(int ballsFaced) { this.ballsFaced = ballsFaced; }

    public int getFours() { return fours; }
    public void setFours(int fours) { this.fours = fours; }

    public int getSixes() { return sixes; }
    public void setSixes(int sixes) { this.sixes = sixes; }

    public boolean isOut() { return isOut; }
    public void setOut(boolean out) { isOut = out; }

    public boolean isHasNotBatted() { return hasNotBatted; }
    public void setHasNotBatted(boolean hasNotBatted) { this.hasNotBatted = hasNotBatted; }

    public String getDismissalInfo() { return dismissalInfo; }
    public void setDismissalInfo(String dismissalInfo) { this.dismissalInfo = dismissalInfo; }
}
