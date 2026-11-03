package com.example.summon;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Secondary trigger path for observing Home-key long presses. The primary
 * trigger is the Default Assistant role, which opens the panel system-wide.
 *
 * Android 12 does not support AccessibilityService raw touch-event
 * observation. Edge swipes on Android 10-13 are handled by
 * FloatingWindowService's overlay strips instead; Android 14+ touch
 * observation is intentionally omitted here so this service remains
 * compatible with older Android versions.
 *
 * This service never consumes key events, so normal Home behavior remains
 * unchanged.
 */
public class MyAccessibilityService extends AccessibilityService {

    private static final long HOME_LONG_PRESS_MS = 400;

    private boolean homeDown = false;
    private long homeDownTime = 0;
    private long lastSummon = 0;

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
        return false; // Never consume: short Home presses still go Home.
    }

    private void summon() {
        if (SummonActivity.isShowing()) return;
        long now = System.currentTimeMillis();
        if (now - lastSummon < 800) return;
        lastSummon = now;

        Intent i = new Intent(this, SummonActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used; we only need key observation.
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }
}
