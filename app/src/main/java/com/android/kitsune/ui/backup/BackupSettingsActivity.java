package com.android.kitsune.ui.backup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.android.kitsune.R;
import com.android.kitsune.base.BaseActivity;
import com.android.kitsune.data.backup.BackupManager;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.DialogUtils;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class BackupSettingsActivity extends BaseActivity {

    // ─── Constants ───────────────────────────────────────────────────────────

    private static final String TAG = "BackupSettingsActivity";

    // ─── Views ───────────────────────────────────────────────────────────────

    private TextView tvGoogleAccount;
    private TextView tvLastBackupTime;
    private TextView tvBackupSize;
    private TextView tvAutoFrequency;
    private TextView tvDriveBackupInfo;
    private LinearLayout layoutGoogleAccount;
    private LinearLayout layoutAutoBackup;
    private LinearLayout layoutFrequency;
    private LinearLayout layoutBackupNow;
    private LinearLayout layoutRestore;
    private ProgressBar progressBackup;
    private TextView tvProgressStatus;
    private View cardProgress;

    // ─── State ───────────────────────────────────────────────────────────────

    private BackupManager backupManager;
    private GoogleSignInClient googleSignInClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean pendingBackupAfterSignIn = false;
    private boolean pendingRestoreAfterSignIn = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    com.google.android.gms.tasks.Task<GoogleSignInAccount> task =
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(com.google.android.gms.common.api.ApiException.class);
                        onGoogleSignInSuccess(account);
                    } catch (Exception e) {
                        Log.e(TAG, "Google Sign-In failed: " + e.getMessage());
                        showToast(getString(R.string.toast_google_login_failed));
                    }
                }
            }
    );

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_settings);

        backupManager = BackupManager.getInstance(this);
        setupGoogleSignIn();
        initViews();
        setupClickListeners();
        refreshUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void initViews() {
        tvGoogleAccount        = findViewById(R.id.tvGoogleAccount);
        tvLastBackupTime       = findViewById(R.id.tvLastBackupTime);
        tvBackupSize           = findViewById(R.id.tvBackupSize);
        tvAutoFrequency        = findViewById(R.id.tvAutoFrequency);
        tvDriveBackupInfo      = findViewById(R.id.tvDriveBackupInfo);
        layoutGoogleAccount    = findViewById(R.id.layoutGoogleAccount);
        layoutAutoBackup       = findViewById(R.id.layoutAutoBackup);
        layoutFrequency        = findViewById(R.id.layoutFrequency);
        layoutBackupNow        = findViewById(R.id.layoutBackupNow);
        layoutRestore          = findViewById(R.id.layoutRestore);
        progressBackup         = findViewById(R.id.progressBackup);
        tvProgressStatus       = findViewById(R.id.tvProgressStatus);
        cardProgress           = findViewById(R.id.cardProgress);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.title_backup_restore));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupClickListeners() {
        layoutGoogleAccount.setOnClickListener(v -> handleGoogleAccountClick());
        layoutAutoBackup.setOnClickListener(v -> toggleAutoBackup());
        layoutFrequency.setOnClickListener(v -> showFrequencyPicker());
        layoutBackupNow.setOnClickListener(v -> handleBackupNow());
        layoutRestore.setOnClickListener(v -> handleRestore());
    }

    // ─── UI Refresh ──────────────────────────────────────────────────────────

    private void refreshUI() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            tvGoogleAccount.setText(account.getEmail());
            backupManager.setBackupAccountEmail(account.getEmail());
        } else {
            String savedEmail = backupManager.getBackupAccountEmail();
            tvGoogleAccount.setText(savedEmail.isEmpty() ? getString(R.string.label_tap_to_connect_account) : savedEmail);
        }

        tvLastBackupTime.setText(backupManager.getLastBackupDisplayText());

        String freq = backupManager.getBackupFrequency();
        tvAutoFrequency.setText(frequencyLabel(freq));

        if (account != null) {
            fetchDriveBackupInfo();
        } else {
            tvBackupSize.setText("—");
            tvDriveBackupInfo.setText(getString(R.string.msg_connect_google_info));
        }
    }

    private void fetchDriveBackupInfo() {
        String username = UserSession.getInstance().getUsername();
        backupManager.fetchBackupInfo(username, new BackupManager.BackupInfoCallback() {
            @Override
            public void onResult(String fileName, long sizeBytes, long modifiedTimeMs) {
                mainHandler.post(() -> {
                    if (fileName == null) {
                        tvBackupSize.setText("—");
                        tvDriveBackupInfo.setText(getString(R.string.msg_no_backup_drive));
                    } else {
                        String sizeStr = formatSize(sizeBytes);
                        tvBackupSize.setText(sizeStr);
                        String dateStr = modifiedTimeMs > 0
                                ? new SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault())
                                .format(new Date(modifiedTimeMs))
                                : "—";
                        tvDriveBackupInfo.setText(getString(R.string.label_drive_backup_info, sizeStr, dateStr));
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    tvBackupSize.setText("—");
                    tvDriveBackupInfo.setText(getString(R.string.msg_cannot_load_backup_info));
                });
            }
        });
    }

    // ─── Google Account ──────────────────────────────────────────────────────

    private void handleGoogleAccountClick() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            showAccountOptionsSheet(account.getEmail());
        } else {
            startGoogleSignIn(false, false);
        }
    }

    private void showAccountOptionsSheet(String email) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.ModernBottomSheetDialog);
        View view = LayoutInflater.from(this).inflate(R.layout.bottomsheet_account_options,
                new LinearLayout(this), false);

        ((TextView) view.findViewById(R.id.tvSheetEmail)).setText(email);

        view.findViewById(R.id.btnChangeAccount).setOnClickListener(v -> {
            sheet.dismiss();
            googleSignInClient.signOut().addOnCompleteListener(task ->
                    startGoogleSignIn(false, false));
        });

        view.findViewById(R.id.btnDisconnect).setOnClickListener(v -> {
            sheet.dismiss();
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                backupManager.setBackupAccountEmail("");
                backupManager.setAutoBackupEnabled(false);
                refreshUI();
                showToast(getString(R.string.toast_google_account_disconnected));
            });
        });

        sheet.setContentView(view);
        sheet.show();
    }

    private void startGoogleSignIn(boolean backupAfter, boolean restoreAfter) {
        pendingBackupAfterSignIn  = backupAfter;
        pendingRestoreAfterSignIn = restoreAfter;
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void onGoogleSignInSuccess(GoogleSignInAccount account) {
        if (account.getEmail() != null) {
            backupManager.setBackupAccountEmail(account.getEmail());
        }
        refreshUI();
        showToast(getString(R.string.toast_google_account_connected, account.getEmail()));

        if (pendingBackupAfterSignIn) {
            pendingBackupAfterSignIn = false;
            startBackup();
        } else if (pendingRestoreAfterSignIn) {
            pendingRestoreAfterSignIn = false;
            startRestore();
        }
    }

    // ─── Auto Backup Toggle ──────────────────────────────────────────────────

    private void toggleAutoBackup() {
        boolean current = backupManager.isAutoBackupEnabled();
        if (!current) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account == null) {
                showToast(getString(R.string.toast_connect_google_first));
                startGoogleSignIn(false, false);
                return;
            }
        }
        backupManager.setAutoBackupEnabled(!current);
        refreshUI();
        showToast(getString(!current ? R.string.toast_auto_backup_enabled : R.string.toast_auto_backup_disabled));
    }

    // ─── Frequency Picker ────────────────────────────────────────────────────

    private void showFrequencyPicker() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.ModernBottomSheetDialog);
        View view = LayoutInflater.from(this).inflate(R.layout.bottomsheet_backup_frequency,
                new LinearLayout(this), false);

        String current = backupManager.getBackupFrequency();

        String[] options = {"daily", "weekly", "monthly"};
        int[] viewIds    = {R.id.optionDaily,      R.id.optionWeekly,      R.id.optionMonthly};
        int[] checkIds   = {R.id.iconCheckDaily,   R.id.iconCheckWeekly,   R.id.iconCheckMonthly};

        for (int i = 0; i < options.length; i++) {
            View item      = view.findViewById(viewIds[i]);
            View checkIcon = view.findViewById(checkIds[i]);
            checkIcon.setVisibility(options[i].equals(current) ? View.VISIBLE : View.GONE);

            final String opt = options[i];
            item.setOnClickListener(v -> {
                backupManager.setBackupFrequency(opt);
                sheet.dismiss();
                refreshUI();
            });
        }

        sheet.setContentView(view);
        sheet.show();
    }

    // ─── Backup Now ──────────────────────────────────────────────────────────

    private void handleBackupNow() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            DialogUtils.showConfirmDialog(this,
                    getString(R.string.dialog_title_connect_google),
                    getString(R.string.dialog_msg_connect_backup),
                    getString(R.string.action_connect),
                    getString(R.string.action_cancel),
                    () -> startGoogleSignIn(true, false),
                    null
            );
            return;
        }
        startBackup();
    }

    private void startBackup() {
        showProgress(true);
        backupManager.performBackup(new BackupManager.BackupCallback() {
            @Override
            public void onSuccess(String message) {
                mainHandler.post(() -> {
                    hideProgress();
                    refreshUI();
                    showSuccessSheet(
                            getString(R.string.sheet_title_backup_success),
                            getString(R.string.sheet_msg_backup_success)
                    );
                });
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> {
                    hideProgress();
                    showToast(getString(R.string.toast_backup_failed, error));
                });
            }

            @Override
            public void onProgress(int percent, String status) {
                mainHandler.post(() -> {
                    progressBackup.setProgress(percent);
                    tvProgressStatus.setText(status);
                });
            }
        });
    }

    // ─── Restore ─────────────────────────────────────────────────────────────

    private void handleRestore() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            DialogUtils.showConfirmDialog(this,
                    getString(R.string.dialog_title_connect_google),
                    getString(R.string.dialog_msg_connect_restore),
                    getString(R.string.action_connect),
                    getString(R.string.action_cancel),
                    () -> startGoogleSignIn(false, true),
                    null
            );
            return;
        }

        DialogUtils.showConfirmDialog(this,
                getString(R.string.dialog_title_restore),
                getString(R.string.dialog_msg_restore_confirm),
                getString(R.string.action_restore),
                getString(R.string.action_cancel),
                this::startRestore,
                null
        );
    }

    private void startRestore() {
        showProgress(true);
        tvProgressStatus.setText(getString(R.string.status_starting_restore));

        backupManager.performRestore(new BackupManager.BackupCallback() {
            @Override
            public void onSuccess(String message) {
                mainHandler.post(() -> {
                    hideProgress();
                    refreshUI();
                    showSuccessSheet(
                            getString(R.string.sheet_title_restore_success),
                            getString(R.string.sheet_msg_restore_success)
                    );
                });
            }

            @Override
            public void onFailure(String error) {
                mainHandler.post(() -> {
                    hideProgress();
                    showToast(getString(R.string.toast_restore_failed, error));
                });
            }

            @Override
            public void onProgress(int percent, String status) {
                mainHandler.post(() -> {
                    progressBackup.setProgress(percent);
                    tvProgressStatus.setText(status);
                });
            }
        });
    }

    // ─── Progress UI ─────────────────────────────────────────────────────────

    private void showProgress(boolean show) {
        cardProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        layoutBackupNow.setEnabled(!show);
        layoutRestore.setEnabled(!show);
        if (show) {
            progressBackup.setProgress(0);
            tvProgressStatus.setText(getString(R.string.status_preparing));
        }
    }

    private void hideProgress() {
        showProgress(false);
    }

    // ─── Success Sheet ───────────────────────────────────────────────────────

    private void showSuccessSheet(String title, String message) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.ModernBottomSheetDialog);
        View view = LayoutInflater.from(this).inflate(R.layout.bottomsheet_backup_success,
                new LinearLayout(this), false);

        ((TextView) view.findViewById(R.id.tvSuccessTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tvSuccessMessage)).setText(message);
        ((TextView) view.findViewById(R.id.tvLastBackupLabel)).setText(backupManager.getLastBackupDisplayText());

        view.findViewById(R.id.btnDone).setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(view);
        sheet.show();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String frequencyLabel(String freq) {
        return switch (freq) {
            case "daily"   -> getString(R.string.freq_daily);
            case "monthly" -> getString(R.string.freq_monthly);
            default        -> getString(R.string.freq_weekly);
        };
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024));
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}