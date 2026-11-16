package com.example.focusblocker;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * MainActivity: Main controller activity for FocusBlocker.
 * 
 * Viva Concepts:
 * 1. Activity Lifecycle: onResume() is used to refresh permission statuses dynamically
 *    when returning from system settings.
 * 2. Intents: Explicit Intents launch our own activities and services;
 *    Implicit Intents open system settings (e.g. ACTION_USAGE_ACCESS_SETTINGS).
 * 3. Runtime Permissions: Demonstrates checking and requesting modern permissions (POST_NOTIFICATIONS).
 * 4. SharedPreferences: Persists user-defined block start time and service state.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_NOTIFICATION = 2001;

    private TextView tvBlockingStatus;
    private TextView tvUsageStatus;
    private Button btnGrantUsage;
    private TextView tvOverlayStatus;
    private Button btnGrantOverlay;
    private LinearLayout layoutNotificationPermission;
    private TextView tvNotificationStatus;
    private Button btnGrantNotification;

    private TimePicker timePickerStart;
    private TextView tvScheduleStatus;
    private Button btnSelectApps;
    private Button btnToggleBlocking;
    private TextView tvRevokeCountdown;

    // Handler for live countdown UI timer
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateRevokeCountdown();
            updateScheduleStatusDisplay();
            countdownHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        requestNotificationPermissionIfNeeded();
        loadSavedStartTime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh permissions and state whenever user returns to MainActivity
        refreshPermissionStatuses();
        refreshBlockingButtonUI();
        updateScheduleStatusDisplay();
        updateScheduleStatusDisplay();
        countdownHandler.post(countdownRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void initViews() {
        tvBlockingStatus = findViewById(R.id.tvBlockingStatus);
        tvUsageStatus = findViewById(R.id.tvUsageStatus);
        btnGrantUsage = findViewById(R.id.btnGrantUsage);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay);
        layoutNotificationPermission = findViewById(R.id.layoutNotificationPermission);
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus);
        btnGrantNotification = findViewById(R.id.btnGrantNotification);

        timePickerStart = findViewById(R.id.timePickerStart);
        // Ensure 12-hour AM/PM view is active so users can easily pick AM or PM
        timePickerStart.setIs24HourView(false);

        tvScheduleStatus = findViewById(R.id.tvScheduleStatus);
        btnSelectApps = findViewById(R.id.btnSelectApps);
        btnToggleBlocking = findViewById(R.id.btnToggleBlocking);
        tvRevokeCountdown = findViewById(R.id.tvRevokeCountdown);
    }

    private void setupListeners() {
        // Open Usage Access Settings
        btnGrantUsage.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        });

        // Open Overlay ("Display over other apps") Settings
        btnGrantOverlay.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            } catch (Exception e) {
                Intent fallback = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivity(fallback);
            }
        });

        // Open/Request Notification permission
        btnGrantNotification.setOnClickListener(v -> requestNotificationPermissionIfNeeded());

        // Real-time listener: immediately updates SharedPreferences whenever user changes time
        timePickerStart.setOnTimeChangedListener((view, hourOfDay, minuteOfHour) -> {
            PrefsHelper.setStartTime(MainActivity.this, hourOfDay, minuteOfHour);
            updateScheduleStatusDisplay();
        });

        // Open App Selection Activity
        btnSelectApps.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AppSelectActivity.class);
            startActivity(intent);
        });

        // Toggle Blocking Service
        btnToggleBlocking.setOnClickListener(v -> toggleBlockingState());
    }

    private void loadSavedStartTime() {
        int hour = PrefsHelper.getStartHour(this);
        int minute = PrefsHelper.getStartMinute(this);
        timePickerStart.setHour(hour);
        timePickerStart.setMinute(minute);
        updateScheduleStatusDisplay();
    }

    /**
     * Toggles the blocking state and starts or stops the Foreground Service.
     */
    private void toggleBlockingState() {
        boolean currentState = PrefsHelper.isBlockingEnabled(this);
        if (!currentState) {
            // Check permissions before starting
            if (!hasUsageAccessPermission() || !hasOverlayPermission()) {
                Toast.makeText(
                        this,
                        "Please grant Usage Access and Overlay permissions first!",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // Save start time selected by user
            int hour = timePickerStart.getHour();
            int minute = timePickerStart.getMinute();
            PrefsHelper.setStartTime(this, hour, minute);

            // Enable blocking and launch service
            PrefsHelper.setBlockingEnabled(this, true);
            Intent serviceIntent = new Intent(this, BlockService.class);
            ContextCompat.startForegroundService(this, serviceIntent);

            Toast.makeText(this, "FocusBlocker Started!", Toast.LENGTH_SHORT).show();
        } else {
            // Disable blocking and stop service
            PrefsHelper.setBlockingEnabled(this, false);
            Intent serviceIntent = new Intent(this, BlockService.class);
            stopService(serviceIntent);

            Toast.makeText(this, "FocusBlocker Stopped!", Toast.LENGTH_SHORT).show();
        }

        refreshBlockingButtonUI();
        updateScheduleStatusDisplay();
    }

    private void refreshBlockingButtonUI() {
        boolean isEnabled = PrefsHelper.isBlockingEnabled(this);
        if (isEnabled) {
            btnToggleBlocking.setText(R.string.stop_blocking);
            btnToggleBlocking.setBackgroundColor(Color.parseColor("#D32F2F")); // Red
            tvBlockingStatus.setText("Service Status: Active (Monitoring)");
            tvBlockingStatus.setTextColor(Color.parseColor("#388E3C")); // Green
        } else {
            btnToggleBlocking.setText(R.string.start_blocking);
            btnToggleBlocking.setBackgroundColor(Color.parseColor("#1E88E5")); // Blue
            tvBlockingStatus.setText("Service Status: Inactive");
            tvBlockingStatus.setTextColor(Color.parseColor("#D32F2F")); // Red
        }
    }

    /**
     * Requirement: Refresh permission statuses in onResume()
     */
    private void refreshPermissionStatuses() {
        // 1. Usage Access
        boolean usageGranted = hasUsageAccessPermission();
        if (usageGranted) {
            tvUsageStatus.setText("Granted");
            tvUsageStatus.setTextColor(Color.parseColor("#388E3C"));
            btnGrantUsage.setEnabled(false);
        } else {
            tvUsageStatus.setText("Not Granted (Required)");
            tvUsageStatus.setTextColor(Color.parseColor("#D32F2F"));
            btnGrantUsage.setEnabled(true);
        }

        // 2. Overlay Permission
        boolean overlayGranted = hasOverlayPermission();
        if (overlayGranted) {
            tvOverlayStatus.setText("Granted");
            tvOverlayStatus.setTextColor(Color.parseColor("#388E3C"));
            btnGrantOverlay.setEnabled(false);
        } else {
            tvOverlayStatus.setText("Not Granted (Required)");
            tvOverlayStatus.setTextColor(Color.parseColor("#D32F2F"));
            btnGrantOverlay.setEnabled(true);
        }

        // 3. Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            layoutNotificationPermission.setVisibility(View.VISIBLE);
            boolean notificationGranted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;

            if (notificationGranted) {
                tvNotificationStatus.setText("Granted");
                tvNotificationStatus.setTextColor(Color.parseColor("#388E3C"));
                btnGrantNotification.setEnabled(false);
            } else {
                tvNotificationStatus.setText("Not Granted");
                tvNotificationStatus.setTextColor(Color.parseColor("#D32F2F"));
                btnGrantNotification.setEnabled(true);
            }
        } else {
            layoutNotificationPermission.setVisibility(View.GONE);
        }
    }

    /**
     * Viva Concept: Checking AppOpsManager OPSTR_GET_USAGE_STATS
     */
    private boolean hasUsageAccessPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Viva Concept: Checking Settings.canDrawOverlays
     */
    private boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    /**
     * Requirement: Request POST_NOTIFICATIONS at runtime on Android 13+
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_NOTIFICATION) {
            refreshPermissionStatuses();
        }
    }

    /**
     * Requirement: Live countdown of remaining revoke time when one is active
     */
    private void updateRevokeCountdown() {
        long revokeUntil = PrefsHelper.getRevokeUntil(this);
        long now = System.currentTimeMillis();

        if (revokeUntil > now) {
            long remainingMillis = revokeUntil - now;
            long remainingSeconds = remainingMillis / 1000;
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;

            String timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
            tvRevokeCountdown.setText("Temporary Pause Active: " + timeString + " remaining");
            tvRevokeCountdown.setTextColor(Color.parseColor("#F57C00")); // Orange
        } else {
            tvRevokeCountdown.setText("No active pause (Blocking is active)");
            tvRevokeCountdown.setTextColor(Color.parseColor("#757575")); // Gray
        }
    }

    /**
     * Updates the real-time schedule status showing whether the current clock time
     * has passed the configured block start time.
     */
    private void updateScheduleStatusDisplay() {
        if (tvScheduleStatus == null) return;

        int startHour = PrefsHelper.getStartHour(this);
        int startMinute = PrefsHelper.getStartMinute(this);

        Calendar scheduledCal = Calendar.getInstance();
        scheduledCal.set(Calendar.HOUR_OF_DAY, startHour);
        scheduledCal.set(Calendar.MINUTE, startMinute);
        scheduledCal.set(Calendar.SECOND, 0);

        SimpleDateFormat timeFormat12 = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String formattedStartTime = timeFormat12.format(scheduledCal.getTime());

        Calendar now = Calendar.getInstance();
        int currentTotalMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startTotalMinutes = startHour * 60 + startMinute;

        boolean isBlockingEnabled = PrefsHelper.isBlockingEnabled(this);

        if (!isBlockingEnabled) {
            tvScheduleStatus.setText("⏰ Scheduled Start: " + formattedStartTime
                    + "\nStatus: Service is STOPPED. Tap 'Start Blocking' to activate.");
            tvScheduleStatus.setTextColor(Color.parseColor("#616161"));
            tvScheduleStatus.setBackgroundColor(Color.parseColor("#EEEEEE"));
        } else {
            if (currentTotalMinutes < startTotalMinutes) {
                int diffMinutes = startTotalMinutes - currentTotalMinutes;
                tvScheduleStatus.setText("⏰ Scheduled Start: " + formattedStartTime
                        + "\nStatus: WAITING ⏳ (Blocking begins in " + diffMinutes + " minute" + (diffMinutes > 1 ? "s" : "") + ")");
                tvScheduleStatus.setTextColor(Color.parseColor("#E65100")); // Dark Orange
                tvScheduleStatus.setBackgroundColor(Color.parseColor("#FFF3E0")); // Light Orange
            } else {
                tvScheduleStatus.setText("⏰ Scheduled Start: " + formattedStartTime
                        + "\nStatus: BLOCKING ACTIVE 🔒 (Current time is past " + formattedStartTime + ")");
                tvScheduleStatus.setTextColor(Color.parseColor("#1B5E20")); // Dark Green
                tvScheduleStatus.setBackgroundColor(Color.parseColor("#E8F5E9")); // Light Green
            }
        }
    }
}
