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
import okhttp3.logging.HttpLoggingInterceptor
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
    @SerializedName("tmdb_id")
    val tmdbId: Int?,
    val genres: List<String>,
    val director: String?,
    @SerializedName("actors")
    val cast: List<String>,
    val overview: String?,
    @SerializedName("poster_url")
    val posterUrl: String?,
    @SerializedName("backdrop_url")
    val backdropUrl: String?,
    @SerializedName("tmdb_rating")
    val tmdbRating: Double?,
    @SerializedName("vote_count")
    val voteCount: Int?,
    val runtime: Int?,
    @SerializedName("user_rating")
    val userRating: Double?,
    @SerializedName("watched_date")
    val watchedDate: String?,
    @SerializedName("is_watched")
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
    //alias per compatibilita
    val totalMovies: Int get() = total_movies
    val watchedCount: Int get() = watched_count
    val watchlistCount: Int get() = watchlist_count
    val averageRating: Double get() = average_rating
}

// TMDB
data class AddMovieFromTmdbRequest(
    @SerializedName("tmdb_id")
    val tmdbId: Int,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("status")
    val status: String = "watchlist"
)

data class SyncPopularRequest(
    val limit: Int = 10000
)

data class SyncPopularResponse(
    val synced: Int,
    val errors: Int
)

data class TmdbStatsResponse(
    val total: Int,
    val enriched: Int,
    val notEnriched: Int,
    val withTmdbId: Int
)

// LISTS
data class CreateListRequest(
    val user_id: String,
    val name: String,
    val description: String? = null,
    val is_public: Boolean = false,
    val movie_ids: List<String> = emptyList()
)

data class UpdateListRequest(
    val name: String? = null,
    val description: String? = null,
    val is_public: Boolean? = null,
    val movie_ids: List<String>? = null
)

data class AddMovieToListRequest(
    val movie_id: String
)

