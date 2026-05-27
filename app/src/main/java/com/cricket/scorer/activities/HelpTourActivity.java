package com.cricket.scorer.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.cricket.scorer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * HelpTourActivity.java
 *
 * Interactive multi-page walkthrough of the entire app. Replaces a static
 * help dialog with a richer experience:
 *
 *   - Full-screen layout with mockup previews of each screen
 *   - Clickable hotspots on the mockup → reveal contextual hints
 *   - Animated page transitions (slide + fade)
 *   - Progress dots and STEP X OF Y label
 *   - Skip button always visible
 *   - First-launch awareness via SharedPreferences ("has_seen_tour")
 *
 * Each tour page builds its own mockup via code (no extra XML files needed).
 * To launch:   startActivity(new Intent(ctx, HelpTourActivity.class));
 * Or auto-show on first launch via HelpTourActivity.showIfFirstLaunch(ctx).
 */
public class HelpTourActivity extends AppCompatActivity {

    private static final String PREFS_NAME       = "cricket_scorer_prefs";
    private static final String KEY_HAS_SEEN_TOUR = "has_seen_tour";

    // ── State ───────────────────────────────────────────────────────────────
    private int currentPage = 0;
    private List<TourPage> pages;
    private int density;

    // ── Views ───────────────────────────────────────────────────────────────
    private TextView     stepLabel, titleView, subtitleView, hintText;
    private LinearLayout dotsBar, bodyContainer;
    private FrameLayout  mockupContainer;
    private CardView     hintCard;
    private Button       prevBtn, nextBtn;
    private ScrollView   scrollView;

    /** Auto-show on first launch — call this from HomeActivity.onResume(). */
    public static void showIfFirstLaunch(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!p.getBoolean(KEY_HAS_SEEN_TOUR, false)) {
            ctx.startActivity(new android.content.Intent(ctx, HelpTourActivity.class));
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_tour);
        density = (int) getResources().getDisplayMetrics().density;

        bindViews();
        pages = buildPages();
        buildDots();
        wireNav();
        renderPage(0, true);

