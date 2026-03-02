package com.android.alpha.ui.geminichat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;

import java.util.List;

/**
 * Adapter untuk menampilkan pesan dalam sesi chat Gemini.

 * PERUBAHAN dari versi asli:
 * - ViewHolder AI (TYPE_AI) kini menampilkan action bar di bawah bubble:
 *   • btnCopy       → salin teks ke clipboard
 *   • btnSaveAsNote → callback ke ChatActivity untuk simpan sebagai catatan
 * - Tambah interface {@link OnSaveNoteListener} agar ChatActivity
 *   bisa menangani logika simpan tanpa coupling ke Notes package.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── View types ─────────────────────────────────────────────────────────────
    private static final int TYPE_USER    = ChatMessage.TYPE_USER;
    private static final int TYPE_AI      = ChatMessage.TYPE_AI;
    private static final int TYPE_LOADING = ChatMessage.TYPE_LOADING;
    private static final int TYPE_ERROR   = ChatMessage.TYPE_ERROR;

    // ── Data ───────────────────────────────────────────────────────────────────
    private final List<ChatMessage> messages;

    // ── Listener ───────────────────────────────────────────────────────────────
    /**
     * Callback yang dipanggil saat user menekan "Jadikan Catatan"
     * pada bubble AI. ChatActivity yang mengimplementasi listener ini.
     */
    public interface OnSaveNoteListener {
        void onSaveNote(String content);
    }

    /** Callback retry dari bubble error */
    public interface OnRetryListener {
        void onRetry();
    }

    private OnRetryListener retryListener;

    public void setRetryListener(OnRetryListener listener) {
        this.retryListener = listener;
    }

    private OnSaveNoteListener saveNoteListener;

    public void setSaveNoteListener(OnSaveNoteListener listener) {
        this.saveNoteListener = listener;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════════════

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADAPTER OVERRIDES
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return switch (viewType) {
            case TYPE_USER    -> new UserViewHolder(
                    inflater.inflate(R.layout.item_message_user, parent, false));
            case TYPE_LOADING -> new LoadingViewHolder(
                    inflater.inflate(R.layout.item_message_loading, parent, false));
            case TYPE_ERROR   -> new ErrorViewHolder(
                    inflater.inflate(R.layout.item_message_error, parent, false));
            default           -> new AiViewHolder(
                    inflater.inflate(R.layout.item_message_ai, parent, false));
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        switch (holder.getItemViewType()) {
            case TYPE_USER  -> ((UserViewHolder)    holder).bind(msg);
            case TYPE_AI    -> ((AiViewHolder)      holder).bind(msg, saveNoteListener);
            case TYPE_ERROR -> ((ErrorViewHolder)   holder).bind(msg, retryListener);
            // TYPE_LOADING tidak memerlukan data binding
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ══════════════════════════════════════════════════════════════════════════
    // VIEW HOLDERS
    // ══════════════════════════════════════════════════════════════════════════

    // ─── User message ─────────────────────────────────────────────────────────
    static final class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView text;

        UserViewHolder(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.messageText);
        }

        void bind(ChatMessage msg) {
            text.setText(msg.getText());
        }
    }

    // ─── AI message (dengan Copy + Save as Note) ───────────────────────────────
    static final class AiViewHolder extends RecyclerView.ViewHolder {
        private final TextView     messageText;
        private final View         btnCopy;
        private final LinearLayout btnSaveAsNote;

        AiViewHolder(@NonNull View v) {
            super(v);
            messageText   = v.findViewById(R.id.messageText);
            btnCopy       = v.findViewById(R.id.btnCopy);
            btnSaveAsNote = v.findViewById(R.id.btnSaveAsNote);
        }

        void bind(ChatMessage msg, OnSaveNoteListener saveNoteListener) {
            // Tampilkan dengan format rapi (bold, bullet, heading, numbered list)
            messageText.setText(MarkdownFormatter.toSpannable(msg.getText()));
            messageText.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

            // ── Tombol Salin ──────────────────────────────────────────────────
            btnCopy.setOnClickListener(v -> {
                Context ctx = v.getContext();
                ClipboardManager clipboard =
                        (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText("Gemini Response", msg.getText()));
                    Toast.makeText(ctx,
                            ctx.getString(R.string.toast_copied),
                            Toast.LENGTH_SHORT).show();
                }
            });

            // ── Tombol Jadikan Catatan ─────────────────────────────────────────
            if (saveNoteListener != null) {
                btnSaveAsNote.setVisibility(View.VISIBLE);
                btnSaveAsNote.setOnClickListener(v ->
                        saveNoteListener.onSaveNote(msg.getText()));
            } else {
                btnSaveAsNote.setVisibility(View.GONE);
            }
        }
    }

    // ─── Loading — animasi rotate icon + dots pulse ───────────────────────────
    static final class LoadingViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.ImageView loadingIcon;
        private final View dot1, dot2, dot3;

        LoadingViewHolder(@NonNull View v) {
            super(v);
            loadingIcon = v.findViewById(R.id.loadingIcon);
            dot1 = v.findViewById(R.id.dot1);
            dot2 = v.findViewById(R.id.dot2);
            dot3 = v.findViewById(R.id.dot3);
            startAnimations();
        }

        private void startAnimations() {
            // Fade in/out icon
            if (loadingIcon != null) {
                android.animation.ObjectAnimator fade =
                        android.animation.ObjectAnimator.ofFloat(loadingIcon, "alpha", 1f, 0.2f);
                fade.setDuration(800);
                fade.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
                fade.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
                fade.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                fade.start();
            }
            bounceDot(dot1, 0);
            bounceDot(dot2, 180);
            bounceDot(dot3, 360);
        }

        private void bounceDot(View dot, long delay) {
            if (dot == null) return;
            android.animation.ObjectAnimator anim =
                    android.animation.ObjectAnimator.ofFloat(dot, "translationY", 0f, -10f, 0f);
            anim.setDuration(600);
            anim.setStartDelay(delay);
            anim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            anim.setRepeatMode(android.animation.ObjectAnimator.RESTART);
            anim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            anim.start();
        }
    }

    // ─── Error ────────────────────────────────────────────────────────────────
    static final class ErrorViewHolder extends RecyclerView.ViewHolder {
        private final TextView text;
        private final View     btnRetry;

        ErrorViewHolder(@NonNull View v) {
            super(v);
            text     = v.findViewById(R.id.messageText);
            btnRetry = v.findViewById(R.id.btnRetry);
        }

        void bind(ChatMessage msg, OnRetryListener retryListener) {
            text.setText(msg.getText());
            if (retryListener != null) {
                btnRetry.setVisibility(View.VISIBLE);
                btnRetry.setOnClickListener(v -> retryListener.onRetry());
            } else {
                btnRetry.setVisibility(View.GONE);
            }
        }
    }
}