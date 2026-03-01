package com.android.alpha.ui.notes;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.android.alpha.geminichat.ChatActivity;
import com.android.alpha.utils.DialogUtils;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class NoteActivity extends AppCompatActivity
        implements NoteAdapter.OnNoteClickListener, NoteAdapter.OnSelectionModeListener {

    private NoteAdapter   adapter;
    private NoteViewModel viewModel;
    private List<Note>    allNotes = new ArrayList<>();

    private RecyclerView                 recyclerView;
    private FloatingActionButton         fabAddNote;
    private ExtendedFloatingActionButton fabGemini;
    private View                         cardFabGemini;   // wrapper card untuk hide/show
    private View                         cardFabAddNote;  // wrapper card untuk hide/show
    private LinearLayout                 selectionModeActionBar;
    private RelativeLayout               selectionModeHeader;
    private TextView                     tvSelectionCount;
    private SearchView                   searchView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private enum MultiAction { PIN, DELETE }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);
        initViewModel();
        initViews();
        setupSearchView();
        setupRecyclerView();
        setupListeners();
        handleIncomingNote();
        viewModel.getActiveNotes().observe(this, notes -> {
            allNotes = notes;
            applyFilter();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadNotes(this);
        // Setiap masuk halaman: extend FAB gemini, lalu shrink otomatis setelah 2.5 detik
        showExtendedGeminiFab();
    }

    // ── Extended FAB logic ────────────────────────────────────────────────────

    private void showExtendedGeminiFab() {
        handler.removeCallbacksAndMessages(null);
        fabGemini.extend();
        handler.postDelayed(() -> fabGemini.shrink(), 2500);
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        String userId = UserSession.getInstance()
                .getUserData(UserSession.getInstance().getUsername()).userId;
        viewModel.setUserId(userId);
    }

    private void initViews() {
        recyclerView           = findViewById(R.id.recycler_view_notes);
        fabAddNote             = findViewById(R.id.fab_add_note);
        fabGemini              = findViewById(R.id.fab_gemini);
        cardFabGemini          = findViewById(R.id.card_fab_gemini);
        cardFabAddNote         = findViewById(R.id.card_fab_add_note);
        selectionModeActionBar = findViewById(R.id.selection_mode_action_bar);
        selectionModeHeader    = findViewById(R.id.selection_mode_header);
        tvSelectionCount       = findViewById(R.id.tv_selection_count);
        searchView             = findViewById(R.id.search_view);
    }

    private void setupRecyclerView() {
        adapter = new NoteAdapter(new ArrayList<>(), this, this, false, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    // ── Incoming note dari ChatActivity ──────────────────────────────────────

    private void handleIncomingNote() {
        Intent intent = getIntent();
        if (intent == null) return;
        String title   = intent.getStringExtra("note_title");
        String content = intent.getStringExtra("note_content");
        if (content != null && !content.isEmpty()) {
            openEditNoteWithContent(title != null ? title : "", content);
        }
    }

    private void openEditNoteWithContent(String title, String content) {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        viewModel.saveNote(this, note);
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra("note_id", note.getId());
        startActivity(intent);
        Toast.makeText(this, getString(R.string.toast_note_saved_from_ai), Toast.LENGTH_SHORT).show();
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        fabAddNote.setOnClickListener(v ->
                startActivity(new Intent(this, EditNoteActivity.class)));

        fabGemini.setOnClickListener(v -> {
            handler.removeCallbacksAndMessages(null);
            startActivity(new Intent(this, ChatActivity.class));
        });

        findViewById(R.id.action_cancel_selection).setOnClickListener(v -> adapter.exitSelectionMode());
        findViewById(R.id.action_select_all).setOnClickListener(v -> adapter.selectAll());
        findViewById(R.id.action_pin).setOnClickListener(v -> handleMultiAction(MultiAction.PIN));
        findViewById(R.id.action_delete).setOnClickListener(v -> handleMultiAction(MultiAction.DELETE));

        searchView.clearFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { applyFilter(q); return true; }
            @Override public boolean onQueryTextChange(String t)  { applyFilter(t); return true; }
        });

        recyclerView.setOnTouchListener((v, event) -> {
            if (!searchView.isIconified()) { searchView.setIconified(true); searchView.clearFocus(); }
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return false;
        });
    }

    // ── Filter ───────────────────────────────────────────────────────────────

    private void applyFilter(String query) {
        String s = query != null ? query.toLowerCase(Locale.ROOT) : "";
        List<Note> filtered = allNotes.stream()
                .filter(n -> n.getTitle().toLowerCase().contains(s)
                        || n.getContent().toLowerCase().contains(s))
                .collect(Collectors.toList());
        adapter.updateNotes(filtered);
    }

    private void applyFilter() {
        applyFilter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
    }

    // ── Multi-selection ───────────────────────────────────────────────────────
    private void handleMultiAction(MultiAction action) {
        Set<String> selectedIds = adapter.getSelectedNoteIds();
        if (selectedIds.isEmpty()) return;
        @SuppressLint({"NewApi", "LocalSuppress"}) List<Note> notes = allNotes.stream()
                .filter(n -> selectedIds.contains(n.getId()))
                .toList();
        switch (action) {
            case PIN:
                boolean targetPin = !notes.stream().allMatch(Note::isPinned);
                notes.forEach(n -> { n.setPinned(targetPin); viewModel.saveNote(this, n); });
                Toast.makeText(this,
                        targetPin ? getString(R.string.toast_pinned) : getString(R.string.toast_unpinned),
                        Toast.LENGTH_SHORT).show();
                break;
            case DELETE:
                DialogUtils.showConfirmDialog(this,
                        getString(R.string.dialog_delete_multiple_title),
                        getString(R.string.dialog_delete_multiple_msg),
                        getString(R.string.action_delete),
                        getString(R.string.action_cancel),
                        () -> {
                            notes.forEach(n -> viewModel.deleteNote(this, n.getId()));
                            Toast.makeText(this, getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show();
                            adapter.exitSelectionMode();
                            viewModel.loadNotes(this);
                        }, null);
                return;
        }
        adapter.exitSelectionMode();
        viewModel.loadNotes(this);
    }

    // ── Adapter callbacks ─────────────────────────────────────────────────────

    @Override
    public void onNoteClick(Note note) {
        Intent intent = new Intent(this, EditNoteActivity.class);
        intent.putExtra("note_id", note.getId());
        startActivity(intent);
    }

    @Override
    public void onSelectionModeChange(boolean active) {
        int vis = active ? View.GONE : View.VISIBLE;
        cardFabGemini.setVisibility(vis);
        cardFabAddNote.setVisibility(vis);
        selectionModeHeader.setVisibility(active ? View.VISIBLE : View.GONE);
        selectionModeActionBar.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSelectionCountChange(int count) {
        tvSelectionCount.setText(
                String.format(Locale.getDefault(), getString(R.string.selection_count_format), count));
    }

    // ── SearchView styling ────────────────────────────────────────────────────

    private void setupSearchView() {
        searchView.post(() -> {
            View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            if (searchPlate != null)
                searchPlate.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_search_rounded));

            android.widget.EditText et =
                    searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (et != null) {
                et.setBackground(null);
                et.setHint(getString(R.string.search_notes_hint));
                et.setHintTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onSurface));
                et.setTextColor(ContextCompat.getColor(this, R.color.md_theme_light_onBackground));
            }

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

            ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeIcon != null) closeIcon.setImageResource(R.drawable.transparent_icon);
        });
    }
}