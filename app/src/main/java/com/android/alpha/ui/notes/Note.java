package com.android.alpha.ui.notes;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note implements Serializable {

    // ─── Variables ───────────────────────────────────────────────────────────

    @PrimaryKey
    @NonNull
    private String id;
    private String title = "";
    private String content = "";
    private long timestamp;
    private boolean pinned = false;


    // ─── Constructors ────────────────────────────────────────────────────────

    public Note() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.timestamp = System.currentTimeMillis();
    }

    public Note(@NonNull String id) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
    }


    // ─── Getters & Setters ───────────────────────────────────────────────────

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }


    // ─── Utilities ───────────────────────────────────────────────────────────

    public String getSubtitle() {
        String cleaned = android.text.Html
                .fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .trim()
                .replace("\n", " ");

        if (cleaned.length() > 100) {
            return cleaned.substring(0, 100) + "...";
        } else {
            return cleaned;
        }
    }

    public String getFormattedDate() {
        return new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }
}