package com.example.movieapp.data.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// data class per deserializzare risposta backend
data class NotificationCheck(
    val listId: String,
    val userId: String,
    val listName: String,
    val userEmail: String,
    val frequency: String,
    val targetDate: String,
    val moviesCount: Int,
    val expectedWatched: Int,
    val remainingDays: Int,
    val recommendedPace: String,
    val shouldNotify: Boolean,
    val reason: String
)

// worker che controlla notifiche liste pianificate
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "NotificationWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== controllo notifiche liste ===")

            // IMPORTANTE: inizializza ApiService nel worker
            ApiService.initialize(applicationContext)

            // chiama endpoint backend per controllare notifiche
            val response = ApiService.apiInterface.checkNotifications()

            Log.d(TAG, "response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val notifications = response.body()!!

                Log.d(TAG, "ricevute ${notifications.size} notifiche da controllare")

                // filtra solo quelle da inviare
                val toNotify = notifications.filter { it.shouldNotify }

                Log.d(TAG, "${toNotify.size} notifiche da mostrare")

                // mostra notifica per ogni lista
                toNotify.forEach { notification ->
                    Log.d(TAG, "processando: ${notification.listName}, shouldNotify=${notification.shouldNotify}")
                    showListNotification(notification)
                }

                Log.d(TAG, "=== controllo completato con successo ===")
                Result.success()

            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "errore chiamata api: ${response.code()}")
                Log.e(TAG, "error body: $errorBody")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "errore worker", e)
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun showListNotification(notification: NotificationCheck) {
        try {
            Log.d(TAG, "mostro notifica per: ${notification.listName}")

            // FORMATTA DATA CORRETTAMENTE: da "2025-11-30T00:00:00.000Z" a "30/11/2025"
            val targetDateStr = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = inputFormat.parse(notification.targetDate.substringBefore('T'))
                date?.let { outputFormat.format(it) } ?: notification.targetDate.substringBefore('T')
            } catch (e: Exception) {
                notification.targetDate.substringBefore('T')
            }

            // MESSAGGIO INTELLIGENTE basato sul tempo
            val message = when {
                // deadline passata
                notification.remainingDays <= 0 -> {
                    "⏰ Scadenza raggiunta! Hai ${notification.moviesCount} film da recuperare!"
                }

                // urgente (meno di 7 giorni)
                notification.remainingDays <= 7 -> {
                    "🔥 Ultimi ${notification.remainingDays} giorni! " +
                            "Dovresti aver visto circa ${notification.expectedWatched}/${notification.moviesCount} film"
                }

                // in tempo (più di 7 giorni)
                else -> {
                    "📅 Mancano ${notification.remainingDays} giorni al $targetDateStr\n\n" +
                            "Dovresti aver visto circa ${notification.expectedWatched}/${notification.moviesCount} film\n" +
                            "Ritmo: ${notification.recommendedPace} 🎬"
                }
            }

            // mostra notifica usando NotificationHelper
            NotificationHelper.showListReminderSmart(
                context = applicationContext,
                listName = notification.listName,
                message = message
            )

            Log.d(TAG, "✅ notifica mostrata: $message")

        } catch (e: Exception) {
            Log.e(TAG, "❌ errore mostra notifica per ${notification.listName}", e)
            e.printStackTrace()
        }
    }
}