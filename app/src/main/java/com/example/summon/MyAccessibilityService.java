package com.example.summon;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * This service is an optional extra trigger path only. On Android 12,
 * the system assistant role and the overlay-trigger service are the reliable
 * mechanisms for Gemini-like summon behavior.
 */
public class MyAccessibilityService extends AccessibilityService {

    private static final long HOME_LONG_PRESS_MS = 400;
    private static final float MIN_SWIPE_PX = 120f;
    private static final long MAX_SWIPE_MS = 700;
    private static final long SUMMON_DEBOUNCE_MS = 800;

    private boolean homeDown = false;
    private long homeDownTime = 0;

    private boolean edgeActive = false;
    private long edgeDownTime = 0;
    private float edgeStartY = 0;

    private long lastSummon = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                java.lang.reflect.Method method = AccessibilityService.class
                        .getMethod("setMotionEventSources", int.class);
                if (method != null) {
                    method.invoke(this, InputDevice.SOURCE_TOUCHSCREEN);
                }
            } catch (Exception ignored) {
                // Optional Android 14+ raw touch hook; do not break Android 12.
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0
                    && !event.isCanceled()) {
                homeDown = true;
                homeDownTime = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP && homeDown) {
                homeDown = false;
                long elapsed = System.currentTimeMillis() - homeDownTime;
                if (elapsed >= HOME_LONG_PRESS_MS) {
                    summon();
                }
            }
        }
        return false;
    }

    @Override
    public void onMotionEvent(MotionEvent event) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int edge = Math.round(12f * getResources().getDisplayMetrics().density);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getX() <= edge || event.getX() >= screenWidth - edge) {
                    edgeActive = true;
                    edgeDownTime = System.currentTimeMillis();
                    edgeStartY = event.getY();
                } else {
                    edgeActive = false;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (edgeActive) {
                    edgeActive = false;
                    float dy = Math.abs(event.getY() - edgeStartY);
                    long dt = System.currentTimeMillis() - edgeDownTime;
                    if (dy >= MIN_SWIPE_PX && dt <= MAX_SWIPE_MS) {
                        summon();
                    }
                }
                break;
        }
    }

    private void summon() {
        if (SummonActivity.isShowing()) return;
        long now = System.currentTimeMillis();
        if (now - lastSummon < SUMMON_DEBOUNCE_MS) return;
        lastSummon = now;

        Intent intent = new Intent(this, SummonActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
