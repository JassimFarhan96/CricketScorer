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
 * BUG FIX — undoLastBall() wicket case:
 *
 * BEFORE (broken):
 *   When a wicket was undone, the dismissed player's isOut flag was cleared
 *   but the strikerIndex was NOT restored. The incoming batsman who had
 *   just walked in remained as the striker, while the originally-dismissed
 *   batsman was left dangling with no index pointing to them.
 *
 * AFTER (fixed):
 *   On a wicket undo we must:
 *     1. Find who the ORIGINAL striker was before the wicket — this is
 *        the player who was set as out (their isOut == true at undo time).
 *        We locate them by scanning the batting list for the dismissed player.
 *     2. Restore strikerIndex back to that original striker's list index.
 *     3. Mark the incoming batsman (currently at strikerIndex) as
 *        hasNotBatted = true again and clear their in-play status,
 *        so they are available to come in again later.
 *     4. Decrement nextBatsmanIndex so the pointer is consistent.
 *     5. Restore the dismissed player's isOut = false and dismissalInfo = "".
 *
 * This logic is the same for both single and two-batsman modes because
 * in both cases the striker is the player who was dismissed.
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
     * strikerIndex is updated to point at the incoming batsman.
     */
    public MatchState deliverWicket(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        // Remember which index was the striker BEFORE the wicket
        int dismissedIndex = innings.getStrikerIndex();

        innings.recordWicket(striker); // marks striker.isOut = true, increments wickets

        // All-out check
        int threshold = allOutThreshold(batters);
        if (innings.getTotalWickets() >= threshold) {
            return endInnings(innings);
        }

        // Bring in the new batsman at the striker's end
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
     * Undoes the last delivery, reversing all stat changes.
     *
     * WICKET UNDO FIX:
     *   Scans the batting list to find the currently-out player who was
     *   the original striker (their isOut == true and they were not the
     *   non-striker). Restores:
     *     - strikerIndex  → back to the dismissed player
     *     - dismissed.isOut → false, dismissalInfo → ""
     *     - incoming batsman.hasNotBatted → true (they go back to pavilion)
     *     - nextBatsmanIndex → decremented by 1
     *     - innings.totalWickets → decremented (done inside Innings.undoLastBall)
     *
     * @return true if a ball was successfully undone, false if nothing to undo
     */
    public boolean undoLastBall() {
        Innings      innings     = match.getCurrentInningsData();
        Over         currentOver = innings.getCurrentOver();
        List<Player> batters     = match.getCurrentBattingPlayers();

        if (currentOver.getBalls().isEmpty()) return false;

        Ball lastBall = currentOver.getBalls().get(currentOver.getBalls().size() - 1);

        if (lastBall.getType() == Ball.BallType.WICKET) {
            // ── Wicket undo (THE FIX) ──────────────────────────────────

            // Step 1: The current strikerIndex points to the incoming batsman
            //         who just walked in. We need to send them back.
            int    incomingIndex  = innings.getStrikerIndex();
            Player incomingBatter = batters.get(incomingIndex);

            // Step 2: Find the originally-dismissed player.
            //         They are the one with isOut == true who is NOT the
            //         non-striker (non-striker is unchanged by a wicket).
            int    dismissedIndex = -1;
            Player dismissedPlayer = null;
            int    nonStrikerIdx  = innings.getNonStrikerIndex(); // -1 in single mode

            for (int i = 0; i < batters.size(); i++) {
                Player p = batters.get(i);
                if (p.isOut() && i != nonStrikerIdx) {
                    // This is the dismissed striker — the most recently dismissed
                    // player that is not the non-striker
                    dismissedIndex  = i;
                    dismissedPlayer = p;
                    // Don't break — take the last one found in case of multiple
                    // wickets (though undo only handles one at a time)
                    break;
                }
            }

            if (dismissedPlayer != null) {
                // Step 3: Restore the original striker
                innings.setStrikerIndex(dismissedIndex);
                dismissedPlayer.setOut(false);
                dismissedPlayer.setDismissalInfo("");

                // Step 4: Send the incoming batsman back to the pavilion
                incomingBatter.setHasNotBatted(true);

                // Step 5: Decrement the next-batsman pointer
                if (innings.getNextBatsmanIndex() > 1) {
                    innings.setNextBatsmanIndex(innings.getNextBatsmanIndex() - 1);
                }
            }

            // Step 6: Remove the ball and decrement wicket + validBall counters
            innings.undoLastBall(getStriker()); // getStriker() now returns dismissed player
            return true;
        }

        // Normal / Wide / No-ball undo — unchanged
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

    private MatchState endInnings(Innings innings) {
        innings.setComplete(true);
        if (match.getCurrentInnings() == 1) {
            match.setCurrentInnings(2);
            Innings second = new Innings(2, match.isSingleBatsmanMode());
            match.setSecondInnings(second);
            for (Player p : match.getCurrentBattingPlayers()) p.resetForNewInnings();
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
        int          threshold  = allOutThreshold(chasers);
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

    /**
     * All-out threshold:
     *   Single-batsman mode → N wickets (every player dismissed individually)
     *   Two-batsman mode    → N-1 wickets (last batsman has no partner)
     */
    private int allOutThreshold(List<Player> batters) {
        return match.isSingleBatsmanMode() ? batters.size() : batters.size() - 1;
    }

    // ─── Utility getters ──────────────────────────────────────────────────────

    /** Returns the Player currently on strike. */
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
     * Single mode: excludes only striker.
     * Two mode: excludes striker AND non-striker.
     */
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
