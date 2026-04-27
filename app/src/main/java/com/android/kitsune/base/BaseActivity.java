package com.android.kitsune.base;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;

import com.android.kitsune.R;
import com.android.kitsune.utils.LoadingDialog;

public class BaseActivity extends AppCompatActivity {

    private LoadingDialog loadingDialog;

    // ─── Lifecycle ─────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (loadingDialog != null) {
            if (loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
            loadingDialog = null;
        }
    }

    // ─── Theme & Configuration ─────────────────────────────────────

    protected void applyTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String mode = prefs.getString("theme_mode", "system");
        String colorTheme = prefs.getString("color_theme", "blue");

        applyColorTheme(colorTheme);
        applyDarkMode(mode);
    }

    private void applyColorTheme(String colorTheme) {
        switch (colorTheme) {
            case "purple":
                setTheme(R.style.Theme_Kitsune_Purple);
                break;
            case "pink":
                setTheme(R.style.Theme_Kitsune_Pink);
                break;
            case "green":
                setTheme(R.style.Theme_Kitsune_Green);
                break;
            case "orange":
                setTheme(R.style.Theme_Kitsune_Orange);
                break;
            case "blue":
            default:
                setTheme(R.style.Theme_Kitsune);
                break;
        }
    }

    private void applyDarkMode(String mode) {
        switch (mode) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "system":
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }


    // ─── Typography & Fonts ────────────────────────────────────────

    protected Typeface getAppFont() {
        try {
            return ResourcesCompat.getFont(this, R.font.linottesemibold);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    public void applyFont(View... views) {
        Typeface tf = getAppFont();
        for (View v : views) {
            if (v instanceof TextView) {
                ((TextView) v).setTypeface(tf);
            }
        }
    }


    // ─── Resource Utilities ────────────────────────────────────────

    public int getAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }


    // ─── Dialog Utilities (Added) ──────────────────────────────────

    public void showLoading() {
        if (isFinishing() || isDestroyed()) return;

        if (loadingDialog == null) {
            loadingDialog = new LoadingDialog(this);
        }

        if (!loadingDialog.isShowing()) {
            loadingDialog.show();
        }
    }

    public void hideLoading() {
        try {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                if (!isFinishing() && !isDestroyed()) {
                    loadingDialog.dismiss();
                }
            }
        } catch (Exception ignored) {}
    }
}