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
 * CHANGE: endInnings() no longer auto-marks the 2nd innings openers as
 * hasNotBatted=false. That is now handled by InningsActivity's opener
 * selection dialog, which fires at the start of every innings before
 * any ball is bowled. This means ALL players enter innings 2 with
 * hasNotBatted=true, and the dialog sets the chosen openers to false.
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
        striker.setHasNotBatted(false); // safety: mark as having batted
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
     * Sets dismissed striker's hasNotBatted=false before dismissal
     * so they always appear in the batting table even if they scored 0.
     */
    public MatchState deliverWicket(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        striker.setHasNotBatted(false); // ensure dismissed player shows in table
        innings.recordWicket(striker);

        int threshold = allOutThreshold(batters);
        if (innings.getTotalWickets() >= threshold) {
            return endInnings(innings);
        }

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
     * Wicket undo: restores original striker, sends incoming batsman back.
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
                incomingBatter.setHasNotBatted(true); // back to pavilion
                if (innings.getNextBatsmanIndex() > 1) {
                    innings.setNextBatsmanIndex(innings.getNextBatsmanIndex() - 1);
                }
            }

            innings.undoLastBall(getStriker());
            return true;
        }

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
     * Ends the current innings and sets up innings 2 if needed.
     *
     * CHANGE: No longer auto-marks openers as hasNotBatted=false.
     * InningsActivity.showOpenerSelectionDialog() handles that when
     * the new innings screen opens.
     */
    private MatchState endInnings(Innings innings) {
        innings.setComplete(true);

        if (match.getCurrentInnings() == 1) {
            match.setCurrentInnings(2);

            Innings second = new Innings(2, match.isSingleBatsmanMode());
            match.setSecondInnings(second);

            // Reset all chasing team players — everyone hasNotBatted=true.
            // The opener selection dialog in InningsActivity will set the
            // chosen openers to hasNotBatted=false before the first ball.
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
