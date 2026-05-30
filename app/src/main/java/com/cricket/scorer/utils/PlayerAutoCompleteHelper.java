package com.cricket.scorer.utils;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * PlayerAutoCompleteHelper.java
 *
 * Attaches a lightweight suggestion dropdown to one or more player name
 * EditText fields. The dropdown appears below the focused EditText as soon
 * as the user types ≥ 1 character and there is at least one matching name.
 * Tapping a suggestion fills the EditText and dismisses the dropdown.
 *
 * Design decisions:
 *   - Uses a PopupWindow (not AutoCompleteTextView) so the EditText fields
 *     in SetupActivity and TournamentPlayersActivity do not need to be
 *     replaced — any existing EditText can be enhanced in-place.
 *   - Filtering is prefix + substring: a suggestion matches if the name
 *     contains the typed text (case-insensitive). Prefix matches rank first.
 *   - Max 6 suggestions shown at once to keep the popup compact.
 *   - The popup is dismissed when the field loses focus or the user clears it.
 *   - Suggestions are loaded once per activity on a background thread and
 *     shared across all fields via the same list instance.
 *
 * Usage:
 *   // In onCreate (after the EditTexts exist):
 *   PlayerAutoCompleteHelper.loadAndAttach(this, homePlayerFields);
 *   PlayerAutoCompleteHelper.loadAndAttach(this, awayPlayerFields);
 */
public final class PlayerAutoCompleteHelper {

    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final int MIN_CHARS         = 1;

    private PlayerAutoCompleteHelper() {}

    /**
     * Loads suggestions on a background thread then attaches the dropdown
     * to every EditText in the provided array.
     *
     * @param ctx    Activity context
     * @param fields array of player name EditTexts to enhance
     */
    public static void loadAndAttach(Context ctx, EditText[] fields) {
        new Thread(() -> {
            List<String> suggestions = PlayerNameSuggestionsUtil.buildSuggestions(ctx);
            if (fields == null || fields.length == 0) return;
            // Post attachment back to the main thread
            ((android.app.Activity) ctx).runOnUiThread(() -> {
                for (EditText et : fields) {
                    if (et != null) attach(ctx, et, suggestions);
                }
            });
        }).start();
    }

    /**
     * Same as above but accepts a List of EditTexts (for TournamentPlayersActivity).
     */
    public static void loadAndAttach(Context ctx, List<EditText> fields) {
        if (fields == null || fields.isEmpty()) return;
        loadAndAttach(ctx, fields.toArray(new EditText[0]));
    }

    // ── Core attachment ───────────────────────────────────────────────────────

    private static void attach(Context ctx, EditText et, List<String> allSuggestions) {
        final SuggestionPopup popup = new SuggestionPopup(ctx, allSuggestions, et);

        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.length() >= MIN_CHARS) {
                    popup.update(query);
                } else {
                    popup.dismiss();
                }
            }
        });

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) popup.dismiss();
        });
    }

    // ── Popup implementation ──────────────────────────────────────────────────

    private static final class SuggestionPopup {
        private final Context        ctx;
        private final List<String>   allNames;
        private final EditText       anchor;
        private       PopupWindow    window;
        private       ListView       listView;
        private       SuggestionAdapter adapter;

        SuggestionPopup(Context ctx, List<String> allNames, EditText anchor) {
            this.ctx      = ctx;
            this.allNames = allNames;
            this.anchor   = anchor;
        }

        void update(String query) {
            List<String> filtered = filter(query);
            if (filtered.isEmpty()) { dismiss(); return; }

            if (window == null || !window.isShowing()) {
                buildPopup(filtered);
                showPopup();
            } else {
                adapter.setItems(filtered);
                adapter.notifyDataSetChanged();
                int itemH = (int)(48 * ctx.getResources().getDisplayMetrics().density);
                window.update(anchor, 0, getYOffset(),
                        anchor.getWidth(),
                        Math.min(filtered.size(), MAX_VISIBLE_ITEMS) * itemH);
            }
        }

        void dismiss() {
            if (window != null && window.isShowing()) {
                window.dismiss();
                window = null;
            }
        }

        private List<String> filter(String query) {
            String q = query.toLowerCase();
            // Prefix matches first, then substring matches
            List<String> prefix    = new ArrayList<>();
            List<String> substring = new ArrayList<>();
            for (String name : allNames) {
                String lower = name.toLowerCase();
                if (lower.startsWith(q))    prefix.add(name);
                else if (lower.contains(q)) substring.add(name);
                if (prefix.size() + substring.size() >= MAX_VISIBLE_ITEMS * 2) break;
            }
            List<String> combined = new ArrayList<>(prefix);
            combined.addAll(substring);
            return combined.subList(0, Math.min(combined.size(), MAX_VISIBLE_ITEMS));
        }

        private void buildPopup(List<String> items) {
            listView = new ListView(ctx);
            adapter  = new SuggestionAdapter(ctx, items);
            listView.setAdapter(adapter);
            listView.setDividerHeight(0);

            int itemH = (int)(48 * ctx.getResources().getDisplayMetrics().density);
            int height = Math.min(items.size(), MAX_VISIBLE_ITEMS) * itemH;

            window = new PopupWindow(listView,
                    anchor.getWidth(), height, false);
            window.setElevation(8f);
            window.setOutsideTouchable(true);
            window.setFocusable(false); // keep keyboard open

            listView.setOnItemClickListener((parent, view, pos, id) -> {
                String chosen = adapter.getItem(pos);
                if (chosen != null) {
                    anchor.setText(chosen);
                    anchor.setSelection(chosen.length());
                }
                dismiss();
            });
        }

        private void showPopup() {
            window.showAsDropDown(anchor, 0, getYOffset());
        }

        private int getYOffset() {
            // Show directly below the EditText with a small 2dp gap
            return (int)(2 * ctx.getResources().getDisplayMetrics().density);
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private static final class SuggestionAdapter extends ArrayAdapter<String> {
        private List<String> items;

        SuggestionAdapter(Context ctx, List<String> items) {
            super(ctx, 0, new ArrayList<>(items));
            this.items = new ArrayList<>(items);
        }

        void setItems(List<String> newItems) {
            this.items = new ArrayList<>(newItems);
            clear();
            addAll(newItems);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(getContext());
                int ph = (int)(16 * getContext().getResources().getDisplayMetrics().density);
                int pv = (int)(12 * getContext().getResources().getDisplayMetrics().density);
                tv.setPadding(ph, pv, ph, pv);
                tv.setTextSize(14f);
                tv.setSingleLine(true);
            }
            String name = items.get(position);
            tv.setText(name);

            // Theme-aware colors
            try {
                int bg = getContext().getResources().getColor(
                        com.cricket.scorer.R.color.c_bg_card, getContext().getTheme());
                int fg = getContext().getResources().getColor(
                        com.cricket.scorer.R.color.c_text_primary, getContext().getTheme());
                tv.setBackgroundColor(bg);
                tv.setTextColor(fg);
            } catch (Exception e) {
                tv.setBackgroundColor(Color.WHITE);
                tv.setTextColor(Color.BLACK);
            }

            return tv;
        }

        @Override public Filter getFilter() { return new Filter() {
            @Override protected FilterResults performFiltering(CharSequence c) { return new FilterResults(); }
            @Override protected void publishResults(CharSequence c, FilterResults r) {}
        };}
    }
}
