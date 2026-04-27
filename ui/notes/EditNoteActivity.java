package com.android.kitsune.ui.notes;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.android.kitsune.R;
import com.android.kitsune.data.local.NoteStorage;

public class EditNoteActivity extends AppCompatActivity {

    private EditText etTitle, etContent;

    private NoteStorage storage;
    private int noteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_note);

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        Button btnSave = findViewById(R.id.btnSave);

        storage = NoteStorage.getInstance(this);

        // CEK EDIT MODE
        if (getIntent().hasExtra("noteId")) {
            noteId = getIntent().getIntExtra("noteId", -1);
            NoteModel note = storage.getNoteById(noteId);

            if (note != null) {
                etTitle.setText(note.getTitle());
                etContent.setText(note.getContent());
            }
        }

        btnSave.setOnClickListener(v -> saveNote());
    }

    private void saveNote() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (title.isEmpty()) {
            title = getString(R.string.memo_untitled); // atau "Untitled"
        }

        if (noteId == -1) {
            storage.addNote(title, content);
        } else {
            storage.updateNote(noteId, title, content);
        }

        finish();
    }
}
