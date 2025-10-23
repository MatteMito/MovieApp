package com.example.movieapp.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.MovieList
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

//data classes per risposte backend

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val timestamp: String?
)

//auth responses
data class AuthResponse(
    val access_token: String,
    val user: UserInfo
)

data class UserInfo(
    val id: String,
    val email: String,
    val username: String?
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String?
)

//movie dto per backend
data class MovieDto(
    val id: String,
    val title: String,
    val year: Int?,
    val userRating: Double?,
    val watchedDate: String?,
    val userReview: String?,
    val isWatched: Boolean,
    val source: String
)

//enrichment responses
data class EnrichmentResponse(
    val sessionId: String,
    val successfulMovies: List<EnrichedMovieDto>,
    val failedMovies: List<FailedMovie>,
    val totalProcessed: Int,
    val successRate: Double,
    val cacheHits: Int
)

data class EnrichedMovieDto(
    val id: String,
    val title: String,
    val year: Int?,
    val tmdbId: Int?,
    val genres: List<String>,
    val director: String?,
    @SerializedName("actors") // 🔧 MODIFICATO: Backend usa "actors" invece di "cast"
    val cast: List<String>,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val tmdbRating: Double?,
    val voteCount: Int?,
    val runtime: Int?,
    val userRating: Double?,
    val watchedDate: String?,
    val isWatched: Boolean,
    val source: String
)

data class FailedMovie(
    val movie: MovieDto,
    val error: String
)

data class EnrichRequest(
    val movies: List<MovieDto>
)

// 🆕 NUOVO: Batch request con userId
data class BatchUploadRequest(
    val userId: String,  // 🆕 AGGIUNTO: ID utente obbligatorio
    val watchlist: List<MovieDto>,
    val watched: List<MovieDto>
)

// 🆕 NUOVO: Contatori import
data class ImportCounters(
    val fromFile: FileCounters,
    val afterRefresh: TotalCounters
)

data class FileCounters(
    val watched: Int,
    val watchlist: Int,
    val total: Int
)

data class TotalCounters(
    val watched: Int,
    val watchlist: Int,
    val total: Int
)

// 🆕 MODIFICATO: Batch response con contatori
data class BatchResponse(
    val sessionId: String,
    val watchlistResult: EnrichmentResponse,
    val watchedResult: EnrichmentResponse,
    val summary: BatchSummary,
    val counters: ImportCounters  // 🆕 AGGIUNTO: Contatori separati
)

data class BatchSummary(
    val totalMovies: Int,
    val watchlistCount: Int,
    val watchedCount: Int,
    val totalEnriched: Int,
    val overallSuccessRate: Double,
    val cacheHitsTotal: Int
)

//movies list response
data class MoviesListResponse(
    val movies: List<EnrichedMovieDto>
)

// 🆕 NUOVO: User stats response
data class UserStatsResponse(
    val user_id: String,
    val total_movies: Int,
    val watched_count: Int,
    val watchlist_count: Int,
    val average_rating: Double?
)

//RETROFIT INTERFACE

