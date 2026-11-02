package com.example.summon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Restarts the edge-strip foreground service after a reboot, so the edge
 * swipe trigger keeps working without opening the app first.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (SettingsStore.isEdgeTriggerEnabled(context)
                && Settings.canDrawOverlays(context)) {
            context.startForegroundService(
                    new Intent(context, FloatingWindowService.class));
        }
    }
}
