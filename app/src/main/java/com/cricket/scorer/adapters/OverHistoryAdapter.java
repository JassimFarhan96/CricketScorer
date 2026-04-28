package com.cricket.scorer.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cricket.scorer.R;
import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Over;
import java.util.List;

public class OverHistoryAdapter extends RecyclerView.Adapter<OverHistoryAdapter.OverViewHolder> {
    private List<Over> overs;
    public OverHistoryAdapter(List<Over> o) { this.overs = o; }
    public void updateData(List<Over> o) { this.overs = o; notifyDataSetChanged(); }

    @NonNull @Override
    public OverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        Context ctx = parent.getContext(); float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        row.setPadding(dp(ctx,16),dp(ctx,10),dp(ctx,16),dp(ctx,10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        return new OverViewHolder(row);
    }

    @Override public void onBindViewHolder(@NonNull OverViewHolder h, int pos) {
        Over over = overs.get(overs.size()-1-pos); LinearLayout row = (LinearLayout) h.itemView; Context ctx = row.getContext();
        row.removeAllViews();
        row.setBackgroundColor(pos%2==0 ? c(ctx,R.color.c_row_alt_bg) : c(ctx,R.color.c_bg_card));

        TextView tvNum = new TextView(ctx);
        tvNum.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx,52), LinearLayout.LayoutParams.WRAP_CONTENT));
        tvNum.setText("Ov " + over.getOverNumber()); tvNum.setTextSize(12f); tvNum.setTextColor(c(ctx,R.color.c_text_secondary));
        row.addView(tvNum);

        LinearLayout bc = new LinearLayout(ctx); bc.setOrientation(LinearLayout.HORIZONTAL);
        bc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bc.setGravity(Gravity.CENTER_VERTICAL);
        for (Ball b : over.getBalls()) bc.addView(miniball(ctx, b));
        row.addView(bc);

        TextView tvR = new TextView(ctx);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(ctx,36), LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.gravity = Gravity.END; tvR.setLayoutParams(rp);
        tvR.setText(String.valueOf(over.getTotalRuns())); tvR.setTextSize(12f);
        tvR.setTextColor(c(ctx,R.color.c_text_primary)); tvR.setGravity(Gravity.END);
        row.addView(tvR);
    }

    @Override public int getItemCount() { return overs != null ? overs.size() : 0; }

    private TextView miniball(Context ctx, Ball b) {
        int sz = dp(ctx,26), mg = dp(ctx,3);
        TextView tv = new TextView(ctx);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(sz,sz); p.setMargins(mg,mg,mg,mg);
        tv.setLayoutParams(p); tv.setGravity(Gravity.CENTER); tv.setTextSize(9f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD); tv.setText(b.getDisplayLabel());
        int[] col = miniColors(ctx, b);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(col[0]);
        tv.setBackground(g); tv.setTextColor(col[1]); return tv;
    }

    private int[] miniColors(Context ctx, Ball b) {
        switch(b.getType()) {
            case WIDE: case NO_BALL: return new int[]{c(ctx,R.color.c_ball_wide_bg), c(ctx,R.color.c_ball_wide_fg)};
            case WICKET: return new int[]{c(ctx,R.color.c_ball_wicket_bg), c(ctx,R.color.c_ball_wicket_fg)};
            default:
                int r = b.getRuns();
                if (r==4) return new int[]{c(ctx,R.color.c_ball_four_bg), c(ctx,R.color.c_ball_four_fg)};
                if (r==6) return new int[]{c(ctx,R.color.c_ball_six_bg),  c(ctx,R.color.c_ball_six_fg)};
                return new int[]{c(ctx,R.color.c_ball_dot_bg), c(ctx,R.color.c_ball_dot_fg)};
        }
    }

    private int c(Context ctx, int res) { return ctx.getResources().getColor(res, ctx.getTheme()); }
    private int dp(Context ctx, int v) { return (int)(v * ctx.getResources().getDisplayMetrics().density); }
    static class OverViewHolder extends RecyclerView.ViewHolder { OverViewHolder(@NonNull View v){super(v);} }
}
