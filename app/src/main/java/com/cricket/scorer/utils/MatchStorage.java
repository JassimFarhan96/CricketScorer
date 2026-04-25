package com.cricket.scorer.utils;

import android.content.Context;
import android.util.Log;

import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MatchStorage.java
 *
 * Handles saving and loading Match objects to/from the app's internal
 * storage under a directory called "recent_matches".
 *
 * Each match is saved as a single JSON file named:
 *   <timestamp>_<homeTeam>_vs_<awayTeam>.json
 * e.g.  20260423_193045_Mumbai_vs_Delhi.json
 *
 * The JSON schema mirrors the Match/Innings/Over/Ball/Player models.
 * Uses only org.json (built into Android — no extra dependency needed).
 *
 * Public API:
 *   saveMatch(context, match)         → saves to recent_matches/
 *   loadAllMatches(context)           → returns List<Match> sorted newest first
 *   getPage(list, page, pageSize)     → returns one page of matches
 *   getTotalPages(list, pageSize)     → total page count
 *   deleteMatch(context, fileName)    → deletes one file
 */
public class MatchStorage {

    private static final String TAG      = "MatchStorage";
    private static final String DIR_NAME = "recent_matches";

    // ─── Directory helpers ────────────────────────────────────────────────────

    /** Returns (and creates if needed) the recent_matches directory. */
    public static File getStorageDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    /**
     * Serialises a completed Match to JSON and writes it to a new file.
     * @return the File that was written, or null on failure.
     */
    public static File saveMatch(Context context, Match match) {
        try {
            JSONObject root = matchToJson(match);

            // Filename: timestamp + team names
            String ts       = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String safeName = sanitize(match.getHomeTeamName())
                    + "_vs_" + sanitize(match.getAwayTeamName());
            String fileName = ts + "_" + safeName + ".json";

            File file = new File(getStorageDir(context), fileName);
            FileWriter fw = new FileWriter(file);
            fw.write(root.toString(2)); // pretty-printed JSON
            fw.flush();
            fw.close();

            Log.d(TAG, "Match saved: " + file.getAbsolutePath());
            return file;

        } catch (Exception e) {
            Log.e(TAG, "saveMatch failed", e);
            return null;
        }
    }

    // ─── Load all ─────────────────────────────────────────────────────────────

