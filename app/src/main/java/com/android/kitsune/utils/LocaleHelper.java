package com.android.kitsune.utils;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    // ─── Locale Setting ──────────────────────────────────────────────────────

    public static Context setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        return context.createConfigurationContext(getUpdatedConfig(context, locale));
    }


    // ─── Configuration Update ────────────────────────────────────────────────

    private static Configuration getUpdatedConfig(Context context, Locale locale) {
        Configuration config = context.getResources().getConfiguration();
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return config;
    }
}