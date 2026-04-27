package com.android.kitsune.data.session;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.android.kitsune.R;
import com.android.kitsune.data.local.UserStorageManager;
import com.android.kitsune.ui.home.ActivityItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.*;

public class UserSession {

    // ---------- MODEL ----------
    public static class UserData {
        String username;
        String password;
        public String userId;

        UserData(String username, String password, String userId) {
            this.username = username;
            this.password = password;
            this.userId = userId;
        }
    }

    private final UserStorageManager storageManager;

    // ---------- CONSTANTS ----------
    private static final String PREF_NAME = "user_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ACTIVITIES = "user_activities"; // Cache aktivitas sesi saat ini
    private static final String KEY_FIRST_LOGIN = "first_login";
    private static final String KEY_USERS_MAP = "users_map";
    private static final String TAG = "UserSession";

    // ---------- INSTANCE & CONTEXT ----------
    private static UserSession instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    private final Gson gson = new Gson();


    private SharedPreferences userPrefs;
    private SharedPreferences.Editor userEditor;

    private SharedPreferences getUserPrefs(String username) {
        return context.getSharedPreferences("session_" + username, Context.MODE_PRIVATE);
    }

    private void switchToUserPrefs(String username) {
        userPrefs = getUserPrefs(username);
        userEditor = userPrefs.edit();
    }

    // ---------- CACHE ----------
    private boolean isLoggedInCache;
    private String usernameCache;

    // ---------- MULTI-USER DATA CACHE ----------
    private final Map<String, UserData> usersMap = new HashMap<>();

    // ---------- LISTENERS ----------
    private final List<UserSessionListener> listeners = new ArrayList<>();
    private final List<ActivityListener> activityListeners = new ArrayList<>();
    private UserSessionListener badgeListener;
    private static final String KEY_LANGUAGE = "app_language";

