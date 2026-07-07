package com.scamshield.app.ui;

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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.scamshield.app.R;

/**
 * FloatingAssistantService
 * Package: com.scamshield.app.ui
 *
 * A persistent foreground Service that draws a small draggable circular icon
 * on screen at all times. The user can drag it out of the way and tap it to
 * get quick help.
 *
 * ── Why a Service (not an Activity)? ────────────────────────────────────────
 * An Activity only exists while it is in the foreground (visible to the user).
 * A Service can continue running even when the user switches to another app.
 * Because we want our floating bubble to stay on screen at all times — not just
 * when Scam Shield's own screen is open — we use a foreground Service.
 *
 * A "foreground" Service is one that shows a persistent notification in the
 * status bar. This is required by Android for any Service that does visible
 * work — it prevents Android from silently killing the Service when memory
 * is low, and it keeps the user informed that something is running.
 *
 * ── How the dragging works ───────────────────────────────────────────────────
 * WindowManager lets us add Views outside of any Activity.
 * We attach a Touch listener to the bubble View. When the user puts their
 * finger down, we record the initial position. As they drag (ACTION_MOVE),
 * we update the WindowManager layout params to follow the finger.
 * When they lift their finger (ACTION_UP), if the finger didn't move much,
 * we treat it as a tap and show the help menu.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class FloatingAssistantService extends Service {

    private static final String TAG = "ScamShield.FloatingIcon";

    /** Notification channel ID required for Android 8.0+ foreground services. */
    private static final String CHANNEL_ID = "scamshield_floating_service";

    /** Notification ID — must be > 0. Identifies this notification. */
    private static final int NOTIFICATION_ID = 101;

    /**
     * How many pixels of movement counts as a "drag" vs a "tap".
     * If the finger moves less than this, onReceive() treats it as a tap.
     */
    private static final int TAP_THRESHOLD_PX = 10;

    private WindowManager windowManager;
    private View          bubbleView;
    private View          menuView;

    // Used to track drag movement
    private float initialTouchX, initialTouchY;   // finger position at ACTION_DOWN
    private int   initialBubbleX, initialBubbleY; // bubble position at ACTION_DOWN
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
        // START_STICKY means if Android kills this Service (due to memory pressure),
        // the OS will restart it automatically as soon as memory is available.
        // This is the right flag for a persistent UI Service.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleView != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
        }
        if (menuView != null) {
            try { windowManager.removeView(menuView); } catch (Exception ignored) {}
        }
        Log.d(TAG, "FloatingAssistantService destroyed.");
    }

    /**
     * Services that are not bound (started services) return null here.
     * We use startService() / stopService(), not bindService().
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // =========================================================================
    // Foreground notification
    // =========================================================================

    /**
     * Android 8.0+ requires a visible notification for foreground Services.
     * Without calling startForeground(), the system kills our Service after ~5 sec.
     * The notification tells the user: "Scam Shield is running and watching for scams."
     */
    private void startForegroundWithNotification() {
        // Create notification channel (required on API 26+; silently ignored on lower)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Scam Shield Protection",
                NotificationManager.IMPORTANCE_LOW  // LOW = no sound, just persistent icon
            );
            channel.setDescription("Scam Shield is actively protecting your messages and calls.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        // Tapping the notification opens the Home screen
        Intent openHomeIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, openHomeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)          // TODO: add shield icon to drawable
            .setContentTitle("Scam Shield is active")
            .setContentText("Monitoring messages and calls for scams.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)           // ongoing = user cannot swipe it away
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        Log.d(TAG, "Foreground notification shown.");
    }

    // =========================================================================
    // Floating bubble — creation and drag/tap handling
    // =========================================================================

    /**
     * Creates and adds the circular floating icon to the screen.
     * Uses WindowManager so it appears above all other apps.
     */
    private void addFloatingBubble() {
        // Inflate the bubble layout
        bubbleView = LayoutInflater.from(this)
                .inflate(R.layout.view_floating_bubble, null);
        // WindowManager params for the bubble
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            // NOT_FOCUSABLE: touches outside bubble go through to the app below
            PixelFormat.TRANSLUCENT
        );

        // ── Bottom-right corner positioning ─────────────────────────────────
        // Gravity.BOTTOM | Gravity.END places the origin at the bottom-right.
        // params.x = distance from right edge (16dp in pixels)
        // params.y = distance from bottom edge: 64dp nav bar + 12dp clearance = 76dp
        float density = getResources().getDisplayMetrics().density;
        int marginRightPx  = (int) (16 * density);   // 16dp from right
        int marginBottomPx = (int) (76 * density);   // clears 64dp nav + 12dp gap

        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x       = marginRightPx;
        params.y       = marginBottomPx;

        bubbleView.setOnTouchListener(new BubbleTouchListener(params));
        windowManager.addView(bubbleView, params);

        Log.d(TAG, "Floating bubble added — bottom-right corner, above nav bar.");
    }

    // =========================================================================
    // Touch listener — handles both drag and tap
    // =========================================================================

    /**
     * Inner class that handles touch events on the floating bubble.
     * An inner class (non-static) can access the outer class's fields
     * (windowManager, menuView, etc.) directly — convenient here.
     */
    private class BubbleTouchListener implements View.OnTouchListener {

        private final WindowManager.LayoutParams params;

        BubbleTouchListener(WindowManager.LayoutParams params) {
            this.params = params;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    // Finger touched the bubble — record start positions
                    initialTouchX  = event.getRawX();
                    initialTouchY  = event.getRawY();
                    initialBubbleX = params.x;
                    initialBubbleY = params.y;
                    isDragging     = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // Calculate how far the finger has moved from the start
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;

                    if (Math.abs(dx) > TAP_THRESHOLD_PX
                            || Math.abs(dy) > TAP_THRESHOLD_PX) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        // With Gravity.BOTTOM | Gravity.END:
                        //   params.x = distance from RIGHT edge  (positive = move left)
                        //   params.y = distance from BOTTOM edge (positive = move up)
                        int targetX = initialBubbleX - (int) dx;  // inverted: drag right → decrease x
                        int targetY = initialBubbleY - (int) dy;  // inverted: drag down  → decrease y

                        // Clamp X: keep at least 0 from right edge, max ~screen width - bubble
                        if (targetX < 0) targetX = 0;

                        // Clamp Y: minimum 76dp from bottom (keeps bubble above nav bar)
                        float density = getResources().getDisplayMetrics().density;
                        int minBottomMargin = (int) (76 * density);
                        if (targetY < minBottomMargin) targetY = minBottomMargin;

                        // Clamp Y: maximum so bubble doesn't go off the top of screen
                        try {
                            android.graphics.Point displaySize = new android.graphics.Point();
                            windowManager.getDefaultDisplay().getSize(displaySize);
                            int maxY = displaySize.y - (int) (60 * density); // leave bubble height
                            if (targetY > maxY) targetY = maxY;
                        } catch (Exception ignored) {}

                        params.x = targetX;
                        params.y = targetY;
                        windowManager.updateViewLayout(bubbleView, params);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    // Finger lifted — if not a drag, treat as a tap
                    if (!isDragging) {
                        onBubbleTapped();
                    }
                    return true;
            }
            return false;
        }
    }

    // =========================================================================
    // Bubble tap — show help menu
    // =========================================================================

    /**
     * Called when the user taps (not drags) the floating bubble.
     * Shows a small popup menu with three quick-help options.
     */
    private void onBubbleTapped() {
        // If the menu is already visible, hide it (toggle behaviour)
        if (menuView != null && menuView.isAttachedToWindow()) {
            try { windowManager.removeView(menuView); } catch (Exception ignored) {}
            menuView = null;
            return;
        }

        menuView = LayoutInflater.from(this)
                .inflate(R.layout.view_floating_menu, null);

        // ── Button 1: Check something ───────────────────────────────────────
        View btnCheck = menuView.findViewById(R.id.btn_menu_check);
        btnCheck.setOnClickListener(v -> {
            dismissMenu();
            Intent i = new Intent(FloatingAssistantService.this, CheckSomethingActivity.class);
            // Activities cannot be started directly from a Service — we need
            // the FLAG_ACTIVITY_NEW_TASK flag when launching from a non-Activity context.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        // ── Button 2: Learn about scams ─────────────────────────────────────
        View btnLearn = menuView.findViewById(R.id.btn_menu_learn);
        btnLearn.setOnClickListener(v -> {
            dismissMenu();
            Intent i = new Intent(FloatingAssistantService.this, LearnAboutScamsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        // ── Button 3: I got scammed ─────────────────────────────────────────
        View btnScammed = menuView.findViewById(R.id.btn_menu_scammed);
        btnScammed.setOnClickListener(v -> {
            dismissMenu();
            Intent i = new Intent(FloatingAssistantService.this, IGotScammedActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        // Position menu near the bubble
        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;
        menuParams.x       = 100;
        menuParams.y       = 500;

        windowManager.addView(menuView, menuParams);
        Log.d(TAG, "Floating menu shown.");
    }

    private void dismissMenu() {
        if (menuView != null) {
            try { windowManager.removeView(menuView); } catch (Exception ignored) {}
            menuView = null;
        }
    }
}