data class CopyListRequest(
    val userId: String,
    val newName: String? = null
)

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

    @DELETE("movies/user/{userId}/all")
    suspend fun deleteAllUserMovies(@Path("userId") userId: String): Response<ApiResponse<Unit>>

    @DELETE("movies/all")
    suspend fun deleteAllMovies(
        @Header("x-user-id") userId: String
    ): Response<ApiResponse<Any>>

    // TMDB
    @GET("tmdb/search")
    suspend fun searchTmdb(
        @Query("query") query: String,
        @Query("year") year: Int? = null
    ): Response<ApiResponse<Movie>>

    @POST("tmdb/add-to-database")
    suspend fun addMovieFromTmdb(
        @Body request: AddMovieFromTmdbRequest
    ): Response<ApiResponse<Movie>>

    @POST("tmdb/sync-popular")
    suspend fun syncPopularMovies(
        @Body request: SyncPopularRequest
    ): Response<ApiResponse<SyncPopularResponse>>

    @GET("tmdb/autocomplete")
    suspend fun autocompleteMovies(
        @Query("query") query: String,
        @Query("limit") limit: Int = 10
    ): Response<ApiResponse<List<Movie>>>

    @GET("tmdb/stats")
    suspend fun getTmdbStats(): Response<ApiResponse<TmdbStatsResponse>>

    // LISTS
    @POST("lists")
    suspend fun createList(
        @Body request: CreateListRequest
    ): Response<MovieList>

    @PUT("lists/{id}")
    suspend fun updateList(
        @Path("id") listId: String,
        @Body request: UpdateListRequest
    ): Response<MovieList>

    @GET("lists/my")
    suspend fun getMyLists(
        @Query("userId") userId: String
    ): Response<List<MovieList>>

    @GET("lists/public")
    suspend fun getPublicLists(
        @Query("limit") limit: Int = 20,
        @Query("userId") userId: String? = null
    ): Response<List<MovieList>>

    @GET("lists/{id}")
    suspend fun getListById(
        @Path("id") listId: String
    ): Response<MovieList>

    @DELETE("lists/{id}")
    suspend fun deleteList(
        @Path("id") listId: String,
        @Query("userId") userId: String
    ): Response<Any>

    @POST("lists/{id}/movies")
    suspend fun addMovieToList(
        @Path("id") listId: String,
        @Body request: AddMovieToListRequest,
        @Query("userId") userId: String
    ): Response<MovieList>

    @DELETE("lists/{id}/movies/{movieId}")
    suspend fun removeMovieFromList(
        @Path("id") listId: String,
        @Path("movieId") movieId: String,
        @Query("userId") userId: String
    ): Response<MovieList>

    @POST("lists/{id}/follow")
    suspend fun followList(
        @Path("id") listId: String,
        @Body body: Map<String, String>
    ): Response<Any>

    @DELETE("lists/{id}/follow")
    suspend fun unfollowList(
        @Path("id") listId: String,
        @Query("userId") userId: String
    ): Response<Any>

    @GET("lists/{id}/followers")
    suspend fun getListFollowers(
        @Path("id") listId: String
    ): Response<List<UserInfo>>

    @POST("lists/{id}/copy")
    suspend fun copyList(
        @Path("id") listId: String,
        @Body body: CopyListRequest
    ): Response<MovieList>
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

    lateinit var apiInterface: ApiInterface
        private set

    private val okHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                currentToken?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                requestBuilder.header("Accept", "application/json")
                requestBuilder.header("Content-Type", "application/json")

                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .connectTimeout(AppConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    // ============================================
    // INIZIALIZZAZIONE
    // ============================================

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(AppConfig.AUTH_PREFS_NAME, Context.MODE_PRIVATE)

        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiInterface = retrofit.create(ApiInterface::class.java)

        currentToken = prefs.getString("access_token", null)

        val userJson = prefs.getString("user_info", null)
        if (userJson != null) {
            try {
                currentUser = gson.fromJson(userJson, UserInfo::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "errore parsing user info: ${e.message}")
            }
        }

        Log.i(TAG, "apiservice inizializzato")
        Log.i(TAG, "backend: ${AppConfig.BASE_URL}")
        Log.i(TAG, "autenticato: ${isAuthenticated()}")
        if (currentUser != null) {
            Log.i(TAG, "user: ${currentUser?.email}")
        }
    }

    fun getCurrentUserId(): String? = currentUser?.id
    fun hasUserId(): Boolean = currentUser?.id != null
    fun isAuthenticated(): Boolean = currentToken != null && currentUser != null
    fun getCurrentUser(): UserInfo? = currentUser
    fun getToken(): String? = currentToken

    // ============================================
    // AUTH METHODS
    // ============================================

    suspend fun register(email: String, password: String, username: String? = null): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "registrazione utente: $email")

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

                    Log.d(TAG, "registrazione completata: ${authData.user.email}")
                    Result.success(authData)
                } else {
                    val errorMsg = response.body()?.message ?: "registrazione fallita"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore registrazione: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun login(email: String, password: String): Result<AuthResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "login: $email")

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

                    Log.d(TAG, "login completato: ${authData.user.email}")
                    Result.success(authData)
                } else {
                    val errorMsg = response.body()?.message ?: "login fallito"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore login: ${e.message}")
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

        Log.d(TAG, "logout completato")
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
    // TMDB METHODS
    // ============================================

    suspend fun searchMovieOnTmdb(query: String, year: Int? = null): Result<Movie> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "ricerca tmdb: $query${if (year != null) " ($year)" else ""}")

            val response = apiInterface.searchTmdb(query, year)

            if (response.isSuccessful && response.body()?.success == true) {
                val movie = response.body()?.data
                    ?: return@withContext Result.failure(Exception("film non trovato su tmdb"))

                Log.d(TAG, "film trovato su tmdb: ${movie.title}")
                Result.success(movie)
            } else {
                val error = response.body()?.message ?: "nessun film trovato su tmdb"
                Log.w(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore ricerca tmdb", e)
            Result.failure(e)
        }
    }

    suspend fun addMovieFromTmdb(tmdbId: Int, userId: String, status: String = "watchlist"): Result<Movie> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "aggiunta film da tmdb: $tmdbId")

            val request = AddMovieFromTmdbRequest(
                tmdbId = tmdbId,
                userId = userId,
                status = status
            )

            val response = apiInterface.addMovieFromTmdb(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val movie = response.body()?.data
                    ?: return@withContext Result.failure(Exception("errore aggiunta film"))

                Log.d(TAG, "film aggiunto al database: ${movie.title}")
                Result.success(movie)
            } else {
                val error = response.body()?.message ?: "errore aggiunta film da tmdb"
                Log.w(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore addmovie fromtmdb", e)
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
                Log.e(TAG, "userid non disponibile - utente non autenticato")
                return@withContext Result.failure(Exception("utente non autenticato"))
            }

            Log.d(TAG, "batch upload")
            Log.d(TAG, "user id: $userId")
            Log.d(TAG, "watchlist: ${watchlist.size} film")
            Log.d(TAG, "watched: ${watched.size} film")
            Log.d(TAG, "totale: ${watchlist.size + watched.size} film")

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

            Log.d(TAG, "invio batch al backend...")

            val response = apiInterface.batchUpload(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val batchData = response.body()?.data!!

                Log.d(TAG, "batch upload completato")
                Log.d(TAG, "session: ${batchData.sessionId}")
                Log.d(TAG, "film totali: ${batchData.summary.totalMovies}")
                Log.d(TAG, "arricchiti: ${batchData.summary.totalEnriched}")
                Log.d(TAG, "cache hits: ${batchData.summary.cacheHitsTotal}")

                Result.success(batchData)
            } else {
                val errorMsg = response.body()?.message ?: "batch upload fallito"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun enrichMoviesAutomatic(movies: List<Movie>): Result<EnrichmentResponse> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "enrichment automatico")
                Log.d(TAG, "film da processare: ${movies.size}")

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

                Log.d(TAG, "invio richiesta enrichment...")

                val response = apiInterface.enrichMovies(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val enrichmentData = response.body()?.data!!

                    Log.d(TAG, "enrichment completato")
                    Log.d(TAG, "session: ${enrichmentData.sessionId}")
                    Log.d(TAG, "processati: ${enrichmentData.totalProcessed}")
                    Log.d(TAG, "successo: ${enrichmentData.successfulMovies.size}")
                    Log.d(TAG, "success rate: ${(enrichmentData.successRate * 100).toInt()}%")
                    Log.d(TAG, "cache hits: ${enrichmentData.cacheHits}")
                    Log.d(TAG, "falliti: ${enrichmentData.failedMovies.size}")

                    Result.success(enrichmentData)
                } else {
                    val errorMsg = response.body()?.message ?: "enrichment fallito"
                    val errorBody = response.errorBody()?.string()

                    Log.e(TAG, "enrichment fallito")
                    Log.e(TAG, "message: $errorMsg")
                    Log.e(TAG, "error body: $errorBody")

                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "exception: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun getUserStoredMovies(
        status: String? = null
    ): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "userid non disponibile")
                return@withContext Result.failure(Exception("utente non autenticato"))
            }

            Log.d(TAG, "richiesta film per utente $userId (status: ${status ?: "all"})")

            val response = apiInterface.getUserMovies(userId, status)

            if (response.isSuccessful && response.body()?.success == true) {
                val moviesData = response.body()?.data!!
                val movies = moviesData.movies.map { dto -> dtoToMovie(dto) }

                Log.d(TAG, "recuperati ${movies.size} film per utente $userId")
                Result.success(movies)
            } else {
                val errorMsg = response.body()?.message ?: "errore recupero film utente"
                Log.e(TAG, "errore: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore recupero film utente: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUserStats(): Result<UserStatsResponse> {
        return try {
            val userId = getCurrentUserId()

            if (userId == null) {
                Log.e(TAG, "userid mancante per getstats")
                return Result.failure(Exception("userid mancante"))
            }

            Log.d(TAG, "richiesta stats per utente: $userId")

            val response = apiInterface.getUserStats(userId)

            if (response.isSuccessful) {
                val body = response.body()

                if (body?.success == true && body.data != null) {
                    Log.d(TAG, "stats ricevute:")
                    Log.d(TAG, "totali: ${body.data.totalMovies}")
                    Log.d(TAG, "visti: ${body.data.watchedCount}")
                    Log.d(TAG, "da vedere: ${body.data.watchlistCount}")
                    Log.d(TAG, "rating medio: ${body.data.averageRating}")

                    Result.success(body.data)
                } else {
                    val errorMsg = body?.message ?: "risposta vuota"
                    Log.e(TAG, "errore stats: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = "http ${response.code()}: ${response.message()}"
                Log.e(TAG, "errore http stats: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "eccezione getstats", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserMovies(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "userid non disponibile per deleteallusermovies")
                return@withContext Result.failure(Exception("utente non autenticato"))
            }

            Log.d(TAG, "eliminazione tutti i film per user $userId")

            //chiama DELETE /movies/user/{userId}/all
            val response = apiInterface.deleteAllUserMovies(userId)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "tutti i film eliminati con successo")
                Result.success(Unit)
            } else {
                val error = response.body()?.message ?: "errore eliminazione film"
                Log.w(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore deleteallusermovies", e)
            Result.failure(e)
        }
    }

    // ============================================
    // LISTS METHODS
    // ============================================

    suspend fun createList(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
        movieIds: List<String> = emptyList()
    ): Result<MovieList> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()

            if (userId == null) {
                Log.e(TAG, "userid e null!")
                return@withContext Result.failure(Exception("utente non autenticato. effettua nuovamente il login."))
            }

            Log.d(TAG, "creazione lista: $name")

            val request = CreateListRequest(
                user_id = userId,
                name = name,
                description = description,
                is_public = isPublic,
                movie_ids = movieIds
            )

            val response = apiInterface.createList(request)

            if (response.isSuccessful) {
                val list = response.body()
                    ?: return@withContext Result.failure(Exception("dati lista non presenti nella risposta"))

                Log.d(TAG, "lista creata con successo!")
                Log.d(TAG, "id: ${list.id}")
                Log.d(TAG, "nome: ${list.name}")
                Result.success(list)
            } else {
                val error = response.message() ?: "errore creazione lista"
                val errorBody = response.errorBody()?.string()

                Log.e(TAG, "errore creazione lista")
                Log.e(TAG, "messaggio: $error")
                if (errorBody != null) {
                    Log.e(TAG, "error body: $errorBody")
                }

                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "eccezione durante createlist:", e)
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
            Log.d(TAG, "aggiornamento lista $listId")

            val request = UpdateListRequest(
                name = name,
                description = description,
                is_public = isPublic
            )

            val response = apiInterface.updateList(listId, request)

            if (response.isSuccessful) {
                val list = response.body()
                    ?: return@withContext Result.failure(Exception("errore aggiornamento lista"))

                Log.d(TAG, "lista aggiornata: $listId")
                Result.success(list)
            } else {
                val error = response.message() ?: "errore aggiornamento lista"
                Log.w(TAG, error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore updatelist", e)
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
            actors = dto.cast,
            overview = dto.overview,
            runtime = dto.runtime,
            userRating = dto.userRating,
            dateRated = dto.watchedDate,
            isWatched = dto.isWatched,
            source = try {
                DataSource.valueOf(dto.source)
            } catch (e: Exception) {
                DataSource.UNKNOWN
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