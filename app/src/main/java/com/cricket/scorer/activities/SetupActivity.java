package com.cricket.scorer.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * SetupActivity.java
 *
 * CHANGE: Removed the lines that auto-marked openers as hasNotBatted=false.
 * All players now start with hasNotBatted=true. InningsActivity shows a
 * dialog at the start of each innings so the user explicitly picks who bats.
 * The opener selection dialog in InningsActivity sets hasNotBatted=false
 * for the chosen striker (and non-striker in two-batsman mode).
 */
public class SetupActivity extends AppCompatActivity {

    private int     playersPerTeam    = 11;
    private boolean singleBatsmanMode = false;

    private EditText     etHomeTeam, etAwayTeam, etOvers;
    private Spinner      spinnerToss;
    private LinearLayout containerHomePlayers, containerAwayPlayers;
    private Button       btnStartMatch;

    private EditText[] homePlayerFields;
    private EditText[] awayPlayerFields;

    private String battingFirst = "home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        playersPerTeam    = getIntent().getIntExtra(PlayerCountActivity.KEY_PLAYER_COUNT, 11);
        String mode       = getIntent().getStringExtra(PlayerCountActivity.KEY_BATTING_MODE);
        singleBatsmanMode = PlayerCountActivity.MODE_SINGLE.equals(mode);

        homePlayerFields = new EditText[playersPerTeam];
        awayPlayerFields = new EditText[playersPerTeam];

        bindViews();
        setupTossSpinner();
        buildPlayerInputFields();
        setClickListeners();
    }

    private void bindViews() {
        etHomeTeam           = findViewById(R.id.et_home_team);
        etAwayTeam           = findViewById(R.id.et_away_team);
        etOvers              = findViewById(R.id.et_overs);
        spinnerToss          = findViewById(R.id.spinner_toss);
        containerHomePlayers = findViewById(R.id.container_home_players);
        containerAwayPlayers = findViewById(R.id.container_away_players);
        btnStartMatch        = findViewById(R.id.btn_start_match);

        etHomeTeam.setTextColor(Color.parseColor("#111111"));
        etHomeTeam.setHintTextColor(Color.parseColor("#999999"));
        etAwayTeam.setTextColor(Color.parseColor("#111111"));
        etAwayTeam.setHintTextColor(Color.parseColor("#999999"));
        etOvers.setTextColor(Color.parseColor("#111111"));
        etOvers.setHintTextColor(Color.parseColor("#999999"));
    }

    private void setupTossSpinner() {
        final String[] tossOptions = {"Home team bats first", "Away team bats first"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, tossOptions) {
            @Override
            public View getView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getView(pos, cv, p);
                ((TextView) v).setTextColor(Color.parseColor("#111111"));
                ((TextView) v).setTextSize(14f);
                return v;
            }
            @Override
            public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getDropDownView(pos, cv, p);
                ((TextView) v).setTextColor(Color.parseColor("#111111"));
                ((TextView) v).setBackgroundColor(Color.WHITE);
                ((TextView) v).setPadding(32, 24, 32, 24);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerToss.setAdapter(adapter);
        spinnerToss.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                battingFirst = pos == 0 ? "home" : "away";
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void buildPlayerInputFields() {
        for (int i = 0; i < playersPerTeam; i++) {
            homePlayerFields[i] = createPlayerField(i + 1);
            containerHomePlayers.addView(homePlayerFields[i]);
            awayPlayerFields[i] = createPlayerField(i + 1);
            containerAwayPlayers.addView(awayPlayerFields[i]);
        }
    }

    private EditText createPlayerField(int number) {
        EditText et = new EditText(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        et.setLayoutParams(params);
        et.setHint("Player " + number);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setTextColor(Color.parseColor("#111111"));
        et.setHintTextColor(Color.parseColor("#999999"));
        et.setBackgroundResource(R.drawable.bg_input_field);
        et.setPadding(40, 28, 40, 28);
        et.setTextSize(14f);
        return et;
    }

    private void setClickListeners() {
        btnStartMatch.setOnClickListener(v -> {
            if (validateInputs()) buildMatchAndStart();
        });
    }

    private boolean validateInputs() {
        String homeName  = etHomeTeam.getText().toString().trim();
        String awayName  = etAwayTeam.getText().toString().trim();
        String oversText = etOvers.getText().toString().trim();

        if (TextUtils.isEmpty(homeName)) {
            etHomeTeam.setError("Enter home team name"); etHomeTeam.requestFocus(); return false;
        }
        if (TextUtils.isEmpty(awayName)) {
            etAwayTeam.setError("Enter away team name"); etAwayTeam.requestFocus(); return false;
        }
        if (homeName.equalsIgnoreCase(awayName)) {
            Toast.makeText(this, "Team names must be different", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(oversText)) {
            etOvers.setError("Enter number of overs"); etOvers.requestFocus(); return false;
        }
        int overs;
        try { overs = Integer.parseInt(oversText); }
        catch (NumberFormatException e) {
            etOvers.setError("Enter a valid number"); etOvers.requestFocus(); return false;
        }
        if (overs <= 0) {
            etOvers.setError("Overs must be greater than 0"); etOvers.requestFocus(); return false;
        }
        if (overs > 50) {
            etOvers.setError("Overs cannot exceed 50"); etOvers.requestFocus(); return false;
        }
        return true;
    }

    private void buildMatchAndStart() {
        String homeName = etHomeTeam.getText().toString().trim();
        String awayName = etAwayTeam.getText().toString().trim();
        int    overs    = Integer.parseInt(etOvers.getText().toString().trim());

        List<Player> homePlayers = buildPlayerList(homePlayerFields, homeName);
        List<Player> awayPlayers = buildPlayerList(awayPlayerFields, awayName);

        Match match = new Match();
        match.setHomeTeamName(homeName);
        match.setAwayTeamName(awayName);
        match.setMaxOvers(overs);
        match.setBattingFirstTeam(battingFirst);
        match.setHomePlayers(homePlayers);
        match.setAwayPlayers(awayPlayers);
        match.setSingleBatsmanMode(singleBatsmanMode);

        // Create 1st innings — ALL players have hasNotBatted=true.
        // InningsActivity will show the opener selection dialog and set
        // hasNotBatted=false for the chosen openers before any ball is bowled.
        Innings firstInnings = new Innings(1, singleBatsmanMode);
        match.setFirstInnings(firstInnings);
        match.setCurrentInnings(1);

        ((CricketApp) getApplication()).startNewMatch(match);
        startActivity(new Intent(this, InningsActivity.class));
        finish();
    }

    private List<Player> buildPlayerList(EditText[] fields, String teamName) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < fields.length; i++) {
            String name = fields[i].getText().toString().trim();
            if (TextUtils.isEmpty(name)) name = teamName + " P" + (i + 1);
            players.add(new Player(name));
        }
        return players;
    }
}
