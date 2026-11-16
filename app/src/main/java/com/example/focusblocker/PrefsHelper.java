package com.example.focusblocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * PrefsHelper: Centralized helper class to manage SharedPreferences.
 * College Viva Note: SharedPreferences provides a simple key-value store
 * saved in XML format on the device's private storage directory.
 */
public class PrefsHelper {
    public static final String PREFS_NAME = "focus_blocker_prefs";

    public static final String KEY_IS_BLOCKING_ENABLED = "is_blocking_enabled";
    public static final String KEY_START_HOUR = "start_hour";
    public static final String KEY_START_MINUTE = "start_minute";
    public static final String KEY_BLOCKED_PACKAGES = "blocked_packages";
    public static final String KEY_REVOKE_UNTIL = "revoke_until";
    public static final String KEY_INITIAL_SETUP_DONE = "initial_setup_done";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isBlockingEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_BLOCKING_ENABLED, false);
    }

    public static void setBlockingEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_IS_BLOCKING_ENABLED, enabled).apply();
    }

    public static boolean hasSavedStartTime(Context context) {
        return getPrefs(context).contains(KEY_START_HOUR);
    }

    public static int getStartHour(Context context) {
        Calendar now = Calendar.getInstance();
        return getPrefs(context).getInt(KEY_START_HOUR, now.get(Calendar.HOUR_OF_DAY));
    }

    public static int getStartMinute(Context context) {
        Calendar now = Calendar.getInstance();
        return getPrefs(context).getInt(KEY_START_MINUTE, now.get(Calendar.MINUTE));
    }

    public static void setStartTime(Context context, int hour, int minute) {
        getPrefs(context).edit()
                .putInt(KEY_START_HOUR, hour)
                .putInt(KEY_START_MINUTE, minute)
                .apply();
    }

    public static Set<String> getBlockedPackages(Context context) {
        Set<String> set = getPrefs(context).getStringSet(KEY_BLOCKED_PACKAGES, null);
        if (set == null) {
            return new HashSet<>();
        }
        return new HashSet<>(set); // Return copy to prevent mutation bugs
    }

    public static void setBlockedPackages(Context context, Set<String> packages) {
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply();
    }

    public static long getRevokeUntil(Context context) {
        return getPrefs(context).getLong(KEY_REVOKE_UNTIL, 0);
    }

    public static void setRevokeUntil(Context context, long timestampMs) {
        getPrefs(context).edit().putLong(KEY_REVOKE_UNTIL, timestampMs).apply();
    }

    public static boolean isInitialSetupDone(Context context) {
        return getPrefs(context).getBoolean(KEY_INITIAL_SETUP_DONE, false);
    }

    public static void setInitialSetupDone(Context context, boolean done) {
        getPrefs(context).edit().putBoolean(KEY_INITIAL_SETUP_DONE, done).apply();
    }
}
