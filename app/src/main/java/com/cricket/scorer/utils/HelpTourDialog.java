package com.cricket.scorer.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cricket.scorer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * HelpTourDialog.java
 *
 * Shows a paginated quick-tour walking the user through every major screen
 * of the app — what each screen does and what the user can do there.
 *
 * Triggered from the "Quick tour / Help" entry on the Home menu.
 *
 * The dialog is fully self-contained (no extra layout XML needed) and uses
 * the same color tokens as the rest of the app for visual consistency.
 *
 * Each page has:
 *   - Page indicator    (e.g. "2 / 11")
 *   - Title             (e.g. "Track new match")
 *   - Body              (multi-paragraph explanation)
 *   - Prev / Next       (Next becomes "Done" on the last page)
 */
public final class HelpTourDialog {

    private HelpTourDialog() {}

    /** Single page in the tour. */
    private static final class Page {
        final String title;
        final String body;
        Page(String title, String body) { this.title = title; this.body = body; }
    }

    public static void show(Context ctx) {
        final List<Page> pages = buildPages();
        final int[] idx = {0};

        // ── Build the dialog body ────────────────────────────────────────────
        int dp     = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        int dpSm   = (int) (ctx.getResources().getDisplayMetrics().density * 8);
        int dpXSm  = (int) (ctx.getResources().getDisplayMetrics().density * 4);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp, dp, dp, dpSm);

        // Page indicator
        final TextView pageIndicator = new TextView(ctx);
        pageIndicator.setTextSize(12f);
        pageIndicator.setTextColor(getColor(ctx, R.color.c_text_secondary));
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setPadding(0, 0, 0, dpSm);
        root.addView(pageIndicator);

        // Title
        final TextView titleView = new TextView(ctx);
        titleView.setTextSize(18f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(getColor(ctx, R.color.c_text_primary));
        titleView.setPadding(0, 0, 0, dpSm);
        root.addView(titleView);

        // Body (scrollable so long descriptions don't blow up the dialog)
        ScrollView bodyScroll = new ScrollView(ctx);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (ctx.getResources().getDisplayMetrics().density * 320));
        bodyScroll.setLayoutParams(scrollLp);

        final TextView bodyView = new TextView(ctx);
        bodyView.setTextSize(14f);
        bodyView.setTextColor(getColor(ctx, R.color.c_text_primary));
        bodyView.setLineSpacing(0, 1.25f);
        bodyView.setPadding(0, 0, 0, dpSm);
        bodyScroll.addView(bodyView);
        root.addView(bodyScroll);

        // Build the dialog
        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(root)
                .setCancelable(true)
                .create();

        // Custom Prev/Next bar
        LinearLayout navBar = new LinearLayout(ctx);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setPadding(0, dpSm, 0, 0);
        navBar.setGravity(Gravity.END);
        final Button prevBtn = new Button(ctx);
        prevBtn.setText("Previous");
        prevBtn.setAllCaps(false);
        prevBtn.setBackground(null);
        prevBtn.setTextColor(getColor(ctx, R.color.green_dark));
        final Button nextBtn = new Button(ctx);
        nextBtn.setText("Next");
        nextBtn.setAllCaps(false);
        nextBtn.setBackground(null);
        nextBtn.setTextColor(getColor(ctx, R.color.green_dark));
        nextBtn.setTypeface(null, Typeface.BOLD);
        navBar.addView(prevBtn);
        navBar.addView(nextBtn);
        root.addView(navBar);

        final Runnable render = new Runnable() {
            @Override public void run() {
                Page p = pages.get(idx[0]);
                pageIndicator.setText((idx[0] + 1) + " / " + pages.size());
                titleView.setText(p.title);
                bodyView.setText(p.body);
                prevBtn.setVisibility(idx[0] == 0 ? View.INVISIBLE : View.VISIBLE);
                nextBtn.setText(idx[0] == pages.size() - 1 ? "Done" : "Next");
            }
        };

        prevBtn.setOnClickListener(v -> {
            if (idx[0] > 0) { idx[0]--; render.run(); }
        });
        nextBtn.setOnClickListener(v -> {
            if (idx[0] < pages.size() - 1) { idx[0]++; render.run(); }
            else dialog.dismiss();
        });

