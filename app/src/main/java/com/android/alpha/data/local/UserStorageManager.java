package com.android.alpha.data.local;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.*;
import java.util.Objects;

public class UserStorageManager {

    // ─── Tag & Variables ─────────────────────────────────────────────────
    private static final String TAG = "UserStorageManager";
    private static UserStorageManager instance;
    private final Context context;
    private String currentUserId = null;
    private String currentUsername = null;

    // ─── Singleton & Initialization ──────────────────────────────────────

    public UserStorageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized UserStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserStorageManager(context);
        }
        return instance;
    }

    // ─── User Context & Session ──────────────────────────────────────────

    public void switchUserContext(String userId, String username) {
        this.currentUserId = userId;
        this.currentUsername = username;
        createDirectoryIfNeeded(getUserDir(userId), "user folder for " + username);
    }

    public void clearActiveContext() {
        currentUserId = null;
        currentUsername = null;
        Log.d(TAG, "Cleared active user context");
    }

    // ─── Profile Management ──────────────────────────────────────────────

    public void saveUserProfile(String userId, String username, String email, String initialPhotoPath) {
        try {
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("email", email);
            json.put("photoPath", initialPhotoPath != null ? initialPhotoPath : "");
            saveUserProfile(userId, json);
            Log.d(TAG, "Saved INITIAL profile for " + username);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save initial profile: " + e.getMessage());
        }
    }

    public void saveUserProfile(String userId, JSONObject profileJson) {
        if (profileJson == null) {
            Log.e(TAG, "Attempted to save a null profile JSON for user: " + userId);
            return;
        }

        File file = new File(getUserDir(userId), "profile.json");
        createDirectoryIfNeeded(Objects.requireNonNull(file.getParentFile()), "directory for user: " + userId);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(profileJson.toString(2));
            Log.d(TAG, "Saved profile update for user ID: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save updated profile: " + e.getMessage(), e);
        }
    }

    public JSONObject loadActiveUserProfile() {
        if (currentUserId == null) {
            Log.w(TAG, "Cannot load active profile. Active user ID is null.");
            return null;
        }
        return loadUserProfile(currentUserId);
    }

    public JSONObject loadUserProfile(String userId) {
        File file = new File(getUserDir(userId), "profile.json");
        if (!file.exists()) {
            Log.w(TAG, "Profile file not found for user: " + userId);
            return null;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load profile: " + e.getMessage());
            return null;
        }
    }

    // ─── File & Directory Utilities ──────────────────────────────────────

    public void createUserFolder(String userId, String username) {
        createDirectoryIfNeeded(getUserDir(userId), "user folder for " + username);
    }

    public File getUserFile(String filename) {
        return new File(getActiveUserDir(), filename);
    }

    public void deleteUserFolder(String userId) {
        File dir = getUserDir(userId);
        if (dir.exists()) {
            deleteRecursive(dir);
            Log.d(TAG, "Deleted user folder: " + dir.getAbsolutePath());
        }
    }

    private File getActiveUserDir() {
        if (currentUserId == null) {
            Log.w(TAG, "Active user ID is null — defaulting to app files dir");
            return context.getFilesDir();
        }
        return getUserDir(currentUserId);
    }

    private File getUserDir(String userId) {
        return new File(context.getFilesDir(), "user_" + userId);
    }

    private void createDirectoryIfNeeded(File dir, String logName) {
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                Log.d(TAG, "Created " + logName + ": " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create " + logName);
            }
        } else {
            Log.d(TAG, "Using existing " + logName + ": " + dir.getAbsolutePath());
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
        }
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }
}