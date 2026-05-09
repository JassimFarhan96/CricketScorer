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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MatchStorage.java
 *
 * CHANGE: inningsToJson() and jsonToInnings() now include all four
 * bowling stat maps:
 *   bowlerOversMap    — complete overs per bowler
 *   bowlerRunsMap     — runs conceded per bowler  (was missing)
 *   bowlerWicketsMap  — wickets taken per bowler  (was missing)
 *   bowlerBallsMap    — valid balls bowled per bowler (was missing)
 *
 * Without these, loading a saved match from disk returned empty bowling
 * tables in StatsActivity because getBowlerStats() reads from these maps.
 *
 * Also persists Over.bowlerIndex and Over.bowlerName so the over history
 * correctly shows which bowler bowled each over when viewing saved matches.
 */
public class MatchStorage {

    private static final String TAG      = "MatchStorage";
    private static final String DIR_NAME = "recent_matches";
    /**
     * Tournament matches are saved under recent_tournaments/matches/ — a
     * subdirectory of recent_tournaments. This keeps tournament *archive*
     * files (which live directly under recent_tournaments/) separate from
     * individual *match* files, so RecentTournamentsActivity sees only
     * archive entries and not stray match-file entries.
     */
    private static final String TOURNAMENT_DIR_NAME = "recent_tournaments/matches";

    // ─── Directory ────────────────────────────────────────────────────────────

