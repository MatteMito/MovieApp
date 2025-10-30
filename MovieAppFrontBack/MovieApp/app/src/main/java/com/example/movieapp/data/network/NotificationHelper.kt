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

/**
 * helper per gestione notifiche import in background
 */
object NotificationHelper {

    private const val CHANNEL_ID = "import_channel"
    private const val CHANNEL_NAME = "Import Film"
    private const val CHANNEL_DESCRIPTION = "Notifiche per import film in background"

    const val NOTIFICATION_ID_IMPORT = 1001

    /**
     * crea notification channel (necessario per android 8+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * crea notifica progress per import in corso
     */
    fun createProgressNotification(
        context: Context,
        title: String,
        message: String,
        progress: Int,
        maxProgress: Int = 100
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

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(maxProgress, progress, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * crea notifica completamento import
     */
    fun createCompletionNotification(
        context: Context,
        title: String,
        message: String,
        success: Boolean = true
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

        val icon = if (success) R.drawable.ic_home else R.drawable.ic_warning

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * mostra notifica
     */
    fun showNotification(context: Context, notificationId: Int, notification: android.app.Notification) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            //permesso notifiche non concesso
            android.util.Log.w("NotificationHelper", "permesso notifiche negato")
        }
    }

    /**
     * cancella notifica
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId)
    }
}