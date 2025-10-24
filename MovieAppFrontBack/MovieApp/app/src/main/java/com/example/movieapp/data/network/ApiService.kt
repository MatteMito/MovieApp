package com.example.movieapp.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ============================================
// DATA CLASSES PER RISPOSTE BACKEND
// ============================================

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val timestamp: String?
)

// AUTH
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

// MOVIES
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

// ENRICHMENT
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
    @SerializedName("actors")
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

// BATCH
data class BatchUploadRequest(
    val userId: String,
    val watchlist: List<MovieDto>,
    val watched: List<MovieDto>
)

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

data class BatchResponse(
    val sessionId: String,
    val watchlistResult: EnrichmentResponse,
    val watchedResult: EnrichmentResponse,
    val summary: BatchSummary,
    val counters: ImportCounters
)

data class BatchSummary(
    val totalMovies: Int,
    val watchlistCount: Int,
    val watchedCount: Int,
    val totalEnriched: Int,
    val overallSuccessRate: Double,
    val cacheHitsTotal: Int
)

data class MoviesListResponse(
    val movies: List<EnrichedMovieDto>
)

data class UserStatsResponse(
    val user_id: String,
    val total_movies: Int,
    val watched_count: Int,
    val watchlist_count: Int,
    val average_rating: Double
) {
    // Alias per compatibilità
    val totalMovies: Int get() = total_movies
    val watchedCount: Int get() = watched_count
    val watchlistCount: Int get() = watchlist_count
    val averageRating: Double get() = average_rating
}

// ============================================
// RETROFIT INTERFACE
// ============================================

interface ApiInterface {

    // AUTH
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    // MOVIES
    @GET("movies/health")
    suspend fun healthCheck(): Response<ApiResponse<Any>>

    @POST("movies/enrich")
    suspend fun enrichMovies(@Body request: EnrichRequest): Response<ApiResponse<EnrichmentResponse>>

    @POST("movies/batch")
    suspend fun batchUpload(@Body request: BatchUploadRequest): Response<ApiResponse<BatchResponse>>

    @GET("movies/user/{userId}")
    suspend fun getUserMovies(
        @Path("userId") userId: String,
        @Query("status") status: String? = null
    ): Response<ApiResponse<MoviesListResponse>>

    @GET("movies/user/{userId}/stats")
    suspend fun getUserStats(
        @Path("userId") userId: String
    ): Response<ApiResponse<UserStatsResponse>>

    @GET("movies/initialize")
    suspend fun initializeApp(): Response<ApiResponse<Map<String, Any>>>

    // LISTE
    @GET("lists")
    suspend fun getUserLists(
        @Query("userId") userId: String
    ): Response<ApiResponse<List<MovieList>>>

