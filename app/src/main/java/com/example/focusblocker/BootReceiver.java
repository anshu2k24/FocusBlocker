package com.example.focusblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * BootReceiver: BroadcastReceiver listening for BOOT_COMPLETED.
 * 
 * Viva Concepts:
 * 1. BroadcastReceiver: An Android component that listens for system-wide broadcast events
 *    (like device reboot, battery low, etc.) even when the app is not running.
 * 2. Static Registration: Declared in AndroidManifest.xml so the system wakes up our receiver on boot.
 * 3. Starting Foreground Service from Boot: ContextCompat.startForegroundService must be used
 *    because background service start restrictions apply on Android 8.0+ (Oreo).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device reboot completed received.");

            // Check if blocking was previously enabled by the user
            boolean isBlockingEnabled = PrefsHelper.isBlockingEnabled(context);
            if (isBlockingEnabled) {
                Log.d(TAG, "Blocking is enabled. Restarting BlockService...");
                Intent serviceIntent = new Intent(context, BlockService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                Log.d(TAG, "Blocking is disabled. BlockService will not start.");
            }
        }
    }
}
