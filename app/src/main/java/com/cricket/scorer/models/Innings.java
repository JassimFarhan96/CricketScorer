package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Innings.java
 *
 * CHANGE: Added bowler tracking.
 *   - currentBowlerIndex / currentBowlerName: the bowler for the
 *     current (active) over. Set by the bowler-selection dialog.
 *   - bowlerOversMap: maps bowler name → number of complete overs bowled.
 *     Updated in completeCurrentOver().
 *   - bowlerSelected: flag set true once the user confirms the bowler
 *     dialog for the current over; reset to false when a new over begins.
 */
public class Innings implements Serializable {

    private int     inningsNumber;
    private boolean singleBatsmanMode;

    private int totalRuns;
    private int totalWickets;
    private int totalValidBalls;

    private List<Over>         completedOvers;
    private Over               currentOver;

    private int strikerIndex;
    private int nonStrikerIndex;
    private int nextBatsmanIndex;

    private boolean isComplete;

    // ── Bowler tracking ───────────────────────────────────────────────────────
    /** Index into the BOWLING team's player list for the current over's bowler */
    private int    currentBowlerIndex = -1;
    /** Display name of the current bowler (cached) */
    private String currentBowlerName  = "";
    /** True once the user has confirmed the bowler for the current over */
    private boolean bowlerSelected    = false;
    /**
     * Maps bowler name → complete overs bowled this innings.
     * Incremented in completeCurrentOver().
     */
    private Map<String, Integer> bowlerOversMap = new HashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public Innings(int inningsNumber, boolean singleBatsmanMode) {
        this.inningsNumber     = inningsNumber;
        this.singleBatsmanMode = singleBatsmanMode;
        this.completedOvers    = new ArrayList<>();
        this.bowlerOversMap    = new HashMap<>();

        strikerIndex     = 0;
        nonStrikerIndex  = singleBatsmanMode ? -1 : 1;
        nextBatsmanIndex = singleBatsmanMode ?  1 :  2;

        startNewOver();
    }

    // ─── Over management ──────────────────────────────────────────────────────

    public void startNewOver() {
        int overNum = completedOvers.size() + 1;
        currentOver = new Over(overNum);
        // Reset bowler state — dialog must be shown again for the new over
        bowlerSelected     = false;
        currentBowlerIndex = -1;
        currentBowlerName  = "";
    }

    /**
     * Assigns the bowler for the current over and marks selection as done.
     * Called when the user confirms the bowler dialog.
     */
    public void setCurrentOverBowler(int bowlerIndex, String bowlerName) {
        this.currentBowlerIndex = bowlerIndex;
        this.currentBowlerName  = bowlerName != null ? bowlerName : "";
        this.bowlerSelected     = true;
        // Tag the over itself so it's persisted with the over data
        if (currentOver != null) {
            currentOver.setBowlerIndex(bowlerIndex);
            currentOver.setBowlerName(bowlerName);
        }
    }

    /**
     * Completes the current over:
     *   1. Tags the over with the bowler info (in case it wasn't set yet).
     *   2. Increments that bowler's over count in bowlerOversMap.
     *   3. Moves currentOver to completedOvers.
     *   4. Rotates strike (two-batsman mode only).
     *   5. Starts a fresh over and resets bowler selection flag.
     */
    public void completeCurrentOver() {
        if (currentOver != null) {
            // Ensure bowler is tagged on the over object
            currentOver.setBowlerIndex(currentBowlerIndex);
            currentOver.setBowlerName(currentBowlerName);

            // Update bowler overs count
            if (!currentBowlerName.isEmpty()) {
                int prev = bowlerOversMap.containsKey(currentBowlerName)
                        ? bowlerOversMap.get(currentBowlerName) : 0;
                bowlerOversMap.put(currentBowlerName, prev + 1);
            }
            completedOvers.add(currentOver);
        }
        if (!singleBatsmanMode) swapStrike();
        startNewOver(); // resets bowlerSelected to false
    }

    // ─── Ball recording ───────────────────────────────────────────────────────

    public void recordNormalBall(int runs, Player striker) {
        Ball ball = Ball.normal(runs);
        currentOver.addBall(ball);
        totalRuns       += runs;
        totalValidBalls += 1;
        striker.addRuns(runs);
        if (!singleBatsmanMode && runs % 2 == 1) swapStrike();
    }

    public void recordWide() {
        currentOver.addBall(Ball.wide());
        totalRuns += 1;
    }

