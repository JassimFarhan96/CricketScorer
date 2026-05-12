package com.cricket.scorer.utils;

import android.content.Context;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LiveMatchState.java
 * CHANGE: Persists/restores bowlerRunsMap, bowlerWicketsMap, bowlerBallsMap
 * in addition to the existing bowlerOversMap.
 */
public class LiveMatchState {

    private static final String TAG      = "LiveMatchState";
    private static final String FILENAME = "live_match.json";

    public static File getFile(Context context) {
        return new File(context.getFilesDir(), FILENAME);
    }

    public static boolean hasSavedState(Context context) {
        File f = getFile(context);
        if (!f.exists() || f.length() == 0) return false;
        try { new JSONObject(readFile(f)); return true; }
        catch (Exception e) { return false; }
    }

    public static void persist(Context context, Match match) {
        try { writeFile(getFile(context), matchToJson(match).toString(2)); }
        catch (Exception e) { AppLogger.e(TAG, "persist failed", e); }
    }

    public static Match restore(Context context) {
        File f = getFile(context);
        if (!f.exists() || f.length() == 0) return null;
        try { return jsonToMatch(new JSONObject(readFile(f))); }
        catch (Exception e) { AppLogger.e(TAG, "restore failed", e); return null; }
    }

    public static void clear(Context context) {
        try { writeFile(getFile(context), ""); }
        catch (Exception e) { AppLogger.e(TAG, "clear failed", e); }
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

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
        // Joker fields
        o.put("hasJoker",          m.hasJoker());
        o.put("jokerName",         nvl(m.getJokerName()));
        o.put("jokerRole",         m.getJokerRole().name());
        o.put("homePlayers",  playersToJson(m.getHomePlayers()));
        o.put("awayPlayers",  playersToJson(m.getAwayPlayers()));
        o.put("firstInnings",  m.getFirstInnings()  != null ? inningsToJson(m.getFirstInnings())  : JSONObject.NULL);
        o.put("secondInnings", m.getSecondInnings() != null ? inningsToJson(m.getSecondInnings()) : JSONObject.NULL);
        return o;
    }

    private static JSONArray playersToJson(List<Player> players) throws Exception {
        JSONArray arr = new JSONArray();
        if (players == null) return arr;
        for (Player p : players) {
            JSONObject o = new JSONObject();
            o.put("name", p.getName()); o.put("runsScored", p.getRunsScored());
            o.put("ballsFaced", p.getBallsFaced()); o.put("fours", p.getFours());
            o.put("sixes", p.getSixes()); o.put("out", p.isOut());
            o.put("hasNotBatted", p.isHasNotBatted());
            o.put("dismissal", nvl(p.getDismissalInfo()));
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
        o.put("currentBowlerIndex", inn.getCurrentBowlerIndex());
        o.put("currentBowlerName",  nvl(inn.getCurrentBowlerName()));
        o.put("bowlerSelected",     inn.isBowlerSelected());
        // Baby over fields
        o.put("babyOverActivated",        inn.isBabyOverActivated());
        o.put("currentSecondBowlerIndex", inn.getCurrentSecondBowlerIndex());
        o.put("currentSecondBowlerName",  nvl(inn.getCurrentSecondBowlerName()));
        o.put("secondBowlerSelected",     inn.isSecondBowlerSelected());
        o.put("bowlerOversMap",   mapToJson(inn.getBowlerOversMap()));
        o.put("bowlerRunsMap",    mapToJson(inn.getBowlerRunsMap()));
        o.put("bowlerWicketsMap", mapToJson(inn.getBowlerWicketsMap()));
        o.put("bowlerBallsMap",   mapToJson(inn.getBowlerBallsMap()));

        JSONArray oversArr = new JSONArray();
        for (Over ov : inn.getCompletedOvers())  oversArr.put(overToJson(ov));
        if (inn.getCurrentOver() != null)         oversArr.put(overToJson(inn.getCurrentOver()));
        o.put("completedOvers", oversArr);
        o.put("currentOverNum", inn.getCurrentOver() != null
                ? inn.getCurrentOver().getOverNumber()
                : inn.getCompletedOvers().size() + 1);
        return o;
    }

    private static JSONObject overToJson(Over ov) throws Exception {
        JSONObject o = new JSONObject();
        o.put("overNumber",  ov.getOverNumber());
        o.put("bowlerIndex", ov.getBowlerIndex());
        o.put("bowlerName",  nvl(ov.getBowlerName()));
        // Baby over fields
        o.put("isBabyOver",           ov.isBabyOver());
        o.put("secondBowlerIndex",    ov.getSecondBowlerIndex());
        o.put("secondBowlerName",     nvl(ov.getSecondBowlerName()));
        o.put("secondBowlerFromBall", ov.getSecondBowlerFromBall());
        JSONArray balls = new JSONArray();
        for (Ball b : ov.getBalls()) {
            JSONObject bo = new JSONObject();
            bo.put("type", b.getType().name()); bo.put("runs", b.getRuns()); bo.put("valid", b.isValid());
            balls.put(bo);
        }
        o.put("balls", balls);
        return o;
    }

    private static JSONObject mapToJson(Map<String, Integer> map) throws Exception {
        JSONObject o = new JSONObject();
        if (map != null) for (Map.Entry<String, Integer> e : map.entrySet()) o.put(e.getKey(), e.getValue());
        return o;
    }

    // ── Deserialisation ───────────────────────────────────────────────────────

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
        // Joker fields
        m.setHasJoker(o.optBoolean("hasJoker", false));
        m.setJokerName(o.optString("jokerName", ""));
        try {
            m.setJokerRole(com.cricket.scorer.models.Match.JokerRole.valueOf(
                    o.optString("jokerRole", "NONE")));
        } catch (Exception e) {
            m.setJokerRole(com.cricket.scorer.models.Match.JokerRole.NONE);
        }
        m.setHomePlayers(jsonToPlayers(o.getJSONArray("homePlayers")));
        m.setAwayPlayers(jsonToPlayers(o.getJSONArray("awayPlayers")));
        if (!o.isNull("firstInnings"))  m.setFirstInnings(jsonToInnings(o.getJSONObject("firstInnings")));
        if (!o.isNull("secondInnings")) m.setSecondInnings(jsonToInnings(o.getJSONObject("secondInnings")));
        return m;
    }

