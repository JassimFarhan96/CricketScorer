package com.cricket.scorer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cricket.scorer.R;
import com.cricket.scorer.adapters.BallAdapter;
import com.cricket.scorer.adapters.OverHistoryAdapter;
import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Innings;
import com.cricket.scorer.models.Match;
import com.cricket.scorer.models.Over;
import com.cricket.scorer.models.Player;
import com.cricket.scorer.utils.LiveMatchState;
import com.cricket.scorer.utils.MatchEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InningsActivity extends AppCompatActivity {
    private CricketApp app; private Match match; private MatchEngine engine;
    private TextView tvInningsTitle,tvScore,tvOversInfo,tvCRR,tvRRR,tvModeBadge;
    private LinearLayout layoutTargetBanner;
    private TextView tvTargetInfo,tvRequiredBalls;
    private TableLayout tableBatsmen;
    private TextView tvCurrentOverLabel,tvBallsRemaining;
    private RecyclerView rvCurrentOverBalls,rvOverHistory;
    private Button btnDot,btn1,btn2,btn3,btn4,btn6,btnWide,btnNoBall,btnWicket,btnUndo;
    private BallAdapter ballAdapter; private OverHistoryAdapter overHistoryAdapter;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s); setContentView(R.layout.activity_innings);
        app = (CricketApp)getApplication(); match = app.getCurrentMatch(); engine = app.getMatchEngine();
        if (match == null) { finish(); return; }
        bindViews(); setupAdapters(); setClickListeners();
        setBallButtonsEnabled(false); refreshUI();
        if (isInningsJustStarted()) showOpenerSelectionDialog();
        else setBallButtonsEnabled(true);
    }

    @Override protected void onPause() {
        super.onPause();
        if (match != null && !match.isMatchCompleted()) LiveMatchState.persist(this, match);
    }

    private boolean isInningsJustStarted() {
        Innings inn = match.getCurrentInningsData();
        Over cur = inn.getCurrentOver();
        return (cur == null || cur.getBalls().isEmpty()) && inn.getCompletedOvers().isEmpty();
    }

    private void resetAndReshowOpeners() {
        Innings inn = match.getCurrentInningsData(); List<Player> pl = match.getCurrentBattingPlayers();
        boolean single = match.isSingleBatsmanMode();
        for (Player p : pl) { p.setHasNotBatted(true); p.setOut(false); }
        inn.setStrikerIndex(0); inn.setNonStrikerIndex(single ? -1 : 1);
        inn.setNextBatsmanIndex(single ? 1 : 2);
        LiveMatchState.persist(this, match); setBallButtonsEnabled(false); refreshUI();
        showOpenerSelectionDialog();
    }

    private void showOpenerSelectionDialog() {
        List<Player> pl = match.getCurrentBattingPlayers(); Innings inn = match.getCurrentInningsData();
        boolean single = match.isSingleBatsmanMode();
        String[] names = new String[pl.size()];
        for (int i=0;i<pl.size();i++) names[i]=(i+1)+". "+pl.get(i).getName();
        final int[] sc = {0};
        new AlertDialog.Builder(this).setTitle("Select opening striker").setCancelable(false)
            .setSingleChoiceItems(names,0,(d,w)->sc[0]=w)
            .setPositiveButton("Confirm",(d,w)->{
                int si=sc[0]; inn.setStrikerIndex(si); pl.get(si).setHasNotBatted(false);
                if (single) { inn.setNonStrikerIndex(-1); inn.setNextBatsmanIndex(nextAvail(pl,si,-1));
                    LiveMatchState.persist(this,match); setBallButtonsEnabled(true); refreshUI();
                } else showNonStrikerDialog(si);
            }).show();
    }

    private void showNonStrikerDialog(int si) {
        List<Player> pl = match.getCurrentBattingPlayers(); Innings inn = match.getCurrentInningsData();
        List<Integer> ci = new ArrayList<>(); List<String> cn = new ArrayList<>();
        for (int i=0;i<pl.size();i++) if(i!=si){ci.add(i);cn.add((i+1)+". "+pl.get(i).getName());}
        final int[] nc = {0};
        new AlertDialog.Builder(this).setTitle("Select non-striker").setCancelable(false)
            .setSingleChoiceItems(cn.toArray(new String[0]),0,(d,w)->nc[0]=w)
            .setPositiveButton("Confirm",(d,w)->{
                int ni=ci.get(nc[0]); inn.setNonStrikerIndex(ni); pl.get(ni).setHasNotBatted(false);
                inn.setNextBatsmanIndex(nextAvail(pl,si,ni));
                LiveMatchState.persist(this,match); setBallButtonsEnabled(true); refreshUI();
            }).show();
    }

    private int nextAvail(List<Player> pl, int si, int ni) {
        for (int i=0;i<pl.size();i++) if(i!=si&&i!=ni) return i; return pl.size();
    }

    private void setBallButtonsEnabled(boolean e) {
        float a = e?1f:0.35f;
        btnDot.setEnabled(e);btnDot.setAlpha(a);btn1.setEnabled(e);btn1.setAlpha(a);
        btn2.setEnabled(e);btn2.setAlpha(a);btn3.setEnabled(e);btn3.setAlpha(a);
        btn4.setEnabled(e);btn4.setAlpha(a);btn6.setEnabled(e);btn6.setAlpha(a);
        btnWide.setEnabled(e);btnWide.setAlpha(a);btnNoBall.setEnabled(e);btnNoBall.setAlpha(a);
        btnWicket.setEnabled(e);btnWicket.setAlpha(a);
        btnUndo.setEnabled(true); btnUndo.setAlpha(1f);
    }

    private void bindViews() {
        tvInningsTitle=findViewById(R.id.tv_innings_title); tvScore=findViewById(R.id.tv_score);
        tvOversInfo=findViewById(R.id.tv_overs_info); tvCRR=findViewById(R.id.tv_crr);
        tvRRR=findViewById(R.id.tv_rrr); tvModeBadge=findViewById(R.id.tv_mode_badge);
        layoutTargetBanner=findViewById(R.id.layout_target_banner);
        tvTargetInfo=findViewById(R.id.tv_target_info); tvRequiredBalls=findViewById(R.id.tv_required_balls);
        tableBatsmen=findViewById(R.id.table_batsmen);
        tvCurrentOverLabel=findViewById(R.id.tv_current_over_label); tvBallsRemaining=findViewById(R.id.tv_balls_remaining);
        rvCurrentOverBalls=findViewById(R.id.rv_current_over_balls); rvOverHistory=findViewById(R.id.rv_over_history);
        btnDot=findViewById(R.id.btn_dot);btn1=findViewById(R.id.btn_1);btn2=findViewById(R.id.btn_2);
        btn3=findViewById(R.id.btn_3);btn4=findViewById(R.id.btn_4);btn6=findViewById(R.id.btn_6);
        btnWide=findViewById(R.id.btn_wide);btnNoBall=findViewById(R.id.btn_noball);
        btnWicket=findViewById(R.id.btn_wicket);btnUndo=findViewById(R.id.btn_undo);
    }

    private void setupAdapters() {
        ballAdapter=new BallAdapter(new ArrayList<>());
        rvCurrentOverBalls.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false));
        rvCurrentOverBalls.setAdapter(ballAdapter);
        overHistoryAdapter=new OverHistoryAdapter(new ArrayList<>());
        rvOverHistory.setLayoutManager(new LinearLayoutManager(this));
        rvOverHistory.setAdapter(overHistoryAdapter);
    }

    private void setClickListeners() {
        btnDot.setOnClickListener(v->handleBall(0)); btn1.setOnClickListener(v->handleBall(1));
        btn2.setOnClickListener(v->handleBall(2)); btn3.setOnClickListener(v->handleBall(3));
        btn4.setOnClickListener(v->handleBall(4)); btn6.setOnClickListener(v->handleBall(6));
        btnWide.setOnClickListener(v->handleMatchState(engine.deliverWide()));
        btnNoBall.setOnClickListener(v->handleMatchState(engine.deliverNoBall()));
        btnWicket.setOnClickListener(v->showWicketDialog());
        btnUndo.setOnClickListener(v->{
            if (isInningsJustStarted()) resetAndReshowOpeners();
            else { if(!engine.undoLastBall()) Toast.makeText(this,"Nothing to undo",Toast.LENGTH_SHORT).show();
                   else { LiveMatchState.persist(this,match); refreshUI(); } }
        });
    }

    private void handleBall(int r) { handleMatchState(engine.deliverNormalBall(r)); }

    private void showWicketDialog() {
        List<Player> av=engine.getAvailableBatsmen();
        if(av.isEmpty()){handleMatchState(engine.deliverWicket(engine.getNextBatsmanIndex()));return;}
        String[] names=new String[av.size()]; final List<Player> bt=match.getCurrentBattingPlayers();
        for(int i=0;i<av.size();i++) names[i]=(i+1)+". "+av.get(i).getName();
        final int[] ch={0};
        new AlertDialog.Builder(this).setTitle("Wicket — choose next batsman")
            .setSingleChoiceItems(names,0,(d,w)->ch[0]=w)
            .setPositiveButton("Confirm",(d,w)->handleMatchState(engine.deliverWicket(bt.indexOf(av.get(ch[0])))))
            .setNegativeButton("Cancel",null).show();
    }

    private void handleMatchState(MatchEngine.MatchState state) {
        switch(state) {
            case BALL_RECORDED: LiveMatchState.persist(this,match); refreshUI(); break;
            case OVER_COMPLETE: LiveMatchState.persist(this,match); refreshUI();
                Toast.makeText(this,"Over "+match.getCurrentInningsData().getCompletedOvers().size()+" complete!",Toast.LENGTH_SHORT).show(); break;
            case INNINGS_COMPLETE: LiveMatchState.persist(this,match);
                startActivity(new Intent(this,InningsBreakActivity.class)); finish(); break;
            case MATCH_COMPLETE: LiveMatchState.clear(this);
                startActivity(new Intent(this,StatsActivity.class)); finish(); break;
        }
    }

    private void refreshUI() {
        Innings inn=match.getCurrentInningsData(); int inum=match.getCurrentInnings(); boolean single=match.isSingleBatsmanMode();
        tvInningsTitle.setText(inum==1?"1st Innings":"2nd Innings");
        tvScore.setText(inn.getScoreString());
        tvOversInfo.setText(String.format(Locale.US,"Ov %s / %d",inn.getOversString(),match.getMaxOvers()));
        tvCRR.setText(String.format(Locale.US,"CRR: %.2f",inn.getCurrentRunRate()));
        tvModeBadge.setText(single?"Single bat":"Two bat");
        tvModeBadge.setTextColor(c(R.color.c_mode_badge_fg)); tvModeBadge.setVisibility(View.VISIBLE);
        if(inum==2){
            int t=match.getTarget(); layoutTargetBanner.setVisibility(View.VISIBLE); tvRRR.setVisibility(View.VISIBLE);
            tvTargetInfo.setText("Target: "+t);
            tvRequiredBalls.setText("Need "+inn.getRunsNeeded(t)+" off "+inn.getBallsRemaining(match.getMaxOvers())+" balls");
            tvRRR.setText(String.format(Locale.US,"RRR: %.2f",inn.getRequiredRunRate(t,match.getMaxOvers())));
        } else { layoutTargetBanner.setVisibility(View.GONE); tvRRR.setVisibility(View.GONE); }
        refreshBattingTable(inn,single); refreshCurrentOver(inn);
        overHistoryAdapter.updateData(inn.getCompletedOvers());
    }

    private void refreshBattingTable(Innings inn, boolean single) {
        tableBatsmen.removeAllViews();
        addRow(new String[]{"Batsman","R","B","4s","6s","SR"},true,false,false);
        List<Player> pl=match.getCurrentBattingPlayers();
        int si=inn.getStrikerIndex(), nsi=inn.getNonStrikerIndex();
        for(int i=0;i<pl.size();i++){
            Player p=pl.get(i);
            boolean isSt=(i==si)&&!p.isOut(), isNs=!single&&(i==nsi)&&!p.isOut();
            boolean atC=isSt||isNs;
            if(p.isHasNotBatted()&&!atC) continue;
            String name=isSt?"⚡ "+p.getName():isNs?"  "+p.getName():p.getName();
            String sr=p.getBallsFaced()>0?String.format(Locale.US,"%.1f",p.getStrikeRate()):"-";
            addRow(new String[]{name,String.valueOf(p.getRunsScored()),String.valueOf(p.getBallsFaced()),
                String.valueOf(p.getFours()),String.valueOf(p.getSixes()),sr},false,atC,p.isOut());
        }
    }

    private void addRow(String[] cells, boolean hdr, boolean active, boolean out) {
        TableRow row=new TableRow(this);
        row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,TableRow.LayoutParams.WRAP_CONTENT));
        if(active) row.setBackgroundColor(c(R.color.c_row_active_bg));
        else if(hdr) row.setBackgroundColor(c(R.color.c_row_header_bg));
        int[] w={3,1,1,1,1,1};
        for(int i=0;i<cells.length;i++){
            TextView tv=new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(0,TableRow.LayoutParams.WRAP_CONTENT,w[i]));
            tv.setText(cells[i]); tv.setPadding(12,10,12,10); tv.setTextSize(12f);
            if(hdr){tv.setTextColor(c(R.color.c_row_header_text));tv.setTypeface(null,android.graphics.Typeface.BOLD);}
            else if(out){tv.setTextColor(c(R.color.c_text_disabled));if(i==0)tv.setPaintFlags(tv.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);}
            else tv.setTextColor(c(R.color.c_text_primary));
            row.addView(tv);
        }
        tableBatsmen.addView(row);
    }

    private void refreshCurrentOver(Innings inn) {
        Over cur=inn.getCurrentOver(); int cnt=inn.getCompletedOvers().size();
        tvCurrentOverLabel.setText("Over "+(cnt+1));
        int valid=cur!=null?cur.getValidBallCount():0; int rem=6-valid;
        tvBallsRemaining.setText(rem+(rem==1?" ball left":" balls left"));
        List<Ball> db=new ArrayList<>();
        if(cur!=null) db.addAll(cur.getBalls());
        for(int i=0;i<Math.max(0,6-valid);i++) db.add(null);
        ballAdapter.updateData(db);
    }

    private int c(int res) { return getResources().getColor(res, getTheme()); }
}
