package com.android.alpha.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
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
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.notes.NoteActivity;
import com.android.alpha.ui.geminichat.ChatActivity;
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
 * Host activity yang mengelola navigation drawer, fragment navigation,
 * toolbar title, profile header, notification badge, dan user session.

 * PERUBAHAN:
 * - highlightActiveMenu()  → rekursif ke sub-menu agar highlight bekerja
 * - showGuestProfile()     → email guest diisi @string/placeholder_guest_email
 * - onNavigationItemSelected() → nav_gemini membuka ChatActivity
 * - getFragmentForMenu()   → tidak berubah (Gemini bukan fragment)
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        UserSession.UserSessionListener {

    // ─── CONSTANTS ─────────────────────────────────────────────────────────────
    private static final int  PERMISSION_REQUEST_CODE = 101;
    private static final long FRAGMENT_LOAD_DELAY     = 600;
    private static final long LOGOUT_DELAY            = 1200;
    private final        String TAG                   = "MainActivity";

    // ─── UI ────────────────────────────────────────────────────────────────────
    private DrawerLayout        drawerLayout;
    private NavigationView      navigationView;
    private Toolbar             toolbar;
    private ImageView           ivProfile;
    private LottieAnimationView lottieProfileNav;
    private TextView            tvUsername, tvUserEmail;
    private View                notificationBadge;

    // ─── UTILS ─────────────────────────────────────────────────────────────────
    private LoadingDialog  loadingDialog;
    private final Handler  handler   = new Handler();
    private float          startX;
    private boolean        isSwiping = false;

    // ══════════════════════════════════════════════════════════════════════════
    // INTERFACE
    // ══════════════════════════════════════════════════════════════════════════

    public interface ToolbarTitleProvider {
        int getToolbarTitleRes();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = "en";
        try { lang = UserSession.getInstance().getLanguage(); }
        catch (IllegalStateException ignored) {}
        super.attachBaseContext(
                com.android.alpha.utils.LocaleHelper.setLocale(newBase, lang));
    }

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

        showFragment(new HomeFragment(), "Home", false);

        if (getIntent().getBooleanExtra("show_badge", false)) showNotificationBadge();
        requestNotificationPermission();
        session.setBadgeListener(badgeSessionListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCurrentLanguage();
        updateToolbarTitleByFragment();

        Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (fragment instanceof HomeFragment) ((HomeFragment) fragment).refreshNotes();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        UserSession session = getSession();
        if (session != null) session.removeListener();
    }

    private UserSession getSession() { return UserSession.getInstance(); }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    private void initViews() {
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar        = findViewById(R.id.toolbar);
        loadingDialog  = new LoadingDialog(this);
    }

    private void setupToolbar() { setSupportActionBar(toolbar); }

    private Typeface getAppFont() {
        try {
            return androidx.core.content.res.ResourcesCompat.getFont(
                    this, R.font.linottesemibold); // ttf / otf aman
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NAVIGATION DRAWER
    // ══════════════════════════════════════════════════════════════════════════

    private void setupNavigationDrawer() {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        Drawable menuIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu);

        if (menuIcon != null) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.text_color, typedValue, true);
            menuIcon.setTint(typedValue.data);
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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

        Typeface tf = getAppFont();

        tvUsername.setTypeface(tf);
        tvUserEmail.setTypeface(tf);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Warna merah pada teks item Logout. */
    private void colorLogoutItem() {
        MenuItem logoutItem = navigationView.getMenu().findItem(R.id.nav_logout);
        if (logoutItem == null) return;
        SpannableString s = new SpannableString(logoutItem.getTitle());
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.color_error, tv, true);

        s.setSpan(new ForegroundColorSpan(tv.data), 0, s.length(), 0);
        logoutItem.setTitle(s);
    }

    private void setupFooter() {
        TextView footer = new TextView(this);
        footer.setText(getString(R.string.footer_text));
        footer.setTextSize(12);

        // font
        footer.setTypeface(getAppFont());

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.text_color, typedValue, true);
        footer.setTextColor(typedValue.data);

        footer.setGravity(Gravity.CENTER);
        int p = (int) (40 * getResources().getDisplayMetrics().density);
        footer.setPadding(0, p, 0, p);

        NavigationView.LayoutParams params = new NavigationView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        footer.setLayoutParams(params);

        navigationView.addView(footer);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BACK GESTURE
    // ══════════════════════════════════════════════════════════════════════════

    private void setupGestureBack() {
        View container = findViewById(R.id.fragment_container);
        container.setOnTouchListener((v, e) -> {
            Fragment current = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);
            if (current instanceof HomeFragment) return false;

            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX    = e.getX();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float delta = e.getX() - startX;
                    if (delta > 0) { isSwiping = true; v.setTranslationX(delta * 0.8f); }
                    return true;
                case MotionEvent.ACTION_UP:
                    float move = e.getX() - startX;
                    if (isSwiping && move > v.getWidth() / 3f) animateBack(v);
                    else v.animate().translationX(0).setDuration(200).start();
                    if (Math.abs(move) < 10) v.performClick();
                    isSwiping = false;
                    return true;
            }
            return false;
        });
    }

    private void animateBack(View v) {
        v.animate().translationX(v.getWidth()).setDuration(200).withEndAction(() -> {
            v.setTranslationX(0);
            getOnBackPressedDispatcher().onBackPressed();
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FRAGMENT NAVIGATION
    // ══════════════════════════════════════════════════════════════════════════

    public void showFragment(Fragment fragment, String title, boolean addToBackStack) {
        showLoading();
        handler.postDelayed(() -> {
            if (addToBackStack) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out)
                        .replace(R.id.fragment_container, fragment, title)
                        .addToBackStack(title)
                        .commitAllowingStateLoss();
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment, title)
                        .commitAllowingStateLoss();
            }

            // FIX 1: Panggil setelah commitAllowingStateLoss selesai via executePendingTransactions()
            // agar fragment sudah benar-benar ada di container saat title/highlight diupdate.
            getSupportFragmentManager().executePendingTransactions();
            syncTitleAndHighlight();

            loadingDialog.dismiss();
        }, FRAGMENT_LOAD_DELAY);
    }

    private void setupBackStackListener() {
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            // FIX 2: Post ke main looper agar fragment sudah fully attached
            // sebelum kita baca via findFragmentById(). Tanpa ini, saat back stack
            // di-pop (gesture back / back button), fragment lama masih terbaca.
            new Handler(Looper.getMainLooper()).post(this::syncTitleAndHighlight);
        });
    }

    /**
     * Single source of truth untuk update title toolbar + highlight nav.
     * Dipanggil dari: showFragment(), backStackListener, onResume().
     */
    private void syncTitleAndHighlight() {
        Fragment f = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (getSupportActionBar() == null) return;

        if (f instanceof ToolbarTitleProvider) {
            getSupportActionBar().setTitle(((ToolbarTitleProvider) f).getToolbarTitleRes());
        } else if (f == null) {
            // Tidak ada fragment → default ke Home
            getSupportActionBar().setTitle(R.string.menu_title_home);
            Typeface tf = getAppFont();

            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View v = toolbar.getChildAt(i);
                if (v instanceof TextView) {
                    ((TextView) v).setTypeface(tf);
                }
            }
        }

        highlightActiveMenu(f);
    }

    // Tetap ada untuk kompatibilitas (dipanggil dari onResume)
    private void updateToolbarTitleByFragment() {
        syncTitleAndHighlight();
    }

    /**
     * Men-check item nav yang sesuai fragment aktif.
     * Rekursif ke sub-menu agar semua level ter-update.
     * f == null → default highlight ke Home.
     */
    private void highlightActiveMenu(Fragment f) {
        if (navigationView == null) return;

        int activeId = R.id.nav_home; // default
        if      (f instanceof ProfileFragment)  activeId = R.id.nav_profile;
        else if (f instanceof SettingsFragment) activeId = R.id.nav_settings;
        // HomeFragment / null → nav_home
        // Notes/Gemini/Notif  → tidak punya fragment, tidak di-highlight

        setMenuItemChecked(navigationView.getMenu(), activeId);
    }

    /**
     * Rekursif: iterasi seluruh item + sub-menu,
     * set checked=true hanya untuk activeId, lainnya false.
     */
    private void setMenuItemChecked(Menu menu, int activeId) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.hasSubMenu()) {
                setMenuItemChecked(Objects.requireNonNull(item.getSubMenu()), activeId);
            } else {
                item.setChecked(item.getItemId() == activeId);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWER ITEM SELECTION
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_logout) {
            showLogoutDialog();

        } else if (id == R.id.nav_notifications) {
            startActivity(new Intent(this, NotificationActivity.class));
            hideNotificationBadge();

        } else if (id == R.id.nav_notes) {
            // Notes membuka NoteActivity — bukan fragment
            startActivity(new Intent(this, NoteActivity.class));

        } else if (id == R.id.nav_gemini) {
            // Gemini membuka ChatActivity — bukan fragment
            startActivity(new Intent(this, ChatActivity.class));

        } else {
            Fragment fragment = getFragmentForMenu(id);
            if (fragment != null)
                showFragment(
                        fragment,
                        Objects.requireNonNull(item.getTitle()).toString(),
                        id != R.id.nav_home
                );
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private Fragment getFragmentForMenu(int id) {
        if (id == R.id.nav_home)     return new HomeFragment();
        if (id == R.id.nav_profile)  return new ProfileFragment();
        if (id == R.id.nav_settings) return new SettingsFragment();
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE HEADER
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onProfileUpdated() { runOnUiThread(this::updateNavHeaderProfile); }

    /**
     * Mengisi nav header dengan data profil user yang sedang login.
     * Logika SAMA PERSIS dengan ProfileFragment.refreshProfileData()
     * agar email konsisten di kedua tempat:
     *   ada email di JSON → pakai itu
     *   tidak ada         → username + "@example.com"  (sama dengan ProfileFragment)
     *   guest/tidak login → showGuestProfile()
     */
    public void updateNavHeaderProfile() {
        try {
            UserSession session  = getSession();
            String      username = session.getUsername();

            if (username == null || username.isEmpty()) { showGuestProfile(); return; }

            // loadCurrentProfileJson() — API yang sama dipakai ProfileFragment
            JSONObject json     = session.loadCurrentProfileJson();
            String     fullName = username;
            String     email    = username + "@example.com"; // fallback sama dengan ProfileFragment
            String     photo    = "";

            if (json != null) {
                fullName = json.optString("username",  fullName);
                email    = json.optString("email",     email);   // timpa fallback jika tersimpan
                photo    = json.optString("photoPath", "");
            }

            tvUsername.setText(fullName);
            tvUserEmail.setText(email);
            Typeface tf = getAppFont();

            tvUsername.setTypeface(tf);
            tvUserEmail.setTypeface(tf);

            if (!photo.isEmpty()) {
                ivProfile.setVisibility(View.VISIBLE);
                lottieProfileNav.setVisibility(View.GONE);
                Glide.with(this).load(photo).circleCrop().into(ivProfile);
            } else {
                ivProfile.setVisibility(View.GONE);
                lottieProfileNav.setVisibility(View.VISIBLE);
                lottieProfileNav.setAnimation(R.raw.profile_animation);
                lottieProfileNav.playAnimation();
            }

        } catch (Exception e) {
            Log.e(TAG, "Profile update error: " + e.getMessage());
            showGuestProfile();
        }
    }

    /**
     * Tampilkan Lottie guest dan isi email dengan fallback guest.
     * Format email guest mengikuti pola yang sama: "guest@example.com"
     * bukan string statis, agar konsisten dengan logika ProfileFragment.
     */
    private void showGuestProfile() {
        ivProfile.setVisibility(View.GONE);
        lottieProfileNav.setVisibility(View.VISIBLE);
        tvUsername.setText(R.string.title_guest);
        tvUserEmail.setText(R.string.placeholder_email); // "guest@example.com"
        lottieProfileNav.setAnimation(R.raw.profile_animation);
        lottieProfileNav.playAnimation();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION BADGE
    // ══════════════════════════════════════════════════════════════════════════

    private void setupNotificationBadge() {
        MenuItem    item    = navigationView.getMenu().findItem(R.id.nav_notifications);
        if (item == null) return;
        FrameLayout frame   = new FrameLayout(this);
        View        badge   = new View(this);
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

    public void showNotificationBadge() {
        if (notificationBadge != null) notificationBadge.setVisibility(View.VISIBLE);
    }

    public void hideNotificationBadge() {
        if (notificationBadge != null) notificationBadge.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ══════════════════════════════════════════════════════════════════════════

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

    public void applyCurrentLanguage() {
        String lang = UserSession.getInstance().getLanguage();
        Locale locale = new Locale(lang == null || lang.isEmpty() ? "en" : lang);
        Locale.setDefault(locale);

        android.content.res.Resources     res    = getResources();
        android.content.res.Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void showLoading() {
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SESSION LISTENERS
    // ══════════════════════════════════════════════════════════════════════════

    private final UserSession.UserSessionListener mainSessionListener =
            new UserSession.UserSessionListener() {
                @Override public void onBadgeCleared() {
                    UserSession.UserSessionListener.super.onBadgeCleared();
                }
                @Override public void onProfileUpdated() {
                    runOnUiThread(MainActivity.this::updateNavHeaderProfile);
                }
            };

    private final UserSession.UserSessionListener badgeSessionListener =
            new UserSession.UserSessionListener() {
                @Override public void onBadgeCleared()   { hideNotificationBadge(); }
                @Override public void onProfileUpdated() {
                    UserSession.UserSessionListener.super.onProfileUpdated();
                }
            };
}