package com.android.alpha.data.session;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.alpha.R;
import com.android.alpha.data.local.UserStorageManager;
import com.android.alpha.ui.notifications.ActivityItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * Singleton class that manages user session, authentication,
 * activity logs, notifications, and profile data.
 */
public class UserSession {

    // ─── TAG ───────────────────────────────────────────────────────────────────
    private static final String TAG = "UserSession";

    // ─── PREFS KEYS ────────────────────────────────────────────────────────────
    private static final String PREF_NAME       = "user_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USERNAME     = "username";
    private static final String KEY_ACTIVITIES   = "user_activities";
    private static final String KEY_FIRST_LOGIN  = "first_login";
    private static final String KEY_USERS_MAP    = "users_map";
    private static final String KEY_LANGUAGE     = "app_language";

    // ─── SINGLETON ─────────────────────────────────────────────────────────────
    private static UserSession instance;

    // ─── DEPENDENCIES ──────────────────────────────────────────────────────────
    private final Context            context;
    private final SharedPreferences  prefs;
    private final SharedPreferences.Editor editor;
    private final Gson               gson = new Gson();
    private final UserStorageManager storageManager;

    // ─── PER-USER PREFS ────────────────────────────────────────────────────────
    private SharedPreferences       userPrefs;
    private SharedPreferences.Editor userEditor;

    // ─── CACHE ─────────────────────────────────────────────────────────────────
    private boolean isLoggedInCache;
    private String  usernameCache;
    private boolean addedLoginActivity = false;
    private final Map<String, UserData> usersMap = new HashMap<>();

    // ─── LISTENERS ─────────────────────────────────────────────────────────────
    private final List<UserSessionListener>  listeners         = new ArrayList<>();
    private final List<ActivityListener>     activityListeners = new ArrayList<>();
    private UserSessionListener              badgeListener;
    private ActivityClearedListener          activityClearedListener;

    // ══════════════════════════════════════════════════════════════════════════
    // MODEL
    // ══════════════════════════════════════════════════════════════════════════

    /** Holds credentials and unique ID for a registered user. */
    public static class UserData {
        String username;
        public String password;
        public String userId;

