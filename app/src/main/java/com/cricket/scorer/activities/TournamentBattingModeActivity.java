package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.cricket.scorer.R;
import com.cricket.scorer.models.Tournament;
import com.cricket.scorer.utils.TournamentStorage;

/**
 * TournamentBattingModeActivity
 *
 * Step 3: choose single batsman vs two batsmen mode + max overs per match.
 */
public class TournamentBattingModeActivity extends BaseNavActivity {

    private RadioGroup radioGroup;
    private EditText   etOvers;
    private Button     btnContinue;

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_tournament_batting_mode);
        radioGroup  = findViewById(R.id.radio_mode);
        etOvers     = findViewById(R.id.et_overs);
        btnContinue = findViewById(R.id.btn_continue);
        btnContinue.setOnClickListener(v -> onContinue());
    }

    private void onContinue() {
        Tournament t = ((CricketApp) getApplication()).getCurrentTournament();
        if (t == null) { finish(); return; }

        boolean single = radioGroup.getCheckedRadioButtonId() == R.id.radio_single;
        t.setSingleBatsmanMode(single);

        String ov = etOvers.getText().toString().trim();
        if (ov.isEmpty()) { etOvers.setError("Required"); return; }
        int overs;
        try { overs = Integer.parseInt(ov); } catch (Exception e) {
            etOvers.setError("Invalid"); return;
        }
        if (overs < 1 || overs > 50) { etOvers.setError("1 – 50 only"); return; }
        t.setMaxOversPerMatch(overs);

        TournamentStorage.save(this, t);
        startActivity(new Intent(this, TournamentScheduleActivity.class));
    }
}