    @GET("lists/public")
    suspend fun getPublicLists(
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<MovieList>>>

    @GET("lists/{id}")
    suspend fun getListById(
        @Path("id") listId: String
    ): Response<ApiResponse<MovieList>>

    @POST("lists")
    suspend fun createList(
        @Body request: CreateListRequest
    ): Response<ApiResponse<MovieList>>

    @PUT("lists/{id}")
    suspend fun updateList(
        @Path("id") listId: String,
        @Body request: UpdateListRequest
    ): Response<ApiResponse<MovieList>>

    @DELETE("lists/{id}")
    suspend fun deleteList(
        @Path("id") listId: String
    ): Response<ApiResponse<Any>>

    @POST("lists/{id}/movies")
    suspend fun addMovieToList(
        @Path("id") listId: String,
        @Body request: AddMovieToListRequest
    ): Response<ApiResponse<MovieList>>

    @DELETE("lists/{id}/movies/{movieId}")
    suspend fun removeMovieFromList(
        @Path("id") listId: String,
        @Path("movieId") movieId: String
    ): Response<ApiResponse<MovieList>>

    @DELETE("movies/user/{userId}/all")
    suspend fun deleteAllUserMovies(@Path("userId") userId: String): Response<ApiResponse<Unit>>

    @POST("lists/{id}/follow")
    suspend fun followList(
        @Path("id") listId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<MovieList>>
}

// ============================================
// API SERVICE SINGLETON
// ============================================

object ApiService {
    private const val TAG = "ApiService"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private var currentToken: String? = null
    private var currentUser: UserInfo? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(AppConfig.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(AppConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .addHeader("Content-Type", "application/json")

            currentToken?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiInterface: ApiInterface = retrofit.create(ApiInterface::class.java)

    // ============================================
    // INIZIALIZZAZIONE
    // ============================================

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(AppConfig.AUTH_PREFS_NAME, Context.MODE_PRIVATE)

        currentToken = prefs.getString("access_token", null)

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

    fun getCurrentUserId(): String? = currentUser?.id
    fun hasUserId(): Boolean = currentUser?.id != null
    fun isAuthenticated(): Boolean = currentToken != null && currentUser != null
    fun getCurrentUser(): UserInfo? = currentUser

    // ============================================
    // AUTH METHODS
    // ============================================

    suspend fun register(email: String, password: String, username: String? = null): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📝 Registrazione utente: $email")

                val request = RegisterRequest(email, password, username)
                val response = apiInterface.register(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data!!

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

    // ============================================
    // CONNECTIVITY
    // ============================================

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiInterface.healthCheck()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            false
        }
    }

    // ============================================
    // LISTE METHODS
    // ============================================

    suspend fun deleteAllUserMovies(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Eliminazione tutti i film per utente: $userId")

            val response = apiInterface.deleteAllUserMovies(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ Film eliminati dal backend")
                Result.success(Unit)
            } else {
                val error = response.body()?.message ?: "Errore eliminazione"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore deleteAllUserMovies", e)
            Result.failure(e)
        }
    }

    suspend fun getUserLists(): Result<List<MovieList>> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Utente non autenticato"))

            Log.d(TAG, "📋 Recupero liste per utente $userId")

            val response = apiInterface.getUserLists(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                val lists = response.body()?.data ?: emptyList()
                Log.d(TAG, "✅ ${lists.size} liste recuperate")
                Result.success(lists)
            } else {
                val error = response.body()?.message ?: "Errore recupero liste"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore getUserLists", e)
            Result.failure(e)
        }
    }

    suspend fun getPublicLists(limit: Int = 20): Result<List<MovieList>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌍 Recupero liste pubbliche (limit: $limit)")

            val response = apiInterface.getPublicLists(limit)

            if (response.isSuccessful && response.body()?.success == true) {
                val lists = response.body()?.data ?: emptyList()
                Log.d(TAG, "✅ ${lists.size} liste pubbliche recuperate")
                Result.success(lists)
            } else {
                val error = response.body()?.message ?: "Errore recupero liste pubbliche"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore getPublicLists", e)
            Result.failure(e)
        }
    }

    suspend fun getListById(listId: String): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📄 Recupero dettaglio lista $listId")

            val response = apiInterface.getListById(listId)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data
                    ?: return@withContext Result.failure(Exception("Lista non trovata"))

                Log.d(TAG, "✅ Lista recuperata: ${list.name} (${list.movieCount} film)")
                Result.success(list)
            } else {
                val error = response.body()?.message ?: "Errore recupero lista"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore getListById", e)
            Result.failure(e)
        }
    }

    suspend fun createList(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
        movieIds: List<String> = emptyList()
    ): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            // 🔍 LOGGING DETTAGLIATO
            Log.d(TAG, "========================================")
            Log.d(TAG, "📝 CREAZIONE LISTA - INIZIO")
            Log.d(TAG, "   Nome: $name")
            Log.d(TAG, "   Pubblica: $isPublic")
            Log.d(TAG, "   Descrizione: ${description ?: "nessuna"}")
            Log.d(TAG, "========================================")

            // 🔍 Verifica stato autenticazione
            Log.d(TAG, "🔐 Verifica autenticazione:")
            Log.d(TAG, "   currentToken: ${if (currentToken != null) "PRESENTE" else "NULL"}")
            Log.d(TAG, "   currentUser: ${if (currentUser != null) "PRESENTE" else "NULL"}")
            Log.d(TAG, "   currentUser.id: ${currentUser?.id ?: "NULL"}")
            Log.d(TAG, "   currentUser.email: ${currentUser?.email ?: "NULL"}")
            Log.d(TAG, "   isAuthenticated(): ${isAuthenticated()}")
            Log.d(TAG, "   hasUserId(): ${hasUserId()}")

            val userId = getCurrentUserId()

            if (userId == null) {
                Log.e(TAG, "❌ ERRORE: userId è NULL!")
                Log.e(TAG, "   Possibili cause:")
                Log.e(TAG, "   1. Login non completato correttamente")
                Log.e(TAG, "   2. Dati utente non salvati in SharedPreferences")
                Log.e(TAG, "   3. ApiService.initialize() non chiamato dopo login")

                // 🔍 Verifica SharedPreferences
                val savedToken = prefs.getString("access_token", null)
                val savedUserJson = prefs.getString("user_info", null)
                Log.d(TAG, "📦 Contenuto SharedPreferences:")
                Log.d(TAG, "   access_token: ${if (savedToken != null) "PRESENTE" else "NULL"}")
                Log.d(TAG, "   user_info: ${if (savedUserJson != null) "PRESENTE" else "NULL"}")
                if (savedUserJson != null) {
                    Log.d(TAG, "   user_info JSON: $savedUserJson")
                }

                return@withContext Result.failure(Exception("❌ Utente non autenticato. Effettua nuovamente il login."))
            }

            Log.d(TAG, "✅ UserId trovato: $userId")
            Log.d(TAG, "🌐 Invio richiesta al backend...")
            Log.d(TAG, "   URL: ${AppConfig.BASE_URL}lists")

            val request = CreateListRequest(
                userId = userId,
                name = name,
                description = description,
                isPublic = isPublic,
                movieIds = movieIds
            )

            Log.d(TAG, "📤 Request body:")
            Log.d(TAG, "   userId: ${request.userId}")
            Log.d(TAG, "   name: ${request.name}")
            Log.d(TAG, "   description: ${request.description}")
            Log.d(TAG, "   isPublic: ${request.isPublic}")
            Log.d(TAG, "   movieIds: ${request.movieIds}")

            val response = apiInterface.createList(request)

            Log.d(TAG, "📥 Risposta ricevuta:")
            Log.d(TAG, "   isSuccessful: ${response.isSuccessful}")
            Log.d(TAG, "   code: ${response.code()}")
            Log.d(TAG, "   message: ${response.message()}")

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data
                    ?: return@withContext Result.failure(Exception("Dati lista non presenti nella risposta"))

                Log.d(TAG, "✅ LISTA CREATA CON SUCCESSO!")
                Log.d(TAG, "   ID: ${list.id}")
                Log.d(TAG, "   Nome: ${list.name}")
                Log.d(TAG, "========================================")
                Result.success(list)
            } else {
                val error = response.body()?.message ?: response.message() ?: "Errore creazione lista"
                val errorBody = response.errorBody()?.string()

                Log.e(TAG, "❌ ERRORE CREAZIONE LISTA")
                Log.e(TAG, "   Messaggio: $error")
                if (errorBody != null) {
                    Log.e(TAG, "   Error Body: $errorBody")
                }
                Log.e(TAG, "========================================")

                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ECCEZIONE durante createList:", e)
            Log.e(TAG, "   Tipo: ${e.javaClass.simpleName}")
            Log.e(TAG, "   Messaggio: ${e.message}")
            Log.e(TAG, "   Stack trace:")
            e.printStackTrace()
            Log.e(TAG, "========================================")
            Result.failure(e)
        }
    }

    suspend fun updateList(
        listId: String,
        name: String? = null,
        description: String? = null,
        isPublic: Boolean? = null
    ): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "✏️ Aggiornamento lista $listId")

            val request = UpdateListRequest(
                name = name,
                description = description,
                isPublic = isPublic
            )

            val response = apiInterface.updateList(listId, request)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data
                    ?: return@withContext Result.failure(Exception("Errore aggiornamento lista"))

                Log.d(TAG, "✅ Lista aggiornata: $listId")
                Result.success(list)
            } else {
                val error = response.body()?.message ?: "Errore aggiornamento lista"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore updateList", e)
            Result.failure(e)
        }
    }

    suspend fun deleteList(listId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Eliminazione lista $listId")

            val response = apiInterface.deleteList(listId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ Lista eliminata: $listId")
                Result.success(true)
            } else {
                val error = response.body()?.message ?: "Errore eliminazione lista"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore deleteList", e)
            Result.failure(e)
        }
    }

    suspend fun addMovieToList(listId: String, movieId: String): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "➕ Aggiunta film $movieId a lista $listId")

            val request = AddMovieToListRequest(movieId)
            val response = apiInterface.addMovieToList(listId, request)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data
                    ?: return@withContext Result.failure(Exception("Errore aggiunta film"))

                Log.d(TAG, "✅ Film aggiunto a lista")
                Result.success(list)
            } else {
                val error = response.body()?.message ?: "Errore aggiunta film"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore addMovieToList", e)
            Result.failure(e)
        }
    }

    suspend fun removeMovieFromList(listId: String, movieId: String): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "➖ Rimozione film $movieId da lista $listId")

            val response = apiInterface.removeMovieFromList(listId, movieId)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data
                    ?: return@withContext Result.failure(Exception("Errore rimozione film"))

                Log.d(TAG, "✅ Film rimosso da lista")
                Result.success(list)
            } else {
                val error = response.body()?.message ?: "Errore rimozione film"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore removeMovieFromList", e)
            Result.failure(e)
        }
    }

    suspend fun followList(listId: String): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Utente non autenticato"))

            Log.d(TAG, "👥 Follow lista $listId")

            val request = mapOf("userId" to userId)
            val response = apiInterface.followList(listId, request)

            if (response.isSuccessful && response.body()?.success == true) {
                val list = response.body()?.data
                    ?: return@withContext Result.failure(Exception("Errore follow lista"))

                Log.d(TAG, "✅ Lista seguita")
                Result.success(list)
            } else {
                val error = response.body()?.message ?: "Errore follow lista"
                Log.w(TAG, "⚠️ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore followList", e)
            Result.failure(e)
        }
    }

    // ============================================
    // MOVIES METHODS
    // ============================================

    suspend fun batchUpload(
        watchlist: List<Movie>,
        watched: List<Movie>
    ): Result<BatchResponse> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ userId non disponibile - utente non autenticato")
                return@withContext Result.failure(Exception("Utente non autenticato"))
            }

            Log.d(TAG, "╔═══════════════════════════════════════╗")
            Log.d(TAG, "📦 BATCH UPLOAD")
            Log.d(TAG, "   User ID: $userId")
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

    /**
     * ✅ Recupera i film dell'utente corrente
     * Questo è l'UNICO metodo da usare per recuperare i film
     */
    suspend fun getUserStoredMovies(
        status: String? = null
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

    /**
     * Ottieni statistiche utente (veloce - solo contatori)
     */
    suspend fun getUserStats(): Result<UserStatsResponse> {
        return try {
            val userId = getCurrentUserId()

            if (userId == null) {
                Log.e(TAG, "❌ userId mancante per getUserStats")
                return Result.failure(Exception("userId mancante"))
            }

            Log.d(TAG, "📊 Richiesta stats per utente: $userId")

            val response = apiInterface.getUserStats(userId)

            if (response.isSuccessful) {
                val body = response.body()

                if (body?.success == true && body.data != null) {
                    Log.d(TAG, "✅ Stats ricevute:")
                    Log.d(TAG, "   Totali: ${body.data.totalMovies}")
                    Log.d(TAG, "   Visti: ${body.data.watchedCount}")
                    Log.d(TAG, "   Da vedere: ${body.data.watchlistCount}")
                    Log.d(TAG, "   Rating medio: ${body.data.averageRating}")

                    Result.success(body.data)
                } else {
                    val errorMsg = body?.message ?: "Risposta vuota"
                    Log.e(TAG, "❌ Errore stats: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, "❌ Errore HTTP stats: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Eccezione getUserStats", e)
            Result.failure(e)
        }
    }

    // ============================================
    // UTILITY
    // ============================================

    private fun dtoToMovie(dto: EnrichedMovieDto): Movie {
        return Movie(
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

    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "backend_host" to AppConfig.BACKEND_HOST,
            "backend_port" to AppConfig.BACKEND_PORT,
            "base_url" to AppConfig.BASE_URL,
            "authenticated" to isAuthenticated(),
            "current_user" to (currentUser?.email ?: "none"),
            "user_id" to (currentUser?.id ?: "none"),
            "token_present" to (currentToken != null),
            "config_valid" to AppConfig.isBackendConfigValid()
        )
    }
}