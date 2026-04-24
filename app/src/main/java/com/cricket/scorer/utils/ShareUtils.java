package com.cricket.scorer.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;

import java.util.List;
import java.util.Locale;

/**
 * ShareUtils.java
 * Utility class for generating and sharing the match scorecard.
 *
 * Builds a plain-text scorecard and opens WhatsApp with
 * a pre-filled message to the given phone number.
 */
public class ShareUtils {

    /**
     * Builds a formatted plain-text scorecard string for the completed match.
     *
     * @param match the completed Match object
     * @return multi-line scorecard string
     */
    public static String buildScorecard(Match match) {
        StringBuilder sb = new StringBuilder();
        Innings i1 = match.getFirstInnings();
        Innings i2 = match.getSecondInnings();

        String bat1 = match.getBattingFirstTeam().equals("home")
                ? match.getHomeTeamName() : match.getAwayTeamName();
        String bat2 = match.getBattingFirstTeam().equals("home")
                ? match.getAwayTeamName() : match.getHomeTeamName();

        // ── Header ─────────────────────────────────────────────────────────
        sb.append("🏏 CRICKET SCORECARD\n");
        sb.append("══════════════════════\n");
        sb.append(match.getHomeTeamName()).append(" vs ").append(match.getAwayTeamName()).append("\n");
        sb.append("Format: ").append(match.getMaxOvers()).append(" overs per side\n\n");

        // ── 1st Innings summary ────────────────────────────────────────────
        if (i1 != null) {
            sb.append("📊 1st INNINGS — ").append(bat1).append("\n");
            sb.append(i1.getScoreString()).append(" (").append(i1.getOversString()).append(" ov)\n");
            sb.append(String.format(Locale.US, "Run Rate: %.2f\n\n", i1.getCurrentRunRate()));

            List<Player> bat1Players = match.getBattingFirstTeam().equals("home")
                    ? match.getHomePlayers() : match.getAwayPlayers();
            sb.append(formatBattingCard(bat1Players));
            sb.append(formatOverSummary(i1));
        }

        // ── 2nd Innings summary ────────────────────────────────────────────
        if (i2 != null) {
            sb.append("📊 2nd INNINGS — ").append(bat2).append("\n");
            sb.append(i2.getScoreString()).append(" (").append(i2.getOversString()).append(" ov)\n");
            sb.append(String.format(Locale.US, "Run Rate: %.2f\n\n", i2.getCurrentRunRate()));

            List<Player> bat2Players = match.getBattingFirstTeam().equals("home")
                    ? match.getAwayPlayers() : match.getHomePlayers();
            sb.append(formatBattingCard(bat2Players));
            sb.append(formatOverSummary(i2));
        }

        // ── Result ─────────────────────────────────────────────────────────
        sb.append("══════════════════════\n");
        sb.append("🏆 RESULT\n");
        sb.append(match.getResultDescription()).append("\n");
        sb.append("══════════════════════\n");
        sb.append("Shared via Cricket Scorer App");

        return sb.toString();
    }

    /** Formats batting scorecard for one team's innings */
    private static String formatBattingCard(List<Player> players) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%-18s %3s %3s %3s %3s %6s\n",
                "Batsman", "R", "B", "4s", "6s", "SR"));
        sb.append("─────────────────────────────────────\n");
        for (Player p : players) {
            if (p.isHasNotBatted() && p.getBallsFaced() == 0) continue;
            float sr = p.getBallsFaced() > 0
                    ? ((float) p.getRunsScored() / p.getBallsFaced()) * 100f : 0f;
            String status = p.isOut() ? "out" : "not out";
            sb.append(String.format(Locale.US, "%-18s %3d %3d %3d %3d %6.1f  %s\n",
                    truncate(p.getName(), 18),
                    p.getRunsScored(), p.getBallsFaced(),
                    p.getFours(), p.getSixes(), sr, status));
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Formats a brief over-by-over summary */
    private static String formatOverSummary(Innings innings) {
        StringBuilder sb = new StringBuilder();
        sb.append("Over summary:\n");
        List<Over> overs = innings.getAllOvers();
        for (Over over : overs) {
            sb.append(String.format(Locale.US, "  Ov %-2d: %-25s — %d runs\n",
                    over.getOverNumber(),
                    over.getSummary(),
                    over.getTotalRuns()));
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Truncates a string to maxLen characters */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    // ─── WhatsApp sharing ─────────────────────────────────────────────────────

    /**
     * Opens WhatsApp with a pre-filled scorecard message.
     *
     * @param context     Android context
     * @param phoneNumber international format, e.g. "+919876543210"
     * @param match       the completed match
     */
    public static void shareViaWhatsApp(Context context, String phoneNumber, Match match) {
        String scorecard = buildScorecard(match);
        String cleanedNumber = phoneNumber.replaceAll("[^+\\d]", "");
        String url = "https://wa.me/" + cleanedNumber + "?text=" +
                Uri.encode(scorecard);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        context.startActivity(intent);
    }

    /**
     * Generic text share intent (shares to any app: Messages, Gmail, etc.)
     */
    public static void shareAsText(Context context, Match match) {
        String scorecard = buildScorecard(match);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Cricket Scorecard");
        shareIntent.putExtra(Intent.EXTRA_TEXT, scorecard);
        context.startActivity(Intent.createChooser(shareIntent, "Share scorecard via"));
    }
}