interface ApiInterface {
    //auth endpoints
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): retrofit2.Response<ApiResponse<AuthResponse>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<ApiResponse<AuthResponse>>

    //health check
    @GET("movies/health")
    suspend fun healthCheck(): retrofit2.Response<ApiResponse<Any>>

    //enrichment automatico
    @POST("movies/enrich")
    suspend fun enrichMovies(@Body request: EnrichRequest): retrofit2.Response<ApiResponse<EnrichmentResponse>>

    // 🔄 MODIFICATO: batch upload con userId
    @POST("movies/batch")
    suspend fun batchUpload(@Body request: BatchUploadRequest): retrofit2.Response<ApiResponse<BatchResponse>>

    // ⚠️ DEPRECATO: recupero tutti i film (usare getUserMovies)
    @GET("movies/all")
    suspend fun getAllMovies(): retrofit2.Response<ApiResponse<MoviesListResponse>>

    // 🆕 NUOVO: recupero film per utente specifico
    @GET("movies/user/{userId}")
    suspend fun getUserMovies(
        @Path("userId") userId: String,
        @Query("status") status: String? = null  // "watched" o "watchlist"
    ): retrofit2.Response<ApiResponse<MoviesListResponse>>

    // 🆕 NUOVO: statistiche utente
    @GET("movies/user/{userId}/stats")
    suspend fun getUserStats(
        @Path("userId") userId: String
    ): retrofit2.Response<ApiResponse<UserStatsResponse>>

    //initialize app
    @GET("movies/initialize")
    suspend fun initializeApp(): retrofit2.Response<ApiResponse<Map<String, Any>>>

    //liste endpoints
    @GET("lists")
    suspend fun getUserLists(): retrofit2.Response<ApiResponse<List<MovieList>>>

    @GET("lists/public")
    suspend fun getPublicLists(@Query("limit") limit: Int): retrofit2.Response<ApiResponse<List<MovieList>>>

    @POST("lists")
    suspend fun createList(@Body request: Map<String, String>): retrofit2.Response<ApiResponse<MovieList>>

    @PUT("lists/{id}")
    suspend fun updateList(
        @Path("id") listId: String,
        @Body request: Map<String, String>
    ): retrofit2.Response<ApiResponse<MovieList>>

    @DELETE("lists/{id}")
    suspend fun deleteList(@Path("id") listId: String): retrofit2.Response<ApiResponse<Any>>

    @POST("lists/{id}/follow")
    suspend fun followList(@Path("id") listId: String): retrofit2.Response<ApiResponse<Any>>
}

//API SERVICE SINGLETON

object ApiService {
    private const val TAG = "ApiService"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    //stato autenticazione
    private var currentToken: String? = null
    private var currentUser: UserInfo? = null

    //okhttp client con interceptor per auth
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(AppConfig.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val originalRequest = chain.request()

            //aggiungi token jwt se disponibile
            val requestBuilder = originalRequest.newBuilder()
                .addHeader("Content-Type", "application/json")

            currentToken?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()
            chain.proceed(request)
        }
        .build()

    //retrofit instance
    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiInterface: ApiInterface = retrofit.create(ApiInterface::class.java)

    //INIZIALIZZAZIONE

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(AppConfig.AUTH_PREFS_NAME, Context.MODE_PRIVATE)

        //carica token salvato
        currentToken = prefs.getString("access_token", null)

        //carica user info salvato
        val userJson = prefs.getString("user_info", null)
        if (userJson != null) {
            try {
                currentUser = gson.fromJson(userJson, UserInfo::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "errore parsing user info: ${e.message}")
            }
        }

