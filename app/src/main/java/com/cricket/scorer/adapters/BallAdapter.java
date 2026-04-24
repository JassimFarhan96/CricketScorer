package com.cricket.scorer.adapters;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cricket.scorer.models.Ball;

import java.util.List;

/**
 * BallAdapter.java
 * RecyclerView adapter that displays ball-by-ball circles for the current over.
 *
 * Each ball is shown as a circular TextView with:
 *  - Color coded by ball type (dot, runs, wide, no-ball, wicket, empty)
 *  - Label showing the outcome ("·", "1"–"6", "Wd", "NB", "W")
 *  - null entry in the list = empty placeholder slot
 *
 * Used in: InningsActivity → rv_current_over_balls
 */
public class BallAdapter extends RecyclerView.Adapter<BallAdapter.BallViewHolder> {

    // Ball circle size in dp (converted to px at runtime)
    private static final int BALL_SIZE_DP = 42;
    private static final int BALL_MARGIN_DP = 5;

    private List<Ball> balls; // null entry = empty slot

    public BallAdapter(List<Ball> balls) {
        this.balls = balls;
    }

    public void updateData(List<Ball> newBalls) {
        this.balls = newBalls;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create the ball circle programmatically (a square TextView with rounded corners)
        TextView tv = new TextView(parent.getContext());

        float density = parent.getContext().getResources().getDisplayMetrics().density;
        int sizePx = (int) (BALL_SIZE_DP * density);
        int marginPx = (int) (BALL_MARGIN_DP * density);

        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(sizePx, sizePx);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(13f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        return new BallViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull BallViewHolder holder, int position) {
        Ball ball = balls.get(position);
        TextView tv = (TextView) holder.itemView;

        if (ball == null) {
            // ── Empty slot (dashed border, transparent background) ────────
            tv.setText("");
            tv.setBackgroundResource(android.R.drawable.btn_default); // replaced by drawable below
            applyEmptyStyle(tv);
        } else {
            // ── Ball with outcome ─────────────────────────────────────────
            tv.setText(ball.getDisplayLabel());
            applyBallStyle(tv, ball);
        }
    }

    @Override
    public int getItemCount() {
        return balls != null ? balls.size() : 0;
    }

    // ─── Styling helpers ──────────────────────────────────────────────────────

    /**
     * Applies color and text based on ball type/runs.
     * Colors mirror the web app's design system.
     */
    private void applyBallStyle(TextView tv, Ball ball) {
        String bgColor, textColor;

        switch (ball.getType()) {
            case WIDE:
                bgColor = "#FAEEDA"; textColor = "#633806"; break;
            case NO_BALL:
                bgColor = "#FCEBEB"; textColor = "#E24B4A"; break;
            case WICKET:
                bgColor = "#E24B4A"; textColor = "#FFFFFF"; break;
            case NORMAL:
            default:
                int runs = ball.getRuns();
                if (runs == 0) {
                    bgColor = "#F1EFE8"; textColor = "#888780";
                } else if (runs == 4) {
                    bgColor = "#B5D4F4"; textColor = "#185FA5";
                } else if (runs == 6) {
                    bgColor = "#378ADD"; textColor = "#FFFFFF";
                } else {
                    bgColor = "#E6F1FB"; textColor = "#185FA5";
                }
                break;
        }

        tv.setBackgroundDrawable(makeCircleDrawable(bgColor, null));
        tv.setTextColor(Color.parseColor(textColor));
    }

    private void applyEmptyStyle(TextView tv) {
        tv.setBackgroundDrawable(makeCircleDrawable(null, "#CCCCCC"));
        tv.setTextColor(Color.TRANSPARENT);
    }

    /**
     * Creates a circular shape drawable programmatically.
     * fillColor = null means transparent fill (for empty slots).
     * strokeColor = null means no border.
     */
    private android.graphics.drawable.GradientDrawable makeCircleDrawable(
            String fillColor, String strokeColor) {
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (fillColor != null) {
            gd.setColor(Color.parseColor(fillColor));
        } else {
            gd.setColor(Color.TRANSPARENT);
        }
        if (strokeColor != null) {
            gd.setStroke(3, Color.parseColor(strokeColor));
            // Dashed stroke for empty slots
            gd.setStroke(3, Color.parseColor(strokeColor));
        }
        return gd;
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class BallViewHolder extends RecyclerView.ViewHolder {
        BallViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