    private static List<Player> jsonToPlayers(JSONArray arr) throws Exception {
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Player p = new Player(o.getString("name"));
            p.setRunsScored(o.optInt("runsScored", 0)); p.setBallsFaced(o.optInt("ballsFaced", 0));
            p.setFours(o.optInt("fours", 0)); p.setSixes(o.optInt("sixes", 0));
            p.setOut(o.optBoolean("out", false)); p.setHasNotBatted(o.optBoolean("hasNotBatted", true));
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
        inn.setComplete(o.optBoolean("complete", false));
        inn.setCurrentBowlerIndex(o.optInt("currentBowlerIndex", -1));
        inn.setCurrentBowlerName(o.optString("currentBowlerName", ""));
        inn.setBowlerSelected(o.optBoolean("bowlerSelected", false));
        // Baby over fields
        inn.setBabyOverActivated(o.optBoolean("babyOverActivated", false));
        inn.setCurrentSecondBowlerIndex(o.optInt("currentSecondBowlerIndex", -1));
        inn.setCurrentSecondBowlerName(o.optString("currentSecondBowlerName", ""));
        inn.setSecondBowlerSelected(o.optBoolean("secondBowlerSelected", false));
        inn.setBowlerOversMap(jsonToIntMap(o.optJSONObject("bowlerOversMap")));
        inn.setBowlerRunsMap(jsonToIntMap(o.optJSONObject("bowlerRunsMap")));
        inn.setBowlerWicketsMap(jsonToIntMap(o.optJSONObject("bowlerWicketsMap")));
        inn.setBowlerBallsMap(jsonToIntMap(o.optJSONObject("bowlerBallsMap")));

        int currentOverNum = o.optInt("currentOverNum", 1);
        JSONArray oversArr = o.optJSONArray("completedOvers");
        if (oversArr != null) {
            for (int i = 0; i < oversArr.length(); i++) {
                JSONObject ov = oversArr.getJSONObject(i);
                int ovNum = ov.getInt("overNumber");
                Over over = new Over(ovNum);
                over.setBowlerIndex(ov.optInt("bowlerIndex", -1));
                over.setBowlerName(ov.optString("bowlerName", ""));
                // Baby over fields
                over.setBabyOver(ov.optBoolean("isBabyOver", false));
                over.setSecondBowlerIndex(ov.optInt("secondBowlerIndex", -1));
                over.setSecondBowlerName(ov.optString("secondBowlerName", ""));
                over.setSecondBowlerFromBall(ov.optInt("secondBowlerFromBall", 4));
                JSONArray ballsArr = ov.optJSONArray("balls");
                if (ballsArr != null) for (int j = 0; j < ballsArr.length(); j++) {
                    JSONObject bo = ballsArr.getJSONObject(j);
                    Ball.BallType type = Ball.BallType.valueOf(bo.getString("type"));
                    Ball ball;
                    switch (type) {
                        case WIDE:    ball = Ball.wide();                   break;
                        case NO_BALL: ball = Ball.noBall();                 break;
                        case WICKET:  ball = Ball.wicket();                 break;
                        default:      ball = Ball.normal(bo.optInt("runs",0)); break;
                    }
                    over.addBall(ball);
                }
                if (ovNum == currentOverNum) inn.setCurrentOver(over);
                else inn.getCompletedOvers().add(over);
            }
        }
        if (inn.getCurrentOver() == null) inn.startNewOver();
        return inn;
    }

    private static Map<String, Integer> jsonToIntMap(JSONObject obj) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        if (obj == null) return map;
        JSONArray keys = obj.names();
        if (keys != null) for (int i = 0; i < keys.length(); i++) {
            String k = keys.getString(i); map.put(k, obj.getInt(k));
        }
        return map;
    }

    private static String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line; while ((line = br.readLine()) != null) sb.append(line);
        br.close(); return sb.toString();
    }

    private static void writeFile(File f, String content) throws Exception {
        FileWriter fw = new FileWriter(f, false); fw.write(content); fw.flush(); fw.close();
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
