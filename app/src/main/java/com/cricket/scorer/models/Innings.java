package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Innings.java
 *
 * CHANGE — Baby over support:
 *
 * babyOverActivated:
 *   Set true when the user taps "Baby Over" (after ball 3).
 *   Means: the CURRENT over will be split — a second bowler will bowl
 *   the remaining balls. Reset to false when the over completes.
 *
 * currentSecondBowlerIndex / currentSecondBowlerName:
 *   The second bowler for the current over (set after baby-over dialog).
 *
 * secondBowlerSelected:
 *   True once the user has confirmed the second bowler for this baby over.
 *   Once true, recordNormalBall/recordWide etc. credit stats to the second
 *   bowler (because valid ball count >= secondBowlerFromBall).
 *
 * All four stat maps (overs/runs/wickets/balls) accumulate for BOTH bowlers
 * independently across the whole innings.
 *
 * completeCurrentOver() — increments overs for BOTH bowlers proportionally:
 *   First bowler:  gets 0.5 overs credit (stored as 0 complete + tracked via balls)
 *   Second bowler: same
 *   For simplicity in the overs-count map we still credit 1 full over to the
 *   FIRST bowler in a baby over, with the split annotated on the Over object.
 *   The runs/balls/wickets maps are accurate per-bowler regardless.
 */
public class Innings implements Serializable {

    private int     inningsNumber;
    private boolean singleBatsmanMode;

    private int totalRuns;
    private int totalWickets;
    private int totalValidBalls;

    private List<Over>  completedOvers;
    private Over        currentOver;

    private int strikerIndex;
    private int nonStrikerIndex;
    private int nextBatsmanIndex;

    private boolean isComplete;

    // ── First bowler for current over ────────────────────────────────────────
    private int     currentBowlerIndex  = -1;
    private String  currentBowlerName   = "";
    private boolean bowlerSelected      = false;

    // ── Baby over — second bowler for current over ────────────────────────────
    private boolean babyOverActivated        = false;
    private int     currentSecondBowlerIndex = -1;
    private String  currentSecondBowlerName  = "";
    private boolean secondBowlerSelected     = false;

    // ── Bowling stat maps ─────────────────────────────────────────────────────
    private Map<String, Integer> bowlerOversMap   = new HashMap<>();
    private Map<String, Integer> bowlerRunsMap    = new HashMap<>();
    private Map<String, Integer> bowlerWicketsMap = new HashMap<>();
    private Map<String, Integer> bowlerBallsMap   = new HashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public Innings(int inningsNumber, boolean singleBatsmanMode) {
        this.inningsNumber     = inningsNumber;
        this.singleBatsmanMode = singleBatsmanMode;
        completedOvers    = new ArrayList<>();
        bowlerOversMap    = new HashMap<>();
        bowlerRunsMap     = new HashMap<>();
        bowlerWicketsMap  = new HashMap<>();
        bowlerBallsMap    = new HashMap<>();
        strikerIndex     = 0;
        nonStrikerIndex  = singleBatsmanMode ? -1 : 1;
        nextBatsmanIndex = singleBatsmanMode ?  1 :  2;
        startNewOver();
    }

    // ─── Over management ──────────────────────────────────────────────────────

    public void startNewOver() {
        int overNum = completedOvers.size() + 1;
        currentOver              = new Over(overNum);
        bowlerSelected           = false;
        currentBowlerIndex       = -1;
        currentBowlerName        = "";
        babyOverActivated        = false;
        currentSecondBowlerIndex = -1;
        currentSecondBowlerName  = "";
        secondBowlerSelected     = false;
    }

    public void setCurrentOverBowler(int index, String name) {
        currentBowlerIndex = index;
        currentBowlerName  = name != null ? name : "";
        bowlerSelected     = true;
        if (currentOver != null) {
            currentOver.setBowlerIndex(index);
            currentOver.setBowlerName(name);
        }
    }

