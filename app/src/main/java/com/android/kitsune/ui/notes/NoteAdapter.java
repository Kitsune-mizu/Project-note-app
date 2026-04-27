package com.android.kitsune.ui.notes;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.android.kitsune.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    // ─── Interfaces ──────────────────────────────────────────────────────────

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public interface OnSelectionModeListener {
        void onSelectionModeChange(boolean active);
        void onSelectionCountChange(int count);
    }


    // ─── Constants & Variables ───────────────────────────────────────────────

    private List<Note> notes;
    private final OnNoteClickListener clickListener;
    private final OnSelectionModeListener selectionListener;

    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    private final boolean selectionEnabled;
    private final boolean isHome;

    private final int[] noteColors = {
            0xFFFFE4E1,
            0xFFFFF1E6,
            0xFFEAF4FF,
            0xFFE8F8F5,
            0xFFF3E8FF,
            0xFFFFFBEA
    };


    // ─── Constructor ─────────────────────────────────────────────────────────

    public NoteAdapter(List<Note> notes,
                       OnNoteClickListener clickListener,
                       OnSelectionModeListener selectionListener,
                       boolean isHome,
                       boolean selectionEnabled) {
        this.notes = notes;
        this.clickListener = clickListener;
        this.selectionListener = selectionListener;
        this.isHome = isHome;
        this.selectionEnabled = selectionEnabled;
    }


    // ─── Data Management ─────────────────────────────────────────────────────

    public void updateNotes(List<Note> newNotes) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new NoteDiffCallback(this.notes, newNotes));
        this.notes = newNotes;
        diff.dispatchUpdatesTo(this);

        selectedIds.retainAll(getIds());
        if (selectionListener != null) {
            selectionListener.onSelectionCountChange(selectedIds.size());
        }
    }

    private Set<String> getIds() {
        Set<String> ids = new HashSet<>();
        for (Note n : notes) {
            ids.add(n.getId());
        }
        return ids;
    }


    // ─── Selection Handlers ──────────────────────────────────────────────────

    public void toggleSelection(String id) {
        if (!selectedIds.add(id)) {
            selectedIds.remove(id);
        }

        if (selectionListener != null) {
            selectionListener.onSelectionCountChange(selectedIds.size());
        }
    }

    public void exitSelectionMode() {
        selectionMode = false;
        selectedIds.clear();
        notifyItemRangeChanged(0, notes.size());

        if (selectionListener != null) {
            selectionListener.onSelectionModeChange(false);
            selectionListener.onSelectionCountChange(0);
        }
    }

    public void selectAll() {
        selectedIds.clear();
        for (Note n : notes) {
            selectedIds.add(n.getId());
        }
        notifyItemRangeChanged(0, notes.size());

        if (selectionListener != null) {
            selectionListener.onSelectionCountChange(selectedIds.size());
        }
    }

    public Set<String> getSelectedNoteIds() {
        return selectedIds;
    }


    // ─── Adapter Overrides ───────────────────────────────────────────────────

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);

        bindText(holder, note);
        bindPin(holder, note);
        bindSelection(holder, note);
        setBackgroundColor(holder, position);
        setupClickListeners(holder, note, position);

        holder.itemView.getLayoutParams().height = isHome
                ? (int) (120 * holder.itemView.getResources().getDisplayMetrics().density)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }


    // ─── Bind Helpers ────────────────────────────────────────────────────────

    private void bindText(NoteViewHolder h, Note note) {
        if (note.getTitle().isEmpty()) {
            h.title.setText(h.itemView.getContext().getString(R.string.placeholder_no_title));
        } else {
            h.title.setText(note.getTitle());
        }

        h.sub.setText(note.getSubtitle());
        h.date.setText(note.getFormattedDate());

        Linkify.addLinks(h.sub, Linkify.WEB_URLS);
        h.sub.setMovementMethod(LinkMovementMethod.getInstance());

        TypedValue tv = new TypedValue();
        h.itemView.getContext().getTheme().resolveAttribute(R.attr.color_blue, tv, true);
        h.sub.setLinkTextColor(tv.data);

        h.date.setVisibility(View.VISIBLE);
    }

    private void bindPin(NoteViewHolder h, Note note) {
        if (note.isPinned()) {
            h.pin.setVisibility(View.VISIBLE);
        } else {
            h.pin.setVisibility(View.GONE);
        }
    }

    private void bindSelection(NoteViewHolder h, Note note) {
        boolean isSelected = selectedIds.contains(note.getId());

        if (selectionMode) {
            h.check.setVisibility(View.VISIBLE);
        } else {
            h.check.setVisibility(View.GONE);
        }

        h.check.setImageResource(isSelected ? R.drawable.ic_checkbox_on : R.drawable.ic_checkbox_off);
    }

    private void setBackgroundColor(NoteViewHolder h, int pos) {
        Context ctx = h.itemView.getContext();

        if (isDarkMode(ctx)) {
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorSurface,
                    tv,
                    true
            );
            ((CardView) h.itemView).setCardBackgroundColor(tv.data);
        } else {
            ((CardView) h.itemView).setCardBackgroundColor(
                    noteColors[pos % noteColors.length]
            );
        }
    }

    private boolean isDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void setupClickListeners(NoteViewHolder h, Note note, int pos) {
        h.itemView.setOnClickListener(v -> {
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(50)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(50).start())
                    .start();

            if (selectionMode) {
                toggleSelection(note.getId());
                notifyItemChanged(pos);
            } else if (clickListener != null) {
                clickListener.onNoteClick(note);
            }
        });

        h.itemView.setOnLongClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                    .start();

            if (!selectionEnabled) {
                return false;
            }

            if (!selectionMode) {
                selectionMode = true;
                toggleSelection(note.getId());

                if (selectionListener != null) {
                    selectionListener.onSelectionModeChange(true);
                }

                notifyItemRangeChanged(0, notes.size());
            }
            return true;
        });
    }


    // ─── ViewHolder ──────────────────────────────────────────────────────────

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView title, sub, date;
        ImageView pin, check;

        public NoteViewHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_note_title);
            sub = v.findViewById(R.id.tv_note_sub);
            date = v.findViewById(R.id.tv_note_date);
            pin = v.findViewById(R.id.iv_note_pin);
            check = v.findViewById(R.id.btn_select_note);
        }
    }


    // ─── DiffUtil Callback ───────────────────────────────────────────────────

    static class NoteDiffCallback extends DiffUtil.Callback {
        private final List<Note> oldList, newList;

        NoteDiffCallback(List<Note> oldList, List<Note> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int o, int n) {
            return oldList.get(o).getId().equals(newList.get(n).getId());
        }

        @Override
        public boolean areContentsTheSame(int o, int n) {
            return oldList.get(o).equals(newList.get(n));
        }
    }
}