package com.cricket.scorer.models;

import java.io.Serializable;

/**
 * Player.java
 *
 * CHANGE: Added retiredHurt flag.
 *   retiredHurt = true  → player left field due to injury; NOT dismissed;
 *                         can return to bat later if they recover.
 *   isOut       = true  → player is fully dismissed (bowled, caught, etc.)
 *
 * These two flags are mutually exclusive:
 *   - Retired hurt:  retiredHurt=true,  isOut=false, hasNotBatted=false
 *   - Dismissed:     retiredHurt=false, isOut=true,  hasNotBatted=false
 *   - Yet to bat:    retiredHurt=false, isOut=false, hasNotBatted=true
 *   - At crease:     retiredHurt=false, isOut=false, hasNotBatted=false
 */
public class Player implements Serializable {

    private String  name;
    private int     runsScored;
    private int     ballsFaced;
    private int     fours;
    private int     sixes;
    private boolean isOut;
    private boolean hasNotBatted;
    private boolean retiredHurt;     // NEW
    private String  dismissalInfo;

    public Player(String name) {
        this.name         = name;
        this.runsScored   = 0;
        this.ballsFaced   = 0;
        this.fours        = 0;
        this.sixes        = 0;
        this.isOut        = false;
        this.hasNotBatted = true;
        this.retiredHurt  = false;
        this.dismissalInfo = "";
    }

    // ─── Batting stat updates ─────────────────────────────────────────────────

    public void addRuns(int runs) {
        this.runsScored += runs;
        this.ballsFaced++;
        if (runs == 4) this.fours++;
        if (runs == 6) this.sixes++;
    }

    public void addBallFaced() { this.ballsFaced++; }

    public void dismiss(String info) {
        this.isOut        = true;
        this.retiredHurt  = false;
        this.dismissalInfo = info;
    }

    /** Mark as retired hurt — leaves the field but is NOT dismissed. */
    public void retireHurt() {
        this.retiredHurt  = true;
        this.isOut        = false;
        this.dismissalInfo = "retired hurt";
    }

    public void resetForNewInnings() {
        this.runsScored    = 0;
        this.ballsFaced    = 0;
        this.fours         = 0;
        this.sixes         = 0;
        this.isOut         = false;
        this.hasNotBatted  = true;
        this.retiredHurt   = false;
        this.dismissalInfo = "";
    }

    // ─── Computed stats ───────────────────────────────────────────────────────

    public float getStrikeRate() {
        if (ballsFaced == 0) return 0f;
        return ((float) runsScored / ballsFaced) * 100f;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String  getName()                    { return name; }
    public void    setName(String v)            { name = v; }
    public int     getRunsScored()              { return runsScored; }
    public void    setRunsScored(int v)         { runsScored = v; }
    public int     getBallsFaced()              { return ballsFaced; }
    public void    setBallsFaced(int v)         { ballsFaced = v; }
    public int     getFours()                   { return fours; }
    public void    setFours(int v)              { fours = v; }
    public int     getSixes()                   { return sixes; }
    public void    setSixes(int v)              { sixes = v; }
    public boolean isOut()                      { return isOut; }
    public void    setOut(boolean v)            { isOut = v; }
    public boolean isHasNotBatted()             { return hasNotBatted; }
    public void    setHasNotBatted(boolean v)   { hasNotBatted = v; }
    public boolean isRetiredHurt()              { return retiredHurt; }
    public void    setRetiredHurt(boolean v)    { retiredHurt = v; }
    public String  getDismissalInfo()           { return dismissalInfo; }
    public void    setDismissalInfo(String v)   { dismissalInfo = v != null ? v : ""; }
}
