// FILE: app/src/main/java/com/example/movieapp/ui/social/ListDetailViewModel.kt
// ViewModel per dettaglio lista

package com.example.movieapp.ui.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

class ListDetailViewModel : ViewModel() {
    private val TAG = "ListDetailViewModel"

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _listDetails = MutableLiveData<MovieList?>()
    val listDetails: LiveData<MovieList?> = _listDetails

    /**
     * Carica dettagli lista con film
     */
    fun loadListDetails(listId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "📄 Caricamento dettagli lista $listId")

                val result = ApiService.getListById(listId)

                if (result.isSuccess) {
                    val list = result.getOrNull()
                    _listDetails.value = list
                    Log.d(TAG, "✅ Lista caricata: ${list?.name} (${list?.movieCount} film)")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore caricamento lista"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore loadListDetails", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}