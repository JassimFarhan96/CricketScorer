package com.cricket.scorer.adapters;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cricket.scorer.models.Ball;
import com.cricket.scorer.models.Over;

import java.util.List;

/**
 * OverHistoryAdapter.java
 * RecyclerView adapter that lists all completed overs in the over history panel.
 *
 * Each row shows:
 *   "Ov 3"   [·] [1] [W] [4] [·] [2]   12 runs
 *
 * Used in: InningsActivity → rv_over_history
 */
public class OverHistoryAdapter extends RecyclerView.Adapter<OverHistoryAdapter.OverViewHolder> {

    private List<Over> overs;

    public OverHistoryAdapter(List<Over> overs) {
        this.overs = overs;
    }

    public void updateData(List<Over> newOvers) {
        this.overs = newOvers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Build row layout programmatically
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        row.setPadding(16, 10, 16, 10);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return new OverViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull OverViewHolder holder, int position) {
        // Show overs in reverse (most recent at top)
        Over over = overs.get(overs.size() - 1 - position);
        LinearLayout row = (LinearLayout) holder.itemView;
        row.removeAllViews();

        float density = row.getContext().getResources().getDisplayMetrics().density;

        // ── Over number label ─────────────────────────────────────────────
        TextView tvOverNum = new TextView(row.getContext());
        LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(
                (int) (56 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        tvOverNum.setLayoutParams(numParams);
        tvOverNum.setText("Ov " + over.getOverNumber());
        tvOverNum.setTextSize(12f);
        tvOverNum.setTextColor(Color.parseColor("#888780"));
        row.addView(tvOverNum);

        // ── Mini ball circles ─────────────────────────────────────────────
        LinearLayout ballsContainer = new LinearLayout(row.getContext());
        ballsContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ballsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ballsContainer.setLayoutParams(ballsParams);
        ballsContainer.setGravity(Gravity.CENTER_VERTICAL);

        for (Ball ball : over.getBalls()) {
            TextView mini = makeMiniball(row, ball, density);
            ballsContainer.addView(mini);
        }
        row.addView(ballsContainer);

        // ── Total runs ────────────────────────────────────────────────────
        TextView tvRuns = new TextView(row.getContext());
        LinearLayout.LayoutParams runsParams = new LinearLayout.LayoutParams(
                (int) (40 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        runsParams.gravity = Gravity.END;
        tvRuns.setLayoutParams(runsParams);
        tvRuns.setText(String.valueOf(over.getTotalRuns()));
        tvRuns.setTextSize(12f);
        tvRuns.setTextColor(Color.parseColor("#444441"));
        tvRuns.setGravity(Gravity.END);
        row.addView(tvRuns);

        // ── Divider ───────────────────────────────────────────────────────
        row.setBackgroundColor(position % 2 == 0
                ? Color.parseColor("#FAFAF9") : Color.WHITE);
    }

    @Override
    public int getItemCount() {
        return overs != null ? overs.size() : 0;
    }

    // ─── Mini ball helper ─────────────────────────────────────────────────────

    private TextView makeMiniball(LinearLayout parent, Ball ball, float density) {
        int size = (int) (26 * density);
        int margin = (int) (3 * density);

        TextView tv = new TextView(parent.getContext());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.setMargins(margin, margin, margin, margin);
        tv.setLayoutParams(p);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(9f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setText(ball.getDisplayLabel());

        // Colors
        String bg, fg;
        switch (ball.getType()) {
            case WIDE:    bg = "#FAEEDA"; fg = "#633806"; break;
            case NO_BALL: bg = "#FAEEDA"; fg = "#633806"; break;
            case WICKET:  bg = "#E24B4A"; fg = "#FFFFFF"; break;
            case NORMAL:
            default:
                int runs = ball.getRuns();
                if (runs == 0)      { bg = "#F1EFE8"; fg = "#888780"; }
                else if (runs == 4) { bg = "#B5D4F4"; fg = "#185FA5"; }
                else if (runs == 6) { bg = "#378ADD"; fg = "#FFFFFF"; }
                else                { bg = "#E6F1FB"; fg = "#185FA5"; }
                break;
        }
        tv.setTextColor(Color.parseColor(fg));
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(bg));
        tv.setBackground(gd);
        return tv;
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class OverViewHolder extends RecyclerView.ViewHolder {
        OverViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
