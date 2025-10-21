package com.example.movieapp.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * servizio cache locale per film arricchiti
 * ottimizzato con flag per evitare duplicazioni
 */
class CacheService private constructor(private val context: Context) {

    private val TAG = "CacheService"

    //usa appconfig per nome sharedpreferences
    private var prefs: SharedPreferences = context.getSharedPreferences(
        AppConfig.CACHE_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    //configurazione ottimizzazioni da appconfig
    private val SAVE_BATCH_SIZE = AppConfig.BATCH_SIZE

    //cache in memoria per accesso veloce
    private var memoryCache: MutableMap<String, CachedMovieEntry> = mutableMapOf()
    private var isCacheLoaded = false
    private var unsavedChanges = 0
    private var isInitialized = false

    companion object {
        @Volatile
        private var INSTANCE: CacheService? = null

        fun getInstance(context: Context): CacheService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CacheService(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * inizializza cache con test backend usando appconfig
     */
    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "cache già inizializzata")
            return
        }

        prefs = context.getSharedPreferences(AppConfig.CACHE_PREFS_NAME, Context.MODE_PRIVATE)

        //test backend prima di usare cache
        kotlin.concurrent.thread {
            val isBackendOnline = kotlinx.coroutines.runBlocking {
                com.example.movieapp.data.network.ApiService.checkBackendHealth().isSuccess
            }

            if (isBackendOnline) {
                Log.i(TAG, "✅ backend ${AppConfig.BACKEND_HOST} online - cache attiva")
                prefs.edit().putBoolean("offline_mode", false).apply()
            } else {
                Log.w(TAG, "⚠️ backend ${AppConfig.BACKEND_HOST} offline - modalità cache locale")
                prefs.edit().putBoolean("offline_mode", true).apply()
            }
        }

        isInitialized = true
        Log.d(TAG, "cache service inizializzato")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    /**
     * verifica se in modalità offline
     */
    fun isOfflineMode(): Boolean {
        return prefs.getBoolean("offline_mode", false)
    }

    /**
     * imposta modalità offline
     */
    fun setOfflineMode(offline: Boolean) {
        prefs.edit().putBoolean("offline_mode", offline).apply()
        Log.d(TAG, if (offline) "modalità offline attivata" else "modalità offline disattivata")
    }

    /**
     * info stato con appconfig
     */
    fun getStatusInfo(): Map<String, Any> {
        return mapOf(
            "offline_mode" to isOfflineMode(),
            "cache_size" to memoryCache.size,
            "backend_host" to AppConfig.BACKEND_HOST,
            "backend_port" to AppConfig.BACKEND_PORT,
            "backend_url" to AppConfig.BACKEND_URL,
            "last_check" to System.currentTimeMillis()
        )
    }

    /**
     * carica cache dal disco
     */
    private fun loadCacheFromDisk() {
        try {
            val cachedJson = prefs.getString("cached_movies", null)
            if (cachedJson != null) {
                val type = object : TypeToken<Map<String, CachedMovieEntry>>() {}.type
                val loadedCache: Map<String, CachedMovieEntry> = gson.fromJson(cachedJson, type)

                memoryCache = loadedCache.toMutableMap()
                Log.d(TAG, "cache caricata: ${memoryCache.size} film (permanente)")
            } else {
                Log.d(TAG, "nessuna cache esistente")
            }
            isCacheLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "errore caricamento cache: ${e.message}", e)
            memoryCache.clear()
            isCacheLoaded = true
        }
    }

    /**
     * salva cache sul disco
     */
    private fun saveCacheToDisk() {
        try {
            val json = gson.toJson(memoryCache)
            prefs.edit()
                .putString("cached_movies", json)
                .putLong("cache_timestamp", System.currentTimeMillis())
                .apply()

            unsavedChanges = 0
            Log.d(TAG, "cache salvata: ${memoryCache.size} film")
        } catch (e: Exception) {
            Log.e(TAG, "errore salvataggio cache: ${e.message}", e)
        }
    }

    /**
     * genera chiave normalizzata
     */
    private fun generateCacheKey(title: String, year: Int?): String {
        val normalizedTitle = title.lowercase().trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
        return "${normalizedTitle}_${year ?: "unknown"}"
    }

    /**
     * controlla se film in cache (ottimizzato)
     */
    fun isCached(title: String, year: Int?): Boolean {
        if (!isCacheLoaded) loadCacheFromDisk()

        val key = generateCacheKey(title, year)
        val entry = memoryCache[key]

        if (entry != null) {
            // ✅ FIXED: Rimosse le !! non necessarie
            val isEnriched = entry.movie.tmdbId != null && entry.movie.tmdbId > 0
            Log.d(TAG, "cache hit: $title (arricchito: $isEnriched)")
            return isEnriched
        }

        Log.d(TAG, "cache miss: $title")
        return false
    }

    /**
     * recupera film dalla cache
     */
    fun getCachedMovie(title: String, year: Int?): Movie? {
        if (!isCacheLoaded) loadCacheFromDisk()

        val key = generateCacheKey(title, year)
        val entry = memoryCache[key]

        if (entry != null && entry.movie.tmdbId != null) {
            Log.d(TAG, "recuperato da cache: ${entry.movie.title}")
            return entry.movie
        }

        return null
    }

