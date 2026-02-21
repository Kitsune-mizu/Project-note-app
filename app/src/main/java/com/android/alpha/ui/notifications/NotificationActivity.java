package com.android.alpha.ui.notifications;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Activity untuk menampilkan daftar aktivitas/notifikasi pengguna.
 * Mendukung pembaruan real-time via listener dan hapus semua notifikasi.
 */
public class NotificationActivity extends AppCompatActivity {

    // --- Fields ---

    // Adapter untuk daftar notifikasi
    private NotificationAdapter adapter;

    // Listener untuk menerima aktivitas baru secara real-time dari UserSession
    private UserSession.ActivityListener activityListener;

    // Komponen UI utama
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
        // Refresh tampilan list dan tandai notifikasi sebagai sudah dibaca
        adapter.submitList(new ArrayList<>(adapter.getCurrentList()), this::updateEmptyState);
        UserSession.getInstance().notifyBadgeCleared();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Lepas listener agar tidak terjadi memory leak
        if (activityListener != null)
            UserSession.getInstance().removeActivityListener();
    }

    // --- UI Setup ---

    /** Setup toolbar dengan tombol back dan judul halaman */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.notifications_title);
        }

        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onPrimary));
        Objects.requireNonNull(toolbar.getNavigationIcon())
                .setTint(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
    }

    /** Inisialisasi RecyclerView, empty state view, dan adapter */
    private void setupRecyclerView() {
        rvActivities = findViewById(R.id.rvActivities);
        emptyActivity = findViewById(R.id.emptyActivity);

        rvActivities.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        rvActivities.setAdapter(adapter);
    }

    /** Tampilkan empty state jika list kosong, sembunyikan jika ada data */
    private void updateEmptyState() {
        boolean isEmpty = adapter.getCurrentList().isEmpty();
        emptyActivity.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvActivities.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // --- Data Management ---

    /** Muat daftar aktivitas awal dari UserSession saat activity pertama kali dibuka */
    private void loadInitialActivities() {
        adapter.submitList(
                new ArrayList<>(UserSession.getInstance().getActivities()),
                this::updateEmptyState);
    }

    /**
     * Daftarkan listener ke UserSession untuk menerima aktivitas baru secara real-time.
     * Item baru ditambahkan di posisi paling atas list.
     */
    private void setupActivityListener() {
        activityListener = item -> runOnUiThread(() -> {
            List<ActivityItem> current = new ArrayList<>(adapter.getCurrentList());
            current.add(0, item);
            adapter.submitList(current, this::updateEmptyState);
        });

        UserSession.getInstance().addActivityListener(activityListener);
    }

    /** Hapus semua notifikasi dari session dan kosongkan list di UI */
    private void clearAllNotifications() {
        UserSession.getInstance().clearActivities();
        adapter.submitList(new ArrayList<>(), this::updateEmptyState);
    }

    // --- Menu & Action Handling ---

    /** Inflate menu dengan tombol "Clear All" */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notifications, menu);
        return true;
    }

    /**
     * Tangani aksi menu:
     * - home (back): tutup activity
     * - action_clear_all: hapus semua notifikasi
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        }

        if (id == R.id.action_clear_all) {
            clearAllNotifications();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}