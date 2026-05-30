package com.cricket.scorer.utils;

import android.content.Context;

import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.models.TournamentTeam;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * PlayerNameSuggestionsUtil.java
 *
 * Manages a persistent cache of player names at:
 *   filesDir/player_name_cache.json
 *
 * Cache format:
 *   {
 *     "fileCount": 42,
 *     "names": [
 *       { "name": "Rahul",  "count": 15 },
 *       { "name": "Virat",  "count": 9  },
 *       { "name": "Rohit",  "count": 3  }
 *     ]
 *   }
 *
 * ── Three operations ──────────────────────────────────────────────────────
 *
 * 1. buildSuggestions(ctx)
 *    Called when the setup screen opens.
 *    - If cache exists and fileCount matches live count → return cached names
 *      instantly (~2ms, no file reads).
 *    - If cache is missing or stale (first install, restore) → full scan of
 *      all match/tournament files, write cache, return names.
 *
 * 2. mergeMatchNames(ctx, match)
 *    Called immediately after MatchStorage saves a match.
 *    - Reads the cache (small single file).
 *    - Merges only the CURRENT match's player names into it:
 *        new name → added with count 1
 *        existing name → count incremented
 *    - Increments fileCount by 1.
 *    - Writes updated cache back.
 *    No historical files read. O(players_per_match) ≈ O(22) operations.
 *
 * 3. mergeTournamentNames(ctx, tournament)
 *    Same as above but for a tournament's team rosters.
 *    fileCount incremented by 1 regardless of how many team files changed
 *    (tournament tracker is a single file).
 *
 * ── Why NOT invalidate ────────────────────────────────────────────────────
 *    The old approach deleted the cache after each save, forcing a full
 *    rebuild on the next setup-screen open. With large match histories
 *    (1000+ files) that rebuild takes ~600ms every time a match is saved.
 *    Incremental merging reduces that to ~2ms per save and ~2ms per load,
 *    regardless of total match count.
 *
 * ── Known minor limitation ────────────────────────────────────────────────
 *    If match files are deleted (e.g. via a selective restore), the cache
 *    may still list those players' names. This is harmless — the user sees
 *    an extra suggestion they can ignore. A full rebuild can be forced by
 *    calling forceRebuild(ctx) — used after restore completes.
 */
public final class PlayerNameSuggestionsUtil {

    private static final String TAG        = "PlayerNameCache";
    private static final String CACHE_FILE = "player_name_cache.json";

    private static final String[] SCAN_DIRS = {
            "recent_matches",
            "recent_tournaments",
            "recent_tournaments/matches"
    };

    private PlayerNameSuggestionsUtil() {}

    // ── 1. buildSuggestions ───────────────────────────────────────────────────

    /**
     * Returns frequency-sorted player name suggestions.
     * Uses cache when valid; falls back to full scan otherwise.
     * Safe to call from a background thread.
     */
    public static List<String> buildSuggestions(Context ctx) {
        int liveCount = countJsonFiles(ctx);
        AppLogger.d(TAG, "buildSuggestions: live file count = " + liveCount);

        List<String> cached = tryLoadFromCache(ctx, liveCount);
        if (cached != null) {
            AppLogger.d(TAG, "buildSuggestions: CACHE HIT — returned " + cached.size()
                    + " suggestions from cache (no file scan needed)");
            return cached;
        }

        AppLogger.d(TAG, "buildSuggestions: CACHE MISS — triggering full scan");
        return fullScanAndCache(ctx, liveCount);
    }

    // ── 2. mergeMatchNames ────────────────────────────────────────────────────

    /**
     * Incrementally updates the cache with players from the given match.
     * Reads + writes only the small cache file. No historical files touched.
     * Safe to call from a background thread.
     */
    public static void mergeMatchNames(Context ctx, Match match) {
        if (match == null) {
            AppLogger.w(TAG, "mergeMatchNames: skipped — match is null");
            return;
        }
        List<String> names = new ArrayList<>();
        if (match.getHomePlayers() != null) {
            for (Player p : match.getHomePlayers()) addIfValid(names, p.getName());
        }
        if (match.getAwayPlayers() != null) {
            for (Player p : match.getAwayPlayers()) addIfValid(names, p.getName());
        }
        AppLogger.d(TAG, "mergeMatchNames: merging " + names.size()
                + " player names from match ["
                + match.getHomeTeamName() + " vs " + match.getAwayTeamName() + "]");
        mergeNamesIntoCache(ctx, names, 1);
    }

    // ── 3. mergeTournamentNames ───────────────────────────────────────────────

