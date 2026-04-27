package com.android.kitsune.data.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.android.kitsune.R;
import com.android.kitsune.data.local.UserStorageManager;
import com.android.kitsune.data.session.UserSession;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class BackupManager {

    private static final String TAG = "BackupManager";
    private static final String BACKUP_PREF_KEY    = "backup_prefs";
    private static final String KEY_LAST_BACKUP     = "last_backup_time";
    private static final String KEY_BACKUP_ACCOUNT  = "backup_google_account";
    private static final String KEY_AUTO_BACKUP      = "auto_backup_enabled";
    private static final String KEY_BACKUP_FREQUENCY = "backup_frequency";

    private static BackupManager instance;
    private final Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    public interface BackupCallback {
        void onSuccess(String message);
        void onFailure(String error);
        void onProgress(int percent, String status);
    }

    private BackupManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized BackupManager getInstance(Context context) {
        if (instance == null) {
            instance = new BackupManager(context);
        }
        return instance;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(BACKUP_PREF_KEY, Context.MODE_PRIVATE);
    }

    public long getLastBackupTime() {
        return getPrefs().getLong(KEY_LAST_BACKUP, 0);
    }

    private void setLastBackupTime(long time) {
        getPrefs().edit().putLong(KEY_LAST_BACKUP, time).apply();
    }

    public String getBackupAccountEmail() {
        return getPrefs().getString(KEY_BACKUP_ACCOUNT, "");
    }

    public void setBackupAccountEmail(String email) {
        getPrefs().edit().putString(KEY_BACKUP_ACCOUNT, email).apply();
    }

    public boolean isAutoBackupEnabled() {
        return getPrefs().getBoolean(KEY_AUTO_BACKUP, false);
    }

    public void setAutoBackupEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply();
    }

    public String getBackupFrequency() {
        return getPrefs().getString(KEY_BACKUP_FREQUENCY, "weekly");
    }

    public void setBackupFrequency(String frequency) {
        getPrefs().edit().putString(KEY_BACKUP_FREQUENCY, frequency).apply();
    }

    public String getLastBackupDisplayText() {
        long time = getLastBackupTime();
        if (time == 0) return context.getString(R.string.backup_never);

        Date date = new Date(time);
        String formatted = new SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault()).format(date);

        long diff = System.currentTimeMillis() - time;
        long hours = diff / (1000 * 60 * 60);

        if (hours < 1) return context.getString(R.string.backup_just_now);
        if (hours < 24) return context.getString(R.string.backup_hours_ago, hours);
        return formatted;
    }

    private Drive buildDriveService() throws Exception {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            throw new Exception(context.getString(R.string.err_not_logged_in_google));
        }

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singletonList(DriveScopes.DRIVE_APPDATA)
        );
        credential.setSelectedAccount(account.getAccount());

        return new Drive.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential
        ).setApplicationName("KitsuneApp").build();
    }

    public void performBackup(BackupCallback callback) {
        executor.execute(() -> {
            try {
                callback.onProgress(5, context.getString(R.string.status_preparing_backup));

                UserSession session = UserSession.getInstance();
                if (!session.isLoggedIn()) {
                    throw new Exception(context.getString(R.string.err_no_active_session));
                }

                String username = session.getUsername();
                Drive driveService = buildDriveService();

                callback.onProgress(15, context.getString(R.string.status_connecting_drive));

                Map<String, Object> backupPayload = new HashMap<>();

                callback.onProgress(30, context.getString(R.string.status_collecting_chat));
                SharedPreferences chatPrefs = context.getSharedPreferences(
                        "chat_sessions_" + username, Context.MODE_PRIVATE);
                String chatJson = chatPrefs.getString("sessions_list", "[]");
                backupPayload.put("chat_sessions", chatJson);

                Map<String, Object> dailyCountMap = new HashMap<>();
                dailyCountMap.put("count", chatPrefs.getInt("daily_request_count", 0));
                dailyCountMap.put("date", chatPrefs.getString("daily_request_date", ""));
                backupPayload.put("daily_count", gson.toJson(dailyCountMap));

                callback.onProgress(50, context.getString(R.string.status_collecting_profile));
                UserSession.UserData userData = session.getUserData(username);
                if (userData != null) {
                    UserStorageManager storage = UserStorageManager.getInstance(context);
                    JSONObject profile = storage.loadUserProfile(userData.userId);
                    if (profile != null) {
                        backupPayload.put("profile", profile.toString());
                    }
                }

                callback.onProgress(65, context.getString(R.string.status_collecting_notes));
                SharedPreferences notesPrefs = context.getSharedPreferences(
                        "notes_" + (userData != null ? userData.userId : username),
                        Context.MODE_PRIVATE);
                String notesJson = gson.toJson(notesPrefs.getAll());
                backupPayload.put("notes", notesJson);

                backupPayload.put("backup_version", "1");
                backupPayload.put("backup_username", username);
                backupPayload.put("backup_timestamp", String.valueOf(System.currentTimeMillis()));

                String fullJson = gson.toJson(backupPayload);

                callback.onProgress(80, context.getString(R.string.status_uploading_drive));
                uploadToDrive(driveService, fullJson, username);

                long now = System.currentTimeMillis();
                setLastBackupTime(now);

                callback.onProgress(100, context.getString(R.string.status_backup_complete));
                callback.onSuccess(context.getString(R.string.msg_backup_success));

            } catch (Exception e) {
                Log.e(TAG, "Backup failed: " + e.getMessage(), e);
                callback.onFailure(context.getString(R.string.err_backup_failed, e.getMessage()));
            }
        });
    }

    private void uploadToDrive(Drive drive, String jsonContent, String username) throws Exception {
        String fileName = "alpha_backup_" + username + ".json";

        FileList existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='" + fileName + "'")
                .setFields("files(id,name)")
                .execute();

        if (existing.getFiles() != null) {
            for (File old : existing.getFiles()) {
                drive.files().delete(old.getId()).execute();
            }
        }

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList("appDataFolder"));

        byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(jsonBytes);

        com.google.api.client.http.InputStreamContent mediaContent =
                new com.google.api.client.http.InputStreamContent("application/json", stream);
        mediaContent.setLength(jsonBytes.length);

        drive.files().create(fileMetadata, mediaContent)
                .setFields("id,name,size")
                .execute();
    }

    public void performRestore(BackupCallback callback) {
        executor.execute(() -> {
            try {
                callback.onProgress(5, context.getString(R.string.status_connecting_drive));

                UserSession session = UserSession.getInstance();
                if (!session.isLoggedIn()) {
                    throw new Exception(context.getString(R.string.err_no_active_session));
                }

                String username = session.getUsername();
                Drive driveService = buildDriveService();

                callback.onProgress(20, context.getString(R.string.status_locating_backup));

                String fileName = "alpha_backup_" + username + ".json";
                FileList fileList = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name='" + fileName + "'")
                        .setFields("files(id,name,modifiedTime)")
                        .execute();

                if (fileList.getFiles() == null || fileList.getFiles().isEmpty()) {
                    throw new Exception(context.getString(R.string.err_no_backup_found));
                }

                File backupFile = fileList.getFiles().get(0);

                callback.onProgress(40, context.getString(R.string.status_downloading_backup));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                driveService.files().get(backupFile.getId())
                        .executeMediaAndDownloadTo(outputStream);

                String jsonContent = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    jsonContent = outputStream.toString(StandardCharsets.UTF_8);
                }

                Map<String, Object> payload = gson.fromJson(jsonContent,
                        new TypeToken<Map<String, Object>>() {}.getType());

                // FIX: Added null check for payload (Warning Fixed)
                if (payload != null) {
                    callback.onProgress(60, context.getString(R.string.status_restoring_chat));
                    if (payload.containsKey("chat_sessions")) {
                        String chatJson = (String) payload.get("chat_sessions");
                        context.getSharedPreferences("chat_sessions_" + username, Context.MODE_PRIVATE)
                                .edit().putString("sessions_list", chatJson).apply();
                    }

                    callback.onProgress(75, context.getString(R.string.status_restoring_notes));
                    if (payload.containsKey("notes")) {
                        String notesRaw = (String) payload.get("notes");
                        Map<String, Object> notesMap = gson.fromJson(notesRaw,
                                new TypeToken<Map<String, Object>>() {}.getType());

                        UserSession.UserData userData = session.getUserData(username);
                        String prefName = "notes_" + (userData != null ? userData.userId : username);
                        SharedPreferences.Editor notesEditor =
                                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit();

                        if (notesMap != null) {
                            for (Map.Entry<String, Object> entry : notesMap.entrySet()) {
                                notesEditor.putString(entry.getKey(), String.valueOf(entry.getValue()));
                            }
                        }
                        notesEditor.apply();
                    }

                    callback.onProgress(90, context.getString(R.string.status_restoring_profile));
                    if (payload.containsKey("profile")) {
                        String profileStr = (String) payload.get("profile");
                        UserSession.UserData userData = session.getUserData(username);
                        if (userData != null) {
                            JSONObject profileJson = new JSONObject(profileStr);
                            UserStorageManager.getInstance(context)
                                    .saveUserProfile(userData.userId, profileJson);
                            session.refreshCache();
                        }
                    }

                    callback.onProgress(100, context.getString(R.string.status_restore_complete));
                    callback.onSuccess(context.getString(R.string.msg_restore_success));
                } else {
                    throw new Exception("Payload is null");
                }

            } catch (Exception e) {
                Log.e(TAG, "Restore failed: " + e.getMessage(), e);
                callback.onFailure(context.getString(R.string.err_restore_failed, e.getMessage()));
            }
        });
    }

    public void fetchBackupInfo(String username, BackupInfoCallback callback) {
        executor.execute(() -> {
            try {
                Drive driveService = buildDriveService();
                String fileName = "alpha_backup_" + username + ".json";

                FileList fileList = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name='" + fileName + "'")
                        .setFields("files(id,name,size,modifiedTime)")
                        .execute();

                if (fileList.getFiles() == null || fileList.getFiles().isEmpty()) {
                    callback.onResult(null, 0, 0);
                } else {
                    File f = fileList.getFiles().get(0);
                    long sizeBytes = f.getSize() != null ? f.getSize() : 0;
                    long modifiedMs = f.getModifiedTime() != null
                            ? f.getModifiedTime().getValue() : 0;
                    callback.onResult(f.getName(), sizeBytes, modifiedMs);
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchBackupInfo failed: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        });
    }

    public interface BackupInfoCallback {
        void onResult(String fileName, long sizeBytes, long modifiedTimeMs);
        void onError(String error);
    }
}