        // Mark tour as seen so first-launch trigger won't fire again
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_HAS_SEEN_TOUR, true).apply();
    }

    private void bindViews() {
        stepLabel       = findViewById(R.id.tour_step_label);
        titleView       = findViewById(R.id.tour_title);
        subtitleView    = findViewById(R.id.tour_subtitle);
        hintText        = findViewById(R.id.tour_hint_text);
        hintCard        = findViewById(R.id.tour_hint_card);
        dotsBar         = findViewById(R.id.tour_dots);
        bodyContainer   = findViewById(R.id.tour_body);
        mockupContainer = findViewById(R.id.tour_mockup);
        prevBtn         = findViewById(R.id.tour_prev);
        nextBtn         = findViewById(R.id.tour_next);
        scrollView      = findViewById(R.id.tour_scroll);
        findViewById(R.id.tour_skip).setOnClickListener(v -> finish());
    }

    private void wireNav() {
        prevBtn.setOnClickListener(v -> {
            if (currentPage > 0) { currentPage--; renderPage(currentPage, false); }
        });
        nextBtn.setOnClickListener(v -> {
            if (currentPage < pages.size() - 1) { currentPage++; renderPage(currentPage, true); }
            else finish();
        });
    }

    private void buildDots() {
        dotsBar.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(8 * density, 8 * density);
            lp.setMarginStart(4 * density); lp.setMarginEnd(4 * density);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.dot_tour_inactive);
            dotsBar.addView(dot);
        }
    }

    private void updateDots() {
        for (int i = 0; i < dotsBar.getChildCount(); i++) {
            View dot = dotsBar.getChildAt(i);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            if (i == currentPage) {
                dot.setBackgroundResource(R.drawable.dot_tour_active);
                lp.width = 20 * density;
            } else {
                dot.setBackgroundResource(R.drawable.dot_tour_inactive);
                lp.width = 8 * density;
            }
            dot.setLayoutParams(lp);
        }
    }

    private void renderPage(int idx, boolean forward) {
        TourPage page = pages.get(idx);

        stepLabel.setText("STEP " + (idx + 1) + " OF " + pages.size());
        titleView.setText(page.title);
        subtitleView.setText(page.subtitle);
        hintCard.setVisibility(View.GONE);

        // Rebuild mockup + body
        mockupContainer.removeAllViews();
        bodyContainer.removeAllViews();
        page.buildMockup(this, mockupContainer);
        page.buildBody(this, bodyContainer);

        // Animate the content area
        int animRes = forward ? R.anim.slide_in_right : R.anim.slide_in_left;
        mockupContainer.startAnimation(AnimationUtils.loadAnimation(this, animRes));
        bodyContainer.startAnimation(AnimationUtils.loadAnimation(this, animRes));

        scrollView.smoothScrollTo(0, 0);
        updateDots();

        // Update bottom buttons
        prevBtn.setVisibility(idx == 0 ? View.INVISIBLE : View.VISIBLE);
        nextBtn.setText(idx == pages.size() - 1 ? "Get started" : "Next");
    }

    /** Reveals an inline hint card with the given message. */
    void showHint(String message) {
        hintText.setText(message);
        hintCard.setVisibility(View.VISIBLE);
        hintCard.startAnimation(
                AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
    }

    // ─── Helpers for building UI in code ────────────────────────────────────

    int dp(int v) { return v * density; }

    int color(int resId) {
        try { return getResources().getColor(resId, getTheme()); }
        catch (Exception e) { return Color.BLACK; }
    }

    /** Pill-shaped tappable element used inside mockups (e.g. fake ball buttons). */
    @SuppressLint("ClickableViewAccessibility")
    View makePill(String label, int bgColor, int textColor, String hint) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        bg.setColor(bgColor);
        tv.setBackground(bg);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(v -> showHint(hint));
        return tv;
    }

    /** Circle button used for ball-by-ball indicators (Wd, Nb, 4, etc). */
    View makeCircle(String label, int bgColor, String hint) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setWidth(dp(40)); tv.setHeight(dp(40));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(bgColor);
        tv.setBackground(bg);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(v -> showHint(hint));
        return tv;
    }

    /** Adds a "✓ point" line to the body section. */
    void addBullet(LinearLayout container, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView tick = new TextView(this);
        tick.setText("✓");
        tick.setTextColor(color(R.color.green_dark));
        tick.setTextSize(15f);
        tick.setTypeface(null, Typeface.BOLD);
        tick.setPadding(0, 0, dp(10), 0);
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(color(R.color.c_text_primary));
        body.setTextSize(14f);
        body.setLineSpacing(0, 1.3f);
        row.addView(tick);
        row.addView(body);
        container.addView(row);
    }

    /** "Tap any of the items below to learn more" prompt above the mockup. */
    TextView tapPrompt() {
        TextView t = new TextView(this);
        t.setText("👆  Tap any item below to learn more");
        t.setTextColor(color(R.color.c_text_secondary));
        t.setTextSize(12f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    /** Card-style mockup container with rounded corners and subtle background. */
    LinearLayout mockupCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(R.color.c_bg_card_alt));
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, color(R.color.c_divider));
        card.setBackground(bg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    // ─── Tour content ───────────────────────────────────────────────────────

    private List<TourPage> buildPages() {
        List<TourPage> p = new ArrayList<>();
        p.add(new WelcomePage());
        p.add(new HomeMenuPage());
        p.add(new SetupPage());
        p.add(new LiveScoringPage());
        p.add(new LongPressPage());
        p.add(new InningsBreakPage());
        p.add(new EndOfMatchPage());
        p.add(new RecentMatchesPage());
        p.add(new TournamentsPage());
        p.add(new ExportSharePage());
        p.add(new FinishPage());
        return p;
    }

    // ─── Base + page implementations ────────────────────────────────────────

    abstract static class TourPage {
        String title, subtitle;
        TourPage(String t, String s) { title = t; subtitle = s; }
        abstract void buildMockup(HelpTourActivity a, FrameLayout container);
        abstract void buildBody(HelpTourActivity a, LinearLayout container);
    }

    static class WelcomePage extends TourPage {
        WelcomePage() { super("Welcome aboard 🏏",
                "Cricket Scorer makes it easy to track matches and tournaments ball-by-ball. Let's take a 2-minute tour."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(a);
            icon.setImageResource(com.cricket.scorer.R.mipmap.ic_launcher);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(a.dp(80), a.dp(80));
            icon.setLayoutParams(ilp);
            c.addView(icon);

            TextView tag = new TextView(a);
            tag.setText("Built for backyard, club, turf &\ntournament cricket");
            tag.setGravity(Gravity.CENTER);
            tag.setTextColor(a.color(R.color.c_text_secondary));
            tag.setTextSize(13f);
            tag.setLineSpacing(0, 1.3f);
            tag.setPadding(0, a.dp(12), 0, 0);
            c.addView(tag);
            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Track every ball with extras, byes and run-outs");
            a.addBullet(container, "Undo the last ball. Can be done only till start of over");
            a.addBullet(container, "Edit the number of overs during 1st innings");
            a.addBullet(container, "Joker player - this player is common to both teams and cannot bat and bowl at the same time.");
            a.addBullet(container, "Save matches and tournaments data");
            a.addBullet(container, "Share scorecards as text, image or Excel");
            a.addBullet(container, "Works fully offline — no account needed");
        }
    }

    static class HomeMenuPage extends TourPage {
        HomeMenuPage() { super("Home menu",
                "Four entries from the home screen. Tap each row below to see what they do."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());
            c.addView(makeRow(a, "🏏", "Matches", "Track new match, view recent matches & stats",
                    "Tap this to score individual matches or browse past ones."));
            c.addView(makeRow(a, "🏆", "Tournaments", "Run multi-team competitions",
                    "Round-robin schedules, points tables and full archives."));
            c.addView(makeRow(a, "📤", "Backup/Restore data", "Backup all matches & tournaments data. Restore the backed-up data whenever required.",
                    "Use this for backups before a phone reset or app reinstall."));
            c.addView(makeRow(a, "?",  "Quick tour / Help", "This walkthrough",
                    "You can reopen this tour any time from here."));
            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Shake the phone any time and select Gmail option to report a bug to cricketscorer.support@gmail.com");
            a.addBullet(container, "If a match is in progress and if the app got closed, resume dialog appears on app re-launch");
        }
        private View makeRow(HelpTourActivity a, String icon, String title, String sub, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(a.dp(8), a.dp(10), a.dp(8), a.dp(10));
            row.setClickable(true); row.setFocusable(true);
            row.setBackgroundResource(android.R.color.transparent);
            row.setOnClickListener(v -> a.showHint(hint));

            TextView ic = new TextView(a);
            ic.setText(icon);
            ic.setTextSize(20f);
            ic.setTextColor(Color.WHITE);
            ic.setGravity(Gravity.CENTER);
            ic.setWidth(a.dp(40)); ic.setHeight(a.dp(40));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(a.color(R.color.green_dark));
            ic.setBackground(bg);
            row.addView(ic);

            LinearLayout text = new LinearLayout(a);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(a.dp(12), 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            text.setLayoutParams(lp);
            TextView t = new TextView(a);
            t.setText(title); t.setTextSize(14f); t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(a.color(R.color.c_text_primary));
            TextView s = new TextView(a);
            s.setText(sub); s.setTextSize(11f);
            s.setTextColor(a.color(R.color.c_text_secondary));
            text.addView(t); text.addView(s);
            row.addView(text);

            TextView chev = new TextView(a);
            chev.setText("›"); chev.setTextSize(20f);
            chev.setTextColor(a.color(R.color.c_text_secondary));
            row.addView(chev);
            return row;
        }
    }

    static class SetupPage extends TourPage {
        SetupPage() { super("Setting up a match",
                "When you start a new match, the setup screen collects everything you need to begin scoring."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());

            c.addView(fakeField(a, "Home team",   "Stallions",
                    "Names appear in the live scoring header and final scorecard."));
            c.addView(fakeField(a, "Away team",   "Tigers", ""));
            c.addView(fakeField(a, "Overs",       "20",
                    "Innings length — used to compute required run rate during the chase."));
            c.addView(fakeField(a, "Players/side", "11",
                    "Determines how many wickets fall before all out."));

            // Mode toggles
            LinearLayout modeRow = new LinearLayout(a);
            modeRow.setOrientation(LinearLayout.HORIZONTAL);
            modeRow.setPadding(0, a.dp(8), 0, 0);
            View singleBat = a.makePill("Single bat", a.color(R.color.green_light),
                    a.color(R.color.green_dark),
                    "Single-batsman mode — for backyard games where the batsman doesn't change ends.");
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.setMarginEnd(a.dp(8));
            singleBat.setLayoutParams(plp);
            View joker = a.makePill("⚡ Joker", a.color(R.color.green_light),
                    a.color(R.color.green_dark),
                    "Joker player — one shared player who can bat for one side AND bowl for the other (not both at the same time).");
            modeRow.addView(singleBat); modeRow.addView(joker);
            c.addView(modeRow);

            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "After setup, you pick the toss winner & openers");
            a.addBullet(container, "Opening bowler is selected on the same screen");
        }
        private View fakeField(HelpTourActivity a, String label, String value, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, a.dp(6), 0, a.dp(6));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(!hint.isEmpty());
            row.setFocusable(!hint.isEmpty());
            if (!hint.isEmpty()) row.setOnClickListener(v -> a.showHint(hint));

            TextView lbl = new TextView(a);
            lbl.setText(label);
            lbl.setTextColor(a.color(R.color.c_text_secondary));
            lbl.setTextSize(13f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lbl.setLayoutParams(lp);
            row.addView(lbl);
            TextView val = new TextView(a);
            val.setText(value);
            val.setTextColor(a.color(R.color.c_text_primary));
            val.setTextSize(14f);
            val.setTypeface(null, Typeface.BOLD);
            row.addView(val);
            return row;
        }
    }

    static class LiveScoringPage extends TourPage {
        LiveScoringPage() { super("Live scoring",
                "The most-used screen. Tap any element below to learn what it does."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());

            // Fake score header (green band)
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable hbg = new GradientDrawable();
            hbg.setColor(a.color(R.color.green_dark));
            hbg.setCornerRadius(a.dp(8));
            header.setBackground(hbg);
            header.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
            header.setClickable(true);
            header.setOnClickListener(v -> a.showHint(
                    "Score header — shows runs/wickets, overs played out of total, current run rate, and required rate in the chase."));

            TextView ts = new TextView(a);
            ts.setText("1st Innings");
            ts.setTextColor(0xAAFFFFFF);
            ts.setTextSize(11f);
            header.addView(ts);
            TextView sc = new TextView(a);
            sc.setText("48/2");
            sc.setTextColor(Color.WHITE);
            sc.setTextSize(28f);
            sc.setTypeface(null, Typeface.BOLD);
            header.addView(sc);
            TextView ov = new TextView(a);
            ov.setText("Ov 5.4 / 20  ·  CRR 8.47");
            ov.setTextColor(0xAAFFFFFF);
            ov.setTextSize(12f);
            header.addView(ov);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.bottomMargin = a.dp(12);
            header.setLayoutParams(hp);
            c.addView(header);

            // Ball history strip
            TextView histLbl = new TextView(a);
            histLbl.setText("THIS OVER");
            histLbl.setTextSize(10f);
            histLbl.setTextColor(a.color(R.color.c_text_secondary));
            histLbl.setLetterSpacing(0.1f);
            c.addView(histLbl);

            LinearLayout balls = new LinearLayout(a);
            balls.setOrientation(LinearLayout.HORIZONTAL);
            balls.setPadding(0, a.dp(8), 0, a.dp(12));
            balls.addView(a.makeCircle("·", 0xFF555555,
                    "Dot ball — no runs, valid delivery."));
            addSpace(balls, a, 8);
            balls.addView(a.makeCircle("Wd", 0xFFB58A00,
                    "Wide — 1 run extra. Long-press to add additional runs the batsmen ran on a wide."));
            addSpace(balls, a, 8);
            balls.addView(a.makeCircle("4", 0xFF0E5DAA,
                    "Boundary — 4 runs."));
            addSpace(balls, a, 8);
            balls.addView(a.makeCircle("Nb", 0xFFB23535,
                    "No-ball — 1 run penalty. Long-press for extras or run-out."));
            addSpace(balls, a, 8);
            balls.addView(a.makeCircle("W", 0xFFB23535,
                    "Wicket. Long-press for run-out with completed runs."));
            c.addView(balls);

            // Action button grids - run values (DOT + 1-6). Long-press hints
            // call out the bye / leg-bye flow on every run button.
            LinearLayout runRow1 = new LinearLayout(a);
            runRow1.setOrientation(LinearLayout.HORIZONTAL);
            runRow1.setPadding(0, a.dp(6), 0, 0);
            runRow1.addView(weightedPill(a, "DOT", 0xFF333333, Color.WHITE,
                    "Dot ball — no runs taken. Long-press for bye / leg-bye (i.e. dot ball but the batsmen ran extras)."));
            runRow1.addView(weightedPill(a, "1",   0xFF333333, Color.WHITE,
                    "1 run off the bat. Long-press to record 1 bye or 1 leg-bye instead."));
            runRow1.addView(weightedPill(a, "2",   0xFF333333, Color.WHITE,
                    "2 runs off the bat. Long-press to record 2 byes or 2 leg-byes instead."));
            runRow1.addView(weightedPill(a, "3",   0xFF333333, Color.WHITE,
                    "3 runs off the bat. Long-press to record 3 byes or 3 leg-byes instead."));
            c.addView(runRow1);

            LinearLayout runRow2 = new LinearLayout(a);
            runRow2.setOrientation(LinearLayout.HORIZONTAL);
            runRow2.setPadding(0, a.dp(8), 0, 0);
            runRow2.addView(weightedPill(a, "4",   0xFF0E5DAA, Color.WHITE,
                    "4-run boundary. Long-press to record 4 byes or 4 leg-byes instead."));
            runRow2.addView(weightedPill(a, "5",   0xFF333333, Color.WHITE,
                    "5 runs (rare — overthrow etc). Long-press for 5 byes or 5 leg-byes."));
            runRow2.addView(weightedPill(a, "6",   0xFF0E5DAA, Color.WHITE,
                    "6-run boundary. Long-press to record 6 byes or 6 leg-byes instead."));
            c.addView(runRow2);

            LinearLayout grid2 = new LinearLayout(a);
            grid2.setOrientation(LinearLayout.HORIZONTAL);
            grid2.setPadding(0, a.dp(8), 0, 0);
            grid2.addView(weightedPill(a, "WIDE",     0xFFB58A00, Color.WHITE,
                    "Tap = 1-run wide. Long-press for additional runs / run-out on the wide."));
            grid2.addView(weightedPill(a, "NO BALL",  0xFFB23535, Color.WHITE,
                    "Tap = 1-run no-ball. Long-press for additional runs (batsman, bye or leg-bye) and run-outs."));
            c.addView(grid2);

            LinearLayout grid3 = new LinearLayout(a);
            grid3.setOrientation(LinearLayout.HORIZONTAL);
            grid3.setPadding(0, a.dp(8), 0, 0);
            grid3.addView(weightedPill(a, "WICKET", 0xFFB23535, Color.WHITE,
                    "Tap = standard dismissal. Long-press for run-out with bye/leg-bye completed runs."));
            grid3.addView(weightedPill(a, "RET. HURT", 0xFFCC7B17, Color.WHITE,
                    "Retire the striker hurt. You can recall them later."));
            grid3.addView(weightedPill(a, "UNDO", 0xFF0E5DAA, Color.WHITE,
                    "Reverses the last ball. Undo can be done only till start of over."));
            c.addView(grid3);

            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Live batting & bowling tables update after every ball");
            a.addBullet(container, "Edit overs mid-match if the captains agree on a shorter/longer game");
            a.addBullet(container, "Undo the last ball. Can be done only till starting of that over. Cannot undo previous over");
        }
        private View weightedPill(HelpTourActivity a, String label, int bg, int fg, String hint) {
            View v = a.makePill(label, bg, fg, hint);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(a.dp(4)); lp.setMarginEnd(a.dp(4));
            v.setLayoutParams(lp);
            return v;
        }
        private void addSpace(LinearLayout parent, HelpTourActivity a, int dp) {
            View s = new View(a);
            s.setLayoutParams(new LinearLayout.LayoutParams(a.dp(dp), 1));
            parent.addView(s);
        }
    }

    static class LongPressPage extends TourPage {
        LongPressPage() { super("Power user tip 💡",
                "Long-pressing the special buttons opens a deeper menu. Tap each example below to see the prompt."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());
            c.addView(longPressRow(a, "WIDE", 0xFFB58A00,
                    "Long-press WIDE → choose additional runs taken on the wide (0–6) AND optionally record a run-out on the wide."));
            c.addView(longPressRow(a, "NO BALL", 0xFFB23535,
                    "Long-press NO BALL → choose how the additional runs were scored (Batsman / Bye / Leg-bye), add extras (0–6), and optionally a run-out."));
            c.addView(longPressRow(a, "WICKET", 0xFFB23535,
                    "Long-press WICKET → run-out with completed runs (0–4). Choose Batsman / Bye / Leg-bye for those runs, then pick which batsman is out."));
            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Extras shown as e.g. \"Nb+2Lb\" or \"3B+W\" in the over history");
            a.addBullet(container, "Bye/leg-bye runs go to team extras, not the batsman's score");
            a.addBullet(container, "No-balls never count as a valid delivery — ballsFaced stays the same");
        }
        private View longPressRow(HelpTourActivity a, String label, int bg, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, a.dp(8), 0, a.dp(8));
            row.setClickable(true);
            row.setOnClickListener(v -> a.showHint(hint));
            View pill = a.makePill(label, bg, Color.WHITE, hint);
            row.addView(pill);
            TextView txt = new TextView(a);
            txt.setText("👆 long-press");
            txt.setTextColor(a.color(R.color.c_text_secondary));
            txt.setTextSize(12f);
            txt.setPadding(a.dp(12), 0, 0, 0);
            row.addView(txt);
            return row;
        }
    }

    static class InningsBreakPage extends TourPage {
        InningsBreakPage() { super("Innings break",
                "When the first innings ends, you land on a summary screen with the full scorecard."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());

            c.addView(fakeStatTile(a, "📋 Batting scorecard",
                    "Runs, balls, 4s, 6s, SR per batsman",
                    "Tap to see the full batting card. Extras row sits under R as e.g. 5 (3B,2Lb)."));
            c.addView(fakeStatTile(a, "🎯 Bowling figures",
                    "O / R / W / Econ + extras breakdown",
                    "Per-bowler extras are shown as 4 (3Wd,1Nb) — you can see exactly what each bowler conceded."));
            c.addView(fakeStatTile(a, "📈 Charts",
                    "Over-by-over runs + run-rate progression",
                    "Bar chart for runs per over, line chart for cumulative run rate."));
            c.addView(fakeStatTile(a, "▶ Start 2nd innings",
                    "Pick openers & opening bowler",
                    "Begins the chase — the target appears in the header."));

            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Charts are exported with the scorecard image and Excel");
            a.addBullet(container, "Single-batsman mode shows a simplified batting card");
        }
        private View fakeStatTile(HelpTourActivity a, String title, String sub, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.color(R.color.c_bg_card));
            bg.setCornerRadius(a.dp(8));
            bg.setStroke(1, a.color(R.color.c_divider));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = a.dp(8);
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setOnClickListener(v -> a.showHint(hint));
            TextView t = new TextView(a);
            t.setText(title); t.setTextSize(14f); t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(a.color(R.color.c_text_primary));
            TextView s = new TextView(a);
            s.setText(sub); s.setTextSize(12f);
            s.setTextColor(a.color(R.color.c_text_secondary));
            row.addView(t); row.addView(s);
            return row;
        }
    }

    static class EndOfMatchPage extends TourPage {
        EndOfMatchPage() { super("End of match",
                "After the chase ends, the Statistics screen shows everything from both innings."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());

            // Result banner
            LinearLayout banner = new LinearLayout(a);
            banner.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(a.color(R.color.green_dark));
            bbg.setCornerRadius(a.dp(10));
            banner.setBackground(bbg);
            banner.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
            banner.setClickable(true);
            banner.setOnClickListener(v -> a.showHint(
                    "Result banner — shows the winning team and margin (e.g. 'Stallions won by 23 runs' or 'by 6 wickets')."));
            TextView win = new TextView(a);
            win.setText("Stallions won by 23 runs 🏆");
            win.setTextColor(Color.WHITE);
            win.setTextSize(16f);
            win.setTypeface(null, Typeface.BOLD);
            banner.addView(win);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.bottomMargin = a.dp(12);
            banner.setLayoutParams(blp);
            c.addView(banner);

            // Share row
            LinearLayout share = new LinearLayout(a);
            share.setOrientation(LinearLayout.HORIZONTAL);
            share.setPadding(0, 0, 0, a.dp(8));
            share.addView(shareBtn(a, "📝", "Text",
                    "Plain-text scorecard — ideal for WhatsApp / SMS sharing."));
            share.addView(shareBtn(a, "🖼", "Image",
                    "Rendered PNG of the full scorecard with charts."));
            share.addView(shareBtn(a, "📊", "Excel",
                    "Multi-sheet .xlsx workbook with batting, bowling and chart sheets."));
            c.addView(share);

            // Deep stats teaser
            LinearLayout deep = new LinearLayout(a);
            deep.setOrientation(LinearLayout.HORIZONTAL);
            deep.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12));
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(a.color(R.color.green_light));
            dbg.setCornerRadius(a.dp(10));
            deep.setBackground(dbg);
            deep.setClickable(true);
            deep.setOnClickListener(v -> a.showHint(
                    "Deep stats — per-batsman run distribution, partnership graphs, over-by-over scoring, run-rate curves and more."));
            TextView de = new TextView(a);
            de.setText("📊 Deep stats — partnerships, run dist, RR curves");
            de.setTextColor(a.color(R.color.green_dark));
            de.setTextSize(13f);
            de.setTypeface(null, Typeface.BOLD);
            deep.addView(de);
            c.addView(deep);

            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Match is automatically saved to Recent matches");
            a.addBullet(container, "Tournament matches save to a separate archive");
        }
        private View shareBtn(HelpTourActivity a, String icon, String label, String hint) {
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(a.dp(8), a.dp(10), a.dp(8), a.dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.color(R.color.c_bg_card));
            bg.setCornerRadius(a.dp(8));
            bg.setStroke(1, a.color(R.color.c_divider));
            col.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(a.dp(6));
            col.setLayoutParams(lp);
            col.setClickable(true);
            col.setOnClickListener(v -> a.showHint(hint));
            TextView ic = new TextView(a);
            ic.setText(icon); ic.setTextSize(22f); ic.setGravity(Gravity.CENTER);
            TextView t = new TextView(a);
            t.setText(label); t.setTextSize(11f); t.setGravity(Gravity.CENTER);
            t.setTextColor(a.color(R.color.c_text_primary));
            t.setTypeface(null, Typeface.BOLD);
            col.addView(ic); col.addView(t);
            return col;
        }
    }

    static class RecentMatchesPage extends TourPage {
        RecentMatchesPage() { super("Recent matches",
                "Every completed match is saved automatically. Browse, reopen or delete from here."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());
            c.addView(matchRow(a, "Stallions vs Tigers", "Stallions won by 23 runs", "Today",
                    "Tap a match to reopen its full scorecard."));
            c.addView(matchRow(a, "Lions vs Eagles", "Lions won by 6 wickets", "Yesterday",
                    "Long-press to delete a saved match permanently."));
            c.addView(matchRow(a, "Sharks vs Panthers", "Tied match", "3 days ago",
                    "Tied matches and DLS results are also stored."));
            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "View → reopen the scorecard");
            a.addBullet(container, "Delete → delete the saved match");
            a.addBullet(container, "Tournament matches live in Recent tournaments, not here");
        }
        private View matchRow(HelpTourActivity a, String teams, String result, String when, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.color(R.color.c_bg_card));
            bg.setCornerRadius(a.dp(8));
            bg.setStroke(1, a.color(R.color.c_divider));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = a.dp(8);
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setOnClickListener(v -> a.showHint(hint));

            LinearLayout txt = new LinearLayout(a);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = new TextView(a);
            t.setText(teams); t.setTextSize(14f); t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(a.color(R.color.c_text_primary));
            TextView s = new TextView(a);
            s.setText(result); s.setTextSize(12f);
            s.setTextColor(a.color(R.color.c_text_secondary));
            txt.addView(t); txt.addView(s);
            row.addView(txt);

            TextView ago = new TextView(a);
            ago.setText(when); ago.setTextSize(11f);
            ago.setTextColor(a.color(R.color.c_text_secondary));
            row.addView(ago);
            return row;
        }
    }

    static class TournamentsPage extends TourPage {
        TournamentsPage() { super("Tournaments",
                "Run a full multi-team competition. Auto-fixtures, live points table, individual match tracking."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());

            // Mini points table
            TextView heading = new TextView(a);
            heading.setText("POINTS TABLE");
            heading.setTextSize(10f);
            heading.setLetterSpacing(0.1f);
            heading.setTextColor(a.color(R.color.c_text_secondary));
            heading.setPadding(0, 0, 0, a.dp(8));
            c.addView(heading);

            c.addView(pointsRow(a, "1", "Stallions", "4", "8", "+1.42",
                    "Tap a team to see all their matches and detailed stats."));
            c.addView(pointsRow(a, "2", "Lions",    "4", "6", "+0.83",
                    "Standings sort by points, then net run rate."));
            c.addView(pointsRow(a, "3", "Tigers",   "4", "4", "-0.21",
                    "Updates live after every match completes."));
            c.addView(pointsRow(a, "4", "Eagles",   "4", "2", "-2.04",
                    "Bottom team — net run rate breaks ties."));

            // Schedule preview
            TextView shead = new TextView(a);
            shead.setText("FIXTURES");
            shead.setTextSize(10f);
            shead.setLetterSpacing(0.1f);
            shead.setTextColor(a.color(R.color.c_text_secondary));
            shead.setPadding(0, a.dp(12), 0, a.dp(8));
            c.addView(shead);

            c.addView(fixtureRow(a, "Match 5", "Stallions vs Lions", "Tap to score",
                    "Tap a fixture to begin scoring that match. Resumes mid-game if you leave."));

            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Round-robin fixtures auto-generated");
            a.addBullet(container, "Net run rate computed automatically");
            a.addBullet(container, "Resume mid-tournament from the home screen prompt");
        }
        private View pointsRow(HelpTourActivity a, String pos, String team,
                                String m, String pts, String nrr, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.color(R.color.c_bg_card));
            bg.setCornerRadius(a.dp(6));
            bg.setStroke(1, a.color(R.color.c_divider));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = a.dp(4);
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setOnClickListener(v -> a.showHint(hint));
            addCell(a, row, pos, 0.3f, true);
            addCell(a, row, team, 1.2f, true);
            addCell(a, row, m, 0.3f, false);
            addCell(a, row, pts, 0.3f, true);
            addCell(a, row, nrr, 0.5f, false);
            return row;
        }
        private void addCell(HelpTourActivity a, LinearLayout parent, String text, float weight, boolean bold) {
            TextView tv = new TextView(a);
            tv.setText(text); tv.setTextSize(12f);
            if (bold) tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(a.color(R.color.c_text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
            tv.setLayoutParams(lp);
            parent.addView(tv);
        }
        private View fixtureRow(HelpTourActivity a, String num, String teams, String action, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.color(R.color.green_light));
            bg.setCornerRadius(a.dp(8));
            row.setBackground(bg);
            row.setClickable(true);
            row.setOnClickListener(v -> a.showHint(hint));
            TextView t = new TextView(a);
            t.setText(num + "  ·  " + teams);
            t.setTextColor(a.color(R.color.green_dark));
            t.setTextSize(13f);
            t.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            t.setLayoutParams(lp);
            row.addView(t);
            TextView arr = new TextView(a);
            arr.setText("›");
            arr.setTextSize(20f);
            arr.setTextColor(a.color(R.color.green_dark));
            row.addView(arr);
            return row;
        }
    }

    static class ExportSharePage extends TourPage {
        ExportSharePage() { super("Share & export",
                "Any scorecard can be shared three ways. Backup is also one tap away."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.addView(a.tapPrompt());
            c.addView(exportRow(a, "📝", "Text",
                    "Quick share to WhatsApp / SMS",
                    "Plain-text scorecard formatted for easy reading. Best for casual sharing."));
            c.addView(exportRow(a, "🖼", "Image",
                    "PNG with charts embedded",
                    "Looks like a professional scorecard. Includes the over-by-over bar chart."));
            c.addView(exportRow(a, "📊", "Excel",
                    "Multi-sheet workbook",
                    "Full .xlsx file with batting, bowling, summary and chart sheets. Open in Excel / Google Sheets."));
            c.addView(exportRow(a, "💾", "Backup/Restore",
                    "From home menu — use to take backup before phone reset or app re-install",
                    "Saves every match and tournament data into Downloads after backup. Select the saved file to restore the data."));
            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Excel charts match what you see in-app");
            a.addBullet(container, "Image scorecards work on any phone with a viewer");
        }
        private View exportRow(HelpTourActivity a, String icon, String title, String sub, String hint) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.color(R.color.c_bg_card));
            bg.setCornerRadius(a.dp(8));
            bg.setStroke(1, a.color(R.color.c_divider));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = a.dp(8);
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setOnClickListener(v -> a.showHint(hint));
            TextView ic = new TextView(a);
            ic.setText(icon); ic.setTextSize(22f);
            ic.setPadding(0, 0, a.dp(12), 0);
            row.addView(ic);
            LinearLayout text = new LinearLayout(a);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = new TextView(a);
            t.setText(title); t.setTextSize(14f); t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(a.color(R.color.c_text_primary));
            TextView s = new TextView(a);
            s.setText(sub); s.setTextSize(11f);
            s.setTextColor(a.color(R.color.c_text_secondary));
            text.addView(t); text.addView(s);
            row.addView(text);
            return row;
        }
    }

    static class FinishPage extends TourPage {
        FinishPage() { super("You're all set! 🎉",
                "That's the whole app. You can reopen this tour any time from the home menu."); }
        @Override void buildMockup(HelpTourActivity a, FrameLayout container) {
            LinearLayout c = a.mockupCard();
            c.setGravity(Gravity.CENTER);
            TextView big = new TextView(a);
            big.setText("🏏");
            big.setTextSize(64f);
            big.setGravity(Gravity.CENTER);
            big.setPadding(0, a.dp(16), 0, a.dp(8));
            c.addView(big);

            TextView msg = new TextView(a);
            msg.setText("Ready to score?\nTap \"Get started\" to head back home.");
            msg.setGravity(Gravity.CENTER);
            msg.setTextColor(a.color(R.color.c_text_secondary));
            msg.setTextSize(14f);
            msg.setLineSpacing(0, 1.3f);
            msg.setPadding(0, 0, 0, a.dp(16));
            c.addView(msg);
            container.addView(c);
        }
        @Override void buildBody(HelpTourActivity a, LinearLayout container) {
            a.addBullet(container, "Found a bug? Shake your phone to report it to cricketscorer.support@gmail.com");
            a.addBullet(container, "Need to restore data from backup file? Use Bacup/Restore option from the home menu");
            a.addBullet(container, "Want this tour again? Tap Quick tour / Help on home");
        }
    }
}
