package com.android.kitsune.utils;

import android.view.View;
import android.view.animation.AlphaAnimation;

import com.facebook.shimmer.ShimmerFrameLayout;

public class ShimmerHelper {

    /**
     * Shows the Shimmer layout and starts its animation, while hiding the associated content views.
     *
     * @param shimmerLayout The ShimmerFrameLayout to show.
     * @param contentViews The content Views to hide.
     */
    public static void show(ShimmerFrameLayout shimmerLayout, View... contentViews) {
        if (shimmerLayout == null) return;

        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();

        for (View v : contentViews) {
            if (v != null) v.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Hides the Shimmer layout, stops its animation, and shows the associated content views
     * with a fade-in effect.
     *
     * @param shimmerLayout The ShimmerFrameLayout to hide.
     * @param contentViews The content Views to show with a fade-in.
     */
    public static void hide(ShimmerFrameLayout shimmerLayout, View... contentViews) {
        if (shimmerLayout == null) return;

        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);

        for (View v : contentViews) {
            if (v != null) {
                v.setVisibility(View.VISIBLE);

                // Configure and start fade-in animation
                AlphaAnimation fadeIn = new AlphaAnimation(0.3f, 1f);
                fadeIn.setDuration(400);
                fadeIn.setFillAfter(true);
                v.startAnimation(fadeIn);
            }
        }
    }
}