package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
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
 * TournamentSetupActivity
 *
 * Step 1 of tournament setup.
 * Inputs:
 *   - Players per team (2-12, hard cap on screen)
 *   - Number of teams  (2-12)
 *   - Team name fields appear after team count is entered, in a horizontal
 *     scroll. The team count itself is unbounded (user may enter 99) but
 *     only 12 fields render at a time and the user scrolls horizontally
 *     through them — per the requirement.
 *
 * On Continue: builds a Tournament object with empty teams and goes to
 * TournamentPlayersActivity.
 */
public class TournamentSetupActivity extends BaseNavActivity {

    private EditText             etPlayersPerTeam;
    private EditText             etNumberOfTeams;
    private LinearLayout         teamFieldsContainer;
    private HorizontalScrollView teamFieldsScroll;
    private Button               btnContinue;

    private final List<EditText> teamNameFields = new ArrayList<>();

    @Override
    protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournament_setup);

        etPlayersPerTeam    = findViewById(R.id.et_players_per_team);
        etNumberOfTeams     = findViewById(R.id.et_number_of_teams);
        teamFieldsContainer = findViewById(R.id.team_fields_container);
        teamFieldsScroll    = findViewById(R.id.team_fields_scroll);
        btnContinue         = findViewById(R.id.btn_continue);

        // Watch for team count changes — rebuild the input fields
        etNumberOfTeams.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged (CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged (Editable s) { rebuildTeamFields(); }
        });

        btnContinue.setOnClickListener(v -> onContinue());
    }

    private void rebuildTeamFields() {
        teamFieldsContainer.removeAllViews();
        teamNameFields.clear();

        String txt = etNumberOfTeams.getText().toString().trim();
        if (txt.isEmpty()) {
            teamFieldsScroll.setVisibility(View.GONE);
            return;
        }
        int n;
        try { n = Integer.parseInt(txt); } catch (Exception e) { return; }
        if (n < 2) {
            teamFieldsScroll.setVisibility(View.GONE);
            return;
        }

        teamFieldsScroll.setVisibility(View.VISIBLE);
        int fieldWidthDp = 160; // each team field is 160dp wide

        for (int i = 0; i < n; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    dp(fieldWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(dp(4), dp(4), dp(4), dp(4));
            col.setLayoutParams(clp);

            TextView label = new TextView(this);
            label.setText("Team " + (i + 1));
            label.setTextSize(11f);
            label.setTextColor(getResources().getColor(R.color.c_text_secondary, getTheme()));
            label.setPadding(dp(4), 0, 0, dp(2));
            col.addView(label);

            EditText et = new EditText(this);
            et.setHint("Team " + (i + 1) + " name");
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            et.setSingleLine(true);
            et.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            col.addView(et);
            teamNameFields.add(et);

            teamFieldsContainer.addView(col);
        }
    }

    private void onContinue() {
        // Validate players per team
        String ppt = etPlayersPerTeam.getText().toString().trim();
        if (ppt.isEmpty()) { etPlayersPerTeam.setError("Required"); return; }
        int playersPerTeam;
        try { playersPerTeam = Integer.parseInt(ppt); } catch (Exception e) {
            etPlayersPerTeam.setError("Invalid number"); return;
        }
        if (playersPerTeam < 2 || playersPerTeam > 12) {
            etPlayersPerTeam.setError("2 – 12 only"); return;
        }

        // Validate team count
        String tc = etNumberOfTeams.getText().toString().trim();
        if (tc.isEmpty()) { etNumberOfTeams.setError("Required"); return; }
        int teamCount;
        try { teamCount = Integer.parseInt(tc); } catch (Exception e) {
            etNumberOfTeams.setError("Invalid number"); return;
        }
        if (teamCount < 2) { etNumberOfTeams.setError("Minimum 2"); return; }

        // Validate team names
        List<TournamentTeam> teams = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < teamNameFields.size(); i++) {
            String name = teamNameFields.get(i).getText().toString().trim();
            if (name.isEmpty()) name = "Team " + (i + 1);
            if (seen.contains(name)) {
                Toast.makeText(this, "Team names must be unique", Toast.LENGTH_SHORT).show();
                return;
            }
            seen.add(name);
            // Pre-fill placeholder players
            TournamentTeam team = new TournamentTeam(name);
            List<Player> players = new ArrayList<>();
            for (int j = 0; j < playersPerTeam; j++) players.add(new Player(""));
            team.setPlayers(players);
            teams.add(team);
        }

        // Build Tournament and stash in app singleton; persist
        Tournament t = new Tournament();
        t.setPlayersPerTeam(playersPerTeam);
        t.setTeams(teams);
        ((CricketApp) getApplication()).startNewTournament(t);
        TournamentStorage.save(this, t);

        startActivity(new Intent(this, TournamentPlayersActivity.class));
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
