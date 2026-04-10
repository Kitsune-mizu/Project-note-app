package com.android.alpha.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;
import com.android.alpha.base.BaseActivity;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.ui.main.MainActivity;
import com.android.alpha.utils.LoadingDialog;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends BaseActivity {

    // ─── CONSTANTS ─────────────────────────────────────────────────────────────
    private static final String PREFS_NAME   = "login_prefs";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    // ─── INTERFACE ─────────────────────────────────────────────────────────────

    /** Functional interface for validating text input on change. */
    private interface TextValidation { void onValidated(String text); }

    // ─── UI COMPONENTS ─────────────────────────────────────────────────────────
    private EditText        etUsername, etPassword;
    private TextInputLayout tilUsername, tilPassword;
    private CheckBox        cbRememberMe;
    private Button          btnLogin;
    private TextView        tvSignUp, tvForgotPassword;

    // ─── DEPENDENCIES ──────────────────────────────────────────────────────────
    private LoadingDialog loadingDialog;

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeComponents();
        loadSavedCredentials();
        setupInputListeners();
        setupClickListeners();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Binds all views, initializes the loading dialog, and starts the Lottie animation. */
    private void initializeComponents() {
        tilUsername      = findViewById(R.id.tilUsername);
        tilPassword      = findViewById(R.id.tilPassword);
        etUsername       = findViewById(R.id.etUsername);
        etPassword       = findViewById(R.id.etPassword);
        btnLogin         = findViewById(R.id.btnLogin);
        tvSignUp         = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        cbRememberMe     = findViewById(R.id.cbRememberMe);
        loadingDialog    = new LoadingDialog(this);

        LottieAnimationView lottie = findViewById(R.id.lottieAnimationView);
        lottie.setAnimation(R.raw.login_animation);
        lottie.playAnimation();

        Typeface tf = getFont();

        etUsername.setTypeface(tf);
        etPassword.setTypeface(tf);

        btnLogin.setTypeface(tf);
        tvSignUp.setTypeface(tf);
        tvForgotPassword.setTypeface(tf);
        cbRememberMe.setTypeface(tf);
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

    /** Attaches real-time TextWatchers for inline validation on username and password fields. */
    private void setupInputListeners() {
        etUsername.addTextChangedListener(createWatcher(this::validateUsername));
        etPassword.addTextChangedListener(createWatcher(this::validatePasswordFormat));
    }

    /**
     * Creates a TextWatcher that invokes the given callback after each text change.
     * @param validation the validation logic to run on text change.
     */
    private TextWatcher createWatcher(TextValidation validation) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { validation.onValidated(s.toString()); }
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CLICK LISTENERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Sets up click listeners for login, sign-up, and forgot-password actions. */
    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        // Navigate to SignUpActivity after a short loading delay
        tvSignUp.setOnClickListener(v -> {
            showLoading();
            new Handler().postDelayed(() -> {
                loadingDialog.dismiss();
                startActivity(new Intent(this, SignUpActivity.class));
            }, 1500);
        });

        // Navigate to ForgotPasswordActivity, hiding the keyboard first
        tvForgotPassword.setOnClickListener(v -> {
            hideKeyboard(v);
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGIN LOGIC
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reads credentials from input, validates them, then attempts login after a short delay.
     * On success, saves credentials and navigates to MainActivity.
     * On failure, shows the appropriate field error.
     */
    private void attemptLogin() {
        String username = etUsername.getText() == null ? "" : etUsername.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();

        hideKeyboard(etPassword);

        if (validateInputFields(username, password)) return;

        showLoading();
        new Handler().postDelayed(() -> {
            loadingDialog.dismiss();
            UserSession session = UserSession.getInstance();

            // Username not found in registered users map
            if (session.getUserData(username) == null) {
                tilUsername.setError(getString(R.string.username_not_found));
                tilPassword.setError(null);
                return;
            }

            if (session.login(username, password)) {
                saveCredentials(username, password);
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                // Username exists but password is incorrect
                tilPassword.setError(getString(R.string.incorrect_password));
                tilUsername.setError(null);
            }
        }, 1500);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Validates both input fields before submitting login.
     * @return true if any field has a validation error, false if all inputs are valid.
     */
    private boolean validateInputFields(String username, String password) {
        boolean hasError = false;

        if (username.isEmpty()) {
            tilUsername.setError(getString(R.string.username_required)); hasError = true;
        } else if (UserSession.isUsernameInvalid(username)) {
            tilUsername.setError(getString(R.string.username_format_error)); hasError = true;
        } else {
            tilUsername.setError(null);
        }

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.password_required)); hasError = true;
        } else if (UserSession.isPasswordInvalid(password)) {
            tilPassword.setError(getString(R.string.password_format_error)); hasError = true;
        } else {
            tilPassword.setError(null);
        }

        return hasError;
    }

    /**
     * Validates the username field inline as the user types.
     * Shows an error if the username is not found in the registered users map.
     */
    private void validateUsername(String username) {
        if (username.isEmpty()) { tilUsername.setError(null); return; }
        if (UserSession.getInstance().getUserData(username) == null)
            tilUsername.setError(getString(R.string.username_not_found));
        else
            tilUsername.setError(null);
    }

    /**
     * Validates the password format inline as the user types.
     * Shows an error if the password does not meet format requirements.
     */
    private void validatePasswordFormat(String password) {
        if (password.isEmpty()) { tilPassword.setError(null); return; }
        tilPassword.setError(UserSession.isPasswordInvalid(password)
                ? getString(R.string.password_format_error) : null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REMEMBER ME / CREDENTIAL PERSISTENCE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Saves or clears credentials in SharedPreferences based on the "Remember Me" checkbox state.
     * Only saves if the checkbox is checked.
     */
    private void saveCredentials(String username, String password) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        if (cbRememberMe.isChecked()) {
            editor.putBoolean(KEY_REMEMBER, true)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password);
        } else {
            editor.clear();
        }
        editor.apply();
    }

    /**
     * Pre-fills the username and password fields if "Remember Me" was previously checked.
     * Also restores the checkbox state.
     */
    private void loadSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_REMEMBER, false)) {
            etUsername.setText(prefs.getString(KEY_USERNAME, ""));
            etPassword.setText(prefs.getString(KEY_PASSWORD, ""));
            cbRememberMe.setChecked(true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Shows the loading dialog if it is not already visible. */
    private void showLoading() {
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    /** Hides the soft keyboard from the given view's window. */
    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}