package com.android.kitsune.ui.notes;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import com.android.kitsune.R;
import com.android.kitsune.data.local.NoteStorage;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class NotesActivity extends AppCompatActivity {

    private RecyclerView rvNotes;
    private NotesAdapter adapter;
    private ArrayList<NoteModel> notes;
    private NoteStorage storage;

    private NoteModel recentlyDeletedNote;
    private int recentlyDeletedPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        rvNotes = findViewById(R.id.rvNotes);
        FloatingActionButton btnAddNote = findViewById(R.id.btnAddNote);
        EditText etSearch = findViewById(R.id.etSearch);

        storage = NoteStorage.getInstance(this);
        notes = storage.getNotes();

        // sort pinned dulu
        notes.sort((a, b) -> Boolean.compare(b.pinned, a.pinned));

        adapter = new NotesAdapter(notes, this, storage);
        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        rvNotes.setAdapter(adapter);

        // swipe left untuk delete
        ItemTouchHelper.SimpleCallback callback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;

                        recentlyDeletedNote = notes.get(pos);
                        recentlyDeletedPosition = pos;

                        notes.remove(pos);
                        adapter.notifyItemRemoved(pos);

                        storage.deleteNote(recentlyDeletedNote.getId());

                        showUndoSnackbar();
                    }
                };

        new ItemTouchHelper(callback).attachToRecyclerView(rvNotes);

        // search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        // tambah note baru
        btnAddNote.setOnClickListener(v ->
                startActivity(new Intent(NotesActivity.this, EditNoteActivity.class))
        );
    }

    private void showUndoSnackbar() {
        Snackbar snackbar = Snackbar.make(rvNotes, R.string.note_deleted, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> undoDelete());
        snackbar.show();
    }

    private void undoDelete() {
        if (recentlyDeletedNote == null) return;

        // restore ke list di memory
        notes.add(recentlyDeletedPosition, recentlyDeletedNote);
        adapter.notifyItemInserted(recentlyDeletedPosition);

        // restore ke storage
        storage.undoDelete();

        Toast.makeText(this, R.string.note_restored, Toast.LENGTH_SHORT).show();

        recentlyDeletedNote = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        notes = storage.getNotes();
        adapter.updateData(notes);
    }
}
