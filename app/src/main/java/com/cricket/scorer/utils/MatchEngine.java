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
 * CHANGE: Added Joker player support on top of existing retired-hurt logic.
 *
 * New MatchState:
 *   JOKER_MUST_STOP_BOWLING — all batsmen dismissed while joker is bowling.
 *   Joker must come in to bat; another bowler from the bowling team must
 *   complete the remaining balls of the current over.
 *
 * Joker rules enforced here:
 *   - isAllOut() checks whether all NON-JOKER batsmen are dismissed when
 *     joker is bowling. If so, triggers JOKER_MUST_STOP_BOWLING before
 *     the normal all-out threshold.
 *   - deliverWicket() clears joker's batting role when joker is dismissed.
 *   - deliverRetiredHurt() clears joker's batting role when joker retires.
 *   - checkAfterValidBall() / OVER_COMPLETE path clears joker bowling role.
 */
public class MatchEngine {

    public enum MatchState {
        BALL_RECORDED,
        OVER_COMPLETE,
        INNINGS_COMPLETE,
        MATCH_COMPLETE,
        /** All active batsmen done; retired-hurt players need to be prompted */
        PROMPT_RETIRED_HURT,
        /**
         * All non-joker batsmen dismissed while joker is bowling.
         * Joker must stop bowling and come in to bat.
         * InningsActivity prompts the user to select a replacement bowler
         * to complete the remaining balls of the current over.
         */
        JOKER_MUST_STOP_BOWLING
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

        // If dismissed batsman is the joker, clear their batting role
        if (match.isJoker(striker.getName())) {
            match.clearJokerRole();
        }

        // All-out check (joker-aware)
        if (isAllOut(innings, batters)) {
            return handleAllOut(innings, batters);
        }

        innings.setStrikerIndex(newBatsmanIndex);
        if (newBatsmanIndex < batters.size()) batters.get(newBatsmanIndex).setHasNotBatted(false);
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) innings.setNextBatsmanIndex(newBatsmanIndex + 1);