        render.run();
        dialog.show();
    }

    private static int getColor(Context ctx, int resId) {
        try { return ctx.getResources().getColor(resId, ctx.getTheme()); }
        catch (Exception e) { return Color.BLACK; }
    }

    // ─── Tour content ────────────────────────────────────────────────────────

    private static List<Page> buildPages() {
        List<Page> pages = new ArrayList<>();

        pages.add(new Page(
                "Welcome to Cricket Scorer",
                "This quick tour walks you through every screen of the app so you " +
                "know what each section does.\n\n" +
                "You can:\n" +
                "• Track ball-by-ball scoring for casual matches\n" +
                "• Run multi-team tournaments with round-robin fixtures\n" +
                "• Export scorecards as text, image, or Excel files\n" +
                "• Review past matches, tournaments and deep statistics\n\n" +
                "Tap Next to start the tour, or close this dialog any time."));

        pages.add(new Page(
                "Home menu",
                "The main screen has four entries:\n\n" +
                "• Matches — track a new individual match, view recent matches " +
                "and match statistics.\n\n" +
                "• Tournaments — set up a multi-team tournament, view recent " +
                "tournament archives.\n\n" +
                "• Export data — save every saved match and tournament as JSON " +
                "files into your phone's Downloads folder for backup.\n\n" +
                "• Report a bug — sends a screenshot plus app logs to support. " +
                "You can also shake your device any time to trigger this."));

        pages.add(new Page(
                "Matches menu",
                "Tap Matches on the home menu to reach this sub-menu:\n\n" +
                "• Track new match — start a brand-new match. Asks for team " +
                "names, number of overs and players.\n\n" +
                "• Recent matches — list of all your saved completed matches. " +
                "Tap one to view its full scorecard.\n\n" +
                "• Match statistics — quick stats summary across the most " +
                "recently played match."));

        pages.add(new Page(
                "Setup screen",
                "When you start a new match you'll go through:\n\n" +
                "• Team names — enter the home and away team names.\n\n" +
                "• Overs and player count — choose innings length (1–50 overs) " +
                "and how many players per side.\n\n" +
                "• Single-batsman mode — toggle if you're scoring a backyard " +
                "match where the batsman doesn't change ends.\n\n" +
                "• Joker player — optionally enable a shared player who can " +
                "bat for one side and bowl for the other.\n\n" +
                "• Toss and openers — pick who bats first, then choose the two " +
                "openers and the opening bowler."));

        pages.add(new Page(
                "Live scoring screen",
                "The most-used screen. Each ball you tap is recorded instantly:\n\n" +
                "• Run buttons (0–6) — tap to add runs off the bat.\n\n" +
                "• Wide / No-ball — tap once for a plain extra. Long-press to " +
                "enter additional runs or to record a bye/leg-bye off a no-ball, " +
                "or a run-out on that delivery.\n\n" +
                "• Wicket — tap to record a normal dismissal. Long-press for " +
                "run-out (with completed runs, optionally as bye/leg-bye).\n\n" +
                "• Ret. Hurt — mark the striker as retired hurt; you can recall " +
                "them later.\n\n" +
                "• Undo — reverses the last ball (also works with shake-to-undo " +
                "if enabled).\n\n" +
                "• Edit overs — change the total overs mid-match if the captains " +
                "agree to a shortened game.\n\n" +
                "The header shows score, over count and current run rate. The " +
                "batting table updates after every ball."));

        pages.add(new Page(
                "Innings break",
                "When the first innings ends you land on this summary screen:\n\n" +
                "• View full batting scorecard with batsman runs, balls, fours, " +
                "sixes and strike rate. The Extras row shows team byes and " +
                "leg-byes under the R column.\n\n" +
                "• View bowling figures — overs, runs, wickets, economy and " +
                "the per-bowler extras breakdown (e.g. 4 (3Wd,1Nb)).\n\n" +
                "• Charts — over-by-over runs and run-rate progression.\n\n" +
                "• Start 2nd innings — sets up the chase with the openers and " +
                "opening bowler from the second team."));

        pages.add(new Page(
                "End of match — Statistics",
                "After the chase ends, the Stats screen shows the full match:\n\n" +
                "• Result banner — who won and by what margin.\n\n" +
                "• Both innings — complete batting and bowling tables side by " +
                "side with the extras row under R.\n\n" +
                "• Share — send the scorecard as text (WhatsApp, etc.), as a " +
                "rendered image, or as an .xlsx Excel file.\n\n" +
                "• Deep stats — dive into per-batsman run distribution, partnership " +
                "graphs, over-by-over scoring, run-rate curves and more.\n\n" +
                "Every completed match is automatically saved into Recent matches."));

        pages.add(new Page(
                "Recent matches",
                "List of every completed match still on your device. For each " +
                "entry you can:\n\n" +
                "• Tap to open the saved scorecard (same view as end-of-match).\n\n" +
                "• Long-press to delete a saved match.\n\n" +
                "• Export from the scorecard via Share once it's open.\n\n" +
                "Matches that were played as part of a tournament show up under " +
                "Recent tournaments instead — they're stored separately so this " +
                "list stays focused on standalone matches."));

        pages.add(new Page(
                "Tournaments menu",
                "Two entries:\n\n" +
                "• Track a tournament — start a new multi-team tournament. You " +
                "set the team count, players per team, overs per match and " +
                "competition format (round-robin and/or knockouts).\n\n" +
                "• Recent tournaments — saved tournament archives. Each one " +
                "remembers every match played, team standings and overall stats."));

        pages.add(new Page(
                "Tournament setup & dashboard",
                "Tournament workflow:\n\n" +
                "• Setup — choose number of teams, players per side, overs per " +
                "match, batting mode (single/dual) and whether to include a " +
                "joker player.\n\n" +
                "• Players — enter team names and player rosters.\n\n" +
                "• Schedule — auto-generated round-robin fixtures. Tap any " +
                "scheduled match to start scoring it.\n\n" +
                "• Dashboard — live points table, run-rate standings and " +
                "completed match results. Resume mid-tournament any time from " +
                "the home screen prompt."));

        pages.add(new Page(
                "Sharing & exporting",
                "Anywhere you see a Share button you can export the scorecard:\n\n" +
                "• Text — clean plain-text summary, ideal for WhatsApp or SMS.\n\n" +
                "• Image — fully rendered scorecard as a PNG.\n\n" +
                "• Excel (.xlsx) — multi-sheet workbook with batting, bowling " +
                "and over-by-over charts.\n\n" +
                "From Home → Export data you can also dump every saved match " +
                "and tournament as JSON files into Downloads, useful as a " +
                "device backup before factory reset or app reinstall.\n\n" +
                "That's the full tour! Tap Done to return to the home menu."));

        return pages;
    }
}
