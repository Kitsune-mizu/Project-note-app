package com.android.kitsune.ui.auth;

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
import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.LoadingDialog;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText etUsername, etOldPassword, etNewPassword;
    private TextView tvUsernameError, tvOldPasswordError, tvNewPasswordError;
    private Button btnChangePassword;
    private LoadingDialog loadingDialog;

    // --- Activity Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initializeComponents();
        setupInputListeners();
        setupClickListeners();
    }

    // --- Initialization Methods ---

    private void initializeComponents() {
        etUsername = findViewById(R.id.etUsername);
        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);

        tvUsernameError = findViewById(R.id.tvUsernameError);
        tvOldPasswordError = findViewById(R.id.tvOldPasswordError);
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        loadingDialog = new LoadingDialog(this);

        // Setup Lottie Animation
        LottieAnimationView lottieAnimationView = findViewById(R.id.lottieAnimationView);
        lottieAnimationView.setAnimation(R.raw.login_animation);
        lottieAnimationView.playAnimation();
    }

    private void setupClickListeners() {
        btnChangePassword.setOnClickListener(v -> attemptPasswordChange());
    }

    private void setupInputListeners() {
        etUsername.addTextChangedListener(createWatcher(this::validateUsername));
        etOldPassword.addTextChangedListener(createWatcher((s) -> tvOldPasswordError.setVisibility(View.GONE)));
        etNewPassword.addTextChangedListener(createWatcher(this::validateNewPassword));
    }

    private TextWatcher createWatcher(TextValidation validation) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { validation.onValidated(s.toString()); }
        };
    }

    private interface TextValidation {
        void onValidated(String text);
    }

    // --- Validation Logic ---

    private void validateUsername(String username) {
        if (username.isEmpty()) {
            tvUsernameError.setVisibility(View.GONE);
            return;
        }
        // isUsernameInvalid returns TRUE if the format is wrong
        if (UserSession.isUsernameInvalid(username)) {
            tvUsernameError.setText(R.string.username_error_message);
            tvUsernameError.setVisibility(View.VISIBLE);
        } else {
            tvUsernameError.setVisibility(View.GONE);
        }
    }

    private void validateNewPassword(String password) {
        if (password.isEmpty()) {
            tvNewPasswordError.setVisibility(View.GONE);
            return;
        }
        // isPasswordInvalid returns TRUE if the format is wrong
        if (UserSession.isPasswordInvalid(password)) {
            tvNewPasswordError.setText(R.string.password_error_message);
            tvNewPasswordError.setVisibility(View.VISIBLE);
        } else {
            tvNewPasswordError.setVisibility(View.GONE);
        }
    }

    // --- Core Functionality ---

    private void attemptPasswordChange() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String oldPassword = etOldPassword.getText() != null ? etOldPassword.getText().toString().trim() : "";
        String newPassword = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";

        // Reset previous errors
        tvUsernameError.setVisibility(View.GONE);
        tvOldPasswordError.setVisibility(View.GONE);
        tvNewPasswordError.setVisibility(View.GONE);

        if (validateInputFields(username, oldPassword, newPassword)) {
            showToast(getString(R.string.error_fix_fields));
            return;
        }

        loadingDialog.show();

        // Simulate network delay
        new Handler().postDelayed(() -> {
            UserSession session = UserSession.getInstance();
            boolean success = session.resetPassword(username, oldPassword, newPassword);
            loadingDialog.dismiss();

            if (success) {
                showToast(getString(R.string.success_password_changed));
                finish();
            } else {
                handleLoginError(session, username);
            }
        }, 2000);
    }

    private boolean validateInputFields(String username, String oldPassword, String newPassword) {
        boolean hasError = false;

        // Username validation (required and format)
        if (username.isEmpty()) {
            tvUsernameError.setText(R.string.field_required);
            tvUsernameError.setVisibility(View.VISIBLE);
            hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            tvUsernameError.setText(R.string.username_error_message);
            tvUsernameError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        // Old password validation (required)
        if (oldPassword.isEmpty()) {
            tvOldPasswordError.setText(R.string.field_required);
            tvOldPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        // New password validation (required and format)
        if (newPassword.isEmpty()) {
            tvNewPasswordError.setText(R.string.field_required);
            tvNewPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        } else if (UserSession.isPasswordInvalid(newPassword)) {
            tvNewPasswordError.setText(R.string.password_error_message);
            tvNewPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        // New password must be different from the old one
        if (newPassword.equals(oldPassword) && !newPassword.isEmpty()) {
            tvNewPasswordError.setText(R.string.new_password_same_error);
            tvNewPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        return hasError;
    }

    private void handleLoginError(UserSession session, String username) {
        // isUsername returns TRUE if the username DOES NOT EXIST
        if (session.isUsername(username)) {
            // Incorrect old password
            tvOldPasswordError.setText(getString(R.string.incorrect_old_password));
            tvOldPasswordError.setVisibility(View.VISIBLE);
            showToast(getString(R.string.incorrect_old_password));
        } else {
            // Username not found
            tvUsernameError.setText(getString(R.string.username_not_found));
            tvUsernameError.setVisibility(View.VISIBLE);
            showToast(getString(R.string.username_not_found));
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}