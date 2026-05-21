package com.cricket.scorer.utils;

import com.cricket.scorer.models.BowlerStat;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MatchScorecardBuilder.java
 *
 * Pure data-to-table conversion. Takes a Match and produces String[][]
 * arrays for each section of a scorecard. Both the Excel exporter and
 * the PNG exporter consume the same tables so the two formats stay in
 * sync visually.
 *
 * No Android dependencies, no I/O — easy to unit-test if we ever do.
 *
 * Table conventions:
 *   First row of every returned array is the header.
 *   Empty values are "" not null, so renderers don't have to null-check.
 */
public final class MatchScorecardBuilder {

    private MatchScorecardBuilder() {}

    // ─── Summary header table (3 rows: match details, innings 1, innings 2) ──

    /**
     * Top-of-scorecard summary block.
     *   [Title row]   ← caller usually renders this separately as a title
     *   ["Field", "Value"] header
     *   Date, format, teams, result, etc.
     */
    public static String[][] summaryTable(Match match) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Field", "Value"});
        rows.add(new String[]{"Date", new SimpleDateFormat("dd MMM yyyy, HH:mm",
                Locale.US).format(new Date())});
        rows.add(new String[]{"Format",
                match.getMaxOvers() + " overs · " +
                        (match.isSingleBatsmanMode() ? "Single bat" : "Two bat")});
        rows.add(new String[]{"Teams",
                safe(match.getHomeTeamName()) + " vs " + safe(match.getAwayTeamName())});
        // Resolve "home"/"away" to the actual team name for display
        String battedFirst = "home".equals(match.getBattingFirstTeam())
                ? safe(match.getHomeTeamName())
                : safe(match.getAwayTeamName());
        rows.add(new String[]{"Batted first", battedFirst});

        Innings i1 = match.getFirstInnings();
        if (i1 != null) {
            rows.add(new String[]{
                    inningsHeading(match, 1),
                    i1.getScoreString() + " (" + i1.getOversString() + " ov)"
            });
        }
        Innings i2 = match.getSecondInnings();
        if (i2 != null) {
            rows.add(new String[]{
                    inningsHeading(match, 2),
                    i2.getScoreString() + " (" + i2.getOversString() + " ov)"
            });
        }
        if (match.isMatchCompleted() && match.getResultDescription() != null) {
            rows.add(new String[]{"Result", match.getResultDescription()});
        }
        return rows.toArray(new String[0][]);
    }

    // ─── Batting card for one innings ────────────────────────────────────────

    public static String[][] battingTable(Match match, int innings) {
        Innings inn = innings == 1 ? match.getFirstInnings() : match.getSecondInnings();
        if (inn == null) return new String[][]{
                { "Batter", "Status", "R", "B", "4s", "6s", "SR" }
        };
        // Derive which team's players batted in this innings
        // battingFirstTeam stores "home" or "away", NOT the team name
        boolean homeFirst = "home".equals(match.getBattingFirstTeam());
        List<Player> players = (innings == 1)
                ? (homeFirst ? match.getHomePlayers() : match.getAwayPlayers())
                : (homeFirst ? match.getAwayPlayers() : match.getHomePlayers());

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{ "Batter", "Status", "R", "B", "4s", "6s", "SR" });
        if (players == null) return rows.toArray(new String[0][]);

        for (Player p : players) {
            String status;
            if (p.isHasNotBatted()) status = "did not bat";
            else if (p.isRetiredHurt()) status = "retired hurt";
            else if (p.isOut()) status = safe(p.getDismissalInfo(), "out");
            else status = "not out";
            rows.add(new String[]{
                    safe(p.getName()),
                    status,
                    String.valueOf(p.getRunsScored()),
                    String.valueOf(p.getBallsFaced()),
                    String.valueOf(p.getFours()),
                    String.valueOf(p.getSixes()),
                    String.format(Locale.US, "%.1f", p.getStrikeRate())
            });
        }
        // Footer total row
        rows.add(new String[]{
                "TOTAL",
                inn.getScoreString() + " in " + inn.getOversString() + " ov",
                String.valueOf(inn.getTotalRuns()),
                String.valueOf(inn.getTotalValidBalls()),
                "", "",
                String.format(Locale.US, "%.2f", inn.getCurrentRunRate())
        });
        return rows.toArray(new String[0][]);
    }

    // ─── Bowling card for one innings ────────────────────────────────────────

    public static String[][] bowlingTable(Match match, int innings) {
        Innings inn = innings == 1 ? match.getFirstInnings() : match.getSecondInnings();
        if (inn == null) return new String[][]{
                { "Bowler", "O", "R", "Ext", "W", "Econ" }
        };
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{ "Bowler", "O", "R", "Ext", "W", "Econ" });
        for (BowlerStat bs : inn.getBowlerStats()) {
            rows.add(new String[]{
                    safe(bs.getName()),
                    bs.getOversString(),
                    String.valueOf(bs.getRuns()),
                    String.valueOf(bs.getExtras()),
                    String.valueOf(bs.getWickets()),
                    String.format(Locale.US, "%.2f", bs.getEconomy())
            });
        }
        return rows.toArray(new String[0][]);
    }

    // ─── Over-by-over runs (for in-depth sheet) ──────────────────────────────

    public static String[][] overByOverTable(Match match, int innings) {
        Innings inn = innings == 1 ? match.getFirstInnings() : match.getSecondInnings();
        if (inn == null) return new String[][]{ { "Over", "Runs", "Wickets" } };
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{ "Over", "Runs", "Wickets" });
        List<Over> overs = inn.getAllOvers();
        for (int i = 0; i < overs.size(); i++) {
            int runs = 0, wkts = 0;
            for (com.cricket.scorer.models.Ball b : overs.get(i).getBalls()) {
                runs += b.getRuns();
                if (b.getType() == com.cricket.scorer.models.Ball.BallType.WICKET) wkts++;
            }
            rows.add(new String[]{
                    String.valueOf(i + 1),
                    String.valueOf(runs),
                    String.valueOf(wkts)
            });
        }
        return rows.toArray(new String[0][]);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** "Lions" / "Tigers" depending on which team batted in this innings. */
    public static String inningsHeading(Match match, int innings) {
        String bf = match.getBattingFirstTeam();
        if (bf == null) return "Innings " + innings + " score";
        // battingFirstTeam stores "home" or "away", NOT the team name
        boolean homeFirst = "home".equals(bf);
        String name;
        if (innings == 1) name = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        else              name = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();
        return safe(name) + " score";
    }

    /**
     * Returns "[BowlingTeam] score" for the given innings.
     * The bowling team is the OPPOSITE of the batting team.
     */
    public static String bowlingHeading(Match match, int innings) {
        String bf = match.getBattingFirstTeam();
        if (bf == null) return "Innings " + innings + " bowling";
        boolean homeFirst = "home".equals(bf);
        String name;
        if (innings == 1) name = homeFirst ? match.getAwayTeamName() : match.getHomeTeamName();
        else              name = homeFirst ? match.getHomeTeamName() : match.getAwayTeamName();
        return safe(name) + " score";
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String safe(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }
}
