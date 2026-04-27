package com.android.kitsune.ui.notes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NoteViewModel extends ViewModel {

    // ─── Constants & Variables ───────────────────────────────────────────────

    private static final String TAG = "NoteViewModel";
    private final MutableLiveData<List<Note>> activeNotes = new MutableLiveData<>(new ArrayList<>());
    private String userId;
    private final Gson gson = new Gson();


    // ─── User ID Management ──────────────────────────────────────────────────

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }


    // ─── LiveData Accessors ──────────────────────────────────────────────────

    public LiveData<List<Note>> getActiveNotes() {
        return activeNotes;
    }


    // ─── CRUD Operations ─────────────────────────────────────────────────────

    public void loadNotes(Context context) {
        if (userId == null || context == null) {
            return;
        }

        List<Note> notesList = new ArrayList<>();
        try {
            Map<String, ?> notesMap = getPrefs(context).getAll();

            for (Object value : notesMap.values()) {
                if (!(value instanceof String)) {
                    continue;
                }

                Note note = safeFromJson((String) value);
                if (note != null) {
                    notesList.add(note);
                }
            }

            notesList.sort((a, b) -> {
                if (a.isPinned() && !b.isPinned()) {
                    return -1;
                }
                if (!a.isPinned() && b.isPinned()) {
                    return 1;
                }
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });

            activeNotes.postValue(notesList);

        } catch (Exception e) {
            Log.e(TAG, "loadNotes error", e);
        }
    }

    public void saveNote(Context context, Note note) {
        if (userId == null || context == null || note == null) {
            return;
        }

        note.setTimestamp(System.currentTimeMillis());

        try {
            putNote(context, note.getId(), note);
        } catch (Exception e) {
            Log.e(TAG, "saveNote error", e);
        }

        refreshNotes(context);
    }

    public void deleteNote(Context context, String id) {
        if (userId == null || context == null || id == null) {
            return;
        }

        try {
            getPrefs(context).edit().remove(id).apply();
        } catch (Exception e) {
            Log.e(TAG, "deleteNote error", e);
        }

        refreshNotes(context);
    }


    // ─── Data Retrieval ──────────────────────────────────────────────────────

    public Note getNoteById(Context context, String id) {
        if (userId == null || context == null || id == null) {
            return null;
        }

        try {
            String raw = getPrefs(context).getString(id, null);
            if (raw == null) {
                return null;
            } else {
                return gson.fromJson(raw, Note.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "getNoteById error", e);
            return null;
        }
    }


    // ─── Utilities ───────────────────────────────────────────────────────────

    public void refreshNotes(Context context) {
        loadNotes(context);
    }

    private SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("notes_" + userId, Context.MODE_PRIVATE);
    }

    private Note safeFromJson(String json) {
        try {
            return gson.fromJson(json, Note.class);
        } catch (Exception e) {
            Log.w(TAG, "Skipping invalid note json", e);
            return null;
        }
    }

    private void putNote(Context context, String id, Note note) {
        getPrefs(context).edit().putString(id, gson.toJson(note)).apply();
    }
}