    /**
     * aggiunge film alla cache (solo se arricchito)
     */
    fun cacheMovie(movie: Movie) {
        if (!isCacheLoaded) loadCacheFromDisk()

        // ✅ FIXED: Rimossa !! non necessaria
        //⭐ rifiuta film senza tmdbid (non arricchiti)
        if (movie.tmdbId == null || movie.tmdbId <= 0) {
            Log.w(TAG, "rifiutato film non arricchito: ${movie.title}")
            return
        }

        val key = generateCacheKey(movie.title, movie.year)
        val entry = CachedMovieEntry(
            movie = movie,
            cacheTimestamp = System.currentTimeMillis()
        )

        memoryCache[key] = entry
        unsavedChanges++

        Log.d(TAG, "film cachato: ${movie.title} (tmdb: ${movie.tmdbId})")

        //salva ogni batch_size film (da appconfig)
        if (unsavedChanges >= SAVE_BATCH_SIZE) {
            saveCacheToDisk()
        }
    }

    /**
     * aggiunge lista film (batch)
     */
    fun cacheMovies(movies: List<Movie>) {
        if (!isCacheLoaded) loadCacheFromDisk()

        // ✅ FIXED: Rimossa !! non necessaria
        //filtra solo film arricchiti
        val enrichedMovies = movies.filter {
            it.tmdbId != null && it.tmdbId > 0
        }

        var addedCount = 0

        enrichedMovies.forEach { movie ->
            val key = generateCacheKey(movie.title, movie.year)
            if (!memoryCache.containsKey(key)) {
                val entry = CachedMovieEntry(
                    movie = movie,
                    cacheTimestamp = System.currentTimeMillis()
                )
                memoryCache[key] = entry
                addedCount++
            }
        }

        if (addedCount > 0) {
            unsavedChanges += addedCount
            Log.d(TAG, "aggiunti $addedCount film arricchiti alla cache")
            saveCacheToDisk()
        }
    }

    /**
     * separa film in cache da quelli da arricchire
     */
    fun filterMovies(movies: List<Movie>): CacheFilterResult {
        if (!isCacheLoaded) loadCacheFromDisk()

        val cached = mutableListOf<Movie>()
        val needEnrichment = mutableListOf<Movie>()

        movies.forEach { movie ->
            if (isCached(movie.title, movie.year)) {
                val cachedMovie = getCachedMovie(movie.title, movie.year)
                if (cachedMovie != null) {
                    //copia dati tmdb dalla cache
                    val enrichedMovie = movie.copy(
                        tmdbId = cachedMovie.tmdbId,
                        genres = cachedMovie.genres,
                        director = cachedMovie.director,
                        cast = cachedMovie.cast,
                        overview = cachedMovie.overview,
                        tagline = cachedMovie.tagline,
                        posterUrl = cachedMovie.posterUrl,
                        backdropUrl = cachedMovie.backdropUrl,
                        tmdbRating = cachedMovie.tmdbRating,
                        voteCount = cachedMovie.voteCount,
                        runtime = cachedMovie.runtime,
                        budget = cachedMovie.budget,
                        revenue = cachedMovie.revenue,
                        status = cachedMovie.status,
                        originalLanguage = cachedMovie.originalLanguage,
                        originalTitle = cachedMovie.originalTitle,
                        popularity = cachedMovie.popularity,
                        adult = cachedMovie.adult,
                        homepage = cachedMovie.homepage,
                        imdbId = cachedMovie.imdbId,
                        productionCompanies = cachedMovie.productionCompanies,
                        productionCountries = cachedMovie.productionCountries,
                        spokenLanguages = cachedMovie.spokenLanguages,
                        keywords = cachedMovie.keywords,
                        certification = cachedMovie.certification,
                        trailerUrl = cachedMovie.trailerUrl
                    )
                    cached.add(enrichedMovie)
                } else {
                    needEnrichment.add(movie)
                }
            } else {
                needEnrichment.add(movie)
            }
        }

        Log.d(TAG, "filtro: ${cached.size} in cache, ${needEnrichment.size} da arricchire")

        return CacheFilterResult(
            cachedMovies = cached,
            moviesNeedingEnrichment = needEnrichment,
            cacheHitRate = if (movies.isNotEmpty()) cached.size.toDouble() / movies.size else 0.0
        )
    }

    /**
     * statistiche cache con appconfig
     */
    fun getCacheStats(): CacheStats {
        if (!isCacheLoaded) loadCacheFromDisk()

        val metadata = prefs.getLong("cache_timestamp", 0)
        val lastUpdate = if (metadata > 0) Date(metadata) else null

        Log.d(TAG, "stats cache: ${memoryCache.size} film")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")

        return CacheStats(
            totalCachedMovies = memoryCache.size,
            expiredMovies = 0,
            lastUpdateTime = lastUpdate,
            cacheSize = calculateCacheSize(),
            unsavedChanges = unsavedChanges
        )
    }

    /**
     * calcola dimensione cache in byte
     */
    private fun calculateCacheSize(): Long {
        return try {
            val json = prefs.getString("cached_movies", "") ?: ""
            json.toByteArray().size.toLong()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * svuota cache
     */
    fun clearCache() {
        memoryCache.clear()
        prefs.edit().clear().apply()
        unsavedChanges = 0
        Log.d(TAG, "cache pulita")
    }

    /**
     * forza salvataggio immediato
     */
    fun forceSave() {
        if (unsavedChanges > 0) {
            saveCacheToDisk()
            Log.d(TAG, "salvataggio forzato")
        }
    }
}

//data classes
data class CachedMovieEntry(
    val movie: Movie,
    val cacheTimestamp: Long
)

data class CacheFilterResult(
    val cachedMovies: List<Movie>,
    val moviesNeedingEnrichment: List<Movie>,
    val cacheHitRate: Double
)

data class CacheStats(
    val totalCachedMovies: Int,
    val expiredMovies: Int,
    val lastUpdateTime: Date?,
    val cacheSize: Long,
    val unsavedChanges: Int
)