package com.cricket.scorer.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
 * Each completed over row displays:
 *
 *   ┌─────────────────────────────────────────────────────┐
 *   │  Ov 1       [·][6][1][4][·][2]               14    │
 *   │  Bb (bowler)                                  runs  │
 *   └─────────────────────────────────────────────────────┘
 *
 * Layout per ViewHolder:
 *   - Vertical LinearLayout (the card)
 *     └── Row 1 (horizontal): over label | ball circles | runs total
 *     └── Row 2 (horizontal): bowler name (if available)
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
        // Root: vertical card per over
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        return new OverViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull OverViewHolder holder, int position) {
        // Newest over at top
        Over         over = overs.get(overs.size() - 1 - position);
        LinearLayout card = (LinearLayout) holder.itemView;
        Context      ctx  = card.getContext();
        card.removeAllViews();

        // Alternating background
        card.setBackgroundColor(position % 2 == 0
                ? res(ctx, R.color.c_row_alt_bg)
                : res(ctx, R.color.c_bg_card));
        card.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 4));

        // ── Row 1: over number | ball circles | runs ──────────────────────
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Over number label
        TextView tvOverNum = new TextView(ctx);
        tvOverNum.setLayoutParams(new LinearLayout.LayoutParams(
                dp(ctx, 44), LinearLayout.LayoutParams.WRAP_CONTENT));
        tvOverNum.setText("Ov " + over.getOverNumber());
        tvOverNum.setTextSize(12f);
        tvOverNum.setTypeface(null, Typeface.BOLD);
        tvOverNum.setTextColor(res(ctx, R.color.c_text_primary));
        row1.addView(tvOverNum);

        // Ball circles container
        LinearLayout ballsRow = new LinearLayout(ctx);
        ballsRow.setOrientation(LinearLayout.HORIZONTAL);
        ballsRow.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ballsRow.setGravity(Gravity.CENTER_VERTICAL);

        for (Ball b : over.getBalls()) {
            ballsRow.addView(makeBallCircle(ctx, b));
        }
        // If fewer than 6 valid balls (e.g. all out mid-over), add empty slots
        int validCount = over.getValidBallCount();
        // No empty slots for completed overs — show what was actually bowled
        row1.addView(ballsRow);

        // Runs total
        TextView tvRuns = new TextView(ctx);
        LinearLayout.LayoutParams runsParams = new LinearLayout.LayoutParams(
                dp(ctx, 32), LinearLayout.LayoutParams.WRAP_CONTENT);
        runsParams.gravity = Gravity.END;
        tvRuns.setLayoutParams(runsParams);
        tvRuns.setText(String.valueOf(over.getTotalRuns()));
        tvRuns.setTextSize(13f);
        tvRuns.setTypeface(null, Typeface.BOLD);
        tvRuns.setTextColor(res(ctx, R.color.c_text_primary));
        tvRuns.setGravity(Gravity.END);
        row1.addView(tvRuns);

        card.addView(row1);

        // ── Row 2: bowler name (below ball row) ───────────────────────────
        if (over.hasBowler() && !over.getBowlerName().isEmpty()) {
            LinearLayout row2 = new LinearLayout(ctx);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams row2Params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            row2Params.setMargins(0, dp(ctx, 2), 0, dp(ctx, 2));
            row2.setLayoutParams(row2Params);

            // Indent to align with ball circles (match over label width)
            View spacer = new View(ctx);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 44), 1));
            row2.addView(spacer);

            TextView tvBowler = new TextView(ctx);
            tvBowler.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvBowler.setText(over.getBowlerName());
            tvBowler.setTextSize(11f);
            tvBowler.setTextColor(res(ctx, R.color.green_mid));
            tvBowler.setTypeface(null, Typeface.ITALIC);
            row2.addView(tvBowler);

            card.addView(row2);
        }
    }

    @Override
    public int getItemCount() { return overs != null ? overs.size() : 0; }

    // ─── Ball circle builder ──────────────────────────────────────────────────

    private View makeBallCircle(Context ctx, Ball ball) {
        int size = dp(ctx, 26);
        int marg = dp(ctx, 2);

        TextView tv = new TextView(ctx);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(marg, marg, marg, marg);
        tv.setLayoutParams(p);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(9.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setText(ball.getDisplayLabel());

        int[] colors = ballColors(ctx, ball);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(colors[0]);
        tv.setBackground(gd);
        tv.setTextColor(colors[1]);
        return tv;
    }

    private int[] ballColors(Context ctx, Ball ball) {
        switch (ball.getType()) {
            case WIDE:
                return new int[]{res(ctx, R.color.c_ball_wide_bg),   res(ctx, R.color.c_ball_wide_fg)};
            case NO_BALL:
                return new int[]{res(ctx, R.color.c_ball_noball_bg), res(ctx, R.color.c_ball_noball_fg)};
            case WICKET:
                return new int[]{res(ctx, R.color.c_ball_wicket_bg), res(ctx, R.color.c_ball_wicket_fg)};
            default:
                int r = ball.getRuns();
                if (r == 0) return new int[]{res(ctx, R.color.c_ball_dot_bg),  res(ctx, R.color.c_ball_dot_fg)};
                if (r == 4) return new int[]{res(ctx, R.color.c_ball_four_bg), res(ctx, R.color.c_ball_four_fg)};
                if (r == 6) return new int[]{res(ctx, R.color.c_ball_six_bg),  res(ctx, R.color.c_ball_six_fg)};
                return new int[]{res(ctx, R.color.c_ball_runs_bg),  res(ctx, R.color.c_ball_runs_fg)};
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
