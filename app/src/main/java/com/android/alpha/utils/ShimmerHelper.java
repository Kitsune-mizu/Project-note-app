package com.android.alpha.utils;

import android.view.View;
import android.view.animation.AlphaAnimation;

import com.facebook.shimmer.ShimmerFrameLayout;

/**
 * Utility class untuk mengelola tampilan shimmer loading dan transisi ke konten asli.
 * Mendukung animasi fade-in saat konten ditampilkan kembali.
 */
public class ShimmerHelper {

    // --- Shimmer Control ---

    /**
     * Tampilkan shimmer dan sembunyikan konten selama data sedang dimuat.
     *
     * @param shimmerLayout Layout shimmer yang akan ditampilkan
     * @param contentViews  View konten yang disembunyikan selama shimmer aktif
     */
    public static void show(ShimmerFrameLayout shimmerLayout, View... contentViews) {
        if (shimmerLayout == null) return;
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        hideContent(contentViews);
    }

    /**
     * Hentikan shimmer dan tampilkan konten kembali dengan animasi fade-in.
     *
     * @param shimmerLayout Layout shimmer yang akan disembunyikan
     * @param contentViews  View konten yang ditampilkan setelah shimmer selesai
     */
    public static void hide(ShimmerFrameLayout shimmerLayout, View... contentViews) {
        if (shimmerLayout == null) return;
        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);
        showContentWithFade(contentViews);
    }

    // --- Visibility & Animation Helpers ---

    /** Sembunyikan semua view konten (INVISIBLE agar tetap menempati ruang layout) */
    private static void hideContent(View... views) {
        for (View v : views)
            if (v != null) v.setVisibility(View.INVISIBLE);
    }

    /** Tampilkan setiap view konten dengan animasi fade-in */
    private static void showContentWithFade(View... views) {
        for (View v : views) {
            if (v != null) {
                v.setVisibility(View.VISIBLE);
                applyFadeIn(v);
            }
        }
    }

    /** Jalankan animasi fade-in dari alpha 0.3 ke 1.0 selama 400ms */
    private static void applyFadeIn(View view) {
        AlphaAnimation fadeIn = new AlphaAnimation(0.3f, 1f);
        fadeIn.setDuration(400);
        fadeIn.setFillAfter(true);
        view.startAnimation(fadeIn);
    }
}