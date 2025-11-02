//file: app/src/main/java/com/example/movieapp/ui/social/ListDetailViewModel.kt
//viewmodel per dettaglio lista

package com.example.movieapp.ui.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

class ListDetailViewModel : ViewModel() {
    private val TAG = "ListDetailViewModel"

    private val _list = MutableLiveData<MovieList>()
    val list: LiveData<MovieList> = _list

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _searchResults = MutableLiveData<List<Movie>>()
    val searchResults: LiveData<List<Movie>> = _searchResults

    private val _searchLoading = MutableLiveData<Boolean>()
    val searchLoading: LiveData<Boolean> = _searchLoading

    fun loadList(listId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                Log.d(TAG, "carico lista: $listId")
                val response = ApiService.apiInterface.getListById(listId)

                if (response.isSuccessful && response.body() != null) {
                    val movieList = response.body()!!
                    _list.value = movieList
                    Log.d(TAG, "lista caricata: ${movieList.name}")
                    Log.d(TAG, "film: ${movieList.movies.size}")
                } else {
                    val errorMsg = "errore caricamento lista: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, errorMsg)
                }
            } catch (e: Exception) {
                _error.value = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione loadlist", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun searchMovies(query: String) {
        viewModelScope.launch {
            try {
                _searchLoading.value = true

                Log.d(TAG, "cerco film: $query")
                val response = ApiService.apiInterface.autocompleteMovies(query, 10)

                if (response.isSuccessful && response.body()?.success == true) {
                    val movies = response.body()?.data ?: emptyList()
                    _searchResults.value = movies
                    Log.d(TAG, "trovati ${movies.size} film")
                } else {
                    _searchResults.value = emptyList()
                    Log.e(TAG, "errore ricerca: ${response.code()}")
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                Log.e(TAG, "eccezione searchmovies", e)
            } finally {
                _searchLoading.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun addMovie(listId: String, movieId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "aggiungo film $movieId a lista $listId")
                val request = com.example.movieapp.data.network.AddMovieToListRequest(movieId)
                val response = ApiService.apiInterface.addMovieToList(listId, request, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "film aggiunto con successo")
                    onSuccess()
                } else {
                    val errorMsg = "errore aggiunta film: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione addmovie", e)
                onError("errore: ${e.message}")
            }
        }
    }

    fun removeMovie(listId: String, movieId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "rimuovo film $movieId da lista $listId")
                val response = ApiService.apiInterface.removeMovieFromList(listId, movieId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "film rimosso con successo")
                    onSuccess()
                } else {
                    val errorMsg = "errore rimozione film: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione removemovie", e)
                onError("errore: ${e.message}")
            }
        }
    }

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

                Log.d(TAG, "aggiorno lista $listId")
                val request = com.example.movieapp.data.network.UpdateListRequest(
                    name = name,
                    description = description,
                    is_public = isPublic
                )

                val response = ApiService.apiInterface.updateList(listId, request)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista aggiornata con successo")
                    onSuccess()
                } else {
                    val errorMsg = "errore aggiornamento lista: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione updatelist", e)
                onError("errore: ${e.message}")
            }
        }
    }

    fun deleteList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "elimino lista $listId")
                val response = ApiService.apiInterface.deleteList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista eliminata con successo")
                    onSuccess()
                } else {
                    val errorMsg = "errore eliminazione lista: ${response.code()}"
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