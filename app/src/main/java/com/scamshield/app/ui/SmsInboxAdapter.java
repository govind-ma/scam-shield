package com.scamshield.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
 *
 * Features per row:
 *   • Contact name (if in contacts) OR raw phone number
 *   • Colour-coded verdict pill: SCAM = dark red, SUSPICIOUS = orange, SAFE = teal-green
 *   • Trusted messages show a grey "Trusted ✓" badge instead of the verdict pill
 *   • Truncated message preview (2 lines)
 *   • Timestamp
 *   • "✓ Trust this message" button — tapping it marks the message as trusted
 *     (pill turns grey, button disappears, row tap no longer opens CheckSomethingActivity)
 *
 * Row tap → opens CheckSomethingActivity with the full message body pre-filled
 * (EXTRA_PREFILL_TEXT intent extra) — unless the message is trusted.
 */
public class SmsInboxAdapter extends RecyclerView.Adapter<SmsInboxAdapter.ViewHolder> {

    // Colour constants — Safe/Alert Mode palette
    private static final int COLOR_SCAM        = Color.parseColor("#B71C1C");
    private static final int COLOR_SUSPICIOUS  = Color.parseColor("#E65100");
    private static final int COLOR_SAFE        = Color.parseColor("#00695C");
    private static final int COLOR_TRUSTED     = Color.parseColor("#757575"); // grey
    private static final int COLOR_SCAM_BG     = Color.parseColor("#FFEBEE");
    private static final int COLOR_SUSP_BG     = Color.parseColor("#FBE9E7");
    private static final int COLOR_SAFE_BG     = Color.parseColor("#E8F5E9");
    private static final int COLOR_TRUSTED_BG  = Color.parseColor("#F5F5F5"); // light grey
    private static final int COLOR_TRUST_BTN   = Color.parseColor("#E8F5E9"); // green tint for button bg

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

        // ── Sender: contact name if available, else raw number ────────────────
        holder.tvSender.setText(msg.getDisplayName());

        // ── Preview ───────────────────────────────────────────────────────────
        String preview = (msg.body != null && !msg.body.isEmpty())
                ? msg.body : "(empty message)";
        holder.tvPreview.setText(preview);

        // ── Timestamp ─────────────────────────────────────────────────────────
        holder.tvTimestamp.setText(msg.date > 0 ? DATE_FMT.format(new Date(msg.date)) : "");

        // ── Verdict pill + Trust button ───────────────────────────────────────
        if (msg.trusted) {
            // Trusted: grey pill, no action button, row tap does nothing
            holder.tvVerdict.setText("Trusted ✓");
            holder.tvVerdict.setTextColor(COLOR_TRUSTED);
            setPillBackground(holder.tvVerdict, COLOR_TRUSTED_BG);
            holder.llTrustRow.setVisibility(View.GONE);
            holder.vAccentStripe.setBackgroundColor(COLOR_TRUSTED);
            holder.itemView.setOnClickListener(null);
        } else {
            // Not yet trusted: show colour-coded verdict pill + trust button
            DetectionResult.Verdict verdict =
                    (msg.result != null) ? msg.result.verdict : DetectionResult.Verdict.SAFE;

            switch (verdict) {
                case SCAM:
                    holder.tvVerdict.setText("⚠ SCAM");
                    holder.tvVerdict.setTextColor(COLOR_SCAM);
                    setPillBackground(holder.tvVerdict, COLOR_SCAM_BG);
                    holder.vAccentStripe.setBackgroundColor(COLOR_SCAM);
                    holder.llTrustRow.setVisibility(View.VISIBLE);
                    break;
                case SUSPICIOUS:
                    holder.tvVerdict.setText("⚡ SUSPICIOUS");
                    holder.tvVerdict.setTextColor(COLOR_SUSPICIOUS);
                    setPillBackground(holder.tvVerdict, COLOR_SUSP_BG);
                    holder.vAccentStripe.setBackgroundColor(COLOR_SUSPICIOUS);
                    holder.llTrustRow.setVisibility(View.VISIBLE);
                    break;
                case SAFE:
                default:
                    holder.tvVerdict.setText("✓ SAFE");
                    holder.tvVerdict.setTextColor(COLOR_SAFE);
                    setPillBackground(holder.tvVerdict, COLOR_SAFE_BG);
                    holder.vAccentStripe.setBackgroundColor(COLOR_SAFE);
                    // Hide trust button for SAFE messages
                    holder.llTrustRow.setVisibility(View.GONE);
                    break;
            }

            // Trust button — only visible for SCAM/SUSPICIOUS messages
            setPillBackground(holder.btnTrust, COLOR_TRUST_BTN);

            holder.btnTrust.setOnClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_ID) return;
                SmsMessage target = items.get(adapterPos);
                target.trusted = true;
                notifyItemChanged(adapterPos);
                Toast.makeText(ctx,
                        "Marked as trusted: " + target.getDisplayName(),
                        Toast.LENGTH_SHORT).show();
            });

            // Row tap → open CheckSomethingActivity with body pre-filled
            String body = msg.body;
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, CheckSomethingActivity.class);
                intent.putExtra(CheckSomethingActivity.EXTRA_PREFILL_TEXT, body);
                ctx.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Applies a rounded-rectangle background with the given fill colour.
     * GradientDrawable preserves corner radius (setBackgroundColor would lose it).
     */
    private static void setPillBackground(TextView view, int fillColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(24f);
        shape.setColor(fillColor);
        view.setBackground(shape);
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView    tvSender;
        final TextView    tvVerdict;
        final TextView    tvPreview;
        final TextView    tvTimestamp;
        final LinearLayout llTrustRow;
        final TextView    btnTrust;
        final View        vAccentStripe;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender    = itemView.findViewById(R.id.tv_sms_sender);
            tvVerdict   = itemView.findViewById(R.id.tv_sms_verdict);
            tvPreview   = itemView.findViewById(R.id.tv_sms_preview);
            tvTimestamp = itemView.findViewById(R.id.tv_sms_timestamp);
            llTrustRow  = itemView.findViewById(R.id.ll_trust_row);
            btnTrust    = itemView.findViewById(R.id.btn_trust_message);
            vAccentStripe = itemView.findViewById(R.id.v_accent_stripe);
        }
    }
}
