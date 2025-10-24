package com.example.movieapp.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.DataSource
import com.example.movieapp.data.network.ApiService
import android.util.Log
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

/**
 * repository centralizzato per gestione film con auto-sync backend dopo ogni operazione
 */
class MovieRepository private constructor(private val context: Context) {

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val moviesList = mutableListOf<Movie>()
    private val TAG = "MovieRepository"

    //sharedpreferences usando appconfig
    private val sharedPrefs = context.getSharedPreferences(
        AppConfig.REPO_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    //coroutine scope per operazioni async
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        private var INSTANCE: MovieRepository? = null

        fun getInstance(context: Context? = null): MovieRepository {
            return INSTANCE ?: synchronized(this) {
                if (context == null) {
                    throw IllegalArgumentException("context richiesto per prima inizializzazione")
                }
                INSTANCE ?: MovieRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    init {
        _movies.value = emptyList()
        Log.d(TAG, "repository inizializzato")
        Log.d(TAG, "database: ${AppConfig.DATABASE_TYPE}")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    /**
     * carica film da database locale con sync backend automatico
     */
    suspend fun loadMoviesFromDatabase(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== caricamento database ===")

                //step 1: carica da room locale
                val localSuccess = loadMoviesFromLocalStorage()
                val localMovies = moviesList.toList()

                Log.d(TAG, "room: ${localMovies.size} film")

                //step 2: sync con backend usando apiservice
                if (AppConfig.ENABLE_AUTO_SYNC) {
                    val backendMovies = syncWithBackendDatabase()

                    if (backendMovies.isNotEmpty()) {
                        Log.d(TAG, "=== sync backend ${AppConfig.BACKEND_HOST} ===")
                        Log.d(TAG, "backend: ${backendMovies.size} film")
                        Log.d(TAG, "room: ${localMovies.size} film")

                        //usa dataset più completo
                        val useBackend = backendMovies.size > localMovies.size ||
                                backendMovies.count { it.tmdbId != null } > localMovies.count { it.tmdbId != null }

                        if (useBackend) {
                            Log.d(TAG, "✓ uso dati backend (più completi)")

                            moviesList.clear()
                            moviesList.addAll(backendMovies)

                            withContext(Dispatchers.Main) {
                                _movies.value = moviesList.toList()
                            }

                            saveMoviesToRoomDatabase()

                            val enrichedCount = backendMovies.count { it.tmdbId != null }
                            Log.d(TAG, "✅ caricati ${backendMovies.size} film dal backend")
                            Log.d(TAG, "   arricchiti: $enrichedCount (${(enrichedCount * 100) / backendMovies.size}%)")

                            return@withContext true
                        } else {
                            Log.d(TAG, "✓ uso dati room (più recenti)")
                        }
                    } else {
                        Log.d(TAG, "backend non disponibile - uso solo room")
                    }
                }

                localSuccess

            } catch (e: Exception) {
                Log.e(TAG, "errore caricamento database: ${e.message}", e)
                false
            }
        }
    }

    /**
     * carica da storage locale
     */
    private fun loadMoviesFromLocalStorage(): Boolean {
        return try {
            val moviesJson = sharedPrefs.getString("room_movies", null)
            val lastSaved = sharedPrefs.getLong("room_last_saved", 0)
            val backendVersion = sharedPrefs.getString("backend_version", "unknown")

            if (moviesJson != null && lastSaved > 0) {
                val type = object : TypeToken<List<Movie>>() {}.type
                val loadedMovies: List<Movie> = gson.fromJson(moviesJson, type)

                moviesList.clear()
                moviesList.addAll(loadedMovies)
                _movies.value = moviesList.toList()

                val daysSince = (System.currentTimeMillis() - lastSaved) / (1000 * 60 * 60 * 24)
                val enrichedCount = loadedMovies.count { it.tmdbId != null }

                Log.d(TAG, "caricati ${loadedMovies.size} film da room")
                Log.d(TAG, "arricchiti: $enrichedCount (${(enrichedCount.toDouble() / loadedMovies.size * 100).toInt()}%)")
                Log.d(TAG, "ultimo save: $daysSince giorni fa")
                Log.d(TAG, "backend version: $backendVersion")

                true
            } else {
                Log.d(TAG, "nessun film in room")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore caricamento room: ${e.message}", e)
            false
        }
    }

    /**
     * sync con backend usando apiservice
     */
    private suspend fun syncWithBackendDatabase(): List<Movie> {
        return try {
            Log.d(TAG, "=== sync ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT} ===")

            val result = ApiService.getUserStoredMovies()

            if (result.isSuccess) {
                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    val enrichedCount = backendMovies.count { it.tmdbId != null }

                    Log.d(TAG, "backend: ${backendMovies.size} film totali")
                    Log.d(TAG, "arricchiti: $enrichedCount (${(enrichedCount.toDouble() / backendMovies.size * 100).toInt()}%)")

                    //mostra esempi
                    Log.d(TAG, "=== esempi dal backend ===")
                    backendMovies.filter { it.tmdbId != null }.take(3).forEach { movie ->
                        Log.d(TAG, "film: ${movie.title} (${movie.year})")
                        Log.d(TAG, "  tmdb: ${movie.tmdbId}")
                        Log.d(TAG, "  generi: ${movie.genres.joinToString(", ").ifEmpty { "nessuno" }}")
                        Log.d(TAG, "  regista: ${movie.director ?: "sconosciuto"}")
                    }

                    //verifica non arricchiti
                    val notEnriched = backendMovies.filter { it.tmdbId == null }
                    if (notEnriched.isNotEmpty()) {
                        Log.w(TAG, "⚠️ ${notEnriched.size} film senza tmdb id")
                    }

                    backendMovies
                } else {
                    Log.d(TAG, "backend vuoto")
                    emptyList()
                }
            } else {
                val error = result.exceptionOrNull()
                Log.w(TAG, "backend ${AppConfig.BACKEND_HOST} non raggiungibile: ${error?.message}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore sync ${AppConfig.BACKEND_HOST}", e)
            emptyList()
        }
    }

    /**
     * ✅ NUOVO: Forza refresh esplicito dal backend
     */
    suspend fun refreshFromBackend(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Refresh forzato dal backend...")

                val backendMovies = syncWithBackendDatabase()

                if (backendMovies.isNotEmpty()) {
                    moviesList.clear()
                    moviesList.addAll(backendMovies)

                    withContext(Dispatchers.Main) {
                        _movies.value = moviesList.toList()
                    }

                    saveMoviesToRoomDatabase()

                    val enrichedCount = backendMovies.count { it.tmdbId != null }
                    Log.d(TAG, "✅ Refresh completato: ${backendMovies.size} film")
                    Log.d(TAG, "   Arricchiti: $enrichedCount")

                    true
                } else {
                    Log.w(TAG, "⚠️ Backend returned empty list")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore refresh backend", e)
                false
            }
        }
    }

    /**
     * salva in room con metadata da appconfig
     */
    private fun saveMoviesToRoomDatabase() {
        try {
            val moviesJson = gson.toJson(moviesList)
            val enrichedCount = moviesList.count { it.tmdbId != null }
            val watchedCount = moviesList.count { it.isWatched }

            sharedPrefs.edit()
                .putString("room_movies", moviesJson)
                .putLong("room_last_saved", System.currentTimeMillis())
                .putString("backend_version", AppConfig.APP_VERSION)
                .putString("backend_host", AppConfig.BACKEND_HOST)
                .putInt("backend_port", AppConfig.BACKEND_PORT)
                .putInt("enriched_count", enrichedCount)
                .putInt("watched_count", watchedCount)
                .putString("database_type", AppConfig.DATABASE_TYPE)
                .apply()

            Log.d(TAG, "room aggiornato: ${moviesList.size} film")
            Log.d(TAG, "stats: $enrichedCount arricchiti, $watchedCount visti")

        } catch (e: Exception) {
            Log.e(TAG, "errore salvataggio room: ${e.message}", e)
        }
    }

    /**
     * aggiunge film evitando duplicati con auto-sync
     */
    fun addMovies(newMovies: List<Movie>) {
        if (newMovies.isEmpty()) {
            Log.d(TAG, "nessun film da aggiungere")
            return
        }

        val uniqueNewMovies = newMovies.filter { newMovie ->
            moviesList.none { existing -> existing.id == newMovie.id }
        }

        if (uniqueNewMovies.isNotEmpty()) {
            moviesList.addAll(uniqueNewMovies)
            _movies.value = moviesList.toList()

            //salva in room
            repositoryScope.launch {
                saveMoviesToRoomDatabase()
            }

            val enrichedNew = uniqueNewMovies.count { it.tmdbId != null }
            Log.d(TAG, "aggiunti ${uniqueNewMovies.size} film unici")
            Log.d(TAG, "duplicati ignorati: ${newMovies.size - uniqueNewMovies.size}")
            Log.d(TAG, "nuovi arricchiti: $enrichedNew")
            Log.d(TAG, "totale: ${moviesList.size} film")

            logEnrichmentStatus(uniqueNewMovies)
        } else {
            Log.d(TAG, "tutti i ${newMovies.size} film sono duplicati")
        }
    }

    /**
     * sostituisce film esistenti con auto-sync
     */
    fun replaceMovies(updatedMovies: List<Movie>) {
        if (updatedMovies.isEmpty()) {
            Log.d(TAG, "nessun film da sostituire")
            return
        }

        var replacedCount = 0
        var addedCount = 0

        updatedMovies.forEach { updatedMovie ->
            val index = moviesList.indexOfFirst { it.id == updatedMovie.id }
            if (index != -1) {
                moviesList[index] = updatedMovie
                replacedCount++
            } else {
                moviesList.add(updatedMovie)
                addedCount++
            }
        }

        _movies.value = moviesList.toList()

        repositoryScope.launch {
            saveMoviesToRoomDatabase()
        }

        val enrichedUpdated = updatedMovies.count { it.tmdbId != null }
        Log.d(TAG, "=== replace completato ===")
        Log.d(TAG, "sostituiti: $replacedCount")
        Log.d(TAG, "aggiunti: $addedCount")
        Log.d(TAG, "arricchiti: $enrichedUpdated/${updatedMovies.size}")

        logEnrichmentStatus(updatedMovies)
    }

    /**
     * sostituisce tutto
     */
    fun replaceAll(newMovies: List<Movie>) {
        val previousSize = moviesList.size
        moviesList.clear()
        moviesList.addAll(newMovies)
        _movies.value = moviesList.toList()

        repositoryScope.launch {
            saveMoviesToRoomDatabase()
        }

        val enrichedCount = newMovies.count { it.tmdbId != null }
        Log.d(TAG, "=== replace all ===")
        Log.d(TAG, "precedenti: $previousSize")
        Log.d(TAG, "nuovi: ${newMovies.size}")
        Log.d(TAG, "arricchiti: $enrichedCount")

        logEnrichmentStatus(newMovies)
    }

    /**
     * log enrichment status
     */
    private fun logEnrichmentStatus(movies: List<Movie>) {
        val enriched = movies.count { it.tmdbId != null }
        val notEnriched = movies.size - enriched
        val watched = movies.count { it.isWatched }

        Log.d(TAG, "=== enrichment status ===")
        Log.d(TAG, "processati: ${movies.size}")
        Log.d(TAG, "con tmdb: $enriched (${(enriched.toDouble() / movies.size * 100).toInt()}%)")
        Log.d(TAG, "senza tmdb: $notEnriched")
        Log.d(TAG, "visti: $watched")

        //esempi arricchiti
        movies.filter { it.tmdbId != null }.take(3).forEach { movie ->
            Log.d(TAG, "✓ ${movie.title} - tmdb: ${movie.tmdbId}")
            Log.d(TAG, "  generi: ${movie.genres.joinToString(", ").ifEmpty { "n/a" }}")
        }

        //esempi non arricchiti
        movies.filter { it.tmdbId == null }.take(2).forEach { movie ->
            Log.d(TAG, "✗ ${movie.title} - non arricchito")
        }
    }
    //getter con filtri

    fun getAllMovies(): List<Movie> = moviesList.toList()

    fun getWatchlist(): List<Movie> {
        val watchlist = moviesList.filter { !it.isWatched }
        Log.d(TAG, "watchlist: ${watchlist.size} film")
        return watchlist
    }

    fun getWatched(): List<Movie> {
        val watched = moviesList.filter { it.isWatched }
        Log.d(TAG, "watched: ${watched.size} film")
        return watched
    }

    fun getMoviesBySource(source: DataSource): List<Movie> {
        val bySource = moviesList.filter { it.source == source }
        Log.d(TAG, "source $source: ${bySource.size} film")
        return bySource
    }

    fun getEnrichedMovies(): List<Movie> {
        val enriched = moviesList.filter { it.tmdbId != null }
        Log.d(TAG, "enriched: ${enriched.size} film")
        return enriched
    }

    /**
     * statistiche con appconfig
     */
    fun getStats(): Map<String, Int> {
        val stats = mapOf(
            "total" to moviesList.size,
            "watched" to getWatched().size,
            "watchlist" to getWatchlist().size,
            "imdb" to moviesList.count { it.source == DataSource.IMDB },
            "letterboxd" to moviesList.count { it.source == DataSource.LETTERBOXD },
            "enriched" to getEnrichedMovies().size,
            "with_rating" to moviesList.count { it.userRating != null },
            "with_poster" to moviesList.count { it.posterUrl != null },
            "recent_movies" to moviesList.count { it.year != null && it.year >= 2020 }
        )

        Log.d(TAG, "stats: $stats")
        return stats
    }

    /**
     * dettagli enrichment con appconfig
     */
    fun getEnrichmentDetails(): String {
        val enriched = getEnrichedMovies()
        val unenriched = moviesList.filter { it.tmdbId == null }
        val backendVersion = sharedPrefs.getString("backend_version", "unknown")
        val lastSaved = sharedPrefs.getLong("room_last_saved", 0)

        return buildString {
            appendLine("=== dettagli enrichment ===")
            appendLine("database: ${AppConfig.DATABASE_TYPE}")
            appendLine("backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
            appendLine("version: $backendVersion")
            appendLine("ultimo sync: ${if (lastSaved > 0) java.util.Date(lastSaved) else "mai"}")
            appendLine()
            appendLine("film totali: ${moviesList.size}")
            appendLine("arricchiti: ${enriched.size}")
            appendLine("non arricchiti: ${unenriched.size}")
            appendLine("tasso: ${if (moviesList.isNotEmpty()) (enriched.size * 100) / moviesList.size else 0}%")
            appendLine()
            if (enriched.isNotEmpty()) {
                appendLine("esempi arricchiti:")
                enriched.take(3).forEach { movie ->
                    appendLine("• ${movie.title} (${movie.year})")
                    appendLine("  tmdb: ${movie.tmdbId}")
                    appendLine("  generi: ${movie.genres.joinToString(", ").ifEmpty { "n/a" }}")
                }
                appendLine()
            }

            if (unenriched.isNotEmpty()) {
                appendLine("non arricchiti:")
                unenriched.take(3).forEach { movie ->
                    appendLine("• ${movie.title} (${movie.year ?: "n/a"})")
                }
                appendLine()
            }

            val stats = getStats()
            appendLine("statistiche:")
            stats.forEach { (key, value) ->
                appendLine("$key: $value")
            }
        }
    }

    /**
     * pulisce tutto
     */
    fun clearAll() {
        val previousSize = moviesList.size
        moviesList.clear()
        _movies.value = emptyList()

        sharedPrefs.edit().clear().apply()

        Log.d(TAG, "repository pulito ($previousSize film rimossi)")
    }

    /**
     * pulisce per source
     */
    fun clearBySource(source: DataSource) {
        val previousSize = moviesList.size
        moviesList.removeAll { it.source == source }
        _movies.value = moviesList.toList()

        repositoryScope.launch {
            saveMoviesToRoomDatabase()
        }

        val removedCount = previousSize - moviesList.size
        Log.d(TAG, "rimossi $removedCount film da $source")
    }

    /**
     * cleanup
     */
    fun cleanup() {
        repositoryScope.cancel()
        Log.d(TAG, "repository cleanup completato")
    }
}