    /**
     * Called when the user confirms a baby-over bowler swap.
     * Marks the current over as a baby over and sets the second bowler.
     */
    public void setSecondBowlerForBabyOver(int index, String name) {
        babyOverActivated        = true;
        currentSecondBowlerIndex = index;
        currentSecondBowlerName  = name != null ? name : "";
        secondBowlerSelected     = true;
        if (currentOver != null) {
            currentOver.setBabyOver(true);
            currentOver.setSecondBowlerIndex(index);
            currentOver.setSecondBowlerName(name);
            currentOver.setSecondBowlerFromBall(currentOver.getValidBallCount() + 1);
        }
    }

    /**
     * Returns the name of the bowler who should receive credit for the
     * NEXT ball to be bowled, based on baby over state.
     */
    public String getActiveBowlerName() {
        if (babyOverActivated && secondBowlerSelected && !currentSecondBowlerName.isEmpty()) {
            return currentSecondBowlerName;
        }
        return currentBowlerName;
    }

    public void completeCurrentOver() {
        if (currentOver != null) {
            currentOver.setBowlerIndex(currentBowlerIndex);
            currentOver.setBowlerName(currentBowlerName);

            if (currentOver.isBabyOver()) {
                // Baby over: neither bowler gets a complete over credited.
                // Their balls/runs/wickets are already tracked accurately.
                // We do NOT increment bowlerOversMap for either bowler —
                // the display uses bowlerBallsMap for fractional notation.
            } else {
                // Normal over: first bowler gets one complete over
                if (!currentBowlerName.isEmpty()) {
                    addToMap(bowlerOversMap, currentBowlerName, 1);
                }
            }
            completedOvers.add(currentOver);
        }
        if (!singleBatsmanMode) swapStrike();
        startNewOver();
    }

    // ─── Ball recording ───────────────────────────────────────────────────────

    public void recordNormalBall(int runs, Player striker) {
        Ball ball = Ball.normal(runs);
        currentOver.addBall(ball);
        totalRuns       += runs;
        totalValidBalls += 1;
        striker.addRuns(runs);
        String bowler = getActiveBowlerName();
        if (!bowler.isEmpty()) {
            addToMap(bowlerRunsMap,  bowler, runs);
            addToMap(bowlerBallsMap, bowler, 1);
        }
        if (!singleBatsmanMode && runs % 2 == 1) swapStrike();
    }

    public void recordWide() {
        currentOver.addBall(Ball.wide());
        totalRuns += 1;
        String bowler = getActiveBowlerName();
        if (!bowler.isEmpty()) addToMap(bowlerRunsMap, bowler, 1);
    }

    public void recordNoBall() {
        currentOver.addBall(Ball.noBall());
        totalRuns += 1;
        String bowler = getActiveBowlerName();
        if (!bowler.isEmpty()) addToMap(bowlerRunsMap, bowler, 1);
    }

    public void recordWicket(Player outPlayer) {
        currentOver.addBall(Ball.wicket());
        totalWickets    += 1;
        totalValidBalls += 1;
        outPlayer.dismiss("out");
        String bowler = getActiveBowlerName();
        if (!bowler.isEmpty()) {
            addToMap(bowlerWicketsMap, bowler, 1);
            addToMap(bowlerBallsMap,   bowler, 1);
        }
    }