        return checkAfterValidBall(innings);
    }

    /**
     * Run-out wicket where some runs were completed before the dismissal.
     *
     * @param runsCompleted   how many runs successfully crossed before the wicket (0..4)
     * @param strikerOut      true if the striker is the one run out; false if non-striker is out
     * @param newBatsmanIndex index in current batting list of the incoming batter
     */
    public MatchState deliverRunOutWicket(int runsCompleted, boolean strikerOut,
                                          int newBatsmanIndex) {
        Innings      innings    = match.getCurrentInningsData();
        Player       striker    = getStriker();
        Player       nonStriker = getNonStriker();
        Player       outPlayer  = strikerOut ? striker : nonStriker;
        List<Player> batters    = match.getCurrentBattingPlayers();

        striker.setHasNotBatted(false);
        if (nonStriker != null) nonStriker.setHasNotBatted(false);

        // Capture indices BEFORE recordRunOutWicket — recordRunOutWicket swaps strike if
        // runsCompleted is odd, so striker/non-striker indices may change inside it.
        int strikerIdxBefore    = innings.getStrikerIndex();
        int nonStrikerIdxBefore = innings.getNonStrikerIndex();

        innings.recordRunOutWicket(striker, outPlayer, runsCompleted);

        // Clear joker batting role if joker is dismissed
        if (match.isJoker(outPlayer.getName())) {
            match.clearJokerRole();
        }

        // All-out check
        if (isAllOut(innings, batters)) {
            return handleAllOut(innings, batters);
        }

        // Place the new batsman in the slot of the dismissed player.
        // After recordRunOutWicket, the strike has already swapped if runsCompleted is odd.
        // We need to figure out which SLOT (striker or non-striker) the dismissed player
        // currently occupies, then put the incoming batter there.
        int dismissedIdx = batters.indexOf(outPlayer);
        if (innings.getStrikerIndex() == dismissedIdx) {
            innings.setStrikerIndex(newBatsmanIndex);
        } else if (innings.getNonStrikerIndex() == dismissedIdx) {
            innings.setNonStrikerIndex(newBatsmanIndex);
        }
        if (newBatsmanIndex < batters.size()) batters.get(newBatsmanIndex).setHasNotBatted(false);
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) innings.setNextBatsmanIndex(newBatsmanIndex + 1);

        return checkAfterValidBall(innings);
    }

    public MatchState deliverRetiredHurt(int newBatsmanIndex) {
        Innings      innings = match.getCurrentInningsData();
        Player       striker = getStriker();
        List<Player> batters = match.getCurrentBattingPlayers();

        striker.setHasNotBatted(false);
        striker.retireHurt();

        // If retiring player is the joker, clear their batting role
        if (match.isJoker(striker.getName())) {
            match.clearJokerRole();
        }

        innings.setStrikerIndex(newBatsmanIndex);
        if (newBatsmanIndex < batters.size()) batters.get(newBatsmanIndex).setHasNotBatted(false);
        if (innings.getNextBatsmanIndex() <= newBatsmanIndex) innings.setNextBatsmanIndex(newBatsmanIndex + 1);

        return MatchState.BALL_RECORDED;
    }

    public MatchState returnFromRetiredHurt(int playerIndex, boolean returnsToPlay) {
        Innings      innings = match.getCurrentInningsData();
        List<Player> batters = match.getCurrentBattingPlayers();
        Player       p       = batters.get(playerIndex);

        if (returnsToPlay) {
            p.setRetiredHurt(false);
            p.setDismissalInfo("");
            innings.setStrikerIndex(playerIndex);
            // If returning player is joker, restore batting role
            if (match.isJoker(p.getName())) match.setJokerBatting();
            return MatchState.BALL_RECORDED;
        } else {
            p.setRetiredHurt(false);
            p.dismiss("retired out");
            innings.setTotalWickets(innings.getTotalWickets() + 1);

            if (isAllOut(innings, batters)) {
                List<Player> remaining = getRetiredHurtPlayers(batters);
                if (!remaining.isEmpty()) return MatchState.PROMPT_RETIRED_HURT;
                return endInnings(innings);
            }
            return MatchState.PROMPT_RETIRED_HURT;
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
            // For run-out wickets, the dismissed player could have been at either the
            // striker OR the non-striker slot, and the incoming batter replaces them
            // in that same slot. Detect by checking whether the current striker or
            // non-striker slot holds a "fresh" player (hasNotBatted), and the OTHER
            // slot holds a dismissed player.
            int strikerIdx    = innings.getStrikerIndex();
            int nonStrikerIdx = innings.getNonStrikerIndex();

            int    incomingSlot   = -1;          // which slot the new batter sits in
            int    dismissedIndex = -1;
            Player dismissedPlayer = null;

            // The "incoming" batter is the one in striker/non-striker who is fresh
            // (no balls faced AND not the actual non-striker from before — i.e.
            // hasNotBatted is true OR they're the highest-numbered batter in play).
            // Simpler heuristic: the dismissed player is the only "out" player NOT
            // already at one of the two crease slots.
            for (int i = 0; i < batters.size(); i++) {
                if (batters.get(i).isOut() && i != strikerIdx && i != nonStrikerIdx) {
                    dismissedIndex  = i;
                    dismissedPlayer = batters.get(i);
                    break;
                }
            }
            // Figure out which slot the dismissed player should be restored to.
            // The OTHER slot still has its original occupant; the slot with a
            // hasNotBatted player is the one that just received the incoming batter.
            if (dismissedPlayer != null) {
                Player atStriker    = batters.get(strikerIdx);
                Player atNonStriker = (nonStrikerIdx >= 0 && nonStrikerIdx < batters.size())
                                      ? batters.get(nonStrikerIdx) : null;
                Player incomingBatter;
                if (atStriker.isHasNotBatted() && atStriker.getBallsFaced() == 0
                        && atStriker.getRunsScored() == 0) {
                    incomingSlot   = strikerIdx;
                    incomingBatter = atStriker;
                    innings.setStrikerIndex(dismissedIndex);
                } else if (atNonStriker != null && atNonStriker.isHasNotBatted()
                        && atNonStriker.getBallsFaced() == 0
                        && atNonStriker.getRunsScored() == 0) {
                    incomingSlot   = nonStrikerIdx;
                    incomingBatter = atNonStriker;
                    innings.setNonStrikerIndex(dismissedIndex);
                } else {
                    // Fallback to legacy behaviour (regular wicket where striker is out)
                    incomingBatter = atStriker;
                    innings.setStrikerIndex(dismissedIndex);
                }

                dismissedPlayer.setOut(false);
                dismissedPlayer.setDismissalInfo("");
                if (match.isJoker(dismissedPlayer.getName())) match.setJokerBatting();
                incomingBatter.setHasNotBatted(true);
                if (innings.getNextBatsmanIndex() > 1) {
                    innings.setNextBatsmanIndex(innings.getNextBatsmanIndex() - 1);
                }
            }
            Ball removed = innings.undoLastBall(getStriker());
            // For run-out wickets, reverse the completed-runs stats on the striker.
            // Innings.undoLastBall() has already swapped the strike index back, so
            // getStriker() now returns the batter who originally faced the delivery.
            if (removed != null && removed.isRunOutWicket() && removed.getRuns() > 0) {
                Player originalStriker = getStriker();
                if (originalStriker != null) {
                    int rc = removed.getRuns();
                    originalStriker.setRunsScored(originalStriker.getRunsScored() - rc);
                    if (originalStriker.getBallsFaced() > 0) {
                        originalStriker.setBallsFaced(originalStriker.getBallsFaced() - 1);
                    }
                    if (rc == 4 && originalStriker.getFours() > 0) {
                        originalStriker.setFours(originalStriker.getFours() - 1);
                    }
                    if (rc == 6 && originalStriker.getSixes() > 0) {
                        originalStriker.setSixes(originalStriker.getSixes() - 1);
                    }
                }
            }
            return true;
        }

        innings.undoLastBall(getStriker());
        return true;
    }

    // ─── All-out helpers ──────────────────────────────────────────────────────

    /**
     * True when the innings should end or joker should stop bowling.
     *
     * JOKER SPECIAL CASE:
     *   When joker is bowling and all NON-JOKER batsmen are dismissed,
     *   we treat this as "all out for joker purposes" even if the standard
     *   threshold hasn't been reached — because the joker must now bat.
     *   This is detected separately in handleAllOut.
     *
     * STANDARD CASE:
     *   active = total - retiredHurt
     *   threshold = active (single) or active - 1 (two-bat)
     */
    private boolean isAllOut(Innings innings, List<Player> batters) {
        // Joker special check: if joker is bowling and all non-joker batters are gone
        if (match.hasJoker() && match.isJokerBowling()) {
            int nonJokerCanBat = 0;
            for (Player p : batters) {
                if (!match.isJoker(p.getName()) && !p.isOut() && !p.isRetiredHurt()) {
                    nonJokerCanBat++;
                }
            }
            if (nonJokerCanBat == 0) return true;
        }

        // Standard all-out check
        int retiredHurtCount = 0;
        for (Player p : batters) if (p.isRetiredHurt()) retiredHurtCount++;
        int active    = batters.size() - retiredHurtCount;
        int threshold = match.isSingleBatsmanMode() ? active : active - 1;
        return innings.getTotalWickets() >= threshold;
    }

    private MatchState handleAllOut(Innings innings, List<Player> batters) {
        // Check joker special case first
        if (match.hasJoker() && match.isJokerBowling()) {
            int nonJokerCanBat = 0;
            for (Player p : batters) {
                if (!match.isJoker(p.getName()) && !p.isOut() && !p.isRetiredHurt()) {
                    nonJokerCanBat++;
                }
            }
            if (nonJokerCanBat == 0 && !innings.getCurrentOver().isComplete()) {
                // Joker must stop bowling — comes in to bat
                match.clearJokerRole(); // clear BOWLING role so joker can bat
                return MatchState.JOKER_MUST_STOP_BOWLING;
            }
        }

        // Standard retired hurt check
        List<Player> retiredHurt = getRetiredHurtPlayers(batters);
        if (!retiredHurt.isEmpty()) {
            return MatchState.PROMPT_RETIRED_HURT;
        }
        return endInnings(innings);
    }

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
            // Over complete: clear joker bowling role so they can bat next
            if (match.hasJoker() && match.isJokerBowling()) match.clearJokerRole();
            if (innings.getCompletedOvers().size() >= match.getMaxOvers()) {
                return endInnings(innings);
            }
            return MatchState.OVER_COMPLETE;
        }
        return MatchState.BALL_RECORDED;
    }

    private MatchState endInnings(Innings innings) {
        innings.setComplete(true);
        // Clear joker role at end of innings
        if (match.hasJoker()) match.clearJokerRole();
        if (match.getCurrentInnings() == 1) {
            match.setCurrentInnings(2);
            Innings second = new Innings(2, match.isSingleBatsmanMode());
            match.setSecondInnings(second);
            for (Player p : match.getCurrentBattingPlayers()) p.resetForNewInnings();
            return MatchState.INNINGS_COMPLETE;
        }
        return endMatch(innings);
    }

    /**
     * Called by the "Edit overs" dialog after maxOvers has been mutated.
     * If the new max means the current innings has already reached its
     * limit AND we are at an over boundary (no balls bowled in the
     * current over), end the innings immediately. Returns the resulting
     * MatchState — usually INNINGS_COMPLETE if it ended, or BALL_RECORDED
     * if no end is triggered.
     *
     * Only ends at an over boundary because ending mid-over would orphan
     * the unfinished over in the scorecard. If the user reduces max while
     * mid-over, the innings will end naturally when the current over
     * completes (checkAfterValidBall handles that).
     */
    public MatchState endInningsIfMaxReached() {
        Innings innings = match.getCurrentInningsData();
        if (innings == null || innings.isComplete()) return MatchState.BALL_RECORDED;
        int completedOvers = innings.getCompletedOvers().size();
        boolean atOverBoundary = innings.getCurrentOver() == null
                || innings.getCurrentOver().getBalls().isEmpty();
        if (atOverBoundary && completedOvers >= match.getMaxOvers()) {
            return endInnings(innings);
        }
        return MatchState.BALL_RECORDED;
    }

    private MatchState endMatch(Innings secondInnings) {
        secondInnings.setComplete(true);
        match.setMatchCompleted(true);
        if (match.hasJoker()) match.clearJokerRole();

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

    public List<Player> getAvailableBatsmen() {
        Innings      inn       = match.getCurrentInningsData();
        List<Player> batters   = match.getCurrentBattingPlayers();
        List<Player> available = new ArrayList<>();
        int si = inn.getStrikerIndex(), nsi = inn.getNonStrikerIndex();
        for (int i = 0; i < batters.size(); i++) {
            Player  p = batters.get(i);
            boolean atCrease = (i == si) || (i == nsi);
            if (!atCrease && !p.isOut() && !p.isRetiredHurt()) available.add(p);
        }
        return available;
    }

    public int getNextBatsmanIndex() { return match.getCurrentInningsData().getNextBatsmanIndex(); }
    public Match getMatch() { return match; }
}
