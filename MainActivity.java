package com.android.kitsune;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.kitsune.ui.map.MapFragment;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.data.local.UserStorageManager;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.ui.auth.LoginActivity;
import com.android.kitsune.ui.home.HomeFragment;
import com.android.kitsune.ui.home.NotificationActivity;
import com.android.kitsune.ui.profile.ProfileFragment;
import com.android.kitsune.ui.profile.SettingsFragment;
import com.android.kitsune.utils.DialogUtils;
import com.android.kitsune.utils.LoadingDialog;
import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import android.content.Context;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, UserSession.UserSessionListener {

    // --- UI Components ---
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView ivProfile;
    private LottieAnimationView lottieProfileNav;
    private TextView tvUsername, tvUserEmail;
    private View notificationBadge;
    private Toolbar toolbar;

    // --- State & Utilities ---
    private LoadingDialog loadingDialog;
    private final Handler handler = new Handler();

    // Gesture variables
    private float startX;
    private boolean isSwiping = false;
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final long FRAGMENT_LOAD_DELAY = 600;
    private static final long LOGOUT_DELAY = 1200;

    // --- Lifecycle Methods ---

    public void setToolbarTitle(String title) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = UserSession.getInstance() != null
                ? UserSession.getInstance().getLanguage()
                : "en";

        if (lang == null || lang.isEmpty()) lang = "en";

        Context localeContext = com.android.kitsune.utils.LocaleHelper.setLocale(newBase, lang);
        super.attachBaseContext(localeContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UserSession.init(this);
        UserSession session = getSession();
        session.addListener(mainSessionListener);
        session.removeOldActivities(); // Maintenance task

        initializeViews();
        setupToolbar();
        setupNavigationDrawer();
        setupGestureBack();
        setupFooter();
        setupFragmentBackStackListener();

        // Initial fragment load
        showFragment(new HomeFragment(), "Home", false);

        // Check for notification badge flag
        if (getIntent().getBooleanExtra("show_badge", false)) {
            showNotificationBadge();
        }

        requestNotificationPermission();

        session.setBadgeListener(badgeSessionListener);
    }

    public void applyCurrentLanguage() {
        String langCode = UserSession.getInstance() != null
                ? UserSession.getInstance().getLanguage()
                : "en";

        if (langCode == null || langCode.isEmpty()) langCode = "en";

        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        android.content.res.Resources res = getResources();
        android.content.res.Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCurrentLanguage();

        NavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            navView.getMenu().findItem(R.id.nav_notifications).setChecked(true);
        }

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            if (fragment instanceof HomeFragment)
                setToolbarTitle(getString(R.string.menu_title_home));
            else if (fragment instanceof ProfileFragment)
                setToolbarTitle(getString(R.string.menu_title_profile));
            else if (fragment instanceof SettingsFragment)
                setToolbarTitle(getString(R.string.menu_title_settings));
            else if (fragment instanceof MapFragment)
                setToolbarTitle(getString(R.string.map_title));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        UserSession session = getSession();
        if (session != null) {
            session.removeListener(mainSessionListener);
            session.removeListener(badgeSessionListener);
        }
    }

    private int currentMenuId = R.id.nav_home;

    private void highlightActiveMenu(Fragment fragment) {
        if (navigationView == null) return;

        int newMenuId = R.id.nav_home;

        if (fragment instanceof ProfileFragment) {
            newMenuId = R.id.nav_profile;
        } else if (fragment instanceof SettingsFragment) {
            newMenuId = R.id.nav_settings;
        } else if (currentMenuId == R.id.nav_notifications) {
            newMenuId = R.id.nav_notifications;
        }

        if (newMenuId != currentMenuId) {
            animateMenuSelectionChange(currentMenuId, newMenuId);
            currentMenuId = newMenuId;
        }

        navigationView.getMenu().findItem(R.id.nav_home)
                .setChecked(newMenuId == R.id.nav_home);
        navigationView.getMenu().findItem(R.id.nav_profile)
                .setChecked(newMenuId == R.id.nav_profile);
        navigationView.getMenu().findItem(R.id.nav_settings)
                .setChecked(newMenuId == R.id.nav_settings);
        navigationView.getMenu().findItem(R.id.nav_notifications)
                .setChecked(newMenuId == R.id.nav_notifications);
    }

    private void animateMenuSelectionChange(int oldId, int newId) {
        MenuItem oldItem = navigationView.getMenu().findItem(oldId);
        MenuItem newItem = navigationView.getMenu().findItem(newId);

        if (oldItem != null && newItem != null) {
            View oldView = navigationView.findViewById(oldItem.getItemId());
            View newView = navigationView.findViewById(newItem.getItemId());

            if (oldView != null) {
                oldView.animate()
                        .alpha(0.8f)
                        .setDuration(150)
                        .withEndAction(() -> oldView.setAlpha(1f))
                        .start();
            }

            if (newView != null) {
                newView.setAlpha(0.7f);
                newView.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start();
            }
        }
    }

    // --- Session Listeners ---

    private final UserSession.UserSessionListener mainSessionListener = new UserSession.UserSessionListener() {
        @Override
        public void onBadgeCleared() {
            // Handled by badgeSessionListener
        }

        @Override
        public void onProfileUpdated() {
            runOnUiThread(() -> {
                updateNavHeaderProfile();
                // Refresh visible fragments if they are active
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (fragment instanceof HomeFragment)
                    ((HomeFragment) fragment).loadUserData();
                if (fragment instanceof ProfileFragment)
                    ((ProfileFragment) fragment).refreshProfileData();
            });
        }
    };

    private final UserSession.UserSessionListener badgeSessionListener = new UserSession.UserSessionListener() {
        @Override
        public void onBadgeCleared() {
            hideNotificationBadge();
        }

        @Override
        public void onProfileUpdated() {
            // Ignore
        }
    };

    @Override
    public void onProfileUpdated() {
        // Redundant with mainSessionListener but kept for explicit interface implementation
        runOnUiThread(this::updateNavHeaderProfile);
    }

    private UserSession getSession() {
        return UserSession.getInstance();
    }

    // --- Initialization & Setup ---

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        loadingDialog = new LoadingDialog(this);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle("Home");
    }

    private void setupNavigationDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            ivProfile = headerView.findViewById(R.id.ivProfile);
            lottieProfileNav = headerView.findViewById(R.id.lottieProfileNav);
            tvUsername = headerView.findViewById(R.id.tvUsername);
            tvUserEmail = headerView.findViewById(R.id.tvUserEmail);
            updateNavHeaderProfile();
        }

        Menu menu = navigationView.getMenu();
        MenuItem logoutItem = menu.findItem(R.id.nav_logout);

        if (logoutItem != null) {
            SpannableString s = new SpannableString(logoutItem.getTitle());
            s.setSpan(new ForegroundColorSpan(
                    ContextCompat.getColor(this, R.color.md_theme_light_error)
            ), 0, s.length(), 0);
            logoutItem.setTitle(s);
        }

        setupNotificationBadge();
    }

    private void setupFooter() {
        TextView footerView = new TextView(this);
        footerView.setText(getString(R.string.footer_text));
        footerView.setTextSize(12);
        footerView.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
        footerView.setGravity(Gravity.CENTER);
        footerView.setPadding(0, 40, 0, 40);
        NavigationView.LayoutParams params = new NavigationView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM;
        footerView.setLayoutParams(params);
        navigationView.addView(footerView);
    }

    private void setupFragmentBackStackListener() {
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (getSupportActionBar() == null || currentFragment == null) return;

            // Default title (Home)
            int titleResId = R.string.menu_title_home;

            if (currentFragment instanceof ProfileFragment) {
                titleResId = R.string.menu_title_profile;
            } else if (currentFragment instanceof SettingsFragment) {
                titleResId = R.string.menu_title_settings;
            } else if (currentFragment instanceof MapFragment) {
                titleResId = R.string.map_title;
            }

            applyCurrentLanguage();
            getSupportActionBar().setTitle(getString(titleResId));

            highlightActiveMenu(currentFragment);
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
        }
    }

    // --- Gesture & Back Navigation ---

    private void setupGestureBack() {
        View container = findViewById(R.id.fragment_container);
        container.setOnTouchListener((v, event) -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            // Disable swipe gesture on the root fragment (Home)
            if (currentFragment instanceof HomeFragment) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    isSwiping = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getX() - startX;
                    if (deltaX > 0) { // Only allow swiping from left to right
                        isSwiping = true;
                        v.setTranslationX(deltaX * 0.8f);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.performClick();
                    if (isSwiping) {
                        float totalDelta = event.getX() - startX;
                        // Trigger back if swiped more than 1/3 of the screen width
                        if (totalDelta > v.getWidth() / 3f) {
                            animateAndGoBack(v);
                        } else {
                            // Snap back
                            v.animate().translationX(0).setDuration(200).start();
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private void animateAndGoBack(View v) {
        ViewPropertyAnimator animator = v.animate().translationX(v.getWidth()).setDuration(200);
        animator.withEndAction(() -> {
            v.setTranslationX(0);
            getOnBackPressedDispatcher().onBackPressed();
        });
        animator.start();
    }

    // --- Fragment Management ---

    public void showFragment(Fragment fragment, String title, boolean addToBackStack) {
        showLoading();
        handler.postDelayed(() -> {
            if (!isFinishing()) {
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

                highlightActiveMenu(fragment);
                loadingDialog.dismiss();
            }
        }, FRAGMENT_LOAD_DELAY);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) {
            showLogoutConfirmation();
        } else if (id == R.id.nav_notifications) {
            currentMenuId = R.id.nav_notifications;
            navigationView.getMenu().findItem(R.id.nav_notifications).setChecked(true);
            startActivity(new Intent(this, NotificationActivity.class));
            hideNotificationBadge();
        } else {
            Fragment fragment = getFragmentForMenuItem(id);
            // Only add to back stack if it's not the home fragment
            boolean addToBackStack = id != R.id.nav_home;
            if (fragment != null)
                showFragment(fragment, Objects.requireNonNull(item.getTitle()).toString(), addToBackStack);
        }

        drawerLayout.closeDrawers();
        return true;
    }

    private Fragment getFragmentForMenuItem(int id) {
        if (id == R.id.nav_home) return new HomeFragment();
        if (id == R.id.nav_profile) return new ProfileFragment();
        if (id == R.id.nav_settings) return new SettingsFragment();
        return null;
    }

    // --- Profile & Header Management ---

    public void updateNavHeaderProfile() {
        if (ivProfile == null || lottieProfileNav == null || tvUsername == null || tvUserEmail == null)
            return;

        UserSession session = getSession();
        String username = session.getUsername();

        if (username == null || username.isEmpty()) {
            tvUsername.setText(R.string.title_guest);
            tvUserEmail.setText("");
            ivProfile.setVisibility(View.GONE);
            lottieProfileNav.setVisibility(View.VISIBLE);
            return;
        }

        String fullName = username;
        String email = username + "@example.com";
        String profilePic = "";

        try {
            UserSession.UserData userData = session.getUserData(username);

            if (userData != null) {
                // Load user profile from storage based on User ID
                JSONObject profileJson = UserStorageManager.getInstance(this).loadUserProfile(userData.userId);

                if (profileJson != null) {
                    fullName = profileJson.optString("username", username);
                    email = profileJson.optString("email", username + "@example.com");
                    profilePic = profileJson.optString("photoPath", "");
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error loading profile: " + e.getMessage());
        }

        tvUsername.setText(fullName);
        tvUserEmail.setText(email);

        if (!profilePic.isEmpty()) {
            lottieProfileNav.setVisibility(View.GONE);
            ivProfile.setVisibility(View.VISIBLE);
            Glide.with(this).load(profilePic).circleCrop().into(ivProfile);
        } else {
            ivProfile.setVisibility(View.GONE);
            lottieProfileNav.setVisibility(View.VISIBLE);
            lottieProfileNav.setAnimation(R.raw.profile_animation);
            lottieProfileNav.playAnimation();
        }
    }

    // --- Notification Badge Management ---

    private void setupNotificationBadge() {
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_notifications);
        if (menuItem == null) return;

        // Create a FrameLayout to hold the badge
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        // Create the badge View (red circle)
        View badge = new View(this);
        badge.setBackgroundResource(R.drawable.badge_red_circle);

        int size = (int) (8 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(size, size);

        badgeParams.gravity = Gravity.TOP | Gravity.END; // pojok kanan atas
        // Sesuaikan margin top dan end agar pas
        int topMargin = (int) (17 * getResources().getDisplayMetrics().density);
        int endMargin = (int) (8 * getResources().getDisplayMetrics().density);
        badgeParams.setMargins(0, topMargin, endMargin, 0);

        badge.setLayoutParams(badgeParams);
        badge.setVisibility(View.GONE);
        frame.addView(badge);
        menuItem.setActionView(frame);
        notificationBadge = badge;
    }

    public void showNotificationBadge() {
        if (notificationBadge != null) notificationBadge.setVisibility(View.VISIBLE);
    }

    public void hideNotificationBadge() {
        if (notificationBadge != null) notificationBadge.setVisibility(View.GONE);
    }

    // --- Logout and Dialogs ---

    public void logout() {
        showLoading();

        handler.postDelayed(() -> {
            try {
                UserSession.getInstance().logout();

                // Clear back stack and navigate to LoginActivity
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);

                finish();
                loadingDialog.dismiss();

            } catch (Exception e) {
                loadingDialog.dismiss();
                Log.e("MainActivity", "Logout error: " + e.getMessage(), e);
            }
        }, LOGOUT_DELAY);
    }

    private void showLogoutConfirmation() {
        DialogUtils.showConfirmDialog(
                this,
                getString(R.string.logout_title),
                getString(R.string.logout_message),
                getString(R.string.logout),
                getString(R.string.cancel),
                this::logout,
                null
        );
    }

    private void showLoading() {
        if (loadingDialog != null && !loadingDialog.isShowing()) loadingDialog.show();
    }
}