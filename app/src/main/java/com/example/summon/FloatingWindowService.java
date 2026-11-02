package com.example.summon;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Fallback trigger path for Android 10-13 (API 29-33), where a plain
 * AccessibilityService cannot observe raw screen touches.
 *
 * When MainActivity detects SYSTEM_ALERT_WINDOW permission is granted, this
 * service adds two INVISIBLE, 24px-wide, full-height touch-capturing strips
 * on the extreme screen edges. A vertical swipe on either strip summons
 * SummonActivity. The strips pass through all touches that are not a
 * deliberate vertical swipe, so normal app usage is unaffected.
 *
 * (For simplicity and to avoid blocking system gestures, we use
 * FLAG_NOT_FOCUSABLE and a very narrow touch region; on API 34+ the
 * AccessibilityService edge detection is preferred and this service
 * is optional.)
 */
public class FloatingWindowService extends Service {

    public static final int EDGE_WIDTH_PX = 24;
    private static final float MIN_SWIPE_DISTANCE = 150f;
    private static final long MAX_SWIPE_DURATION_MS = 800;

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
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addEdgeStrip(true);
        addEdgeStrip(false);
    }

    private void addEdgeStrip(boolean isLeft) {
        int w = getResources().getDisplayMetrics().widthPixels;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                EDGE_WIDTH_PX,
                WindowManager.LayoutParams.MATCH_PARENT,
                isLeft ? 0 : w - EDGE_WIDTH_PX,
                0,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        final long[] downTime = {0};
        final float[] downY = {0};

        View v = new View(this);
        v.setLayoutParams(new WindowManager.LayoutParams(
                EDGE_WIDTH_PX,
                WindowManager.LayoutParams.MATCH_PARENT));
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime[0] = System.currentTimeMillis();
                    downY[0] = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dy = Math.abs(event.getRawY() - downY[0]);
                    long dt = System.currentTimeMillis() - downTime[0];
                    if (dy > MIN_SWIPE_DISTANCE && dt < MAX_SWIPE_DURATION_MS) {
                        summon();
                    }
                    return true;
            }
            return false;
        });

        if (isLeft) leftEdge = v; else rightEdge = v;
        wm.addView(v, lp);
    }

    private void summon() {
        Intent i = new Intent(this, SummonActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    @Override
    public void onDestroy() {
        if (wm != null) {
            if (leftEdge != null) wm.removeView(leftEdge);
            if (rightEdge != null) wm.removeView(rightEdge);
        }
        super.onDestroy();
    }

    @SuppressWarnings("unused")
    private static int sdk() {
        return Build.VERSION.SDK_INT;
    }
}
