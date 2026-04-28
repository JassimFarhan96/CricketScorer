package com.cricket.scorer.activities;
import android.content.Intent; import android.os.Bundle; import android.text.TextUtils;
import android.view.View; import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.cricket.scorer.R; import com.cricket.scorer.models.*;
import java.util.*;

public class SetupActivity extends AppCompatActivity {
    private int playersPerTeam=11; private boolean singleBatsmanMode=false;
    private EditText etHomeTeam,etAwayTeam,etOvers; private Spinner spinnerToss;
    private LinearLayout containerHomePlayers,containerAwayPlayers; private Button btnStartMatch;
    private EditText[] homePlayerFields,awayPlayerFields; private String battingFirst="home";

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s); setContentView(R.layout.activity_setup);
        playersPerTeam=getIntent().getIntExtra(PlayerCountActivity.KEY_PLAYER_COUNT,11);
        String mode=getIntent().getStringExtra(PlayerCountActivity.KEY_BATTING_MODE);
        singleBatsmanMode=PlayerCountActivity.MODE_SINGLE.equals(mode);
        homePlayerFields=new EditText[playersPerTeam]; awayPlayerFields=new EditText[playersPerTeam];
        bindViews(); setupTossSpinner(); buildPlayerInputFields(); setClickListeners();
    }

    private void bindViews() {
        etHomeTeam=findViewById(R.id.et_home_team); etAwayTeam=findViewById(R.id.et_away_team);
        etOvers=findViewById(R.id.et_overs); spinnerToss=findViewById(R.id.spinner_toss);
        containerHomePlayers=findViewById(R.id.container_home_players);
        containerAwayPlayers=findViewById(R.id.container_away_players);
        btnStartMatch=findViewById(R.id.btn_start_match);
    }

    private void setupTossSpinner() {
        final String[] opts={"Home team bats first","Away team bats first"};
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,opts){
            @Override public View getView(int p,View cv,android.view.ViewGroup vg){
                View v=super.getView(p,cv,vg);((TextView)v).setTextColor(c(R.color.c_text_primary));((TextView)v).setTextSize(14f);return v;}
            @Override public View getDropDownView(int p,View cv,android.view.ViewGroup vg){
                View v=super.getDropDownView(p,cv,vg);((TextView)v).setTextColor(c(R.color.c_text_primary));
                ((TextView)v).setBackgroundColor(c(R.color.c_bg_spinner_popup));((TextView)v).setPadding(32,24,32,24);return v;}
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerToss.setAdapter(a);
        spinnerToss.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?>p,View v,int pos,long id){battingFirst=pos==0?"home":"away";}
            @Override public void onNothingSelected(AdapterView<?>p){}
        });
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
        startActivity(new Intent(this,InningsActivity.class)); finish();
    }

    private List<Player> buildList(EditText[] fields,String team){
        List<Player> l=new ArrayList<>();
        for(int i=0;i<fields.length;i++){String n=fields[i].getText().toString().trim();if(TextUtils.isEmpty(n))n=team+" P"+(i+1);l.add(new Player(n));}
        return l;
    }

    private int c(int res){return getResources().getColor(res,getTheme());}
}
