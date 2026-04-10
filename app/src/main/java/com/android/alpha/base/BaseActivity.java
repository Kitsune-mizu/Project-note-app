package com.android.alpha.base;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.android.alpha.R;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    protected void applyTheme() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        String mode = prefs.getString("theme_mode", "system");
        String colorTheme = prefs.getString("color_theme", "blue");

        // APPLY COLOR THEME
        switch (colorTheme) {
            case "purple":
                setTheme(R.style.Theme_Alpha_Purple);
                break;
            case "pink":
                setTheme(R.style.Theme_Alpha_Pink);
                break;
            case "green":
                setTheme(R.style.Theme_Alpha_Green);
                break;
            case "orange":
                setTheme(R.style.Theme_Alpha_Orange);
                break;
            default:
                setTheme(R.style.Theme_Alpha);
                break;
        }

        // APPLY DARK MODE
        switch (mode) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}