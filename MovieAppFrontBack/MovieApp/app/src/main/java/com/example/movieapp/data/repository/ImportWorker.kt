package com.example.movieapp.data.repository

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.WebSocketService
import com.example.movieapp.data.parser.CsvProcessor
import com.example.movieapp.data.parser.CsvParseResult
import com.example.movieapp.data.network.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * worker per import film in background
 * fix: completa automaticamente quando processed = total (es. 606/606 = 100%)
 */
class ImportWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "ImportWorker"
    private val csvProcessor = CsvProcessor()
    private val webSocketService = WebSocketService.getInstance()

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_CSV_TYPE = "csv_type"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_MESSAGE = "message"
        const val KEY_TOTAL_MOVIES = "total_movies"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== import worker started ===")

            val filePath = inputData.getString(KEY_FILE_PATH)
            val csvType = inputData.getString(KEY_CSV_TYPE)

            if (filePath == null || csvType == null) {
                Log.e(TAG, "parametri mancanti")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "parametri mancanti"))
            }

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "file non trovato: $filePath")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "file non trovato"))
            }

            //step 1: parsing csv (0-10%)
            Log.d(TAG, "--- STEP 1: PARSING (0-10%) ---")
            setForeground(createForegroundInfo(5, "lettura file..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 5))

            val parseResult: CsvParseResult = when (csvType) {
                "IMDB_WATCHED" -> csvProcessor.parseImdbWatchedCsv(FileInputStream(file))
                "IMDB_WATCHLIST" -> csvProcessor.parseImdbWatchlistCsv(FileInputStream(file))
                "LETTERBOXD_WATCHED" -> csvProcessor.parseLetterboxdWatchedCsv(FileInputStream(file))
                "LETTERBOXD_WATCHLIST" -> csvProcessor.parseLetterboxdWatchlistCsv(FileInputStream(file))
                else -> {
                    Log.e(TAG, "tipo csv non valido: $csvType")
                    return@withContext Result.failure(workDataOf(KEY_MESSAGE to "tipo csv non valido"))
                }
            }

            val movies = parseResult.movies
            Log.d(TAG, "parsed ${movies.size} film")

            if (movies.isEmpty()) {
                Log.w(TAG, "nessun film nel csv")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "file vuoto"))
            }

            setProgressAsync(workDataOf(KEY_PROGRESS to 10))
            delay(200)

            //step 2: connetti websocket (10-20%)
            Log.d(TAG, "--- STEP 2: WEBSOCKET (10-20%) ---")
            setForeground(createForegroundInfo(15, "connessione..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 15))

            try {
                webSocketService.connect()
                delay(1000)
                Log.d(TAG, "websocket connesso: ${webSocketService.isConnected()}")
            } catch (e: Exception) {
                Log.w(TAG, "websocket error: ${e.message}")
            }

            setProgressAsync(workDataOf(KEY_PROGRESS to 20))
            delay(200)

            //step 3: separa watched/watchlist (20-30%)
            Log.d(TAG, "--- STEP 3: SEPARAZIONE (20-30%) ---")
            setForeground(createForegroundInfo(25, "preparazione..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 25))

            val watched = movies.filter { it.isWatched }
            val watchlist = movies.filter { !it.isWatched }

            Log.d(TAG, "watched: ${watched.size}, watchlist: ${watchlist.size}")

            setProgressAsync(workDataOf(KEY_PROGRESS to 30))
            delay(200)

            //step 4: upload (30-40%)
            Log.d(TAG, "--- STEP 4: UPLOAD (30-40%) ---")
            setForeground(createForegroundInfo(35, "upload..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 35))

            val uploadResult = ApiService.batchUpload(watchlist, watched)

            if (uploadResult.isFailure) {
                val error = uploadResult.exceptionOrNull()?.message ?: "errore upload"
                Log.e(TAG, "upload failed: $error")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to error))
            }

            val batchResponse = uploadResult.getOrNull()
            if (batchResponse == null) {
                Log.e(TAG, "risposta vuota")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "risposta backend vuota"))
            }

            Log.d(TAG, "upload ok, session: ${batchResponse.sessionId}")
            setProgressAsync(workDataOf(KEY_PROGRESS to 40))
            delay(200)

            //step 5: monitora enrichment (40-95%)
            //fix: rileva automaticamente quando processed = total
            Log.d(TAG, "--- STEP 5: ENRICHMENT (40-95%) ---")
            setForeground(createForegroundInfo(40, "enrichment..."))

            val enrichmentCompleted = monitorEnrichmentProgress(batchResponse.sessionId, movies.size)

            Log.d(TAG, "enrichment completato: $enrichmentCompleted")

            //step 6: finalizzazione (95-100%)
            Log.d(TAG, "--- STEP 6: FINALIZZAZIONE (95-100%) ---")
            setProgressAsync(workDataOf(KEY_PROGRESS to 95))
            delay(200)

            setForeground(createForegroundInfo(98, "finalizzazione..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 98))
            delay(300)

            //100%
            setForeground(createForegroundInfo(100, "completato!"))
            setProgressAsync(workDataOf(KEY_PROGRESS to 100))
            delay(500)

            showSuccessNotification(movies.size, batchResponse.summary.totalEnriched)

            Log.d(TAG, "=== import SUCCESS ===")

            return@withContext Result.success(
                workDataOf(
                    KEY_MESSAGE to "import completato: ${movies.size} film",
                    KEY_TOTAL_MOVIES to movies.size,
                    KEY_PROGRESS to 100
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "=== ERRORE ===", e)
            e.printStackTrace()
            showFailureNotification("Errore: ${e.message}")

            return@withContext Result.failure(
                workDataOf(
                    KEY_MESSAGE to "errore: ${e.message}",
                    KEY_PROGRESS to 0
                )
            )
        }
    }

    /**
     * monitora enrichment via websocket
     * fix: quando processed = total (606/606) completa automaticamente
     */
    private suspend fun monitorEnrichmentProgress(sessionId: String, totalExpected: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "monitor enrichment session: $sessionId, total atteso: $totalExpected")

                val timeout = 10 * 60 * 1000L //10 minuti max
                val startTime = System.currentTimeMillis()
                var lastProgress = 40
                var completed = false

                while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
                    val update = webSocketService.enrichmentUpdates.value

                    if (update != null && update.sessionId == sessionId) {
                        //calcola progress worker (40-95%)
                        val workerProgress = 40 + ((update.percentage * 55) / 100)

                        if (workerProgress > lastProgress) {
                            setProgressAsync(workDataOf(KEY_PROGRESS to workerProgress))
                            setForeground(createForegroundInfo(workerProgress, "${update.processed}/${update.total}"))
                            lastProgress = workerProgress
                            Log.d(TAG, "enrichment: ${update.processed}/${update.total} (${update.percentage}%) -> $workerProgress%")
                        }

                        //fix: rileva automaticamente quando processed = total
                        if (update.processed >= update.total && update.total > 0) {
                            completed = true
                            setProgressAsync(workDataOf(KEY_PROGRESS to 95))
                            Log.d(TAG, "✅ COMPLETATO AUTOMATICAMENTE: ${update.processed}/${update.total} = 100%")
                            break
                        }

                        //oppure se riceve evento "completed"
                        if (update.type == "completed") {
                            completed = true
                            setProgressAsync(workDataOf(KEY_PROGRESS to 95))
                            Log.d(TAG, "✅ COMPLETATO VIA EVENTO: completed")
                            break
                        }
                    }

                    delay(500)
                }

                if (!completed) {
                    Log.w(TAG, "⚠️ timeout enrichment dopo ${(System.currentTimeMillis() - startTime) / 1000}s")
                    setProgressAsync(workDataOf(KEY_PROGRESS to 95))
                }

                true

            } catch (e: Exception) {
                Log.e(TAG, "errore monitor: ${e.message}", e)
                setProgressAsync(workDataOf(KEY_PROGRESS to 95))
                true
            }
        }
    }

    private fun showSuccessNotification(totalMovies: Int, enrichedMovies: Int) {
        try {
            val notification = NotificationHelper.createCompletionNotification(
                context = context,
                title = "Import Completato!",
                message = "$totalMovies film importati, $enrichedMovies arricchiti",
                success = true
            )

            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            notificationManager.notify(2, notification)
            Log.d(TAG, "notifica successo mostrata")
        } catch (e: Exception) {
            Log.e(TAG, "errore notifica", e)
        }
    }

    private fun showFailureNotification(message: String) {
        try {
            val notification = NotificationHelper.createCompletionNotification(
                context = context,
                title = "Import Fallito",
                message = message,
                success = false
            )

            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            notificationManager.notify(3, notification)
            Log.d(TAG, "notifica fallimento mostrata")
        } catch (e: Exception) {
            Log.e(TAG, "errore notifica", e)
        }
    }

    private fun createForegroundInfo(progress: Int, status: String): ForegroundInfo {
        val notification = NotificationHelper.createImportNotification(
            context = context,
            progress = progress,
            status = status
        )

        return ForegroundInfo(1, notification)
    }
}