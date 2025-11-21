package com.example.movieapp.data.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.movieapp.MainActivity
import com.example.movieapp.R

// helper per gestione notifiche import e reminder liste
object NotificationHelper {

    private const val IMPORT_CHANNEL_ID = "import_channel"
    private const val IMPORT_CHANNEL_NAME = "Import Film"
    private const val IMPORT_CHANNEL_DESCRIPTION = "Notifiche per import film in background"

    private const val REMINDER_CHANNEL_ID = "reminder_channel"
    private const val REMINDER_CHANNEL_NAME = "Reminder Liste"
    private const val REMINDER_CHANNEL_DESCRIPTION = "Notifiche per ricordarti di guardare film pianificati"

    // crea notification channels (necessario per android 8+)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // channel import csv
            val importChannel = NotificationChannel(
                IMPORT_CHANNEL_ID,
                IMPORT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = IMPORT_CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(importChannel)

            // channel reminder liste
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = REMINDER_CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    // crea notifica import per foreground service
    fun createImportNotification(
        context: Context,
        progress: Int,
        status: String
    ): android.app.Notification {

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, IMPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle("Import Film")
            .setContentText(status)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)

        //aggiungi progress bar se progress < 100
        if (progress < 100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    // crea notifica reminder per liste pianificate
    fun createListReminderNotification(
        context: Context,
        listName: String,
        moviesCount: Int,
        targetDate: String
    ): android.app.Notification {

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "lists")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (moviesCount == 1) {
            "Hai $moviesCount film da guardare entro il $targetDate"
        } else {
            "Hai $moviesCount film da guardare entro il $targetDate"
        }

        return NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("📽️ $listName")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
    }

    // mostra notifica reminder (da chiamare dal backend o da worker locale)
    fun showListReminder(
        context: Context,
        listName: String,
        moviesCount: Int,
        targetDate: String
    ) {
        val notification = createListReminderNotification(context, listName, moviesCount, targetDate)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    // crea notifica reminder SMART con messaggio personalizzato
    fun showListReminderSmart(
        context: Context,
        listName: String,
        message: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "lists")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("📬 $listName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}