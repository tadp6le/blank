package com.example.summon;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/**
 * Quick-settings tile — a trigger that works on every device and every
 * Android version: pull down the shade, tap "Summon AI", panel opens.
 */
public class SummonTileService extends TileService {

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        if (SummonActivity.isShowing()) return;

        Intent i = new Intent(this, SummonActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }
}
