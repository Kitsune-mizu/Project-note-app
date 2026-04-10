package com.android.alpha.ui.notes;

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

import com.android.alpha.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter RecyclerView untuk menampilkan daftar catatan.
 * Mendukung mode multi-selection dengan animasi klik dan long-press.
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    // --- Interfaces ---

    /** Listener untuk event klik pada item catatan */
    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    /** Listener untuk perubahan status dan jumlah seleksi */
    public interface OnSelectionModeListener {
        void onSelectionModeChange(boolean active);
        void onSelectionCountChange(int count);
    }

    // --- Fields ---

    private List<Note> notes;
    private final OnNoteClickListener clickListener;
    private final OnSelectionModeListener selectionListener;

    // Status mode seleksi dan set ID catatan yang dipilih
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    // Konfigurasi adapter
    private final boolean selectionEnabled;
    private final boolean isHome;

    // Palet warna latar belakang item catatan (berulang berdasarkan posisi)
    private final int[] noteColors = {
            0xFFFFE4E1, // soft coral
            0xFFFFF1E6, // peach cream
            0xFFEAF4FF, // baby blue
            0xFFE8F8F5, // mint soft
            0xFFF3E8FF, // lavender pastel
            0xFFFFFBEA  // soft yellow cream
    };

    // --- Constructor ---

    /**
     * @param notes             Daftar catatan awal
     * @param clickListener     Listener klik item
     * @param selectionListener Listener perubahan seleksi
     * @param isHome            True jika dipakai di halaman home (tinggi item tetap)
     * @param selectionEnabled  True jika long-press untuk seleksi diaktifkan
     */
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

    // --- Data Management ---

    /**
     * Perbarui daftar catatan secara efisien menggunakan DiffUtil.
     * ID yang sudah tidak ada di list baru akan dihapus dari seleksi.
     */
    public void updateNotes(List<Note> newNotes) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new NoteDiffCallback(this.notes, newNotes));
        this.notes = newNotes;
        diff.dispatchUpdatesTo(this);

        selectedIds.retainAll(getIds());
        if (selectionListener != null)
            selectionListener.onSelectionCountChange(selectedIds.size());
    }

    /** Ambil semua ID dari catatan yang sedang ditampilkan */
    private Set<String> getIds() {
        Set<String> ids = new HashSet<>();
        for (Note n : notes) ids.add(n.getId());
        return ids;
    }

    // --- Selection Handlers ---

    /** Toggle seleksi pada catatan berdasarkan ID-nya */
    public void toggleSelection(String id) {
        if (!selectedIds.add(id)) selectedIds.remove(id);
        if (selectionListener != null)
            selectionListener.onSelectionCountChange(selectedIds.size());
    }

    /** Keluar dari mode seleksi dan bersihkan semua pilihan */
    public void exitSelectionMode() {
        selectionMode = false;
        selectedIds.clear();
        notifyItemRangeChanged(0, notes.size());
        if (selectionListener != null) {
            selectionListener.onSelectionModeChange(false);
            selectionListener.onSelectionCountChange(0);
        }
    }

    /** Pilih semua catatan yang sedang ditampilkan */
    public void selectAll() {
        selectedIds.clear();
        for (Note n : notes) selectedIds.add(n.getId());
        notifyItemRangeChanged(0, notes.size());
        if (selectionListener != null)
            selectionListener.onSelectionCountChange(selectedIds.size());
    }

    /** Kembalikan set ID catatan yang sedang dipilih */
    public Set<String> getSelectedNoteIds() {
        return selectedIds;
    }

    // --- RecyclerView Adapter Implementation ---

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

        // Atur tinggi item: tetap 120dp di home, wrap_content di halaman lain
        holder.itemView.getLayoutParams().height = isHome
                ? (int) (120 * holder.itemView.getResources().getDisplayMetrics().density)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    // --- Bind Helpers ---

    /** Isi teks judul, subtitle (dengan link), dan tanggal catatan */
    private void bindText(NoteViewHolder h, Note note) {
        h.title.setText(note.getTitle().isEmpty()
                ? h.itemView.getContext().getString(R.string.placeholder_no_title)
                : note.getTitle());
        h.sub.setText(note.getSubtitle());
        h.date.setText(note.getFormattedDate());

        // Aktifkan deteksi URL pada subtitle
        Linkify.addLinks(h.sub, Linkify.WEB_URLS);
        h.sub.setMovementMethod(LinkMovementMethod.getInstance());
        TypedValue tv = new TypedValue();
        h.itemView.getContext().getTheme().resolveAttribute(R.attr.color_blue, tv, true);
        h.sub.setLinkTextColor(tv.data);

        h.date.setVisibility(View.VISIBLE);
    }

    /** Tampilkan atau sembunyikan ikon pin sesuai status catatan */
    private void bindPin(NoteViewHolder h, Note note) {
        h.pin.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);
    }

    /** Tampilkan checkbox dan update ikonnya sesuai status seleksi item */
    private void bindSelection(NoteViewHolder h, Note note) {
        boolean isSelected = selectedIds.contains(note.getId());
        h.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        h.check.setImageResource(isSelected ? R.drawable.ic_checkbox_on : R.drawable.ic_checkbox_off);
    }

    /** Set warna latar CardView berdasarkan posisi item (berulang dari palet) */
    private void setBackgroundColor(NoteViewHolder h, int pos) {
        Context ctx = h.itemView.getContext();

        if (isDarkMode(ctx)) {
            // Mode gelap → ambil dari theme (tanpa helper method)
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorSurface,
                    tv,
                    true
            );

            ((CardView) h.itemView).setCardBackgroundColor(tv.data);

        } else {
            // Mode terang → pakai warna statis
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
    /**
     * Setup listener klik dan long-press pada item:
     * - Klik biasa: buka catatan atau toggle seleksi jika mode seleksi aktif
     * - Long-press: aktifkan mode seleksi (jika selectionEnabled)
     */
    private void setupClickListeners(NoteViewHolder h, Note note, int pos) {
        h.itemView.setOnClickListener(v -> {
            // Animasi tekan ringan
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
            // Animasi tekan lebih dalam
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                    .start();

            if (!selectionEnabled) return false;

            if (!selectionMode) {
                selectionMode = true;
                toggleSelection(note.getId());
                if (selectionListener != null)
                    selectionListener.onSelectionModeChange(true);

                // Refresh semua item agar checkbox muncul di seluruh list
                notifyItemRangeChanged(0, notes.size());
            }
            return true;
        });
    }

    // --- ViewHolder ---

    /** ViewHolder yang menyimpan referensi view dalam satu item catatan */
    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView title, sub, date;
        ImageView pin, check;

        public NoteViewHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_note_title);
            sub   = v.findViewById(R.id.tv_note_sub);
            date  = v.findViewById(R.id.tv_note_date);
            pin   = v.findViewById(R.id.iv_note_pin);
            check = v.findViewById(R.id.btn_select_note);
        }
    }

    // --- DiffUtil Callback ---

    /**
     * Callback DiffUtil untuk menghitung perbedaan antara dua list catatan.
     * Digunakan oleh updateNotes() agar animasi perubahan item lebih efisien.
     */
    static class NoteDiffCallback extends DiffUtil.Callback {
        private final List<Note> oldList, newList;

        NoteDiffCallback(List<Note> oldList, List<Note> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        /** Cek apakah dua item merupakan catatan yang sama berdasarkan ID */
        @Override
        public boolean areItemsTheSame(int o, int n) {
            return oldList.get(o).getId().equals(newList.get(n).getId());
        }

        /**
         * Cek apakah isi catatan sama.
         * Bergantung pada implementasi equals() di model Note.
         */
        @Override
        public boolean areContentsTheSame(int o, int n) {
            return oldList.get(o).equals(newList.get(n));
        }
    }
}