package com.cricket.scorer.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
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
 * FIX: Ball circles are now inside a HorizontalScrollView so the user
 * can scroll left/right to see all deliveries in an over, including
 * overs with extras (7+ balls).
 *
 * Row layout per over:
 *
 *   ┌─────────────────────────────────────────────────────────┐
 *   │ Ov 4  [HorizontalScrollView: ●●●●●●]              12   │
 *   │       Gg  (bowler, green italic)                        │
 *   └─────────────────────────────────────────────────────────┘
 *
 * Structure:
 *   card (vertical LinearLayout)
 *   └── row1 (horizontal): overNum | HScrollView[balls] | runs
 *   └── row2 (horizontal): spacer  | bowlerName
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
        LinearLayout card = new LinearLayout(parent.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        return new OverViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull OverViewHolder holder, int position) {
        Over         over = overs.get(overs.size() - 1 - position); // newest first
        LinearLayout card = (LinearLayout) holder.itemView;
        Context      ctx  = card.getContext();
        card.removeAllViews();

        // Alternating background
        card.setBackgroundColor(position % 2 == 0
                ? res(ctx, R.color.c_row_alt_bg)
                : res(ctx, R.color.c_bg_card));
        card.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 4));

        // ── Row 1: over label | scrollable balls | runs ───────────────────
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Over number — fixed width so balls always start at same indent
        TextView tvOv = new TextView(ctx);
        tvOv.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT));
        tvOv.setText("Ov " + over.getOverNumber());
        tvOv.setTextSize(11.5f);
        tvOv.setTypeface(null, Typeface.BOLD);
        tvOv.setTextColor(res(ctx, R.color.c_text_primary));
        row1.addView(tvOv);

        // HorizontalScrollView wrapping the ball circles
        // weight=1 so it takes all space between over label and runs total
        HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
        LinearLayout.LayoutParams hsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        hScroll.setLayoutParams(hsParams);
        hScroll.setHorizontalScrollBarEnabled(false); // no visible scrollbar, swipe to scroll
        hScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScroll.setFillViewport(false);

        // Inner container for balls — horizontal, not constrained in width
        LinearLayout ballsContainer = new LinearLayout(ctx);
        ballsContainer.setOrientation(LinearLayout.HORIZONTAL);
        ballsContainer.setGravity(Gravity.CENTER_VERTICAL);
        ballsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        for (Ball b : over.getBalls()) {
            ballsContainer.addView(makeBallCircle(ctx, b));
        }

        hScroll.addView(ballsContainer);
        row1.addView(hScroll);

        // Runs total — fixed width, right-aligned
        TextView tvRuns = new TextView(ctx);
        LinearLayout.LayoutParams runsParams = new LinearLayout.LayoutParams(
                dp(ctx, 30), LinearLayout.LayoutParams.WRAP_CONTENT);
        runsParams.gravity = Gravity.END;
        tvRuns.setLayoutParams(runsParams);
        tvRuns.setText(String.valueOf(over.getTotalRuns()));
        tvRuns.setTextSize(12f);
        tvRuns.setTypeface(null, Typeface.BOLD);
        tvRuns.setTextColor(res(ctx, R.color.c_text_primary));
        tvRuns.setGravity(Gravity.END);
        row1.addView(tvRuns);

        card.addView(row1);

        // ── Row 2: bowler name below balls ────────────────────────────────
        if (over.hasBowler() && !over.getBowlerName().isEmpty()) {
            LinearLayout row2 = new LinearLayout(ctx);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            r2p.setMargins(0, dp(ctx, 1), 0, dp(ctx, 2));
            row2.setLayoutParams(r2p);

            // Spacer to align bowler name under balls (match over label width)
            View spacer = new View(ctx);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 40), 1));
            row2.addView(spacer);

            TextView tvBowler = new TextView(ctx);
            tvBowler.setText(over.getBowlerName());
            tvBowler.setTextSize(10.5f);
            tvBowler.setTextColor(res(ctx, R.color.green_mid));
            tvBowler.setTypeface(null, Typeface.ITALIC);
            row2.addView(tvBowler);

            card.addView(row2);
        }
    }

    @Override
    public int getItemCount() { return overs != null ? overs.size() : 0; }

    // ─── Ball circle ──────────────────────────────────────────────────────────

    private View makeBallCircle(Context ctx, Ball ball) {
        int size = dp(ctx, 28);
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
