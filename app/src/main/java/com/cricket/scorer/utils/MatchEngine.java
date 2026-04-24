package com.cricket.scorer.utils;

import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * MatchEngine.java
 * Central business-logic controller for a cricket match.
 *
 * CHANGES for batting mode:
 *
 * Single-batsman mode (match.isSingleBatsmanMode() == true):
 *   - Only strikerIndex is valid; nonStrikerIndex == -1
 *   - Strike NEVER rotates (handled inside Innings.recordNormalBall)
 *   - At end of over: no rotation (Innings.completeCurrentOver skips swap)
 *   - Wicket: incoming batsman takes striker's slot; no non-striker to worry about
 *   - getAvailableBatsmen(): excludes only striker (not non-striker)
 *
 * Two-batsman mode (standard, match.isSingleBatsmanMode() == false):
 *   - Unchanged behaviour from before
 */
public class MatchEngine {

    public enum MatchState {
        BALL_RECORDED,
        OVER_COMPLETE,
        INNINGS_COMPLETE,
        MATCH_COMPLETE
    }

    private Match match;

    public MatchEngine(Match match) {
        this.match = match;
    }

    // ─── Delivery processing ──────────────────────────────────────────────────

    public MatchState deliverNormalBall(int runs) {
        Innings innings  = match.getCurrentInningsData();
        Player  striker  = getStriker();
        innings.recordNormalBall(runs, striker);
        return checkAfterValidBall(innings);
    }

    public MatchState deliverWide() {
        match.getCurrentInningsData().recordWide();
        return MatchState.BALL_RECORDED;
    }

    public MatchState deliverNoBall() {
        match.getCurrentInningsData().recordNoBall();
        return MatchState.BALL_RECORDED;
    }

    /**
     * Records a wicket and brings in the next batsman.
     *
     * Two-batsman mode:
     *   The striker is out; newBatsmanIndex fills the striker's slot.
     *   The non-striker remains unchanged.
     *
     * Single-batsman mode:
     *   The striker is out; newBatsmanIndex fills the striker's slot.
     *   (Same logic — just no non-striker to worry about.)
     */
    public MatchState deliverWicket(int newBatsmanIndex) {
        Innings        innings  = match.getCurrentInningsData();
        Player         striker  = getStriker();
        List<Player>   batters  = match.getCurrentBattingPlayers();

        innings.recordWicket(striker);

        // Put the incoming batsman at the striker's end
        innings.setStrikerIndex(newBatsmanIndex);
        if (newBatsmanIndex < batters.size()) {
            batters.get(newBatsmanIndex).setHasNotBatted(false);
        }

        // Advance the "next to come in" pointer past the chosen player
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) {
            innings.setNextBatsmanIndex(newBatsmanIndex + 1);
        }

        // All out?
        if (innings.getTotalWickets() >= batters.size() - 1) {
            // All out = (total players - 1) wickets
            // e.g. 11 players → out after 10 wickets
            // single-batsman: every player gets to bat (no partner needed)
            return endInnings(innings);
        }

