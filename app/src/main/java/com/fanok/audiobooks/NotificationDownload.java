package com.fanok.audiobooks;

import static com.fanok.audiobooks.service.ExoDownloadService.CHANNEL_ID;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.activity.ClearSavedActivity;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;

public class NotificationDownload {

    @OptIn(markerClass = UnstableApi.class)
    public static Notification getNotification(Context context, @Nullable String id, int progress, boolean stateDownload) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
                if (ch == null) {
                    ch = new NotificationChannel(CHANNEL_ID, "Загрузки книг", NotificationManager.IMPORTANCE_LOW);
                    ch.setDescription("Прогресс загрузки аудиокниг");
                    ch.setShowBadge(true);
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                    nm.createNotificationChannel(ch);
                } else {
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                }
            }
        }

        Intent intent = new Intent(context, DownloadActionReceiver.class).setAction("PAUSE_ALL");
        intent.putExtra("id", id);
        PendingIntent pausePi = PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent cancelPi = PendingIntent.getBroadcast(
                context, 2, new Intent(context, DownloadActionReceiver.class)
                        .setAction("CANCEL_ALL"),
                PendingIntent.FLAG_IMMUTABLE);

        PendingIntent resumePi = PendingIntent.getBroadcast(
                context, 3, new Intent(context, DownloadActionReceiver.class).setAction("RESUME_ALL"),
                PendingIntent.FLAG_IMMUTABLE);

        String title = stateDownload ? "Скачивание аудиокниги" : "Загрузка приостановлена";
        String contentText = progress > 0 ? "Прогресс: " + progress + "%" : "Загрузка файла...";

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setOnlyAlertOnce(true)
                .setOngoing(stateDownload)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(new long[]{0L})
                .setContentTitle(title)
                .setContentText(contentText)
                .setProgress(100, Math.max(0, progress), progress <= 0)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ClearSavedActivity.class), PendingIntent.FLAG_IMMUTABLE));

        if (stateDownload) {
            b.addAction(0, "Пауза", pausePi);
        } else {
            b.addAction(0, "Возобновить", resumePi);
        }
        b.addAction(0, "Отмена", cancelPi);

        return b.build();
    }
}
