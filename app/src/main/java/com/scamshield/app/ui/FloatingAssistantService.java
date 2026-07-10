package com.scamshield.app.ui;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.scamshield.app.R;
import com.scamshield.app.data.LocalDataStore;

/**
 * FloatingAssistantService
 * Package: com.scamshield.app.ui
 *
 * Persistent foreground Service that draws a small draggable glowing shield
 * on screen at all times. Tapping it opens ChatAssistantActivity directly.
 *
 * ── Glow animation ───────────────────────────────────────────────────────────
 * Two concentric oval ring Views sit behind the shield FAB.
 * A ValueAnimator pulses their scaleX/scaleY (1.0 → 1.5) and alpha (0.6 → 0.0)
 * on a ~1.8s cycle. The outer ring is offset by 900ms so the two rings pulse
 * in a staggered "ripple" pattern, giving the impression of expanding waves.
 * Glow color is green (Safe Mode) or red (Alert Mode) — checked on each start.
 *
 * ── Tap behaviour ────────────────────────────────────────────────────────────
 * Single tap (not drag) → opens ChatAssistantActivity with FLAG_ACTIVITY_NEW_TASK.
 * No intermediate menu — direct launch for maximum simplicity.
 */
public class FloatingAssistantService extends Service {

    private static final String TAG = "ScamShield.FloatingIcon";

    private static final String CHANNEL_ID    = "scamshield_floating_service";
    private static final int    NOTIFICATION_ID = 101;
    private static final int    TAP_THRESHOLD_PX = 10;

    // Safe Mode = teal/green, Alert Mode = red
    private static final int COLOR_SAFE  = Color.parseColor("#00695C");
    private static final int COLOR_ALERT = Color.parseColor("#B71C1C");

    private WindowManager windowManager;
    private View          bubbleView;

    // Glow ring views (populated after inflation)
    private View   glowOuter;
    private View   glowInner;

    // Glow animators — kept so we can cancel on destroy
    private ValueAnimator outerAnimator;
    private ValueAnimator innerAnimator;

    // Drag tracking
    private float   initialTouchX, initialTouchY;
    private int     initialBubbleX, initialBubbleY;
    private boolean isDragging = false;

