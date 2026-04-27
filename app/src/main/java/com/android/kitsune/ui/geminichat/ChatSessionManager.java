package com.android.kitsune.ui.geminichat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;

import com.android.kitsune.R;
import com.android.kitsune.data.session.UserSession;
import com.android.kitsune.ui.notifications.ActivityItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatSessionManager {

    // ─── Constants & Variables ───────────────────────────────────────────────

    private static final String TAG = "ChatSessionManager";
    private static final String KEY_SESSIONS = "sessions_list";
    private static final int MAX_SESSIONS = 50;

    private static final String KEY_DAILY_COUNT = "daily_request_count";
    private static final String KEY_LAST_DATE = "daily_request_date";

    private static ChatSessionManager instance;

    private final Context context;
    private final Gson gson = new Gson();


    // ─── Singleton & Initialization ──────────────────────────────────────────

    private ChatSessionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static ChatSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChatSessionManager(context);
        }
        return instance;
    }


    // ─── Resource Helpers ────────────────────────────────────────────────────

    private int getAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }


    // ─── Preferences & Storage ───────────────────────────────────────────────

    private SharedPreferences getPrefsForCurrentUser() {
        UserSession session = UserSession.getInstance();
        if (!session.isLoggedIn() || session.getUsername() == null || session.getUsername().isEmpty()) {
            Log.w(TAG, "getPrefsForCurrentUser: no active user session.");
            return null;
        }
        return context.getSharedPreferences(
                "chat_sessions_" + session.getUsername(),
                Context.MODE_PRIVATE
        );
    }

    public List<ChatSession> loadSessions() {
        SharedPreferences prefs = getPrefsForCurrentUser();
        if (prefs == null) return new ArrayList<>();

        String json = prefs.getString(KEY_SESSIONS, "");
        if (json.isEmpty()) return new ArrayList<>();

        try {
            List<ChatSession> sessions = gson.fromJson(
                    json, new TypeToken<List<ChatSession>>() {}.getType());
            return sessions != null ? sessions : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "loadSessions failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveSessions(List<ChatSession> sessions) {
        SharedPreferences prefs = getPrefsForCurrentUser();
        if (prefs == null) return;

        List<ChatSession> capped = sessions.size() > MAX_SESSIONS
                ? new ArrayList<>(sessions.subList(0, MAX_SESSIONS))
                : sessions;

        try {
            prefs.edit().putString(KEY_SESSIONS, gson.toJson(capped)).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveSessions failed: " + e.getMessage());
        }
    }


    // ─── Session Operations ──────────────────────────────────────────────────

    public void upsertSession(ChatSession session) {
        List<ChatSession> sessions = loadSessions();
        int idx = indexById(sessions, session.getId());

        if (idx >= 0) {
            sessions.set(idx, session);
        } else {
            sessions.add(0, session);
            logActivity(
                    R.string.activity_new_chat_title,
                    R.string.activity_new_chat_message,
                    R.drawable.ic_chat_bubble,
                    R.attr.color_1
            );
        }
        saveSessions(sessions);
    }

    public void renameSession(String sessionId, String newTitle) {
        List<ChatSession> sessions = loadSessions();
        int idx = indexById(sessions, sessionId);
        if (idx < 0) return;

        sessions.get(idx).setTitle(newTitle);
        saveSessions(sessions);
    }

    public void deleteSession(String sessionId) {
        List<ChatSession> sessions = loadSessions();
        int idx = indexById(sessions, sessionId);
        if (idx < 0) return;

        sessions.remove(idx);
        saveSessions(sessions);
        logActivity(
                R.string.activity_chat_deleted_title,
                R.string.activity_chat_deleted_message,
                R.drawable.ic_delete,
                R.attr.color_error
        );
    }


    // ─── Lifecycle Hooks ─────────────────────────────────────────────────────

    public void onUserLogout(String username) {
        Log.d(TAG, "onUserLogout: preserving sessions for " + username);
    }

    public void onAccountDeleted(String username) {
        context.getSharedPreferences("chat_sessions_" + username, Context.MODE_PRIVATE)
                .edit().clear().apply();
        Log.d(TAG, "onAccountDeleted: chat sessions wiped for " + username);
    }


    // ─── Daily Limits ────────────────────────────────────────────────────────

    public void resetDailyCountIfNewDay() {
        SharedPreferences prefs = getPrefsForCurrentUser();
        if (prefs == null) return;

        String today = todayString();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (!today.equals(lastDate)) {
            prefs.edit()
                    .putInt(KEY_DAILY_COUNT, 0)
                    .putString(KEY_LAST_DATE, today)
                    .apply();
        }
    }

    public int getDailyCount() {
        SharedPreferences prefs = getPrefsForCurrentUser();
        if (prefs == null) return 0;

        resetDailyCountIfNewDay();
        return prefs.getInt(KEY_DAILY_COUNT, 0);
    }

    public void incrementDailyCount() {
        SharedPreferences prefs = getPrefsForCurrentUser();
        if (prefs == null) return;

        resetDailyCountIfNewDay();
        int current = prefs.getInt(KEY_DAILY_COUNT, 0);
        prefs.edit().putInt(KEY_DAILY_COUNT, current + 1).apply();
    }


    // ─── Private Helpers ─────────────────────────────────────────────────────

    private int indexById(List<ChatSession> sessions, String id) {
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private String todayString() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    private void logActivity(int titleRes, int descRes, int iconRes, int colorAttr) {
        try {
            UserSession userSession = UserSession.getInstance();
            if (!userSession.isLoggedIn()) return;

            userSession.addActivity(new ActivityItem(
                    titleRes,
                    descRes,
                    System.currentTimeMillis(),
                    iconRes,
                    getAttrColor(colorAttr),
                    userSession.getUsername()
            ));
        } catch (Exception e) {
            Log.w(TAG, "logActivity skipped: " + e.getMessage());
        }
    }
}