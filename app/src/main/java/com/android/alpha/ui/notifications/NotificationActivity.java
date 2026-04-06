package com.android.alpha.ui.notifications;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Activity untuk menampilkan daftar aktivitas/notifikasi pengguna.
 * Mendukung pembaruan real-time via listener dan hapus semua notifikasi.
 */
public class NotificationActivity extends AppCompatActivity {

    // --- Fields ---

    private NotificationAdapter adapter;
    private UserSession.ActivityListener activityListener;
    private RecyclerView rvActivities;
    private View emptyActivity;

    // --- Lifecycle ---

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        setupToolbar();
        setupRecyclerView();
        loadInitialActivities();
        setupActivityListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.submitList(new ArrayList<>(adapter.getCurrentList()), this::updateEmptyState);
        UserSession.getInstance().notifyBadgeCleared();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activityListener != null)
            UserSession.getInstance().removeActivityListener();
    }

    // --- UI Setup ---

    private int getAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private Typeface getFont() {
        try {
            return androidx.core.content.res.ResourcesCompat.getFont(
                    this, R.font.linottesemibold);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Typeface tf = getFont();

        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View v = toolbar.getChildAt(i);
            if (v instanceof TextView) {
                ((TextView) v).setTypeface(tf);
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false); // judul ditampilkan via TextView
        }

        Objects.requireNonNull(toolbar.getNavigationIcon())
                .setTint(getAttrColor(R.attr.text_color));
    }

    private void setupRecyclerView() {
        rvActivities = findViewById(R.id.rvActivities);
        emptyActivity = findViewById(R.id.emptyActivity);

        rvActivities.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        rvActivities.setAdapter(adapter);
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getCurrentList().isEmpty();
        emptyActivity.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvActivities.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // --- Data Management ---

    private void loadInitialActivities() {
        adapter.submitList(
                new ArrayList<>(UserSession.getInstance().getActivities()),
                this::updateEmptyState);
    }

    private void setupActivityListener() {
        activityListener = item -> runOnUiThread(() -> {
            List<ActivityItem> current = new ArrayList<>(adapter.getCurrentList());
            current.add(0, item);
            adapter.submitList(current, this::updateEmptyState);
        });

        UserSession.getInstance().addActivityListener(activityListener);
    }

    private void clearAllNotifications() {
        UserSession.getInstance().clearActivities();
        adapter.submitList(new ArrayList<>(), this::updateEmptyState);
    }

    // --- Delete Confirmation Dialog ---

    /**
     * Tampilkan popup konfirmasi hapus semua notifikasi.
     * Menggunakan layout dialog_delete_activity.xml.
     */
    private void showDeleteConfirmationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_delete_activity);

        // Buat background window transparan agar rounded corner drawable terlihat
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            // Margin kiri & kanan agar dialog tidak full width
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            dialog.getWindow().setAttributes(params);
        }

        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);
        MaterialButton btnDelete = dialog.findViewById(R.id.btnDelete);

        // Tutup dialog tanpa aksi
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Hapus semua notifikasi lalu tutup dialog
        btnDelete.setOnClickListener(v -> {
            clearAllNotifications();
            dialog.dismiss();
        });

        dialog.show();
    }

    // --- Menu & Action Handling ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notifications, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        // Tampilkan dialog konfirmasi saat icon delete ditekan
        if (id == R.id.action_delete) {
            showDeleteConfirmationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}