    public void setLanguage(String langCode) {
        editor.putString(KEY_LANGUAGE, langCode).apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "en"); // default English
    }

    public interface UserSessionListener {
        default void onProfileUpdated() {}
        default void onBadgeCleared() {}
    }

    public interface ActivityListener {
        void onNewActivity(ActivityItem item);
    }

    // ---------- LISTENER MANAGEMENT ----------
    public void setBadgeListener(UserSessionListener listener) {
        this.badgeListener = listener;
    }

    public void notifyBadgeCleared() {
        if (badgeListener != null) badgeListener.onBadgeCleared();
    }

    public void addListener(UserSessionListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(UserSessionListener listener) {
        listeners.remove(listener);
    }

    public void addActivityListener(ActivityListener listener) {
        if (!activityListeners.contains(listener)) activityListeners.add(listener);
    }

    public void removeActivityListener(ActivityListener listener) {
        activityListeners.remove(listener);
    }

    public void notifyProfileUpdated() {
        for (UserSessionListener listener : listeners) {
            listener.onProfileUpdated();
        }
    }

    // ---------- INITIALIZATION ----------
    private UserSession(Context context) {
        this.context = context.getApplicationContext();
        this.storageManager = new UserStorageManager(context);
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
        loadUsersMap();
        isLoggedInCache = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        usernameCache = prefs.getString(KEY_USERNAME, "");
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new UserSession(context);
        }
    }

    public static UserSession getInstance() {
        if (instance == null) {
            throw new IllegalStateException("UserSession not initialized. Call init(context) first.");
        }
        return instance;
    }

    // ---------- CACHE GETTERS/SETTERS ----------
    public void refreshCache() {
        isLoggedInCache = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        usernameCache = prefs.getString(KEY_USERNAME, "");
        notifyProfileUpdated();
    }

    public boolean isLoggedIn() {
        return isLoggedInCache;
    }

    public String getUsername() {
        return usernameCache;
    }

    public void setUsername(String username) {
        usernameCache = username;
        editor.putString(KEY_USERNAME, username).apply();
        notifyProfileUpdated();
        addProfileUpdateActivity();
    }

    // ---------- MULTI-USER MANAGEMENT ----------
    private void loadUsersMap() {
        String json = prefs.getString(KEY_USERS_MAP, "");
        if (!json.isEmpty()) {
            try {
                java.lang.reflect.Type type = new TypeToken<Map<String, UserData>>() {}.getType();
                Map<String, UserData> map = gson.fromJson(json, type);
                usersMap.clear();
                if (map != null) usersMap.putAll(map);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load users map: " + e.getMessage());
            }
        }
    }

    public UserData getUserData(String username) {
        loadUsersMap();
        return usersMap.get(username);
    }

    private void saveUsersMap() {
        String json = gson.toJson(usersMap);
        editor.putString(KEY_USERS_MAP, json);
        editor.apply();
    }

    public boolean isUsername(String username) {
        loadUsersMap();
        return !usersMap.containsKey(username);
    }

    // ---------- VALIDATION ----------
    public static boolean isUsernameInvalid(String username) {
        return !username.matches("^(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d]{6,20}$");
    }

    public static boolean isPasswordInvalid(String password) {
        return !password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");
    }

    // ---------- AUTHENTICATION ----------
    public boolean registerUser(String username, String password) {
        if (isUsernameInvalid(username)) {
            Log.w(TAG, "Username must be capital letters + numbers, min 6 chars.");
            return false;
        }
        if (isPasswordInvalid(password)) {
            Log.w(TAG, "Password must be letters + numbers, min 8 chars.");
            return false;
        }

        loadUsersMap();

        if (usersMap.containsKey(username)) {
            Log.w(TAG, "Username already exists! Please choose a different one.");
            return false;
        }

        String uuid = UUID.randomUUID().toString();
        usersMap.put(username, new UserData(username, password, uuid));
        saveUsersMap();

        storageManager.createUserFolder(uuid, username);
        storageManager.saveUserProfile(uuid, username, username + "@example.com", null);

        Log.d(TAG, "User registered: " + username + " UUID:" + uuid);
        return true;
    }

    public boolean login(String username, String password) {
        loadUsersMap();

        UserData user = usersMap.get(username);
        if (user != null && user.password.equals(password)) {

            storageManager.createUserFolder(user.userId, username);
            JSONObject profile = storageManager.loadUserProfile(user.userId);
            if (profile == null) {
                storageManager.saveUserProfile(user.userId, username, username + "@example.com", null);
            }

            storageManager.switchUserContext(user.userId, username);
            JSONObject activeProfile = storageManager.loadActiveUserProfile();
            if (activeProfile != null) {
                Log.d(TAG, "Active profile loaded for " + username + ": " + activeProfile);
            } else {
                Log.w(TAG, "Active profile is null for " + username);
            }

            storageManager.setCurrentUsername(username);
            Log.d(TAG, "Current username set in storage manager: " + storageManager.getCurrentUsername());

            usernameCache = username;
            isLoggedInCache = true;
            editor.putString(KEY_USERNAME, username);
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.apply();

            switchToUserPrefs(username);
            loadUserCache(username);

            addLoginActivity();
            setFirstLoginIfNotExists();
            notifyProfileUpdated();

            Log.d(TAG, "Login successful → " + username + " UUID:" + user.userId);
            return true;
        }

        Log.w(TAG, "Login failed: " + username);
        return false;
    }

    public void logout() {
        try {
            if (usernameCache == null || usernameCache.isEmpty()) return;

            String loggedOutUser = usernameCache;

            switchToUserPrefs(loggedOutUser);
            saveUserCache(loggedOutUser);

            editor.remove(KEY_IS_LOGGED_IN);
            editor.remove(KEY_USERNAME);
            editor.apply();

            isLoggedInCache = false;
            usernameCache = "";

            addLogoutActivity();
            notifyProfileUpdated();
            storageManager.clearActiveContext();

            Log.d(TAG, "Logout successful for user: " + loggedOutUser);

        } catch (Exception e) {
            Log.e(TAG, "Logout error: " + e.getMessage(), e);
        }
    }

    private void loadUserCache(String username) {
        SharedPreferences userPrefs = context.getSharedPreferences("user_cache_" + username, Context.MODE_PRIVATE);
        String activitiesJson = userPrefs.getString(KEY_ACTIVITIES, "");
        editor.putString(KEY_ACTIVITIES, activitiesJson).apply();
    }

    private void saveUserCache(String username) {
        SharedPreferences userPrefs = context.getSharedPreferences("user_cache_" + username, Context.MODE_PRIVATE);
        SharedPreferences.Editor userEditor = userPrefs.edit();

        String activitiesJson = prefs.getString(KEY_ACTIVITIES, "");

        userEditor.putString(KEY_ACTIVITIES, activitiesJson).apply();
    }

    public boolean resetPassword(String username, String oldPassword, String newPassword) {
        UserData user = usersMap.get(username);
        if (user == null || !user.password.equals(oldPassword)) return false;

        if (isPasswordInvalid(newPassword)) {
            Log.w(TAG, "New password invalid: letters+numbers, min 8 chars.");
            return false;
        }

        user.password = newPassword;
        usersMap.put(username, user);
        saveUsersMap();

        if (username.equals(usernameCache)) {
            refreshCache();
        }

        Log.d(TAG, "Password reset successful for user: " + username);
        return true;
    }

    public boolean deleteAccount(String username) {
        UserData user = usersMap.get(username);
        if (user == null) return false;

        String userId = user.userId;

        usersMap.remove(username);
        saveUsersMap();

        storageManager.deleteUserFolder(userId);
        context.deleteSharedPreferences("session_" + username);
        storageManager.clearActiveContext();

        clearUserProfilePrefs(username);
        clearUserSpecificData(userId);
        clearActivitiesForUser(userId);
        clearUserCache(username);

        if (username.equals(usernameCache)) {
            usernameCache = "";
            isLoggedInCache = false;
            editor.remove(KEY_IS_LOGGED_IN);
            editor.remove(KEY_USERNAME);
            editor.remove(KEY_FIRST_LOGIN);
            editor.remove(KEY_ACTIVITIES);
            editor.apply();
        }

        // ===== Update: gunakan string resource =====
        addActivity(new ActivityItem(
                R.string.activity_account_deleted_title,
                R.string.activity_account_deleted_message,
                System.currentTimeMillis(),
                R.drawable.ic_delete,
                ContextCompat.getColor(context, R.color.md_theme_light_error),
                usernameCache
        ));

        notifyProfileUpdated();
        Log.d(TAG, "User " + username + " deleted successfully with UUID: " + userId);
        return true;
    }

    private void clearUserProfilePrefs(String username) {
        SharedPreferences profilePrefs = context.getSharedPreferences("user_profile_" + username, Context.MODE_PRIVATE);
        profilePrefs.edit().clear().apply();
    }

    private void clearUserSpecificData(String userId) {
        try {
            SharedPreferences userPrefs = context.getSharedPreferences("user_data_" + userId, Context.MODE_PRIVATE);
            userPrefs.edit().clear().apply();
            context.deleteFile("profile_pic_" + userId + ".jpg");
            context.deleteFile("profile_pic_" + userId + ".png");
            Log.d(TAG, "User specific data cleared for UUID: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear user data: " + e.getMessage());
        }
    }

    private void clearActivitiesForUser(String userId) {
        List<ActivityItem> activities = getActivities();
        List<ActivityItem> filtered = new ArrayList<>();
        for (ActivityItem activity : activities) {
            if (activity.getUserId() == null || !activity.getUserId().equals(userId)) {
                filtered.add(activity);
            }
        }

        saveActivities(filtered);
    }

    private void clearUserCache(String username) {
        try {
            SharedPreferences userPrefs = context.getSharedPreferences("user_cache_" + username, Context.MODE_PRIVATE);
            userPrefs.edit().clear().apply();

            String[] files = context.fileList();
            for (String file : files) {
                if (file.startsWith("cache_" + username) || file.startsWith("cookie_" + username) || file.contains(username)) {
                    context.deleteFile(file);
                }
            }
            Log.d(TAG, "Cache and cookies for user " + username + " deleted");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete user cache/cookies " + username + ": " + e.getMessage());
        }
    }

    // ---------- ACTIVITY LOG ----------
    public List<ActivityItem> getActivities() {
        SharedPreferences prefSource = (isLoggedInCache && userPrefs != null)
                ? userPrefs : prefs;
        String json = prefSource.getString(KEY_ACTIVITIES, "");
        if (json.isEmpty()) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<ActivityItem>>(){}.getType());
    }

    public void saveActivities(List<ActivityItem> activities) {
        SharedPreferences.Editor editTarget = (isLoggedInCache && userEditor != null)
                ? userEditor : editor;
        editTarget.putString(KEY_ACTIVITIES, gson.toJson(activities)).apply();
    }

    public void clearActivities() {
        editor.remove(KEY_ACTIVITIES).apply();
    }

    public void addActivity(ActivityItem item) {
        if (item == null) return;

        if (item.getTitleResId() == 0 || item.getDescriptionResId() == 0) {
            Log.w(TAG, "Skipping activity with invalid string resource IDs: " + item);
            return;
        }

        List<ActivityItem> activities = getActivities();
        long now = System.currentTimeMillis();
        long sevenDays = 7L * 24 * 60 * 60 * 1000;
        List<ActivityItem> recent = new ArrayList<>();
        for (ActivityItem a : activities) if (now - a.getTimestamp() <= sevenDays) recent.add(a);

        recent.add(0, item);
        if (recent.size() > 10) recent = new ArrayList<>(recent.subList(0, 10));
        saveActivities(recent);

        for (ActivityListener listener : activityListeners) listener.onNewActivity(item);

        String title = context.getString(item.getTitleResId());
        if (!title.equals(context.getString(R.string.activity_login_title))
                && !title.equals(context.getString(R.string.activity_logout_title))) {
            showSystemNotificationInternal(item);
        }
    }

    public void removeOldActivities() {
        List<ActivityItem> activities = getActivities();
        if (activities.isEmpty()) return;

        long now = System.currentTimeMillis();
        long sevenDays = 7L * 24 * 60 * 60 * 1000;
        List<ActivityItem> recent = new ArrayList<>();
        for (ActivityItem item : activities) if (now - item.getTimestamp() <= sevenDays) recent.add(item);
        saveActivities(recent);
    }

    // --- LOGIN ACTIVITY ---
    public void addLoginActivity() {
        int titleRes = R.string.activity_login_title;
        int descRes = R.string.activity_login_message;

        if (titleRes == 0 || descRes == 0) {
            Log.w(TAG, "Skipping login activity: invalid string resources");
            return;
        }

        addActivity(new ActivityItem(
                titleRes,
                descRes,
                System.currentTimeMillis(),
                R.drawable.ic_login,
                ContextCompat.getColor(context, R.color.green),
                usernameCache
        ));
    }

    // --- LOGOUT ACTIVITY ---
    public void addLogoutActivity() {
        int titleRes = R.string.activity_logout_title;
        int descRes = R.string.activity_logout_message;

        if (titleRes == 0 || descRes == 0) {
            Log.w(TAG, "Skipping logout activity: invalid string resources");
            return;
        }

        addActivity(new ActivityItem(
                titleRes,
                descRes,
                System.currentTimeMillis(),
                R.drawable.ic_logout,
                ContextCompat.getColor(context, R.color.md_theme_light_error),
                usernameCache
        ));
    }

    // --- PROFILE UPDATE ACTIVITY ---
    public void addProfileUpdateActivity() {
        int titleRes = R.string.activity_profile_update_title;
        int descRes = R.string.activity_profile_update_message;

        if (titleRes == 0 || descRes == 0) {
            Log.w(TAG, "Skipping profile update activity: invalid string resources");
            return;
        }

        addActivity(new ActivityItem(
                titleRes,
                descRes,
                System.currentTimeMillis(),
                R.drawable.ic_person,
                ContextCompat.getColor(context, R.color.md_theme_light_onPrimary),
                usernameCache
        ));
    }

    // ---------- NOTIFICATIONS ----------
    private void showSystemNotificationInternal(ActivityItem item) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("notifications_enabled", true)) return;

        String channelId = "alpha_activity_channel";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "User Activities", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(item.getIconRes())
                .setContentTitle(context.getString(item.getTitleResId()))
                .setContentText(context.getString(item.getDescriptionResId()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(item.getColor())
                .setAutoCancel(true);

        int notificationId = (int) (System.currentTimeMillis() % 10000);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build());
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to post notification: " + e.getMessage(), e);
            }
        }
    }

    // ---------- TIME & STATS ----------
    public void setFirstLoginIfNotExists() {
        if (!prefs.contains(KEY_FIRST_LOGIN)) editor.putLong(KEY_FIRST_LOGIN, System.currentTimeMillis()).apply();
    }

    public long getFirstLoginTime() {
        return prefs.getLong(KEY_FIRST_LOGIN, System.currentTimeMillis());
    }

    public int getActiveDays() {
        long firstLogin = getFirstLoginTime();
        return (int) ((System.currentTimeMillis() - firstLogin) / (1000 * 60 * 60 * 24)) + 1;
    }

    public void saveProfileData(String key, String value) throws Exception {
        String currentUsername = getUsername();

        if (!isLoggedIn() || currentUsername.isEmpty()) {
            throw new IllegalStateException("User is not logged in.");
        }

        UserData userData = getUserData(currentUsername);
        if (userData == null) {
            throw new IllegalStateException("User data not found for current session.");
        }

        JSONObject profileJson = storageManager.loadUserProfile(userData.userId);

        if (profileJson == null) {
            profileJson = new JSONObject();
            profileJson.put("username", currentUsername);
            profileJson.put("email", currentUsername + "@example.com");
        }

        File profileFile = storageManager.getUserFile("profile.json");
        Log.d(TAG, "Active user profile file: " + profileFile.getAbsolutePath());

        profileJson.put(key, value);

        storageManager.saveUserProfile(userData.userId, profileJson);

        notifyProfileUpdated();
    }

    public JSONObject loadCurrentProfileJson() {
        String currentUsername = getUsername();

        if (!isLoggedIn() || currentUsername.isEmpty()) {
            return null;
        }

        UserData userData = getUserData(currentUsername);

        if (userData == null) {
            return null;
        }

        return storageManager.loadUserProfile(userData.userId);
    }

}
