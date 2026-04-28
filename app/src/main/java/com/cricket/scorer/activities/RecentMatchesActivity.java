package com.cricket.scorer.activities;
import android.content.Intent; import android.graphics.Typeface; import android.os.Bundle;
import android.view.View; import android.widget.*;
import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity;
import com.cricket.scorer.R; import com.cricket.scorer.models.Match; import com.cricket.scorer.utils.MatchStorage;
import java.util.*; import java.util.Locale;

public class RecentMatchesActivity extends AppCompatActivity {
    private static final int PAGE_SIZE=5;
    private LinearLayout containerMatches; private TextView tvPageInfo,tvEmptyState;
    private Button btnPrev,btnNext; private List<Match> allMatches; private int currentPage=1;

    @Override protected void onCreate(Bundle s){super.onCreate(s);setContentView(R.layout.activity_recent_matches);bindViews();loadAndDisplay();}
    @Override protected void onResume(){super.onResume();loadAndDisplay();}

    private void bindViews(){
        containerMatches=findViewById(R.id.container_matches);tvPageInfo=findViewById(R.id.tv_page_info);
        tvEmptyState=findViewById(R.id.tv_empty_state);btnPrev=findViewById(R.id.btn_prev);btnNext=findViewById(R.id.btn_next);
        btnPrev.setOnClickListener(v->{if(currentPage>1){currentPage--;displayPage();}});
        btnNext.setOnClickListener(v->{if(currentPage<MatchStorage.getTotalPages(allMatches,PAGE_SIZE)){currentPage++;displayPage();}});
    }

    private void loadAndDisplay(){allMatches=MatchStorage.loadAllMatches(this);currentPage=1;displayPage();}

    private void displayPage(){
        containerMatches.removeAllViews(); int tp=MatchStorage.getTotalPages(allMatches,PAGE_SIZE);
        if(allMatches.isEmpty()){tvEmptyState.setVisibility(View.VISIBLE);tvPageInfo.setVisibility(View.GONE);btnPrev.setVisibility(View.GONE);btnNext.setVisibility(View.GONE);return;}
        tvEmptyState.setVisibility(View.GONE);tvPageInfo.setVisibility(View.VISIBLE);btnPrev.setVisibility(View.VISIBLE);btnNext.setVisibility(View.VISIBLE);
        for(Match m:MatchStorage.getPage(allMatches,currentPage,PAGE_SIZE)) containerMatches.addView(buildCard(m));
        tvPageInfo.setText(String.format(Locale.US,"Page %d of %d  (%d matches total)",currentPage,tp,allMatches.size()));
        btnPrev.setEnabled(currentPage>1);btnPrev.setAlpha(currentPage>1?1f:0.4f);
        btnNext.setEnabled(currentPage<tp);btnNext.setAlpha(currentPage<tp?1f:0.4f);
    }

    private View buildCard(Match m){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0,0,0,dp(16)); card.setLayoutParams(cp);
        card.setBackgroundResource(R.drawable.bg_match_card); card.setPadding(dp(16),dp(16),dp(16),dp(16));

        TextView tvT=new TextView(this); tvT.setText(m.getHomeTeamName()+" vs "+m.getAwayTeamName());
        tvT.setTextSize(16f); tvT.setTextColor(c(R.color.green_mid)); tvT.setTypeface(null,Typeface.BOLD); card.addView(tvT);

        TextView tvF=new TextView(this); tvF.setText(m.getMaxOvers()+" overs \u00b7 "+(m.isSingleBatsmanMode()?"Single bat":"Two bat"));
        tvF.setTextSize(12f); tvF.setTextColor(c(R.color.c_text_secondary));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.setMargins(0,dp(4),0,dp(8)); tvF.setLayoutParams(fp); card.addView(tvF);
        card.addView(divider());

