package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.models.TournamentTeam;
import com.cricket.scorer.utils.TournamentStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * TournamentPlayersActivity
 *
 * Step 2 of tournament setup.
 * Per-team horizontal scroll of player name inputs.
 * Each team displays as a card with a horizontal scroll of N player fields
 * (N = playersPerTeam).
 */
public class TournamentPlayersActivity extends BaseNavActivity {

    private LinearLayout teamsContainer;
    private Button       btnContinue;

    /** Outer list = teams; inner list = player EditTexts for that team */
    private final List<List<EditText>> playerFieldGrid = new ArrayList<>();

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournament_players);

        teamsContainer = findViewById(R.id.teams_container);
        btnContinue    = findViewById(R.id.btn_continue);
        btnContinue.setOnClickListener(v -> onContinue());

        Tournament t = ((CricketApp) getApplication()).getCurrentTournament();
        if (t == null) { finish(); return; }

        buildPlayerInputs(t);
    }

    private void buildPlayerInputs(Tournament t) {
        int playersPerTeam = t.getPlayersPerTeam();
        for (int ti = 0; ti < t.getTeams().size(); ti++) {
            TournamentTeam team = t.getTeams().get(ti);

            // Team card
            LinearLayout teamCard = new LinearLayout(this);
            teamCard.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(dp(12), dp(8), dp(12), dp(8));
            teamCard.setLayoutParams(cardLp);
            teamCard.setBackgroundColor(getResources().getColor(R.color.c_bg_card, getTheme()));
            teamCard.setPadding(dp(12), dp(12), dp(12), dp(12));

            TextView teamName = new TextView(this);
            teamName.setText(team.getName());
            teamName.setTextSize(15f);
            teamName.setTypeface(null, android.graphics.Typeface.BOLD);
            teamName.setTextColor(getResources().getColor(R.color.c_text_primary, getTheme()));
            teamCard.addView(teamName);

            // Horizontal scroll of player fields
            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            hScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout playerRow = new LinearLayout(this);
            playerRow.setOrientation(LinearLayout.HORIZONTAL);

            List<EditText> fields = new ArrayList<>();
            for (int p = 0; p < playersPerTeam; p++) {
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        dp(140), LinearLayout.LayoutParams.WRAP_CONTENT);
                clp.setMargins(dp(4), dp(4), dp(4), dp(4));
                col.setLayoutParams(clp);

                TextView lbl = new TextView(this);
                lbl.setText("Player " + (p + 1));
                lbl.setTextSize(10f);
                lbl.setTextColor(getResources().getColor(R.color.c_text_secondary, getTheme()));
                col.addView(lbl);

                EditText et = new EditText(this);
                et.setHint("Player " + (p + 1));
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                et.setSingleLine(true);
                if (p < team.getPlayers().size()) {
                    String existing = team.getPlayers().get(p).getName();
                    if (existing != null && !existing.isEmpty()) et.setText(existing);
                }
                et.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                col.addView(et);
                fields.add(et);
                playerRow.addView(col);
            }
            playerFieldGrid.add(fields);

            hScroll.addView(playerRow);
            teamCard.addView(hScroll);
            teamsContainer.addView(teamCard);
        }
    }

    private void onContinue() {
        Tournament t = ((CricketApp) getApplication()).getCurrentTournament();
        if (t == null) { finish(); return; }

        // Save player names back into the tournament
        for (int ti = 0; ti < playerFieldGrid.size(); ti++) {
            TournamentTeam team = t.getTeams().get(ti);
            List<Player> updated = new ArrayList<>();
            List<EditText> fields = playerFieldGrid.get(ti);
            for (int p = 0; p < fields.size(); p++) {
                String name = fields.get(p).getText().toString().trim();
                if (name.isEmpty()) name = team.getName() + " P" + (p + 1);
                updated.add(new Player(name));
            }
            team.setPlayers(updated);
        }
        TournamentStorage.save(this, t);
        startActivity(new Intent(this, TournamentBattingModeActivity.class));
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