    public void recordNoBall() {
        currentOver.addBall(Ball.noBall());
        totalRuns += 1;
    }

    public void recordWicket(Player outPlayer) {
        currentOver.addBall(Ball.wicket());
        totalWickets    += 1;
        totalValidBalls += 1;
        outPlayer.dismiss("out");
    }

    public Ball undoLastBall(Player striker) {
        Ball removed = currentOver.removeLastBall();
        if (removed == null) return null;
        switch (removed.getType()) {
            case NORMAL:
                totalRuns       -= removed.getRuns();
                totalValidBalls -= 1;
                striker.setRunsScored(striker.getRunsScored() - removed.getRuns());
                striker.setBallsFaced(striker.getBallsFaced() - 1);
                if (removed.getRuns() == 4) striker.setFours(striker.getFours() - 1);
                if (removed.getRuns() == 6) striker.setSixes(striker.getSixes() - 1);
                if (!singleBatsmanMode && removed.getRuns() % 2 == 1) swapStrike();
                break;
            case WIDE: case NO_BALL:
                totalRuns -= 1;
                break;
            case WICKET:
                totalValidBalls -= 1;
                totalWickets    -= 1;
                break;
        }
        return removed;
    }

    // ─── Strike rotation ──────────────────────────────────────────────────────

    public void swapStrike() {
        if (singleBatsmanMode) return;
        int tmp      = strikerIndex;
        strikerIndex = nonStrikerIndex;
        nonStrikerIndex = tmp;
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

    public int     getRunsNeeded(int target)       { return Math.max(0, target - totalRuns); }
    public int     getBallsRemaining(int maxOvers)  { return (maxOvers * 6) - totalValidBalls; }
    public String  getScoreString()                 { return totalRuns + "/" + totalWickets; }

    public List<Over> getAllOvers() {
        List<Over> all = new ArrayList<>(completedOvers);
        if (currentOver != null && !currentOver.getBalls().isEmpty()) all.add(currentOver);
        return all;
    }

    /**
     * Returns a summary of bowler overs for display under the over history.
     * Format: "Player Name: 2 ov", one per line.
     * Only includes bowlers who have bowled at least one complete over.
     */
    public String getBowlerSummary() {
        if (bowlerOversMap.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : bowlerOversMap.entrySet()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(e.getKey()).append(": ").append(e.getValue())
              .append(e.getValue() == 1 ? " over" : " overs");
        }
        return sb.toString();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int     getInningsNumber()                  { return inningsNumber; }
    public boolean isSingleBatsmanMode()               { return singleBatsmanMode; }
    public int     getTotalRuns()                      { return totalRuns; }
    public void    setTotalRuns(int v)                 { totalRuns = v; }
    public int     getTotalWickets()                   { return totalWickets; }
    public void    setTotalWickets(int v)              { totalWickets = v; }
    public int     getTotalValidBalls()                { return totalValidBalls; }
    public void    setTotalValidBalls(int v)           { totalValidBalls = v; }
    public List<Over> getCompletedOvers()              { return completedOvers; }
    public Over    getCurrentOver()                    { return currentOver; }
    public void    setCurrentOver(Over v)              { currentOver = v; }
    public int     getStrikerIndex()                   { return strikerIndex; }
    public void    setStrikerIndex(int v)              { strikerIndex = v; }
    public int     getNonStrikerIndex()                { return nonStrikerIndex; }
    public void    setNonStrikerIndex(int v)           { nonStrikerIndex = v; }
    public int     getNextBatsmanIndex()               { return nextBatsmanIndex; }
    public void    setNextBatsmanIndex(int v)          { nextBatsmanIndex = v; }
    public boolean isComplete()                        { return isComplete; }
    public void    setComplete(boolean v)              { isComplete = v; }
    public int     getCurrentBowlerIndex()             { return currentBowlerIndex; }
    public void    setCurrentBowlerIndex(int v)        { currentBowlerIndex = v; }
    public String  getCurrentBowlerName()              { return currentBowlerName; }
    public void    setCurrentBowlerName(String v)      { currentBowlerName = v != null ? v : ""; }
    public boolean isBowlerSelected()                  { return bowlerSelected; }
    public void    setBowlerSelected(boolean v)        { bowlerSelected = v; }
    public Map<String, Integer> getBowlerOversMap()    { return bowlerOversMap; }
    public void setBowlerOversMap(Map<String, Integer> v) { bowlerOversMap = v != null ? v : new HashMap<>(); }
}