    // =========================================================================
    // Service lifecycle
    // =========================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundWithNotification();
        addFloatingBubble();
        Log.d(TAG, "FloatingAssistantService started.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopGlowAnimation();
        if (bubbleView != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
        }
        Log.d(TAG, "FloatingAssistantService destroyed.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // =========================================================================
    // Foreground notification
    // =========================================================================

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Scam Shield Protection",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Scam Shield is actively protecting your messages and calls.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Intent openHomeIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, openHomeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Scam Shield is active")
            .setContentText("Tap the shield bubble for the Cyber Help Agent.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    // =========================================================================
    // Floating bubble — creation
    // =========================================================================

    private void addFloatingBubble() {
        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.view_floating_bubble, null);

        glowOuter = bubbleView.findViewById(R.id.glow_ring_outer);
        glowInner = bubbleView.findViewById(R.id.glow_ring_inner);

        // Pick glow color based on current threat state
        boolean isAlert = false;
        try {
            isAlert = LocalDataStore.getInstance().isAlertModeActive();
        } catch (Exception ignored) {}
        applyGlowColor(isAlert ? COLOR_ALERT : COLOR_SAFE);
        startGlowAnimation();

        // Window params — bottom-right corner, safely above the 64dp nav bar.
        // x = distance from RIGHT edge, y = distance from BOTTOM edge (Gravity.BOTTOM|END).
        // 24dp right margin keeps it off the edge; 80dp bottom = 64dp nav + 16dp gap.
        float density      = getResources().getDisplayMetrics().density;
        int marginRightPx  = (int) (24 * density);   // 24dp from right edge
        int marginBottomPx = (int) (80 * density);   // 64dp nav bar + 16dp breathing room

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x       = marginRightPx;
        params.y       = marginBottomPx;

        bubbleView.setOnTouchListener(new BubbleTouchListener(params));
        windowManager.addView(bubbleView, params);

        Log.d(TAG, "Floating bubble added — glow color=" + (isAlert ? "RED" : "GREEN"));
    }

    // =========================================================================
    // Glow animation — two staggered ring pulses
    // =========================================================================

    /**
     * Creates two ValueAnimators that animate scale + alpha on the glow ring Views.
     * Outer ring: 1.8s period, delay 0ms.
     * Inner ring: 1.8s period, delay 900ms (half-period offset → staggered ripple).
     */
    private void startGlowAnimation() {
        if (glowOuter == null || glowInner == null) return;

        // Outer ring — larger expansion (1.0 → 1.5)
        outerAnimator = buildRingAnimator(glowOuter, 1.0f, 1.55f, 1800, 0);
        outerAnimator.start();

        // Inner ring — smaller expansion (1.0 → 1.3), starts half a cycle later
        innerAnimator = buildRingAnimator(glowInner, 1.0f, 1.3f, 1800, 900);
        innerAnimator.start();
    }

    private ValueAnimator buildRingAnimator(View ring, float scaleFrom, float scaleTo, int duration, int startDelay) {
        // Animate a single float 0→1 and map it to scale + alpha ourselves
        // so we can control the curve precisely.
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(duration);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.setStartDelay(startDelay);

        anim.addUpdateListener(animator -> {
            float fraction = (float) animator.getAnimatedValue();
            float scale  = scaleFrom + (scaleTo - scaleFrom) * fraction;
            float alpha  = 0.65f * (1f - fraction);   // starts visible, fades to 0
            ring.setScaleX(scale);
            ring.setScaleY(scale);
            ring.setAlpha(alpha);
        });
        return anim;
    }

    private void stopGlowAnimation() {
        if (outerAnimator != null) { outerAnimator.cancel(); outerAnimator = null; }
        if (innerAnimator != null) { innerAnimator.cancel(); innerAnimator = null; }
    }

    /**
     * Tints both glow ring backgrounds to the given color.
     * Called once on startup based on LocalDataStore.isAlertModeActive().
     */
    private void applyGlowColor(int color) {
        if (glowOuter != null) glowOuter.getBackground().setTint(color);
        if (glowInner != null) glowInner.getBackground().setTint(color);
    }

    // =========================================================================
    // Touch listener — drag + tap
    // =========================================================================

    private class BubbleTouchListener implements View.OnTouchListener {

        private final WindowManager.LayoutParams params;

        BubbleTouchListener(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    initialTouchX  = event.getRawX();
                    initialTouchY  = event.getRawY();
                    initialBubbleX = params.x;
                    initialBubbleY = params.y;
                    isDragging     = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;

                    if (Math.abs(dx) > TAP_THRESHOLD_PX || Math.abs(dy) > TAP_THRESHOLD_PX) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        // Gravity.BOTTOM | Gravity.END: x = from right, y = from bottom
                        int targetX = initialBubbleX - (int) dx;
                        int targetY = initialBubbleY - (int) dy;

                        if (targetX < 0) targetX = 0;

                        float density = getResources().getDisplayMetrics().density;
                        // Minimum Y from bottom: keep bubble above the nav bar (80dp)
                        int minBottomMargin = (int) (80 * density);
                        if (targetY < minBottomMargin) targetY = minBottomMargin;

                        // Maximum Y from bottom: don't let bubble go off the top of screen
                        try {
                            android.graphics.Point displaySize = new android.graphics.Point();
                            windowManager.getDefaultDisplay().getSize(displaySize);
                            int maxY = displaySize.y - (int) (100 * density);
                            if (targetY > maxY) targetY = maxY;
                        } catch (Exception ignored) {}

                        params.x = targetX;
                        params.y = targetY;
                        windowManager.updateViewLayout(bubbleView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        onBubbleTapped();
                    }
                    return true;
            }
            return false;
        }
    }

    // =========================================================================
    // Tap — open Cyber Help Agent directly
    // =========================================================================

    /**
     * Opens ChatAssistantActivity on single tap.
     * No intermediate menu — direct launch for maximum simplicity.
     * FLAG_ACTIVITY_NEW_TASK is required when starting an Activity from a Service.
     */
    private void onBubbleTapped() {
        Intent i = new Intent(FloatingAssistantService.this, ChatAssistantActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        Log.d(TAG, "Bubble tapped → ChatAssistantActivity opened.");
    }
}
