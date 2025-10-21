package com.example.movieapp.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.parser.CsvProcessor
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.EnrichmentProgressTracker
import com.example.movieapp.data.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.InputStream
import android.content.Context

class HomeViewModel : ViewModel() {
    private val TAG = "HomeViewModel"

    private var movieRepository: MovieRepository? = null
    private val csvProcessor = CsvProcessor()
    private var applicationContext: Context? = null

    private val progressTracker = EnrichmentProgressTracker()

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _enrichmentProgress = MutableLiveData<Pair<Int, Int>>()
    val enrichmentProgress: LiveData<Pair<Int, Int>> = _enrichmentProgress

    init {
        _isLoading.value = false
        _message.value = ""
        _enrichmentProgress.value = 0 to 0

        viewModelScope.launch {
            progressTracker.progress.collect { progress ->
                _enrichmentProgress.postValue(progress.current to progress.total)
            }
        }

        Log.d(TAG, "homeviewmodel inizializzato")
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        movieRepository = MovieRepository.getInstance(applicationContext)

        ApiService.initialize(applicationContext!!)
        CacheService.getInstance(applicationContext!!).init(applicationContext!!)

        movieRepository?.movies?.observeForever { movies ->
            _movies.value = movies ?: emptyList()
        }

        loadSavedMovies()
        testBackendConnectivity()

        Log.d(TAG, "viewmodel inizializzato")
    }