    /**
     * Incrementally updates the cache with players from all teams in the
     * given tournament. fileCount incremented by 1 (one tracker file saved).
     * Safe to call from a background thread.
     */
    public static void mergeTournamentNames(Context ctx, Tournament tournament) {
        if (tournament == null) {
            AppLogger.w(TAG, "mergeTournamentNames: skipped — tournament is null");
            return;
        }
        List<String> names = new ArrayList<>();
        if (tournament.getTeams() != null) {
            for (TournamentTeam team : tournament.getTeams()) {
                if (team.getPlayers() == null) continue;
                for (Player p : team.getPlayers()) addIfValid(names, p.getName());
            }
        }
        AppLogger.d(TAG, "mergeTournamentNames: merging " + names.size()
                + " player names from tournament ["
                + tournament.getTeams().size() + " teams]");
        mergeNamesIntoCache(ctx, names, 1);
    }

    /**
     * Forces a full rebuild of the cache on the next buildSuggestions() call.
     * Call this after a restore operation completes, since restored files may
     * contain players not yet in the cache, or the file count has changed.
     */
    public static void forceRebuild(Context ctx) {
        File cacheFile = new File(ctx.getFilesDir(), CACHE_FILE);
        boolean existed = cacheFile.isFile();
        boolean deleted = cacheFile.delete();
        if (existed) {
            if (deleted) {
                AppLogger.d(TAG, "forceRebuild: cache file deleted successfully"
                        + " — full rebuild will happen on next buildSuggestions() call");
            } else {
                AppLogger.e(TAG, "forceRebuild: FAILED to delete cache file at "
                        + cacheFile.getAbsolutePath()
                        + " — stale cache may be served until file system allows deletion");
            }
        } else {
            AppLogger.d(TAG, "forceRebuild: cache file did not exist — nothing to delete");
        }
    }

    // ── Core: merge a name list into the cache ────────────────────────────────

    private static void mergeNamesIntoCache(Context ctx, List<String> newNames, int fileCountDelta) {
        if (newNames.isEmpty() && fileCountDelta == 0) {
            AppLogger.d(TAG, "mergeNamesIntoCache: no names to merge and no fileCount change — skipped");
            return;
        }
        synchronized (CACHE_FILE) {
            CacheData data = loadCacheRaw(ctx);
            if (data == null) {
                AppLogger.d(TAG, "mergeNamesIntoCache: no existing cache — creating fresh cache"
                        + " from current file count");
                data = new CacheData(countJsonFiles(ctx), new HashMap<>(), new HashMap<>());
            } else {
                AppLogger.d(TAG, "mergeNamesIntoCache: loaded existing cache"
                        + " [" + data.displayName.size() + " names, fileCount=" + data.fileCount + "]");
            }

            int newCount  = 0;
            int incrCount = 0;
            for (String name : newNames) {
                String key = name.toLowerCase();
                if (!data.displayName.containsKey(key)) {
                    data.displayName.put(key, name);
                    data.freq.put(key, 1L);
                    newCount++;
                } else {
                    data.freq.put(key, data.freq.get(key) + 1);
                    incrCount++;
                }
            }

            int oldFileCount = data.fileCount;
            data.fileCount += fileCountDelta;

            AppLogger.d(TAG, "mergeNamesIntoCache: merge result"
                    + " — " + newCount + " new name(s) added"
                    + ", " + incrCount + " existing name(s) frequency incremented"
                    + ", fileCount " + oldFileCount + " → " + data.fileCount);

            writeCache(ctx, data);
        }
    }

    // ── Cache I/O ─────────────────────────────────────────────────────────────

