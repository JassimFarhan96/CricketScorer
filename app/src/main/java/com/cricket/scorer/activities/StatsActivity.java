package com.cricket.scorer.activities;
import android.content.Intent; import android.graphics.Typeface; import android.os.Bundle;
import android.view.View; import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.cricket.scorer.R; import com.cricket.scorer.models.*;
import com.cricket.scorer.utils.*; import java.io.File; import java.util.*; import java.util.Locale;

public class StatsActivity extends AppCompatActivity {
    public static final String EXTRA_SAVED_FILE_NAME="saved_file_name";
    private LinearLayout layoutWinnerBanner;
    private TextView tvWinnerText,tvWinnerSub,tvTeam1Name,tvTeam1Score,tvTeam1Crr,tvTeam2Name,tvTeam2Score,tvTeam2Crr;
    private TableLayout tableFirstInnings,tableSecondInnings;
    private EditText etWhatsappNumber;
    private Button btnSaveMatch,btnShareWhatsApp,btnShareGeneral,btnNewMatch;
    private Match match; private boolean isViewingFromDisk=false;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s); setContentView(R.layout.activity_stats); bindViews();
        String sf=getIntent().getStringExtra(EXTRA_SAVED_FILE_NAME);
        if(sf!=null){isViewingFromDisk=true;for(Match m:MatchStorage.loadAllMatches(this))if(sf.equals(m.getSavedFileName())){match=m;break;}}
        else{match=((CricketApp)getApplication()).getCurrentMatch();LiveMatchState.clear(this);}
        if(match==null){Toast.makeText(this,"Match data not found",Toast.LENGTH_SHORT).show();finish();return;}
        populateStats(); setClickListeners();
    }

    private void bindViews(){
        layoutWinnerBanner=findViewById(R.id.layout_winner_banner);tvWinnerText=findViewById(R.id.tv_winner_text);tvWinnerSub=findViewById(R.id.tv_winner_sub);
        tvTeam1Name=findViewById(R.id.tv_team1_name);tvTeam1Score=findViewById(R.id.tv_team1_score);tvTeam1Crr=findViewById(R.id.tv_team1_crr);
        tvTeam2Name=findViewById(R.id.tv_team2_name);tvTeam2Score=findViewById(R.id.tv_team2_score);tvTeam2Crr=findViewById(R.id.tv_team2_crr);
        tableFirstInnings=findViewById(R.id.table_first_innings);tableSecondInnings=findViewById(R.id.table_second_innings);
        etWhatsappNumber=findViewById(R.id.et_whatsapp_number);btnSaveMatch=findViewById(R.id.btn_save_match);
        btnShareWhatsApp=findViewById(R.id.btn_share_whatsapp);btnShareGeneral=findViewById(R.id.btn_share_general);btnNewMatch=findViewById(R.id.btn_new_match);
    }

    private void populateStats(){
        Innings i1=match.getFirstInnings(),i2=match.getSecondInnings();
        String bat1=match.getBattingFirstTeam().equals("home")?match.getHomeTeamName():match.getAwayTeamName();
        String bat2=match.getBattingFirstTeam().equals("home")?match.getAwayTeamName():match.getHomeTeamName();
        layoutWinnerBanner.setVisibility(View.VISIBLE);
        tvWinnerText.setText(match.getResultDescription()!=null?match.getResultDescription():"Match complete");
        tvWinnerSub.setText(match.getHomeTeamName()+" vs "+match.getAwayTeamName()+" \u00b7 "+match.getMaxOvers()+" overs");
        if(i1!=null){tvTeam1Name.setText(bat1);tvTeam1Score.setText(i1.getScoreString());tvTeam1Crr.setText(String.format(Locale.US,"%s ov \u00b7 CRR %.2f",i1.getOversString(),i1.getCurrentRunRate()));}
        if(i2!=null){tvTeam2Name.setText(bat2);tvTeam2Score.setText(i2.getScoreString());tvTeam2Crr.setText(String.format(Locale.US,"%s ov \u00b7 CRR %.2f",i2.getOversString(),i2.getCurrentRunRate()));}
        buildTable(tableFirstInnings,match.getBattingFirstTeam().equals("home")?match.getHomePlayers():match.getAwayPlayers());
        buildTable(tableSecondInnings,match.getBattingFirstTeam().equals("home")?match.getAwayPlayers():match.getHomePlayers());
        btnSaveMatch.setVisibility(isViewingFromDisk?View.GONE:View.VISIBLE);
    }

    private void buildTable(TableLayout t, List<Player> pl){
        t.removeAllViews(); addRow(t,new String[]{"Batsman","R","B","4s","6s","SR","Status"},true);
        for(Player p:pl){
            if(p.isHasNotBatted()&&p.getBallsFaced()==0&&p.getRunsScored()==0) continue;
            String sr=p.getBallsFaced()>0?String.format(Locale.US,"%.1f",p.getStrikeRate()):"-";
            addRow(t,new String[]{p.getName(),String.valueOf(p.getRunsScored()),String.valueOf(p.getBallsFaced()),
                String.valueOf(p.getFours()),String.valueOf(p.getSixes()),sr,p.isOut()?"Out":"Not out"},false);
        }
    }

    private void addRow(TableLayout t,String[] cells,boolean hdr){
        TableRow row=new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,TableRow.LayoutParams.WRAP_CONTENT));
        if(hdr) row.setBackgroundColor(c(R.color.c_row_header_bg));
        int[] w={3,1,1,1,1,1,2};
        for(int i=0;i<cells.length;i++){
            TextView tv=new TextView(this);tv.setLayoutParams(new TableRow.LayoutParams(0,TableRow.LayoutParams.WRAP_CONTENT,w[i]));
            tv.setText(cells[i]);tv.setPadding(10,8,10,8);tv.setTextSize(11.5f);
            tv.setTextColor(hdr?c(R.color.c_row_header_text):c(R.color.c_text_primary));
            if(hdr) tv.setTypeface(null,Typeface.BOLD); t.addView(row); break;
        }
        // rebuild properly
        t.removeView(row); row=new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,TableRow.LayoutParams.WRAP_CONTENT));
        if(hdr) row.setBackgroundColor(c(R.color.c_row_header_bg));
        for(int i=0;i<cells.length;i++){
            TextView tv=new TextView(this);tv.setLayoutParams(new TableRow.LayoutParams(0,TableRow.LayoutParams.WRAP_CONTENT,w[i]));
            tv.setText(cells[i]);tv.setPadding(10,8,10,8);tv.setTextSize(11.5f);
            tv.setTextColor(hdr?c(R.color.c_row_header_text):c(R.color.c_text_primary));
            if(hdr) tv.setTypeface(null,Typeface.BOLD);
            row.addView(tv);
        }
        t.addView(row);
    }

    private void setClickListeners(){
        btnSaveMatch.setOnClickListener(v->{
            File f=MatchStorage.saveMatch(this,match);
            if(f!=null){btnSaveMatch.setEnabled(false);btnSaveMatch.setText("Saved \u2713");
                btnSaveMatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c(R.color.green_mid)));
                Toast.makeText(this,"Match saved",Toast.LENGTH_SHORT).show();}
            else Toast.makeText(this,"Failed to save",Toast.LENGTH_SHORT).show();
        });
        btnShareWhatsApp.setOnClickListener(v->{
            String n=etWhatsappNumber.getText().toString().trim();
            if(n.isEmpty()){Toast.makeText(this,"Enter a WhatsApp number",Toast.LENGTH_SHORT).show();return;}
            if(!n.startsWith("+"))n="+"+n; ShareUtils.shareViaWhatsApp(this,n,match);
        });
        btnShareGeneral.setOnClickListener(v->ShareUtils.shareAsText(this,match));
        btnNewMatch.setOnClickListener(v->{
            ((CricketApp)getApplication()).clearMatch();
            Intent i=new Intent(this,HomeActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);finish();
        });
    }

    @Override public void onBackPressed(){if(!isViewingFromDisk)((CricketApp)getApplication()).clearMatch();super.onBackPressed();}
    private int c(int res){return getResources().getColor(res,getTheme());}
}
