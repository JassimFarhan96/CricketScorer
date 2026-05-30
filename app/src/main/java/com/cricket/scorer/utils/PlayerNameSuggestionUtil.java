package com.cricket.scorer.utils;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;

import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PlayerNameSuggestionUtil.java
 *
 * Collects all unique player names from every saved match and tournament
 * and attaches dynamic autocomplete suggestions to AutoCompleteTextView
 * fields as the user types.
 *
 * Features:
 *   - Scans recent_matches/, recent_tournaments/matches/ for player names
 *   - Case-insensitive prefix matching (e.g. "ra" matches "Rajan", "Rahul")
 *   - Results are sorted alphabetically for easy scanning
 *   - Minimum 1 character typed before suggestions appear
 *   - Does NOT auto-complete on selection (user still controls the field)
 *   - Thread-safe: name collection runs on a background thread, adapter
 *     update runs on UI thread
 *
 * Usage:
 *   // In onCreate / buildPlayerInputFields:
 *   PlayerNameSuggestionUtil.attach(context, autoCompleteTextView);
 *
 *   // To share one loaded list across many fields in the same screen:
 *   PlayerNameSuggestionUtil util = new PlayerNameSuggestionUtil(context);
 *   util.loadAsync(() -> {
 *       for (AutoCompleteTextView tv : allFields) util.attachTo(tv);
 *   });
 */
public class PlayerNameSuggestionUtil {

    private final Context     ctx;
    private List<String>      allNames = new ArrayList<>();
    private boolean           loaded   = false;

    public PlayerNameSuggestionUtil(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Convenience one-liner: creates a util, loads names in the background,
     * and attaches suggestions to the given field once loading is done.
     */
    public static void attach(Context ctx, AutoCompleteTextView field) {
        PlayerNameSuggestionUtil util = new PlayerNameSuggestionUtil(ctx);
        util.loadAsync(() -> util.attachTo(field));
    }

    /**
     * Load names in the background then run {@code onReady} on the UI thread.
     * Call this once per screen, then call {@link #attachTo} for each field.
     */
    public void loadAsync(Runnable onReady) {
        new Thread(() -> {
            loadNames();
            android.os.Handler main = new android.os.Handler(
                    android.os.Looper.getMainLooper());
            main.post(onReady);
        }).start();
    }

    /**
     * Attaches a suggestion adapter to {@code field}. Must be called after
     * {@link #loadAsync} completes (i.e. inside the {@code onReady} callback).
     */
    public void attachTo(AutoCompleteTextView field) {
        if (allNames.isEmpty()) return;

        // Use a custom filtering adapter so matching is case-insensitive
        // and works on any prefix, not just the full string.
        SuggestionAdapter adapter = new SuggestionAdapter(
                ctx, new ArrayList<>(allNames));
        field.setAdapter(adapter);
        field.setThreshold(1);          // show after 1 char typed
        field.setDropDownHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        // Dismiss the dropdown if user clears the field completely
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (s.length() == 0) field.dismissDropDown();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ── Name collection ──────────────────────────────────────────────────────

    private synchronized void loadNames() {
        if (loaded) return;
        Set<String> names = new LinkedHashSet<>();

        // 1. Standalone recent matches
        collectFromRecentMatches(names);

        // 2. Tournament match files
        collectFromTournamentMatches(names);

        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        allNames = sorted;
        loaded   = true;
    }

    private void collectFromRecentMatches(Set<String> names) {
        try {
            List<Match> matches = MatchStorage.loadAllMatches(ctx);
            for (Match m : matches) {
                addPlayers(names, m.getHomePlayers());
                addPlayers(names, m.getAwayPlayers());
            }
        } catch (Exception e) {
            AppLogger.e("PlayerNameSuggestionUtil", "collectFromRecentMatches failed", e);
        }
    }

    private void collectFromTournamentMatches(Set<String> names) {
        try {
            File tourneyMatchDir = MatchStorage.getTournamentStorageDir(ctx);
            if (tourneyMatchDir == null || !tourneyMatchDir.isDirectory()) return;
            File[] files = tourneyMatchDir.listFiles(
                    (d, n) -> n.endsWith(".json"));
            if (files == null) return;
            for (File f : files) {
                try {
                    // loadTournamentMatch takes a filename (not a path) and
                    // resolves it inside getTournamentStorageDir automatically.
                    Match m = MatchStorage.loadTournamentMatch(ctx, f.getName());
                    if (m != null) {
                        addPlayers(names, m.getHomePlayers());
                        addPlayers(names, m.getAwayPlayers());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AppLogger.e("PlayerNameSuggestionUtil", "collectFromTournamentMatches failed", e);
        }
    }

    private void addPlayers(Set<String> names, List<Player> players) {
        if (players == null) return;
        for (Player p : players) {
            String name = p.getName();
            if (name != null && !name.trim().isEmpty()
                    && !name.startsWith("Player ")   // skip default "Player N" placeholders
                    && !name.matches(".*P\\d+$")) {  // skip "TeamName P1" placeholders
                names.add(name.trim());
            }
        }
    }

    // ── Custom adapter with case-insensitive prefix filtering ────────────────

    private static class SuggestionAdapter extends ArrayAdapter<String> {

        private final List<String> allItems;
        private List<String>       filteredItems;
        private final SuggestionFilter filter = new SuggestionFilter();

        SuggestionAdapter(Context ctx, List<String> items) {
            super(ctx, android.R.layout.simple_dropdown_item_1line, items);
            this.allItems      = new ArrayList<>(items);
            this.filteredItems = new ArrayList<>(items);
        }

        @Override public int getCount()               { return filteredItems.size(); }
        @Override public String getItem(int pos)      { return filteredItems.get(pos); }
        @Override public Filter getFilter()           { return filter; }

        private class SuggestionFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = allItems;
                    results.count  = allItems.size();
                    return results;
                }
                String lower = constraint.toString().toLowerCase().trim();
                List<String> matched = new ArrayList<>();
                for (String name : allItems) {
                    if (name.toLowerCase().startsWith(lower)
                            || containsWordStartingWith(name.toLowerCase(), lower)) {
                        matched.add(name);
                    }
                }
                results.values = matched;
                results.count  = matched.size();
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredItems = (List<String>) results.values;
                if (results.count > 0) notifyDataSetChanged();
                else                   notifyDataSetInvalidated();
            }

            /** Matches if any word in the name starts with the prefix. */
            private boolean containsWordStartingWith(String name, String prefix) {
                for (String word : name.split("\\s+")) {
                    if (word.startsWith(prefix)) return true;
                }
                return false;
            }
        }
    }
}
