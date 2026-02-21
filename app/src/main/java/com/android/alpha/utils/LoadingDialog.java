package com.android.alpha.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;

/**
 * Dialog loading dengan animasi Lottie.
 * Digunakan untuk memblokir interaksi pengguna saat proses background sedang berjalan.
 */
public class LoadingDialog extends Dialog {

    // --- Fields ---

    // View animasi Lottie yang ditampilkan selama loading
    private LottieAnimationView lottieAnimationView;

    // Menentukan apakah dialog bisa ditutup dengan menekan di luar area dialog
    private final boolean isCancelable;

    // --- Constructors ---

    /** Buat LoadingDialog yang tidak bisa dibatalkan pengguna (default) */
    public LoadingDialog(@NonNull Context context) {
        this(context, false);
    }

    /** Buat LoadingDialog dengan opsi cancelable yang ditentukan secara manual */
    public LoadingDialog(@NonNull Context context, boolean isCancelable) {
        super(context);
        this.isCancelable = isCancelable;
    }

    // --- Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDialog();
        setupAnimation();
    }

    /** Hentikan animasi saat dialog tidak lagi terlihat */
    @Override
    protected void onStop() {
        stopLottieAnimation();
        super.onStop();
    }

    /** Hentikan animasi sebelum dialog di-dismiss */
    @Override
    public void dismiss() {
        stopLottieAnimation();
        super.dismiss();
    }

    // --- Dialog Setup ---

    /** Konfigurasi dialog: hapus judul, set layout, cancelable, dan background transparan */
    private void setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_loading);
        setCancelable(isCancelable);

        Window window = getWindow();
        if (window != null)
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    // --- Animation Control ---

    /** Bind view Lottie dan mulai putar animasi loading */
    private void setupAnimation() {
        lottieAnimationView = findViewById(R.id.lottieLoading);
        if (lottieAnimationView != null) {
            lottieAnimationView.setAnimation(R.raw.loading_animation);
            lottieAnimationView.playAnimation();
        }
    }

    /** Hentikan animasi Lottie jika sedang berjalan */
    private void stopLottieAnimation() {
        if (lottieAnimationView != null && lottieAnimationView.isAnimating())
            lottieAnimationView.cancelAnimation();
    }
}