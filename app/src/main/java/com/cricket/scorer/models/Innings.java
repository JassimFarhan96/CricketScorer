package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Innings.java
 *
 * CHANGE: Supports single-batsman mode via the singleBatsmanMode flag
 * (read from Match and passed into the constructor).
 *
 * In single-batsman mode:
 *   - nonStrikerIndex = -1  (no non-striker exists)
 *   - swapStrike()          does nothing
 *   - nextBatsmanIndex      starts at 1 (only one opener, not two)
 *
 * In two-batsman mode (standard):
 *   - nonStrikerIndex starts at 1
 *   - swapStrike() swaps striker ↔ non-striker
 *   - nextBatsmanIndex starts at 2
 */
public class Innings implements Serializable {

    private int     inningsNumber;
    private boolean singleBatsmanMode; // mirrors Match.singleBatsmanMode

    private int totalRuns;
    private int totalWickets;
    private int totalValidBalls;

    private List<Over> completedOvers;
    private Over       currentOver;

    private int strikerIndex;
    private int nonStrikerIndex;  // -1 when singleBatsmanMode == true
    private int nextBatsmanIndex;

    private boolean isComplete;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public Innings(int inningsNumber, boolean singleBatsmanMode) {
        this.inningsNumber     = inningsNumber;
        this.singleBatsmanMode = singleBatsmanMode;
        this.totalRuns         = 0;
        this.totalWickets      = 0;
        this.totalValidBalls   = 0;
        this.completedOvers    = new ArrayList<>();
        this.isComplete        = false;

        this.strikerIndex = 0;

        if (singleBatsmanMode) {
            this.nonStrikerIndex  = -1; // no non-striker
            this.nextBatsmanIndex = 1;  // next player to come in is index 1
        } else {
            this.nonStrikerIndex  = 1;  // standard: player[1] is non-striker
            this.nextBatsmanIndex = 2;  // next player to come in is index 2
        }

        startNewOver();
    }

    // ─── Over management ──────────────────────────────────────────────────────

    public void startNewOver() {
        int overNum = completedOvers.size() + 1;
        currentOver = new Over(overNum);
    }

    /**
     * Moves the current over to completedOvers and starts a fresh over.
     * In two-batsman mode, also swaps strike (batsmen cross at end of over).
     * In single-batsman mode, no swap — the same striker faces all balls.
     */
    public void completeCurrentOver() {
        if (currentOver != null) completedOvers.add(currentOver);
        if (!singleBatsmanMode) swapStrike(); // rotate only in two-batsman mode
        startNewOver();
    }

    // ─── Ball recording ───────────────────────────────────────────────────────

    /**
     * Records a normal delivery.
     *
     * Two-batsman mode: rotates strike on odd runs (1, 3).
     * Single-batsman mode: NO rotation — same striker faces every ball.
     */
    public void recordNormalBall(int runs, Player striker) {
        Ball ball = Ball.normal(runs);
        currentOver.addBall(ball);
        totalRuns        += runs;
        totalValidBalls  += 1;
        striker.addRuns(runs);

        // Rotate only in two-batsman mode and only on odd runs
        if (!singleBatsmanMode && runs % 2 == 1) {
            swapStrike();
        }
    }

    /** Records a wide (+1 extra, NOT a valid ball). */
    public void recordWide() {
        currentOver.addBall(Ball.wide());
        totalRuns += 1;
    }

    /** Records a no-ball (+1 extra, NOT a valid ball). */
    public void recordNoBall() {
        currentOver.addBall(Ball.noBall());
        totalRuns += 1;
    }

    /**
     * Records a wicket (valid ball). Marks the striker as out.
     * The caller (MatchEngine) sets the new strikerIndex after this.
     */
    public void recordWicket(Player outPlayer) {
        currentOver.addBall(Ball.wicket());
        totalWickets     += 1;
        totalValidBalls  += 1;
        outPlayer.dismiss("out");
    }

