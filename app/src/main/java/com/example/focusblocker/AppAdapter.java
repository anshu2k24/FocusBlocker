package com.example.focusblocker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * AppAdapter: RecyclerView Adapter displaying installed applications.
 * Viva Concept: RecyclerView reuses (recycles) views to minimize memory allocation
 * when displaying long scrollable lists.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count);
    }

    private final List<AppInfoModel> appList;
    private final OnSelectionChangedListener selectionChangedListener;

    public AppAdapter(List<AppInfoModel> appList, OnSelectionChangedListener listener) {
        this.appList = appList;
        this.selectionChangedListener = listener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfoModel app = appList.get(position);

        holder.tvAppName.setText(app.getAppName());
        holder.tvPackageName.setText(app.getPackageName());
        holder.ivAppIcon.setImageDrawable(app.getIcon());
        holder.tvGameBadge.setVisibility(app.isGame() ? View.VISIBLE : View.GONE);

        // Remove listener before setting checked status to avoid recursive triggers
        holder.cbSelectApp.setOnCheckedChangeListener(null);
        holder.cbSelectApp.setChecked(app.isChecked());

        holder.cbSelectApp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.setChecked(isChecked);
            notifySelectionChange();
        });

        // Clicking anywhere on the item toggles the checkbox
        holder.itemView.setOnClickListener(v -> {
            boolean newState = !app.isChecked();
            app.setChecked(newState);
            holder.cbSelectApp.setChecked(newState);
            notifySelectionChange();
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    private void notifySelectionChange() {
        if (selectionChangedListener != null) {
            int selectedCount = 0;
            for (AppInfoModel app : appList) {
                if (app.isChecked()) {
                    selectedCount++;
                }
            }
            selectionChangedListener.onSelectionChanged(selectedCount);
        }
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        TextView tvGameBadge;
        CheckBox cbSelectApp;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            tvGameBadge = itemView.findViewById(R.id.tvGameBadge);
            cbSelectApp = itemView.findViewById(R.id.cbSelectApp);
        }
    }
}
