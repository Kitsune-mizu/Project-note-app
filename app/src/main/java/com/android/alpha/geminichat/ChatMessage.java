package com.android.alpha.geminichat;

/**
 * Immutable data model untuk satu pesan dalam sesi chat.
 * timestamp digunakan oleh DiffUtil di ChatActivity untuk
 * identifikasi item secara unik.
 */
public class ChatMessage {

    public static final int TYPE_USER    = 0;
    public static final int TYPE_AI      = 1;
    public static final int TYPE_LOADING = 2;
    public static final int TYPE_ERROR   = 3;

    private String     text;
    private final int  type;
    private final long timestamp;   // dipakai DiffUtil.areItemsTheSame()

    public ChatMessage(String text, int type) {
        this.text      = text;
        this.type      = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getText()           { return text; }
    public void   setText(String t)   { this.text = t; }
    public int    getType()           { return type; }
    public long   getTimestamp()      { return timestamp; }
}