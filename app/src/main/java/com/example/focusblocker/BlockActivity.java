package com.example.focusblocker;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * BlockActivity: Full-screen overlay activity shown when user attempts to access a blocked app.
 * Viva Concept: Demonstrates overriding Back press behavior to enforce app blocking,
 * launching home screen via Intent.CATEGORY_HOME, and setting temporary timer tokens
 * in SharedPreferences.
 */
public class BlockActivity extends AppCompatActivity {

    public static final String EXTRA_BLOCKED_PACKAGE = "extra_blocked_package";

    private TextView tvBlockedAppName;
    private Button btnGoHome;
    private Spinner spinnerRevokeDuration;
    private Button btnRevokeBlock;

    private static final int[] REVOKE_MINUTES = {5, 10, 15, 30};
    private static final String[] SPINNER_LABELS = {
            "5 minutes",
            "10 minutes",
            "15 minutes",
            "30 minutes"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block);

        initViews();
        displayBlockedAppInfo();
        setupBackPressInterceptor();
    }

    private void initViews() {
        tvBlockedAppName = findViewById(R.id.tvBlockedAppName);
        btnGoHome = findViewById(R.id.btnGoHome);
        spinnerRevokeDuration = findViewById(R.id.spinnerRevokeDuration);
        btnRevokeBlock = findViewById(R.id.btnRevokeBlock);

        // Populate Spinner with temporary revoke options (5, 10, 15, 30 mins)
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                SPINNER_LABELS
        );
        spinnerRevokeDuration.setAdapter(adapter);

        btnGoHome.setOnClickListener(v -> goHome());
        btnRevokeBlock.setOnClickListener(v -> confirmRevoke());
    }

    private void displayBlockedAppInfo() {
        String packageName = getIntent().getStringExtra(EXTRA_BLOCKED_PACKAGE);
        if (packageName == null || packageName.isEmpty()) {
            tvBlockedAppName.setText("This Application");
            return;
        }

        // Resolve friendly application name
        String appName = packageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            appName = pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException ignored) {}

        tvBlockedAppName.setText(appName);
    }

    /**
     * Requirement: Override back press so it cannot be bypassed.
     * Redirects user to home screen if they try to press back.
     */
    private void setupBackPressInterceptor() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goHome();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Redundant safeguard for older API compatibility
        goHome();
    }

    /**
     * Sends the user directly to the Android home screen launcher.
     */
    private void goHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
    }

    /**
     * Requirement: Save revokeUntil = now + minutes in SharedPreferences (max 30 mins),
     * show Toast, and finish.
     */
    private void confirmRevoke() {
        int selectedIndex = spinnerRevokeDuration.getSelectedItemPosition();
        if (selectedIndex < 0 || selectedIndex >= REVOKE_MINUTES.length) {
            selectedIndex = 0;
        }

        int minutes = REVOKE_MINUTES[selectedIndex];
        // Enforce maximum 30 minutes rule
        if (minutes > 30) {
            minutes = 30;
        }

        long now = System.currentTimeMillis();
        long revokeUntil = now + (minutes * 60 * 1000L);

        PrefsHelper.setRevokeUntil(this, revokeUntil);

        Toast.makeText(this, "Block revoked for " + minutes + " minutes! Enjoy your break.", Toast.LENGTH_LONG).show();
        finish();
    }
}
