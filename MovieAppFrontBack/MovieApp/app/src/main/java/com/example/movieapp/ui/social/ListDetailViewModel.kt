// FILE: app/src/main/java/com/example/movieapp/ui/social/ListDetailViewModel.kt
// ViewModel per dettaglio lista - REFACTORED

package com.example.movieapp.ui.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.AddMovieToListRequest
import kotlinx.coroutines.launch

class ListDetailViewModel : ViewModel() {
    private val TAG = "ListDetailViewModel"

    // ===== LIVEDATA =====
    private val _list = MutableLiveData<MovieList>()
    val list: LiveData<MovieList> = _list

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Carica dettagli lista
     */
    fun loadList(listId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val response = ApiService.apiInterface.getListById(listId)
                if (response.isSuccessful && response.body() != null) {
                    val movieList = response.body()!!
                    _list.value = movieList
                    _movies.value = movieList.movies ?: emptyList()
                    Log.d(TAG, "✅ Lista caricata: ${movieList.name} (${movieList.movies?.size} film)")
                } else {
                    _error.value = "Errore caricamento lista: ${response.code()}"
                    Log.e(TAG, "❌ Errore: ${response.code()}")
                }
            } catch (e: Exception) {
                _error.value = "Errore di rete: ${e.message}"
                Log.e(TAG, "❌ Eccezione: ${e.message}", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun addMovieToList(
        listId: String,
        movieId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId() ?: ""

                Log.d(TAG, "aggiunta film: $movieId a lista: $listId")
                val response = ApiService.apiInterface.addMovieToList(
                    listId,
                    AddMovieToListRequest(movieId),
                    userId
                )

                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "film aggiunto")
                    loadList(listId)
                    onSuccess()
                } else {
                    val error = "errore: ${response.code()}"
                    Log.e(TAG, error)
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione addmovietolist", e)
                onError("errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    fun removeMovieFromList(
        listId: String,
        movieId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId() ?: ""

                Log.d(TAG, "rimozione film: $movieId da lista: $listId")
                val response = ApiService.apiInterface.removeMovieFromList(
                    listId,
                    movieId,
                    userId
                )

                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "film rimosso")
                    loadList(listId)
                    onSuccess()
                } else {
                    val error = "errore: ${response.code()}"
                    Log.e(TAG, error)
                    onError(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione removemoviefromlist", e)
                onError("errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Reset errore
     */
    fun clearError() {
        _error.value = null
    }
}