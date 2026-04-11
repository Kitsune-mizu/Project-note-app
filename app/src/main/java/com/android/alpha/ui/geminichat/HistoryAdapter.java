package com.android.alpha.ui.geminichat;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    // ─── Variables & Interfaces ──────────────────────────────────────────────

    private final List<ChatSession> sessions;
    private final HistoryListener listener;

    public interface HistoryListener {
        void onSessionClick(ChatSession session);
        void onRenameClick(ChatSession session, int position);
        void onDeleteClick(ChatSession session, int position);
    }


    // ─── Constructor ─────────────────────────────────────────────────────────

    public HistoryAdapter(List<ChatSession> sessions, HistoryListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }


    // ─── Adapter Overrides ───────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(sessions.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }


    // ─── ViewHolder ──────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView titleText;
        private final ImageButton moreButton;

        public ViewHolder(@NonNull View v) {
            super(v);
            titleText = v.findViewById(R.id.historyTitle);
            moreButton = v.findViewById(R.id.historyMoreBtn);
        }

        void bind(ChatSession session, HistoryListener listener) {
            titleText.setText(session.getTitle());

            itemView.setOnClickListener(v -> listener.onSessionClick(session));

            moreButton.setOnClickListener(anchor -> {
                @android.annotation.SuppressLint("InflateParams")
                View popup = LayoutInflater.from(anchor.getContext())
                        .inflate(R.layout.popup_history_menu, null);

                PopupWindow popupWindow = new PopupWindow(
                        popup,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                );

                popupWindow.setElevation(16f);
                popupWindow.setOutsideTouchable(true);
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                popup.findViewById(R.id.menuRename).setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onRenameClick(session, pos);
                    }
                    popupWindow.dismiss();
                });

                popup.findViewById(R.id.menuDelete).setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onDeleteClick(session, pos);
                    }
                    popupWindow.dismiss();
                });

                popup.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );

                int offsetX = -popup.getMeasuredWidth() + anchor.getWidth();
                popupWindow.showAsDropDown(anchor, offsetX, 0);
            });
        }
    }
}