        UserData(String username, String password, String userId) {
            this.username = username;
            this.password = password;
            this.userId   = userId;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERFACES
    // ══════════════════════════════════════════════════════════════════════════

    /** Callbacks for profile/badge events. */
    public interface UserSessionListener {
        default void onProfileUpdated() {}
        default void onBadgeCleared()   {}
    }

    /** Callback triggered when a new activity is added. */
    public interface ActivityListener {
        void onNewActivity(ActivityItem item);
    }

    /** Callback triggered when all activities are cleared. */
    public interface ActivityClearedListener {
        void onActivitiesCleared();
    }

    /** Registers a listener for activity-cleared events. */
    public void setActivityClearedListener(ActivityClearedListener listener) {
        this.activityClearedListener = listener;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Private constructor — use {@link #init(Context)} and {@link #getInstance()}. */
    private UserSession(Context context) {
        this.context        = context.getApplicationContext();
        this.storageManager = UserStorageManager.getInstance(context);
        this.prefs          = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.editor         = prefs.edit();

        loadUsersMap();
        isLoggedInCache = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        usernameCache   = prefs.getString(KEY_USERNAME, "");
    }

    /** Initializes the singleton. Must be called before {@link #getInstance()}. */
    public static void init(Context context) {
        if (instance == null) instance = new UserSession(context);
    }

    /** Returns the singleton instance. Throws if {@link #init} was not called. */
    public static UserSession getInstance() {
        if (instance == null) throw new IllegalStateException("UserSession not initialized.");
        return instance;
    }

    /** Returns true if the session has been initialized with a valid logged-in user. */
    public boolean isInitialized() {
        return instance != null && usernameCache != null && !usernameCache.isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATE CACHE & UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns whether a user is currently logged in. */
    public boolean isLoggedIn() { return isLoggedInCache; }

    /** Returns the currently logged-in username from cache. */
    public String getUsername() { return usernameCache; }

    /** Returns whether the login activity has already been added this session. */
    public boolean hasAddedLoginActivity() { return addedLoginActivity; }

    /** Sets the login activity tracking flag. */
    public void setAddedLoginActivity(boolean added) { this.addedLoginActivity = added; }

    /** Reloads login state and username from SharedPreferences and notifies listeners. */
    public void refreshCache() {
        isLoggedInCache = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        usernameCache   = prefs.getString(KEY_USERNAME, "");
        notifyProfileUpdated();
    }

    /** Updates the username in cache and persistent storage, then notifies listeners. */
    public void setUsername(String username) {
        usernameCache = username;
        editor.putString(KEY_USERNAME, username).apply();
        notifyProfileUpdated();
        addProfileUpdateActivity();
    }

    /** Returns user-specific SharedPreferences keyed by username. */
    private SharedPreferences getUserPrefs(String username) {
        return context.getSharedPreferences("session_" + username, Context.MODE_PRIVATE);
    }

    /** Switches active userPrefs/userEditor to the given user. */
    private void switchToUserPrefs(String username) {
        userPrefs  = getUserPrefs(username);
        userEditor = userPrefs.edit();
    }

    private int getAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER MAP MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /** Loads the users map from SharedPreferences into memory. */
    private void loadUsersMap() {
        String json = prefs.getString(KEY_USERS_MAP, "");
        if (!json.isEmpty()) {
            try {
                Map<String, UserData> map = gson.fromJson(json, new TypeToken<Map<String, UserData>>(){}.getType());
                usersMap.clear();
                if (map != null) usersMap.putAll(map);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load users map: " + e.getMessage());
            }
        }
    }

    /** Persists the current users map to SharedPreferences. */
    private void saveUsersMap() {
        editor.putString(KEY_USERS_MAP, gson.toJson(usersMap)).apply();
    }

    /** Returns UserData for the given username (reloads map first). */
    public UserData getUserData(String username) {
        loadUsersMap();
        return usersMap.get(username);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns true if the username does NOT meet requirements (uppercase + digit, 6–20 chars). */
    public static boolean isUsernameInvalid(String username) {
        return !username.matches("^(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d]{6,20}$");
    }

    /** Returns true if the password does NOT meet requirements (letter + digit, min 8 chars). */
    public static boolean isPasswordInvalid(String password) {
        return !password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AUTHENTICATION & SESSION FLOW
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a new user with the given credentials.
     * @return true on success, false if validation fails or username already exists.
     */
    public boolean registerUser(String username, String password) {
        if (isUsernameInvalid(username)) {
            Log.w(TAG, "Username must contain capital letters + numbers, min 6 characters.");
            return false;
        }
        if (isPasswordInvalid(password)) {
            Log.w(TAG, "Password must contain letters + numbers, min 8 characters.");
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

    /**
     * Logs in a user with the given credentials.
     * @return true on success, false if credentials are invalid.
     */
    public boolean login(String username, String password) {
        loadUsersMap();

        UserData user = usersMap.get(username);
        if (user == null || !user.password.equals(password)) {
            Log.w(TAG, "Login failed: " + username);
            return false;
        }

        storageManager.createUserFolder(user.userId, username);
        if (storageManager.loadUserProfile(user.userId) == null) {
            storageManager.saveUserProfile(user.userId, username, username + "@example.com", null);
        }

        storageManager.switchUserContext(user.userId, username);

        JSONObject activeProfile = storageManager.loadActiveUserProfile();
        if (activeProfile != null) Log.d(TAG, "Active profile loaded for " + username + ": " + activeProfile);
        else                       Log.w(TAG, "Active profile is null for " + username);

        storageManager.setCurrentUsername(username);
        Log.d(TAG, "Current username set in storage manager: " + storageManager.getCurrentUsername());

        usernameCache   = username;
        isLoggedInCache = true;
        editor.putString(KEY_USERNAME, username)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();

        switchToUserPrefs(username);
        loadUserCache(username);
        addLoginActivity();
        setFirstLoginIfNotExists();
        notifyProfileUpdated();

        Log.d(TAG, "Login successful → " + username + " UUID:" + user.userId);
        return true;
    }

    /** Logs out the current user, persisting their data and clearing session state. */
    public void logout() {
        try {
            if (usernameCache == null || usernameCache.isEmpty()) return;

            String loggedOutUser = usernameCache;

            switchToUserPrefs(loggedOutUser);
            saveUserCache(loggedOutUser);

            editor.remove(KEY_IS_LOGGED_IN)
                    .remove(KEY_USERNAME)
                    .apply();

            isLoggedInCache = false;
            usernameCache   = "";

            addLogoutActivity();
            notifyProfileUpdated();
            storageManager.clearActiveContext();

            Log.d(TAG, "Logout successful for user: " + loggedOutUser);

        } catch (Exception e) {
            Log.e(TAG, "Logout error: " + e.getMessage(), e);
        }
    }

    /**
     * Resets the password for the given user after verifying the old password.
     * @return true on success, false if old password is wrong or new password is invalid.
     */
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

        if (username.equals(usernameCache)) refreshCache();

        Log.d(TAG, "Password reset successful for user: " + username);
        return true;
    }

    /**
     * Permanently deletes a user account and all associated data.
     * @return true on success, false if user not found.
     */
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
            usernameCache   = "";
            isLoggedInCache = false;
            editor.remove(KEY_IS_LOGGED_IN)
                    .remove(KEY_USERNAME)
                    .remove(KEY_FIRST_LOGIN)
                    .remove(KEY_ACTIVITIES)
                    .apply();
        }

        addActivity(new ActivityItem(
                R.string.activity_account_deleted_title,
                R.string.activity_account_deleted_message,
                System.currentTimeMillis(),
                R.drawable.ic_delete,
                getAttrColor(R.attr.color_error),
                usernameCache
        ));

        notifyProfileUpdated();
        Log.d(TAG, "User " + username + " deleted successfully with UUID: " + userId);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER CACHE AND DATA CLEARING
    // ══════════════════════════════════════════════════════════════════════════

    /** Loads activities from per-user cache into main prefs on login. */
    private void loadUserCache(String username) {
        SharedPreferences cache = context.getSharedPreferences("user_cache_" + username, Context.MODE_PRIVATE);
        editor.putString(KEY_ACTIVITIES, cache.getString(KEY_ACTIVITIES, "")).apply();
    }

    /** Saves current activities into per-user cache on logout. */
    private void saveUserCache(String username) {
        context.getSharedPreferences("user_cache_" + username, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVITIES, prefs.getString(KEY_ACTIVITIES, ""))
                .apply();
    }

    /** Clears profile-specific SharedPreferences for a user. */
    private void clearUserProfilePrefs(String username) {
        context.getSharedPreferences("user_profile_" + username, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    /** Clears user-specific data prefs and profile picture files. */
    private void clearUserSpecificData(String userId) {
        try {
            context.getSharedPreferences("user_data_" + userId, Context.MODE_PRIVATE)
                    .edit().clear().apply();
            context.deleteFile("profile_pic_" + userId + ".jpg");
            context.deleteFile("profile_pic_" + userId + ".png");
            Log.d(TAG, "User specific data cleared for UUID: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear user data: " + e.getMessage());
        }
    }

    /** Removes all activity entries belonging to the given userId. */
    private void clearActivitiesForUser(String userId) {
        List<ActivityItem> activities = getActivities();
        List<ActivityItem> filtered   = new ArrayList<>();
        for (ActivityItem a : activities) {
            if (a.getUserId() == null || !a.getUserId().equals(userId)) filtered.add(a);
        }
        saveActivities(filtered);
    }

    /** Clears per-user cache SharedPreferences and any matching cache files. */
    private void clearUserCache(String username) {
        try {
            context.getSharedPreferences("user_cache_" + username, Context.MODE_PRIVATE)
                    .edit().clear().apply();

            for (String file : context.fileList()) {
                if (file.startsWith("cache_" + username)
                        || file.startsWith("cookie_" + username)
                        || file.contains(username)) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(context.getFilesDir(), file).delete();
                }
            }
            Log.d(TAG, "Cache and cookies for user " + username + " deleted");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete user cache/cookies " + username + ": " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ACTIVITY LOG
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns the current list of activities from the appropriate prefs source. */
    public List<ActivityItem> getActivities() {
        SharedPreferences src = (isLoggedInCache && userPrefs != null) ? userPrefs : prefs;
        String json = src.getString(KEY_ACTIVITIES, "");
        if (json.isEmpty()) return new ArrayList<>();
        return gson.fromJson(json, new TypeToken<List<ActivityItem>>(){}.getType());
    }

    /** Persists the given list of activities to the appropriate prefs target. */
    public void saveActivities(List<ActivityItem> activities) {
        SharedPreferences.Editor target = (isLoggedInCache && userEditor != null) ? userEditor : editor;
        target.putString(KEY_ACTIVITIES, gson.toJson(activities)).apply();
    }

    /** Clears all activities from both main prefs and per-user prefs, then notifies listener. */
    public void clearActivities() {
        editor.remove(KEY_ACTIVITIES).apply();
        if (userEditor != null) userEditor.remove(KEY_ACTIVITIES).apply();
        if (activityClearedListener != null) activityClearedListener.onActivitiesCleared();
    }

    /**
     * Adds a new ActivityItem to the log (capped at 10 items within the last 7 days).
     * Also fires a system notification unless it's a login/logout event.
     */
    public void addActivity(ActivityItem item) {
        if (item == null) return;
        if (item.getTitleResId() == 0 || item.getDescriptionResId() == 0) {
            Log.w(TAG, "Skipping activity with invalid string resource IDs: " + item);
            return;
        }

        long now      = System.currentTimeMillis();
        long sevenDays = 7L * 24 * 60 * 60 * 1000;

        List<ActivityItem> recent = new ArrayList<>();
        for (ActivityItem a : getActivities()) {
            if (now - a.getTimestamp() <= sevenDays) recent.add(a);
        }

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

    /** Removes activity entries older than 7 days. */
    public void removeOldActivities() {
        List<ActivityItem> activities = getActivities();
        if (activities.isEmpty()) return;

        long now      = System.currentTimeMillis();
        long sevenDays = 7L * 24 * 60 * 60 * 1000;

        List<ActivityItem> recent = new ArrayList<>();
        for (ActivityItem item : activities) {
            if (now - item.getTimestamp() <= sevenDays) recent.add(item);
        }
        saveActivities(recent);
    }

    /** Adds a login activity entry for the current user. */
    public void addLoginActivity() {
        int titleRes = R.string.activity_login_title;
        int descRes  = R.string.activity_login_message;
        if (titleRes == 0 || descRes == 0) {
            Log.w(TAG, "Skipping login activity: invalid string resources");
            return;
        }
        addActivity(new ActivityItem(
                titleRes, descRes,
                System.currentTimeMillis(),
                R.drawable.ic_login,
                getAttrColor(R.attr.color_green),
                usernameCache
        ));
    }

    /** Adds a logout activity entry for the current user. */
    public void addLogoutActivity() {
        int titleRes = R.string.activity_logout_title;
        int descRes  = R.string.activity_logout_message;
        if (titleRes == 0 || descRes == 0) {
            Log.w(TAG, "Skipping logout activity: invalid string resources");
            return;
        }
        addActivity(new ActivityItem(
                titleRes, descRes,
                System.currentTimeMillis(),
                R.drawable.ic_logout,
                getAttrColor(R.attr.color_error),
                usernameCache
        ));
    }

    /** Adds a profile update activity entry for the current user. */
    public void addProfileUpdateActivity() {
        int titleRes = R.string.activity_profile_update_title;
        int descRes  = R.string.activity_profile_update_message;
        if (titleRes == 0 || descRes == 0) {
            Log.w(TAG, "Skipping profile update activity: invalid string resources");
            return;
        }
        addActivity(new ActivityItem(
                titleRes, descRes,
                System.currentTimeMillis(),
                R.drawable.ic_person,
                getAttrColor(R.attr.color_blue),
                usernameCache
        ));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /** Posts a system notification for the given activity item if notifications are enabled. */
    private void showSystemNotificationInternal(ActivityItem item) {
        SharedPreferences settings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!settings.getBoolean("notifications_enabled", true)) return;

        String channelId = "alpha_activity_channel";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    context.getString(R.string.notification_channel_user_activities),
                    NotificationManager.IMPORTANCE_HIGH
            );
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

        boolean canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        if (canNotify) {
            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build());
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to post notification: " + e.getMessage(), e);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TIME & STATS
    // ══════════════════════════════════════════════════════════════════════════

    /** Stores the first login timestamp if it hasn't been set yet. */
    public void setFirstLoginIfNotExists() {
        if (!prefs.contains(KEY_FIRST_LOGIN)) {
            editor.putLong(KEY_FIRST_LOGIN, System.currentTimeMillis()).apply();
        }
    }

    /** Returns the timestamp of the user's first login. */
    public long getFirstLoginTime() {
        return prefs.getLong(KEY_FIRST_LOGIN, System.currentTimeMillis());
    }

    /** Returns the number of days since the first login (minimum 1). */
    public int getActiveDays() {
        long firstLogin = getFirstLoginTime();
        return (int) ((System.currentTimeMillis() - firstLogin) / (1000 * 60 * 60 * 24)) + 1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PROFILE DATA MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Saves a single key-value pair to the current user's profile JSON.
     * @throws IllegalStateException if no user is logged in or user data is missing.
     */
    public void saveProfileData(String key, String value) throws Exception {
        String currentUsername = getUsername();
        if (!isLoggedIn() || currentUsername.isEmpty()) throw new IllegalStateException("User is not logged in.");

        UserData userData = getUserData(currentUsername);
        if (userData == null) throw new IllegalStateException("User data not found for current session.");

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

    /**
     * Loads and returns the current user's profile as a JSONObject.
     * @return the profile JSON, or null if not logged in or not found.
     */
    public JSONObject loadCurrentProfileJson() {
        String currentUsername = getUsername();
        if (!isLoggedIn() || currentUsername.isEmpty()) return null;

        UserData userData = getUserData(currentUsername);
        if (userData == null) return null;

        return storageManager.loadUserProfile(userData.userId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LANGUAGE
    // ══════════════════════════════════════════════════════════════════════════

    /** Persists the preferred app language code (e.g. "en", "id"). */
    public void setLanguage(String langCode) {
        editor.putString(KEY_LANGUAGE, langCode).apply();
    }

    /** Returns the current app language code (default: "en"). */
    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "en");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LISTENER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /** Sets the listener that receives badge-cleared events. */
    public void setBadgeListener(UserSessionListener listener) { badgeListener = listener; }

    /** Notifies the badge listener that the badge has been cleared. */
    public void notifyBadgeCleared() { if (badgeListener != null) badgeListener.onBadgeCleared(); }

    /** Adds a UserSessionListener (skips duplicates). */
    public void addListener(UserSessionListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    /** Removes all UserSessionListeners. */
    public void removeListener() { listeners.clear(); }

    /** Adds an ActivityListener (skips duplicates). */
    public void addActivityListener(ActivityListener listener) {
        if (!activityListeners.contains(listener)) activityListeners.add(listener);
    }

    /** Removes all ActivityListeners. */
    public void removeActivityListener() { activityListeners.clear(); }

    /** Notifies all UserSessionListeners that the profile has been updated. */
    public void notifyProfileUpdated() {
        for (UserSessionListener l : listeners) l.onProfileUpdated();
    }
}