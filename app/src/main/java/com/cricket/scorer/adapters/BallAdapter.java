package com.cricket.scorer.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cricket.scorer.R;
import com.cricket.scorer.models.Ball;
import java.util.List;

public class BallAdapter extends RecyclerView.Adapter<BallAdapter.BallViewHolder> {
    private static final int BALL_DP = 42, MARGIN_DP = 5;
    private List<Ball> balls;
    public BallAdapter(List<Ball> b) { this.balls = b; }
    public void updateData(List<Ball> b) { this.balls = b; notifyDataSetChanged(); }

    @NonNull @Override
    public BallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        float d = parent.getContext().getResources().getDisplayMetrics().density;
        TextView tv = new TextView(parent.getContext());
        RecyclerView.LayoutParams p = new RecyclerView.LayoutParams((int)(BALL_DP*d),(int)(BALL_DP*d));
        p.setMargins((int)(MARGIN_DP*d),(int)(MARGIN_DP*d),(int)(MARGIN_DP*d),(int)(MARGIN_DP*d));
        tv.setLayoutParams(p); tv.setGravity(Gravity.CENTER); tv.setTextSize(13f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return new BallViewHolder(tv);
    }

    @Override public void onBindViewHolder(@NonNull BallViewHolder h, int pos) {
        Ball ball = balls.get(pos); TextView tv = (TextView) h.itemView; Context ctx = tv.getContext();
        if (ball == null) {
            tv.setText("");
            tv.setBackground(oval(Color.TRANSPARENT, c(ctx, R.color.c_ball_empty_border), 3));
            return;
        }
        tv.setText(ball.getDisplayLabel());
        int[] col = colors(ctx, ball);
        tv.setBackground(oval(col[0], 0, 0));
        tv.setTextColor(col[1]);
    }

    @Override public int getItemCount() { return balls != null ? balls.size() : 0; }

    private int[] colors(Context ctx, Ball b) {
        switch (b.getType()) {
            case WIDE:    return new int[]{c(ctx,R.color.c_ball_wide_bg),   c(ctx,R.color.c_ball_wide_fg)};
            case NO_BALL: return new int[]{c(ctx,R.color.c_ball_noball_bg), c(ctx,R.color.c_ball_noball_fg)};
            case WICKET:  return new int[]{c(ctx,R.color.c_ball_wicket_bg), c(ctx,R.color.c_ball_wicket_fg)};
            default:
                int r = b.getRuns();
                if (r==0) return new int[]{c(ctx,R.color.c_ball_dot_bg),  c(ctx,R.color.c_ball_dot_fg)};
                if (r==4) return new int[]{c(ctx,R.color.c_ball_four_bg), c(ctx,R.color.c_ball_four_fg)};
                if (r==6) return new int[]{c(ctx,R.color.c_ball_six_bg),  c(ctx,R.color.c_ball_six_fg)};
                return new int[]{c(ctx,R.color.c_ball_runs_bg), c(ctx,R.color.c_ball_runs_fg)};
        }
    }

    private GradientDrawable oval(int fill, int stroke, int sw) {
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(fill);
        if (sw > 0) g.setStroke(sw, stroke); return g;
    }
    private int c(Context ctx, int res) { return ctx.getResources().getColor(res, ctx.getTheme()); }
    static class BallViewHolder extends RecyclerView.ViewHolder { BallViewHolder(@NonNull View v){super(v);} }
}