    private fun testBackendConnectivity() {
        viewModelScope.launch {
            try {
                val isConnected = ApiService.testConnection()

                if (isConnected) {
                    Log.i(TAG, "✅ backend raggiungibile")
                    setMessage("✅ connesso")
                } else {
                    Log.w(TAG, "⚠️ backend non raggiungibile")
                    setMessage("")
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore test connettività", e)
            }
        }
    }

    fun forceBackendSync() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== sync manuale ===")
                setLoading(true)

                val repository = movieRepository
                if (repository == null) {
                    setMessage("❌ errore inizializzazione")
                    setLoading(false)
                    return@launch
                }

                val result = ApiService.getAllStoredMovies()

                if (result.isSuccess) {
                    val backendMovies = result.getOrNull() ?: emptyList()

                    if (backendMovies.isNotEmpty()) {
                        val enrichedCount = backendMovies.count { it.tmdbId != null }

                        repository.replaceAll(backendMovies)

                        setMessage(buildString {
                            appendLine("✅ sincronizzazione completata!")
                            appendLine()
                            appendLine("📊 ${backendMovies.size} film")
                            if (enrichedCount > 0) {
                                appendLine("✨ $enrichedCount con dettagli completi")
                            }
                        })

                        Log.d(TAG, "✅ sync completato")
                    } else {
                        setMessage("💡 importa i tuoi film per iniziare")
                    }
                } else {
                    setMessage("⚠️ impossibile sincronizzare.\nriprova più tardi.")
                }

                setLoading(false)

            } catch (e: Exception) {
                Log.e(TAG, "errore sync", e)
                setMessage("❌ errore di connessione")
                setLoading(false)
            }
        }
    }

    private fun loadSavedMovies() {
        viewModelScope.launch {
            try {
                val repository = movieRepository ?: return@launch

                repository.loadMoviesFromDatabase()
                val localMovies = repository.getAllMovies()

                if (localMovies.isNotEmpty()) {
                    val enrichedCount = localMovies.count { it.tmdbId != null }
                    Log.d(TAG, "caricati ${localMovies.size} film ($enrichedCount con dettagli)")

                    if (enrichedCount > 0) {
                        setMessage("✅ ${localMovies.size} film caricati")
                    }
                }

                if (applicationContext != null) {
                    syncWithBackend()
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore caricamento", e)
            }
        }
    }

    private suspend fun syncWithBackend() {
        try {
            val repository = movieRepository ?: return

            val backendResult = ApiService.getAllStoredMovies()

            if (backendResult.isSuccess) {
                val backendMovies = backendResult.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    val localMovies = repository.getAllMovies()

                    if (backendMovies.size > localMovies.size) {
                        repository.replaceAll(backendMovies)
                        setMessage("✅ sincronizzazione completata")
                    }
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "sync automatico fallito: ${e.message}")
        }
    }

    fun processImdbWatchedCsv(inputStream: InputStream) {
        val repository = movieRepository
        if (repository == null) {
            setMessage("❌ errore inizializzazione")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)

                val result = withContext(Dispatchers.IO) {
                    csvProcessor.parseImdbWatchedCsv(inputStream)
                }

                if (result.successfulRows > 0) {
                    repository.addMovies(result.movies)
                    setMessage("⏳ importazione in background...\n${result.movies.size} film")
                    performEnrichmentWithBackend(result.movies)
                } else {
                    setLoading(false)
                    setMessage("❌ file non valido")
                }

            } catch (e: Exception) {
                Log.e(TAG, "eccezione import", e)
                setLoading(false)
                setMessage("❌ errore durante l'importazione")
            }
        }
    }

    fun processImdbWatchlistCsv(inputStream: InputStream) {
        val repository = movieRepository
        if (repository == null) {
            setMessage("❌ errore inizializzazione")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)

                val result = withContext(Dispatchers.IO) {
                    csvProcessor.parseImdbWatchlistCsv(inputStream)
                }

                if (result.successfulRows > 0) {
                    repository.addMovies(result.movies)
                    setMessage("⏳ importazione in background...\n${result.movies.size} film")
                    performEnrichmentWithBackend(result.movies)
                } else {
                    setLoading(false)
                    setMessage("❌ file non valido")
                }

            } catch (e: Exception) {
                Log.e(TAG, "eccezione import", e)
                setLoading(false)
                setMessage("❌ errore durante l'importazione")
            }
        }
    }

    fun processLetterboxdWatchedCsv(inputStream: InputStream) {
        val repository = movieRepository
        if (repository == null) {
            setMessage("❌ errore inizializzazione")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)

                val result = withContext(Dispatchers.IO) {
                    csvProcessor.parseLetterboxdWatchedCsv(inputStream)
                }

                if (result.successfulRows > 0) {
                    repository.addMovies(result.movies)
                    setMessage("⏳ importazione in background...\n${result.movies.size} film")
                    performEnrichmentWithBackend(result.movies)
                } else {
                    setLoading(false)
                    setMessage("❌ file non valido")
                }

            } catch (e: Exception) {
                Log.e(TAG, "eccezione import", e)
                setLoading(false)
                setMessage("❌ errore durante l'importazione")
            }
        }
    }

    fun processLetterboxdWatchlistCsv(inputStream: InputStream) {
        val repository = movieRepository
        if (repository == null) {
            setMessage("❌ errore inizializzazione")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)

                val result = withContext(Dispatchers.IO) {
                    csvProcessor.parseLetterboxdWatchlistCsv(inputStream)
                }

                if (result.successfulRows > 0) {
                    repository.addMovies(result.movies)
                    setMessage("⏳ importazione in background...\n${result.movies.size} film")
                    performEnrichmentWithBackend(result.movies)
                } else {
                    setLoading(false)
                    setMessage("❌ file non valido")
                }

            } catch (e: Exception) {
                Log.e(TAG, "eccezione import", e)
                setLoading(false)
                setMessage("❌ errore durante l'importazione")
            }
        }
    }

    private suspend fun performEnrichmentWithBackend(movies: List<Movie>) {
        try {
            val repository = movieRepository
            if (repository == null) {
                setMessage("❌ errore inizializzazione")
                setLoading(false)
                return
            }

            Log.d(TAG, "═══════════════════════════════════")
            Log.d(TAG, "🎬 INIZIO ENRICHMENT")
            Log.d(TAG, "Film da arricchire: ${movies.size}")
            Log.d(TAG, "═══════════════════════════════════")

            progressTracker.startTracking(movies.size, viewModelScope)

            withContext(Dispatchers.Main) {
                _enrichmentProgress.value = 0 to movies.size
            }

            Log.d(TAG, "📤 Chiamata API enrichment...")
            val result = withContext(Dispatchers.IO) {
                ApiService.enrichMoviesAutomatic(movies)
            }

            Log.d(TAG, "📥 Risposta ricevuta!")

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val enrichmentResult = result.getOrNull()!!

                    val actuallyEnriched = enrichmentResult.successfulMovies.count { it.tmdbId != null }

                    Log.d(TAG, "✅ ENRICHMENT COMPLETATO:")
                    Log.d(TAG, "   Totali: ${enrichmentResult.totalProcessed}")
                    Log.d(TAG, "   Arricchiti: $actuallyEnriched")
                    Log.d(TAG, "   Cache hits: ${enrichmentResult.cacheHits}")
                    Log.d(TAG, "   Falliti: ${enrichmentResult.failedMovies.size}")

                    progressTracker.complete(enrichmentResult.successfulMovies.size)

                    val enrichedMovies = enrichmentResult.successfulMovies.map { dto ->
                        Movie(
                            id = dto.id,
                            title = dto.title,
                            year = dto.year,
                            director = dto.director,
                            genres = dto.genres,
                            cast = dto.cast,
                            overview = dto.overview,
                            runtime = dto.runtime,
                            userRating = dto.userRating,
                            dateRated = dto.watchedDate,
                            isWatched = dto.isWatched,
                            source = try {
                                com.example.movieapp.data.models.DataSource.valueOf(dto.source)
                            } catch (_: Exception) {
                                com.example.movieapp.data.models.DataSource.UNKNOWN
                            },
                            tmdbId = dto.tmdbId,
                            posterUrl = dto.posterUrl,
                            backdropUrl = dto.backdropUrl,
                            tmdbRating = dto.tmdbRating,
                            voteCount = dto.voteCount
                        )
                    }

                    repository.replaceAll(enrichedMovies)

                    Log.d(TAG, "💾 Salvati ${enrichedMovies.size} film nel repository")

                    enrichedMovies.take(3).forEach { movie ->
                        Log.d(TAG, "  📽️ ${movie.title}")
                        Log.d(TAG, "     isWatched: ${movie.isWatched}")
                        Log.d(TAG, "     tmdbId: ${movie.tmdbId}")
                        Log.d(TAG, "     genres: ${movie.genres.joinToString()}")
                    }

                    Log.d(TAG, "🔄 Auto-sync backend...")
                    delay(1000)

                    val syncResult = withContext(Dispatchers.IO) {
                        ApiService.getAllStoredMovies()
                    }

                    if (syncResult.isSuccess) {
                        val backendMovies = syncResult.getOrNull() ?: emptyList()

                        if (backendMovies.isNotEmpty()) {
                            Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")
                            repository.replaceAll(backendMovies)

                            val watchedCount = backendMovies.count { it.isWatched }
                            Log.d(TAG, "📊 Film watched dopo sync: $watchedCount")
                        }
                    }

                    val summary = buildString {
                        appendLine("✅ importazione completata!")
                        appendLine()
                        appendLine("📊 ${enrichmentResult.successfulMovies.size} film importati")
                        if (actuallyEnriched > 0) {
                            appendLine("✨ $actuallyEnriched con dettagli completi")
                        }
                        if (enrichmentResult.cacheHits > 0) {
                            appendLine("⚡ ${enrichmentResult.cacheHits} già disponibili")
                        }
                        appendLine()
                        appendLine("💡 vai su 'statistiche' per vedere i grafici!")
                    }

                    _message.value = summary
                    Log.d(TAG, "═══════════════════════════════════")
                } else {
                    Log.e(TAG, "❌ Enrichment fallito")
                    _message.value = buildString {
                        appendLine("⚠️ alcuni dettagli non disponibili")
                        appendLine()
                        appendLine("i film sono stati importati,")
                        appendLine("ma non è stato possibile")
                        appendLine("aggiungere tutti i dettagli.")
                    }
                }

                setLoading(false)

                delay(5000)
                progressTracker.reset()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRORE ENRICHMENT", e)
            e.printStackTrace()

            withContext(Dispatchers.Main) {
                _message.value = buildString {
                    appendLine("❌ errore enrichment")
                    appendLine()
                    appendLine("${e.message}")
                    appendLine()
                    appendLine("verifica connessione backend")
                }

                setLoading(false)
                progressTracker.reset()
            }
        }
    }

    fun clearMessage() {
        _message.value = ""
    }

    private fun setLoading(loading: Boolean) {
        if (_isLoading.value != loading) {
            _isLoading.postValue(loading)
        }
    }

    private fun setMessage(message: String) {
        _message.postValue(message)
    }

    fun getStats(): Map<String, Int> {
        return movieRepository?.getStats() ?: emptyMap()
    }

    fun getCurrentBackendInfo(): Map<String, String> = AppConfig.getBackendInfo()

    fun clearAllData() {
        viewModelScope.launch {
            val repository = movieRepository ?: return@launch

            val previousCount = repository.getAllMovies().size

            repository.clearAll()

            if (applicationContext != null) {
                CacheService.getInstance(applicationContext!!).clearCache()
            }

            setMessage(buildString {
                appendLine("✅ pulizia completata")
                appendLine()
                appendLine("$previousCount film rimossi")
            })
        }
    }

    override fun onCleared() {
        super.onCleared()
        movieRepository?.cleanup()
        progressTracker.reset()
    }
}