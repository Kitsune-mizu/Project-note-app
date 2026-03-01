package com.android.alpha.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.local.UserStorageManager;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.geminichat.ChatSessionManager;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.ui.notifications.NotificationActivity;
import com.android.alpha.utils.DialogUtils;
import com.android.alpha.utils.LoadingDialog;
import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

/**
 * The main host activity that manages the navigation drawer, fragment navigation,
 * toolbar title, profile header, notification badge, and user session events.
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, UserSession.UserSessionListener {

    // ─── CONSTANTS ─────────────────────────────────────────────────────────────
    private static final int  PERMISSION_REQUEST_CODE = 101;
    private static final long FRAGMENT_LOAD_DELAY     = 600;
    private static final long LOGOUT_DELAY            = 1200;
    private final        String TAG                   = "MainActivity";

    // ─── UI COMPONENTS ─────────────────────────────────────────────────────────
    private DrawerLayout        drawerLayout;
    private NavigationView      navigationView;
    private Toolbar             toolbar;
    private ImageView           ivProfile;
    private LottieAnimationView lottieProfileNav;
    private TextView            tvUsername, tvUserEmail;
    private View                notificationBadge;

    // ─── UTILITIES ─────────────────────────────────────────────────────────────
    private LoadingDialog  loadingDialog;
    private final Handler  handler   = new Handler();
    private float          startX;
    private boolean        isSwiping = false;

    // ──────────────────────────────────────────────────────────────────────────
    // INTERFACE
    // ──────────────────────────────────────────────────────────────────────────

    /** Implemented by fragments to provide their toolbar title string resource. */
    public interface ToolbarTitleProvider {
        int getToolbarTitleRes();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    /** Applies the saved locale before the activity's context is attached. */
    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = "en";
        try {
            lang = UserSession.getInstance().getLanguage();
        } catch (IllegalStateException ignored) {}
        super.attachBaseContext(com.android.alpha.utils.LocaleHelper.setLocale(newBase, lang));
    }

    /** Initializes session, views, navigation, gesture handling, and loads the home fragment. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UserSession.init(this);
        UserSession session = getSession();
        session.addListener(mainSessionListener);
        session.removeOldActivities();

        initViews();
        setupToolbar();
        setupNavigationDrawer();
        setupGestureBack();
        setupFooter();
        setupBackStackListener();

        // Load default fragment
        showFragment(new HomeFragment(), "Home", false);

        if (getIntent().getBooleanExtra("show_badge", false)) showNotificationBadge();
        requestNotificationPermission();
        session.setBadgeListener(badgeSessionListener);
    }

    /** Reapplies language, updates toolbar title, and refreshes notes in HomeFragment if active. */
    @Override
    protected void onResume() {
        super.onResume();
        applyCurrentLanguage();
        updateToolbarTitleByFragment();

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof HomeFragment) ((HomeFragment) fragment).refreshNotes();
    }

    /** Removes session listeners to prevent memory leaks. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        UserSession session = getSession();
        if (session != null) session.removeListener();
    }

    /** Convenience method to get the current UserSession instance. */
    private UserSession getSession() { return UserSession.getInstance(); }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Binds the primary layout views and initializes the loading dialog. */
    private void initViews() {
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar        = findViewById(R.id.toolbar);
        loadingDialog  = new LoadingDialog(this);
    }

    /** Sets the toolbar as the activity's action bar. */
    private void setupToolbar() { setSupportActionBar(toolbar); }

    // ══════════════════════════════════════════════════════════════════════════
    // NAVIGATION DRAWER
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Configures the navigation drawer: sets the hamburger icon, binds the nav header views,
     * loads the user's profile, colors the logout menu item, and sets up the badge.
     */
    private void setupNavigationDrawer() {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        Drawable menuIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu);
        if (menuIcon != null) menuIcon.setTint(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
        getSupportActionBar().setHomeAsUpIndicator(menuIcon);

        navigationView.setNavigationItemSelectedListener(this);

        View header      = navigationView.getHeaderView(0);
        ivProfile        = header.findViewById(R.id.ivProfile);
        lottieProfileNav = header.findViewById(R.id.lottieProfileNav);
        tvUsername       = header.findViewById(R.id.tvUsername);
        tvUserEmail      = header.findViewById(R.id.tvUserEmail);

        updateNavHeaderProfile();
        colorLogoutItem();
        setupNotificationBadge();
    }

    /** Opens the navigation drawer when the home/hamburger toolbar button is tapped. */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Colors the logout menu item with the error/red theme color. */
    private void colorLogoutItem() {
        MenuItem logoutItem = navigationView.getMenu().findItem(R.id.nav_logout);
        SpannableString s   = new SpannableString(logoutItem.getTitle());
        s.setSpan(new ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.md_theme_light_error)), 0, s.length(), 0);
        logoutItem.setTitle(s);
    }

    /** Dynamically adds a footer TextView to the bottom of the navigation drawer. */
    private void setupFooter() {
        TextView footer = new TextView(this);
        footer.setText(getString(R.string.footer_text));
        footer.setTextSize(12);
        footer.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 40, 0, 40);

        NavigationView.LayoutParams params = new NavigationView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        footer.setLayoutParams(params);

        navigationView.addView(footer);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BACK GESTURE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Attaches a swipe-right-to-go-back touch listener to the fragment container.
     * Ignored when the current fragment is HomeFragment.
     */
    private void setupGestureBack() {
        View container = findViewById(R.id.fragment_container);
        container.setOnTouchListener((v, e) -> {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof HomeFragment) return false;

            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float delta = e.getX() - startX;
                    if (delta > 0) { isSwiping = true; v.setTranslationX(delta * 0.8f); }
                    return true;

                case MotionEvent.ACTION_UP:
                    float move = e.getX() - startX;
                    if (isSwiping && move > v.getWidth() / 3f) animateBack(v);
                    else                                        v.animate().translationX(0).setDuration(200).start();
                    if (Math.abs(move) < 10) v.performClick();
                    isSwiping = false;
                    return true;
            }
            return false;
        });
    }

    /** Animates the container sliding off-screen then triggers the back press. */
    private void animateBack(View v) {
        v.animate().translationX(v.getWidth()).setDuration(200).withEndAction(() -> {
            v.setTranslationX(0);
            getOnBackPressedDispatcher().onBackPressed();
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FRAGMENT NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Replaces the fragment container with the given fragment after a short loading delay.
     * Optionally adds the transaction to the back stack with a fade animation.
     */
    public void showFragment(Fragment fragment, String title, boolean addToBackStack) {
        showLoading();
        handler.postDelayed(() -> {
            if (addToBackStack) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, fragment, title)
                        .addToBackStack(title)
                        .commitAllowingStateLoss();
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment, title)
                        .commitAllowingStateLoss();
            }
            updateToolbarTitleByFragment();
            highlightActiveMenu(fragment);
            loadingDialog.dismiss();
        }, FRAGMENT_LOAD_DELAY);
    }

    /** Registers a back stack listener to keep the toolbar title in sync. */
    private void setupBackStackListener() {
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbarTitleByFragment);
    }

    /** Updates the toolbar title and active nav menu item based on the current fragment. */
    private void updateToolbarTitleByFragment() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f == null || getSupportActionBar() == null) return;
        if (f instanceof ToolbarTitleProvider)
            getSupportActionBar().setTitle(((ToolbarTitleProvider) f).getToolbarTitleRes());
        highlightActiveMenu(f);
    }

    /** Checks the appropriate nav menu item for the currently displayed fragment. */
    private void highlightActiveMenu(Fragment fragment) {
        int newId = R.id.nav_home;
        if (fragment instanceof ProfileFragment)  newId = R.id.nav_profile;
        else if (fragment instanceof SettingsFragment) newId = R.id.nav_settings;

        if (navigationView != null) {
            Menu menu = navigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                item.setChecked(item.getItemId() == newId);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWER MENU ITEM SELECTION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Handles drawer menu item clicks: logout, notifications, or fragment navigation.
     * Closes the drawer after each selection.
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) {
            showLogoutDialog();
        } else if (id == R.id.nav_notifications) {
            startActivity(new Intent(this, NotificationActivity.class));
            hideNotificationBadge();
        } else {
            Fragment fragment = getFragmentForMenu(id);
            if (fragment != null)
                showFragment(fragment, Objects.requireNonNull(item.getTitle()).toString(), id != R.id.nav_home);
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /** Returns the appropriate Fragment for the given navigation menu item ID, or null if unrecognized. */
    private Fragment getFragmentForMenu(int id) {
        if (id == R.id.nav_home)     return new HomeFragment();
        if (id == R.id.nav_profile)  return new ProfileFragment();
        if (id == R.id.nav_settings) return new SettingsFragment();
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /** Called by session listener — delegates to updateNavHeaderProfile on the UI thread. */
    @Override
    public void onProfileUpdated() { runOnUiThread(this::updateNavHeaderProfile); }

    /**
     * Loads the current user's profile from storage and updates the nav header
     * with their display name, email, and profile photo (or guest Lottie animation).
     */
    public void updateNavHeaderProfile() {
        try {
            UserSession session = getSession();
            String user = session.getUsername();

            if (user == null || user.isEmpty()) { showGuestProfile(); return; }

            UserSession.UserData data = session.getUserData(user);
            String full  = user;
            String email = user + "@example.com";
            String photo = "";

            if (data != null) {
                JSONObject p = UserStorageManager.getInstance(this).loadUserProfile(data.userId);
                if (p != null) {
                    full  = p.optString("username", user);
                    email = p.optString("email", email);
                    photo = p.optString("photoPath", "");
                }
            }

            tvUsername.setText(full);
            tvUserEmail.setText(email);

            if (!photo.isEmpty()) {
                lottieProfileNav.setVisibility(View.GONE);
                ivProfile.setVisibility(View.VISIBLE);
                Glide.with(this).load(photo).circleCrop().into(ivProfile);
            } else {
                showGuestProfile();
            }

        } catch (Exception e) {
            Log.e(TAG, "Profile update error: " + e.getMessage());
        }
    }

    /** Shows the guest Lottie animation and clears user-specific nav header fields. */
    private void showGuestProfile() {
        ivProfile.setVisibility(View.GONE);
        lottieProfileNav.setVisibility(View.VISIBLE);
        tvUsername.setText(R.string.title_guest);
        tvUserEmail.setText("");
        lottieProfileNav.setAnimation(R.raw.profile_animation);
        lottieProfileNav.playAnimation();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION BADGE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates and attaches a small red dot badge as the action view
     * of the notifications nav menu item.
     */
    private void setupNotificationBadge() {
        MenuItem    item   = navigationView.getMenu().findItem(R.id.nav_notifications);
        FrameLayout frame  = new FrameLayout(this);
        View        badge  = new View(this);
        float       density = getResources().getDisplayMetrics().density;

        badge.setBackgroundResource(R.drawable.badge_red_circle);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                (int) (8 * density), (int) (8 * density));
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, (int) (17 * density), (int) (8 * density), 0);

        badge.setLayoutParams(params);
        badge.setVisibility(View.GONE);

        frame.addView(badge);
        item.setActionView(frame);
        notificationBadge = badge;
    }

    /** Makes the notification badge visible. */
    public void showNotificationBadge() { if (notificationBadge != null) notificationBadge.setVisibility(View.VISIBLE); }

    /** Hides the notification badge. */
    public void hideNotificationBadge() { if (notificationBadge != null) notificationBadge.setVisibility(View.GONE); }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════════════════

    /** Shows a confirmation dialog before logging the user out. */
    private void showLogoutDialog() {
        DialogUtils.showConfirmDialog(
                this,
                getString(R.string.logout_title),
                getString(R.string.logout_message),
                getString(R.string.logout),
                getString(R.string.cancel),
                this::logout, null
        );
    }

    /**
     * Shows a loading dialog, calls the session logout, then navigates
     * to LoginActivity and clears the back stack.
     */
    public void logout() {
        showLoading();
        handler.postDelayed(() -> {
            try {
                String username = UserSession.getInstance().getUsername();
                ChatSessionManager.getInstance(this).onUserLogout(username);

                UserSession.getInstance().logout();
                Intent i = new Intent(this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } catch (Exception e) {
                Log.e(TAG, "Logout error: " + e.getMessage());
            }
            loadingDialog.dismiss();
        }, LOGOUT_DELAY);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Applies the user's preferred app language to the activity's resources configuration. */
    public void applyCurrentLanguage() {
        String lang   = UserSession.getInstance().getLanguage();
        Locale locale = new Locale(lang == null || lang.isEmpty() ? "en" : lang);
        Locale.setDefault(locale);

        android.content.res.Resources      res    = getResources();
        android.content.res.Configuration  config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    /** Requests the POST_NOTIFICATIONS permission on Android 13 (TIRAMISU) and above. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
        }
    }

    /** Shows the loading dialog if it is not already visible. */
    private void showLoading() { if (!loadingDialog.isShowing()) loadingDialog.show(); }

    // ══════════════════════════════════════════════════════════════════════════
    // SESSION LISTENERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Primary session listener: refreshes the nav header when the profile is updated. */
    private final UserSession.UserSessionListener mainSessionListener = new UserSession.UserSessionListener() {
        @Override public void onBadgeCleared()   { UserSession.UserSessionListener.super.onBadgeCleared(); }
        @Override public void onProfileUpdated() { runOnUiThread(MainActivity.this::updateNavHeaderProfile); }
    };

    /** Badge session listener: hides the badge dot when the badge is cleared. */
    private final UserSession.UserSessionListener badgeSessionListener = new UserSession.UserSessionListener() {
        @Override public void onBadgeCleared()   { hideNotificationBadge(); }
        @Override public void onProfileUpdated() { UserSession.UserSessionListener.super.onProfileUpdated(); }
    };
}