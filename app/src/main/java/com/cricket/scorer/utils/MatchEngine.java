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
 * BUG FIX — second innings opener hasNotBatted flag:
 *
 * Root cause:
 *   In endInnings() → resetForNewInnings() sets hasNotBatted = true for ALL
 *   players in the chasing team. The second innings openers were never marked
 *   hasNotBatted = false (that was only done in SetupActivity for innings 1).
 *
 *   So when the 1st batsman of innings 2 gets out, their state is:
 *     isOut = true, hasNotBatted = true
 *   refreshBattingTable checks:  if (hasNotBatted && !isAtCrease) → skip
 *   Since the dismissed player is no longer at the crease, they are skipped
 *   entirely and don't appear struck-through in the table.
 *
 * Fix (two places):
 *   1. endInnings(): after resetForNewInnings(), explicitly mark the innings-2
 *      openers (strikerIndex=0 and nonStrikerIndex=1 in two mode, or just 0 in
 *      single mode) as hasNotBatted = false — exactly as SetupActivity does
 *      for innings 1.
 *
 *   2. deliverWicket(): set the dismissed striker's hasNotBatted = false
 *      BEFORE calling recordWicket(). This is the belt-and-suspenders guard:
 *      even if a batsman somehow reaches the crease with hasNotBatted still
 *      true (edge case), dismissal guarantees the flag is cleared so they
 *      always render in the table.
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
        // Belt-and-suspenders: mark striker as having batted on first ball faced
        striker.setHasNotBatted(false);
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
     * FIX: Sets dismissed striker's hasNotBatted = false BEFORE recording
     * the wicket so they always appear in the batting table as struck-through,
     * even if they faced no balls (e.g. run out first ball).
     */
    public MatchState deliverWicket(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        // ── FIX: mark striker as having batted before dismissal ────────
        // This ensures their row is never skipped by the hasNotBatted check
        // in refreshBattingTable, even if they faced zero balls.
        striker.setHasNotBatted(false);

        innings.recordWicket(striker);

        // All-out check
        int threshold = allOutThreshold(batters);
        if (innings.getTotalWickets() >= threshold) {
            return endInnings(innings);
        }

        // Bring in the new batsman
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
     * Undoes the last delivery.
     *
     * Wicket undo:
     *   1. Find the dismissed player (isOut==true, not the non-striker).
     *   2. Restore strikerIndex to them.
     *   3. Clear their isOut and dismissalInfo.
     *   4. Send the incoming batsman back (hasNotBatted = true).
     *   5. Decrement nextBatsmanIndex.
     *   6. Remove the ball and decrement wicket + validBall counters.
     */
    public boolean undoLastBall() {
        Innings      innings     = match.getCurrentInningsData();
        Over         currentOver = innings.getCurrentOver();
        List<Player> batters     = match.getCurrentBattingPlayers();

        if (currentOver.getBalls().isEmpty()) return false;

        Ball lastBall = currentOver.getBalls().get(currentOver.getBalls().size() - 1);

        if (lastBall.getType() == Ball.BallType.WICKET) {
            int    incomingIndex  = innings.getStrikerIndex();
            Player incomingBatter = batters.get(incomingIndex);
            int    nonStrikerIdx  = innings.getNonStrikerIndex();

            // Find the originally dismissed player
            int    dismissedIndex  = -1;
            Player dismissedPlayer = null;
            for (int i = 0; i < batters.size(); i++) {
                if (batters.get(i).isOut() && i != nonStrikerIdx) {
                    dismissedIndex  = i;
                    dismissedPlayer = batters.get(i);
                    break;
                }
            }

            if (dismissedPlayer != null) {
                innings.setStrikerIndex(dismissedIndex);
                dismissedPlayer.setOut(false);
                dismissedPlayer.setDismissalInfo("");
                // The dismissed player was marked hasNotBatted=false in deliverWicket;
                // keep it false — they DID take the crease even if they scored 0.

                // Send incoming batsman back to pavilion
                incomingBatter.setHasNotBatted(true);

                if (innings.getNextBatsmanIndex() > 1) {
                    innings.setNextBatsmanIndex(innings.getNextBatsmanIndex() - 1);
                }
            }

            innings.undoLastBall(getStriker());
            return true;
        }

        // Normal / Wide / No-ball undo
        innings.undoLastBall(getStriker());
        return true;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private MatchState checkAfterValidBall(Innings innings) {
        if (match.getCurrentInnings() == 2
                && innings.getTotalRuns() >= match.getTarget()) {
            return endMatch(innings);
        }
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

    /**
     * Ends the current innings.
     *
     * FIX: After resetForNewInnings(), marks the 2nd innings openers as
     * hasNotBatted = false — identical to what SetupActivity does for innings 1.
     * Without this, the first batsman of innings 2 would have hasNotBatted = true
     * even after being dismissed, causing them to be skipped in the batting table.
     */
    private MatchState endInnings(Innings innings) {
        innings.setComplete(true);

        if (match.getCurrentInnings() == 1) {
            match.setCurrentInnings(2);

            Innings second = new Innings(2, match.isSingleBatsmanMode());
            match.setSecondInnings(second);

            // Reset all chasing team players for the new innings
            List<Player> chasers = match.getCurrentBattingPlayers();
            for (Player p : chasers) p.resetForNewInnings();

            // ── FIX: mark the 2nd innings openers as having taken the field ──
            // This mirrors what SetupActivity does for innings 1 openers and
            // ensures that if they get out before facing a ball, they still
            // appear struck-through in the batting table (not skipped).
            if (!chasers.isEmpty()) {
                chasers.get(0).setHasNotBatted(false); // striker
            }
            if (!match.isSingleBatsmanMode() && chasers.size() > 1) {
                chasers.get(1).setHasNotBatted(false); // non-striker (two mode only)
            }
            // ──────────────────────────────────────────────────────────────────

            return MatchState.INNINGS_COMPLETE;
        }
        return endMatch(innings);
    }

    private MatchState endMatch(Innings secondInnings) {
        secondInnings.setComplete(true);
        match.setMatchCompleted(true);

        int          target      = match.getTarget();
        int          runsScored  = secondInnings.getTotalRuns();
        List<Player> chasers     = match.getCurrentBattingPlayers();
        int          threshold   = allOutThreshold(chasers);
        int          wicketsLeft = threshold - secondInnings.getTotalWickets();

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

    private int allOutThreshold(List<Player> batters) {
        return match.isSingleBatsmanMode() ? batters.size() : batters.size() - 1;
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

    public List<Player> getAvailableBatsmen() {
        Innings      innings       = match.getCurrentInningsData();
        List<Player> batters       = match.getCurrentBattingPlayers();
        List<Player> available     = new ArrayList<>();
        int          strikerIdx    = innings.getStrikerIndex();
        int          nonStrikerIdx = innings.getNonStrikerIndex();

        for (int i = 0; i < batters.size(); i++) {
            Player  p          = batters.get(i);
            boolean isAtCrease = (i == strikerIdx) || (i == nonStrikerIdx);
            if (!isAtCrease && !p.isOut()) available.add(p);
        }
        return available;
    }

    public int getNextBatsmanIndex() {
        return match.getCurrentInningsData().getNextBatsmanIndex();
    }

    public Match getMatch() { return match; }
}
