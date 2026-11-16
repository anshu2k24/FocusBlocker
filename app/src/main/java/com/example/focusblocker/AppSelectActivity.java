package com.example.focusblocker;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppSelectActivity: Activity to display installed launchable apps in a RecyclerView.
 * Viva Concept: Demonstrates querying the Android PackageManager for installed apps,
 * classifying apps (e.g. CATEGORY_GAME), and saving user choices in SharedPreferences.
 */
public class AppSelectActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvAppCount;
    private Button btnSelectAllGames;
    private Button btnSaveSelection;

    private AppAdapter adapter;
    private final List<AppInfoModel> appList = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Select Apps to Block");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
        loadInstalledApps();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewApps);
        progressBar = findViewById(R.id.progressBar);
        tvAppCount = findViewById(R.id.tvAppCount);
        btnSelectAllGames = findViewById(R.id.btnSelectAllGames);
        btnSaveSelection = findViewById(R.id.btnSaveSelection);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(appList, this::updateCountDisplay);
        recyclerView.setAdapter(adapter);

        btnSelectAllGames.setOnClickListener(v -> selectAllGames());
        btnSaveSelection.setOnClickListener(v -> saveSelections());
    }

    /**
     * Loads all launchable apps in a background thread to prevent UI stutter.
     */
    private void loadInstalledApps() {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            Set<String> alreadyBlocked = PrefsHelper.getBlockedPackages(AppSelectActivity.this);
            boolean initialSetupDone = PrefsHelper.isInitialSetupDone(AppSelectActivity.this);

            List<AppInfoModel> loadedList = new ArrayList<>();
            String myPackage = getPackageName();

            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo == null) continue;
                String pkgName = ri.activityInfo.packageName;

                // Never include our own app
                if (myPackage.equals(pkgName)) continue;

                String appName = ri.loadLabel(pm).toString();
                Drawable icon = ri.loadIcon(pm);

                boolean isGame = false;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        isGame = (ai.category == ApplicationInfo.CATEGORY_GAME);
                    }
                    if (!isGame) {
                        isGame = ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {}

                // Checkbox state:
                // If previously saved, check if in alreadyBlocked.
                // If first time ever opening, pre-check Instagram and Facebook as requested.
                boolean isChecked;
                if (initialSetupDone) {
                    isChecked = alreadyBlocked.contains(pkgName);
                } else {
                    isChecked = alreadyBlocked.contains(pkgName)
                            || "com.instagram.android".equals(pkgName)
                            || "com.facebook.katana".equals(pkgName);
                }

                loadedList.add(new AppInfoModel(appName, pkgName, icon, isGame, isChecked));
            }

            // Sort alphabetically by app name
            Collections.sort(loadedList, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                appList.clear();
                appList.addAll(loadedList);
                adapter.notifyDataSetChanged();

                // Mark initial setup as completed
                if (!initialSetupDone) {
                    PrefsHelper.setInitialSetupDone(AppSelectActivity.this, true);
                    // Also immediately persist the initial pre-checked state
                    saveCurrentlyCheckedPackages();
                }

                int checkedCount = 0;
                for (AppInfoModel m : appList) {
                    if (m.isChecked()) checkedCount++;
                }
                updateCountDisplay(checkedCount);
            });
        });
    }

    /**
     * Requirement: A "Select all games" button using ApplicationInfo.CATEGORY_GAME
     */
    private void selectAllGames() {
        int gamesSelected = 0;
        for (AppInfoModel app : appList) {
            if (app.isGame()) {
                app.setChecked(true);
                gamesSelected++;
            }
        }
        adapter.notifyDataSetChanged();

        int totalChecked = 0;
        for (AppInfoModel app : appList) {
            if (app.isChecked()) totalChecked++;
        }
        updateCountDisplay(totalChecked);

        Toast.makeText(this, "Selected " + gamesSelected + " games", Toast.LENGTH_SHORT).show();
    }

    private void updateCountDisplay(int count) {
        tvAppCount.setText("Selected apps to block: " + count);
    }

    private Set<String> saveCurrentlyCheckedPackages() {
        Set<String> selected = new HashSet<>();
        for (AppInfoModel app : appList) {
            if (app.isChecked()) {
                selected.add(app.getPackageName());
            }
        }
        PrefsHelper.setBlockedPackages(this, selected);
        return selected;
    }

    private void saveSelections() {
        Set<String> selected = saveCurrentlyCheckedPackages();
        Toast.makeText(this, "Saved " + selected.size() + " apps to block", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