    /**
     * Undoes the last ball in the current over.
     * Reverses all stat mutations including strike-rotation reversal.
     */
    public Ball undoLastBall(Player striker) {
        Ball removed = currentOver.removeLastBall();
        if (removed == null) return null;

        switch (removed.getType()) {
            case NORMAL:
                totalRuns        -= removed.getRuns();
                totalValidBalls  -= 1;
                striker.setRunsScored(striker.getRunsScored() - removed.getRuns());
                striker.setBallsFaced(striker.getBallsFaced() - 1);
                if (removed.getRuns() == 4) striker.setFours(striker.getFours() - 1);
                if (removed.getRuns() == 6) striker.setSixes(striker.getSixes() - 1);
                // Reverse the rotation that was applied for odd runs (two-batsman only)
                if (!singleBatsmanMode && removed.getRuns() % 2 == 1) swapStrike();
                break;
            case WIDE:
            case NO_BALL:
                totalRuns -= 1;
                break;
            case WICKET:
                totalValidBalls -= 1;
                totalWickets    -= 1;
                // Caller (MatchEngine) reverses the player's out status
                break;
        }
        return removed;
    }

    // ─── Strike rotation ──────────────────────────────────────────────────────

    /**
     * Swaps striker ↔ non-striker.
     * Silently does nothing in single-batsman mode (nonStrikerIndex == -1).
     */
    public void swapStrike() {
        if (singleBatsmanMode) return; // no swap — only one batsman
        int tmp          = strikerIndex;
        strikerIndex     = nonStrikerIndex;
        nonStrikerIndex  = tmp;
    }

    // ─── Computed helpers ─────────────────────────────────────────────────────

    public String getOversString() {
        return (totalValidBalls / 6) + "." + (totalValidBalls % 6);
    }

    public float getCurrentRunRate() {
        if (totalValidBalls == 0) return 0f;
        return totalRuns / ((float) totalValidBalls / 6f);
    }

    public float getRequiredRunRate(int target, int maxOvers) {
        int runsNeeded = target - totalRuns;
        int ballsLeft  = (maxOvers * 6) - totalValidBalls;
        if (ballsLeft <= 0) return 0f;
        return runsNeeded / ((float) ballsLeft / 6f);
    }

    public int getRunsNeeded(int target)          { return Math.max(0, target - totalRuns); }
    public int getBallsRemaining(int maxOvers)    { return (maxOvers * 6) - totalValidBalls; }
    public String getScoreString()                { return totalRuns + "/" + totalWickets; }

    public List<Over> getAllOvers() {
        List<Over> all = new ArrayList<>(completedOvers);
        if (currentOver != null && !currentOver.getBalls().isEmpty()) all.add(currentOver);
        return all;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int getInningsNumber()               { return inningsNumber; }
    public boolean isSingleBatsmanMode()        { return singleBatsmanMode; }
    public int getTotalRuns()                   { return totalRuns; }
    public void setTotalRuns(int v)             { totalRuns = v; }
    public int getTotalWickets()                { return totalWickets; }
    public void setTotalWickets(int v)          { totalWickets = v; }
    public int getTotalValidBalls()             { return totalValidBalls; }
    public void setTotalValidBalls(int v)       { totalValidBalls = v; }
    public List<Over> getCompletedOvers()       { return completedOvers; }
    public Over getCurrentOver()                { return currentOver; }
    public void setCurrentOver(Over v)          { currentOver = v; }
    public int getStrikerIndex()                { return strikerIndex; }
    public void setStrikerIndex(int v)          { strikerIndex = v; }
    public int getNonStrikerIndex()             { return nonStrikerIndex; }
    public void setNonStrikerIndex(int v)       { nonStrikerIndex = v; }
    public int getNextBatsmanIndex()            { return nextBatsmanIndex; }
    public void setNextBatsmanIndex(int v)      { nextBatsmanIndex = v; }
    public boolean isComplete()                 { return isComplete; }
    public void setComplete(boolean v)          { isComplete = v; }
}
