package com.example.focusblocker;

import android.graphics.drawable.Drawable;

/**
 * AppInfoModel: POJO (Plain Old Java Object) to hold an installed application's
 * metadata for display in the RecyclerView list.
 */
public class AppInfoModel {
    private final String appName;
    private final String packageName;
    private final Drawable icon;
    private final boolean isGame;
    private boolean isChecked;

    public AppInfoModel(String appName, String packageName, Drawable icon, boolean isGame, boolean isChecked) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isGame = isGame;
        this.isChecked = isChecked;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isGame() {
        return isGame;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }
}
