package com.android.kitsune.ui.notes;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import com.android.kitsune.R;

public class ReminderReceiver extends BroadcastReceiver {

    // ─── Constants ───────────────────────────────────────────────────────────

    private static final String CHANNEL_ID = "note_reminder_channel";


    // ─── Broadcast Handler ───────────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String noteId = intent.getStringExtra("note_id");
        String userId = intent.getStringExtra("user_id");

        if (title == null || title.trim().isEmpty()) {
            title = context.getString(R.string.notification_default_title);
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(context, manager);

        PendingIntent pendingIntent = createPendingIntent(context, noteId, userId);

        int notificationId = (int) System.currentTimeMillis();
        manager.notify(notificationId, buildNotification(context, title, pendingIntent).build());
    }


    // ─── Notification Builder ────────────────────────────────────────────────

    private NotificationCompat.Builder buildNotification(Context context, String title, PendingIntent pendingIntent) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(context.getString(R.string.notification_alert_title))
                .setContentText(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(title))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 300, 250, 300});
    }


    // ─── Channel Management ──────────────────────────────────────────────────

    private void createNotificationChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription(context.getString(R.string.notification_channel_desc));
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }


    // ─── Intent & PendingIntent Creation ─────────────────────────────────────

    private PendingIntent createPendingIntent(Context context, String noteId, String userId) {
        int requestCode;

        if (noteId != null) {
            requestCode = noteId.hashCode();
        } else {
            requestCode = (int) System.currentTimeMillis();
        }

        Intent intent = new Intent(context, EditNoteActivity.class);
        intent.putExtra("note_id", noteId);
        intent.putExtra("user_id", userId);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(EditNoteActivity.class);
        stackBuilder.addNextIntent(intent);

        return stackBuilder.getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}