package com.android.kitsune.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.android.kitsune.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

public class DialogUtils {

    // Interface for dialog callbacks
    public interface DialogCallback {
        void onConfirm(String inputText);
    }

    // --- Confirmation and Input Dialogs ---

    @SuppressLint("InflateParams")
    public static void showConfirmDialog(
            Context context,
            String title,
            String message,
            String positiveText,
            String negativeText,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        MaterialButton btnPositive = view.findViewById(R.id.btnPositive);
        MaterialButton btnNegative = view.findViewById(R.id.btnNegative);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnPositive.setText(positiveText);
        btnNegative.setText(negativeText);

        btnPositive.setOnClickListener(v -> {
            if (onConfirm != null) onConfirm.run();
            dialog.dismiss();
        });
        btnNegative.setOnClickListener(v -> {
            if (onCancel != null) onCancel.run();
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    @SuppressLint("InflateParams")
    public static void showInputDialog(
            Context context,
            String title,
            String hint,
            String initialValue,
            String positiveText,
            String negativeText,
            DialogCallback callback
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_input, null);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        EditText etInput = view.findViewById(R.id.etInput);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        tvTitle.setText(title);
        etInput.setHint(hint);
        etInput.setText(initialValue);
        btnConfirm.setText(positiveText);
        btnCancel.setText(negativeText);

        btnConfirm.setOnClickListener(v -> {
            if (callback != null)
                callback.onConfirm(etInput.getText().toString());
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }

    // --- Information and Time-Based Dialogs ---

    @SuppressLint("InflateParams")
    public static void showInfoDialog(
            Context context,
            String title,
            String message,
            String buttonText,
            Runnable onClose
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_info, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        MaterialButton btnOk = view.findViewById(R.id.btnOk);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnOk.setText(buttonText);

        btnOk.setOnClickListener(v -> {
            if (onClose != null) onClose.run();
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    @SuppressLint("InflateParams")
    public static void showCountdownDialog(
            Context context,
            String title,
            String message,
            String positiveText,
            String negativeText,
            int countdownSeconds,
            Runnable onNext
    ) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        // Using the confirmation dialog layout for the countdown
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        MaterialButton btnPositive = view.findViewById(R.id.btnPositive);
        MaterialButton btnNegative = view.findViewById(R.id.btnNegative);

        // Set initial content and disable the positive button
        tvTitle.setText(title);
        tvMessage.setText(message);
        btnNegative.setText(negativeText);
        btnPositive.setText(context.getString(R.string.text_with_countdown, positiveText, countdownSeconds));
        btnPositive.setEnabled(false);

        btnNegative.setOnClickListener(v -> dialog.dismiss());

        // Countdown logic
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable countdownRunnable = new Runnable() {
            int secondsLeft = countdownSeconds;

            @Override
            public void run() {
                if (secondsLeft > 0) {
                    btnPositive.setText(context.getString(R.string.text_with_countdown, positiveText, secondsLeft));
                    secondsLeft--;
                    handler.postDelayed(this, 1000);
                } else {
                    btnPositive.setText(positiveText);
                    btnPositive.setEnabled(true);
                    btnPositive.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (onNext != null) onNext.run();
                    });
                }
            }
        };
        handler.post(countdownRunnable);

        dialog.setContentView(view);
        dialog.show();
    }
}