package com.android.kitsune.ui.home;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationActivity extends AppCompatActivity {

    private NotificationAdapter adapter;
    private UserSession.ActivityListener activityListener;

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
        List<ActivityItem> current = new ArrayList<>(adapter.getCurrentList());
        adapter.submitList(current);
        UserSession.getInstance().notifyBadgeCleared();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activityListener != null) {
            UserSession.getInstance().removeActivityListener(activityListener);
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.notifications_title);
            toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onPrimary));
        }
        Objects.requireNonNull(toolbar.getNavigationIcon()).setTint(
                ContextCompat.getColor(this, R.color.md_theme_light_onSurface)
        );
    }



    private void setupRecyclerView() {
        RecyclerView rvActivities = findViewById(R.id.rvActivities);
        rvActivities.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        rvActivities.setAdapter(adapter);
    }

    private void loadInitialActivities() {
        adapter.submitList(new ArrayList<>(UserSession.getInstance().getActivities()));
    }

    private void setupActivityListener() {
        activityListener = item -> runOnUiThread(() -> {
            List<ActivityItem> current = new ArrayList<>(adapter.getCurrentList());
            current.add(0, item);
            adapter.submitList(current);
        });
        UserSession.getInstance().addActivityListener(activityListener);
    }

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
        } else if (id == R.id.action_clear_all) {
            clearAllNotifications();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearAllNotifications() {
        UserSession.getInstance().clearActivities();
        adapter.submitList(new ArrayList<>());
    }
}
