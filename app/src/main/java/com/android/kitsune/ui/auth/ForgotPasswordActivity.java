package com.android.kitsune.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.R;
import com.android.kitsune.base.BaseActivity;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.LoadingDialog;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordActivity extends BaseActivity {

    // ─── Interfaces ──────────────────────────────────────────────────────────

    private interface TextValidation {
        void onValidated(String text);
    }


    // ─── UI Components & Variables ───────────────────────────────────────────

    private TextInputEditText etUsername, etOldPassword, etNewPassword;
    private TextView tvUsernameError, tvOldPasswordError, tvNewPasswordError;
    private Button btnChangePassword;

    private LoadingDialog loadingDialog;


    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initializeComponents();
        setupInputListeners();

        btnChangePassword.setOnClickListener(v -> attemptPasswordChange());
    }


    // ─── Initialization ──────────────────────────────────────────────────────

    private void initializeComponents() {
        etUsername = findViewById(R.id.etUsername);
        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);

        tvUsernameError = findViewById(R.id.tvUsernameError);
        tvOldPasswordError = findViewById(R.id.tvOldPasswordError);
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        loadingDialog = new LoadingDialog(this);

        LottieAnimationView lottie = findViewById(R.id.lottieAnimationView);
        lottie.setAnimation(R.raw.login_animation);
        lottie.playAnimation();

        applyFont(
                etUsername,
                etOldPassword,
                etNewPassword,
                tvUsernameError,
                tvOldPasswordError,
                tvNewPasswordError,
                btnChangePassword
        );
    }

    private void setupInputListeners() {
        etUsername.addTextChangedListener(createWatcher(this::validateUsername));
        etOldPassword.addTextChangedListener(createWatcher(s -> hideError(tvOldPasswordError)));
        etNewPassword.addTextChangedListener(createWatcher(this::validateNewPassword));
    }

    private TextWatcher createWatcher(TextValidation callback) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                callback.onValidated(s.toString());
            }
        };
    }


    // ─── Validation ──────────────────────────────────────────────────────────

    private void validateUsername(String username) {
        if (username.isEmpty()) {
            hideError(tvUsernameError);
            return;
        }
        if (UserSession.isUsernameInvalid(username)) {
            showError(tvUsernameError, R.string.username_error_message);
        } else {
            hideError(tvUsernameError);
        }
    }

    private void validateNewPassword(String password) {
        if (password.isEmpty()) {
            hideError(tvNewPasswordError);
            return;
        }
        if (UserSession.isPasswordInvalid(password)) {
            showError(tvNewPasswordError, R.string.password_error_message);
        } else {
            hideError(tvNewPasswordError);
        }
    }

    private boolean validateInputFields(String username, String oldPassword, String newPassword) {
        boolean hasError = false;

        if (username.isEmpty()) {
            showError(tvUsernameError, R.string.field_required);
            hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            showError(tvUsernameError, R.string.username_error_message);
            hasError = true;
        }

        if (oldPassword.isEmpty()) {
            showError(tvOldPasswordError, R.string.field_required);
            hasError = true;
        }

        if (newPassword.isEmpty()) {
            showError(tvNewPasswordError, R.string.field_required);
            hasError = true;
        } else if (UserSession.isPasswordInvalid(newPassword)) {
            showError(tvNewPasswordError, R.string.password_error_message);
            hasError = true;
        }

        if (!newPassword.isEmpty() && newPassword.equals(oldPassword)) {
            showError(tvNewPasswordError, R.string.new_password_same_error);
            hasError = true;
        }

        return hasError;
    }


    // ─── Password Change Process ─────────────────────────────────────────────

    private void attemptPasswordChange() {
        final String username = getText(etUsername);
        final String oldPassword = getText(etOldPassword);
        final String newPassword = getText(etNewPassword);

        hideAllErrors();

        if (validateInputFields(username, oldPassword, newPassword)) {
            showToast(getString(R.string.error_fix_fields));
            return;
        }

        loadingDialog.show();

        new Handler().postDelayed(() -> {
            UserSession session = UserSession.getInstance();
            boolean success = session.resetPassword(username, oldPassword, newPassword);

            loadingDialog.dismiss();

            if (success) {
                showToast(getString(R.string.success_password_changed));
                finish();
            } else {
                handlePasswordResetError(session, username);
            }
        }, 2000);
    }

    private void handlePasswordResetError(UserSession session, String username) {
        if (session.getUserData(username) != null) {
            showError(tvOldPasswordError, R.string.incorrect_old_password);
            showToast(getString(R.string.incorrect_old_password));
        } else {
            showError(tvUsernameError, R.string.username_not_found);
            showToast(getString(R.string.username_not_found));
        }
    }


    // ─── UI Utilities ────────────────────────────────────────────────────────

    private void hideError(TextView tv) {
        tv.setVisibility(View.GONE);
    }

    private void hideAllErrors() {
        hideError(tvUsernameError);
        hideError(tvOldPasswordError);
        hideError(tvNewPasswordError);
    }

    private void showError(TextView tv, int resId) {
        tv.setText(resId);
        tv.setVisibility(View.VISIBLE);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}