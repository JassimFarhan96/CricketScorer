package com.cricket.scorer.utils;

import android.content.Context;

import com.cricket.scorer.models.Player;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.models.TournamentMatch;
import com.cricket.scorer.models.TournamentTeam;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * TournamentStorage.java
 *
 * Persists the active tournament to tournament_tracker.json in app's
 * private files dir. Restored on app launch so user resumes from
 * the exact same state.
 *
 *   save(ctx, tournament) — overwrite tournament_tracker.json
 *   load(ctx)             — returns Tournament or null if no active tournament
 *   clear(ctx)            — delete the tracker file (when tournament finishes)
 *   exists(ctx)           — true if a tracker file is on disk
 */
public class TournamentStorage {

    private static final String FILE_NAME = "tournament_tracker.json";

    public static File trackerFile(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    public static boolean exists(Context ctx) { return trackerFile(ctx).exists(); }

    public static void clear(Context ctx) {
        File f = trackerFile(ctx);
        if (f.exists()) f.delete();
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    public static void save(Context ctx, Tournament t) {
        if (t == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("playersPerTeam",    t.getPlayersPerTeam());
            root.put("maxOversPerMatch",  t.getMaxOversPerMatch());
            root.put("singleBatsmanMode", t.isSingleBatsmanMode());
            root.put("stage",             t.getStage().name());
            root.put("currentMatchIndex", t.getCurrentMatchIndex());
            root.put("championName",      nvl(t.getChampionName()));

            // Teams
            JSONArray teamsArr = new JSONArray();
            for (TournamentTeam team : t.getTeams()) {
                JSONObject to = new JSONObject();
                to.put("name",             nvl(team.getName()));
                to.put("played",           team.getPlayed());
                to.put("wins",             team.getWins());
                to.put("losses",           team.getLosses());
                to.put("totalRunsFor",     team.getTotalRunsFor());
                to.put("totalRunsAgainst", team.getTotalRunsAgainst());
                to.put("totalBallsFaced",  team.getTotalBallsFaced());
                to.put("totalBallsBowled", team.getTotalBallsBowled());
                JSONArray players = new JSONArray();
                for (Player p : team.getPlayers()) players.put(nvl(p.getName()));
                to.put("players", players);
                teamsArr.put(to);
            }
            root.put("teams", teamsArr);

            root.put("leagueFixtures", fixturesToJson(t.getLeagueFixtures()));
            root.put("semiFixtures",   fixturesToJson(t.getSemiFixtures()));
            if (t.getFinalFixture() != null) {
                JSONArray f = new JSONArray();
                f.put(matchToJson(t.getFinalFixture()));
                root.put("finalFixture", f);
            }

            try (FileOutputStream fos = new FileOutputStream(trackerFile(ctx))) {
                fos.write(root.toString(2).getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Load ─────────────────────────────────────────────────────────────────

    public static Tournament load(Context ctx) {
        File f = trackerFile(ctx);
        if (!f.exists()) return null;
        try (InputStream is = new java.io.FileInputStream(f)) {
            String json = new Scanner(is).useDelimiter("\\A").next();
            JSONObject root = new JSONObject(json);

            Tournament t = new Tournament();
            t.setPlayersPerTeam(root.optInt("playersPerTeam", 11));
            t.setMaxOversPerMatch(root.optInt("maxOversPerMatch", 6));
            t.setSingleBatsmanMode(root.optBoolean("singleBatsmanMode", false));
            try {
                t.setStage(Tournament.Stage.valueOf(root.optString("stage", "LEAGUE")));
            } catch (Exception e) { t.setStage(Tournament.Stage.LEAGUE); }
            t.setCurrentMatchIndex(root.optInt("currentMatchIndex", 0));
            t.setChampionName(root.optString("championName", null));

            // Teams
            List<TournamentTeam> teams = new ArrayList<>();
            JSONArray teamsArr = root.optJSONArray("teams");
            if (teamsArr != null) {
                for (int i = 0; i < teamsArr.length(); i++) {
                    JSONObject to = teamsArr.getJSONObject(i);
                    TournamentTeam team = new TournamentTeam(to.optString("name", ""));
                    team.setPlayed(to.optInt("played", 0));
                    team.setWins(to.optInt("wins", 0));
                    team.setLosses(to.optInt("losses", 0));
                    team.setTotalRunsFor(to.optInt("totalRunsFor", 0));
                    team.setTotalRunsAgainst(to.optInt("totalRunsAgainst", 0));
                    team.setTotalBallsFaced(to.optInt("totalBallsFaced", 0));
                    team.setTotalBallsBowled(to.optInt("totalBallsBowled", 0));
                    JSONArray players = to.optJSONArray("players");
                    List<Player> playerList = new ArrayList<>();
                    if (players != null) {
                        for (int j = 0; j < players.length(); j++) {
                            playerList.add(new Player(players.optString(j, "")));
                        }
                    }
                    team.setPlayers(playerList);
                    teams.add(team);
                }
            }
            t.setTeams(teams);

            t.setLeagueFixtures(fixturesFromJson(root.optJSONArray("leagueFixtures")));
            t.setSemiFixtures(fixturesFromJson(root.optJSONArray("semiFixtures")));

            JSONArray finalArr = root.optJSONArray("finalFixture");
            if (finalArr != null && finalArr.length() > 0) {
                t.setFinalFixture(matchFromJson(finalArr.getJSONObject(0)));
            }
            return t;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static JSONArray fixturesToJson(List<TournamentMatch> list) throws Exception {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (TournamentMatch m : list) arr.put(matchToJson(m));
        return arr;
    }

    private static List<TournamentMatch> fixturesFromJson(JSONArray arr) throws Exception {
        List<TournamentMatch> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) out.add(matchFromJson(arr.getJSONObject(i)));
        return out;
    }

    private static JSONObject matchToJson(TournamentMatch m) throws Exception {
        JSONObject mo = new JSONObject();
        mo.put("teamAName",  nvl(m.getTeamAName()));
        mo.put("teamBName",  nvl(m.getTeamBName()));
        mo.put("completed",  m.isCompleted());
        mo.put("winnerName", nvl(m.getWinnerName()));
        mo.put("teamAScore", m.getTeamAScore());
        mo.put("teamBScore", m.getTeamBScore());
        mo.put("resultDescription", nvl(m.getResultDescription()));
        mo.put("savedMatchFile",    nvl(m.getSavedMatchFile()));
        return mo;
    }

    private static TournamentMatch matchFromJson(JSONObject mo) {
        TournamentMatch m = new TournamentMatch(
                mo.optString("teamAName", ""), mo.optString("teamBName", ""));
        m.setCompleted(mo.optBoolean("completed", false));
        m.setWinnerName(mo.optString("winnerName", ""));
        m.setTeamAScore(mo.optInt("teamAScore", 0));
        m.setTeamBScore(mo.optInt("teamBScore", 0));
        m.setResultDescription(mo.optString("resultDescription", ""));
        m.setSavedMatchFile(mo.optString("savedMatchFile", ""));
        return m;
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    // ─── Archive (recent_tournaments/) ────────────────────────────────────────
    //
    // When a tournament finishes, save() to tracker should be cleared and the
    // full result archived in <filesDir>/recent_tournaments/<timestamp>_<champ>.json
    // RecentTournamentsActivity lists these; TournamentDetailsActivity loads one.

    private static final String ARCHIVE_DIR = "recent_tournaments";

    public static java.io.File archiveDir(Context ctx) {
        java.io.File d = new java.io.File(ctx.getFilesDir(), ARCHIVE_DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /**
     * Saves the completed tournament to recent_tournaments/<timestamp>_<champ>.json.
     * Returns the file written, or null on error.
     */
    public static java.io.File archiveCompleted(Context ctx, Tournament t) {
        if (t == null) return null;
        try {
            // Serialize the same way as save(), but to the archive directory
            JSONObject root = buildJson(t);
            root.put("savedAtMillis", System.currentTimeMillis());

            String champ = t.getChampionName() != null ? t.getChampionName() : "unknown";
            champ = champ.replaceAll("[^A-Za-z0-9]", "_");
            String fname = System.currentTimeMillis() + "_" + champ + ".json";
            java.io.File out = new java.io.File(archiveDir(ctx), fname);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(root.toString(2).getBytes());
            }
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Loads all archived tournaments, newest first.
     * Each Tournament has its savedAtMillis set in a transient field via
     * the convention key in JSON ("savedAtMillis").
     */
    public static java.util.List<ArchivedTournament> loadArchived(Context ctx) {
        java.util.List<ArchivedTournament> out = new ArrayList<>();
        java.io.File dir = archiveDir(ctx);
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return out;
        for (java.io.File f : files) {
            try (InputStream is = new java.io.FileInputStream(f)) {
                String json = new Scanner(is).useDelimiter("\\A").next();
                JSONObject root = new JSONObject(json);
                Tournament t = parseTournament(root);
                long when = root.optLong("savedAtMillis", f.lastModified());
                out.add(new ArchivedTournament(t, when, f.getName()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Sort newest first
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.savedAtMillis, a.savedAtMillis));
        return out;
    }

    /** Loads a single archived tournament by file name. */
    public static Tournament loadArchivedByName(Context ctx, String fileName) {
        java.io.File f = new java.io.File(archiveDir(ctx), fileName);
        if (!f.exists()) return null;
        try (InputStream is = new java.io.FileInputStream(f)) {
            String json = new Scanner(is).useDelimiter("\\A").next();
            return parseTournament(new JSONObject(json));
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    /** Wrapper holding an archived tournament + its save timestamp + filename. */
    public static class ArchivedTournament {
        public final Tournament tournament;
        public final long       savedAtMillis;
        public final String     fileName;
        public ArchivedTournament(Tournament t, long ts, String fn) {
            this.tournament = t; this.savedAtMillis = ts; this.fileName = fn;
        }
    }

    // ─── Refactor: reusable JSON build / parse used by both save() and archive() ─

    private static JSONObject buildJson(Tournament t) throws Exception {
        JSONObject root = new JSONObject();
        root.put("playersPerTeam",    t.getPlayersPerTeam());
        root.put("maxOversPerMatch",  t.getMaxOversPerMatch());
        root.put("singleBatsmanMode", t.isSingleBatsmanMode());
        root.put("stage",             t.getStage().name());
        root.put("currentMatchIndex", t.getCurrentMatchIndex());
        root.put("championName",      nvl(t.getChampionName()));

        JSONArray teamsArr = new JSONArray();
        for (TournamentTeam team : t.getTeams()) {
            JSONObject to = new JSONObject();
            to.put("name",             nvl(team.getName()));
            to.put("played",           team.getPlayed());
            to.put("wins",             team.getWins());
            to.put("losses",           team.getLosses());
            to.put("totalRunsFor",     team.getTotalRunsFor());
            to.put("totalRunsAgainst", team.getTotalRunsAgainst());
            to.put("totalBallsFaced",  team.getTotalBallsFaced());
            to.put("totalBallsBowled", team.getTotalBallsBowled());
            JSONArray players = new JSONArray();
            for (Player p : team.getPlayers()) players.put(nvl(p.getName()));
            to.put("players", players);
            teamsArr.put(to);
        }
        root.put("teams", teamsArr);

        root.put("leagueFixtures", fixturesToJson(t.getLeagueFixtures()));
        root.put("semiFixtures",   fixturesToJson(t.getSemiFixtures()));
        if (t.getFinalFixture() != null) {
            JSONArray f = new JSONArray();
            f.put(matchToJson(t.getFinalFixture()));
            root.put("finalFixture", f);
        }
        return root;
    }

    private static Tournament parseTournament(JSONObject root) throws Exception {
        Tournament t = new Tournament();
        t.setPlayersPerTeam(root.optInt("playersPerTeam", 11));
        t.setMaxOversPerMatch(root.optInt("maxOversPerMatch", 6));
        t.setSingleBatsmanMode(root.optBoolean("singleBatsmanMode", false));
        try {
            t.setStage(Tournament.Stage.valueOf(root.optString("stage", "LEAGUE")));
        } catch (Exception e) { t.setStage(Tournament.Stage.LEAGUE); }
        t.setCurrentMatchIndex(root.optInt("currentMatchIndex", 0));
        t.setChampionName(root.optString("championName", null));

        java.util.List<TournamentTeam> teams = new ArrayList<>();
        JSONArray teamsArr = root.optJSONArray("teams");
        if (teamsArr != null) {
            for (int i = 0; i < teamsArr.length(); i++) {
                JSONObject to = teamsArr.getJSONObject(i);
                TournamentTeam team = new TournamentTeam(to.optString("name", ""));
                team.setPlayed(to.optInt("played", 0));
                team.setWins(to.optInt("wins", 0));
                team.setLosses(to.optInt("losses", 0));
                team.setTotalRunsFor(to.optInt("totalRunsFor", 0));
                team.setTotalRunsAgainst(to.optInt("totalRunsAgainst", 0));
                team.setTotalBallsFaced(to.optInt("totalBallsFaced", 0));
                team.setTotalBallsBowled(to.optInt("totalBallsBowled", 0));
                JSONArray players = to.optJSONArray("players");
                java.util.List<Player> playerList = new ArrayList<>();
                if (players != null) {
                    for (int j = 0; j < players.length(); j++) {
                        playerList.add(new Player(players.optString(j, "")));
                    }
                }
                team.setPlayers(playerList);
                teams.add(team);
            }
        }
        t.setTeams(teams);

        t.setLeagueFixtures(fixturesFromJson(root.optJSONArray("leagueFixtures")));
        t.setSemiFixtures(fixturesFromJson(root.optJSONArray("semiFixtures")));

        JSONArray finalArr = root.optJSONArray("finalFixture");
        if (finalArr != null && finalArr.length() > 0) {
            t.setFinalFixture(matchFromJson(finalArr.getJSONObject(0)));
        }
        return t;
    }
}
