package com.android.alpha.ui.auth;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.utils.LoadingDialog;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Activity that allows a user to reset their password
 * by providing their username, old password, and a new password.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    // ─── INTERFACE ─────────────────────────────────────────────────────────────

    /** Functional interface for validating text input on change. */
    private interface TextValidation { void onValidated(String text); }

    // ─── UI COMPONENTS ─────────────────────────────────────────────────────────
    private TextInputEditText etUsername, etOldPassword, etNewPassword;
    private TextView          tvUsernameError, tvOldPasswordError, tvNewPasswordError;
    private Button            btnChangePassword;

    // ─── DEPENDENCIES ──────────────────────────────────────────────────────────
    private LoadingDialog loadingDialog;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initializeComponents();
        setupInputListeners();
        btnChangePassword.setOnClickListener(v -> attemptPasswordChange());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Binds all views and sets up the loading dialog and Lottie animation. */
    private void initializeComponents() {
        etUsername    = findViewById(R.id.etUsername);
        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);

        tvUsernameError    = findViewById(R.id.tvUsernameError);
        tvOldPasswordError = findViewById(R.id.tvOldPasswordError);
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        loadingDialog     = new LoadingDialog(this);

        LottieAnimationView lottie = findViewById(R.id.lottieAnimationView);
        lottie.setAnimation(R.raw.login_animation);
        lottie.playAnimation();

        Typeface tf = getFont();

        etUsername.setTypeface(tf);
        etOldPassword.setTypeface(tf);
        etNewPassword.setTypeface(tf);

        tvUsernameError.setTypeface(tf);
        tvOldPasswordError.setTypeface(tf);
        tvNewPasswordError.setTypeface(tf);

        btnChangePassword.setTypeface(tf);
    }

    private Typeface getFont() {
        try {
            return androidx.core.content.res.ResourcesCompat.getFont(
                    this, R.font.linottesemibold);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INPUT LISTENERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Attaches real-time TextWatchers to each input field for inline validation. */
    private void setupInputListeners() {
        etUsername.addTextChangedListener(createWatcher(this::validateUsername));
        etOldPassword.addTextChangedListener(createWatcher(s -> hideError(tvOldPasswordError)));
        etNewPassword.addTextChangedListener(createWatcher(this::validateNewPassword));
    }

    /**
     * Creates a TextWatcher that invokes the given callback on text change.
     * @param callback the validation logic to run after text changes.
     */
    private TextWatcher createWatcher(TextValidation callback) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { callback.onValidated(s.toString()); }
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Validates the username field inline and shows/hides the error view accordingly. */
    private void validateUsername(String username) {
        if (username.isEmpty()) { hideError(tvUsernameError); return; }
        if (UserSession.isUsernameInvalid(username)) showError(tvUsernameError, R.string.username_error_message);
        else                                         hideError(tvUsernameError);
    }

    /** Validates the new password field inline and shows/hides the error view accordingly. */
    private void validateNewPassword(String password) {
        if (password.isEmpty()) { hideError(tvNewPasswordError); return; }
        if (UserSession.isPasswordInvalid(password)) showError(tvNewPasswordError, R.string.password_error_message);
        else                                         hideError(tvNewPasswordError);
    }

    /**
     * Validates all three input fields before submitting the password change request.
     * @return true if any field has a validation error, false if all inputs are valid.
     */
    private boolean validateInputFields(String username, String oldPassword, String newPassword) {
        boolean hasError = false;

        if (username.isEmpty()) {
            showError(tvUsernameError, R.string.field_required); hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            showError(tvUsernameError, R.string.username_error_message); hasError = true;
        }

        if (oldPassword.isEmpty()) {
            showError(tvOldPasswordError, R.string.field_required); hasError = true;
        }

        if (newPassword.isEmpty()) {
            showError(tvNewPasswordError, R.string.field_required); hasError = true;
        } else if (UserSession.isPasswordInvalid(newPassword)) {
            showError(tvNewPasswordError, R.string.password_error_message); hasError = true;
        }

        if (!newPassword.isEmpty() && newPassword.equals(oldPassword)) {
            showError(tvNewPasswordError, R.string.new_password_same_error); hasError = true;
        }

        return hasError;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PASSWORD CHANGE PROCESS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reads input fields, runs validation, then attempts the password reset
     * after a short loading delay. Finishes the activity on success.
     */
    private void attemptPasswordChange() {
        final String username    = getText(etUsername);
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
            boolean success     = session.resetPassword(username, oldPassword, newPassword);

            loadingDialog.dismiss();

            if (success) {
                showToast(getString(R.string.success_password_changed));
                finish();
            } else {
                handlePasswordResetError(session, username);
            }
        }, 2000);
    }

    /**
     * Shows the appropriate error when password reset fails.
     * Distinguishes between "username not found" and "incorrect old password".
     */
    private void handlePasswordResetError(UserSession session, String username) {
        if (session.getUserData(username) != null) {
            showError(tvOldPasswordError, R.string.incorrect_old_password);
            showToast(getString(R.string.incorrect_old_password));
        } else {
            showError(tvUsernameError, R.string.username_not_found);
            showToast(getString(R.string.username_not_found));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Hides the given error TextView. */
    private void hideError(TextView tv) { tv.setVisibility(View.GONE); }

    /** Hides all three error TextViews at once. */
    private void hideAllErrors() {
        hideError(tvUsernameError);
        hideError(tvOldPasswordError);
        hideError(tvNewPasswordError);
    }

    /** Sets the given string resource as the error text and makes the TextView visible. */
    private void showError(TextView tv, int resId) {
        tv.setText(resId);
        tv.setVisibility(View.VISIBLE);
    }

    /** Safely extracts trimmed text from a TextInputEditText, returning empty string if null. */
    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    /** Shows a short Toast message. */
    private void showToast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}