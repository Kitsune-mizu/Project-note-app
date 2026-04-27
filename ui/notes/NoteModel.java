package com.android.kitsune.ui.notes;

public class NoteModel {
    public int id;
    public String title;
    public String content;
    public String date;
    public boolean pinned;

    public NoteModel(int id, String title, String content, String date) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.date = date;
        this.pinned = false;
    }

    // --- getters ---
    public int getId() {
        return id;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public String getContent() {
        return content != null ? content : "";
    }

    public String getDate() {
        return date != null ? date : "";
    }

    public boolean isPinned() {
        return pinned;
    }

    // --- setters ---
    public void setId(int id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}
