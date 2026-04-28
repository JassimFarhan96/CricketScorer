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

/**
 * OverHistoryAdapter.java
 *
 * CHANGE: Each row now shows the bowler name (if recorded) beneath
 * the over number. Layout per row:
 *
 *   Ov 3          [·][1][W][4][·][2]        12
 *   P.Kumar                                runs
 */
public class OverHistoryAdapter extends RecyclerView.Adapter<OverHistoryAdapter.OverViewHolder> {

    private List<Over> overs;

    public OverHistoryAdapter(List<Over> overs) { this.overs = overs; }

    public void updateData(List<Over> newOvers) {
        this.overs = newOvers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        row.setPadding(dp(parent.getContext(), 16), dp(parent.getContext(), 8),
                dp(parent.getContext(), 16), dp(parent.getContext(), 8));
        row.setGravity(Gravity.CENTER_VERTICAL);
        return new OverViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull OverViewHolder holder, int position) {
        // Newest over at top
        Over         over = overs.get(overs.size() - 1 - position);
        LinearLayout row  = (LinearLayout) holder.itemView;
        Context      ctx  = row.getContext();
        row.removeAllViews();

        // Alternating row background
        row.setBackgroundColor(position % 2 == 0
                ? res(ctx, R.color.c_row_alt_bg) : res(ctx, R.color.c_bg_card));

        // ── Left column: over number + bowler name ────────────────────────
        LinearLayout leftCol = new LinearLayout(ctx);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(
                dp(ctx, 72), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvOverNum = new TextView(ctx);
        tvOverNum.setText("Ov " + over.getOverNumber());
        tvOverNum.setTextSize(12f);
        tvOverNum.setTextColor(res(ctx, R.color.c_text_secondary));
        leftCol.addView(tvOverNum);

        // Bowler name (shown if recorded)
        if (over.hasBowler() && !over.getBowlerName().isEmpty()) {
            TextView tvBowler = new TextView(ctx);
            // Truncate long names to keep layout tidy
            String name = over.getBowlerName();
            if (name.length() > 10) name = name.substring(0, 9) + "…";
            tvBowler.setText(name);
            tvBowler.setTextSize(10f);
            tvBowler.setTextColor(res(ctx, R.color.c_text_hint));
            leftCol.addView(tvBowler);
        }
        row.addView(leftCol);

        // ── Mini ball circles ─────────────────────────────────────────────
        LinearLayout ballsCont = new LinearLayout(ctx);
        ballsCont.setOrientation(LinearLayout.HORIZONTAL);
        ballsCont.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ballsCont.setGravity(Gravity.CENTER_VERTICAL);
        for (Ball b : over.getBalls()) ballsCont.addView(miniball(ctx, b));
        row.addView(ballsCont);

        // ── Runs total ────────────────────────────────────────────────────
        TextView tvRuns = new TextView(ctx);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                dp(ctx, 36), LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.gravity = Gravity.END;
        tvRuns.setLayoutParams(rp);
        tvRuns.setText(String.valueOf(over.getTotalRuns()));
        tvRuns.setTextSize(12f);
        tvRuns.setTextColor(res(ctx, R.color.c_text_primary));
        tvRuns.setGravity(Gravity.END);
        row.addView(tvRuns);
    }

    @Override
    public int getItemCount() { return overs != null ? overs.size() : 0; }

    // ─── Mini ball helper ─────────────────────────────────────────────────────

    private TextView miniball(Context ctx, Ball ball) {
        int size = dp(ctx, 24);
        int marg = dp(ctx, 3);
        TextView tv = new TextView(ctx);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(marg, marg, marg, marg);
        tv.setLayoutParams(p);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(9f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setText(ball.getDisplayLabel());
        int[] colors = miniColors(ctx, ball);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(colors[0]);
        tv.setBackground(gd);
        tv.setTextColor(colors[1]);
        return tv;
    }

    private int[] miniColors(Context ctx, Ball ball) {
        switch (ball.getType()) {
            case WIDE: case NO_BALL:
                return new int[]{res(ctx, R.color.c_ball_wide_bg),   res(ctx, R.color.c_ball_wide_fg)};
            case WICKET:
                return new int[]{res(ctx, R.color.c_ball_wicket_bg), res(ctx, R.color.c_ball_wicket_fg)};
            default:
                int r = ball.getRuns();
                if (r == 4) return new int[]{res(ctx, R.color.c_ball_four_bg), res(ctx, R.color.c_ball_four_fg)};
                if (r == 6) return new int[]{res(ctx, R.color.c_ball_six_bg),  res(ctx, R.color.c_ball_six_fg)};
                return new int[]{res(ctx, R.color.c_ball_dot_bg), res(ctx, R.color.c_ball_dot_fg)};
        }
    }

    private int res(Context ctx, int colorRes) {
        return ctx.getResources().getColor(colorRes, ctx.getTheme());
    }

    private int dp(Context ctx, int val) {
        return (int) (val * ctx.getResources().getDisplayMetrics().density);
    }

    static class OverViewHolder extends RecyclerView.ViewHolder {
        OverViewHolder(@NonNull View v) { super(v); }
    }
}
