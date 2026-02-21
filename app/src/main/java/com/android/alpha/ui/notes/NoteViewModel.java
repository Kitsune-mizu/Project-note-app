package com.android.alpha.ui.notes;

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

/**
 * ViewModel untuk mengelola data catatan menggunakan SharedPreferences sebagai penyimpanan.
 * Data disimpan per-user berdasarkan userId yang diset sebelum operasi apapun.
 */
public class NoteViewModel extends ViewModel {

    // Tag untuk logging
    private static final String TAG = "NoteViewModel";

    // --- Fields ---

    // LiveData daftar catatan aktif yang diobservasi oleh UI
    private final MutableLiveData<List<Note>> activeNotes = new MutableLiveData<>(new ArrayList<>());

    // ID pengguna yang sedang aktif, digunakan sebagai namespace penyimpanan
    private String userId;

    // Instance Gson untuk serialisasi/deserialisasi objek Note
    private final Gson gson = new Gson();

    // --- User ID Management ---

    /** Set userId sebelum melakukan operasi apapun pada catatan */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** Kembalikan userId yang sedang aktif */
    public String getUserId() {
        return userId;
    }

    // --- LiveData Accessors ---

    /** Kembalikan LiveData daftar catatan aktif untuk diobservasi oleh UI */
    public LiveData<List<Note>> getActiveNotes() {
        return activeNotes;
    }

    // --- CRUD Operations ---

    /**
     * Muat semua catatan dari SharedPreferences, urutkan berdasarkan pin lalu timestamp terbaru,
     * kemudian posting hasilnya ke LiveData.
     */
    public void loadNotes(Context context) {
        if (userId == null || context == null) return;

        List<Note> notesList = new ArrayList<>();
        try {
            Map<String, ?> notesMap = getPrefs(context).getAll();
            for (Object value : notesMap.values()) {
                if (!(value instanceof String)) continue;
                Note note = safeFromJson((String) value);
                if (note != null) notesList.add(note);
            }

            // Urutan: pinned di atas, lalu timestamp terbaru
            notesList.sort((a, b) -> {
                if (a.isPinned() && !b.isPinned()) return -1;
                if (!a.isPinned() && b.isPinned()) return 1;
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });

            activeNotes.postValue(notesList);
        } catch (Exception e) {
            Log.e(TAG, "loadNotes error", e);
        }
    }

    /**
     * Simpan catatan ke SharedPreferences.
     * Timestamp catatan akan diperbarui secara otomatis sebelum disimpan.
     */
    public void saveNote(Context context, Note note) {
        if (userId == null || context == null || note == null) return;

        note.setTimestamp(System.currentTimeMillis());
        try {
            putNote(context, note.getId(), note);
        } catch (Exception e) {
            Log.e(TAG, "saveNote error", e);
        }

        refreshNotes(context);
    }

    /**
     * Hapus catatan dari SharedPreferences berdasarkan ID-nya.
     */
    public void deleteNote(Context context, String id) {
        if (userId == null || context == null || id == null) return;

        try {
            getPrefs(context).edit().remove(id).apply();
        } catch (Exception e) {
            Log.e(TAG, "deleteNote error", e);
        }

        refreshNotes(context);
    }

    /**
     * Ubah status pin catatan berdasarkan ID.
     * Timestamp catatan akan diperbarui setelah perubahan pin.
     */
    public void pinNote(Context context, String id, boolean pinned) {
        if (userId == null || context == null || id == null) return;

        try {
            Note note = getNoteById(context, id);
            if (note == null) return;

            note.setPinned(pinned);
            note.setTimestamp(System.currentTimeMillis());
            putNote(context, id, note);

            refreshNotes(context);
        } catch (Exception e) {
            Log.e(TAG, "pinNote error", e);
        }
    }

    // --- Data Retrieval ---

    /**
     * Ambil satu catatan dari SharedPreferences berdasarkan ID.
     * Kembalikan null jika tidak ditemukan atau terjadi error.
     */
    public Note getNoteById(Context context, String id) {
        if (userId == null || context == null || id == null) return null;

        try {
            String raw = getPrefs(context).getString(id, null);
            return raw == null ? null : gson.fromJson(raw, Note.class);
        } catch (Exception e) {
            Log.e(TAG, "getNoteById error", e);
            return null;
        }
    }

    // --- Utility ---

    /** Muat ulang catatan dari penyimpanan dan perbarui LiveData */
    public void refreshNotes(Context context) {
        loadNotes(context);
    }

    /** Kembalikan SharedPreferences yang digunakan untuk menyimpan catatan user ini */
    private SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences("notes_" + userId, Context.MODE_PRIVATE);
    }

    /**
     * Deserialisasi JSON ke objek Note dengan aman.
     * Kembalikan null dan log warning jika JSON tidak valid.
     */
    private Note safeFromJson(String json) {
        try {
            return gson.fromJson(json, Note.class);
        } catch (Exception e) {
            Log.w(TAG, "Skipping invalid note json", e);
            return null;
        }
    }

    /** Simpan objek Note ke SharedPreferences dalam bentuk JSON string */
    private void putNote(Context context, String id, Note note) {
        getPrefs(context).edit().putString(id, gson.toJson(note)).apply();
    }
}