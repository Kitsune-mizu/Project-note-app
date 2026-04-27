package com.android.kitsune.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.LoadingDialog;

public class SignUpActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private TextView tvLoginLink, tvUsernameError, tvPasswordError, tvConfirmPasswordError;
    private LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initializeViews();
        setupAnimations();
        setupClickListeners();
        setupTextWatchers();
    }

    private void initializeViews() {
        etUsername = findViewById(R.id.etUsernameSignUp);
        etPassword = findViewById(R.id.etPasswordSignUp);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        tvLoginLink = findViewById(R.id.tvLoginLink);

        tvUsernameError = findViewById(R.id.tvUsernameError);
        tvPasswordError = findViewById(R.id.tvPasswordError);
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError);

        loadingDialog = new LoadingDialog(this);
    }

    private void setupAnimations() {
        LottieAnimationView lottieAnimationView = findViewById(R.id.lottieAnimationViewSignUp);
        lottieAnimationView.setAnimation(R.raw.signup_animation);
        lottieAnimationView.playAnimation();
    }

    private void setupClickListeners() {
        btnSignUp.setOnClickListener(v -> attemptSignUp());

        tvLoginLink.setOnClickListener(v -> {
            showLoading();
            new Handler().postDelayed(() -> {
                loadingDialog.dismiss();
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                finish();
            }, 1200);
        });
    }

    private void setupTextWatchers() {
        etUsername.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { validateUsername(s.toString()); }
        });

        etPassword.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                validatePassword(s.toString());
                validateConfirmPassword();
            }
        });

        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { validateConfirmPassword(); }
        });
    }

    private void validateUsername(String username) {
        if (username.isEmpty()) {
            tvUsernameError.setVisibility(View.GONE);
            return;
        }

        if (UserSession.isUsernameInvalid(username)) {
            tvUsernameError.setText(R.string.username_error_message);
            tvUsernameError.setVisibility(View.VISIBLE);
        } else {
            tvUsernameError.setVisibility(View.GONE);
        }
    }

    private void validatePassword(String password) {
        if (password.isEmpty()) {
            tvPasswordError.setVisibility(View.GONE);
            return;
        }

        if (UserSession.isPasswordInvalid(password)) {
            tvPasswordError.setText(R.string.password_error_message);
            tvPasswordError.setVisibility(View.VISIBLE);
        } else {
            tvPasswordError.setVisibility(View.GONE);
        }
    }

    private void validateConfirmPassword() {
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (confirmPassword.isEmpty()) {
            tvConfirmPasswordError.setVisibility(View.GONE);
            return;
        }

        if (!password.equals(confirmPassword)) {
            tvConfirmPasswordError.setText(R.string.confirm_password_error_message);
            tvConfirmPasswordError.setVisibility(View.VISIBLE);
        } else {
            tvConfirmPasswordError.setVisibility(View.GONE);
        }
    }

    private void attemptSignUp() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = performFinalValidation(username, password, confirmPassword);

        if (hasError) {
            Toast.makeText(this, "Please fix the errors above.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading();

        new Handler().postDelayed(() -> {
            loadingDialog.dismiss();

            UserSession session = UserSession.getInstance();
            boolean success = session.registerUser(username, password);

            if (success) {
                Toast.makeText(this, "Sign Up Successful! Please login.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                finish();
            } else {
                // Asumsi kegagalan utama setelah validasi format adalah username sudah ada.
                tvUsernameError.setText(R.string.username_already_exists);
                tvUsernameError.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Username already in use, please choose another.", Toast.LENGTH_LONG).show();
            }
        }, 1500);
    }

    private boolean performFinalValidation(String username, String password, String confirmPassword) {
        boolean hasError = false;

        tvUsernameError.setVisibility(View.GONE);
        tvPasswordError.setVisibility(View.GONE);
        tvConfirmPasswordError.setVisibility(View.GONE);

        if (username.isEmpty()) {
            tvUsernameError.setText(R.string.field_required);
            tvUsernameError.setVisibility(View.VISIBLE);
            hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            tvUsernameError.setText(R.string.username_error_message);
            tvUsernameError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        if (password.isEmpty()) {
            tvPasswordError.setText(R.string.field_required);
            tvPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        } else if (UserSession.isPasswordInvalid(password)) {
            tvPasswordError.setText(R.string.password_error_message);
            tvPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        if (confirmPassword.isEmpty()) {
            tvConfirmPasswordError.setText(R.string.field_required);
            tvConfirmPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        } else if (!password.equals(confirmPassword)) {
            tvConfirmPasswordError.setText(R.string.confirm_password_error_message);
            tvConfirmPasswordError.setVisibility(View.VISIBLE);
            hasError = true;
        }

        return hasError;
    }

    private void showLoading() {
        if (loadingDialog != null && !loadingDialog.isShowing()) {
            loadingDialog.show();
        }
    }
}