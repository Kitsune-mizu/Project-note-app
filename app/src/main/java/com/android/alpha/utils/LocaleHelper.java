package com.android.alpha.utils;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Utility class untuk mengubah bahasa (locale) aplikasi secara dinamis.
 * Mengembalikan Context baru yang sudah dikonfigurasi dengan locale yang dipilih.
 */
public class LocaleHelper {

    // --- Locale Setting ---

    /**
     * Terapkan locale baru ke Context dan kembalikan Context yang sudah diperbarui.
     * Perlu di-attach ke Activity/Application agar perubahan bahasa berlaku.
     *
     * @param context      Context saat ini
     * @param languageCode Kode bahasa ISO 639-1, contoh: "id", "en", "ar"
     * @return Context baru dengan locale yang sudah diterapkan
     */
    public static Context setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        return context.createConfigurationContext(getUpdatedConfig(context, locale));
    }

    // --- Configuration Update ---

    /**
     * Buat Configuration baru dengan locale dan arah layout yang diperbarui.
     * Arah layout otomatis menyesuaikan (LTR/RTL) berdasarkan locale.
     */
    private static Configuration getUpdatedConfig(Context context, Locale locale) {
        Configuration config = context.getResources().getConfiguration();
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return config;
    }
}