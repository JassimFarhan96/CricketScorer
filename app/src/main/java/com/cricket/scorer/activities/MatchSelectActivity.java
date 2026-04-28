package com.cricket.scorer.activities;
import android.content.Intent; import android.os.Bundle; import android.view.View; import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.cricket.scorer.R; import com.cricket.scorer.models.Match; import com.cricket.scorer.utils.MatchStorage;
import java.util.*; import java.util.Locale;

public class MatchSelectActivity extends AppCompatActivity {
    private Spinner spinnerMatches; private LinearLayout layoutPreview;
    private TextView tvPreviewTeams,tvPreviewResult,tvPreviewScores,tvPreviewDate;
    private Button btnViewStats; private TextView tvEmpty;
    private List<Match> allMatches; private int selectedIndex=0;

    @Override protected void onCreate(Bundle s){super.onCreate(s);setContentView(R.layout.activity_match_select);bindViews();loadMatches();}
    @Override protected void onResume(){super.onResume();loadMatches();}

    private void bindViews(){
        spinnerMatches=findViewById(R.id.spinner_matches);layoutPreview=findViewById(R.id.layout_preview);
        tvPreviewTeams=findViewById(R.id.tv_preview_teams);tvPreviewResult=findViewById(R.id.tv_preview_result);
        tvPreviewScores=findViewById(R.id.tv_preview_scores);tvPreviewDate=findViewById(R.id.tv_preview_date);
        btnViewStats=findViewById(R.id.btn_view_stats);tvEmpty=findViewById(R.id.tv_empty);
    }

    private void loadMatches(){
        allMatches=MatchStorage.loadAllMatches(this);
        if(allMatches.isEmpty()){tvEmpty.setVisibility(View.VISIBLE);spinnerMatches.setVisibility(View.GONE);layoutPreview.setVisibility(View.GONE);btnViewStats.setVisibility(View.GONE);return;}
        tvEmpty.setVisibility(View.GONE);spinnerMatches.setVisibility(View.VISIBLE);btnViewStats.setVisibility(View.VISIBLE);
        String[] labels=new String[allMatches.size()];
        for(int i=0;i<allMatches.size();i++){
            Match m=allMatches.get(i);
            String date=m.getSavedFileName()!=null?RecentMatchesActivity.formatDateFromFileName2(m.getSavedFileName()):"";
            labels[i]=m.getHomeTeamName()+" vs "+m.getAwayTeamName()+"  ("+date+")";
        }
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){
            @Override public View getView(int p,View cv,android.view.ViewGroup vg){
                View v=super.getView(p,cv,vg);((TextView)v).setTextColor(c(R.color.c_text_primary));((TextView)v).setTextSize(13f);return v;}
            @Override public View getDropDownView(int p,View cv,android.view.ViewGroup vg){
                View v=super.getDropDownView(p,cv,vg);((TextView)v).setTextColor(c(R.color.c_text_primary));
                ((TextView)v).setBackgroundColor(c(R.color.c_bg_spinner_popup));((TextView)v).setPadding(28,20,28,20);return v;}
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinnerMatches.setAdapter(a);
        spinnerMatches.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?>p,View v,int pos,long id){selectedIndex=pos;showPreview(allMatches.get(pos));}
            @Override public void onNothingSelected(AdapterView<?>p){}
        });
        showPreview(allMatches.get(0));
        btnViewStats.setOnClickListener(v->{
            if(selectedIndex<allMatches.size()){
                Intent i=new Intent(this,StatsActivity.class);
                i.putExtra(StatsActivity.EXTRA_SAVED_FILE_NAME,allMatches.get(selectedIndex).getSavedFileName());
                startActivity(i);
            }
        });
    }

    private void showPreview(Match m){
        layoutPreview.setVisibility(View.VISIBLE);
        tvPreviewTeams.setText(m.getHomeTeamName()+" vs "+m.getAwayTeamName());
        String res=m.getResultDescription();
        tvPreviewResult.setText(res!=null&&!res.isEmpty()?"\uD83C\uDFC6 "+res:"Result unavailable");
        StringBuilder sb=new StringBuilder();
        String b1=m.getBattingFirstTeam().equals("home")?m.getHomeTeamName():m.getAwayTeamName();
        String b2=m.getBattingFirstTeam().equals("home")?m.getAwayTeamName():m.getHomeTeamName();
        if(m.getFirstInnings()!=null) sb.append(b1).append(": ").append(m.getFirstInnings().getScoreString()).append(" (").append(m.getFirstInnings().getOversString()).append(" ov)");
        if(m.getSecondInnings()!=null) sb.append("\n").append(b2).append(": ").append(m.getSecondInnings().getScoreString()).append(" (").append(m.getSecondInnings().getOversString()).append(" ov)");
        tvPreviewScores.setText(sb.toString());
        String ml=m.isSingleBatsmanMode()?"Single bat":"Two bat";
        String date=m.getSavedFileName()!=null?RecentMatchesActivity.formatDateFromFileName2(m.getSavedFileName()):"Unknown date";
        tvPreviewDate.setText(date+"  \u00b7  "+m.getMaxOvers()+" overs  \u00b7  "+ml);
    }

    private int c(int res){return getResources().getColor(res,getTheme());}
}
