package com.android.kitsune.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.MainActivity;
import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.utils.LoadingDialog;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private TextInputLayout tilUsername, tilPassword;
    private CheckBox cbRememberMe;
    private LoadingDialog loadingDialog;

    // --- Components to fix 'never used' warnings ---
    private Button btnLogin;
    private TextView tvSignUp, tvForgotPassword;
    // -----------------------------------------------

    private static final String PREFS_NAME = "login_prefs";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    // --- Activity Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeComponents();
        loadSavedCredentials();
        setupInputListeners();
        setupClickListeners();
    }

    // --- Initialization ---

    private void initializeComponents() {
        // Layouts
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);

        // Inputs
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);

        // Buttons and Text (Now assigning to class member variables)
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);

        loadingDialog = new LoadingDialog(this);

        // Animation
        LottieAnimationView lottieAnimationView = findViewById(R.id.lottieAnimationView);
        lottieAnimationView.setAnimation(R.raw.login_animation);
        lottieAnimationView.playAnimation();
    }

    private void setupClickListeners() {
        // Using the member variables instead of findViewById(R.id.X) again
        btnLogin.setOnClickListener(v -> attemptLogin());

        tvSignUp.setOnClickListener(v -> {
            showLoading();
            new Handler().postDelayed(() -> {
                loadingDialog.dismiss();
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
            }, 1500);
        });

        tvForgotPassword.setOnClickListener(v -> {
            hideKeyboard(v);
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
        });
    }

    private void setupInputListeners() {
        etUsername.addTextChangedListener(createWatcher(this::validateUsername));
        etPassword.addTextChangedListener(createWatcher(this::validatePasswordFormat));
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

    // --- Validation and Core Logic ---

    private void attemptLogin() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        hideKeyboard(etPassword);

        // Perform final, comprehensive validation
        if (validateInputFields(username, password)) {
            return;
        }

        showLoading();

        new Handler().postDelayed(() -> {
            loadingDialog.dismiss();
            UserSession session = UserSession.getInstance();

            // isUsername returns TRUE if the username DOES NOT EXIST
            if (session.isUsername(username)) {
                // Diubah
                tilUsername.setError(getString(R.string.username_not_found));
                tilPassword.setError(null);
                return;
            }

            boolean success = session.login(username, password);

            if (success) {
                saveCredentials(username, password);
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            } else {
                // Diubah
                tilPassword.setError(getString(R.string.incorrect_password));
                tilUsername.setError(null);
            }
        }, 1500);
    }

    private boolean validateInputFields(String username, String password) {
        boolean hasError = false;

        // Username Validation
        if (username.isEmpty()) {
            // Diubah
            tilUsername.setError(getString(R.string.username_required));
            hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            // Diubah
            tilUsername.setError(getString(R.string.username_format_error));
            hasError = true;
        } else {
            tilUsername.setError(null);
        }

        // Password Validation
        if (password.isEmpty()) {
            // Diubah
            tilPassword.setError(getString(R.string.password_required));
            hasError = true;
        } else if (UserSession.isPasswordInvalid(password)) {
            // Diubah
            tilPassword.setError(getString(R.string.password_format_error));
            hasError = true;
        } else {
            tilPassword.setError(null);
        }

        return hasError;
    }

    // Validation for TextWatcher (Real-time feedback)
    private void validateUsername(String username) {
        if (username.isEmpty()) {
            tilUsername.setError(null);
            return;
        }

        // isUsername returns TRUE if the username DOES NOT EXIST
        if (UserSession.getInstance().isUsername(username)) {
            // Diubah
            tilUsername.setError(getString(R.string.username_not_found));
        } else {
            tilUsername.setError(null);
        }
    }

    // Validation for TextWatcher (Real-time feedback)
    private void validatePasswordFormat(String password) {
        if (password.isEmpty()) {
            tilPassword.setError(null);
            return;
        }

        if (UserSession.isPasswordInvalid(password)) {
            // Diubah
            tilPassword.setError(getString(R.string.password_format_error));
        } else {
            tilPassword.setError(null);
        }
    }

    // --- Persistence and Utility ---

    private void saveCredentials(String username, String password) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        if (cbRememberMe.isChecked()) {
            editor.putBoolean(KEY_REMEMBER, true);
            editor.putString(KEY_USERNAME, username);
            editor.putString(KEY_PASSWORD, password);
        } else {
            editor.clear();
        }
        editor.apply();
    }

    private void loadSavedCredentials() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean remember = preferences.getBoolean(KEY_REMEMBER, false);

        if (remember) {
            etUsername.setText(preferences.getString(KEY_USERNAME, ""));
            etPassword.setText(preferences.getString(KEY_PASSWORD, ""));
            cbRememberMe.setChecked(true);
        }
    }

    private void showLoading() {
        if (loadingDialog != null && !loadingDialog.isShowing()) {
            loadingDialog.show();
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}