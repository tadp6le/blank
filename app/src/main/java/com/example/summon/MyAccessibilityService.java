package com.example.summon;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Secondary trigger path (the PRIMARY trigger is the Default Assistant role,
 * which makes Home long-press / corner-swipe summon the panel system-wide).
 *
 * What this service adds:
 *  - Android 14+ (API 34): opts in to raw touch events via
 *    setMotionEventSources() — this is REQUIRED for onMotionEvent to ever
 *    fire, which is why edge swipes did not work before. We detect a
 *    deliberate vertical swipe starting inside the outer 12dp of the screen.
 *  - 3-button navigation: we observe (never consume) KEYCODE_HOME and
 *    measure the press duration to detect a long press.
 *
 * The service never consumes events, so system gestures (back, home) keep
 * working exactly as before.
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
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // Android 14+: without this call onMotionEvent is NEVER invoked.
                setMotionEventSources(InputDevice.SOURCE_TOUCHSCREEN);
            } catch (Exception ignored) {
                // Some OEM builds restrict this; assistant role still works.
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
                if (System.currentTimeMillis() - homeDownTime >= HOME_LONG_PRESS_MS) {
                    summon();
                }
            }
        }
        return false; // never consume: short Home presses still go Home
    }

    @Override
    public void onMotionEvent(MotionEvent event) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int edge = Math.round(12f * getResources().getDisplayMetrics().density);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Only touches that START inside the outer 12dp count.
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
                    // A deliberate, mostly-vertical flick along the edge.
                    if (dy >= MIN_SWIPE_PX && dt <= MAX_SWIPE_MS) {
                        summon();
                    }
                }
                break;
        }
        // no return value: AccessibilityService.onMotionEvent is void
    }

    private void summon() {
        if (SummonActivity.isShowing()) return;
        long now = System.currentTimeMillis();
        if (now - lastSummon < SUMMON_DEBOUNCE_MS) return;
        lastSummon = now;

        Intent i = new Intent(this, SummonActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used; we only need key/motion observation.
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }
