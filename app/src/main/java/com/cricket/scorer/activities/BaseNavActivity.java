package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * BaseNavActivity
 *
 * Base class for all activities that show the bottom navigation bar.
 * Subclasses call setNavContentView(layoutResId) instead of setContentView().
 *
 * Navigation tabs:
 *   Home       → HomeActivity
 *   Recent     → RecentMenuActivity (then matches or tournaments)
 *   Statistics → MatchSelectActivity
 *
 * Each tab clears the back stack so pressing a tab always goes to the
 * root of that section. The currently selected tab is highlighted.
 *
 * Excluded (no nav bar):
 *   InningsActivity, InningsBreakActivity — match in progress
 */
public abstract class BaseNavActivity extends AppCompatActivity {

    protected BottomNavigationView bottomNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Must be called by subclasses instead of setContentView().
     * Inflates the activity_base_nav layout (which wraps the child content),
     * sets the child content, and wires up the bottom nav.
     */
    protected void setNavContentView(@LayoutRes int childLayoutRes) {
        setContentView(R.layout.activity_base_nav);
        // Inflate the child layout into the content container
        getLayoutInflater().inflate(childLayoutRes,
                findViewById(R.id.nav_content_frame), true);
        setupBottomNav();
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav == null) return;

        // Highlight the tab that matches this activity
        bottomNav.setSelectedItemId(getCurrentNavItem());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                if (!(this instanceof HomeActivity)) {
                    navigateTo(HomeActivity.class);
                }
                return true;
            } else if (id == R.id.nav_recent) {
                // Recent tab now lands on a sub-menu (matches vs tournaments).
                // Once the user is inside any of those branches (Recent menu,
                // Recent matches, or Recent tournaments), tapping the tab
                // again is a no-op rather than bouncing back to the menu.
                if (!(this instanceof RecentMenuActivity)) {
                        //&& !(this instanceof RecentMatchesActivity)
                        //&& !(this instanceof RecentTournamentsActivity)) {
                    navigateTo(RecentMenuActivity.class);
                }
                return true;
            } else if (id == R.id.nav_stats) {
                if (!(this instanceof MatchSelectActivity)
                        && !(this instanceof StatsActivity)
                        && !(this instanceof DeepStatsActivity)) {
                    navigateTo(MatchSelectActivity.class);
                }
                return true;
            }
            return false;
        });
    }

    /** Navigate to a root-level tab destination, clearing the back stack. */
    private void navigateTo(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    /**
     * Subclasses override this to return their nav item ID so it can be highlighted.
     * Default: nav_home.
     */
    protected int getCurrentNavItem() {
        return R.id.nav_home;
    }
}
