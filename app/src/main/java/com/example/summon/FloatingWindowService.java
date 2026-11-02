package com.example.summon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Edge-swipe trigger for Android 10-13 (on 14+ the accessibility service
 * observes touches natively). This is the ONLY swipe path on Android 12.
 *
 * FIXES in v1.2 ("swipe up -> white glob -> home screen"):
 *  1. The strips used to span the FULL screen height, covering the bottom
 *     system-gesture zone, so an upward swipe along the edge collided with
 *     the Home gesture. Now the strips cover only the vertical middle of
 *     each edge (12%..88% of screen height): the top and bottom gesture
 *     zones are untouched, so Home/recents/notifications never conflict.
 *  2. The trigger requires a strongly VERTICAL flick (|dx| < |dy|), so
 *     horizontal drags — i.e. the back gesture — never summon.
 *  3. The whole feature can be switched off from the setup screen; when
 *     off, the service removes the strips and stops.
 *
 * HOW TO TRIGGER: flick up (or down) along the MIDDLE of the left or right
 * screen edge — not from the very bottom corner, which is the Home gesture.
 */
public class FloatingWindowService extends Service {

    public static final String CHANNEL_ID = "summon_triggers";
    public static final int NOTIFICATION_ID = 1001;

    // Fraction of screen height reserved for system gestures at top/bottom.
    private static final float TOP_EXCLUDE = 0.12f;
    private static final float BOTTOM_EXCLUDE = 0.12f;

    private static final float MIN_SWIPE_PX = 120f;
    private static final long MAX_SWIPE_MS = 800;

    private WindowManager wm;
    private View leftEdge, rightEdge;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!SettingsStore.isEdgeTriggerEnabled(this) || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        goForeground();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addEdgeStrip(true);
        addEdgeStrip(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!SettingsStore.isEdgeTriggerEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setOngoing(true)
                .build();
        try {
            startForeground(NOTIFICATION_ID, n);
        } catch (Exception ignored) {
            // Very rare OEM quirk; keep running even if the FGS promotion fails.
        }
    }

    private void addEdgeStrip(boolean isLeft) {
        int density = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        int stripWidth = Math.max(24, 12 * density);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Stay clear of the status bar and the bottom navigation-gesture
        // zone: only the vertical middle of the edge is a trigger.
        int yOffset = Math.round(screenHeight * TOP_EXCLUDE);
        int stripHeight = Math.round(screenHeight * (1f - TOP_EXCLUDE - BOTTOM_EXCLUDE));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                stripWidth,
                stripHeight,
                isLeft ? 0 : screenWidth - stripWidth,
                yOffset,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        final long[] downTime = {0};
        final float[] downX = {0};
        final float[] downY = {0};

        View v = new View(this);
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime[0] = System.currentTimeMillis();
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = Math.abs(event.getRawX() - downX[0]);
                    float dy = Math.abs(event.getRawY() - downY[0]);
                    long dt = System.currentTimeMillis() - downTime[0];
                    // Strongly vertical flick only: |dx| < |dy|.
                    if (dy > MIN_SWIPE_PX && dy > dx && dt < MAX_SWIPE_MS) {
                        summon();
                    }
                    return true;
            }
            return false;
        });

        if (isLeft) leftEdge = v; else rightEdge = v;
        try {
            wm.addView(v, lp);
        } catch (Exception ignored) {
            // e.g. overlay permission revoked while starting
        }
    }

    private void summon() {
        if (SummonActivity.isShowing()) return;
        Intent i = new Intent(this, SummonActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    @Override
    public void onDestroy() {
        if (wm != null) {
            if (leftEdge != null) try { wm.removeView(leftEdge); } catch (Exception ignored) {}
            if (rightEdge != null) try { wm.removeView(rightEdge); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
