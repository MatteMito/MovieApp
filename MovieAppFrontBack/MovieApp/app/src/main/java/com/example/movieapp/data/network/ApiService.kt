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

// DATA CLASSES PER RISPOSTE BACKEND
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
    val genres: List<String> = emptyList(),
    val director: String?,
    @SerializedName("actors")
    val cast: List<String> = emptyList(),
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

    //campo status dal backend (watched/watchlist)
    @SerializedName("status")
    val status: String? = null,

    val source: String,

    @SerializedName("production_countries")
    val productionCountries: List<String> = emptyList(),

    @SerializedName("original_language")
    val originalLanguage: String? = null,

    @SerializedName("popularity")
    val popularity: Double? = null,

    @SerializedName("is_enriched")
    val isEnriched: Boolean = false
) {
    //calcola iswatched dal campo status
    val isWatched: Boolean
        get() = status == "watched"
}

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

// RETROFIT INTERFACE
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

    @GET("movies/initialize")
    suspend fun initializeApp(): Response<ApiResponse<Map<String, Any>>>

    @GET("tmdb/autocomplete")
    suspend fun autocompleteMovies(
        @Query("query") query: String,
        @Query("limit") limit: Int = 10
    ): Response<ApiResponse<List<Movie>>>

    // LISTS
    @POST("lists")
    suspend fun createList(
        @Body request: CreateListRequest
    ): Response<MovieList>

    @PUT("lists/{id}")
    suspend fun updateList(
        @Path("id") listId: String,
        @Body request: UpdateListRequest,
        @Query("userId") userId: String
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
        @Path("id") listId: String,
        @Query("userId") userId: String? = null
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

    @POST("lists/{id}/copy")
    suspend fun copyList(
        @Path("id") listId: String,
        @Body body: CopyListRequest
    ): Response<MovieList>
}


// API SERVICE SINGLETON
object ApiService {

    private const val TAG = "ApiService"
    private val gson = Gson()

    private lateinit var prefs: SharedPreferences
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

    // INIZIALIZZAZIONE
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
    fun isAuthenticated(): Boolean = currentToken != null && currentUser != null
    fun getCurrentUser(): UserInfo? = currentUser

    // AUTH METHODS
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

    // CONNECTIVITY
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiInterface.healthCheck()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            false
        }
    }

    // MOVIES METHODS
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

    // UTILITY
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
            status = dto.status,
            source = try {
                DataSource.valueOf(dto.source)
            } catch (e: Exception) {
                DataSource.UNKNOWN
            },
            tmdbId = dto.tmdbId,
            posterUrl = dto.posterUrl,
            backdropUrl = dto.backdropUrl,
            tmdbRating = dto.tmdbRating,
            voteCount = dto.voteCount,
            productionCountries = dto.productionCountries,
            originalLanguage = dto.originalLanguage,
            popularity = dto.popularity,
            isEnriched = true
        )
    }

    //initialize app
    suspend fun initializeApp(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "chiamata initialize app...")

            val response = apiInterface.initializeApp()

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data!!

                Log.d(TAG, "initialize app completato")
                Log.d(TAG, "needs sync: ${data["needsSync"]}")
                Log.d(TAG, "movies in db: ${data["moviesInDb"]}")
                Log.d(TAG, "message: ${data["message"]}")

                Result.success(data)
            } else {
                val errorMsg = response.body()?.message ?: "errore inizializzazione"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore initializeapp", e)
            Result.failure(e)
        }
    }
}