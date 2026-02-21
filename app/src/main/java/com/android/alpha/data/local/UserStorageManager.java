package com.android.alpha.data.local;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.*;
import java.util.Objects;

/**
 * Singleton manager responsible for all file-based user data operations,
 * including profile save/load, user directory management, and active user context.
 */
public class UserStorageManager {

    // ─── TAG ───────────────────────────────────────────────────────────────────
    private static final String TAG = "UserStorageManager";

    // ─── SINGLETON ─────────────────────────────────────────────────────────────
    private static UserStorageManager instance;

    // ─── DEPENDENCIES ──────────────────────────────────────────────────────────
    private final Context context;

    // ─── ACTIVE SESSION STATE ──────────────────────────────────────────────────
    private String currentUserId   = null;
    private String currentUsername = null;

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Private constructor — use {@link #getInstance(Context)}. */
    public UserStorageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Returns the singleton instance, creating it if necessary. Thread-safe. */
    public static synchronized UserStorageManager getInstance(Context context) {
        if (instance == null) instance = new UserStorageManager(context);
        return instance;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER CONTEXT / SESSION MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets the active user context and ensures the user's dedicated directory exists.
     * Must be called after a successful login before accessing user files.
     */
    public void switchUserContext(String userId, String username) {
        this.currentUserId   = userId;
        this.currentUsername = username;
        createDirectoryIfNeeded(getUserDir(userId), "user folder for " + username);
    }

    /** Clears the active user context (called on logout or account deletion). */
    public void clearActiveContext() {
        currentUserId   = null;
        currentUsername = null;
        Log.d(TAG, "Cleared active user context");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates and saves an initial profile.json for a new user.
     * @param initialPhotoPath optional path to profile photo; stored as empty string if null.
     */
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

    /**
     * Writes a JSONObject as profile.json inside the user's directory.
     * Does nothing if profileJson is null.
     */
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

    /**
     * Loads the profile.json for the currently active user.
     * @return the parsed JSONObject, or null if no active user or file is missing.
     */
    public JSONObject loadActiveUserProfile() {
        if (currentUserId == null) {
            Log.w(TAG, "Cannot load active profile. Active user ID is null.");
            return null;
        }
        return loadUserProfile(currentUserId);
    }

    /**
     * Reads and parses profile.json for the given userId.
     * @return the parsed JSONObject, or null if the file does not exist or fails to parse.
     */
    public JSONObject loadUserProfile(String userId) {
        File file = new File(getUserDir(userId), "profile.json");
        if (!file.exists()) {
            Log.w(TAG, "Profile file not found for user: " + userId);
            return null;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load profile: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER FILE & DIRECTORY UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Creates the user's root directory if it does not already exist. */
    public void createUserFolder(String userId, String username) {
        createDirectoryIfNeeded(getUserDir(userId), "user folder for " + username);
    }

    /**
     * Returns a File reference to a named file within the active user's directory.
     * Falls back to app's files directory if no user is active.
     */
    public File getUserFile(String filename) {
        return new File(getActiveUserDir(), filename);
    }

    /** Deletes the entire directory tree for the given userId, if it exists. */
    public void deleteUserFolder(String userId) {
        File dir = getUserDir(userId);
        if (dir.exists()) {
            deleteRecursive(dir);
            Log.d(TAG, "Deleted user folder: " + dir.getAbsolutePath());
        }
    }

    /** Returns the directory of the active user, or the app's files dir as fallback. */
    private File getActiveUserDir() {
        if (currentUserId == null) {
            Log.w(TAG, "Active user ID is null — defaulting to app files dir");
            return context.getFilesDir();
        }
        return getUserDir(currentUserId);
    }

    /** Returns the root File directory for a given userId. */
    private File getUserDir(String userId) {
        return new File(context.getFilesDir(), "user_" + userId);
    }

    /** Creates the given directory (and any parents) if it does not already exist. */
    private void createDirectoryIfNeeded(File dir, String logName) {
        if (!dir.exists()) {
            if (dir.mkdirs()) Log.d(TAG, "Created " + logName + ": " + dir.getAbsolutePath());
            else              Log.e(TAG, "Failed to create " + logName);
        } else {
            Log.d(TAG, "Using existing " + logName + ": " + dir.getAbsolutePath());
        }
    }

    /** Recursively deletes a file or directory tree. Logs a warning for any failure. */
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        if (!file.delete()) Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns the username of the currently active user. */
    public String getCurrentUsername() { return currentUsername; }

    /** Sets the username for the currently active user. */
    public void setCurrentUsername(String currentUsername) { this.currentUsername = currentUsername; }
}