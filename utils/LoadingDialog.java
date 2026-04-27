package com.android.kitsune.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.android.kitsune.R;

public class LoadingDialog extends Dialog {

    private LottieAnimationView lottieAnimationView;
    private final boolean isCancelable;

    // Default constructor: non-cancelable
    public LoadingDialog(@NonNull Context context) {
        this(context, false);
    }

    // Main constructor
    public LoadingDialog(@NonNull Context context, boolean isCancelable) {
        super(context);
        this.isCancelable = isCancelable;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove the default dialog title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_loading);

        // Make the background transparent
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Apply cancelable setting
        setCancelable(isCancelable);

        // Initialize and start Lottie animation
        lottieAnimationView = findViewById(R.id.lottieLoading);
        if (lottieAnimationView != null) {
            lottieAnimationView.setAnimation(R.raw.loading_animation);
            lottieAnimationView.playAnimation();
        }
    }

    @Override
    protected void onStop() {
        // Stop animation when the dialog is no longer visible
        stopLottieAnimation();
        super.onStop();
    }

    @Override
    public void dismiss() {
        // Ensure animation is cancelled before dismissing
        stopLottieAnimation();
        super.dismiss();
    }

    // Helper method to stop the animation
    private void stopLottieAnimation() {
        if (lottieAnimationView != null && lottieAnimationView.isAnimating()) {
            lottieAnimationView.cancelAnimation();
        }
    }
}