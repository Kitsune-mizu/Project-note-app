package com.android.kitsune.ui.notes;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.kitsune.R;
import com.android.kitsune.data.local.NoteStorage;

import java.util.ArrayList;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.ViewHolder> {

    ArrayList<NoteModel> list;
    ArrayList<NoteModel> fullList;
    Context ctx;
    NoteStorage storage;

    public NotesAdapter(ArrayList<NoteModel> list, Context ctx, NoteStorage storage) {
        this.list = list;
        this.ctx = ctx;
        this.storage = storage;
        this.fullList = new ArrayList<>(list);
    }

    // SEARCH
    public void filter(String text) {
        list.clear();

        if (text == null || text.isEmpty()) {
            list.addAll(fullList);
        } else {
            text = text.toLowerCase();
            for (NoteModel n : fullList) {
                if (n.getTitle().toLowerCase().contains(text) ||
                        n.getContent().toLowerCase().contains(text)) {
                    list.add(n);
                }
            }
        }
        notifyDataSetChanged();
    }

    // DIPANGGIL DARI onResume NotesActivity
    public void updateData(ArrayList<NoteModel> newList) {
        this.list = newList;
        this.fullList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_note, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NotesAdapter.ViewHolder holder, int position) {
        NoteModel note = list.get(position);

        holder.tvTitle.setText(note.getTitle());
        holder.tvContent.setText(note.getContent());
        holder.tvDate.setText(note.getDate());

        // klik → edit
        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, EditNoteActivity.class);
            i.putExtra("noteId", note.getId());
            ctx.startActivity(i);
        });

        // pin visibility
        holder.ivPin.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

        // long click → toggle pin
        holder.itemView.setOnLongClickListener(v -> {
            boolean newPinned = !note.isPinned();
            note.setPinned(newPinned);

            storage.updateNotePin(note.getId(), newPinned);

            // refresh data dari storage biar urutan pinned bener
            ArrayList<NoteModel> fresh = storage.getNotes();
            updateData(fresh);

            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvContent, tvDate;
        ImageView ivPin;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvContent = itemView.findViewById(R.id.tvNoteContent);
            tvDate = itemView.findViewById(R.id.tvNoteDate);
            ivPin = itemView.findViewById(R.id.ivPin);
        }
    }
}
