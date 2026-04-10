package com.android.alpha.ui.main;

import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.android.alpha.R;
import com.android.alpha.base.BaseActivity;
import com.android.alpha.data.local.UserStorageManager;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.common.Refreshable;
import com.android.alpha.ui.notes.EditNoteActivity;
import com.android.alpha.ui.notes.Note;
import com.android.alpha.ui.notes.NoteActivity;
import com.android.alpha.ui.notes.NoteAdapter;
import com.android.alpha.ui.notes.NoteViewModel;
import com.android.alpha.ui.notifications.ActivityItem;
import com.android.alpha.ui.notifications.NotificationActivity;
import com.android.alpha.ui.notifications.NotificationAdapter;
import com.android.alpha.utils.DialogUtils;
import com.facebook.shimmer.ShimmerFrameLayout;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The main home screen fragment displaying a greeting, active-days counter,
 * recent activity history, and a horizontal notes preview.
 * Implements session listeners to react to profile updates, new activities, and clears.
 * UPDATED: Shimmer hanya muncul saat load pertama, tidak saat swipe refresh.
 */
public class HomeFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider,
        UserSession.UserSessionListener,
        UserSession.ActivityListener,
        UserSession.ActivityClearedListener,
        Refreshable {

    // ─── TAG ───────────────────────────────────────────────────────────────────
    private final String TAG = "HomeFragment";

    // ─── VIEWS ─────────────────────────────────────────────────────────────────
    private ShimmerFrameLayout  shimmerLayout;
    private LottieAnimationView lottieWelcome;
    private TextView            tvGreeting, tvUsername, tvDateTime, tvActiveDays, tvViewAll;
    private RecyclerView        recyclerViewActivities;
    private NotificationAdapter activityAdapter;
    private LinearLayout        emptyActivity;
    private SwipeRefreshLayout  swipeRefreshLayout;
    private TextView            tvViewAllNotes;
    private RecyclerView        recyclerViewNotes;
    private LinearLayout        emptyNotes;

    // ─── DATA & ADAPTERS ───────────────────────────────────────────────────────
    private NoteViewModel              noteViewModel;
    private NoteAdapter                adapter;
    private final List<ActivityItem>   activityList = new ArrayList<>();
    private final Handler              handler      = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat     dateFormat   =
            new SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale.getDefault());

    // ─── STATE ─────────────────────────────────────────────────────────────────
    /** Flag untuk melacak apakah ini adalah load pertama kali */
    private boolean isFirstLoad = true;

    // ─── LAUNCHERS ─────────────────────────────────────────────────────────────
    private ActivityResultLauncher<Intent> noteLauncher;

    // ─── RUNNABLES ─────────────────────────────────────────────────────────────

    /** Updates the date/time display every 60 seconds while the fragment is active. */
    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            updateDateTime();
            handler.postDelayed(this, 60000);
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    /** Required empty constructor for fragment instantiation. */
    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    /** Initializes all views, components, listeners, and starts the shimmer load sequence. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        initUIComponents();
        registerListeners();

        setupActivityRecycler();
        setupRecyclerView();
        setupNoteLauncher();

        // SHIMMER HANYA SAAT LOAD PERTAMA
        if (isFirstLoad) {
            startShimmerInitialLoad(view.findViewById(R.id.scrollViewHome));
            isFirstLoad = false;
        } else {
            // Jika bukan first load, langsung load data tanpa shimmer
            loadAllData();
        }

        handler.post(timeUpdater);
    }

    /** Reloads notes every time the fragment becomes visible. */
    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) loadNotes();
    }

    /** Removes handler callbacks and unregisters session listeners to avoid memory leaks. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        UserSession.getInstance().setActivityClearedListener(null);
        unregisterListeners();
    }

    /** Returns the toolbar title string resource for this fragment. */
    @Override
    public int getToolbarTitleRes() { return R.string.menu_title_home; }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Binds all view references from the given root view. */
    private void initializeViews(View view) {
        swipeRefreshLayout     = view.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout          = view.findViewById(R.id.shimmerLayout);
        lottieWelcome          = view.findViewById(R.id.lottieWelcome);
        tvGreeting             = view.findViewById(R.id.tvGreeting);
        tvUsername             = view.findViewById(R.id.tvUsername);
        tvDateTime             = view.findViewById(R.id.tvDateTime);
        tvActiveDays           = view.findViewById(R.id.tvActiveDays);
        tvViewAll              = view.findViewById(R.id.tvViewAll);
        recyclerViewActivities = view.findViewById(R.id.recyclerViewActivities);
        emptyActivity          = view.findViewById(R.id.emptyActivity);
        tvViewAllNotes         = view.findViewById(R.id.tvViewAllNotes);
        recyclerViewNotes      = view.findViewById(R.id.recyclerViewNotes);
        emptyNotes             = view.findViewById(R.id.emptyNotes);

        ((BaseActivity) requireActivity()).applyFont(
                tvGreeting,
                tvUsername,
                tvDateTime,
                tvActiveDays,
                tvViewAll,
                tvViewAllNotes
        );
    }

    /** Sets up the Lottie animation, pull-to-refresh, back press, and click listeners. */
    private void initUIComponents() {
        setupLottie();
        setupRefresh();
        setupBackPress();
        setupClickListeners();
    }

    /** Configures and starts the looping welcome Lottie animation. */
    private void setupLottie() {
        lottieWelcome.setAnimation(R.raw.welcome_animation);
        lottieWelcome.setRepeatCount(LottieDrawable.INFINITE);
        lottieWelcome.playAnimation();
    }

    /** Wires the SwipeRefreshLayout to the refresh handler. */
    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

    /** Intercepts the back button to show an exit confirmation dialog. */
    private void setupBackPress() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        DialogUtils.showConfirmDialog(
                                requireContext(),
                                getString(R.string.dialog_exit_title),
                                getString(R.string.dialog_exit_message),
                                getString(R.string.action_exit),
                                getString(R.string.action_cancel),
                                () -> requireActivity().finishAffinity(),
                                null
                        );
                    }
                }
        );
    }

    /** Sets click listeners for "View All" activity and notes links. */
    private void setupClickListeners() {
        tvViewAll.setOnClickListener(v -> openAllActivities());
        tvViewAllNotes.setOnClickListener(v -> openAllNotes());
    }

    /** Registers this fragment as a listener for session, activity, and clear events. */
    private void registerListeners() {
        UserSession.getInstance().addListener(this);
        UserSession.getInstance().addActivityListener(this);
        UserSession.getInstance().setActivityClearedListener(this);
    }

    /** Placeholder for any future listener cleanup logic. */
    private void unregisterListeners() {}

    /** Clears the local activity list and refreshes the RecyclerView when all activities are cleared. */
    @Override
    public void onActivitiesCleared() {
        activityList.clear();
        refreshActivityList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTES & RECYCLERVIEW
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets up the horizontal notes RecyclerView, initializes the NoteViewModel,
     * and observes note changes to update the adapter and empty-state visibility.
     */
    private void setupRecyclerView() {
        adapter = new NoteAdapter(
                new ArrayList<>(),
                note -> openNoteDetail(note.getId()),
                new NoteAdapter.OnSelectionModeListener() {
                    @Override public void onSelectionModeChange(boolean active) {}
                    @Override public void onSelectionCountChange(int count) {}
                },
                true,
                false
        );

        recyclerViewNotes.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerViewNotes.setHasFixedSize(false);
        recyclerViewNotes.setAdapter(adapter);

        noteViewModel = new ViewModelProvider(requireActivity()).get(NoteViewModel.class);
        String userId = UserSession.getInstance()
                .getUserData(UserSession.getInstance().getUsername()).userId;
        noteViewModel.setUserId(userId);
        loadNotes();

        noteViewModel.getActiveNotes().observe(getViewLifecycleOwner(), notes -> {
            if (notes == null) return;

            List<Note> sorted = new ArrayList<>(notes);
            sorted.sort((n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
            adapter.updateNotes(sorted);

            boolean isEmpty = sorted.isEmpty();
            emptyNotes.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerViewNotes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    /** Triggers a ViewModel notes refresh if the ViewModel is initialized. */
    public void refreshNotes() {
        if (noteViewModel != null) noteViewModel.loadNotes(requireContext());
    }

    /** Loads and refreshes notes from the ViewModel. */
    private void loadNotes() {
        noteViewModel.loadNotes(requireContext());
        noteViewModel.refreshNotes(requireContext());
    }

    /** Registers the activity result launcher used to open and return from note editing. */
    private void setupNoteLauncher() {
        noteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> loadNotes()
        );
    }

    /** Opens the EditNoteActivity for the selected note using the note launcher. */
    private void openNoteDetail(String noteId) {
        Note selectedNote = noteViewModel.getNoteById(requireContext(), noteId);
        Intent intent = new Intent(requireContext(), EditNoteActivity.class);
        intent.putExtra("note_id", selectedNote.getId());
        noteLauncher.launch(intent);
    }

    /** Navigates to the full NoteActivity screen. */
    private void openAllNotes() {
        startActivity(new Intent(requireContext(), NoteActivity.class));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA LOADING & SHIMMER
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Shows the shimmer skeleton loader, then hides it and loads real data
     * after a 1.2-second delay.
     * HANYA DIPANGGIL SAAT LOAD PERTAMA KALI.
     */
    private void startShimmerInitialLoad(View scrollView) {
        shimmerLayout.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
        // JANGAN panggil shimmerLayout.startShimmer()
        handler.postDelayed(() -> {
            loadAllData();
            shimmerLayout.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
        }, 1200);
    }

    /** Loads user data, activity history, and updates the date/time display. */
    private void loadAllData() {
        try {
            loadUserData();
            loadActivityHistory();
            updateDateTime();
        } catch (Exception e) {
            Log.e(TAG, "Error loading data", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER DATA & GREETING
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Loads the current user's display name from their profile JSON,
     * updates the greeting, username label, and active-days counter.
     */
    public void loadUserData() {
        String username = Optional.ofNullable(UserSession.getInstance().getUsername())
                .orElse(getString(R.string.title_guest));

        try {
            UserSession.UserData userData = UserSession.getInstance().getUserData(username);
            if (userData != null && getContext() != null) {
                JSONObject profileJson = UserStorageManager.getInstance(getContext())
                        .loadUserProfile(userData.userId);
                if (profileJson != null) username = profileJson.optString("username", username);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading profile", e);
        }

        tvUsername.setText(username);
        updateGreeting();
        UserSession.getInstance().setFirstLoginIfNotExists();
        tvActiveDays.setText(getString(R.string.active_days_format,
                UserSession.getInstance().getActiveDays()));
    }

    /** Sets the greeting text based on the current hour of day. */
    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res  = (hour < 12) ? R.string.greeting_morning  :
                (hour < 15) ? R.string.greeting_afternoon :
                        (hour < 19) ? R.string.greeting_evening   :
                                R.string.greeting_night;
        tvGreeting.setText(getString(res));
    }

    /** Updates the date/time TextView with the current time using the configured date format. */
    private void updateDateTime() {
        tvDateTime.setText(dateFormat.format(new Date()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACTIVITY HISTORY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Populates the local activity list from the session, adds a login activity
     * if not already present, then refreshes the activity RecyclerView.
     */
    private void loadActivityHistory() {
        activityList.clear();
        activityList.addAll(UserSession.getInstance().getActivities());

        if (!UserSession.getInstance().hasAddedLoginActivity() && UserSession.getInstance().isLoggedIn()) {
            UserSession.getInstance().addLoginActivity();
            UserSession.getInstance().setAddedLoginActivity(true);
        }

        refreshActivityList();
    }

    /** Sets up the activity history RecyclerView with a vertical LinearLayoutManager. */
    private void setupActivityRecycler() {
        activityAdapter = new NotificationAdapter();
        recyclerViewActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewActivities.setNestedScrollingEnabled(false);
        recyclerViewActivities.setAdapter(activityAdapter);
    }

    /**
     * Submits the latest activity list (capped at 5 items) to the adapter,
     * toggling the empty-state view as needed.
     */
    private void refreshActivityList() {
        if (activityList.isEmpty()) {
            emptyActivity.setVisibility(View.VISIBLE);
            recyclerViewActivities.setVisibility(View.GONE);
            return;
        }

        emptyActivity.setVisibility(View.GONE);
        recyclerViewActivities.setVisibility(View.VISIBLE);

        List<ActivityItem> limited = activityList.subList(0, Math.min(activityList.size(), 5));
        activityAdapter.submitList(new ArrayList<>(limited));
    }

    /** Navigates to the full NotificationActivity and hides the notification badge. */
    private void openAllActivities() {
        startActivity(new Intent(requireContext(), NotificationActivity.class));
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).hideNotificationBadge();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SESSION LISTENERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Reloads user data when the session profile is updated. */
    @Override
    public void onProfileUpdated() { loadUserData(); }

    /**
     * Prepends the new activity item to the local list (capped at 10),
     * refreshes the RecyclerView, and shows the notification badge.
     */
    @Override
    public void onNewActivity(ActivityItem item) {
        if (item == null) return;

        activityList.add(0, item);
        if (activityList.size() > 10) activityList.subList(10, activityList.size()).clear();
        refreshActivityList();

        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showNotificationBadge();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REFRESHABLE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Handles pull-to-refresh: TANPA SHIMMER, langsung reload data.
     * Shimmer hanya muncul saat load pertama kali.
     */
    @Override
    public void onRefreshRequested() {
        // TIDAK ADA SHIMMER saat swipe refresh
        handler.postDelayed(() -> {
            try {
                noteViewModel.refreshNotes(requireContext());
                loadAllData();
                Toast.makeText(requireContext(), R.string.toast_home_updated, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Refresh error", e);
            } finally {
                swipeRefreshLayout.setRefreshing(false);
            }
        }, 800); // Delay lebih pendek karena tidak ada shimmer
    }
}