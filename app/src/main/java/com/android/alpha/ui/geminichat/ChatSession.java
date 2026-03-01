package com.android.alpha.ui.geminichat;

import java.util.List;

/**
 * Represents a single chat session (conversation thread).
 * Stored per-user using UserSession's SharedPreferences key system.

 * ownerUsername ties the session to a specific account so history
 * is isolated per user — consistent with UserSession's pattern.

 * Catatan: getter/setter createdAt, updatedAt, ownerUsername, dan
 * setMessages sengaja dipertahankan karena digunakan oleh Gson
 * saat serialisasi/deserialisasi ke SharedPreferences. Suppress
 * warning "never used" via @SuppressWarnings karena Gson
 * membutuhkannya via reflection.
 */
@SuppressWarnings("unused")   // Gson memerlukan getter/setter via reflection
public class ChatSession {

    private final String id;
    private String title;
    private List<ChatMessage> messages;
    private final String ownerUsername;
    private long createdAt;
    private long updatedAt;

    public ChatSession(String id, String title, List<ChatMessage> messages, String ownerUsername) {
        this.id            = id;
        this.title         = title;
        this.messages      = messages;
        this.ownerUsername = ownerUsername;
        this.createdAt     = System.currentTimeMillis();
        this.updatedAt     = this.createdAt;
    }

    // ─── Getters / Setters ──────────────────────────────────────────────────

    public String getId()             { return id; }

    public String getTitle()          { return title; }
    public void setTitle(String title) {
        this.title     = title;
        this.updatedAt = System.currentTimeMillis();
    }

    public List<ChatMessage> getMessages()                  { return messages; }
    public void setMessages(List<ChatMessage> messages) {
        this.messages  = messages;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getOwnerUsername()                        { return ownerUsername; }

    public long getCreatedAt()                              { return createdAt; }
    public void setCreatedAt(long createdAt)                { this.createdAt = createdAt; }

    public long getUpdatedAt()                              { return updatedAt; }
    public void setUpdatedAt(long updatedAt)                { this.updatedAt = updatedAt; }
}