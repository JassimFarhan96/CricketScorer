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
import java.util.ArrayList;
import java.util.List;

/**
 * LiveMatchState.java
 *
 * Manages a single JSON file — live_match.json — that acts as the
 * auto-save slot for the currently in-progress match.
 *
 * Contract:
 *   • After every delivery, InningsActivity calls persist(context, match).
 *   • When the match completes (win / all-out / overs done), StatsActivity
 *     calls clear(context) so the file is empty for the next match.
 *   • On app launch, HomeActivity calls hasSavedState(context).
 *     If true it calls restore(context) and jumps straight to the correct
 *     mid-match screen (InningsActivity or InningsBreakActivity).
 *
 * The JSON schema is identical to MatchStorage so both layers share the
 * same serialisation helpers (duplicated here to keep the classes
 * self-contained and avoid tight coupling).
 *
 * File location: context.getFilesDir()/live_match.json
 */
public class LiveMatchState {

    private static final String TAG      = "LiveMatchState";
    private static final String FILENAME = "live_match.json";

    // ─── File reference ───────────────────────────────────────────────────────

    private static File getFile(Context context) {
        return new File(context.getFilesDir(), FILENAME);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns true if live_match.json exists and contains valid JSON
     * (i.e. a match is in progress and needs to be restored).
     */
    public static boolean hasSavedState(Context context) {
        File f = getFile(context);
        if (!f.exists() || f.length() == 0) return false;
        try {
            String content = readFile(f);
            new JSONObject(content); // parse-check
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Serialises the current Match state and writes it to live_match.json.
     * Called after every ball so the file always reflects the latest state.
     */
    public static void persist(Context context, Match match) {
        try {
            JSONObject json = matchToJson(match);
            writeFile(getFile(context), json.toString(2));
        } catch (Exception e) {
            Log.e(TAG, "persist failed", e);
        }
    }

    /**
     * Deserialises live_match.json back into a Match object.
     * Returns null if the file is missing, empty, or corrupt.
     */
    public static Match restore(Context context) {
        File f = getFile(context);
        if (!f.exists() || f.length() == 0) return null;
        try {
            String     content = readFile(f);
            JSONObject json    = new JSONObject(content);
            return jsonToMatch(json);
        } catch (Exception e) {
            Log.e(TAG, "restore failed", e);
            return null;
        }
    }

    /**
     * Empties live_match.json.
     * Called when a match finishes so the next launch starts fresh.
     */
    public static void clear(Context context) {
        try {
            writeFile(getFile(context), "");
            Log.d(TAG, "Live match state cleared");
        } catch (Exception e) {
            Log.e(TAG, "clear failed", e);
        }
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
        o.put("currentInnings",    m.getCurrentInnings());
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

        // Persist ALL overs — both completed and the current partial over
        JSONArray oversArr = new JSONArray();
        for (Over ov : inn.getCompletedOvers())  oversArr.put(overToJson(ov));
        if (inn.getCurrentOver() != null)         oversArr.put(overToJson(inn.getCurrentOver()));
        o.put("completedOvers",  new JSONArray(oversArr.toString())); // will split on restore
        o.put("currentOverNum",  inn.getCurrentOver() != null
                ? inn.getCurrentOver().getOverNumber() : inn.getCompletedOvers().size() + 1);
        return o;
    }

    private static JSONObject overToJson(Over ov) throws Exception {
        JSONObject o = new JSONObject();
        o.put("overNumber", ov.getOverNumber());
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

    // ═════════════════════════════════════════════════════════════════════════
    // Deserialisation  (JSON → Match)
    // ═════════════════════════════════════════════════════════════════════════

    private static Match jsonToMatch(JSONObject o) throws Exception {
        Match m = new Match();
        m.setHomeTeamName(o.getString("homeTeamName"));
        m.setAwayTeamName(o.getString("awayTeamName"));
        m.setMaxOvers(o.getInt("maxOvers"));
        m.setBattingFirstTeam(o.getString("battingFirstTeam"));
        m.setCurrentInnings(o.optInt("currentInnings", 1));
        m.setSingleBatsmanMode(o.optBoolean("singleBatsmanMode", false));
        m.setMatchCompleted(o.optBoolean("matchCompleted", false));
        m.setWinnerTeam(o.optString("winnerTeam", null));
        m.setResultDescription(o.optString("resultDescription", null));
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

    /**
     * Restores an Innings from JSON.
     *
     * The saved data contains ALL overs in one array (completedOvers).
     * The last entry in that array is the current (possibly partial) over;
     * all previous entries are completed overs.
     *
     * currentOverNum tells us which over number is the active one so we
     * can correctly split completed vs current.
     */
    private static Innings jsonToInnings(JSONObject o) throws Exception {
        boolean single     = o.optBoolean("singleBatsmanMode", false);
        Innings inn        = new Innings(o.getInt("inningsNumber"), single);

        // Restore aggregate counters
        inn.setTotalRuns(o.optInt("totalRuns", 0));
        inn.setTotalWickets(o.optInt("totalWickets", 0));
        inn.setTotalValidBalls(o.optInt("totalValidBalls", 0));
        inn.setStrikerIndex(o.optInt("strikerIndex", 0));
        inn.setNonStrikerIndex(o.optInt("nonStrikerIndex", single ? -1 : 1));
        inn.setNextBatsmanIndex(o.optInt("nextBatsmanIndex", single ? 1 : 2));
        inn.setComplete(o.optBoolean("complete", false));

        int currentOverNum = o.optInt("currentOverNum", 1);

        JSONArray oversArr = o.optJSONArray("completedOvers");
        if (oversArr != null) {
            for (int i = 0; i < oversArr.length(); i++) {
                JSONObject ov   = oversArr.getJSONObject(i);
                int        ovNum = ov.getInt("overNumber");
                Over       over  = new Over(ovNum);

                JSONArray ballsArr = ov.optJSONArray("balls");
                if (ballsArr != null) {
                    for (int j = 0; j < ballsArr.length(); j++) {
                        JSONObject bo = ballsArr.getJSONObject(j);
                        over.addBall(jsonToBall(bo));
                    }
                }

                if (ovNum == currentOverNum) {
                    // This is the partial (current) over — set as currentOver
                    inn.setCurrentOver(over);
                } else {
                    // Completed over — add to the list directly
                    inn.getCompletedOvers().add(over);
                }
            }
        }

        // Safety: if no currentOver was set, start a fresh one
        if (inn.getCurrentOver() == null) {
            inn.startNewOver();
        }

        return inn;
    }

    private static Ball jsonToBall(JSONObject bo) throws Exception {
        Ball.BallType type = Ball.BallType.valueOf(bo.getString("type"));
        switch (type) {
            case WIDE:    return Ball.wide();
            case NO_BALL: return Ball.noBall();
            case WICKET:  return Ball.wicket();
            default:      return Ball.normal(bo.optInt("runs", 0));
        }
    }

    // ─── File I/O helpers ─────────────────────────────────────────────────────

    private static String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static void writeFile(File f, String content) throws Exception {
        FileWriter fw = new FileWriter(f, false); // false = overwrite
        fw.write(content);
        fw.flush();
        fw.close();
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
