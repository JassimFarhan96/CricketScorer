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
 * CHANGE — Retired Hurt support:
 *
 * deliverRetiredHurt(newBatsmanIndex):
 *   - Marks the current striker as retiredHurt (not a dismissal, no wicket)
 *   - Brings in newBatsmanIndex as the new striker
 *   - Does NOT trigger endInnings
 *   - Returns BALL_RECORDED (no ball was bowled — it's a substitution event)
 *
 * All-out threshold now EXCLUDES retired-hurt players:
 *   Active players = total - dismissed - retiredHurt
 *   All-out when active == 1 (two-bat) or == 0 (single-bat)
 *   i.e. threshold = (total - retiredHurtCount) - (singleMode ? 0 : 1)
 *
 * getRetiredHurtPlayers():
 *   Returns the list of players currently flagged retiredHurt, in the
 *   order they retired. InningsActivity uses this list after the last
 *   active player is dismissed to prompt each one "Return to bat?".
 *
 * returnFromRetiredHurt(playerIndex, returnsToPlay):
 *   Called after the user answers the prompt.
 *   If returnsToPlay=true:  clear retiredHurt flag, set as new striker
 *   If returnsToPlay=false: mark as dismissed (retired out)
 *   Returns INNINGS_COMPLETE / MATCH_COMPLETE if the innings ends, else
 *   BALL_RECORDED so play continues.
 */
public class MatchEngine {

    public enum MatchState {
        BALL_RECORDED,
        OVER_COMPLETE,
        INNINGS_COMPLETE,
        MATCH_COMPLETE,
        /** All active batsmen done; retired-hurt players need to be prompted */
        PROMPT_RETIRED_HURT
    }

    private Match match;

    public MatchEngine(Match match) { this.match = match; }

    // ─── Delivery processing ──────────────────────────────────────────────────

    public MatchState deliverNormalBall(int runs) {
        Innings innings = match.getCurrentInningsData();
        Player  striker = getStriker();
        striker.setHasNotBatted(false);
        innings.recordNormalBall(runs, striker);
        return checkAfterValidBall(innings);
    }

    public MatchState deliverWide()   { match.getCurrentInningsData().recordWide();   return MatchState.BALL_RECORDED; }
    public MatchState deliverNoBall() { match.getCurrentInningsData().recordNoBall(); return MatchState.BALL_RECORDED; }

    public MatchState deliverWicket(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        striker.setHasNotBatted(false);
        innings.recordWicket(striker);

        // All-out check: threshold accounts for retired-hurt players
        if (isAllOut(innings, batters)) {
            return handleAllOut(innings, batters);
        }

        innings.setStrikerIndex(newBatsmanIndex);
        if (newBatsmanIndex < batters.size()) batters.get(newBatsmanIndex).setHasNotBatted(false);
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) innings.setNextBatsmanIndex(newBatsmanIndex + 1);

        return checkAfterValidBall(innings);
    }

    /**
     * Retires the current striker hurt.
     *
     * No wicket is recorded. No ball is added to the over.
     * The retiring player is flagged retiredHurt and leaves the field.
     * newBatsmanIndex comes in as the new striker.
     *
     * Returns BALL_RECORDED — the over state is unchanged.
     */
    public MatchState deliverRetiredHurt(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        striker.setHasNotBatted(false);
        striker.retireHurt();   // flag retiredHurt, NOT dismissed

        // Bring in the replacement
        innings.setStrikerIndex(newBatsmanIndex);
        if (newBatsmanIndex < batters.size()) batters.get(newBatsmanIndex).setHasNotBatted(false);
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) innings.setNextBatsmanIndex(newBatsmanIndex + 1);

        return MatchState.BALL_RECORDED;
    }

    /**
     * Called after a wicket (or after all active players are done) to handle
     * the return-to-bat prompt for each retired-hurt player in order.
     *
     * returnsToPlay=true  → clear retiredHurt, they become the new striker
     * returnsToPlay=false → retire them OUT (counts as dismissal)
     *
     * After processing this player, checks if innings should end.
     */
    public MatchState returnFromRetiredHurt(int playerIndex, boolean returnsToPlay) {
        Innings      innings = match.getCurrentInningsData();
        List<Player> batters = match.getCurrentBattingPlayers();
        Player       p       = batters.get(playerIndex);

        if (returnsToPlay) {
            p.setRetiredHurt(false);
            p.setDismissalInfo("");
            innings.setStrikerIndex(playerIndex);
            // They're back — play continues
            return MatchState.BALL_RECORDED;
        } else {
            // Declined to return → retire out (counts as wicket)
            p.setRetiredHurt(false);
            p.dismiss("retired out");
            innings.setTotalWickets(innings.getTotalWickets() + 1);

            // Check if this was the last player
            if (isAllOut(innings, batters)) {
                // Any more retired hurt to prompt?
                List<Player> remaining = getRetiredHurtPlayers(batters);
                if (!remaining.isEmpty()) {
                    return MatchState.PROMPT_RETIRED_HURT;
                }
                return endInnings(innings);
            }
            return MatchState.PROMPT_RETIRED_HURT; // still more to prompt
        }
    }

    // ─── Undo ─────────────────────────────────────────────────────────────────

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
                incomingBatter.setHasNotBatted(true);
                if (innings.getNextBatsmanIndex() > 1) innings.setNextBatsmanIndex(innings.getNextBatsmanIndex() - 1);
            }
            innings.undoLastBall(getStriker());
            return true;
        }

        innings.undoLastBall(getStriker());
        return true;
    }

    // ─── All-out helpers ──────────────────────────────────────────────────────

    /**
     * True when all players who are available to bat have been dismissed.
     *
     * Retired-hurt players are NOT counted as dismissed — they may return.
     * The threshold is calculated only over the "active" pool:
     *   active = total players − retired-hurt count
     *   dismissed = totalWickets
     *   all-out when dismissed >= activeThreshold
     *
     * Two-bat: all-out at (active - 1) dismissals (last man has no partner)
     * Single:  all-out at  active       dismissals
     */
    private boolean isAllOut(Innings innings, List<Player> batters) {
        int retiredHurtCount = 0;
        for (Player p : batters) if (p.isRetiredHurt()) retiredHurtCount++;

        int active    = batters.size() - retiredHurtCount;
        int threshold = match.isSingleBatsmanMode() ? active : active - 1;
        return innings.getTotalWickets() >= threshold;
    }

    /**
     * After active players are all out, check if there are retired-hurt
     * players to prompt. If yes → PROMPT_RETIRED_HURT. If no → end innings.
     */
    private MatchState handleAllOut(Innings innings, List<Player> batters) {
        List<Player> retiredHurt = getRetiredHurtPlayers(batters);
        if (!retiredHurt.isEmpty()) {
            return MatchState.PROMPT_RETIRED_HURT;
        }
        return endInnings(innings);
    }

    /**
     * Returns all players currently flagged retiredHurt, in list order
     * (preserves the order they retired during the innings).
     */
    public List<Player> getRetiredHurtPlayers(List<Player> batters) {
        List<Player> list = new ArrayList<>();
        for (Player p : batters) if (p.isRetiredHurt()) list.add(p);
        return list;
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private MatchState checkAfterValidBall(Innings innings) {
        if (match.getCurrentInnings() == 2 && innings.getTotalRuns() >= match.getTarget()) {
            return endMatch(innings);
        }
        if (innings.getCurrentOver().isComplete()) {
            innings.completeCurrentOver();
            if (innings.getCompletedOvers().size() >= match.getMaxOvers()) {
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

        int          target      = match.getTarget();
        int          runsScored  = secondInnings.getTotalRuns();
        List<Player> chasers     = match.getCurrentBattingPlayers();
        int          retiredHurt = 0;
        for (Player p : chasers) if (p.isRetiredHurt()) retiredHurt++;
        int active      = chasers.size() - retiredHurt;
        int threshold   = match.isSingleBatsmanMode() ? active : active - 1;
        int wicketsLeft = threshold - secondInnings.getTotalWickets();

        String bat1 = match.getBattingFirstTeam().equals("home") ? match.getHomeTeamName() : match.getAwayTeamName();
        String bat2 = match.getBattingFirstTeam().equals("home") ? match.getAwayTeamName() : match.getHomeTeamName();

        if (runsScored >= target) {
            match.setWinnerTeam(match.getBattingFirstTeam().equals("home") ? "away" : "home");
            match.setResultDescription(bat2 + " won by " + Math.max(0, wicketsLeft) + (wicketsLeft == 1 ? " wicket" : " wickets"));
        } else {
            int margin = match.getFirstInnings().getTotalRuns() - runsScored;
            if (margin > 0) {
                match.setWinnerTeam(match.getBattingFirstTeam());
                match.setResultDescription(bat1 + " won by " + margin + (margin == 1 ? " run" : " runs"));
            } else {
                match.setWinnerTeam("tie");
                match.setResultDescription("Match tied! Both teams scored " + match.getFirstInnings().getTotalRuns() + " runs");
            }
        }
        return MatchState.MATCH_COMPLETE;
    }

    private int allOutThreshold(List<Player> batters) {
        int rh = 0; for (Player p : batters) if (p.isRetiredHurt()) rh++;
        int active = batters.size() - rh;
        return match.isSingleBatsmanMode() ? active : active - 1;
    }

    // ─── Utility getters ──────────────────────────────────────────────────────

    public Player getStriker() {
        Innings inn = match.getCurrentInningsData();
        return match.getCurrentBattingPlayers().get(inn.getStrikerIndex());
    }

    public Player getNonStriker() {
        if (match.isSingleBatsmanMode()) return null;
        Innings inn = match.getCurrentInningsData();
        int idx = inn.getNonStrikerIndex();
        List<Player> b = match.getCurrentBattingPlayers();
        return (idx >= 0 && idx < b.size()) ? b.get(idx) : null;
    }

    /**
     * Returns players available to come in — excludes striker, non-striker,
     * dismissed players, AND retired-hurt players (they are handled separately).
     */
    public List<Player> getAvailableBatsmen() {
        Innings      inn       = match.getCurrentInningsData();
        List<Player> batters   = match.getCurrentBattingPlayers();
        List<Player> available = new ArrayList<>();
        int si = inn.getStrikerIndex(), nsi = inn.getNonStrikerIndex();
        for (int i = 0; i < batters.size(); i++) {
            Player  p = batters.get(i);
            boolean atCrease = (i == si) || (i == nsi);
            // Exclude: at crease, dismissed, or retired hurt
            if (!atCrease && !p.isOut() && !p.isRetiredHurt()) available.add(p);
        }
        return available;
    }

    public int getNextBatsmanIndex() { return match.getCurrentInningsData().getNextBatsmanIndex(); }
    public Match getMatch() { return match; }
}
