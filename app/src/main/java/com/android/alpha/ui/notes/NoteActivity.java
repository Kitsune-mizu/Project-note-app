package com.android.alpha.ui.notes;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.alpha.R;
import com.android.alpha.data.session.UserSession;
import com.android.alpha.utils.DialogUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Activity utama untuk menampilkan, mencari, dan mengelola daftar catatan.
 * Mendukung multi-selection untuk pin dan hapus catatan sekaligus.
 */
public class NoteActivity extends AppCompatActivity
        implements NoteAdapter.OnNoteClickListener, NoteAdapter.OnSelectionModeListener {

    // Adapter dan ViewModel untuk data catatan
    private NoteAdapter adapter;
    private NoteViewModel viewModel;
    private List<Note> allNotes = new ArrayList<>();

    // Komponen UI utama
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddNote;
    private LinearLayout selectionModeActionBar;
    private RelativeLayout selectionModeHeader;
    private TextView tvSelectionCount;
    private SearchView searchView;

    // Aksi yang tersedia saat mode multi-selection aktif
    private enum MultiAction { PIN, DELETE }

    // --- Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        initViewModel();
        initViews();
        setupSearchView();
        setupRecyclerView();
        setupListeners();

        // Observasi perubahan data catatan aktif dan terapkan filter pencarian
        viewModel.getActiveNotes().observe(this, notes -> {
            allNotes = notes;
            applyFilter();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload catatan setiap kali activity kembali aktif
        viewModel.loadNotes(this);
    }

    // --- Initialization ---

    /** Inisialisasi ViewModel dan set userId dari sesi pengguna aktif */
    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        String userId = UserSession.getInstance()
                .getUserData(UserSession.getInstance().getUsername()).userId;
        viewModel.setUserId(userId);
    }

    /** Bind semua komponen UI dari layout */
    private void initViews() {
        recyclerView           = findViewById(R.id.recycler_view_notes);
        fabAddNote             = findViewById(R.id.fab_add_note);
        selectionModeActionBar = findViewById(R.id.selection_mode_action_bar);
        selectionModeHeader    = findViewById(R.id.selection_mode_header);
        tvSelectionCount       = findViewById(R.id.tv_selection_count);
        searchView             = findViewById(R.id.search_view);
    }

    /** Inisialisasi RecyclerView dengan adapter catatan */
    private void setupRecyclerView() {
        adapter = new NoteAdapter(
                new ArrayList<>(),
                this,
                this,
                false,  // isHome = false
                true    // selectionEnabled = true
        );
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    // --- UI Setup ---

    /** Kustomisasi tampilan SearchView: background, hint, warna ikon, dan ukuran */
    private void setupSearchView() {
        searchView.post(() -> {
            // Background search plate
            View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            if (searchPlate != null)
                searchPlate.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_search_rounded));

            // Styling teks input
            android.widget.EditText searchEditText =
                    searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEditText != null) {
                searchEditText.setBackground(null);
                searchEditText.setHint(getString(R.string.search_notes_hint));
                searchEditText.setHintTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
                searchEditText.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onBackground));
            }

            // Ikon pencarian: gambar, warna, dan ukuran
            ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
            if (searchIcon != null) {
                searchIcon.setImageResource(R.drawable.ic_search_note);
                searchIcon.setColorFilter(
                        ContextCompat.getColor(this, R.color.md_theme_light_onSurface),
                        PorterDuff.Mode.SRC_IN);
                int sizePx = (int) (40 * getResources().getDisplayMetrics().density + 0.5f);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
                params.gravity = Gravity.CENTER_VERTICAL;
                searchIcon.setLayoutParams(params);
            }

            // Sembunyikan ikon close bawaan dengan ikon transparan
            ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeIcon != null)
                closeIcon.setImageResource(R.drawable.transparent_icon);
        });
    }

    // --- Listeners ---

    /**
     * Setup semua listener UI:
     * FAB tambah catatan, tombol selection bar, SearchView, dan touch RecyclerView.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        // Buka halaman tambah catatan baru
        fabAddNote.setOnClickListener(v ->
                startActivity(new Intent(this, EditNoteActivity.class)));

        // Tombol aksi pada selection bar
        findViewById(R.id.action_cancel_selection).setOnClickListener(v -> adapter.exitSelectionMode());
        findViewById(R.id.action_select_all).setOnClickListener(v -> adapter.selectAll());
        findViewById(R.id.action_pin).setOnClickListener(v -> handleMultiAction(MultiAction.PIN));
        findViewById(R.id.action_delete).setOnClickListener(v -> handleMultiAction(MultiAction.DELETE));

        // Filter pencarian saat teks berubah atau di-submit
        searchView.clearFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { applyFilter(query); return true; }
            @Override public boolean onQueryTextChange(String text)  { applyFilter(text);  return true; }
        });

        // Tutup keyboard/SearchView saat RecyclerView disentuh
        recyclerView.setOnTouchListener((v, event) -> {
            if (!searchView.isIconified()) {
                searchView.setIconified(true);
                searchView.clearFocus();
            }
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return false;
        });
    }

    // --- Data Filtering ---

    /** Filter catatan berdasarkan query: cocokkan judul atau isi catatan */
    private void applyFilter(String query) {
        String search = query != null ? query.toLowerCase(Locale.ROOT) : "";
        List<Note> filtered = allNotes.stream()
                .filter(n -> n.getTitle().toLowerCase().contains(search)
                        || n.getContent().toLowerCase().contains(search))
                .collect(Collectors.toList());
        adapter.updateNotes(filtered);
    }

    /** Terapkan filter menggunakan query yang sedang aktif di SearchView */
    private void applyFilter() {
        applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
    }

    // --- Multi-Selection Handlers ---

    /**
     * Menangani aksi PIN atau DELETE terhadap catatan yang dipilih.
     * DELETE menampilkan dialog konfirmasi sebelum dieksekusi.
     */
    private void handleMultiAction(MultiAction action) {
        Set<String> selectedIds = adapter.getSelectedNoteIds();
        if (selectedIds.isEmpty()) return;

        List<Note> notes = allNotes.stream()
                .filter(n -> selectedIds.contains(n.getId()))
                .collect(Collectors.toList());

        switch (action) {
            case PIN:
                // Toggle pin: pin semua jika belum semua di-pin, unpin jika sudah semua
                boolean targetPin = !notes.stream().allMatch(Note::isPinned);
                notes.forEach(n -> {
                    n.setPinned(targetPin);
                    viewModel.saveNote(this, n);
                });
                Toast.makeText(this,
                        targetPin ? getString(R.string.toast_pinned) : getString(R.string.toast_unpinned),
                        Toast.LENGTH_SHORT).show();
                break;

            case DELETE:
                // Tampilkan konfirmasi sebelum menghapus
                DialogUtils.showConfirmDialog(
                        this,
                        getString(R.string.dialog_delete_multiple_title),
                        getString(R.string.dialog_delete_multiple_msg),
                        getString(R.string.action_delete),
                        getString(R.string.action_cancel),
                        () -> {
                            notes.forEach(n -> viewModel.deleteNote(this, n.getId()));
                            Toast.makeText(this, getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show();
                            adapter.exitSelectionMode();
                            viewModel.loadNotes(this);
                        },
                        null);
                return; // Hindari pemanggilan exitSelectionMode di bawah sebelum konfirmasi
        }

        adapter.exitSelectionMode();
        viewModel.loadNotes(this);
    }

    // --- NoteAdapter Interface Implementations ---

    /** Buka halaman edit saat catatan diklik */
    @Override
    public void onNoteClick(Note note) {
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra("note_id", note.getId());
        startActivity(intent);
    }

    /** Tampilkan atau sembunyikan selection bar dan FAB berdasarkan status mode seleksi */
    @Override
    public void onSelectionModeChange(boolean active) {
        int selectionVisibility = active ? View.VISIBLE : View.GONE;
        int fabVisibility       = active ? View.GONE   : View.VISIBLE;
        selectionModeHeader.setVisibility(selectionVisibility);
        selectionModeActionBar.setVisibility(selectionVisibility);
        fabAddNote.setVisibility(fabVisibility);
    }

    /** Perbarui teks jumlah catatan yang dipilih */
    @Override
    public void onSelectionCountChange(int count) {
        tvSelectionCount.setText(
                String.format(Locale.getDefault(), getString(R.string.selection_count_format), count));
    }
}