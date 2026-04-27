package com.android.kitsune.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.kitsune.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

@SuppressWarnings("unused")
public class InputDialog extends BottomSheetDialogFragment {

    // ─── Interfaces ──────────────────────────────────────────────────────────

    public interface InputDialogListener {
        void onTextEntered(String newText);
    }


    // ─── Variables ───────────────────────────────────────────────────────────

    private InputDialogListener listener;


    // ─── Factory Method ──────────────────────────────────────────────────────

    public static InputDialog newInstance(String title, String initialValue, InputDialogListener listener) {
        InputDialog dialog = new InputDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("value", initialValue);
        dialog.setArguments(args);
        dialog.setListener(listener);
        return dialog;
    }


    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.dialog_input, container, false);

        Bundle args = getArguments();
        String title = args == null ? "" : args.getString("title", "");
        String initialValue = args == null ? "" : args.getString("value", "");

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        EditText etInput = view.findViewById(R.id.etInput);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        tvTitle.setText(title);
        etInput.setText(initialValue);

        btnCancel.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTextEntered(etInput.getText().toString());
            }
            dismiss();
        });

        return view;
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }


    // ─── Listeners & Setters ─────────────────────────────────────────────────

    public void setListener(InputDialogListener listener) {
        this.listener = listener;
    }
}