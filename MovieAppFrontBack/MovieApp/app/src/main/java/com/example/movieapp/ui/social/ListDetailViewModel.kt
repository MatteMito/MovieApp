package com.example.movieapp.ui.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.models.DataSource
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// viewmodel per dettaglio lista condivisa, gestisce crud film e ricerca con arricchimento automatico
class ListDetailViewModel : ViewModel() {
    private val TAG = "ListDetailViewModel"

    // livedata lista corrente
    private val _currentList = MutableLiveData<MovieList>()
    val currentList: LiveData<MovieList> = _currentList

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // livedata per ricerca film da aggiungere
    private val _searchResults = MutableLiveData<List<Movie>>()
    val searchResults: LiveData<List<Movie>> = _searchResults

    private val _searchLoading = MutableLiveData<Boolean>()
    val searchLoading: LiveData<Boolean> = _searchLoading

    // job per cancellare ricerca precedente durante debounce
    private var searchJob: Job? = null

    // carica lista dal backend con opzione force update per aggiornare ui
    fun loadList(listId: String, forceUpdate: Boolean = false) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "carico lista: $listId (userId: $userId, force: $forceUpdate)")

                // passa userid per accedere a liste private
                val response = if (userId != null) {
                    ApiService.apiInterface.getListById(listId, userId)
                } else {
                    ApiService.apiInterface.getListById(listId)
                }

                Log.d(TAG, "response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!

                    // forza aggiornamento ui anche se dati uguali usando postvalue
                    if (forceUpdate) {
                        _currentList.postValue(list)
                    } else {
                        _currentList.value = list
                    }

                    Log.d(TAG, "lista caricata: ${list.name}, ${list.movies.size} film")
                } else {
                    val errorMsg = "errore caricamento lista: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "errorbody: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                _error.value = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione loadlist", e)
            } finally {
                _loading.value = false
            }
        }
    }

    // ricerca film nel database con debounce per ottimizzare chiamate
    fun searchMovies(query: String) {
        // cancella ricerca precedente
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                // debounce 500ms per evitare troppe chiamate durante digitazione
                delay(500)
                _searchLoading.value = true

                Log.d(TAG, "autocomplete database movies: $query")
                // cerca in database "movies" (tutti i film disponibili)
                val response = ApiService.apiInterface.autocompleteMovies(query, 10)

                if (response.isSuccessful && response.body() != null) {
                    val movies = response.body()?.data ?: emptyList()
                    _searchResults.value = movies
                    Log.d(TAG, "trovati ${movies.size} film nel database")
                } else {
                    _searchResults.value = emptyList()
                    Log.e(TAG, "errore autocomplete: ${response.code()}")
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                Log.e(TAG, "eccezione autocomplete", e)
            } finally {
                _searchLoading.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // aggiunge film alla lista con arricchimento automatico se necessario
    fun addMovie(
        listId: String,
        movie: Movie,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                // step 1: verifica se film ha dati tmdb completi
                if (!movie.isEnriched) {
                    Log.d(TAG, "film non arricchito: ${movie.title}, arricchisco prima di aggiungere...")

                    // arricchisci film con dati tmdb prima di aggiungere
                    val enrichResult = enrichMovieBeforeAdding(movie)

                    if (enrichResult == null) {
                        Log.w(TAG, "impossibile arricchire film ${movie.title}, aggiungo comunque")
                    } else {
                        Log.d(TAG, "film arricchito con successo: ${enrichResult.title}")
                    }
                }

                // step 2: aggiungi film alla lista
                Log.d(TAG, "aggiungo film ${movie.id} alla lista $listId")
                val request = com.example.movieapp.data.network.AddMovieToListRequest(movie_id = movie.id)
                val response = ApiService.apiInterface.addMovieToList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "film aggiunto con successo, ricarico lista...")
                    // ricarica lista per mostrare film aggiunto
                    loadList(listId, forceUpdate = true)
                    onSuccess()
                } else {
                    val errorMsg = "errore aggiunta: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione addmovie", e)
                onError("errore: ${e.message}")
            }
        }
    }

    // arricchisce film con dati tmdb chiamando endpoint enrichment
    private suspend fun enrichMovieBeforeAdding(movie: Movie): Movie? {
        return try {
            Log.d(TAG, "chiamata enrichment per ${movie.title}")

            // prepara richiesta enrichment
            val request = com.example.movieapp.data.network.EnrichRequest(
                movies = listOf(
                    com.example.movieapp.data.network.MovieDto(
                        id = movie.id,
                        title = movie.title,
                        year = movie.year,
                        userRating = null,
                        watchedDate = null,
                        userReview = null,
                        isWatched = false,
                        source = movie.source.name
                    )
                )
            )

            val response = ApiService.apiInterface.enrichMovies(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val enrichmentData = response.body()?.data
                val enrichedMovies = enrichmentData?.successfulMovies

                if (!enrichedMovies.isNullOrEmpty()) {
                    val enrichedDto = enrichedMovies[0]
                    Log.d(TAG, "film arricchito: ${enrichedDto.title} (tmdb_id: ${enrichedDto.tmdbId})")

                    // converti dto a movie model
                    dtoToMovie(enrichedDto)
                } else {
                    Log.w(TAG, "nessun film arricchito nella risposta")
                    null
                }
            } else {
                Log.e(TAG, "errore enrichment: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore enrichMovieBeforeAdding", e)
            null
        }
    }

    // converte dto enrichment a model movie
    private fun dtoToMovie(dto: com.example.movieapp.data.network.EnrichedMovieDto): Movie {
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
            voteCount = dto.voteCount,
            productionCountries = dto.productionCountries,
            originalLanguage = dto.originalLanguage,
            popularity = dto.popularity,
            isEnriched = dto.isEnriched
        )
    }

    // rimuove film dalla lista
    fun removeMovie(listId: String, movieId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "rimuovo film $movieId dalla lista $listId")
                val response = ApiService.apiInterface.removeMovieFromList(listId, movieId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "film rimosso, ricarico lista...")
                    // ricarica lista per aggiornare ui
                    loadList(listId, forceUpdate = true)
                    onSuccess()
                } else {
                    val errorMsg = "errore rimozione: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione removemovie", e)
                onError("errore: ${e.message}")
            }
        }
    }

    // aggiorna metadati lista con supporto notifiche
    fun updateList(
        listId: String,
        name: String,
        description: String?,
        isPublic: Boolean,
        targetDate: String? = null,
        frequency: String? = null,
        notificationsEnabled: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "aggiornamento lista $listId (notifiche: $notificationsEnabled)")

                val request = com.example.movieapp.data.network.UpdateListRequest(
                    name = name,
                    description = description,
                    is_public = isPublic,
                    target_date = targetDate,
                    frequency = frequency,
                    notifications_enabled = notificationsEnabled
                )

                val response = ApiService.apiInterface.updateList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista aggiornata con successo, ricarico...")
                    // ricarica lista per mostrare modifiche
                    loadList(listId, forceUpdate = true)
                    onSuccess()
                } else {
                    val errorMsg = "errore aggiornamento: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione updatelist", e)
                onError("errore: ${e.message}")
            }
        }
    }
}