    /** Tries to load names from cache if fileCount matches. Returns null on miss. */
    private static List<String> tryLoadFromCache(Context ctx, int liveCount) {
        File cacheFile = new File(ctx.getFilesDir(), CACHE_FILE);
        if (!cacheFile.isFile()) {
            AppLogger.d(TAG, "tryLoadFromCache: cache file does not exist — full scan required");
            return null;
        }
        try {
            String raw = readFile(cacheFile);
            if (raw == null) {
                AppLogger.e(TAG, "tryLoadFromCache: FAILED to read cache file at "
                        + cacheFile.getAbsolutePath() + " — full scan required");
                return null;
            }
            JSONObject root = new JSONObject(raw);
            int cachedCount = root.optInt("fileCount", -1);
            if (cachedCount != liveCount) {
                AppLogger.d(TAG, "tryLoadFromCache: cache STALE"
                        + " [cached fileCount=" + cachedCount
                        + ", live fileCount=" + liveCount + "]"
                        + " — full scan required");
                return null;
            }
            JSONArray arr = root.optJSONArray("names");
            if (arr == null) {
                AppLogger.e(TAG, "tryLoadFromCache: cache file is malformed"
                        + " — 'names' array missing — full scan required");
                return null;
            }
            List<String> result = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject entry = arr.optJSONObject(i);
                if (entry != null) {
                    String n = entry.optString("name", "").trim();
                    if (!n.isEmpty()) result.add(n);
                }
            }
            AppLogger.d(TAG, "tryLoadFromCache: cache VALID"
                    + " [fileCount=" + cachedCount + "]"
                    + " — loaded " + result.size() + " suggestions");
            return result;
        } catch (Exception e) {
            AppLogger.e(TAG, "tryLoadFromCache: cache file CORRUPT"
                    + " [" + e.getMessage() + "]"
                    + " — full scan required", e);
            return null;
        }
    }

    /** Loads the full cache including freq map for merging. Returns null if absent/corrupt. */
    private static CacheData loadCacheRaw(Context ctx) {
        File cacheFile = new File(ctx.getFilesDir(), CACHE_FILE);
        if (!cacheFile.isFile()) {
            AppLogger.d(TAG, "loadCacheRaw: no cache file present");
            return null;
        }
        try {
            String raw = readFile(cacheFile);
            if (raw == null) {
                AppLogger.e(TAG, "loadCacheRaw: FAILED to read cache file at "
                        + cacheFile.getAbsolutePath());
                return null;
            }
            JSONObject root = new JSONObject(raw);
            int fileCount = root.optInt("fileCount", 0);
            JSONArray arr = root.optJSONArray("names");
            if (arr == null) {
                AppLogger.e(TAG, "loadCacheRaw: 'names' array missing in cache — treating as empty");
                return new CacheData(fileCount, new HashMap<>(), new HashMap<>());
            }
            Map<String, String> displayName = new HashMap<>();
            Map<String, Long>   freq        = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject entry = arr.optJSONObject(i);
                if (entry == null) continue;
                String name  = entry.optString("name", "").trim();
                long   count = entry.optLong("count", 1);
                if (name.isEmpty()) continue;
                String key = name.toLowerCase();
                displayName.put(key, name);
                freq.put(key, count);
            }
            AppLogger.d(TAG, "loadCacheRaw: loaded " + displayName.size()
                    + " names from cache [fileCount=" + fileCount + "]");
            return new CacheData(fileCount, displayName, freq);
        } catch (Exception e) {
            AppLogger.e(TAG, "loadCacheRaw: FAILED to parse cache JSON — "
                    + e.getMessage(), e);
            return null;
        }
    }

    private static void writeCache(Context ctx, CacheData data) {
        File cacheFile = new File(ctx.getFilesDir(), CACHE_FILE);
        try {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(data.freq.entrySet());
            Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));

            JSONArray arr = new JSONArray();
            for (Map.Entry<String, Long> e : entries) {
                JSONObject obj = new JSONObject();
                obj.put("name",  data.displayName.get(e.getKey()));
                obj.put("count", e.getValue());
                arr.put(obj);
            }
            JSONObject root = new JSONObject();
            root.put("fileCount", data.fileCount);
            root.put("names", arr);

            FileWriter fw = new FileWriter(cacheFile);
            fw.write(root.toString());
            fw.flush();
            fw.close();

            AppLogger.d(TAG, "writeCache: SUCCESS"
                    + " — wrote " + data.displayName.size() + " names"
                    + " [fileCount=" + data.fileCount + "]"
                    + " to " + cacheFile.getAbsolutePath());
        } catch (Exception e) {
            AppLogger.e(TAG, "writeCache: FAILED to write cache file at "
                    + cacheFile.getAbsolutePath()
                    + " — suggestions will still work but next open will re-scan"
                    + " [" + e.getMessage() + "]", e);
        }
    }

    // ── Full scan (first install / after restore) ─────────────────────────────

    private static List<String> fullScanAndCache(Context ctx, int liveCount) {
        AppLogger.d(TAG, "fullScanAndCache: starting full scan"
                + " [liveCount=" + liveCount + "]");
        long startMs = System.currentTimeMillis();

        Map<String, String> displayName = new HashMap<>();
        Map<String, Long>   freq        = new HashMap<>();
        int totalFilesScanned = 0;
        int totalFilesSkipped = 0;

        for (String dirName : SCAN_DIRS) {
            File dir = new File(ctx.getFilesDir(), dirName);
            if (!dir.isDirectory()) {
                AppLogger.d(TAG, "fullScanAndCache: directory not found, skipping — " + dirName);
                continue;
            }
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null || files.length == 0) {
                AppLogger.d(TAG, "fullScanAndCache: no JSON files in " + dirName);
                continue;
            }
            AppLogger.d(TAG, "fullScanAndCache: scanning " + files.length
                    + " file(s) in " + dirName);
            int dirSkipped = 0;
            for (File f : files) {
                int before = displayName.size();
                extractNamesFromFile(f, displayName, freq);
                if (displayName.size() == before) {
                    // No new names extracted — file may be empty or malformed
                    dirSkipped++;
                    totalFilesSkipped++;
                }
                totalFilesScanned++;
            }
            if (dirSkipped > 0) {
                AppLogger.w(TAG, "fullScanAndCache: " + dirSkipped
                        + " file(s) in " + dirName
                        + " yielded no names (empty, malformed or all-duplicate)");
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        AppLogger.d(TAG, "fullScanAndCache: scan complete"
                + " — scanned " + totalFilesScanned + " file(s)"
                + ", skipped " + totalFilesSkipped
                + ", found " + displayName.size() + " unique player name(s)"
                + " in " + elapsedMs + "ms");

        CacheData data = new CacheData(liveCount, displayName, freq);
        writeCache(ctx, data);

        // Return sorted names
        List<Map.Entry<String, Long>> entries = new ArrayList<>(freq.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>(entries.size());
        for (Map.Entry<String, Long> e : entries) result.add(displayName.get(e.getKey()));

        AppLogger.d(TAG, "fullScanAndCache: returning " + result.size()
                + " sorted suggestions to caller");
        return result;
    }

    // ── JSON extraction ───────────────────────────────────────────────────────

    private static void extractNamesFromFile(File f,
                                              Map<String, String> displayName,
                                              Map<String, Long> freq) {
        try {
            String raw = readFile(f);
            if (raw == null || raw.isEmpty()) {
                AppLogger.w(TAG, "extractNamesFromFile: file is empty or unreadable — "
                        + f.getName());
                return;
            }
            JSONObject root = new JSONObject(raw);
            extractFromArray(root.optJSONArray("homePlayers"), displayName, freq);
            extractFromArray(root.optJSONArray("awayPlayers"), displayName, freq);
            extractFromInnings(root.optJSONObject("firstInnings"),  displayName, freq);
            extractFromInnings(root.optJSONObject("secondInnings"), displayName, freq);
            JSONArray teams = root.optJSONArray("teams");
            if (teams != null) {
                for (int t = 0; t < teams.length(); t++) {
                    JSONObject team = teams.optJSONObject(t);
                    if (team != null) extractFromArray(team.optJSONArray("players"), displayName, freq);
                }
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "extractNamesFromFile: FAILED to parse " + f.getName()
                    + " — " + e.getMessage());
        }
    }

    private static void extractFromArray(JSONArray arr,
                                          Map<String, String> displayName,
                                          Map<String, Long> freq) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("name", "").trim();
            if (name.isEmpty()) continue;
            String key = name.toLowerCase();
            if (!displayName.containsKey(key)) { displayName.put(key, name); freq.put(key, 1L); }
            else freq.put(key, freq.get(key) + 1);
        }
    }

    private static void extractFromInnings(JSONObject innings,
                                            Map<String, String> displayName,
                                            Map<String, Long> freq) {
        if (innings == null) return;
        extractFromArray(innings.optJSONArray("players"), displayName, freq);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int countJsonFiles(Context ctx) {
        int total = 0;
        for (String dir : SCAN_DIRS) {
            File d = new File(ctx.getFilesDir(), dir);
            if (!d.isDirectory()) continue;
            File[] files = d.listFiles((f, n) -> n.endsWith(".json"));
            if (files != null) total += files.length;
        }
        return total;
    }

    private static void addIfValid(List<String> list, String name) {
        if (name != null && !name.trim().isEmpty()) list.add(name.trim());
    }

    private static String readFile(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder((int) f.length());
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            AppLogger.e(TAG, "readFile: FAILED to read " + f.getName()
                    + " — " + e.getMessage());
            return null;
        }
    }

    /** Internal struct holding cache state for read-modify-write operations. */
    private static final class CacheData {
        int                 fileCount;
        Map<String, String> displayName;
        Map<String, Long>   freq;
        CacheData(int fc, Map<String, String> dn, Map<String, Long> fr) {
            fileCount = fc; displayName = dn; freq = fr;
        }
    }
}