        return checkAfterValidBall(innings);
    }

    /** Undoes the last delivery, reversing all stat changes. */
    public boolean undoLastBall() {
        Innings innings     = match.getCurrentInningsData();
        Over    currentOver = innings.getCurrentOver();

        if (currentOver.getBalls().isEmpty()) return false;

        Ball lastBall = currentOver.getBalls().get(currentOver.getBalls().size() - 1);

        if (lastBall.getType() == Ball.BallType.WICKET) {
            // Reverse the dismissed player's status
            int currentStrikerIdx = innings.getStrikerIndex();
            Player currentStriker = match.getCurrentBattingPlayers().get(currentStrikerIdx);
            currentStriker.setOut(false);
            currentStriker.setDismissalInfo("");
            if (innings.getNextBatsmanIndex() > innings.getNonStrikerIndex() + 1) {
                innings.setNextBatsmanIndex(innings.getNextBatsmanIndex() - 1);
            }
        }

        innings.undoLastBall(getStriker());
        return true;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private MatchState checkAfterValidBall(Innings innings) {
        // Chase win check (2nd innings)
        if (match.getCurrentInnings() == 2
                && innings.getTotalRuns() >= match.getTarget()) {
            return endMatch(innings);
        }

        // Over complete?
        if (innings.getCurrentOver().isComplete()) {
            innings.completeCurrentOver(); // swap only in two-batsman mode
            int oversCompleted = innings.getCompletedOvers().size();
            if (oversCompleted >= match.getMaxOvers()) {
                return endInnings(innings);
            }
            return MatchState.OVER_COMPLETE;
        }

        return MatchState.BALL_RECORDED;
    }

    private MatchState endInnings(Innings innings) {
        innings.setComplete(true);

        if (match.getCurrentInnings() == 1) {
            match.setCurrentInnings(2);

            // Create 2nd innings with the same batting mode
            Innings second = new Innings(2, match.isSingleBatsmanMode());
            match.setSecondInnings(second);

            // Reset the chasing team's player stats
            for (Player p : match.getCurrentBattingPlayers()) {
                p.resetForNewInnings();
            }

            return MatchState.INNINGS_COMPLETE;
        }
        return endMatch(innings);
    }

    private MatchState endMatch(Innings secondInnings) {
        secondInnings.setComplete(true);
        match.setMatchCompleted(true);

        int    target       = match.getTarget();
        int    runsScored   = secondInnings.getTotalRuns();
        int    wicketsLeft  = match.getCurrentBattingPlayers().size()
                              - 1 - secondInnings.getTotalWickets();
        String bat1Name     = match.getBattingFirstTeam().equals("home")
                              ? match.getHomeTeamName() : match.getAwayTeamName();
        String bat2Name     = match.getBattingFirstTeam().equals("home")
                              ? match.getAwayTeamName() : match.getHomeTeamName();

        if (runsScored >= target) {
            match.setWinnerTeam(match.getBattingFirstTeam().equals("home") ? "away" : "home");
            match.setResultDescription(bat2Name + " won by "
                    + Math.max(0, wicketsLeft)
                    + (wicketsLeft == 1 ? " wicket" : " wickets"));
        } else {
            int margin = match.getFirstInnings().getTotalRuns() - runsScored;
            if (margin > 0) {
                match.setWinnerTeam(match.getBattingFirstTeam());
                match.setResultDescription(bat1Name + " won by "
                        + margin + (margin == 1 ? " run" : " runs"));
            } else {
                match.setWinnerTeam("tie");
                match.setResultDescription("Match tied! Both teams scored "
                        + match.getFirstInnings().getTotalRuns() + " runs");
            }
        }
        return MatchState.MATCH_COMPLETE;
    }

    // ─── Utility getters ──────────────────────────────────────────────────────

    public Player getStriker() {
        Innings      innings = match.getCurrentInningsData();
        List<Player> batters = match.getCurrentBattingPlayers();
        return batters.get(innings.getStrikerIndex());
    }

    public Player getNonStriker() {
        if (match.isSingleBatsmanMode()) return null; // no non-striker
        Innings      innings = match.getCurrentInningsData();
        List<Player> batters = match.getCurrentBattingPlayers();
        int          idx     = innings.getNonStrikerIndex();
        return (idx >= 0 && idx < batters.size()) ? batters.get(idx) : null;
    }

    /**
     * Returns players who can come in as the next batsman after a wicket.
     *
     * Two-batsman mode: excludes striker AND non-striker (both at crease).
     * Single-batsman mode: excludes only the striker (no non-striker).
     */
    public List<Player> getAvailableBatsmen() {
        Innings      innings        = match.getCurrentInningsData();
        List<Player> batters        = match.getCurrentBattingPlayers();
        List<Player> available      = new ArrayList<>();
        int          strikerIdx     = innings.getStrikerIndex();
        int          nonStrikerIdx  = innings.getNonStrikerIndex(); // -1 in single mode

        for (int i = 0; i < batters.size(); i++) {
            Player p = batters.get(i);
            boolean isAtCrease = (i == strikerIdx) || (i == nonStrikerIdx);
            if (!isAtCrease && !p.isOut()) {
                available.add(p);
            }
        }
        return available;
    }

    public int getNextBatsmanIndex() {
        return match.getCurrentInningsData().getNextBatsmanIndex();
    }

    public Match getMatch() { return match; }
}
