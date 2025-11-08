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

class ListDetailViewModel : ViewModel() {
    private val TAG = "ListDetailViewModel"

    //livedata
    private val _currentList = MutableLiveData<MovieList>()
    val currentList: LiveData<MovieList> = _currentList

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    //autocomplete
    private val _searchResults = MutableLiveData<List<Movie>>()
    val searchResults: LiveData<List<Movie>> = _searchResults

    private val _searchLoading = MutableLiveData<Boolean>()
    val searchLoading: LiveData<Boolean> = _searchLoading

    private var searchJob: Job? = null

    //carica lista con forza aggiornamento
    fun loadList(listId: String, forceUpdate: Boolean = false) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "carico lista: $listId (userId: $userId, force: $forceUpdate)")

                //passa userid per accesso liste private
                val response = if (userId != null) {
                    ApiService.apiInterface.getListById(listId, userId)
                } else {
                    ApiService.apiInterface.getListById(listId)
                }

                Log.d(TAG, "response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!

                    //forza aggiornamento anche se uguale usando postValue
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

    //search movies con debounce - cerca in database "movies" (tutti i film disponibili)
    fun searchMovies(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                delay(500) //debounce
                _searchLoading.value = true

                Log.d(TAG, "autocomplete database movies: $query")
                //usa autocomplete tmdb che cerca nel database movies
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

    //aggiungi film alla lista con arricchimento automatico se necessario
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

                //step 1: controlla se il film e' gia arricchito
                if (!movie.isEnriched) {
                    Log.d(TAG, "film non arricchito: ${movie.title}, arricchisco prima di aggiungere...")

                    //arricchisci il film prima di aggiungerlo
                    val enrichResult = enrichMovieBeforeAdding(movie)

                    if (enrichResult == null) {
                        Log.w(TAG, "impossibile arricchire film ${movie.title}, aggiungo comunque")
                    } else {
                        Log.d(TAG, "film arricchito con successo: ${enrichResult.title}")
                    }
                }

                //step 2: aggiungi film alla lista (usa id del film dal database)
                Log.d(TAG, "aggiungo film ${movie.id} alla lista $listId")
                val request = com.example.movieapp.data.network.AddMovieToListRequest(movie_id = movie.id)
                val response = ApiService.apiInterface.addMovieToList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "film aggiunto con successo, ricarico lista...")
                    //ricarica automaticamente la lista per mostrare il film aggiunto
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

    //arricchisci film chiamando backend con endpoint enrichMovies (batch)
    private suspend fun enrichMovieBeforeAdding(movie: Movie): Movie? {
        return try {
            Log.d(TAG, "chiamata enrichment per ${movie.title}")

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

                    //converti enrichedmoviedto a movie
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

    //converti enrichedmoviedto a movie
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

    //rimuovi film
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
                    //ricarica automaticamente la lista
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

    //update lista
    fun updateList(
        listId: String,
        name: String,
        description: String?,
        isPublic: Boolean,
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

                Log.d(TAG, "aggiornamento lista $listId")

                val request = com.example.movieapp.data.network.UpdateListRequest(
                    name = name,
                    description = description,
                    is_public = isPublic
                )

                val response = ApiService.apiInterface.updateList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista aggiornata con successo, ricarico...")
                    //ricarica automaticamente la lista per mostrare le modifiche
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