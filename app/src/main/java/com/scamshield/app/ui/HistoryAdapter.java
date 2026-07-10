package com.scamshield.app.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.scamshield.app.R;
import com.scamshield.app.engine.DetectionResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HistoryAdapter
 * Package: com.scamshield.app.ui
 *
 * RecyclerView adapter that displays the list of past DetectionResult entries
 * on the HomeActivity dashboard (the "Recent alerts" section).
 *
 * ── Design rules (elderly-friendly) ──────────────────────────────────────────
 *   • Each item is at least 72dp tall (large touch target)
 *   • Text is at least 16sp
 *   • Verdict pill is colour-coded: RED = SCAM, AMBER = SUSPICIOUS, GREEN = SAFE
 *   • Layout defined in res/layout/item_history.xml
 *
 * ── Data flow ─────────────────────────────────────────────────────────────────
 *   HomeActivity calls LocalDataStore.getInstance().getHistory() → passes the
 *   list to this adapter → RecyclerView renders one row per DetectionResult.
 *
 * ── Live updates ──────────────────────────────────────────────────────────────
 *   ScamAlertManager calls notifyHistoryChanged() (static helper) after saving
 *   a new detection, which triggers HomeActivity to refresh the list.
 *   This uses a simple interface callback to avoid a direct Activity reference
 *   (which would leak memory if the Activity is closed).
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    /** Filter options for the history list. */
    public enum Filter { ALL, SCAM, SUSPICIOUS, SAFE }

    /** Callback interface — HomeActivity implements this to refresh the list. */
    public interface HistoryRefreshListener {
        void onHistoryChanged();
    }

    // ── Static listener registered by HomeActivity ─────────────────────────────
    // Using a static reference is acceptable here because HomeActivity clears it
    // in onPause()/onDestroy() to prevent leaks.
    private static HistoryRefreshListener refreshListener;

    /** Register a listener. Call from HomeActivity.onResume(). */
    public static void setRefreshListener(HistoryRefreshListener listener) {
        refreshListener = listener;
    }

    /** Clear the listener. Call from HomeActivity.onPause(). */
    public static void clearRefreshListener() {
        refreshListener = null;
    }

    /**
     * Triggers a history refresh if HomeActivity is in the foreground.
     * Called by ScamAlertManager after logDetection() completes.
     */
    public static void notifyHistoryChanged() {
        if (refreshListener != null) {
            refreshListener.onHistoryChanged();
        }
    }

    // ── Adapter data ──────────────────────────────────────────────────────────
    /** Full unfiltered list — always kept intact so switching filter never loses data. */
    private List<DetectionResult> allItems;
    /** Currently displayed (filtered) list. */
    private List<DetectionResult> items;
    /** Active filter — defaults to ALL. */
    private Filter currentFilter = Filter.ALL;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    public HistoryAdapter(List<DetectionResult> items) {
        this.allItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.items    = this.allItems;
    }

    /** Replace the full list, applying the current filter, and redraw. */
    public void setItems(List<DetectionResult> newItems) {
        this.allItems = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        applyFilter();
    }

    /**
     * Set the active filter and redraw the list.
     * @param filter One of Filter.ALL, Filter.SCAM, Filter.SUSPICIOUS, Filter.SAFE
     */
    public void setFilter(Filter filter) {
        this.currentFilter = filter;
        applyFilter();
    }

    /** Returns the current active filter. */
    public Filter getFilter() {
        return currentFilter;
    }

    /** Rebuilds the displayed items list from allItems according to currentFilter. */
    private void applyFilter() {
        if (currentFilter == Filter.ALL) {
            items = allItems;
        } else {
            items = new ArrayList<>();
            DetectionResult.Verdict target;
            switch (currentFilter) {
                case SCAM:       target = DetectionResult.Verdict.SCAM;       break;
                case SUSPICIOUS: target = DetectionResult.Verdict.SUSPICIOUS; break;
                case SAFE:       target = DetectionResult.Verdict.SAFE;       break;
                default:         target = null; break;
            }
            if (target != null) {
                for (DetectionResult r : allItems) {
                    if (r.verdict == target) items.add(r);
                }
            }
        }
        notifyDataSetChanged();
    }

    // =========================================================================
    // RecyclerView.Adapter overrides
    // =========================================================================

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DetectionResult result = items.get(position);

        // Check if Alert Mode is active
        boolean isAlert = false;
        try {
            isAlert = com.scamshield.app.data.LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}

        if (isAlert) {
            holder.itemView.setBackgroundResource(R.drawable.card_dark_gray);
            holder.tvReason.setTextColor(Color.WHITE);
            holder.tvSource.setTextColor(Color.parseColor("#B0BEC5"));
            holder.tvTimestamp.setTextColor(Color.parseColor("#9E9E9E"));
        } else {
            holder.itemView.setBackgroundResource(R.drawable.card_white);
            holder.tvReason.setTextColor(Color.parseColor("#1A1A1A"));
            holder.tvSource.setTextColor(Color.parseColor("#555555"));
            holder.tvTimestamp.setTextColor(Color.parseColor("#9E9E9E"));
        }

        // ── Verdict pill ──────────────────────────────────────────────────────
        String verdictLabel;
        int    verdictColor;
        switch (result.verdict) {
            case SCAM:
                verdictLabel = "⚠ SCAM";
                verdictColor = isAlert ? Color.parseColor("#FF5252") : Color.parseColor("#B71C1C");  // deep red
                break;
            case SUSPICIOUS:
                verdictLabel = "⚡ SUSPICIOUS";
                verdictColor = Color.parseColor("#E65100");  // deep orange
                break;
            case SAFE:
            default:
                verdictLabel = "✓ SAFE";
                verdictColor = Color.parseColor("#2E7D32");  // dark green
                break;
        }
        holder.tvVerdict.setText(verdictLabel);
        holder.tvVerdict.setTextColor(verdictColor);

        // ── Source badge (SMS / PAYMENT / CALL) ───────────────────────────────
        String source = (result.sourceType != null && !result.sourceType.isEmpty())
                ? result.sourceType : "UNKNOWN";
        holder.tvSource.setText(source);

        // ── Reason (plain-language, designed for elderly users) ───────────────
        holder.tvReason.setText(result.reason != null ? result.reason : "");

        // ── Timestamp ─────────────────────────────────────────────────────────
        if (result.timestamp > 0) {
            holder.tvTimestamp.setText(DATE_FMT.format(new Date(result.timestamp)));
        } else {
            holder.tvTimestamp.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvVerdict;
        final TextView tvSource;
        final TextView tvReason;
        final TextView tvTimestamp;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVerdict   = itemView.findViewById(R.id.tv_history_verdict);
            tvSource    = itemView.findViewById(R.id.tv_history_source);
            tvReason    = itemView.findViewById(R.id.tv_history_reason);
            tvTimestamp = itemView.findViewById(R.id.tv_history_timestamp);
        }
    }
}