        String b1=m.getBattingFirstTeam().equals("home")?m.getHomeTeamName():m.getAwayTeamName();
        String b2=m.getBattingFirstTeam().equals("home")?m.getAwayTeamName():m.getHomeTeamName();
        if(m.getFirstInnings()!=null) card.addView(scoreLine(b1,m.getFirstInnings().getScoreString(),m.getFirstInnings().getOversString()));
        if(m.getSecondInnings()!=null) card.addView(scoreLine(b2,m.getSecondInnings().getScoreString(),m.getSecondInnings().getOversString()));

        if(m.getResultDescription()!=null&&!m.getResultDescription().isEmpty()){
            card.addView(divider());
            TextView tvR=new TextView(this); tvR.setText("\uD83C\uDFC6 "+m.getResultDescription());
            tvR.setTextSize(13f); tvR.setTextColor(c(R.color.green_mid)); tvR.setTypeface(null,Typeface.BOLD);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0,dp(8),0,dp(8)); tvR.setLayoutParams(rp); card.addView(tvR);
        }

        if(m.getSavedFileName()!=null){
            TextView tvD=new TextView(this); tvD.setText("Saved: "+formatDate(m.getSavedFileName()));
            tvD.setTextSize(11f); tvD.setTextColor(c(R.color.c_text_hint)); card.addView(tvD);
        }

        LinearLayout br=new LinearLayout(this); br.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        brp.setMargins(0,dp(12),0,0); br.setLayoutParams(brp);

        Button bv=new Button(this);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
        vp.setMargins(0,0,dp(6),0); bv.setLayoutParams(vp); bv.setText("View Stats");
        bv.setTextColor(android.graphics.Color.WHITE); bv.setAllCaps(false);
        bv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c(R.color.green_dark)));
        bv.setOnClickListener(v->{Intent i=new Intent(this,StatsActivity.class);i.putExtra(StatsActivity.EXTRA_SAVED_FILE_NAME,m.getSavedFileName());startActivity(i);}); br.addView(bv);

        Button bd=new Button(this);
        LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
        dp2.setMargins(dp(6),0,0,0); bd.setLayoutParams(dp2); bd.setText("Delete");
        bd.setTextColor(c(R.color.red_mid)); bd.setAllCaps(false);
        bd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c(R.color.c_ball_noball_bg)));
        bd.setOnClickListener(v->confirmDelete(m)); br.addView(bd);
        card.addView(br); return card;
    }

    private View scoreLine(String team,String score,String overs){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0,dp(6),0,0); row.setLayoutParams(rp);
        TextView tvT=new TextView(this); tvT.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        tvT.setText(team); tvT.setTextSize(13f); tvT.setTextColor(c(R.color.c_text_primary)); row.addView(tvT);
        TextView tvS=new TextView(this); tvS.setText(score+"  ("+overs+" ov)");
        tvS.setTextSize(13f); tvS.setTextColor(c(R.color.c_text_primary)); tvS.setTypeface(null,Typeface.BOLD); row.addView(tvS);
        return row;
    }

    private View divider(){
        View d=new View(this);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,1);
        p.setMargins(0,dp(8),0,dp(4)); d.setLayoutParams(p); d.setBackgroundColor(c(R.color.c_divider)); return d;
    }

    private void confirmDelete(Match m){
        new AlertDialog.Builder(this).setTitle("Delete match?")
            .setMessage(m.getHomeTeamName()+" vs "+m.getAwayTeamName()+"\nThis cannot be undone.")
            .setPositiveButton("Delete",(d,w)->{
                if(MatchStorage.deleteMatch(this,m.getSavedFileName())){Toast.makeText(this,"Match deleted",Toast.LENGTH_SHORT).show();loadAndDisplay();}
                else Toast.makeText(this,"Could not delete",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel",null).show();
    }

    public static String formatDateFromFileName2(String fn){
        try{String[]months={"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            return fn.substring(6,8)+" "+months[Integer.parseInt(fn.substring(4,6))-1]+" "+fn.substring(0,4)+", "+fn.substring(9,11)+":"+fn.substring(11,13);}
        catch(Exception e){return fn;}
    }
    private String formatDate(String fn){return formatDateFromFileName2(fn);}
    private int c(int res){return getResources().getColor(res,getTheme());}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density);}
}
