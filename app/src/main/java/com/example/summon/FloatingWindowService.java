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
 * Edge-swipe trigger for Android 10-13 (API 29-33), where an accessibility
 * service cannot observe raw touches.
 *
 * Why it did not work before: it was a plain background service that was
 * only started from MainActivity and was killed as soon as the app left the
 * foreground, so the edge strips never existed in practice.
 *
 * Now it is a STICKY FOREGROUND service with a persistent (minimal)
 * notification and a BOOT_COMPLETED receiver, so the strips survive app
 * close and reboot. It places two transparent 12dp strips on the extreme
 * screen edges; a vertical flick on either one summons the panel. The
 * strips only react to vertical swipes — horizontal drags pass through and
 * the system back gesture is unaffected.
 */
public class FloatingWindowService extends Service {

    public static final String CHANNEL_ID = "summon_triggers";
    public static final int NOTIFICATION_ID = 1001;

    private WindowManager wm;
    private View leftEdge, rightEdge;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
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

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                stripWidth,
                WindowManager.LayoutParams.MATCH_PARENT,
                isLeft ? 0 : screenWidth - stripWidth,
                0,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        final long[] downTime = {0};
        final float[] downY = {0};

        View v = new View(this);
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime[0] = System.currentTimeMillis();
                    downY[0] = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dy = Math.abs(event.getRawY() - downY[0]);
                    long dt = System.currentTimeMillis() - downTime[0];
                    if (dy > 120 && dt < 800) {
                        summon();
                    }
                    return true;
            }
            return false; // horizontal drags (back gesture) fall through
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
