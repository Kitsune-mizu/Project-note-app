package com.android.alpha.ui.notes;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Model entity untuk menyimpan data catatan ke database Room.
 * Mengimplementasikan Serializable agar bisa dikirim antar komponen.
 */
@Entity(tableName = "notes")
public class Note implements Serializable {

    // Primary key unik berdasarkan timestamp saat dibuat
    @PrimaryKey
    @NonNull
    private String id;

    // Judul catatan
    private String title = "";

    // Isi catatan (bisa berformat HTML)
    private String content = "";

    // Waktu terakhir catatan dibuat/diubah (dalam milliseconds)
    private long timestamp;

    // Status pin catatan
    private boolean pinned = false;

    // --- Constructors ---

    /** Membuat catatan baru dengan ID otomatis dari waktu saat ini */
    public Note() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.timestamp = System.currentTimeMillis();
    }

    /** Membuat catatan dengan ID yang ditentukan secara manual */
    public Note(@NonNull String id) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
    }

    // --- Getters ---

    @NonNull
    public String getId() { return id; }

    public String getTitle() { return title; }

    public String getContent() { return content; }

    public long getTimestamp() { return timestamp; }

    public boolean isPinned() { return pinned; }

    // --- Setters ---

    public void setId(@NonNull String id) { this.id = id; }

    public void setTitle(String title) { this.title = title; }

    public void setContent(String content) { this.content = content; }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public void setPinned(boolean pinned) { this.pinned = pinned; }

    // --- Utility ---

    /**
     * Mengembalikan pratinjau isi catatan (maks 100 karakter).
     * HTML di-strip, newline diganti spasi, dan diberi "..." jika terpotong.
     */
    public String getSubtitle() {
        String cleaned = android.text.Html
                .fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .trim()
                .replace("\n", " ");

        return cleaned.length() > 100
                ? cleaned.substring(0, 100) + "..."
                : cleaned;
    }

    /**
     * Mengembalikan tanggal terformat dari timestamp catatan.
     * Contoh output: "Jan 21, 14:30"
     */
    public String getFormattedDate() {
        return new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }
}