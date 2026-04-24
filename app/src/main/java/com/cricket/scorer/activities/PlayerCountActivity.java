package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;

/**
 * PlayerCountActivity.java
 *
 * CHANGE: Added batting mode selection below the player count picker.
 *
 * Two options shown as toggle cards:
 *   ① Single batsman — only the striker bats; no non-striker.
 *                      Strike NEVER rotates on odd runs or end of over.
 *                      Wicket brings in next player at striker's end only.
 *
 *   ② Two batsmen    — standard cricket: striker + non-striker.
 *                      Strike rotates on odd runs and at end of each over.
 *                      Wicket replaces the dismissed striker; non-striker stays.
 *
 * Both choices are passed to SetupActivity via Intent extras:
 *   KEY_PLAYER_COUNT  (int)
 *   KEY_BATTING_MODE  (String: "single" or "two")
 *
 * Layout: activity_player_count.xml
 */
public class PlayerCountActivity extends AppCompatActivity {

    // ─── Intent extra keys ────────────────────────────────────────────────────
    public static final String KEY_PLAYER_COUNT = "player_count";
    public static final String KEY_BATTING_MODE = "batting_mode";

    // ─── Batting mode constants ───────────────────────────────────────────────
    public static final String MODE_SINGLE = "single"; // only striker, no non-striker
    public static final String MODE_TWO    = "two";    // striker + non-striker (standard)

    // ─── Player count bounds ──────────────────────────────────────────────────
    private static final int MIN_PLAYERS    = 2;
    private static final int MAX_PLAYERS    = 11;
    private static final int DEFAULT_PLAYERS = 11;

    // ─── State ────────────────────────────────────────────────────────────────
    private int    playerCount  = DEFAULT_PLAYERS;
    private String battingMode  = MODE_TWO; // default: standard two-batsman mode

    // ─── Views ────────────────────────────────────────────────────────────────
    private TextView    tvCount;
    private TextView    tvCountLabel;
    private ImageButton btnMinus;
    private ImageButton btnPlus;

    // Batting mode toggle cards
    private LinearLayout cardSingle;
    private LinearLayout cardTwo;
    private TextView     tvSingleCheck;
    private TextView     tvTwoCheck;

    private Button btnContinue;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_count);

        bindViews();
        setClickListeners();
        updateCountDisplay();
        updateModeDisplay();
    }

    // ─── View binding ─────────────────────────────────────────────────────────

    private void bindViews() {
        tvCount      = findViewById(R.id.tv_player_count);
        tvCountLabel = findViewById(R.id.tv_count_label);
        btnMinus     = findViewById(R.id.btn_minus);
        btnPlus      = findViewById(R.id.btn_plus);
        cardSingle   = findViewById(R.id.card_mode_single);
        cardTwo      = findViewById(R.id.card_mode_two);
        tvSingleCheck = findViewById(R.id.tv_single_check);
        tvTwoCheck    = findViewById(R.id.tv_two_check);
        btnContinue  = findViewById(R.id.btn_continue);
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setClickListeners() {

        btnMinus.setOnClickListener(v -> {
            if (playerCount > MIN_PLAYERS) {
                playerCount--;
                updateCountDisplay();
            } else {
                Toast.makeText(this,
                        "Minimum " + MIN_PLAYERS + " players per team",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (playerCount < MAX_PLAYERS) {
                playerCount++;
                updateCountDisplay();
            } else {
                Toast.makeText(this,
                        "Maximum " + MAX_PLAYERS + " players per team",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // ── Single batsman card ────────────────────────────────────────
        cardSingle.setOnClickListener(v -> {
            battingMode = MODE_SINGLE;
            updateModeDisplay();
        });

        // ── Two batsmen card ───────────────────────────────────────────
        cardTwo.setOnClickListener(v -> {
            battingMode = MODE_TWO;
            updateModeDisplay();
        });

        // ── Continue ───────────────────────────────────────────────────
        btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(PlayerCountActivity.this, SetupActivity.class);
            intent.putExtra(KEY_PLAYER_COUNT, playerCount);
            intent.putExtra(KEY_BATTING_MODE, battingMode);
            startActivity(intent);
        });
    }

    // ─── UI updates ───────────────────────────────────────────────────────────

    /** Refreshes player count display and +/- button opacity. */
    private void updateCountDisplay() {
        tvCount.setText(String.valueOf(playerCount));
        tvCountLabel.setText(playerCount == 1 ? "player per team" : "players per team");
        btnMinus.setAlpha(playerCount <= MIN_PLAYERS ? 0.35f : 1.0f);
        btnPlus.setAlpha(playerCount >= MAX_PLAYERS ? 0.35f : 1.0f);
    }

    /**
     * Highlights the selected batting mode card with a green border and
     * shows a checkmark; the unselected card gets a neutral gray style.
     */
    private void updateModeDisplay() {
        boolean isSingle = MODE_SINGLE.equals(battingMode);

        // Single card
        cardSingle.setBackgroundResource(
                isSingle ? R.drawable.bg_mode_card_selected
                         : R.drawable.bg_mode_card_default);
        tvSingleCheck.setVisibility(isSingle ? View.VISIBLE : View.INVISIBLE);

        // Two card
        cardTwo.setBackgroundResource(
                isSingle ? R.drawable.bg_mode_card_default
                         : R.drawable.bg_mode_card_selected);
        tvTwoCheck.setVisibility(isSingle ? View.INVISIBLE : View.VISIBLE);
    }
}
