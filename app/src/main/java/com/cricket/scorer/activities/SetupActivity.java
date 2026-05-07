package com.cricket.scorer.activities;
import android.content.Intent; import android.os.Bundle; import android.text.TextUtils;
import android.view.View; import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.cricket.scorer.R; import com.cricket.scorer.models.*;
import java.util.*;

public class SetupActivity extends AppCompatActivity {
    private int playersPerTeam=11; private boolean singleBatsmanMode=false;
    private EditText etHomeTeam,etAwayTeam,etOvers;
    private Spinner spinnerTossWinner, spinnerTossChoice;
    private TextView tvTossSummary;
    private TextView tvLabelHomePlayers, tvLabelAwayPlayers;
    private LinearLayout containerHomePlayers,containerAwayPlayers; private Button btnStartMatch;
    private EditText[] homePlayerFields,awayPlayerFields; private String battingFirst="home";

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s); setContentView(R.layout.activity_setup);
        playersPerTeam=getIntent().getIntExtra(PlayerCountActivity.KEY_PLAYER_COUNT,11);
        String mode=getIntent().getStringExtra(PlayerCountActivity.KEY_BATTING_MODE);
        singleBatsmanMode=PlayerCountActivity.MODE_SINGLE.equals(mode);
        homePlayerFields=new EditText[playersPerTeam]; awayPlayerFields=new EditText[playersPerTeam];
        bindViews(); setupTossSpinners(); buildPlayerInputFields(); setClickListeners();
    }

    private void bindViews() {
        etHomeTeam=findViewById(R.id.et_home_team); etAwayTeam=findViewById(R.id.et_away_team);
        etOvers=findViewById(R.id.et_overs);
        spinnerTossWinner=findViewById(R.id.spinner_toss_winner);
        spinnerTossChoice=findViewById(R.id.spinner_toss_choice);
        tvTossSummary=findViewById(R.id.tv_toss_summary);
        tvLabelHomePlayers=findViewById(R.id.tv_label_home_players);
        tvLabelAwayPlayers=findViewById(R.id.tv_label_away_players);
        containerHomePlayers=findViewById(R.id.container_home_players);
        containerAwayPlayers=findViewById(R.id.container_away_players);
        btnStartMatch=findViewById(R.id.btn_start_match);
    }

    /**
     * Sets up the two toss spinners.
     * Spinner 1: which team won the toss (Home/Away — uses team names if entered)
     * Spinner 2: what they chose (Bat / Bowl)
     *
     * The combination determines battingFirst:
     *   winner=Home + Bat   → home bats first
     *   winner=Home + Bowl  → away bats first
     *   winner=Away + Bat   → away bats first
     *   winner=Away + Bowl  → home bats first
     *
     * The summary text below updates live so the user can confirm the order
     * they're about to play in. No popup needed — it's all inline.
     */
    private void setupTossSpinners() {
        rebuildTossWinnerOptions();

        // Choice spinner is static
        ArrayAdapter<String> choiceAdapter = makeAdapter(
                new String[]{"Bat first", "Bowl first"});
        spinnerTossChoice.setAdapter(choiceAdapter);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                recomputeBattingFirst();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        spinnerTossWinner.setOnItemSelectedListener(listener);
        spinnerTossChoice.setOnItemSelectedListener(listener);

        // Refresh winner spinner options + player labels when team names change
        // so user sees the actual names, not "Home"/"Away" placeholders.
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged (CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                int prev = spinnerTossWinner.getSelectedItemPosition();
                rebuildTossWinnerOptions();
                if (prev >= 0 && prev < 2) spinnerTossWinner.setSelection(prev);
                refreshPlayerLabels();
            }
        };
        etHomeTeam.addTextChangedListener(tw);
        etAwayTeam.addTextChangedListener(tw);
        // Initialize labels so they reflect any pre-filled text
        refreshPlayerLabels();
    }

    /**
     * Updates the "HOME TEAM PLAYERS" / "AWAY TEAM PLAYERS" section labels
     * to use the actual team names. Falls back to "Home" / "Away" when blank.
     */
    private void refreshPlayerLabels() {
        String home = etHomeTeam.getText().toString().trim();
        String away = etAwayTeam.getText().toString().trim();
        if (home.isEmpty()) home = "Home";
        if (away.isEmpty()) away = "Away";
        tvLabelHomePlayers.setText(home.toUpperCase() + " PLAYERS");
        tvLabelAwayPlayers.setText(away.toUpperCase() + " PLAYERS");
    }

    private void rebuildTossWinnerOptions() {
        String home = etHomeTeam.getText().toString().trim();
        String away = etAwayTeam.getText().toString().trim();
        if (home.isEmpty()) home = "Home team";
        if (away.isEmpty()) away = "Away team";
        ArrayAdapter<String> a = makeAdapter(new String[]{home, away});
        spinnerTossWinner.setAdapter(a);
    }

    private ArrayAdapter<String> makeAdapter(String[] opts) {
        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, opts){
            @Override public View getView(int p, View cv, android.view.ViewGroup vg) {
                View v = super.getView(p, cv, vg);
                ((TextView)v).setTextColor(c(R.color.c_text_primary));
                ((TextView)v).setTextSize(14f);
                return v;
            }
            @Override public View getDropDownView(int p, View cv, android.view.ViewGroup vg) {
                View v = super.getDropDownView(p, cv, vg);
                ((TextView)v).setTextColor(c(R.color.c_text_primary));
                ((TextView)v).setBackgroundColor(c(R.color.c_bg_spinner_popup));
                ((TextView)v).setPadding(32, 24, 32, 24);
                return v;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    /**
     * Compute battingFirst from the two spinners and update the summary line.
     * homeWonToss=true & choseBat=true   → home bats
     * homeWonToss=true & choseBat=false  → away bats
     * homeWonToss=false & choseBat=true  → away bats
     * homeWonToss=false & choseBat=false → home bats
     */
    private void recomputeBattingFirst() {
        boolean homeWonToss = spinnerTossWinner.getSelectedItemPosition() == 0;
        boolean choseBat    = spinnerTossChoice.getSelectedItemPosition() == 0;
        boolean homeBats    = homeWonToss == choseBat; // XNOR
        battingFirst = homeBats ? "home" : "away";

        String home = etHomeTeam.getText().toString().trim();
        String away = etAwayTeam.getText().toString().trim();
        if (home.isEmpty()) home = "Home team";
        if (away.isEmpty()) away = "Away team";
        String winner = homeWonToss ? home : away;
        String batter = homeBats ? home : away;
        tvTossSummary.setText("→ " + winner + " won toss, "
                + batter + " bats first");
    }

    private void buildPlayerInputFields() {
        for(int i=0;i<playersPerTeam;i++){
            homePlayerFields[i]=createField(i+1); containerHomePlayers.addView(homePlayerFields[i]);
            awayPlayerFields[i]=createField(i+1); containerAwayPlayers.addView(awayPlayerFields[i]);
        }
    }

    private EditText createField(int n) {
        EditText et=new EditText(this);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,4,0,4); et.setLayoutParams(p); et.setHint("Player "+n);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setTextColor(c(R.color.c_text_primary)); et.setHintTextColor(c(R.color.c_text_hint));
        et.setBackgroundResource(R.drawable.bg_input_field); et.setPadding(40,28,40,28); et.setTextSize(14f);
        return et;
    }

    private void setClickListeners(){ btnStartMatch.setOnClickListener(v->{if(validateInputs())buildMatchAndStart();}); }

    private boolean validateInputs(){
        String hn=etHomeTeam.getText().toString().trim(),an=etAwayTeam.getText().toString().trim(),ov=etOvers.getText().toString().trim();
        if(TextUtils.isEmpty(hn)){etHomeTeam.setError("Enter home team name");etHomeTeam.requestFocus();return false;}
        if(TextUtils.isEmpty(an)){etAwayTeam.setError("Enter away team name");etAwayTeam.requestFocus();return false;}
        if(hn.equalsIgnoreCase(an)){Toast.makeText(this,"Team names must be different",Toast.LENGTH_SHORT).show();return false;}
        if(TextUtils.isEmpty(ov)){etOvers.setError("Enter number of overs");etOvers.requestFocus();return false;}
        int overs; try{overs=Integer.parseInt(ov);}catch(NumberFormatException e){etOvers.setError("Enter a valid number");etOvers.requestFocus();return false;}
        if(overs<=0){etOvers.setError("Overs must be greater than 0");etOvers.requestFocus();return false;}
        if(overs>50){etOvers.setError("Overs cannot exceed 50");etOvers.requestFocus();return false;}
        return true;
    }

    private void buildMatchAndStart(){
        String hn=etHomeTeam.getText().toString().trim(),an=etAwayTeam.getText().toString().trim();
        int overs=Integer.parseInt(etOvers.getText().toString().trim());
        List<Player> hp=buildList(homePlayerFields,hn),ap=buildList(awayPlayerFields,an);
        Match m=new Match(); m.setHomeTeamName(hn); m.setAwayTeamName(an);
        m.setMaxOvers(overs); m.setBattingFirstTeam(battingFirst); m.setHomePlayers(hp); m.setAwayPlayers(ap);
        m.setSingleBatsmanMode(singleBatsmanMode);
        Innings fi=new Innings(1,singleBatsmanMode); m.setFirstInnings(fi); m.setCurrentInnings(1);
        ((CricketApp)getApplication()).startNewMatch(m);
        // Ask about joker before starting innings
        showJokerSetupDialog(m);
    }

    /**
     * Step 1 of joker setup: ask if there is a joker player.
     * Non-cancellable — must choose Yes or No.
     */
    private void showJokerSetupDialog(Match match) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Joker Player")
                .setMessage("Is there a Joker player in this match?\n\n"
                        + "A Joker is a special player shared by both teams — "
                        + "they can bat for the batting team AND bowl for the "
                        + "bowling team in the same innings.")
                .setCancelable(false)
                .setPositiveButton("Yes — Enter Joker Name", (d, w) -> showJokerNameDialog(match))
                .setNegativeButton("No Joker", (d, w) -> launchInnings())
                .show();
    }

    /**
     * Step 2 of joker setup: text input for the joker's name.
     * Validates that a name is entered before proceeding.
     */
    private void showJokerNameDialog(Match match) {
        int pad = (int)(20 * getResources().getDisplayMetrics().density);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        android.widget.TextView tvInfo = new android.widget.TextView(this);
        tvInfo.setText("Enter the Joker player's name.\n"
                + "This player can bat AND bowl in the same innings.");
        tvInfo.setTextSize(13f);
        tvInfo.setTextColor(c(R.color.c_text_secondary));
        android.widget.LinearLayout.LayoutParams tvLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.setMargins(0, 0, 0, pad / 2);
        tvInfo.setLayoutParams(tvLp);
        layout.addView(tvInfo);

        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("Joker player name");
        etName.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        etName.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(etName);

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Enter Joker Player Name")
                        .setView(layout)
                        .setCancelable(false)
                        .setPositiveButton("Confirm", null) // overridden below
                        .setNegativeButton("No Joker", (d, w) -> launchInnings())
                        .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) {
                            etName.setError("Please enter a name");
                            return;
                        }
                        match.setHasJoker(true);
                        match.setJokerName(name);
                        match.setJokerRole(com.cricket.scorer.models.Match.JokerRole.NONE);
                        // Add joker to both team player lists so they appear in all dialogs
                        match.getHomePlayers().add(new com.cricket.scorer.models.Player(name));
                        match.getAwayPlayers().add(new com.cricket.scorer.models.Player(name));
                        android.widget.Toast.makeText(this,
                                name + " is the Joker! ⚡",
                                android.widget.Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        launchInnings();
                    });
            // Auto-show keyboard
            etName.requestFocus();
        });
        dialog.show();
    }

    private void launchInnings() {
        startActivity(new Intent(this, InningsActivity.class));
        finish();
    }

    private List<Player> buildList(EditText[] fields,String team){
        List<Player> l=new ArrayList<>();
        for(int i=0;i<fields.length;i++){String n=fields[i].getText().toString().trim();if(TextUtils.isEmpty(n))n=team+" P"+(i+1);l.add(new Player(n));}
        return l;
    }

    private int c(int res){return getResources().getColor(res,getTheme());}
}
