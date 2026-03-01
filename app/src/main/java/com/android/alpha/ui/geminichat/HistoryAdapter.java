package com.android.alpha.ui.geminichat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<ChatSession> sessions;
    private final HistoryListener   listener;

    public interface HistoryListener {
        void onSessionClick(ChatSession session);
        void onRenameClick(ChatSession session, int position);
        void onDeleteClick(ChatSession session, int position);
    }

    public HistoryAdapter(List<ChatSession> sessions, HistoryListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.bind(session, listener);
    }

    @Override
    public int getItemCount() { return sessions.size(); }

    // ViewHolder di-package-private (tidak static public) agar tidak exposed
    // di luar scope yang diperlukan, sesuai lint recommendation.
    // ViewHolder diperbaiki visibility-nya agar tidak lebih sempit dari Adapter
    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView titleText;
        private final ImageButton moreButton;

        public ViewHolder(@NonNull View v) {
            super(v);
            titleText  = v.findViewById(R.id.historyTitle);
            moreButton = v.findViewById(R.id.historyMoreBtn);
        }

        void bind(ChatSession session, HistoryListener listener) {
            titleText.setText(session.getTitle());

            itemView.setOnClickListener(v -> listener.onSessionClick(session));

            moreButton.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.getMenu().add(0, 0, 0,
                        v.getContext().getString(R.string.action_rename));
                popup.getMenu().add(0, 1, 1,
                        v.getContext().getString(R.string.action_delete));

                popup.setOnMenuItemClickListener(item -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return false;

                    if (item.getItemId() == 0) {
                        listener.onRenameClick(session, pos);
                    } else {
                        listener.onDeleteClick(session, pos);
                    }
                    return true;
                });
                popup.show();
            });
        }
    }
}