package com.example.focusblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * BlockService: Android Foreground Service that monitors running apps every 1 second.
 * 
 * Viva Concepts:
 * 1. Foreground Service: Keeps running in background with high priority so Android OS doesn't kill it.
 * 2. UsageStatsManager: Modern Android API to query app usage history and active foreground apps
 *    without violating privacy or using deprecated ActivityManager APIs.
 * 3. Android 14 FGS Types: Required to declare foregroundServiceType="specialUse" and
 *    PROPERTY_SPECIAL_USE_FGS_SUBTYPE in AndroidManifest.xml.
 */
public class BlockService extends Service {

    private static final String TAG = "BlockService";
    private static final String CHANNEL_ID = "focus_blocker_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ScheduledExecutorService scheduler;
    private Set<String> launcherPackages = new HashSet<>();
    private String currentForegroundApp = null;
    private String lastBlockedPackage = null;
    private long lastBlockTriggerTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        cacheLauncherPackages();
        startInForeground();
        startAppMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY tells Android to restart the service if the system kills it for memory
        return START_STICKY;
    }

    /**
     * Creates the mandatory NotificationChannel for Android 8.0+ (API 26+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FocusBlocker Monitoring Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active monitoring notification for FocusBlocker");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Displays a persistent notification to elevate this service to the Foreground.
     */
    private void startInForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FocusBlocker is Active")
                .setContentText("Monitoring apps to help you stay focused.")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 (API 34) requires specialUse type specification
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Caches home launcher package names so the app never blocks the home screen.
     */
    private void cacheLauncherPackages() {
        launcherPackages.clear();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        PackageManager pm = getPackageManager();
        List<ResolveInfo> launchers = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : launchers) {
            if (ri.activityInfo != null) {
                launcherPackages.add(ri.activityInfo.packageName);
            }
        }
        // Always safeguard common system UI
        launcherPackages.add("com.android.systemui");
        launcherPackages.add(getPackageName());
    }

    /**
     * Starts the periodic 1-second monitoring loop.
     */
    private void startAppMonitoring() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::checkForegroundAppAndBlockIfNeeded, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Core blocking logic:
     * 1. Query latest ACTIVITY_RESUMED event in the last 10 seconds.
     * 2. Check if blocking is enabled, current time >= start time, app in blocked list, and now > revokeUntil.
     * 3. Launch BlockActivity if all conditions hold.
     */
    private void checkForegroundAppAndBlockIfNeeded() {
        try {
            if (!PrefsHelper.isBlockingEnabled(this)) {
                return;
            }

            // Do not attempt to block if screen is off or device is locked
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && !powerManager.isInteractive()) {
                return;
            }

            // 1. Check time condition: Is current time past the block start time?
            Calendar now = Calendar.getInstance();
            int currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int startTotalMinutes = PrefsHelper.getStartHour(this) * 60 + PrefsHelper.getStartMinute(this);

            if (currentTotalMinutes < startTotalMinutes) {
                // Not yet time to block apps
                return;
            }

            // 2. Check revoke condition: Is there an active pause?
            long nowMs = System.currentTimeMillis();
            long revokeUntil = PrefsHelper.getRevokeUntil(this);
            if (nowMs <= revokeUntil) {
                // Temporary pause active
                return;
            }

            // 3. Query the current foreground app using UsageStatsManager
            String foregroundPackage = getForegroundPackageName();
            if (foregroundPackage == null || foregroundPackage.isEmpty()) {
                return;
            }

            // 4. Never block our own app or the system launcher
            if (launcherPackages.contains(foregroundPackage) || foregroundPackage.equals(getPackageName())) {
                lastBlockedPackage = null;
                return;
            }

            // 5. Check if foreground app is in the user's blocked list
            Set<String> blockedApps = PrefsHelper.getBlockedPackages(this);
            if (blockedApps.contains(foregroundPackage)) {
                // Prevent duplicate activity launch loops within 2 seconds for the same package
                if (foregroundPackage.equals(lastBlockedPackage) && (nowMs - lastBlockTriggerTime < 2000)) {
                    return;
                }

                lastBlockedPackage = foregroundPackage;
                lastBlockTriggerTime = nowMs;

                Log.d(TAG, "Blocking app: " + foregroundPackage);

                // Launch BlockActivity with FLAG_ACTIVITY_NEW_TASK
                Intent blockIntent = new Intent(this, BlockActivity.class);
                blockIntent.putExtra(BlockActivity.EXTRA_BLOCKED_PACKAGE, foregroundPackage);
                blockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(blockIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in checkForegroundAppAndBlockIfNeeded", e);
        }
    }

    /**
     * Viva Concept: UsageStatsManager.queryEvents queries system app switch events.
     * ACTIVITY_RESUMED indicates an app window has come to the user's foreground.
     * 
     * Continuous Usage Fix:
     * When an app is actively used for minutes without switching, no new ACTIVITY_RESUMED
     * event occurs within a short 10-second window. Therefore, we preserve the last-known
     * resumed application across ticks, ensuring active apps are immediately blocked
     * when the scheduled start time arrives without requiring the user to exit and reopen them.
     */
    private String getForegroundPackageName() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return currentForegroundApp;

        long endTime = System.currentTimeMillis();
        // Look back 15 seconds to catch recent app switch events
        long startTime = endTime - 15000;

        UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                // ACTIVITY_RESUMED has integer value 1 (same as MOVE_TO_FOREGROUND)
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentForegroundApp = event.getPackageName();
                }
            }
        }

        // If currentForegroundApp has not been established yet (e.g. user was already inside
        // the app before service started), scan up to 2 hours back to find the active app.
        if (currentForegroundApp == null) {
            UsageEvents historicalEvents = usageStatsManager.queryEvents(endTime - (2 * 3600 * 1000L), endTime);
            if (historicalEvents != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                while (historicalEvents.hasNextEvent()) {
                    historicalEvents.getNextEvent(event);
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                        currentForegroundApp = event.getPackageName();
                    }
                }
            }
        }

        return currentForegroundApp;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Bound services return an IBinder; our started service returns null
        return null;
    }
}
