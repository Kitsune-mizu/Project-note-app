package com.android.alpha.ui.main;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.*;

import com.android.alpha.R;
import com.android.alpha.base.BaseActivity;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.geminichat.ChatSessionManager;
import com.android.alpha.ui.auth.ForgotPasswordActivity;
import com.android.alpha.ui.auth.LoginActivity;
import com.android.alpha.utils.DialogUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment implements
        MainActivity.ToolbarTitleProvider {

    // ─── Constants & Variables ───────────────────────────────────────────────

    private SwitchMaterial switchNotifications;
    private TextView textCurrentLanguage;
    private TextView textCurrentTheme;
    private TextView textCurrentColorTheme;

    private SharedPreferences prefs;
    private ActivityResultLauncher<String> requestPermissionLauncher;


    // ─── Lifecycle ───────────────────────────────────────────────────────────

    public SettingsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        switchNotifications = view.findViewById(R.id.switchNotifications);
        textCurrentLanguage = view.findViewById(R.id.textCurrentLanguage);
        textCurrentTheme = view.findViewById(R.id.textCurrentTheme);
        textCurrentColorTheme = view.findViewById(R.id.textCurrentColorTheme);

        updateColorThemeText();
        updateColorThemeVisibility();

        setupPermissionLauncher();
        loadSettings();
        setupClickListeners(view);

        ((BaseActivity) requireActivity()).applyFont(
                textCurrentTheme,
                textCurrentColorTheme
        );

        updateThemeText();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLanguageDisplay();
        checkSystemNotificationStatus();
    }

    @Override
    public int getToolbarTitleRes() {
        return R.string.menu_title_settings;
    }


    // ─── Initialization & Setup ──────────────────────────────────────────────

    private void loadSettings() {
        switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.layoutProfile).setOnClickListener(v -> navigateToProfile());
        view.findViewById(R.id.layoutForgotPassword).setOnClickListener(v -> openForgotPassword());
        view.findViewById(R.id.layoutLogout).setOnClickListener(v -> showLogoutConfirmation());
        view.findViewById(R.id.layoutDeleteAccount).setOnClickListener(v -> showDeleteAccountWarnings());
        view.findViewById(R.id.layoutLanguage).setOnClickListener(v -> showLanguageDialog());
        view.findViewById(R.id.layoutTheme).setOnClickListener(v -> showThemeDialog());
        view.findViewById(R.id.layoutColorTheme).setOnClickListener(v -> showColorThemeDialog());

        switchNotifications.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                disableNotifications();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                enableNotifications();
            }
        });
    }


    // ─── UI Display ──────────────────────────────────────────────────────────

    private void updateLanguageDisplay() {
        String lang = UserSession.getInstance().getLanguage();
        int flagRes, textRes;

        textRes = switch (lang) {
            case "id" -> {
                flagRes = R.drawable.flag_id;
                yield R.string.lang_indonesia;
            }
            case "ja" -> {
                flagRes = R.drawable.flag_ja;
                yield R.string.lang_japanese;
            }
            case "ko" -> {
                flagRes = R.drawable.flag_ko;
                yield R.string.lang_korean;
            }
            default -> {
                flagRes = R.drawable.flag_globe;
                yield R.string.lang_english;
            }
        };

        Drawable flag = ContextCompat.getDrawable(requireContext(), flagRes);
        int size = (int) (textCurrentLanguage.getLineHeight() * 1.2f);

        if (flag != null) {
            flag.setBounds(0, 0, size, size);
        }

        textCurrentLanguage.setText(getString(textRes));
        textCurrentLanguage.setCompoundDrawables(null, null, flag, null);

        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        textCurrentLanguage.setCompoundDrawablePadding(padding);
    }


    // ─── Permission Handling ─────────────────────────────────────────────────

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        enableNotifications();
                    } else {
                        switchNotifications.setChecked(false);
                        DialogUtils.showConfirmDialog(
                                requireContext(),
                                getString(R.string.dialog_permission_title),
                                getString(R.string.dialog_notification_permission_msg),
                                getString(R.string.action_open_settings),
                                getString(R.string.action_cancel),
                                this::openAppSettings,
                                null
                        );
                    }
                }
        );
    }


    // ─── Language Dialog ─────────────────────────────────────────────────────

    private void showLanguageDialog() {
        String[] names = getResources().getStringArray(R.array.language_names);
        String[] codes = {"en", "id", "ja", "ko"};
        int[] icons = {R.drawable.flag_globe, R.drawable.flag_id, R.drawable.flag_ja, R.drawable.flag_ko};

        var dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                requireContext(), R.style.ModernBottomSheetDialog);

        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_language_picker, new FrameLayout(requireContext()), false);

        ViewGroup container = sheet.findViewById(R.id.languageContainer);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String current = UserSession.getInstance().getLanguage();

        for (int i = 0; i < names.length; i++) {
            View item = inflater.inflate(R.layout.item_language_option, container, false);

            ((TextView) item.findViewById(R.id.tvLanguageName)).setText(names[i]);
            ((ImageView) item.findViewById(R.id.imgFlag)).setImageResource(icons[i]);

            if (codes[i].equals(current)) {
                item.findViewById(R.id.iconCheck).setVisibility(View.VISIBLE);
            }

            int index = i;
            item.setOnClickListener(v -> onLanguageSelected(dialog, codes[index]));
            container.addView(item);
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void onLanguageSelected(com.google.android.material.bottomsheet.BottomSheetDialog dialog, String code) {
        UserSession.getInstance().setLanguage(code);
        Activity activity = getActivity();
        dialog.dismiss();

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).getSupportFragmentManager()
                    .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity != null && !activity.isFinishing()) {
                activity.recreate();
            }
        }, 250);
    }


    // ─── Navigation & Auth Actions ───────────────────────────────────────────

    private void navigateToProfile() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showFragment(new ProfileFragment(), "Profile", true);
        }
    }

    private void openForgotPassword() {
        startActivity(new Intent(requireContext(), ForgotPasswordActivity.class));
    }

    private void showLogoutConfirmation() {
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.dialog_logout_title),
                getString(R.string.dialog_logout_msg),
                getString(R.string.action_logout),
                getString(R.string.action_cancel),
                () -> {
                    if (getActivity() instanceof MainActivity) {
                        Activity activity = getActivity();
                        if (!activity.isFinishing()) {
                            ((MainActivity) activity).logout();
                        }
                    }
                },
                null
        );
    }


    // ─── Delete Account Process ──────────────────────────────────────────────

    private void showDeleteAccountWarnings() {
        showWarningStep(1);
    }

    private void showWarningStep(int step) {
        String title = "";
        String msg = switch (step) {
            case 1 -> {
                title = getString(R.string.warn_delete_title_1);
                yield getString(R.string.warn_delete_msg_1);
            }
            case 2 -> {
                title = getString(R.string.warn_delete_title_2);
                yield getString(R.string.warn_delete_msg_2);
            }
            case 3 -> {
                title = getString(R.string.warn_delete_title_3);
                yield getString(R.string.warn_delete_msg_3);
            }
            default -> "";
        };

        DialogUtils.showCountdownDialog(
                requireContext(), title, msg,
                getString(R.string.action_next),
                getString(R.string.action_cancel),
                5,
                () -> {
                    if (step < 3) {
                        showWarningStep(step + 1);
                    } else {
                        showFinalDeleteConfirmation();
                    }
                }
        );
    }

    private void showFinalDeleteConfirmation() {
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.dialog_delete_title),
                getString(R.string.dialog_delete_msg),
                getString(R.string.action_delete),
                getString(R.string.action_cancel),
                () -> {
                    UserSession s = UserSession.getInstance();
                    String username = s.getUsername();

                    ChatSessionManager.getInstance(requireContext()).onAccountDeleted(username);

                    if (s.deleteAccount(username)) {
                        Toast.makeText(requireContext(), R.string.toast_account_deleted, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                },
                null
        );
    }


    // ─── Theme Settings ──────────────────────────────────────────────────────

    private void showThemeDialog() {
        String[] modes = {"light", "dark", "system"};
        int[] icons = {
                R.drawable.ic_light_mode,
                R.drawable.ic_dark_mode,
                R.drawable.ic_settings
        };
        int[] names = {
                R.string.theme_light,
                R.string.theme_dark,
                R.string.theme_system
        };

        var dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                requireContext(), R.style.ModernBottomSheetDialog);

        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_theme_picker, new FrameLayout(requireContext()), false);

        ViewGroup container = sheet.findViewById(R.id.themeContainer);
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        String current = prefs.getString("theme_mode", "system");

        for (int i = 0; i < names.length; i++) {
            View item = inflater.inflate(R.layout.item_theme_option, container, false);

            ((TextView) item.findViewById(R.id.tvThemeName)).setText(getString(names[i]));
            ((ImageView) item.findViewById(R.id.iconTheme)).setImageResource(icons[i]);

            if (modes[i].equals(current)) {
                item.findViewById(R.id.iconCheck).setVisibility(View.VISIBLE);
            }

            int index = i;
            item.setOnClickListener(v -> {
                setThemeMode(modes[index]);
                dialog.dismiss();
            });

            container.addView(item);
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void updateThemeText() {
        String mode = prefs.getString("theme_mode", "system");

        switch (mode) {
            case "light":
                textCurrentTheme.setText(getString(R.string.theme_light));
                break;
            case "dark":
                textCurrentTheme.setText(getString(R.string.theme_dark));
                break;
            default:
                textCurrentTheme.setText(getString(R.string.theme_system));
                break;
        }
    }

    private void setThemeMode(String mode) {
        prefs.edit().putString("theme_mode", mode).apply();

        updateThemeText();
        updateColorThemeVisibility();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }, 100);
    }


    // ─── Notification Settings ───────────────────────────────────────────────

    private void checkSystemNotificationStatus() {
        NotificationManager manager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        boolean enabled = manager != null && manager.areNotificationsEnabled();

        prefs.edit().putBoolean("notifications_enabled", enabled).apply();
        switchNotifications.setChecked(enabled);
    }

    private void enableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", true).apply();
        Toast.makeText(requireContext(), R.string.toast_notifications_enabled, Toast.LENGTH_SHORT).show();
    }

    private void disableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", false).apply();
        Toast.makeText(requireContext(), R.string.toast_notifications_disabled, Toast.LENGTH_SHORT).show();
    }

    private void openAppSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().getPackageName()));
        }
        startActivity(intent);
    }


    // ─── Color Theme Settings ────────────────────────────────────────────────

    private void showColorThemeDialog() {
        String[] themes = {"blue", "purple", "pink", "green", "orange"};
        int[] names = {
                R.string.color_theme_blue,
                R.string.color_theme_purple,
                R.string.color_theme_pink,
                R.string.color_theme_green,
                R.string.color_theme_orange
        };

        int[][] colorPreviews = {
                {R.color.color_2, R.color.color_3, R.color.color_4},
                {R.color.color_2_purple, R.color.color_3_purple, R.color.color_4_purple},
                {R.color.color_2_pink, R.color.color_3_pink, R.color.color_4_pink},
                {R.color.color_2_green, R.color.color_3_green, R.color.color_4_green},
                {R.color.color_2_orange, R.color.color_3_orange, R.color.color_4_orange}
        };

        var dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                requireContext(), R.style.ModernBottomSheetDialog);

        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_color_theme_picker, new FrameLayout(requireContext()), false);

        ViewGroup container = sheet.findViewById(R.id.colorThemeContainer);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String current = prefs.getString("color_theme", "blue");

        for (int i = 0; i < names.length; i++) {
            View item = inflater.inflate(R.layout.item_color_theme_option, container, false);

            ((TextView) item.findViewById(R.id.tvColorThemeName)).setText(getString(names[i]));

            item.findViewById(R.id.colorPreview1).setBackgroundColor(ContextCompat.getColor(requireContext(), colorPreviews[i][0]));
            item.findViewById(R.id.colorPreview2).setBackgroundColor(ContextCompat.getColor(requireContext(), colorPreviews[i][1]));
            item.findViewById(R.id.colorPreview3).setBackgroundColor(ContextCompat.getColor(requireContext(), colorPreviews[i][2]));

            if (themes[i].equals(current)) {
                item.findViewById(R.id.iconCheck).setVisibility(View.VISIBLE);
            }

            int index = i;
            item.setOnClickListener(v -> {
                setColorTheme(themes[index]);
                dialog.dismiss();
            });

            container.addView(item);
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void setColorTheme(String theme) {
        prefs.edit().putString("color_theme", theme).apply();
        updateColorThemeText();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }, 100);
    }

    private void updateColorThemeText() {
        String theme = prefs.getString("color_theme", "blue");
        int nameRes = switch (theme) {
            case "purple" -> R.string.color_theme_purple;
            case "pink" -> R.string.color_theme_pink;
            case "green" -> R.string.color_theme_green;
            case "orange" -> R.string.color_theme_orange;
            default -> R.string.color_theme_blue;
        };

        textCurrentColorTheme.setText(getString(nameRes));
    }

    private void updateColorThemeVisibility() {
        String mode = prefs.getString("theme_mode", "system");
        boolean isLight = mode.equals("light") || (mode.equals("system") && !isDarkMode());

        View colorThemeLayout = requireView().findViewById(R.id.layoutColorTheme);
        colorThemeLayout.setVisibility(isLight ? View.VISIBLE : View.GONE);
    }

    private boolean isDarkMode() {
        int mode = AppCompatDelegate.getDefaultNightMode();

        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false;

        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}