    public Ball undoLastBall(Player striker) {
        Ball removed = currentOver.removeLastBall();
        if (removed == null) return null;

        // On undo, determine which bowler to reverse stats for
        // After undo, valid ball count tells us which bowler bowled that ball
        int validAfterUndo = currentOver.getValidBallCount();
        String bowler;
        if (currentOver.isBabyOver() && currentOver.hasSecondBowler()
                && removed.isValid()
                && (validAfterUndo + 1) >= currentOver.getSecondBowlerFromBall()) {
            bowler = currentSecondBowlerName;
        } else {
            bowler = currentBowlerName;
        }

        switch (removed.getType()) {
            case NORMAL:
                totalRuns       -= removed.getRuns();
                totalValidBalls -= 1;
                striker.setRunsScored(striker.getRunsScored() - removed.getRuns());
                striker.setBallsFaced(striker.getBallsFaced() - 1);
                if (removed.getRuns() == 4) striker.setFours(striker.getFours() - 1);
                if (removed.getRuns() == 6) striker.setSixes(striker.getSixes() - 1);
                if (!bowler.isEmpty()) {
                    subtractFromMap(bowlerRunsMap,  bowler, removed.getRuns());
                    subtractFromMap(bowlerBallsMap, bowler, 1);
                }
                // Undo ball 4 when it was the first ball after baby over swap:
                // also reset baby over state so user can re-trigger baby over
                if (currentOver.isBabyOver()
                        && currentOver.getValidBallCount() < currentOver.getSecondBowlerFromBall() - 1) {
                    resetBabyOverState();
                }
                if (!singleBatsmanMode && removed.getRuns() % 2 == 1) swapStrike();
                break;
            case WIDE:
                totalRuns -= 1;
                if (!bowler.isEmpty()) subtractFromMap(bowlerRunsMap, bowler, 1);
                break;
            case NO_BALL:
                totalRuns -= 1;
                if (!bowler.isEmpty()) subtractFromMap(bowlerRunsMap, bowler, 1);
                break;
            case WICKET:
                totalValidBalls -= 1;
                totalWickets    -= 1;
                if (!bowler.isEmpty()) {
                    subtractFromMap(bowlerWicketsMap, bowler, 1);
                    subtractFromMap(bowlerBallsMap,   bowler, 1);
                }
                if (currentOver.isBabyOver()
                        && currentOver.getValidBallCount() < currentOver.getSecondBowlerFromBall() - 1) {
                    resetBabyOverState();
                }
                break;
        }
        return removed;
    }

    /** Reset baby over state when undo takes us back before the swap point */
    public void resetBabyOverState() {
        babyOverActivated        = false;
        currentSecondBowlerIndex = -1;
        currentSecondBowlerName  = "";
        secondBowlerSelected     = false;
        if (currentOver != null) {
            currentOver.setBabyOver(false);
            currentOver.setSecondBowlerIndex(-1);
            currentOver.setSecondBowlerName("");
        }
    }

    // ─── Strike rotation ──────────────────────────────────────────────────────

    public void swapStrike() {
        if (singleBatsmanMode) return;
        int tmp         = strikerIndex;
        strikerIndex    = nonStrikerIndex;
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

    public int    getRunsNeeded(int target)       { return Math.max(0, target - totalRuns); }
    public int    getBallsRemaining(int maxOvers)  { return (maxOvers * 6) - totalValidBalls; }
    public String getScoreString()                 { return totalRuns + "/" + totalWickets; }

    public List<Over> getAllOvers() {
        List<Over> all = new ArrayList<>(completedOvers);
        if (currentOver != null && !currentOver.getBalls().isEmpty()) all.add(currentOver);
        return all;
    }

    public List<BowlerStat> getBowlerStats() {
        List<BowlerStat> stats = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.addAll(bowlerOversMap.keySet());
        names.addAll(bowlerBallsMap.keySet());
        for (String name : names) {
            int overs   = bowlerOversMap.containsKey(name)   ? bowlerOversMap.get(name)   : 0;
            int balls   = bowlerBallsMap.containsKey(name)   ? bowlerBallsMap.get(name)   : 0;
            int runs    = bowlerRunsMap.containsKey(name)    ? bowlerRunsMap.get(name)    : 0;
            int wickets = bowlerWicketsMap.containsKey(name) ? bowlerWicketsMap.get(name) : 0;
            stats.add(new BowlerStat(name, overs, balls, runs, wickets));
        }
        return stats;
    }

    public String getBowlerSummary() {
        if (bowlerOversMap.isEmpty() && bowlerBallsMap.isEmpty()) return "";

        // Collect all bowler names that have bowled at least one ball or over
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.addAll(bowlerOversMap.keySet());
        names.addAll(bowlerBallsMap.keySet());

        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            int completeOvers = bowlerOversMap.containsKey(name)
                    ? bowlerOversMap.get(name) : 0;
            int totalBalls    = bowlerBallsMap.containsKey(name)
                    ? bowlerBallsMap.get(name) : 0;

            // Total balls = completeOvers * 6 + extra balls
            // But bowlerBallsMap only tracks balls within CURRENT or partial overs;
            // completeOvers tracks full 6-ball overs. Combine for display:
            int extraBalls = totalBalls % 6;  // balls beyond complete overs
            int displayOvers = completeOvers; // full overs from normal overs

            if (sb.length() > 0) sb.append("\n");
            sb.append(name).append(": ");
            if (extraBalls > 0) {
                sb.append(displayOvers).append(".").append(extraBalls).append(" ov");
            } else {
                sb.append(displayOvers).append(displayOvers == 1 ? " over" : " overs");
            }
        }
        return sb.toString();
    }

