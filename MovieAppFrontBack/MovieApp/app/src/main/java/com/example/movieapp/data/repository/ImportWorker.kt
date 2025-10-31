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
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.io.File

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

            val csvTypeEnum = when (csvType) {
                "IMDB_WATCHED" -> CsvType.IMDB_WATCHED
                "IMDB_WATCHLIST" -> CsvType.IMDB_WATCHLIST
                "LETTERBOXD_WATCHED" -> CsvType.LETTERBOXD_WATCHED
                "LETTERBOXD_WATCHLIST" -> CsvType.LETTERBOXD_WATCHLIST
                else -> return@withContext Result.failure(workDataOf(KEY_MESSAGE to "tipo csv non valido"))
            }

            val movies = csvProcessor.processCsv(filePath, csvTypeEnum)
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

            val uploadSuccess = uploadMovies(watchlist, watched)

            if (!uploadSuccess) {
                Log.e(TAG, "upload fallito")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "errore durante l'upload"))
            }

            setForeground(createForegroundInfo(40, "enrichment in corso..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 40))

            //step 5: monitora enrichment via websocket
            //fix: timeout dinamico basato sul numero di film (1 minuto per 100 film, min 15 min, max 60 min)
            val enrichmentSuccess = monitorEnrichment(movies.size)

            //step 6: aspetta un po' per sicurezza che il backend finisca
            delay(3000)

            //step 7: refresh finale
            setForeground(createForegroundInfo(95, "aggiornamento database..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 95))

            val repository = MovieRepository.getInstance(context)
            val refreshSuccess = repository.refreshFromBackend()

            //step 8: completion
            setForeground(createForegroundInfo(100, "completato!"))
            setProgressAsync(workDataOf(KEY_PROGRESS to 100))

            val finalMovies = repository.movies.value ?: emptyList()
            Log.d(TAG, "import completato: ${finalMovies.size} film totali")

            //cleanup
            file.delete()

            //rimuovi notifica
            delay(1000)
            NotificationHelper.cancelNotification(context, NotificationHelper.NOTIFICATION_ID_IMPORT)

            Result.success(workDataOf(
                KEY_STATUS to "success",
                KEY_MESSAGE to "import completato",
                KEY_TOTAL_MOVIES to finalMovies.size
            ))

        } catch (e: Exception) {
            Log.e(TAG, "errore import worker: ${e.message}", e)

            //rimuovi notifica in caso di errore
            NotificationHelper.cancelNotification(context, NotificationHelper.NOTIFICATION_ID_IMPORT)

            Result.failure(workDataOf(
                KEY_STATUS to "error",
                KEY_MESSAGE to e.message
            ))
        }
    }

    /**
     * upload film al backend
     */
    private suspend fun uploadMovies(watchlist: List<Movie>, watched: List<Movie>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = ApiService.batchUpload(watchlist, watched)
                result.isSuccess
            } catch (e: Exception) {
                Log.e(TAG, "errore upload: ${e.message}", e)
                false
            }
        }
    }

    /**
     * fix: monitora enrichment con timeout dinamico
     */
    private suspend fun monitorEnrichment(totalMovies: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var lastProgress = 40
                var completed = false
                val startTime = System.currentTimeMillis()

                //fix: timeout dinamico - 1 minuto per 100 film, minimo 15 min, massimo 60 min
                val baseTimeout = 15 * 60 * 1000L //15 minuti base
                val additionalTimeout = (totalMovies / 100) * 60 * 1000L //1 min per 100 film
                val timeout = minOf(baseTimeout + additionalTimeout, 60 * 60 * 1000L) //max 60 min

                Log.d(TAG, "timeout enrichment impostato a ${timeout / 60000} minuti per $totalMovies film")

                //osserva websocket updates
                coroutineScope {
                    val job = launch {
                        webSocketService.enrichmentUpdates.collect { update ->
                            if (update != null) {
                                val progress = 40 + ((update.processed.toFloat() / update.total) * 55).toInt()

                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    val message = "enrichment ${update.processed}/${update.total}: ${update.currentMovie}"

                                    setForeground(createForegroundInfo(progress, message))
                                    setProgressAsync(workDataOf(KEY_PROGRESS to progress))

                                    Log.d(TAG, message)
                                }

                                if (update.processed >= update.total) {
                                    completed = true
                                }
                            }
                        }
                    }

                    //fix: attendi completamento o timeout con controllo isActive
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