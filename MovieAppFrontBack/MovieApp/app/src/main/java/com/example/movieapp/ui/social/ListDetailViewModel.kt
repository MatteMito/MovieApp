//file: app/src/main/java/com/example/movieapp/ui/social/ListDetailViewModel.kt
//viewmodel per listdetailfragment con userid nelle chiamate

package com.example.movieapp.ui.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.MovieList
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

    //carica lista
    fun loadList(listId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "carico lista: $listId (userId: $userId)")

                //passa userid per accesso liste private
                val response = if (userId != null) {
                    ApiService.apiInterface.getListById(listId, userId)
                } else {
                    ApiService.apiInterface.getListById(listId)
                }

                Log.d(TAG, "response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    _currentList.value = list
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

    //aggiungi film
    fun addMovie(listId: String, movieId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "aggiungo film $movieId alla lista $listId")
                val request = com.example.movieapp.data.network.AddMovieToListRequest(movie_id = movieId)
                val response = ApiService.apiInterface.addMovieToList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "film aggiunto")
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
                    Log.d(TAG, "film rimosso")
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

    //aggiorna lista - FIX: passa userId
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

                Log.d(TAG, "aggiorno lista: $listId")
                val request = com.example.movieapp.data.network.UpdateListRequest(
                    name = name,
                    description = description,
                    is_public = isPublic
                )

                //passa userId come query parameter
                val response = ApiService.apiInterface.updateList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista aggiornata")
                    onSuccess()
                } else {
                    val errorMsg = "errore aggiornamento: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "errorbody: ${response.errorBody()?.string()}")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione updatelist", e)
                onError("errore: ${e.message}")
            }
        }
    }

    //elimina lista
    fun deleteList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "elimino lista: $listId")
                val response = ApiService.apiInterface.deleteList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista eliminata")
                    onSuccess()
                } else {
                    val errorMsg = "errore eliminazione: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione deletelist", e)
                onError("errore: ${e.message}")
            }
        }
    }
}