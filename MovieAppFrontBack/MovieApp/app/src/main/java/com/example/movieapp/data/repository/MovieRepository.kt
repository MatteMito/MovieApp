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
 * repository centralizzato per gestione film con auto-sync backend
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

                        //usa dataset piu completo
                        val useBackend = backendMovies.size > localMovies.size ||
                                backendMovies.count { it.tmdbId != null } > localMovies.count { it.tmdbId != null }

                        if (useBackend) {
                            Log.d(TAG, "uso backend (piu completo)")
                            moviesList.clear()
                            moviesList.addAll(backendMovies)
                            withContext(Dispatchers.Main) {
                                _movies.value = moviesList.toList()
                            }
                            saveMoviesToRoomDatabase()
                        } else {
                            Log.d(TAG, "uso room (gia aggiornato)")
                        }

                        true
                    } else {
                        Log.d(TAG, "backend vuoto, uso room")
                        localSuccess
                    }
                } else {
                    Log.d(TAG, "auto-sync disabilitato")
                    localSuccess
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore caricamento: ${e.message}", e)
                false
            }
        }
    }

    /**
     * forza refresh esplicito dal backend - fix per "errore refresh"
     */
    suspend fun refreshFromBackend(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== refresh forzato dal backend ===")

                //prova a ottenere film dal backend
                val backendMovies = syncWithBackendDatabase()

                if (backendMovies.isNotEmpty()) {
                    //aggiorna memoria
                    moviesList.clear()
                    moviesList.addAll(backendMovies)

                    //aggiorna livedata
                    withContext(Dispatchers.Main) {
                        _movies.value = moviesList.toList()
                    }

                    //salva in room
                    saveMoviesToRoomDatabase()

                    val enrichedCount = backendMovies.count { it.tmdbId != null }
                    Log.d(TAG, "refresh completato: ${backendMovies.size} film")
                    Log.d(TAG, "arricchiti: $enrichedCount")

                    true
                } else {
                    //backend vuoto ma non e un errore se utente non ha film
                    Log.w(TAG, "backend vuoto o non raggiungibile")

                    //prova a testare connessione
                    val isReachable = try {
                        ApiService.testConnection()
                    } catch (e: Exception) {
                        false
                    }

                    if (isReachable) {
                        //backend raggiungibile ma vuoto = success
                        Log.d(TAG, "backend raggiungibile ma vuoto (ok)")
                        moviesList.clear()
                        withContext(Dispatchers.Main) {
                            _movies.value = emptyList()
                        }
                        saveMoviesToRoomDatabase()
                        true
                    } else {
                        //backend non raggiungibile = error
                        Log.e(TAG, "backend non raggiungibile")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore refresh backend: ${e.message}", e)
                false
            }
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
                        Log.w(TAG, "${notEnriched.size} film senza tmdb id")
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
     * carica da room local storage
     */
    private fun loadMoviesFromLocalStorage(): Boolean {
        return try {
            val moviesJson = sharedPrefs.getString("movies_list", null)
            val lastSaved = sharedPrefs.getLong("last_saved", 0)
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
     * salva in room con metadata
     */
    private fun saveMoviesToRoomDatabase() {
        try {
            val moviesJson = gson.toJson(moviesList)
            sharedPrefs.edit()
                .putString("movies_list", moviesJson)
                .putLong("last_saved", System.currentTimeMillis())
                .putString("backend_version", AppConfig.APP_VERSION)
                .apply()

            Log.d(TAG, "salvati ${moviesList.size} film in room")
        } catch (e: Exception) {
            Log.e(TAG, "errore salvataggio room: ${e.message}", e)
        }
    }

    /**
     * elimina tutti i film
     */
    fun clearAll() {
        moviesList.clear()
        _movies.value = emptyList()
        sharedPrefs.edit().clear().apply()
        Log.d(TAG, "repository pulito")
    }
}