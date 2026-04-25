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
 *
 * BUG FIX — all-out wicket threshold:
 *
 * Two-batsman mode (standard cricket):
 *   Two players are always on the field together (striker + non-striker).
 *   The last batsman cannot bat alone, so the innings ends when
 *   (totalPlayers - 1) wickets have fallen.
 *   e.g. 11 players → all out after 10 wickets.
 *
 * Single-batsman mode:
 *   Only the striker is on the field at any time — there is no partner.
 *   Every player bats individually, so the innings ends only when ALL
 *   players have been dismissed.
 *   e.g. 11 players → all out after 11 wickets.
 *   e.g.  5 players → all out after  5 wickets.
 *
 * The fix is in deliverWicket(): the threshold is now computed by
 * allOutThreshold(batters) which returns the correct value per mode.
 *
 * The same fix is applied to endMatch() where wicketsLeft is calculated
 * for the result description string.
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
        Innings innings = match.getCurrentInningsData();
        Player  striker = getStriker();
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
     * All-out threshold (THE BUG FIX):
     *   Two-batsman mode → innings ends at (N - 1) wickets
     *   Single-batsman mode → innings ends at N wickets
     *
     * @param newBatsmanIndex index of the incoming batsman in the batting list,
     *                        ignored when the team is all out
     */
    public MatchState deliverWicket(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        innings.recordWicket(striker);

        // ── All-out check (FIXED) ──────────────────────────────────────
        int threshold = allOutThreshold(batters);
        if (innings.getTotalWickets() >= threshold) {
            // Everyone is out — end the innings immediately
            // Do NOT try to bring in a new batsman
            return endInnings(innings);
        }

        // ── Bring in the next batsman ──────────────────────────────────
        innings.setStrikerIndex(newBatsmanIndex);
        if (newBatsmanIndex < batters.size()) {
            batters.get(newBatsmanIndex).setHasNotBatted(false);
        }
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) {
            innings.setNextBatsmanIndex(newBatsmanIndex + 1);
        }

        return checkAfterValidBall(innings);
    }

    /**
     * Returns the number of wickets at which the batting team is all out.
     *
     * Two-batsman mode : N - 1
     *   The last remaining batsman has no partner so cannot continue.
     *
     * Single-batsman mode : N
     *   Each of the N players bats alone; all N can and must be dismissed
     *   before the innings ends via wickets.
     */
    private int allOutThreshold(List<Player> batters) {
        if (match.isSingleBatsmanMode()) {
            return batters.size();       // e.g. 11 players → 11 wickets
        } else {
            return batters.size() - 1;  // e.g. 11 players → 10 wickets
        }
    }

    /** Undoes the last delivery, reversing all stat changes. */
    public boolean undoLastBall() {
        Innings innings     = match.getCurrentInningsData();
        Over    currentOver = innings.getCurrentOver();

        if (currentOver.getBalls().isEmpty()) return false;

        Ball lastBall = currentOver.getBalls().get(currentOver.getBalls().size() - 1);

        if (lastBall.getType() == Ball.BallType.WICKET) {
            int    strikerIdx = innings.getStrikerIndex();
            Player striker    = match.getCurrentBattingPlayers().get(strikerIdx);
            striker.setOut(false);
            striker.setDismissalInfo("");
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
            innings.completeCurrentOver();
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
            Innings second = new Innings(2, match.isSingleBatsmanMode());
            match.setSecondInnings(second);
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

        int          target     = match.getTarget();
        int          runsScored = secondInnings.getTotalRuns();
        List<Player> chasers    = match.getCurrentBattingPlayers();

        // wicketsLeft uses the same mode-aware threshold (FIXED)
        int threshold   = allOutThreshold(chasers);
        int wicketsLeft = threshold - secondInnings.getTotalWickets();

        String bat1Name = match.getBattingFirstTeam().equals("home")
                ? match.getHomeTeamName() : match.getAwayTeamName();
        String bat2Name = match.getBattingFirstTeam().equals("home")
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
        if (match.isSingleBatsmanMode()) return null;
        Innings      innings = match.getCurrentInningsData();
        List<Player> batters = match.getCurrentBattingPlayers();
        int          idx     = innings.getNonStrikerIndex();
        return (idx >= 0 && idx < batters.size()) ? batters.get(idx) : null;
    }

    /**
     * Returns players available to come in after a wicket.
     *
     * Two-batsman mode: excludes striker AND non-striker.
     * Single-batsman mode: excludes only the striker (no non-striker).
     */
    public List<Player> getAvailableBatsmen() {
        Innings      innings       = match.getCurrentInningsData();
        List<Player> batters       = match.getCurrentBattingPlayers();
        List<Player> available     = new ArrayList<>();
        int          strikerIdx    = innings.getStrikerIndex();
        int          nonStrikerIdx = innings.getNonStrikerIndex(); // -1 in single mode

        for (int i = 0; i < batters.size(); i++) {
            Player  p          = batters.get(i);
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
