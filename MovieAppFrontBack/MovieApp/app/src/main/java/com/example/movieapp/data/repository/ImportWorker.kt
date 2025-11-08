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
 * worker per import - fa SOLO parsing e upload, NON aspetta enrichment
 * l'enrichment è monitorato dall'UI via websocket
 */
class ImportWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "ImportWorker"
    private val csvProcessor = CsvProcessor()

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_CSV_TYPE = "csv_type"
        const val KEY_PROGRESS = "progress"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_TOTAL_MOVIES = "total_movies"
        const val KEY_MESSAGE = "message"
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
                Log.e(TAG, "file non trovato")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "file non trovato"))
            }

            //step 1: parsing (0-50%)
            Log.d(TAG, "--- STEP 1: PARSING ---")
            setForeground(createForegroundInfo(25, "lettura file..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 25))

            val parseResult: CsvParseResult = when (csvType) {
                "IMDB_WATCHED" -> csvProcessor.parseImdbWatchedCsv(FileInputStream(file))
                "IMDB_WATCHLIST" -> csvProcessor.parseImdbWatchlistCsv(FileInputStream(file))
                "LETTERBOXD_WATCHED" -> csvProcessor.parseLetterboxdWatchedCsv(FileInputStream(file))
                "LETTERBOXD_WATCHLIST" -> csvProcessor.parseLetterboxdWatchlistCsv(FileInputStream(file))
                else -> {
                    Log.e(TAG, "tipo csv non valido")
                    return@withContext Result.failure(workDataOf(KEY_MESSAGE to "tipo csv non valido"))
                }
            }

            val movies = parseResult.movies
            Log.d(TAG, "parsed ${movies.size} film")

            if (movies.isEmpty()) {
                Log.w(TAG, "file vuoto")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "file vuoto"))
            }

            setProgressAsync(workDataOf(KEY_PROGRESS to 50))
            delay(300)

            //step 2: separa watched/watchlist
            Log.d(TAG, "--- STEP 2: PREPARAZIONE ---")
            setForeground(createForegroundInfo(60, "preparazione..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 60))

            val watched = movies.filter { it.isWatched }
            val watchlist = movies.filter { !it.isWatched }

            Log.d(TAG, "watched: ${watched.size}, watchlist: ${watchlist.size}")
            delay(300)

            //step 3: upload
            Log.d(TAG, "--- STEP 3: UPLOAD ---")
            setForeground(createForegroundInfo(75, "upload al server..."))
            setProgressAsync(workDataOf(KEY_PROGRESS to 75))

            val uploadResult = ApiService.batchUpload(watchlist, watched)

            if (uploadResult.isFailure) {
                val error = uploadResult.exceptionOrNull()?.message ?: "errore upload"
                Log.e(TAG, "upload failed: $error")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to error))
            }

            val batchResponse = uploadResult.getOrNull()
            if (batchResponse == null) {
                Log.e(TAG, "risposta vuota")
                return@withContext Result.failure(workDataOf(KEY_MESSAGE to "risposta vuota"))
            }

            Log.d(TAG, "upload OK! session: ${batchResponse.sessionId}")

            //step 4: completamento worker (100%)
            setProgressAsync(workDataOf(KEY_PROGRESS to 100))
            setForeground(createForegroundInfo(100, "enrichment avviato sul server"))
            delay(500)

            Log.d(TAG, "=== WORKER SUCCESS - enrichment procede in background ===")

            //ritorna SUCCESS con il sessionId per il monitoring UI
            return@withContext Result.success(
                workDataOf(
                    KEY_SESSION_ID to batchResponse.sessionId,
                    KEY_TOTAL_MOVIES to movies.size,
                    KEY_PROGRESS to 100,
                    KEY_MESSAGE to "Upload completato, enrichment in corso..."
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "=== ERRORE ===", e)
            e.printStackTrace()

            return@withContext Result.failure(
                workDataOf(KEY_MESSAGE to "errore: ${e.message}")
            )
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