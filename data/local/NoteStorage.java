package com.android.kitsune.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.kitsune.ui.notes.NoteModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;

public class NoteStorage {

    private static final String TAG = "NoteStorage";

    private static NoteStorage instance;

    private final Context context;
    private final SharedPreferences prefs;

    private static final String NOTES_KEY = "notes";

    private NoteModel lastDeletedNote = null; // For undo delete

    // ==========================
    //  SINGLETON
    // ==========================
    public static synchronized NoteStorage getInstance(Context context) {
        if (instance == null) {
            instance = new NoteStorage(context.getApplicationContext());
        }
        return instance;
    }

    private NoteStorage(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("notes_pref", Context.MODE_PRIVATE);
    }

    // ==========================
    //  GET ALL NOTES
    // ==========================
    public ArrayList<NoteModel> getNotes() {
        ArrayList<NoteModel> list = new ArrayList<>();
        String json = prefs.getString(NOTES_KEY, "[]");

        try {
            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);

                NoteModel note = new NoteModel(
                        obj.getInt("id"),
                        obj.getString("title"),
                        obj.getString("content"),
                        obj.getString("date")
                );

                note.pinned = obj.optBoolean("pinned", false);
                list.add(note);
            }

            // pinned di atas
            list.sort((a, b) -> Boolean.compare(b.pinned, a.pinned));

        } catch (Exception e) {
            Log.e(TAG, "Error parsing notes JSON: " + e.getMessage(), e);
        }

        return list;
    }

    // ==========================
    //  SAVE ALL
    // ==========================
    private void saveNotes(ArrayList<NoteModel> notes) {
        JSONArray arr = new JSONArray();

        try {
            for (NoteModel note : notes) {
                JSONObject json = new JSONObject();
                json.put("id", note.id);
                json.put("title", note.title);
                json.put("content", note.content);
                json.put("date", note.date);
                json.put("pinned", note.pinned);

                arr.put(json);
            }

            prefs.edit().putString(NOTES_KEY, arr.toString()).apply();

        } catch (Exception e) {
            Log.e(TAG, "Failed to save notes: " + e.getMessage(), e);
        }
    }

    // ==========================
    //  ADD NOTE
    // ==========================
    public void addNote(String title, String content) {
        ArrayList<NoteModel> list = getNotes();

        int id = list.isEmpty() ? 1 : list.get(list.size() - 1).id + 1;

        String date = new java.text.SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                java.util.Locale.getDefault()
        ).format(new java.util.Date());

        list.add(new NoteModel(id, title, content, date));
        saveNotes(list);

    }

    // ==========================
    //  GET BY ID
    // ==========================
    public NoteModel getNoteById(int id) {
        for (NoteModel n : getNotes()) {
            if (n.id == id) return n;
        }
        return null;
    }

    // ==========================
    //  UPDATE NOTE
    // ==========================
    public void updateNote(int id, String title, String content) {
        ArrayList<NoteModel> list = getNotes();

        for (NoteModel n : list) {
            if (n.id == id) {
                n.title = title;
                n.content = content;
                n.date = new java.text.SimpleDateFormat(
                        "dd MMM yyyy, HH:mm",
                        java.util.Locale.getDefault()
                ).format(new java.util.Date());
                break;
            }
        }

        saveNotes(list);
    }

    // ==========================
    //  DELETE (UNDO SUPPORT)
    // ==========================
    public void deleteNote(int id) {
        ArrayList<NoteModel> list = getNotes();

        for (NoteModel n : list) {
            if (n.id == id) {
                lastDeletedNote = n; // untuk undo
                break;
            }
        }

        list.removeIf(n -> n.id == id);
        saveNotes(list);
    }

    // ==========================
    //  UNDO DELETE
    // ==========================
    public void undoDelete() {
        if (lastDeletedNote == null) return;

        ArrayList<NoteModel> list = getNotes();
        list.add(lastDeletedNote);

        // urut berdasarkan ID
        list.sort(Comparator.comparingInt(a -> a.id));

        saveNotes(list);
        lastDeletedNote = null;
    }

    // ==========================
    //  PIN / UNPIN
    // ==========================
    public void updateNotePin(int id, boolean pinned) {
        ArrayList<NoteModel> list = getNotes();

        for (NoteModel n : list) {
            if (n.id == id) {
                n.pinned = pinned;
                break;
            }
        }

        // pinned di atas
        list.sort((a, b) -> Boolean.compare(b.pinned, a.pinned));
        saveNotes(list);
    }

    public Context getContext() {
        return context;
    }
}