    public static File getStorageDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Directory for tournament matches — kept separate from recent_matches/. */
    public static File getTournamentStorageDir(Context context) {
        File dir = new File(context.getFilesDir(), TOURNAMENT_DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ─── Tournament match save / load ─────────────────────────────────────────
    //
    // Same JSON format as the regular match save, just a different directory,
    // so RecentMatchesActivity (scans recent_matches/) doesn't show them and
    // TournamentDetailsActivity (loads from recent_tournaments/) can find them.

    public static File saveTournamentMatch(Context context, Match match) {
        return saveMatchToDir(match, getTournamentStorageDir(context));
    }

    /** Loads a tournament match by file name from recent_tournaments/. */
    public static Match loadTournamentMatch(Context context, String fileName) {
        if (fileName == null) return null;
        File file = new File(getTournamentStorageDir(context), fileName);
        if (!file.exists()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            Match m = jsonToMatch(new JSONObject(sb.toString()));
            m.setSavedFileName(file.getName());
            return m;
        } catch (Exception e) {
            Log.e(TAG, "loadTournamentMatch failed", e);
            return null;
        }
    }

    /** Internal: write a Match to JSON in the given target directory. */
    private static File saveMatchToDir(Match match, File targetDir) {
        try {
            JSONObject root = matchToJson(match);
            String ts       = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String safeName = sanitize(match.getHomeTeamName())
                    + "_vs_" + sanitize(match.getAwayTeamName());
            String fileName = ts + "_" + safeName + ".json";

            File file = new File(targetDir, fileName);
            FileWriter fw = new FileWriter(file);
            fw.write(root.toString(2));
            fw.flush();
            fw.close();
            Log.d(TAG, "Match saved: " + file.getAbsolutePath());
            return file;
        } catch (Exception e) {
            Log.e(TAG, "saveMatchToDir failed", e);
            return null;
        }
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    public static File saveMatch(Context context, Match match) {
        try {
            JSONObject root = matchToJson(match);
            String ts       = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String safeName = sanitize(match.getHomeTeamName())
                    + "_vs_" + sanitize(match.getAwayTeamName());
            String fileName = ts + "_" + safeName + ".json";

            File file = new File(getStorageDir(context), fileName);
            FileWriter fw = new FileWriter(file);
            fw.write(root.toString(2));
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

    public static List<Match> loadAllMatches(Context context) {
        List<Match> matches = new ArrayList<>();
        File dir = getStorageDir(context);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return matches;

        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName())); // newest first

        for (File f : files) {
            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Match m = jsonToMatch(new JSONObject(sb.toString()));
                m.setSavedFileName(f.getName());
                matches.add(m);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse " + f.getName(), e);
            }
        }
        return matches;
    }

    // ─── Pagination ───────────────────────────────────────────────────────────

    public static int getTotalPages(List<?> list, int pageSize) {
        if (list.isEmpty()) return 0;
        return (int) Math.ceil((double) list.size() / pageSize);
    }

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
    // Serialisation  (Match → JSON)
    // ═════════════════════════════════════════════════════════════════════════

    private static JSONObject matchToJson(Match m) throws Exception {
        JSONObject o = new JSONObject();
        o.put("homeTeamName",      m.getHomeTeamName());
        o.put("awayTeamName",      m.getAwayTeamName());
        o.put("maxOvers",          m.getMaxOvers());
        o.put("battingFirstTeam",  m.getBattingFirstTeam());
        o.put("singleBatsmanMode", m.isSingleBatsmanMode());
        o.put("matchCompleted",    m.isMatchCompleted());
        o.put("winnerTeam",        nvl(m.getWinnerTeam()));
        o.put("resultDescription", nvl(m.getResultDescription()));
        o.put("homePlayers",  playersToJson(m.getHomePlayers()));
        o.put("awayPlayers",  playersToJson(m.getAwayPlayers()));
        o.put("firstInnings",  m.getFirstInnings()  != null
                ? inningsToJson(m.getFirstInnings())  : JSONObject.NULL);
        o.put("secondInnings", m.getSecondInnings() != null
                ? inningsToJson(m.getSecondInnings()) : JSONObject.NULL);
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
            o.put("dismissal",    nvl(p.getDismissalInfo()));
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

        // ── Bowling maps (all four) ────────────────────────────────────────
        o.put("bowlerOversMap",   intMapToJson(inn.getBowlerOversMap()));
        o.put("bowlerRunsMap",    intMapToJson(inn.getBowlerRunsMap()));
        o.put("bowlerWicketsMap", intMapToJson(inn.getBowlerWicketsMap()));
        o.put("bowlerBallsMap",   intMapToJson(inn.getBowlerBallsMap()));

        // ── Overs (completed + current partial) ───────────────────────────
        JSONArray overs = new JSONArray();
        for (Over ov : inn.getAllOvers()) overs.put(overToJson(ov));
        o.put("overs", overs);
        return o;
    }

    private static JSONObject overToJson(Over ov) throws Exception {
        JSONObject o = new JSONObject();
        o.put("overNumber",  ov.getOverNumber());
        o.put("bowlerIndex", ov.getBowlerIndex());
        o.put("bowlerName",  nvl(ov.getBowlerName()));
        JSONArray balls = new JSONArray();
        for (Ball b : ov.getBalls()) {
            JSONObject bo = new JSONObject();
            bo.put("type",  b.getType().name());
            bo.put("runs",  b.getRuns());
            bo.put("valid", b.isValid());
            balls.put(bo);
        }
        o.put("balls", balls);
        return o;
    }

    /** Serialises a String→Integer map as a JSON object. */
    private static JSONObject intMapToJson(Map<String, Integer> map) throws Exception {
        JSONObject o = new JSONObject();
        if (map != null) {
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
        }
        return o;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Deserialisation  (JSON → Match)
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

        // ── Restore bowling maps (all four) ───────────────────────────────
        inn.setBowlerOversMap(jsonToIntMap(o.optJSONObject("bowlerOversMap")));
        inn.setBowlerRunsMap(jsonToIntMap(o.optJSONObject("bowlerRunsMap")));
        inn.setBowlerWicketsMap(jsonToIntMap(o.optJSONObject("bowlerWicketsMap")));
        inn.setBowlerBallsMap(jsonToIntMap(o.optJSONObject("bowlerBallsMap")));

        // ── Restore overs ─────────────────────────────────────────────────
        JSONArray overs = o.optJSONArray("overs");
        if (overs != null) {
            for (int i = 0; i < overs.length(); i++) {
                JSONObject ov   = overs.getJSONObject(i);
                Over       over = new Over(ov.getInt("overNumber"));
                over.setBowlerIndex(ov.optInt("bowlerIndex", -1));
                over.setBowlerName(ov.optString("bowlerName", ""));
                JSONArray balls = ov.optJSONArray("balls");
                if (balls != null) {
                    for (int j = 0; j < balls.length(); j++) {
                        JSONObject bo   = balls.getJSONObject(j);
                        Ball.BallType t = Ball.BallType.valueOf(bo.getString("type"));
                        Ball ball;
                        switch (t) {
                            case WIDE:    ball = Ball.wide();                   break;
                            case NO_BALL: ball = Ball.noBall();                 break;
                            case WICKET:  ball = Ball.wicket();                 break;
                            default:      ball = Ball.normal(bo.optInt("runs",0)); break;
                        }
                        over.addBall(ball);
                    }
                }
                inn.getCompletedOvers().add(over);
            }
        }
        return inn;
    }

    /** Deserialises a JSON object into a String→Integer map. */
    private static Map<String, Integer> jsonToIntMap(JSONObject obj) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        if (obj == null) return map;
        JSONArray keys = obj.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.getString(i);
                map.put(k, obj.getInt(k));
            }
        }
        return map;
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private static String sanitize(String s) {
        if (s == null) return "Unknown";
        return s.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
