package com.cricket.scorer.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Innings.java
 *
 * CHANGE: Added per-bowler runs and wickets maps alongside bowlerOversMap.
 *   bowlerRunsMap    → maps bowlerName → total runs conceded (incl. extras)
 *   bowlerWicketsMap → maps bowlerName → wickets taken
 *   bowlerBallsMap   → maps bowlerName → valid balls bowled (for economy calc)
 *
 * All three are updated in:
 *   recordNormalBall()  → adds runs + ball to current bowler
 *   recordWide()        → adds 1 run (no ball)
 *   recordNoBall()      → adds 1 run (no ball)
 *   recordWicket()      → adds wicket + ball to current bowler
 *   completeCurrentOver() → already increments bowlerOversMap
 *   undoLastBall()      → reverses the appropriate map entry
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

    // ── Bowler tracking ───────────────────────────────────────────────────────
    private int     currentBowlerIndex = -1;
    private String  currentBowlerName  = "";
    private boolean bowlerSelected     = false;

    private Map<String, Integer> bowlerOversMap   = new HashMap<>();
    private Map<String, Integer> bowlerRunsMap    = new HashMap<>();  // NEW
    private Map<String, Integer> bowlerWicketsMap = new HashMap<>();  // NEW
    private Map<String, Integer> bowlerBallsMap   = new HashMap<>();  // NEW

    // ─── Constructor ──────────────────────────────────────────────────────────

    public Innings(int inningsNumber, boolean singleBatsmanMode) {
        this.inningsNumber     = inningsNumber;
        this.singleBatsmanMode = singleBatsmanMode;
        this.completedOvers    = new ArrayList<>();
        this.bowlerOversMap    = new HashMap<>();
        this.bowlerRunsMap     = new HashMap<>();
        this.bowlerWicketsMap  = new HashMap<>();
        this.bowlerBallsMap    = new HashMap<>();

        strikerIndex     = 0;
        nonStrikerIndex  = singleBatsmanMode ? -1 : 1;
        nextBatsmanIndex = singleBatsmanMode ?  1 :  2;

        startNewOver();
    }

    // ─── Over management ──────────────────────────────────────────────────────

    public void startNewOver() {
        int overNum = completedOvers.size() + 1;
        currentOver        = new Over(overNum);
        bowlerSelected     = false;
        currentBowlerIndex = -1;
        currentBowlerName  = "";
    }

    public void setCurrentOverBowler(int bowlerIndex, String bowlerName) {
        this.currentBowlerIndex = bowlerIndex;
        this.currentBowlerName  = bowlerName != null ? bowlerName : "";
        this.bowlerSelected     = true;
        if (currentOver != null) {
            currentOver.setBowlerIndex(bowlerIndex);
            currentOver.setBowlerName(bowlerName);
        }
    }

    public void completeCurrentOver() {
        if (currentOver != null) {
            currentOver.setBowlerIndex(currentBowlerIndex);
            currentOver.setBowlerName(currentBowlerName);

            if (!currentBowlerName.isEmpty()) {
                addToMap(bowlerOversMap, currentBowlerName, 1);
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
        // Track bowler stats
        if (!currentBowlerName.isEmpty()) {
            addToMap(bowlerRunsMap, currentBowlerName, runs);
            addToMap(bowlerBallsMap, currentBowlerName, 1);
        }
        if (!singleBatsmanMode && runs % 2 == 1) swapStrike();
    }

    public void recordWide() {
        currentOver.addBall(Ball.wide());
        totalRuns += 1;
        // Wide = 1 run conceded, no ball counted
        if (!currentBowlerName.isEmpty()) {
            addToMap(bowlerRunsMap, currentBowlerName, 1);
        }
    }

    public void recordNoBall() {
        currentOver.addBall(Ball.noBall());
        totalRuns += 1;
        // No-ball = 1 run conceded, no ball counted
        if (!currentBowlerName.isEmpty()) {
            addToMap(bowlerRunsMap, currentBowlerName, 1);
        }
    }

    public void recordWicket(Player outPlayer) {
        currentOver.addBall(Ball.wicket());
        totalWickets    += 1;
        totalValidBalls += 1;
        outPlayer.dismiss("out");
        // Track bowler wicket + ball
        if (!currentBowlerName.isEmpty()) {
            addToMap(bowlerWicketsMap, currentBowlerName, 1);
            addToMap(bowlerBallsMap, currentBowlerName, 1);
        }
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
                if (!currentBowlerName.isEmpty()) {
                    subtractFromMap(bowlerRunsMap,  currentBowlerName, removed.getRuns());
                    subtractFromMap(bowlerBallsMap, currentBowlerName, 1);
                }
                if (!singleBatsmanMode && removed.getRuns() % 2 == 1) swapStrike();
                break;
            case WIDE:
                totalRuns -= 1;
                if (!currentBowlerName.isEmpty())
                    subtractFromMap(bowlerRunsMap, currentBowlerName, 1);
                break;
            case NO_BALL:
                totalRuns -= 1;
                if (!currentBowlerName.isEmpty())
                    subtractFromMap(bowlerRunsMap, currentBowlerName, 1);
                break;
            case WICKET:
                totalValidBalls -= 1;
                totalWickets    -= 1;
                if (!currentBowlerName.isEmpty()) {
                    subtractFromMap(bowlerWicketsMap, currentBowlerName, 1);
                    subtractFromMap(bowlerBallsMap,   currentBowlerName, 1);
                }
                break;
        }
        return removed;
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

    public int    getRunsNeeded(int target)      { return Math.max(0, target - totalRuns); }
    public int    getBallsRemaining(int maxOvers) { return (maxOvers * 6) - totalValidBalls; }
    public String getScoreString()               { return totalRuns + "/" + totalWickets; }

    public List<Over> getAllOvers() {
        List<Over> all = new ArrayList<>(completedOvers);
        if (currentOver != null && !currentOver.getBalls().isEmpty()) all.add(currentOver);
        return all;
    }

    /**
     * Returns a list of BowlerStat objects for every bowler who has
     * bowled at least one ball this innings. Used by StatsActivity.
     */
    public List<BowlerStat> getBowlerStats() {
        List<BowlerStat> stats = new ArrayList<>();
        // Collect all bowler names from any of the tracking maps
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

    /** Formatted bowler summary for the over-history panel. */
    public String getBowlerSummary() {
        if (bowlerOversMap.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : bowlerOversMap.entrySet()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(e.getKey()).append(": ")
              .append(e.getValue())
              .append(e.getValue() == 1 ? " over" : " overs");
        }
        return sb.toString();
    }

    // ─── Map helpers ──────────────────────────────────────────────────────────

    private void addToMap(Map<String, Integer> map, String key, int delta) {
        map.put(key, (map.containsKey(key) ? map.get(key) : 0) + delta);
    }

    private void subtractFromMap(Map<String, Integer> map, String key, int delta) {
        int cur = map.containsKey(key) ? map.get(key) : 0;
        map.put(key, Math.max(0, cur - delta));
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
    public Map<String, Integer> getBowlerOversMap()  { return bowlerOversMap; }
    public void setBowlerOversMap(Map<String, Integer> v)   { bowlerOversMap   = v != null ? v : new HashMap<>(); }
    public Map<String, Integer> getBowlerRunsMap()   { return bowlerRunsMap; }
    public void setBowlerRunsMap(Map<String, Integer> v)    { bowlerRunsMap    = v != null ? v : new HashMap<>(); }
    public Map<String, Integer> getBowlerWicketsMap(){ return bowlerWicketsMap; }
    public void setBowlerWicketsMap(Map<String, Integer> v) { bowlerWicketsMap = v != null ? v : new HashMap<>(); }
    public Map<String, Integer> getBowlerBallsMap()  { return bowlerBallsMap; }
    public void setBowlerBallsMap(Map<String, Integer> v)   { bowlerBallsMap   = v != null ? v : new HashMap<>(); }
}
