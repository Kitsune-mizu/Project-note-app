package com.android.kitsune.ui.profile;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.FragmentManager;
import android.app.Activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.android.kitsune.MainActivity;
import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.ui.auth.ForgotPasswordActivity;
import com.android.kitsune.ui.auth.LoginActivity;
import com.android.kitsune.utils.DialogUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    // ---------- UI VIEWS & COMPONENTS ----------
    private SharedPreferences prefs;
    private SwitchMaterial switchNotifications;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private TextView textCurrentLanguage;

    public SettingsFragment() {
        // Required empty public constructor
    }

    // ---------- LIFECYCLE METHODS ----------
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // FIX: Gunakan string resource untuk nama SharedPreferences (jika memungkinkan, tapi "app_settings" umum diterima)
        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        switchNotifications = view.findViewById(R.id.switchNotifications);
        textCurrentLanguage = view.findViewById(R.id.textCurrentLanguage);

        setupPermissionLauncher();
        loadSettings();
        setupClickListeners(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkSystemNotificationStatus();
        String currentLang = UserSession.getInstance().getLanguage();
        int flagRes;
        int textRes;

        switch (currentLang) {
            case "id":
                flagRes = R.drawable.flag_id;
                textRes = R.string.lang_indonesia;
                break;
            case "ja":
                flagRes = R.drawable.flag_ja;
                textRes = R.string.lang_japanese;
                break;
            case "ko":
                flagRes = R.drawable.flag_ko;
                textRes = R.string.lang_korean;
                break;
            default:
                flagRes = R.drawable.flag_globe;
                textRes = R.string.lang_english;
                break;
        }

        // Atur ukuran kecil untuk bendera
        int size = (int) (textCurrentLanguage.getLineHeight() * 1.2f); // ~80% tinggi teks
        Drawable flagDrawable = ContextCompat.getDrawable(requireContext(), flagRes);
        if (flagDrawable != null) {
            flagDrawable.setBounds(0, 0, size, size);
        }

        // Set teks + gambar di kanan
        textCurrentLanguage.setText(getString(textRes));
        textCurrentLanguage.setCompoundDrawables(null, null, flagDrawable, null);
        textCurrentLanguage.setCompoundDrawablePadding(12);

    }

    // ---------- INITIALIZATION & CONFIGURATION ----------
    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        enableNotifications();
                    } else {
                        switchNotifications.setChecked(false);
                        // FIX: String Resource
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

    private void loadSettings() {
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        switchNotifications.setChecked(notificationsEnabled);
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.layoutProfile).setOnClickListener(v -> navigateToProfile());
        view.findViewById(R.id.layoutForgotPassword).setOnClickListener(v -> openForgotPassword());
        view.findViewById(R.id.layoutLogout).setOnClickListener(v -> showLogoutConfirmation());
        view.findViewById(R.id.layoutDeleteAccount).setOnClickListener(v -> showDeleteAccountWarnings());
        view.findViewById(R.id.layoutLanguage).setOnClickListener(v -> showLanguageDialog());

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    enableNotifications();
                }
            } else {
                disableNotifications();
            }
        });
    }

    private void showLanguageDialog() {
        String[] languageNames = {"English", "Indonesia", "日本語", "한국어"};
        String[] languageCodes = {"en", "id", "ja", "ko"};
        int[] flagIcons = {
                R.drawable.flag_globe,
                R.drawable.flag_id,
                R.drawable.flag_ja,
                R.drawable.flag_ko
        };

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.ModernBottomSheetDialog);

        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_language_picker, new FrameLayout(requireContext()), false);

        ViewGroup container = sheetView.findViewById(R.id.languageContainer);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String currentLang = UserSession.getInstance().getLanguage();

        for (int i = 0; i < languageNames.length; i++) {
            View itemView = inflater.inflate(R.layout.item_language_option, container, false);
            TextView tvName = itemView.findViewById(R.id.tvLanguageName);
            ImageView imgFlag = itemView.findViewById(R.id.imgFlag);
            View checkIcon = itemView.findViewById(R.id.iconCheck);

            tvName.setText(languageNames[i]);
            imgFlag.setImageResource(flagIcons[i]);

            if (languageCodes[i].equals(currentLang)) {
                itemView.setBackgroundResource(R.drawable.bg_language_selected);
                checkIcon.setVisibility(View.VISIBLE);
            }

            int index = i;
            itemView.setOnClickListener(v -> {
                UserSession.getInstance().setLanguage(languageCodes[index]);

                // Simpan activity sebelum dialog ditutup
                final Activity activity = getActivity();
                dialog.dismiss();

                if (activity instanceof MainActivity) {
                    MainActivity mainActivity = (MainActivity) activity;
                    mainActivity.getSupportFragmentManager()
                            .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }

                // Delay sedikit supaya transisi dialog selesai dulu sebelum recreate
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (activity != null && !activity.isFinishing()) {
                        activity.recreate();
                    }
                }, 250);
            });

            container.addView(itemView);
        }

        dialog.setContentView(sheetView);
        dialog.show();
    }

    // ---------- NAVIGATION & AUTH ACTIONS ----------
    private void navigateToProfile() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showFragment(new ProfileFragment(), "Profile", true);
        }
    }

    private void openForgotPassword() {
        Intent intent = new Intent(requireContext(), ForgotPasswordActivity.class);
        startActivity(intent);
    }

    private void showLogoutConfirmation() {
        // FIX: String Resource
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.dialog_logout_title),
                getString(R.string.dialog_logout_msg),
                getString(R.string.action_logout),
                getString(R.string.action_cancel),
                () -> {
                    // Ensure activity is MainActivity
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).logout();
                    }
                },
                null
        );
    }

    // ---------- ACCOUNT DELETION LOGIC ----------
    private void showDeleteAccountWarnings() {
        showWarningStep(1);
    }

    private void showWarningStep(int step) {
        String title, message;

        switch (step) {
            case 1:
                // FIX: String Resource
                title = getString(R.string.warn_delete_title_1);
                message = getString(R.string.warn_delete_msg_1);
                break;
            case 2:
                // FIX: String Resource
                title = getString(R.string.warn_delete_title_2);
                message = getString(R.string.warn_delete_msg_2);
                break;
            case 3:
                // FIX: String Resource
                title = getString(R.string.warn_delete_title_3);
                message = getString(R.string.warn_delete_msg_3);
                break;
            default:
                title = "";
                message = "";
        }

        final int[] countdown = {5}; // 5 seconds before the Next button is active

        DialogUtils.showCountdownDialog(
                requireContext(),
                title,
                message,
                getString(R.string.action_next), // FIX: String Resource
                getString(R.string.action_cancel), // FIX: String Resource
                countdown[0],
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
        // FIX: String Resource
        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.dialog_delete_title),
                getString(R.string.dialog_delete_msg),
                getString(R.string.action_delete),
                getString(R.string.action_cancel),
                () -> {
                    UserSession session = UserSession.getInstance();
                    String username = session.getUsername();

                    if (session.deleteAccount(username)) {
                        // FIX: String Resource
                        Toast.makeText(requireContext(), R.string.toast_account_deleted, Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        // FIX: String Resource
                        Toast.makeText(requireContext(), R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                },
                null
        );
    }

    // ---------- NOTIFICATION MANAGEMENT ----------
    private void checkSystemNotificationStatus() {
        NotificationManager manager =
                (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        boolean systemEnabled = manager != null && manager.areNotificationsEnabled();

        if (!systemEnabled) {
            switchNotifications.setChecked(false);
            prefs.edit().putBoolean("notifications_enabled", false).apply();
        } else {
            loadSettings();
        }
    }

    private void enableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", true).apply();
        // FIX: String Resource
        Toast.makeText(requireContext(), R.string.toast_notifications_enabled, Toast.LENGTH_SHORT).show();
    }

    private void disableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", false).apply();
        // FIX: String Resource
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
}