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
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileInputStream

/**
 * worker per import film in background
 * fix: gestisce file grandi e timeout esteso
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

            //crea notification channel
            NotificationHelper.createNotificationChannel(context)

            //mostra notifica foreground
            setForeground(createForegroundInfo(0, "preparazione import..."))

            //recupera parametri
            val filePath = inputData.getString(KEY_FILE_PATH)
                ?: return@withContext Result.failure(workDataOf(KEY_MESSAGE to "file path mancante"))

            val csvType = inputData.getString(KEY_CSV_TYPE)
                ?: return@withContext Result.failure(workDataOf(KEY_MESSAGE to "csv type mancante"))

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "file non trovato: $filePath")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "file non trovato"))
            }

            //step 1: parsing csv
            setForeground(createForegroundInfo(5, "lettura file csv..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 5))

            //fix: chiama il metodo corretto in base al tipo
            val parseResult: CsvParseResult = when (csvType) {
                "IMDB_WATCHED" -> csvProcessor.parseImdbWatchedCsv(FileInputStream(file))
                "IMDB_WATCHLIST" -> csvProcessor.parseImdbWatchlistCsv(FileInputStream(file))
                "LETTERBOXD_WATCHED" -> csvProcessor.parseLetterboxdWatchedCsv(FileInputStream(file))
                "LETTERBOXD_WATCHLIST" -> csvProcessor.parseLetterboxdWatchlistCsv(FileInputStream(file))
                else -> return@withContext Result.failure(workDataOf(KEY_MESSAGE to "tipo csv non valido"))
            }

            val movies = parseResult.movies

            Log.d(TAG, "parsed ${movies.size} film dal csv")

            if (movies.isEmpty()) {
                Log.w(TAG, "nessun film trovato nel csv")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "nessun film trovato"))
            }

            //step 2: connetti websocket per progress
            setForeground(createForegroundInfo(15, "connessione al server..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 15))

            try {
                webSocketService.connect()
                delay(2000)
                Log.d(TAG, "websocket connesso")
            } catch (e: Exception) {
                Log.w(TAG, "websocket non disponibile: ${e.message}")
            }

            setForeground(createForegroundInfo(25, "preparazione upload..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 25))

            //step 3: separa watchlist e watched
            val watchlist = movies.filter { !it.isWatched }
            val watched = movies.filter { it.isWatched }

            Log.d(TAG, "watchlist: ${watchlist.size}, watched: ${watched.size}")

            //step 4: upload al backend
            setForeground(createForegroundInfo(30, "invio film al server..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 30))

            val uploadResult = ApiService.batchUpload(watchlist, watched)

            if (uploadResult.isFailure) {
                Log.e(TAG, "errore upload: ${uploadResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(
                    workDataOf(KEY_MESSAGE to "errore upload: ${uploadResult.exceptionOrNull()?.message}")
                )
            }

            val batchResponse = uploadResult.getOrNull()
            if (batchResponse == null) {
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "risposta backend vuota"))
            }

            Log.d(TAG, "upload completato, session: ${batchResponse.sessionId}")
            setForeground(createForegroundInfo(40, "enrichment in corso..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 40))

            //step 5: monitora enrichment via websocket
            val enrichmentCompleted = monitorEnrichmentProgress(batchResponse.sessionId)

            if (enrichmentCompleted) {
                setForeground(createForegroundInfo(100, "import completato!"))
                setProgressAsync(workDataOf(KEY_PROGRESS to 100))

                delay(1000)

                Log.d(TAG, "=== import worker completed ===")
                return@withContext Result.success(
                    workDataOf(
                        KEY_MESSAGE to "import completato: ${movies.size} film",
                        KEY_TOTAL_MOVIES to movies.size
                    )
                )
            } else {
                Log.w(TAG, "timeout enrichment ma backend continua")
                return@withContext Result.success(
                    workDataOf(
                        KEY_MESSAGE to "import avviato: ${movies.size} film",
                        KEY_TOTAL_MOVIES to movies.size
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "errore worker: ${e.message}", e)
            return@withContext Result.failure(
                workDataOf(KEY_MESSAGE to "errore: ${e.message}")
            )
        }
    }

    /**
     * monitora progress enrichment via websocket
     */
    private suspend fun monitorEnrichmentProgress(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var completed = false
                val timeout = 10 * 60 * 1000L
                val startTime = System.currentTimeMillis()

                coroutineScope {
                    val job: Job = launch {
                        webSocketService.enrichmentUpdates.collect { update ->
                            //fix: controlla se update non è null
                            if (update != null && update.sessionId == sessionId) {
                                val progress = ((update.processed.toFloat() / update.total) * 55 + 40).toInt()
                                setProgressAsync(workDataOf(KEY_PROGRESS to progress))

                                setForeground(
                                    createForegroundInfo(
                                        progress,
                                        "enrichment: ${update.processed}/${update.total}"
                                    )
                                )

                                if (update.processed >= update.total) {
                                    completed = true
                                }
                            }
                        }
                    }

                    //aspetta completamento o timeout con controllo isActive
                    while (!completed &&
                        (System.currentTimeMillis() - startTime) < timeout &&
                        isActive) {
                        delay(500)
                    }

                    job.cancel()
                }

                //fix: se timeout raggiunto, il backend continua in background
                if (!completed) {
                    Log.w(TAG, "timeout raggiunto ma enrichment continua in background")
                    Log.w(TAG, "l'import e completato lato app, il backend continua l'elaborazione")
                    //non e un errore, ritorna success per completare il worker
                    return@withContext true
                }

                completed

            } catch (e: Exception) {
                Log.e(TAG, "errore monitoring enrichment: ${e.message}", e)
                //procedi comunque - il backend sta lavorando
                true
            }
        }
    }

    /**
     * crea foreground info per notifica persistente
     */
    private fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        val notification = NotificationHelper.createProgressNotification(
            context = context,
            title = "Import film in corso",
            message = message,
            progress = progress,
            maxProgress = 100
        )

        return ForegroundInfo(NotificationHelper.NOTIFICATION_ID_IMPORT, notification)
    }
}

/**
 * enum tipi csv supportati
 */
enum class CsvType {
    IMDB_WATCHED,
    IMDB_WATCHLIST,
    LETTERBOXD_WATCHED,
    LETTERBOXD_WATCHLIST
}