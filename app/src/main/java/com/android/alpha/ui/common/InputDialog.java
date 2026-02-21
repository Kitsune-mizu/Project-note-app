package com.android.alpha.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.alpha.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

/**
 * A BottomSheetDialogFragment that presents a simple text input dialog
 * with a title, pre-filled value, and confirm/cancel actions.
 */
@SuppressWarnings("unused")
public class InputDialog extends BottomSheetDialogFragment {

    // ─── INTERFACE ─────────────────────────────────────────────────────────────

    /** Callback triggered when the user confirms the entered text. */
    public interface InputDialogListener {
        void onTextEntered(String newText);
    }

    // ─── STATE ─────────────────────────────────────────────────────────────────
    private InputDialogListener listener;

    // ══════════════════════════════════════════════════════════════════════════
    // FACTORY METHOD
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a new InputDialog instance with the given title and pre-filled value.
     * @param title        the dialog title displayed at the top.
     * @param initialValue the text pre-filled in the input field.
     * @param listener     callback to receive the confirmed text.
     */
    public static InputDialog newInstance(String title, String initialValue, InputDialogListener listener) {
        InputDialog dialog = new InputDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("value", initialValue);
        dialog.setArguments(args);
        dialog.setListener(listener);
        return dialog;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTENER
    // ══════════════════════════════════════════════════════════════════════════

    /** Sets the listener that receives the confirmed input text. */
    public void setListener(InputDialogListener listener) {
        this.listener = listener;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Inflates the dialog layout, binds views, applies argument values,
     * and wires up confirm/cancel button actions.
     */
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.dialog_input, container, false);

        // Read arguments safely
        Bundle args        = getArguments();
        String title       = args == null ? "" : args.getString("title", "");
        String initialValue = args == null ? "" : args.getString("value", "");

        // Bind views
        TextView       tvTitle    = view.findViewById(R.id.tvDialogTitle);
        EditText       etInput    = view.findViewById(R.id.etInput);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);
        MaterialButton btnCancel  = view.findViewById(R.id.btnCancel);

        // Apply initial values
        tvTitle.setText(title);
        etInput.setText(initialValue);

        // Button actions
        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (listener != null) listener.onTextEntered(etInput.getText().toString());
            dismiss();
        });

        return view;
    }

    /** Returns the custom bottom sheet theme style. */
    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }
}