        Log.i(TAG, "✅ ApiService inizializzato")
        Log.i(TAG, "   Backend: ${AppConfig.BASE_URL}")
        Log.i(TAG, "   Autenticato: ${isAuthenticated()}")
        if (currentUser != null) {
            Log.i(TAG, "   User: ${currentUser?.email}")
        }
    }

    // 🆕 NUOVO: Ottieni userId corrente
    fun getCurrentUserId(): String? {
        return currentUser?.id
    }

    // 🆕 NUOVO: Verifica se userId è disponibile
    fun hasUserId(): Boolean {
        return currentUser?.id != null
    }

    //AUTH METHODS

    suspend fun register(email: String, password: String, username: String? = null): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📝 Registrazione utente: $email")

                val request = RegisterRequest(email, password, username)
                val response = apiInterface.register(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data!!

                    //salva token
                    currentToken = authData.access_token
                    currentUser = authData.user

                    prefs.edit().apply {
                        putString("access_token", authData.access_token)
                        putString("user_info", gson.toJson(authData.user))
                        apply()
                    }

                    Log.d(TAG, "✅ Registrazione completata: ${authData.user.email}")
                    Result.success(authData)
                } else {
                    val errorMsg = response.body()?.message ?: "registrazione fallita"
                    Log.e(TAG, "❌ $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ errore registrazione: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔐 Login: $email")

                val request = LoginRequest(email, password)
                val response = apiInterface.login(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data!!

                    //salva token
                    currentToken = authData.access_token
                    currentUser = authData.user

                    prefs.edit().apply {
                        putString("access_token", authData.access_token)
                        putString("user_info", gson.toJson(authData.user))
                        apply()
                    }

                    Log.d(TAG, "✅ Login completato: ${authData.user.email}")
                    Result.success(authData)
                } else {
                    val errorMsg = response.body()?.message ?: "login fallito"
                    Log.e(TAG, "❌ $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ errore login: ${e.message}")
                Result.failure(e)
            }
        }

    fun logout() {
        currentToken = null
        currentUser = null

        prefs.edit().apply {
            remove("access_token")
            remove("user_info")
            apply()
        }

        Log.d(TAG, "👋 Logout completato")
    }

    fun isAuthenticated(): Boolean {
        return currentToken != null && currentUser != null
    }

    fun getCurrentUser(): UserInfo? {
        return currentUser
    }

    //CONNECTIVITY

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiInterface.healthCheck()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            false
        }
    }

    // 🔄 MODIFICATO: Batch upload con userId obbligatorio
    suspend fun batchUpload(
        watchlist: List<Movie>,
        watched: List<Movie>
    ): Result<BatchResponse> = withContext(Dispatchers.IO) {
        try {
            // 🆕 Verifica userId
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ userId non disponibile - utente non autenticato")
                return@withContext Result.failure(Exception("Utente non autenticato"))
            }

            Log.d(TAG, "╔═══════════════════════════════════════╗")
            Log.d(TAG, "📦 BATCH UPLOAD")
            Log.d(TAG, "   User ID: $userId")  // 🆕 Log userId
            Log.d(TAG, "   Watchlist: ${watchlist.size} film")
            Log.d(TAG, "   Watched: ${watched.size} film")
            Log.d(TAG, "   Totale: ${watchlist.size + watched.size} film")
            Log.d(TAG, "╚═══════════════════════════════════════╝")

            val watchlistDtos = watchlist.map { movie ->
                MovieDto(
                    id = movie.id,
                    title = movie.title,
                    year = movie.year,
                    userRating = movie.userRating,
                    watchedDate = movie.dateRated,
                    userReview = null,
                    isWatched = false,
                    source = movie.source.name
                )
            }

            val watchedDtos = watched.map { movie ->
                MovieDto(
                    id = movie.id,
                    title = movie.title,
                    year = movie.year,
                    userRating = movie.userRating,
                    watchedDate = movie.dateRated,
                    userReview = null,
                    isWatched = true,
                    source = movie.source.name
                )
            }

            // 🆕 Request con userId
            val request = BatchUploadRequest(
                userId = userId,
                watchlist = watchlistDtos,
                watched = watchedDtos
            )

            Log.d(TAG, "📤 invio batch al backend...")

            val response = apiInterface.batchUpload(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val batchData = response.body()?.data!!

                Log.d(TAG, "✅ BATCH UPLOAD COMPLETATO")
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "Session: ${batchData.sessionId}")
                Log.d(TAG, "Film totali: ${batchData.summary.totalMovies}")
                Log.d(TAG, "Arricchiti: ${batchData.summary.totalEnriched}")
                Log.d(TAG, "Cache hits: ${batchData.summary.cacheHitsTotal}")
                Log.d(TAG, "")
                // 🆕 Log contatori separati
                Log.d(TAG, "📊 CONTATORI DAL FILE:")
                Log.d(TAG, "   Watched: ${batchData.counters.fromFile.watched}")
                Log.d(TAG, "   Watchlist: ${batchData.counters.fromFile.watchlist}")
                Log.d(TAG, "")
                Log.d(TAG, "📊 CONTATORI TOTALI (dopo refresh):")
                Log.d(TAG, "   Watched: ${batchData.counters.afterRefresh.watched}")
                Log.d(TAG, "   Watchlist: ${batchData.counters.afterRefresh.watchlist}")
                Log.d(TAG, "═══════════════════════════════════════")

                Result.success(batchData)
            } else {
                val errorMsg = response.body()?.message ?: "batch upload fallito"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun enrichMoviesAutomatic(movies: List<Movie>): Result<EnrichmentResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "╔═══════════════════════════════════════╗")
                Log.d(TAG, "🎬 ENRICHMENT AUTOMATICO")
                Log.d(TAG, "   Film da processare: ${movies.size}")
                Log.d(TAG, "╚═══════════════════════════════════════╝")

                val movieDtos = movies.map { movie ->
                    MovieDto(
                        id = movie.id,
                        title = movie.title,
                        year = movie.year,
                        userRating = movie.userRating,
                        watchedDate = movie.dateRated,
                        userReview = null,
                        isWatched = movie.isWatched,
                        source = movie.source.name
                    )
                }

                val request = EnrichRequest(movies = movieDtos)

                Log.d(TAG, "📤 invio richiesta enrichment...")

                val response = apiInterface.enrichMovies(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val enrichmentData = response.body()?.data!!

                    Log.d(TAG, "✅ ENRICHMENT COMPLETATO")
                    Log.d(TAG, "═══════════════════════════════════════")
                    Log.d(TAG, "   Session: ${enrichmentData.sessionId}")
                    Log.d(TAG, "   Processati: ${enrichmentData.totalProcessed}")
                    Log.d(TAG, "   Successo: ${enrichmentData.successfulMovies.size}")
                    Log.d(TAG, "   Success rate: ${(enrichmentData.successRate * 100).toInt()}%")
                    Log.d(TAG, "   Cache hits: ${enrichmentData.cacheHits}")
                    Log.d(TAG, "   Falliti: ${enrichmentData.failedMovies.size}")
                    Log.d(TAG, "═══════════════════════════════════════")

                    Result.success(enrichmentData)
                } else {
                    val errorMsg = response.body()?.message ?: "enrichment fallito"
                    val errorBody = response.errorBody()?.string()

                    Log.e(TAG, "❌ ENRICHMENT FALLITO")
                    Log.e(TAG, "   Message: $errorMsg")
                    Log.e(TAG, "   Error body: $errorBody")
                    Log.e(TAG, "═══════════════════════════════════════")

                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ EXCEPTION: ${e.message}", e)
                Log.e(TAG, "═══════════════════════════════════════")
                Result.failure(e)
            }
        }

    //RECUPERO FILM

    // ⚠️ DEPRECATO: Usare getUserStoredMovies invece
    @Deprecated("Usare getUserStoredMovies(userId) per ottenere solo i film dell'utente")
    suspend fun getAllStoredMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📚 richiesta tutti i film dal backend (DEPRECATO)")

            val response = apiInterface.getAllMovies()

            if (response.isSuccessful && response.body()?.success == true) {
                val moviesData = response.body()?.data!!
                val movies = moviesData.movies.map { dto -> dtoToMovie(dto) }

                Log.d(TAG, "✅ recuperati ${movies.size} film dal backend")
                Result.success(movies)
            } else {
                val errorMsg = response.body()?.message ?: "errore recupero film"
                Log.e(TAG, "❌ errore: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore recupero film: ${e.message}")
            Result.failure(e)
        }
    }

    // 🆕 NUOVO: Recupera film dell'utente corrente
    suspend fun getUserStoredMovies(
        status: String? = null  // "watched", "watchlist", o null per tutti
    ): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ userId non disponibile")
                return@withContext Result.failure(Exception("Utente non autenticato"))
            }

            Log.d(TAG, "📚 richiesta film per utente $userId (status: ${status ?: "all"})")

            val response = apiInterface.getUserMovies(userId, status)

            if (response.isSuccessful && response.body()?.success == true) {
                val moviesData = response.body()?.data!!
                val movies = moviesData.movies.map { dto -> dtoToMovie(dto) }

                Log.d(TAG, "✅ recuperati ${movies.size} film per utente $userId")
                Result.success(movies)
            } else {
                val errorMsg = response.body()?.message ?: "errore recupero film utente"
                Log.e(TAG, "❌ errore: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore recupero film utente: ${e.message}")
            Result.failure(e)
        }
    }

    // 🆕 NUOVO: Recupera statistiche utente
    suspend fun getUserStats(): Result<UserStatsResponse> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ userId non disponibile")
                return@withContext Result.failure(Exception("Utente non autenticato"))
            }

            Log.d(TAG, "📊 richiesta statistiche per utente $userId")

            val response = apiInterface.getUserStats(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                val stats = response.body()?.data!!

                Log.d(TAG, "✅ statistiche recuperate:")
                Log.d(TAG, "   Totale: ${stats.total_movies}")
                Log.d(TAG, "   Watched: ${stats.watched_count}")
                Log.d(TAG, "   Watchlist: ${stats.watchlist_count}")

                Result.success(stats)
            } else {
                val errorMsg = response.body()?.message ?: "errore recupero statistiche"
                Log.e(TAG, "❌ errore: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore recupero statistiche: ${e.message}")
            Result.failure(e)
        }
    }

    //LISTE METHODS (invariati)

    suspend fun getUserLists(): Result<List<MovieList>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Richiesta liste utente")

            val response = apiInterface.getUserLists()

            if (response.isSuccessful && response.body()?.success == true) {
                val lists = response.body()?.data ?: emptyList()
                Log.d(TAG, "✅ Ottenute ${lists.size} liste utente")
                Result.success(lists)
            } else {
                val errorMsg = response.body()?.message ?: "Errore recupero liste"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore recupero liste utente: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getPublicLists(limit: Int = 50): Result<List<MovieList>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Richiesta liste pubbliche (limit: $limit)")

            val response = apiInterface.getPublicLists(limit)

            if (response.isSuccessful && response.body()?.success == true) {
                val lists = response.body()?.data ?: emptyList()
                Log.d(TAG, "✅ Ottenute ${lists.size} liste pubbliche")
                Result.success(lists)
            } else {
                val errorMsg = response.body()?.message ?: "Errore recupero liste pubbliche"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore recupero liste pubbliche: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createList(listName: String): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📝 Creazione lista: $listName")

            val request = mapOf("name" to listName)
            val response = apiInterface.createList(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data!!
                Log.d(TAG, "✅ Lista creata: $listName")
                Result.success(list)
            } else {
                val errorMsg = response.body()?.message ?: "Errore creazione lista"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore creazione lista: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateList(listId: String, newName: String): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "✏️ Aggiornamento lista: $listId -> $newName")

            val request = mapOf("name" to newName)
            val response = apiInterface.updateList(listId, request)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data!!
                Log.d(TAG, "✅ Lista aggiornata")
                Result.success(list)
            } else {
                val errorMsg = response.body()?.message ?: "Errore aggiornamento lista"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore aggiornamento lista: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteList(listId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Eliminazione lista: $listId")

            val response = apiInterface.deleteList(listId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ Lista eliminata")
                Result.success(true)
            } else {
                val errorMsg = response.body()?.message ?: "Errore eliminazione lista"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore eliminazione lista: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun followList(listId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "👁️ Follow lista: $listId")

            val response = apiInterface.followList(listId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ Lista seguita")
                Result.success(true)
            } else {
                val errorMsg = response.body()?.message ?: "Errore follow lista"
                Log.e(TAG, "❌ $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore follow lista: ${e.message}")
            Result.failure(e)
        }
    }

    //UTILITY: CONVERSIONE DTO -> MOVIE

    private fun dtoToMovie(dto: EnrichedMovieDto): Movie {
        return Movie(
            id = dto.id,
            title = dto.title,
            year = dto.year,
            director = dto.director,
            genres = dto.genres,
            cast = dto.cast,  // Mappa automaticamente actors -> cast
            overview = dto.overview,
            runtime = dto.runtime,
            userRating = dto.userRating,
            dateRated = dto.watchedDate,
            isWatched = dto.isWatched,
            source = try {
                com.example.movieapp.data.models.DataSource.valueOf(dto.source)
            } catch (e: Exception) {
                com.example.movieapp.data.models.DataSource.UNKNOWN
            },
            tmdbId = dto.tmdbId,
            posterUrl = dto.posterUrl,
            backdropUrl = dto.backdropUrl,
            tmdbRating = dto.tmdbRating,
            voteCount = dto.voteCount
        )
    }

    //DEBUG INFO

    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "backend_host" to AppConfig.BACKEND_HOST,
            "backend_port" to AppConfig.BACKEND_PORT,
            "base_url" to AppConfig.BASE_URL,
            "authenticated" to isAuthenticated(),
            "current_user" to (currentUser?.email ?: "none"),
            "user_id" to (currentUser?.id ?: "none"),  // 🆕 AGGIUNTO userId
            "token_present" to (currentToken != null),
            "config_valid" to AppConfig.isBackendConfigValid()
        )
    }
}