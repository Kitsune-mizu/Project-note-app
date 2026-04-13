package com.android.alpha.ui.main;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
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

public class HomeFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider,
        UserSession.UserSessionListener,
        UserSession.ActivityListener,
        UserSession.ActivityClearedListener,
        Refreshable {

    // ─── Constants & Variables ───────────────────────────────────────────────

    private final String TAG = "HomeFragment";

    private ShimmerFrameLayout shimmerLayout;
    private LottieAnimationView lottieWelcome;
    private TextView tvGreeting, tvUsername, tvDateTime, tvActiveDays, tvViewAll;
    private RecyclerView recyclerViewActivities;
    private NotificationAdapter activityAdapter;
    private LinearLayout emptyActivity;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvViewAllNotes;
    private RecyclerView recyclerViewNotes;
    private LinearLayout emptyNotes;

    private NoteViewModel noteViewModel;
    private NoteAdapter adapter;
    private final List<ActivityItem> activityList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale.getDefault());

    private boolean isFirstLoad = true;

    // PERBAIKAN: Gunakan static boolean agar tidak ter-reset saat recreate() tema
    private static boolean hasShownGreetingPopup = false;

    private ActivityResultLauncher<Intent> noteLauncher;

    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            updateDateTime();
            handler.postDelayed(this, 60000);
        }
    };


    // ─── Lifecycle ───────────────────────────────────────────────────────────

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        initUIComponents();
        registerListeners();

        setupActivityRecycler();
        setupRecyclerView();
        setupNoteLauncher();

        if (isFirstLoad) {
            startShimmerInitialLoad(view.findViewById(R.id.scrollViewHome));
            isFirstLoad = false;
        } else {
            loadAllData();
        }

        handler.post(timeUpdater);
        checkAndShowGreetingPopup();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            loadNotes();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        UserSession.getInstance().setActivityClearedListener(null);
        unregisterListeners();
    }

    @Override
    public int getToolbarTitleRes() {
        return R.string.menu_title_home;
    }


    // ─── Initialization ──────────────────────────────────────────────────────

    private void initializeViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        shimmerLayout = view.findViewById(R.id.shimmerLayout);
        lottieWelcome = view.findViewById(R.id.lottieWelcome);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvUsername = view.findViewById(R.id.tvUsername);
        tvDateTime = view.findViewById(R.id.tvDateTime);
        tvActiveDays = view.findViewById(R.id.tvActiveDays);
        tvViewAll = view.findViewById(R.id.tvViewAll);
        recyclerViewActivities = view.findViewById(R.id.recyclerViewActivities);
        emptyActivity = view.findViewById(R.id.emptyActivity);
        tvViewAllNotes = view.findViewById(R.id.tvViewAllNotes);
        recyclerViewNotes = view.findViewById(R.id.recyclerViewNotes);
        emptyNotes = view.findViewById(R.id.emptyNotes);

        ((BaseActivity) requireActivity()).applyFont(
                tvGreeting,
                tvUsername,
                tvDateTime,
                tvActiveDays,
                tvViewAll,
                tvViewAllNotes
        );
    }

    private void initUIComponents() {
        setupLottie();
        setupRefresh();
        setupBackPress();
        setupClickListeners();
    }

    private void setupLottie() {
        updateLottieBasedOnTheme();
        lottieWelcome.setRepeatCount(LottieDrawable.INFINITE);
        lottieWelcome.playAnimation();
    }

    private void updateLottieBasedOnTheme() {
        boolean isDark = isDarkModeActive();
        if (isDark) {
            lottieWelcome.setAnimation(R.raw.underwater_ocean);
        } else {
            lottieWelcome.setAnimation(R.raw.welcome_animation);
        }
    }

    private boolean isDarkModeActive() {
        int currentNightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void setupRefresh() {
        swipeRefreshLayout.setOnRefreshListener(this::onRefreshRequested);
    }

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

    private void setupClickListeners() {
        tvViewAll.setOnClickListener(v -> openAllActivities());
        tvViewAllNotes.setOnClickListener(v -> openAllNotes());
    }

    private void registerListeners() {
        UserSession.getInstance().addListener(this);
        UserSession.getInstance().addActivityListener(this);
        UserSession.getInstance().setActivityClearedListener(this);
    }

    private void unregisterListeners() {}

    @Override
    public void onActivitiesCleared() {
        activityList.clear();
        refreshActivityList();
    }


    // ─── Notes & RecyclerView ────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new NoteAdapter(
                new ArrayList<>(),
                note -> openNoteDetail(note.getId()),
                new NoteAdapter.OnSelectionModeListener() {
                    @Override
                    public void onSelectionModeChange(boolean active) {}

                    @Override
                    public void onSelectionCountChange(int count) {}
                },
                true,
                false
        );

        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
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

    public void refreshNotes() {
        if (noteViewModel != null) {
            noteViewModel.loadNotes(requireContext());
        }
    }

    private void loadNotes() {
        noteViewModel.loadNotes(requireContext());
        noteViewModel.refreshNotes(requireContext());
    }

    private void setupNoteLauncher() {
        noteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> loadNotes()
        );
    }

    private void openNoteDetail(String noteId) {
        Note selectedNote = noteViewModel.getNoteById(requireContext(), noteId);
        Intent intent = new Intent(requireContext(), EditNoteActivity.class);
        intent.putExtra("note_id", selectedNote.getId());
        noteLauncher.launch(intent);
    }

    private void openAllNotes() {
        startActivity(new Intent(requireContext(), NoteActivity.class));
    }


    // ─── Data Loading & Shimmer ──────────────────────────────────────────────

    private void startShimmerInitialLoad(View scrollView) {
        shimmerLayout.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        handler.postDelayed(() -> {
            loadAllData();
            shimmerLayout.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
        }, 1200);
    }

    private void loadAllData() {
        try {
            loadUserData();
            loadActivityHistory();
            updateDateTime();
        } catch (Exception e) {
            Log.e(TAG, "Error loading data", e);
        }
    }


    // ─── User Data & Greeting ────────────────────────────────────────────────

    public void loadUserData() {
        String username = Optional.ofNullable(UserSession.getInstance().getUsername())
                .orElse(getString(R.string.title_guest));

        try {
            UserSession.UserData userData = UserSession.getInstance().getUserData(username);
            if (userData != null && getContext() != null) {
                JSONObject profileJson = UserStorageManager.getInstance(getContext())
                        .loadUserProfile(userData.userId);
                if (profileJson != null) {
                    username = profileJson.optString("username", username);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading profile", e);
        }

        tvUsername.setText(username);
        updateGreeting();

        UserSession.getInstance().setFirstLoginIfNotExists();
        tvActiveDays.setText(getString(R.string.active_days_format, UserSession.getInstance().getActiveDays()));
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;

        if (hour < 12) {
            res = R.string.greeting_morning;
        } else if (hour < 15) {
            res = R.string.greeting_afternoon;
        } else if (hour < 19) {
            res = R.string.greeting_evening;
        } else {
            res = R.string.greeting_night;
        }

        tvGreeting.setText(getString(res));
    }

    @SuppressLint("SetTextI18n")
    private void updateDateTime() {
        String date = dateFormat.format(new Date());
        String country = UserSession.getInstance().getDetectedCountryName();
        if (country != null && !country.isEmpty()) {
            tvDateTime.setText(date + " • " + country);
        } else {
            tvDateTime.setText(date);
        }
    }


    // ─── Activity History ────────────────────────────────────────────────────

    private void loadActivityHistory() {
        activityList.clear();
        activityList.addAll(UserSession.getInstance().getActivities());

        if (!UserSession.getInstance().hasAddedLoginActivity() && UserSession.getInstance().isLoggedIn()) {
            UserSession.getInstance().addLoginActivity();
            UserSession.getInstance().setAddedLoginActivity(true);
        }

        refreshActivityList();
    }

    private void setupActivityRecycler() {
        activityAdapter = new NotificationAdapter();
        recyclerViewActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewActivities.setNestedScrollingEnabled(false);
        recyclerViewActivities.setAdapter(activityAdapter);
    }

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

    private void openAllActivities() {
        startActivity(new Intent(requireContext(), NotificationActivity.class));
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideNotificationBadge();
        }
    }


    // ─── Session Listeners ───────────────────────────────────────────────────

    @Override
    public void onProfileUpdated() {
        loadUserData();
    }

    @Override
    public void onNewActivity(ActivityItem item) {
        if (item == null) return;

        activityList.add(0, item);
        if (activityList.size() > 10) {
            activityList.subList(10, activityList.size()).clear();
        }

        refreshActivityList();

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showNotificationBadge();
        }
    }


    // ─── Refreshable ─────────────────────────────────────────────────────────

    @Override
    public void onRefreshRequested() {
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
        }, 800);
    }

    // ─── Kitsune Greeting Popup ──────────────────────────────────────────────

    private void checkAndShowGreetingPopup() {
        // PERBAIKAN: Cek apakah popup sudah pernah muncul di sesi ini
        if (getActivity() == null || hasShownGreetingPopup) return;

        hasShownGreetingPopup = true; // Tandai sudah muncul agar tidak berulang

        handler.postDelayed(this::showKitsuneGreetingPopup, 3000);
    }

    private void showKitsuneGreetingPopup() {
        if (getContext() == null || getActivity() == null || getActivity().isFinishing()) return;

        Dialog dialog = new Dialog(requireContext(),
                android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_kitsune_greeting);
        dialog.setCancelable(true);

        androidx.cardview.widget.CardView popupContainer =
                dialog.findViewById(R.id.popupContainer);

        android.widget.ImageButton btnClose =
                dialog.findViewById(R.id.btnClosePopup);
        LinearLayout typingWrap =
                dialog.findViewById(R.id.typingIndicatorWrap);
        android.widget.LinearLayout row1 =
                dialog.findViewById(R.id.bubbleRow1);
        android.widget.LinearLayout row2 =
                dialog.findViewById(R.id.bubbleRow2);
        TextView tvGreetingMsg  = dialog.findViewById(R.id.tvGreetingMsg);
        TextView tvTipMsg       = dialog.findViewById(R.id.tvTipMsg);
        TextView tvTime1        = dialog.findViewById(R.id.tvTime1);
        TextView tvTime2        = dialog.findViewById(R.id.tvTime2);
        TextView tvDate         = dialog.findViewById(R.id.tvDateSeparator);

        // Date & time
        SimpleDateFormat dateFmt =
                new SimpleDateFormat("EEE, d MMM", new Locale("id"));
        tvDate.setText(dateFmt.format(new Date()));
        String timeNow = new SimpleDateFormat("HH:mm",
                Locale.getDefault()).format(new Date());

        // Pilih greeting sesuai jam
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greetingText;
        if      (hour < 12) greetingText = getString(R.string.popup_greeting_morning);
        else if (hour < 15) greetingText = getString(R.string.popup_greeting_afternoon);
        else if (hour < 19) greetingText = getString(R.string.popup_greeting_evening);
        else                greetingText = getString(R.string.popup_greeting_night);

        String[] tips = {
                getString(R.string.popup_message_1),
                getString(R.string.popup_message_2)
        };
        String tipText = tips[new Random().nextInt(tips.length)];

        // ANIMASI 1: Popup Card Meluncur dari Atas (-150f) Sambil Membesar (0.5f ke 1f)
        popupContainer.setAlpha(0f);
        popupContainer.setScaleX(0.5f); // Mulai dari kecil
        popupContainer.setScaleY(0.5f);
        popupContainer.setTranslationY(-150f); // Mulai dari atas
        popupContainer.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f) // Meluncur turun ke tengah (0f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        // ANIMASI 2: Typing Pertama Muncul
        handler.postDelayed(() -> {
            typingWrap.setVisibility(View.VISIBLE);
            typingWrap.setAlpha(0f);
            typingWrap.setScaleX(0.8f);
            typingWrap.setScaleY(0.8f);
            typingWrap.setPivotX(0f);
            typingWrap.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(350).setInterpolator(new OvershootInterpolator(1.0f)).start();
        }, 500);

        // ANIMASI 3: Ganti Typing dengan Pesan 1
        handler.postDelayed(() -> {
            typingWrap.setVisibility(View.GONE);

            tvGreetingMsg.setText(greetingText);
            tvTime1.setText(timeNow);

            row1.setVisibility(View.VISIBLE);
            row1.setAlpha(0f);
            row1.setScaleX(0.6f);
            row1.setScaleY(0.6f);
            row1.setPivotX(0f);
            row1.setPivotY(0f);
            row1.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }, 1900);

        // ANIMASI 4: Typing Kedua Muncul di Bawah Pesan 1
        handler.postDelayed(() -> {
            typingWrap.setVisibility(View.VISIBLE);
            typingWrap.setAlpha(0f);
            typingWrap.setScaleX(0.8f);
            typingWrap.setScaleY(0.8f);
            typingWrap.setPivotX(0f);
            typingWrap.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(350).setInterpolator(new OvershootInterpolator(1.0f)).start();
        }, 2900);

        // ANIMASI 5: Ganti Typing dengan Pesan 2
        handler.postDelayed(() -> {
            typingWrap.setVisibility(View.GONE);

            tvTipMsg.setText(tipText);
            tvTime2.setText(timeNow);

            row2.setVisibility(View.VISIBLE);
            row2.setAlpha(0f);
            row2.setScaleX(0.6f);
            row2.setScaleY(0.6f);
            row2.setPivotX(0f);
            row2.setPivotY(0f);
            row2.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }, 4300);

        // Tombol close dengan animasi keluar
        btnClose.setOnClickListener(v ->
                dismissPopupWithAnimation(dialog, popupContainer));

        // Tap di luar popup → tutup
        dialog.setOnCancelListener(d -> {
            handler.removeCallbacksAndMessages(null);
            handler.post(timeUpdater);
        });

        dialog.show();
    }

    private void dismissPopupWithAnimation(Dialog dialog,
                                           android.view.View container) {
        container.animate()
                .alpha(0f).scaleX(0.85f).scaleY(0.85f).translationY(15f)
                .setDuration(250)
                .withEndAction(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    handler.removeCallbacks(timeUpdater);
                    handler.post(timeUpdater);
                })
                .start();
    }
}