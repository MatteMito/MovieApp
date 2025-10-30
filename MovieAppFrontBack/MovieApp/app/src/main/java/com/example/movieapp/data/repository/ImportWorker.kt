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
import java.io.File

/**
 * worker per import film in background con notifiche
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
            val csvTypeString = inputData.getString(KEY_CSV_TYPE)

            if (filePath == null || csvTypeString == null) {
                Log.e(TAG, "parametri mancanti")
                return@withContext Result.failure()
            }

            val csvType = CsvType.valueOf(csvTypeString)
            val file = File(filePath)

            if (!file.exists()) {
                Log.e(TAG, "file non trovato: $filePath")
                return@withContext Result.failure()
            }

            //step 1: parsing csv
            setForeground(createForegroundInfo(5, "lettura file csv..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 5))

            val movies = file.inputStream().use { stream ->
                when (csvType) {
                    CsvType.IMDB_WATCHED -> csvProcessor.parseImdbWatchedCsv(stream).movies
                    CsvType.IMDB_WATCHLIST -> csvProcessor.parseImdbWatchlistCsv(stream).movies
                    CsvType.LETTERBOXD_WATCHED -> csvProcessor.parseLetterboxdWatchedCsv(stream).movies
                    CsvType.LETTERBOXD_WATCHLIST -> csvProcessor.parseLetterboxdWatchlistCsv(stream).movies
                }
            }

            if (movies.isEmpty()) {
                Log.w(TAG, "nessun film trovato nel file")
                showCompletionNotification("import completato", "nessun film trovato", false)
                return@withContext Result.success()
            }

            Log.d(TAG, "parsing completato: ${movies.size} film")
            setForeground(createForegroundInfo(15, "trovati ${movies.size} film"))
            setProgressAsync(workDataOf(
                KEY_PROGRESS to 15,
                KEY_TOTAL_MOVIES to movies.size
            ))

            //step 2: connetti websocket
            setForeground(createForegroundInfo(20, "connessione al server..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 20))

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
                showCompletionNotification("import fallito", "errore durante l'upload", false)
                return@withContext Result.failure()
            }

            setForeground(createForegroundInfo(40, "enrichment in corso..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 40))

            //step 5: monitora enrichment via websocket
            val enrichmentSuccess = monitorEnrichment(movies.size)

            //step 6: refresh finale
            setForeground(createForegroundInfo(95, "aggiornamento database..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 95))

            val repository = MovieRepository.getInstance(context)
            val refreshSuccess = repository.refreshFromBackend()

            //step 7: completion
            setForeground(createForegroundInfo(100, "completato!"))
            setProgressAsync(workDataOf(KEY_PROGRESS to 100))

            val finalMovies = repository.movies.value ?: emptyList()
            Log.d(TAG, "import completato: ${finalMovies.size} film totali")

            showCompletionNotification(
                "import completato!",
                "${movies.size} film importati con successo",
                true
            )

            //cleanup
            file.delete()

            Result.success(workDataOf(
                KEY_STATUS to "success",
                KEY_MESSAGE to "import completato",
                KEY_TOTAL_MOVIES to finalMovies.size
            ))

        } catch (e: Exception) {
            Log.e(TAG, "errore import worker: ${e.message}", e)
            showCompletionNotification("import fallito", e.message ?: "errore sconosciuto", false)
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
     * monitora enrichment via websocket
     */
    private suspend fun monitorEnrichment(totalMovies: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var lastProgress = 40
                var completed = false
                val startTime = System.currentTimeMillis()
                val timeout = 15 * 60 * 1000L //15 minuti

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

                    //attendi completamento o timeout
                    while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
                        delay(500)
                    }

                    job.cancel()
                }

                //se timeout raggiunto, il backend sta ancora lavorando
                if (!completed) {
                    Log.w(TAG, "timeout raggiunto ma enrichment continua in background")
                    //non e un errore, ritorna success
                    return@withContext true
                }

                completed

            } catch (e: Exception) {
                Log.e(TAG, "errore monitoring enrichment: ${e.message}", e)
                //procedi comunque
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

    /**
     * mostra notifica completamento
     */
    private fun showCompletionNotification(title: String, message: String, success: Boolean) {
        val notification = NotificationHelper.createCompletionNotification(
            context = context,
            title = title,
            message = message,
            success = success
        )

        NotificationHelper.showNotification(
            context,
            NotificationHelper.NOTIFICATION_ID_IMPORT + 1,
            notification
        )

        //rimuovi notifica progress
        NotificationHelper.cancelNotification(context, NotificationHelper.NOTIFICATION_ID_IMPORT)
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