    /**
     * Loads every .json file from recent_matches/, parses each into a Match,
     * and returns them sorted newest-first (by file name timestamp).
     */
    public static List<Match> loadAllMatches(Context context) {
        List<Match> matches = new ArrayList<>();
        File dir = getStorageDir(context);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return matches;

        // Sort newest first (filename starts with yyyyMMdd_HHmmss)
        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));

        for (File f : files) {
            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Match m = jsonToMatch(new JSONObject(sb.toString()));
                // Tag the match with its filename so we can reference it later
                m.setSavedFileName(f.getName());
                matches.add(m);

            } catch (Exception e) {
                Log.e(TAG, "Failed to parse " + f.getName(), e);
            }
        }
        return matches;
    }

    // ─── Pagination helpers ───────────────────────────────────────────────────

    public static int getTotalPages(List<?> list, int pageSize) {
        if (list.isEmpty()) return 0;
        return (int) Math.ceil((double) list.size() / pageSize);
    }

    /** Returns a sub-list for the given 1-based page number. */
    public static <T> List<T> getPage(List<T> list, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        int to   = Math.min(from + pageSize, list.size());
        if (from >= list.size()) return new ArrayList<>();
        return list.subList(from, to);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    public static boolean deleteMatch(Context context, String fileName) {
        File f = new File(getStorageDir(context), fileName);
        return f.exists() && f.delete();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // JSON serialisation
    // ═════════════════════════════════════════════════════════════════════════

    private static JSONObject matchToJson(Match m) throws Exception {
        JSONObject o = new JSONObject();
        o.put("homeTeamName",      m.getHomeTeamName());
        o.put("awayTeamName",      m.getAwayTeamName());
        o.put("maxOvers",          m.getMaxOvers());
        o.put("battingFirstTeam",  m.getBattingFirstTeam());
        o.put("singleBatsmanMode", m.isSingleBatsmanMode());
        o.put("matchCompleted",    m.isMatchCompleted());
        o.put("winnerTeam",        m.getWinnerTeam() != null ? m.getWinnerTeam() : "");
        o.put("resultDescription", m.getResultDescription() != null ? m.getResultDescription() : "");
        o.put("homePlayers",  playersToJson(m.getHomePlayers()));
        o.put("awayPlayers",  playersToJson(m.getAwayPlayers()));
        o.put("firstInnings",  inningsToJson(m.getFirstInnings()));
        o.put("secondInnings", m.getSecondInnings() != null ? inningsToJson(m.getSecondInnings()) : JSONObject.NULL);
        return o;
    }

    private static JSONArray playersToJson(List<Player> players) throws Exception {
        JSONArray arr = new JSONArray();
        if (players == null) return arr;
        for (Player p : players) {
            JSONObject o = new JSONObject();
            o.put("name",         p.getName());
            o.put("runsScored",   p.getRunsScored());
            o.put("ballsFaced",   p.getBallsFaced());
            o.put("fours",        p.getFours());
            o.put("sixes",        p.getSixes());
            o.put("out",          p.isOut());
            o.put("hasNotBatted", p.isHasNotBatted());
            o.put("dismissal",    p.getDismissalInfo() != null ? p.getDismissalInfo() : "");
            arr.put(o);
        }
        return arr;
    }

    private static JSONObject inningsToJson(Innings inn) throws Exception {
        JSONObject o = new JSONObject();
        o.put("inningsNumber",     inn.getInningsNumber());
        o.put("singleBatsmanMode", inn.isSingleBatsmanMode());
        o.put("totalRuns",         inn.getTotalRuns());
        o.put("totalWickets",      inn.getTotalWickets());
        o.put("totalValidBalls",   inn.getTotalValidBalls());
        o.put("strikerIndex",      inn.getStrikerIndex());
        o.put("nonStrikerIndex",   inn.getNonStrikerIndex());
        o.put("nextBatsmanIndex",  inn.getNextBatsmanIndex());
        o.put("complete",          inn.isComplete());
        JSONArray overs = new JSONArray();
        for (Over ov : inn.getAllOvers()) overs.put(overToJson(ov));
        o.put("overs", overs);
        return o;
    }

    private static JSONObject overToJson(Over ov) throws Exception {
        JSONObject o = new JSONObject();
        o.put("overNumber", ov.getOverNumber());
        JSONArray balls = new JSONArray();
        for (Ball b : ov.getBalls()) {
            JSONObject bo = new JSONObject();
            bo.put("type", b.getType().name());
            bo.put("runs", b.getRuns());
            bo.put("valid", b.isValid());
            balls.put(bo);
        }
        o.put("balls", balls);
        return o;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // JSON deserialisation
    // ═════════════════════════════════════════════════════════════════════════

    private static Match jsonToMatch(JSONObject o) throws Exception {
        Match m = new Match();
        m.setHomeTeamName(o.getString("homeTeamName"));
        m.setAwayTeamName(o.getString("awayTeamName"));
        m.setMaxOvers(o.getInt("maxOvers"));
        m.setBattingFirstTeam(o.getString("battingFirstTeam"));
        m.setSingleBatsmanMode(o.optBoolean("singleBatsmanMode", false));
        m.setMatchCompleted(o.optBoolean("matchCompleted", true));
        m.setWinnerTeam(o.optString("winnerTeam", ""));
        m.setResultDescription(o.optString("resultDescription", ""));
        m.setHomePlayers(jsonToPlayers(o.getJSONArray("homePlayers")));
        m.setAwayPlayers(jsonToPlayers(o.getJSONArray("awayPlayers")));
        if (!o.isNull("firstInnings"))
            m.setFirstInnings(jsonToInnings(o.getJSONObject("firstInnings")));
        if (!o.isNull("secondInnings"))
            m.setSecondInnings(jsonToInnings(o.getJSONObject("secondInnings")));
        return m;
    }

    private static List<Player> jsonToPlayers(JSONArray arr) throws Exception {
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Player p = new Player(o.getString("name"));
            p.setRunsScored(o.optInt("runsScored", 0));
            p.setBallsFaced(o.optInt("ballsFaced", 0));
            p.setFours(o.optInt("fours", 0));
            p.setSixes(o.optInt("sixes", 0));
            p.setOut(o.optBoolean("out", false));
            p.setHasNotBatted(o.optBoolean("hasNotBatted", true));
            p.setDismissalInfo(o.optString("dismissal", ""));
            list.add(p);
        }
        return list;
    }

    private static Innings jsonToInnings(JSONObject o) throws Exception {
        boolean single = o.optBoolean("singleBatsmanMode", false);
        Innings inn = new Innings(o.getInt("inningsNumber"), single);
        inn.setTotalRuns(o.optInt("totalRuns", 0));
        inn.setTotalWickets(o.optInt("totalWickets", 0));
        inn.setTotalValidBalls(o.optInt("totalValidBalls", 0));
        inn.setStrikerIndex(o.optInt("strikerIndex", 0));
        inn.setNonStrikerIndex(o.optInt("nonStrikerIndex", single ? -1 : 1));
        inn.setNextBatsmanIndex(o.optInt("nextBatsmanIndex", single ? 1 : 2));
        inn.setComplete(o.optBoolean("complete", true));
        JSONArray overs = o.optJSONArray("overs");
        if (overs != null) {
            for (int i = 0; i < overs.length(); i++) {
                JSONObject ov = overs.getJSONObject(i);
                Over over = new Over(ov.getInt("overNumber"));
                JSONArray balls = ov.optJSONArray("balls");
                if (balls != null) {
                    for (int j = 0; j < balls.length(); j++) {
                        JSONObject bo = balls.getJSONObject(j);
                        Ball.BallType type = Ball.BallType.valueOf(bo.getString("type"));
                        Ball ball;
                        switch (type) {
                            case WIDE:    ball = Ball.wide();           break;
                            case NO_BALL: ball = Ball.noBall();         break;
                            case WICKET:  ball = Ball.wicket();         break;
                            default:      ball = Ball.normal(bo.optInt("runs", 0)); break;
                        }
                        over.addBall(ball);
                    }
                }
                inn.getCompletedOvers().add(over);
            }
        }
        return inn;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /** Strips characters unsafe for file names. */
    private static String sanitize(String s) {
        if (s == null) return "Unknown";
        return s.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
    }
}
