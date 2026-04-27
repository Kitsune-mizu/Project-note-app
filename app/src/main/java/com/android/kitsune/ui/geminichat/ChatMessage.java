package com.android.kitsune.ui.geminichat;

public class ChatMessage {

    // ─── Constants ───────────────────────────────────────────────────────────

    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_LOADING = 2;
    public static final int TYPE_ERROR = 3;


    // ─── Variables ───────────────────────────────────────────────────────────

    private String text;
    private final int type;
    private final long timestamp;


    // ─── Constructor ─────────────────────────────────────────────────────────

    public ChatMessage(String text, int type) {
        this.text = text;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }


    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getText() {
        return text;
    }

    public void setText(String t) {
        this.text = t;
    }

    public int getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }
}