    // ─── Map helpers ──────────────────────────────────────────────────────────

    private void addToMap(Map<String, Integer> m, String k, int d) {
        m.put(k, (m.containsKey(k) ? m.get(k) : 0) + d);
    }
    private void subtractFromMap(Map<String, Integer> m, String k, int d) {
        m.put(k, Math.max(0, (m.containsKey(k) ? m.get(k) : 0) - d));
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int     getInningsNumber()               { return inningsNumber; }
    public boolean isSingleBatsmanMode()             { return singleBatsmanMode; }
    public int     getTotalRuns()                    { return totalRuns; }
    public void    setTotalRuns(int v)               { totalRuns = v; }
    public int     getTotalWickets()                 { return totalWickets; }
    public void    setTotalWickets(int v)            { totalWickets = v; }
    public int     getTotalValidBalls()              { return totalValidBalls; }
    public void    setTotalValidBalls(int v)         { totalValidBalls = v; }
    public List<Over> getCompletedOvers()            { return completedOvers; }
    public Over    getCurrentOver()                  { return currentOver; }
    public void    setCurrentOver(Over v)            { currentOver = v; }
    public int     getStrikerIndex()                 { return strikerIndex; }
    public void    setStrikerIndex(int v)            { strikerIndex = v; }
    public int     getNonStrikerIndex()              { return nonStrikerIndex; }
    public void    setNonStrikerIndex(int v)         { nonStrikerIndex = v; }
    public int     getNextBatsmanIndex()             { return nextBatsmanIndex; }
    public void    setNextBatsmanIndex(int v)        { nextBatsmanIndex = v; }
    public boolean isComplete()                      { return isComplete; }
    public void    setComplete(boolean v)            { isComplete = v; }
    public int     getCurrentBowlerIndex()           { return currentBowlerIndex; }
    public void    setCurrentBowlerIndex(int v)      { currentBowlerIndex = v; }
    public String  getCurrentBowlerName()            { return currentBowlerName; }
    public void    setCurrentBowlerName(String v)    { currentBowlerName = v != null ? v : ""; }
    public boolean isBowlerSelected()               { return bowlerSelected; }
    public void    setBowlerSelected(boolean v)      { bowlerSelected = v; }
    public boolean isBabyOverActivated()             { return babyOverActivated; }
    public void    setBabyOverActivated(boolean v)   { babyOverActivated = v; }
    public int     getCurrentSecondBowlerIndex()     { return currentSecondBowlerIndex; }
    public void    setCurrentSecondBowlerIndex(int v){ currentSecondBowlerIndex = v; }
    public String  getCurrentSecondBowlerName()      { return currentSecondBowlerName; }
    public void    setCurrentSecondBowlerName(String v){ currentSecondBowlerName = v != null ? v : ""; }
    public boolean isSecondBowlerSelected()          { return secondBowlerSelected; }
    public void    setSecondBowlerSelected(boolean v){ secondBowlerSelected = v; }
    public Map<String, Integer> getBowlerOversMap()  { return bowlerOversMap; }
    public void setBowlerOversMap(Map<String, Integer> v)   { bowlerOversMap   = v != null ? v : new HashMap<>(); }
    public Map<String, Integer> getBowlerRunsMap()   { return bowlerRunsMap; }
    public void setBowlerRunsMap(Map<String, Integer> v)    { bowlerRunsMap    = v != null ? v : new HashMap<>(); }
    public Map<String, Integer> getBowlerWicketsMap(){ return bowlerWicketsMap; }
    public void setBowlerWicketsMap(Map<String, Integer> v) { bowlerWicketsMap = v != null ? v : new HashMap<>(); }
    public Map<String, Integer> getBowlerBallsMap()  { return bowlerBallsMap; }
    public void setBowlerBallsMap(Map<String, Integer> v)   { bowlerBallsMap   = v != null ? v : new HashMap<>(); }
}
