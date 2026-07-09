package com.scamshield.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
 * SmsInboxAdapter
 * Package: com.scamshield.app.ui
 *
 * RecyclerView adapter for the SMS inbox view on HomeActivity.
 * Each row shows a single SMS from the device inbox with a colour-coded
 * verdict pill (SCAM = dark red, SUSPICIOUS = orange, SAFE = teal-green).
 *
 * Row tap → opens CheckSomethingActivity with the full message body pre-filled
 * in the input field (via EXTRA_PREFILL_TEXT intent extra).
 *
 * Layout: res/layout/item_sms_inbox.xml
 */
public class SmsInboxAdapter extends RecyclerView.Adapter<SmsInboxAdapter.ViewHolder> {

    // Colour constants — match the app's Safe/Alert Mode palette
    private static final int COLOR_SCAM       = Color.parseColor("#B71C1C"); // Alert Mode dark red
    private static final int COLOR_SUSPICIOUS = Color.parseColor("#E65100"); // deep orange
    private static final int COLOR_SAFE       = Color.parseColor("#00695C"); // Safe Mode teal-green
    private static final int COLOR_SAFE_BG    = Color.parseColor("#E8F5E9"); // light green tint for pill bg
    private static final int COLOR_SCAM_BG    = Color.parseColor("#FFEBEE"); // light red tint for pill bg
    private static final int COLOR_SUSP_BG    = Color.parseColor("#FBE9E7"); // light orange tint for pill bg

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    private List<SmsMessage> items;

    public SmsInboxAdapter(List<SmsMessage> items) {
        this.items = (items != null) ? items : new ArrayList<>();
    }

    /** Replace the full list and redraw. */
    public void setItems(List<SmsMessage> newItems) {
        this.items = (newItems != null) ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    // =========================================================================
    // RecyclerView.Adapter overrides
    // =========================================================================

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sms_inbox, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsMessage msg = items.get(position);
        Context ctx = holder.itemView.getContext();

        // ── Sender ───────────────────────────────────────────────────────────
        holder.tvSender.setText(msg.address != null ? msg.address : "Unknown");

        // ── Preview (truncation handled by maxLines + ellipsize in XML) ───────
        String preview = (msg.body != null && !msg.body.isEmpty())
                ? msg.body
                : "(empty message)";
        holder.tvPreview.setText(preview);

        // ── Timestamp ─────────────────────────────────────────────────────────
        if (msg.date > 0) {
            holder.tvTimestamp.setText(DATE_FMT.format(new Date(msg.date)));
        } else {
            holder.tvTimestamp.setText("");
        }

        // ── Verdict pill ──────────────────────────────────────────────────────
        DetectionResult.Verdict verdict =
                (msg.result != null) ? msg.result.verdict : DetectionResult.Verdict.SAFE;

        switch (verdict) {
            case SCAM:
                holder.tvVerdict.setText("⚠ SCAM");
                holder.tvVerdict.setTextColor(COLOR_SCAM);
                setPillBackground(holder.tvVerdict, COLOR_SCAM_BG);
                break;
            case SUSPICIOUS:
                holder.tvVerdict.setText("⚡ SUSPICIOUS");
                holder.tvVerdict.setTextColor(COLOR_SUSPICIOUS);
                setPillBackground(holder.tvVerdict, COLOR_SUSP_BG);
                break;
            case SAFE:
            default:
                holder.tvVerdict.setText("✓ SAFE");
                holder.tvVerdict.setTextColor(COLOR_SAFE);
                setPillBackground(holder.tvVerdict, COLOR_SAFE_BG);
                break;
        }

        // ── Row tap: open CheckSomethingActivity with body pre-filled ─────────
        String body = msg.body;
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, CheckSomethingActivity.class);
            intent.putExtra(CheckSomethingActivity.EXTRA_PREFILL_TEXT, body);
            ctx.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Applies a rounded-rectangle background with the given fill colour to a
     * TextView pill. Using GradientDrawable programmatically preserves the
     * corner radius that would be lost if we called setBackgroundColor() directly.
     */
    private static void setPillBackground(TextView view, int fillColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(24f); // 24px radius → smooth pill on any screen density
        shape.setColor(fillColor);
        view.setBackground(shape);
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSender;
        final TextView tvVerdict;
        final TextView tvPreview;
        final TextView tvTimestamp;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender    = itemView.findViewById(R.id.tv_sms_sender);
            tvVerdict   = itemView.findViewById(R.id.tv_sms_verdict);
            tvPreview   = itemView.findViewById(R.id.tv_sms_preview);
            tvTimestamp = itemView.findViewById(R.id.tv_sms_timestamp);
        }
    }
}
