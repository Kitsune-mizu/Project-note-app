package com.android.kitsune.ui.geminichat;

import java.util.List;

@SuppressWarnings("unused")
public class ChatSession {

    // ─── Variables ───────────────────────────────────────────────────────────

    private final String id;
    private String title;
    private List<ChatMessage> messages;
    private final String ownerUsername;
    private long createdAt;
    private long updatedAt;


    // ─── Constructor ─────────────────────────────────────────────────────────

    public ChatSession(String id, String title, List<ChatMessage> messages, String ownerUsername) {
        this.id = id;
        this.title = title;
        this.messages = messages;
        this.ownerUsername = ownerUsername;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }


    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = System.currentTimeMillis();
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}