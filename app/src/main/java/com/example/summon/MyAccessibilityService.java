package com.example.summon;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Detects the two summon triggers and launches the transparent SummonActivity:
 *
 * 1. Home-key long-press  -> via flagRequestFilterKeyEvents (onKeyEvent).
 *    We do NOT need (and must not request) the Default Assistant role.
 *
 * 2. Edge swipe           -> via onMotionEvent (requires API 34+ to receive
 *    raw touch events; on API 29-33 the FloatingWindowService edge zones
 *    provide the same trigger).
 *
 * Gesture conflict note: we only react to touches in the extreme ~24px
 * left/right margins, and we never consume system-back gestures because a
 * back gesture starts further in (~32dp) — we simply observe, not consume.
 */
public class MyAccessibilityService extends AccessibilityService {

    public static final int EDGE_WIDTH_PX = 24;
    private static final long HOME_LONG_PRESS_MS = 500;

    private boolean homeKeyDown = false;
    private long homeKeyDownTime = 0;
    private boolean homeKeyFired = false;

    private boolean edgeTouchActive = false;
    private long edgeTouchStartTime = 0;
    private float edgeStartY = 0;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    if (!homeKeyDown) {
                        homeKeyDown = true;
                        homeKeyDownTime = System.currentTimeMillis();
                        homeKeyFired = false;
                    }
                    break;

                case KeyEvent.ACTION_UP:
                    homeKeyDown = false;
                    if (!homeKeyFired
                            && System.currentTimeMillis() - homeKeyDownTime >= HOME_LONG_PRESS_MS) {
                        summon();
                    }
                    break;
            }
            // Returning true consumes the event. We return false so the
            // system still handles short Home presses normally; the long
            // press has already launched our overlay.
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Nothing extra needed; config comes from accessibility_service_config.xml.
    }

    @Override
    public void onMotionEvent(MotionEvent event) {
        // Available on API 34+ when canRetrieveWindowContent is false but
        // the service has FLAG_REQUEST_FILTER_KEY_EVENTS + touch exploration
        // of motion events. Acts as a secondary trigger path.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                int w = getResources().getDisplayMetrics().widthPixels;
                float x = event.getX();
                if (x <= EDGE_WIDTH_PX || x >= w - EDGE_WIDTH_PX) {
                    edgeTouchActive = true;
                    edgeTouchStartTime = System.currentTimeMillis();
                    edgeStartY = event.getY();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (edgeTouchActive) {
                    edgeTouchActive = false;
                    float dy = Math.abs(event.getY() - edgeStartY);
                    long dt = System.currentTimeMillis() - edgeTouchStartTime;
                    // A deliberate edge swipe: mostly vertical, >150px, <800ms
                    if (dy > 150 && dt < 800) {
                        summon();
                    }
                }
                break;
        }
        // Do not return anything here; AccessibilityService.onMotionEvent is void.
    }

    private void summon() {
        if (homeKeyFired) {
            return; // prevent duplicate triggers
        }
        homeKeyFired = true;

        Intent i = new Intent(this, SummonActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used; we only need key/motion filtering.
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }
}
