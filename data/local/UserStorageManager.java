package com.android.kitsune.data.local;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;

public class UserStorageManager {
    private static final String TAG = "UserStorageManager";
    private final Context context;
    private static UserStorageManager instance;

    private String currentUserId = null;
    private String currentUsername = null;

    public UserStorageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized UserStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserStorageManager(context.getApplicationContext());
        }
        return instance;
    }

    // =====================================================
    // === USER CONTEXT SESSION SUPPORT ====================
    // =====================================================
    public void switchUserContext(String userId, String username) {
        this.currentUserId = userId;
        this.currentUsername = username;

        File userDir = new File(context.getFilesDir(), "user_" + userId);
        if (!userDir.exists()) {
            boolean created = userDir.mkdirs();
            if (created) {
                Log.d(TAG, "Created folder for " + username + ": " + userDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create user folder for " + username);
            }
        } else {
            Log.d(TAG, "Switched to existing folder for " + username + ": " + userDir.getAbsolutePath());
        }
    }

    public void clearActiveContext() {
        currentUserId = null;
        currentUsername = null;
        Log.d(TAG, "Cleared active user context");
    }

    private File getActiveUserDir() {
        if (currentUserId == null) {
            Log.w(TAG, "Active user ID is null — defaulting to app files dir");
            return context.getFilesDir();
        }
        return new File(context.getFilesDir(), "user_" + currentUserId);
    }

    // =====================================================
    // === PROFILE MANAGEMENT ==============================
    // =====================================================

    public void createUserFolder(String userId, String username) {
        File userDir = new File(context.getFilesDir(), "user_" + userId);
        if (!userDir.exists()) {
            boolean created = userDir.mkdirs();
            if (created) {
                Log.d(TAG, "Created folder for " + username + ": " + userDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create user folder for " + username);
            }
        }
    }

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

        try {
            File file = new File(context.getFilesDir(), "user_" + userId + "/profile.json");

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory for user: " + userId);
                    return;
                }
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(profileJson.toString(2));
                writer.flush();
            }

            Log.d(TAG, "Saved profile update for user ID: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save updated profile: " + e.getMessage(), e);
        }
    }

    public JSONObject loadActiveUserProfile() {
        if (currentUserId == null) {
            Log.w(TAG, "loadActiveUserProfile()");
            return null;
        }
        return loadUserProfile(currentUserId);
    }

    public JSONObject loadUserProfile(String userId) {
        File file = new File(context.getFilesDir(), "user_" + userId + "/profile.json");
        if (!file.exists()) {
            Log.w(TAG, "Profile file not found for user: " + userId);
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            BufferedReader br = new BufferedReader(reader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load profile: " + e.getMessage());
        }
        return null;
    }

    // =====================================================
    // === USER DATA / FILE MANAGEMENT =====================
    // =====================================================

    public File getUserFile(String filename) {
        File dir = getActiveUserDir();
        return new File(dir, filename);
    }

    public void deleteUserFolder(String userId) {
        File dir = new File(context.getFilesDir(), "user_" + userId);
        if (dir.exists()) {
            deleteRecursive(dir);
            Log.d(TAG, "Deleted user folder: " + dir.getAbsolutePath());
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
        }
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }
}
