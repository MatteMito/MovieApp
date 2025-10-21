package com.example.movieapp.data.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.MovieList
import com.google.gson.Gson
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

//batch responses
data class BatchUploadRequest(
    val watchlist: List<MovieDto>,
    val watched: List<MovieDto>
)

data class BatchResponse(
    val sessionId: String,
    val watchlistResult: EnrichmentResponse,
    val watchedResult: EnrichmentResponse,
    val summary: BatchSummary
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

    //batch upload
    @POST("movies/batch")
    suspend fun batchUpload(@Body request: BatchUploadRequest): retrofit2.Response<ApiResponse<BatchResponse>>

    //recupero film
    @GET("movies/all")
    suspend fun getAllMovies(): retrofit2.Response<ApiResponse<MoviesListResponse>>

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

        Log.d(TAG, "✅ ApiService inizializzato")
        Log.d(TAG, "Backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
        Log.d(TAG, "Autenticato: ${isAuthenticated()}")
    }

    //AUTHENTICATION

    suspend fun register(
        email: String,
        password: String,
        username: String? = null
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📝 tentativo registrazione: $email")

            val request = RegisterRequest(email, password, username)
            val response = apiInterface.register(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()?.data!!

                //salva token e user
                saveAuthData(authData)

                Log.d(TAG, "✅ registrazione riuscita: $email")
                Result.success(authData)
            } else {
                val errorMsg = response.body()?.message ?: "registrazione fallita"
                Log.e(TAG, "❌ registrazione fallita: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore registrazione: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun login(
        email: String,
        password: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔐 tentativo login: $email")

            val request = LoginRequest(email, password)
            val response = apiInterface.login(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()?.data!!

                //salva token e user
                saveAuthData(authData)

                Log.d(TAG, "✅ login riuscito: $email")
                Result.success(authData)
            } else {
                val errorMsg = response.body()?.message ?: "credenziali non valide"
                Log.e(TAG, "❌ login fallito: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore login: ${e.message}")
            Result.failure(e)
        }
    }

    fun logout() {
        Log.d(TAG, "👋 logout utente: ${currentUser?.email}")

        //pulisci dati autenticazione
        currentToken = null
        currentUser = null

        prefs.edit()
            .remove("access_token")
            .remove("user_info")
            .apply()

        Log.d(TAG, "✅ logout completato")
    }

    fun isAuthenticated(): Boolean {
        return currentToken != null && currentUser != null
    }

    fun getCurrentUser(): UserInfo? {
        return currentUser
    }

    private fun saveAuthData(authData: AuthResponse) {
        currentToken = authData.access_token
        currentUser = authData.user

        prefs.edit()
            .putString("access_token", authData.access_token)
            .putString("user_info", gson.toJson(authData.user))
            .apply()
    }

    //HEALTH CHECK

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🏥 test connessione backend...")

            val response = apiInterface.healthCheck()
            val isHealthy = response.isSuccessful && response.body()?.success == true

            if (isHealthy) {
                Log.d(TAG, "✅ backend raggiungibile e funzionante")
            } else {
                Log.w(TAG, "⚠️ backend non raggiungibile")
            }

            isHealthy
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore connessione: ${e.message}")
            false
        }
    }

    suspend fun checkBackendHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🏥 health check: ${AppConfig.BASE_URL}movies/health")

            val response = apiInterface.healthCheck()

            if (response.isSuccessful) {
                Log.d(TAG, "✅ backend healthy")
                Result.success(true)
            } else {
                Log.e(TAG, "❌ backend unhealthy: ${response.code()}")
                Result.failure(Exception("backend non disponibile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ errore connessione backend: ${e.message}")
            Result.failure(e)
        }
    }

    //ENRICHMENT AUTOMATICO

    /**
     * enrichment automatico film con backend che gestisce tutto il processo di enrichment in modo automatico
     */
    suspend fun enrichMoviesAutomatic(movies: List<Movie>): Result<EnrichmentResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "═══════════════════════════════════")
            Log.d(TAG, "🎬 ENRICHMENT REQUEST START")
            Log.d(TAG, "═══════════════════════════════════")
            Log.d(TAG, "📍 URL: ${AppConfig.BASE_URL}movies/enrich")
            Log.d(TAG, "🔗 Backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
            Log.d(TAG, "📊 Film da arricchire: ${movies.size}")

            if (movies.isEmpty()) {
                Log.w(TAG, "⚠️ Nessun film da arricchire")
                return@withContext Result.failure(Exception("nessun film fornito"))
            }

            // Converti Movie in DTO
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

            // Log primi 3 film
            Log.d(TAG, "📋 Primi film da inviare:")
            movieDtos.take(3).forEach { dto ->
                Log.d(TAG, "   - ${dto.title} (${dto.year}) [${dto.source}]")
            }

            val request = EnrichRequest(movies = movieDtos)

            Log.d(TAG, "📤 Invio richiesta HTTP POST...")

            val response = try {
                apiInterface.enrichMovies(request)
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "❌ ERRORE: Backend non raggiungibile!")
                Log.e(TAG, "   ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
                Log.e(TAG, "   Verifica che npm run start:dev sia attivo")
                return@withContext Result.failure(Exception("Backend non raggiungibile: ${e.message}"))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "❌ TIMEOUT: Backend non risponde")
                return@withContext Result.failure(Exception("Timeout: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERRORE RETE: ${e.javaClass.simpleName}")
                Log.e(TAG, "   ${e.message}")
                e.printStackTrace()
                return@withContext Result.failure(e)
            }

            Log.d(TAG, "📥 Response ricevuta!")
            Log.d(TAG, "   Code: ${response.code()}")
            Log.d(TAG, "   Success: ${response.isSuccessful}")
            Log.d(TAG, "   Body success: ${response.body()?.success}")

            if (response.isSuccessful && response.body()?.success == true) {
                val enrichmentData = response.body()?.data!!

                Log.d(TAG, "✅ ENRICHMENT COMPLETATO:")
                Log.d(TAG, "   Processati: ${enrichmentData.totalProcessed}")
                Log.d(TAG, "   Successi: ${enrichmentData.successfulMovies.size}")
                Log.d(TAG, "   Cache hits: ${enrichmentData.cacheHits}")
                Log.d(TAG, "   Falliti: ${enrichmentData.failedMovies.size}")
                Log.d(TAG, "═══════════════════════════════════")

                Result.success(enrichmentData)
            } else {
                val errorMsg = response.body()?.message ?: "enrichment fallito"
                val errorBody = response.errorBody()?.string()

                Log.e(TAG, "❌ ENRICHMENT FALLITO")
                Log.e(TAG, "   Message: $errorMsg")
                Log.e(TAG, "   Error body: $errorBody")
                Log.e(TAG, "═══════════════════════════════════")

                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION: ${e.message}", e)
            Log.e(TAG, "═══════════════════════════════════")
            Result.failure(e)
        }
    }

    //RECUPERO FILM

    suspend fun getAllStoredMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📚 richiesta tutti i film dal backend")

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

    //LISTE METHODS

    /**
     * Ottiene liste utente
     */
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

    /**
     * Ottiene liste pubbliche
     */
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

    /**
     * Crea nuova lista
     */
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

    /**
     * Aggiorna lista
     */
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

    /**
     * Elimina lista
     */
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

    /**
     * Segui lista pubblica
     */
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

    /**
     * converte enriched movie dto in movie model app
     */
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

    //DEBUG INFO

    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "backend_host" to AppConfig.BACKEND_HOST,
            "backend_port" to AppConfig.BACKEND_PORT,
            "base_url" to AppConfig.BASE_URL,
            "authenticated" to isAuthenticated(),
            "current_user" to (currentUser?.email ?: "none"),
            "token_present" to (currentToken != null),
            "config_valid" to AppConfig.isBackendConfigValid()
        )
    }
}