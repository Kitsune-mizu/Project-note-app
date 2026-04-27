package com.android.kitsune.ui.home;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.android.kitsune.MainActivity;
import com.android.kitsune.R;
import com.android.kitsune.data.local.NoteStorage;
import com.android.kitsune.ui.common.Refreshable;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.data.local.UserStorageManager;
import com.android.kitsune.ui.notes.EditNoteActivity;
import com.android.kitsune.ui.notes.NoteModel;
import com.android.kitsune.ui.notes.NotesActivity;
import com.android.kitsune.utils.ShimmerHelper;
import com.android.kitsune.utils.DialogUtils;
import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment implements
        UserSession.UserSessionListener,
        UserSession.ActivityListener,
        Refreshable {

    // --- UI Components ---
    private ShimmerFrameLayout shimmerLayout;
    private LottieAnimationView lottieWelcome;
    private TextView tvGreeting, tvUsername, tvDateTime, tvActiveDays, tvViewAll;
    private LinearLayout activityContainer, emptyActivity;
    private SwipeRefreshLayout swipeRefreshLayout;

    // --- Data and Utilities ---
    // private SharedPreferences prefs; // DIHAPUS: Diganti dengan UserStorageManager
    private final List<ActivityItem> activityList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale.getDefault()); // FIX: Ganti ke Locale.getDefault()
    private NoteStorage noteStorage;
    private LinearLayout memoContainer, emptyMemo;

    // Runnable to update time every minute
    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            updateDateTime();
            handler.postDelayed(this, 60000); // 1 minute
        }
    };

    public HomeFragment() {}

    // --- Lifecycle Methods ---

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);

        noteStorage = NoteStorage.getInstance(requireContext());

        memoContainer = view.findViewById(R.id.memoPreviewContainer);
        emptyMemo = view.findViewById(R.id.emptyMemo);

        setupLottieAnimation();
        setupSwipeRefresh();
        setupBackPressedHandler();
        registerListeners();

        // Initial loading with Shimmer
        View scrollView = view.findViewById(R.id.scrollViewHome);
        ShimmerHelper.show(shimmerLayout, scrollView);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            loadAllData();
            ShimmerHelper.hide(shimmerLayout, scrollView);
        }, 1200);

        handler.post(timeUpdater);
        setupClickListeners(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(timeUpdater);
        handler.removeCallbacksAndMessages(null);
        unregisterListeners();
    }

    // --- Setup and Initialization ---

    private void initializeViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = view.findViewById(R.id.shimmerLayout);
        lottieWelcome = view.findViewById(R.id.lottieWelcome);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvUsername = view.findViewById(R.id.tvUsername);
        tvDateTime = view.findViewById(R.id.tvDateTime);
        tvActiveDays = view.findViewById(R.id.tvActiveDays);
        tvViewAll = view.findViewById(R.id.tvViewAll);
        activityContainer = view.findViewById(R.id.activityContainer);
        emptyActivity = view.findViewById(R.id.emptyActivity);
    }

    private void setupLottieAnimation() {
        lottieWelcome.setAnimation(R.raw.welcome_animation);
        lottieWelcome.setRepeatCount(LottieDrawable.INFINITE);
        lottieWelcome.playAnimation();
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    private void setupClickListeners(View view) {

        tvViewAll.setOnClickListener(v -> openAllActivities());

        view.findViewById(R.id.tvViewAllNotes).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotesActivity.class))
        );
    }

    private void setupBackPressedHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        DialogUtils.showConfirmDialog(
                                requireContext(),
                                getString(R.string.dialog_exit_title), // FIX: String Resource
                                getString(R.string.dialog_exit_message), // FIX: String Resource
                                getString(R.string.action_exit), // FIX: String Resource
                                getString(R.string.action_cancel), // FIX: String Resource
                                () -> requireActivity().finishAffinity(),
                                null
                        );
                    }
                }
        );
    }

    private void registerListeners() {
        UserSession.getInstance().addListener(this);
        UserSession.getInstance().addActivityListener(this);
    }

    private void unregisterListeners() {
        UserSession.getInstance().removeListener(this);
        UserSession.getInstance().removeActivityListener(this);
    }

    // --- Data Loading and UI Updates ---

    private void loadAllData() {
        if (!isAdded() || getContext() == null) return; // pastikan fragment masih aktif

        try {
            loadUserData();
            loadActivityHistory();
            loadMemoPreview();
            updateDateTime();
        } catch (Exception e) {
            Log.e("HomeFragment", "Error loading all data", e);
        }
    }

    public void loadUserData() {
        if (!isAdded() || getContext() == null) return; // FIX: pastikan fragment masih attached

        String username = UserSession.getInstance().getUsername();
        String fullName = username != null ? username : getString(R.string.title_guest);

        try {
            UserSession.UserData userData = UserSession.getInstance().getUserData(username);
            if (userData != null) {
                Context context = getContext();
                if (context != null) {
                    UserStorageManager storageManager = UserStorageManager.getInstance(context);
                    JSONObject profileJson = storageManager.loadUserProfile(userData.userId);

                    if (profileJson != null) {
                        fullName = profileJson.optString("username", username);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "Error loading profile from storage", e);
        }

        if (!isAdded()) return; // jaga-jaga kalau fragment detached di tengah proses

        tvUsername.setText(fullName);
        updateGreeting();

        UserSession.getInstance().setFirstLoginIfNotExists();
        int activeDays = UserSession.getInstance().getActiveDays();
        tvActiveDays.setText(getString(R.string.active_days_format, activeDays));
    }

    private void updateGreeting() {
        if (!isAdded() || getContext() == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greetingResId;

        if (hour >= 5 && hour < 12) greetingResId = R.string.greeting_morning;
        else if (hour >= 12 && hour < 15) greetingResId = R.string.greeting_afternoon;
        else if (hour >= 15 && hour < 19) greetingResId = R.string.greeting_evening;
        else greetingResId = R.string.greeting_night;

        tvGreeting.setText(getString(greetingResId));
    }

    private void updateDateTime() {
        tvDateTime.setText(dateFormat.format(new Date()));
    }

    private void loadActivityHistory() {
        List<ActivityItem> savedActivities = UserSession.getInstance().getActivities();
        activityList.clear();
        activityList.addAll(savedActivities);

        // Ensure at least one login activity exists on first run
        if (activityList.isEmpty() && UserSession.getInstance().isLoggedIn()) {
            UserSession.getInstance().addLoginActivity();
        }

        refreshActivityList();
    }

    private void refreshActivityList() {
        activityContainer.removeAllViews();
        if (activityList.isEmpty()) {
            emptyActivity.setVisibility(View.VISIBLE);
            return;
        }
        emptyActivity.setVisibility(View.GONE);

        // Display a maximum of 5 recent activities
        int count = Math.min(activityList.size(), 5);
        for (int i = 0; i < count; i++) {
            ActivityItem item = activityList.get(i);
            View itemView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_activity, activityContainer, false);

            TextView tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            TextView tvDesc = itemView.findViewById(R.id.tvActivityDesc);
            TextView tvTime = itemView.findViewById(R.id.tvActivityTime);

            int titleResId = item.getTitleResId();
            if (titleResId != 0) {
                try {
                    tvTitle.setText(getString(titleResId));
                } catch (Resources.NotFoundException e) {
                    tvTitle.setText(""); // fallback
                }
            } else {
                tvTitle.setText("");
            }

            int descResId = item.getDescriptionResId();
            if (descResId != 0) {
                try {
                    tvDesc.setText(getString(descResId));
                } catch (Resources.NotFoundException e) {
                    tvDesc.setText("");
                }
            } else {
                tvDesc.setText("");
            }

            tvTime.setText(getRelativeTime(item.getTimestamp()));

            activityContainer.addView(itemView);
        }
    }

    private String getRelativeTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) return getString(R.string.time_just_now);
        else if (minutes < 60) return getString(R.string.time_minutes_ago, minutes);
        else if (hours < 24) return getString(R.string.time_hours_ago, hours);
        else return getString(R.string.time_days_ago, days);
    }

    private void openAllActivities() {
        Intent intent = new Intent(requireContext(), NotificationActivity.class);
        startActivity(intent);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideNotificationBadge();
        }
    }

    // --- Listener Implementations ---

    @Override
    public void onProfileUpdated() {
        loadUserData();
    }

    @Override
    public void onNewActivity(ActivityItem item) {
        if (item == null) return;
        activityList.add(0, item);
        // Keep the list size manageable (e.g., max 10 for memory/performance)
        if (activityList.size() > 10)
            activityList.subList(10, activityList.size()).clear();
        refreshActivityList();

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showNotificationBadge();
        }
    }

    // --- Refreshable Implementation ---

    @Override
    public void onResume() {
        super.onResume();

        // Update toolbar title sesuai bahasa
        if (getActivity() instanceof MainActivity) {
            String homeTitle = getString(R.string.menu_title_home);
            ((MainActivity) getActivity()).setToolbarTitle(homeTitle);
        }

        loadMemoPreview();
    }

    @Override
    public void onRefreshRequested() {
        View scrollView = requireView().findViewById(R.id.scrollViewHome);

        ShimmerHelper.show(shimmerLayout, scrollView);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                loadAllData();
                Toast.makeText(requireContext(), R.string.toast_home_updated, Toast.LENGTH_SHORT).show(); // FIX: String Resource
            } catch (Exception e) {
                Log.e("HomeFragment", "Error refreshing data", e);
            } finally {
                ShimmerHelper.hide(shimmerLayout, scrollView);
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        }, 1200);

        loadMemoPreview();
    }

    // Notes
    private void loadMemoPreview() {
        if (!isAdded()) return;

        ArrayList<NoteModel> notes = noteStorage.getNotes();

        notes.sort((a, b) -> Boolean.compare(b.pinned, a.pinned));

        memoContainer.removeAllViews();

        if (notes.isEmpty()) {
            emptyMemo.setVisibility(View.VISIBLE);
            memoContainer.setVisibility(View.GONE);
            return;
        }

        emptyMemo.setVisibility(View.GONE);
        memoContainer.setVisibility(View.VISIBLE);

        int limit = Math.min(notes.size(), 3);

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < limit; i++) {
            NoteModel n = notes.get(i);

            View v = inflater.inflate(R.layout.item_note, memoContainer, false);

            ((TextView) v.findViewById(R.id.tvNoteTitle)).setText(n.title);
            ((TextView) v.findViewById(R.id.tvNoteContent)).setText(n.content);
            ((TextView) v.findViewById(R.id.tvNoteDate)).setText(n.date);

            int id = n.id;
            v.setOnClickListener(vi -> {
                Intent i2 = new Intent(requireContext(), EditNoteActivity.class);
                i2.putExtra("noteId", id);
                startActivity(i2);
            });

            memoContainer.addView(v);
        }
    }

}