// FILE: app/src/main/java/com/example/movieapp/ui/social/SocialViewModel.kt
// ViewModel per SocialFragment - COMPLETO

package com.example.movieapp.ui.social

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch

class SocialViewModel : ViewModel() {
    private val TAG = "SocialViewModel"

    // ===== LIVEDATA LISTE =====
    private val _myLists = MutableLiveData<List<MovieList>>()
    val myLists: LiveData<List<MovieList>> = _myLists

    private val _publicLists = MutableLiveData<List<MovieList>>()
    val publicLists: LiveData<List<MovieList>> = _publicLists

    // ===== LIVEDATA FILTRATE (per ricerca) =====
    private val _filteredMyLists = MutableLiveData<List<MovieList>>()
    val filteredMyLists: LiveData<List<MovieList>> = _filteredMyLists

    private val _filteredPublicLists = MutableLiveData<List<MovieList>>()
    val filteredPublicLists: LiveData<List<MovieList>> = _filteredPublicLists

    // ===== LIVEDATA STATI =====
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var context: Context? = null
    private var currentSearchQuery: String = ""

    /**
     * Inizializza ViewModel con contesto
     */
    fun initialize(context: Context) {
        this.context = context
        loadMyLists()
        loadPublicLists()
    }

    // ===== CARICAMENTO LISTE =====

    /**
     * Carica le mie liste
     */
    fun loadMyLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    _error.value = "Utente non autenticato"
                    Log.e(TAG, "❌ userId NULL")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "📋 Carico liste per user: $userId")
                val response = ApiService.apiInterface.getMyLists(userId)

                Log.d(TAG, "📥 Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val lists = response.body() ?: emptyList()
                    _myLists.value = lists
                    applySearchFilter(currentSearchQuery)
                    Log.d(TAG, "✅ Caricate ${lists.size} liste personali")
                } else {
                    val errorMsg = "Errore caricamento liste: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                    Log.e(TAG, "   Message: ${response.message()}")
                    Log.e(TAG, "   ErrorBody: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                _error.value = "Errore di rete: ${e.message}"
                Log.e(TAG, "❌ Eccezione loadMyLists", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Carica liste pubbliche
     */
    fun loadPublicLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "🌍 Carico liste pubbliche (exclude: $userId)")

                val response = ApiService.apiInterface.getPublicLists(20, userId)

                Log.d(TAG, "📥 Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val lists = response.body() ?: emptyList()
                    _publicLists.value = lists
                    applySearchFilter(currentSearchQuery)
                    Log.d(TAG, "✅ Caricate ${lists.size} liste pubbliche")
                } else {
                    val errorMsg = "Errore caricamento liste pubbliche: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                    Log.e(TAG, "   Message: ${response.message()}")
                    Log.e(TAG, "   ErrorBody: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                _error.value = "Errore di rete: ${e.message}"
                Log.e(TAG, "❌ Eccezione loadPublicLists", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Ricarica tutte le liste
     */
    fun refreshLists() {
        loadMyLists()
        loadPublicLists()
    }

    // ===== RICERCA E FILTRI =====

    /**
     * Applica filtro di ricerca
     */
    fun applySearchFilter(query: String) {
        currentSearchQuery = query.trim().lowercase()

        // Filtra le mie liste
        val myListsData = _myLists.value ?: emptyList()
        _filteredMyLists.value = if (currentSearchQuery.isEmpty()) {
            myListsData
        } else {
            myListsData.filter {
                it.name.lowercase().contains(currentSearchQuery) ||
                        it.description?.lowercase()?.contains(currentSearchQuery) == true
            }
        }

        // Filtra liste pubbliche
        val publicListsData = _publicLists.value ?: emptyList()
        _filteredPublicLists.value = if (currentSearchQuery.isEmpty()) {
            publicListsData
        } else {
            publicListsData.filter {
                it.name.lowercase().contains(currentSearchQuery) ||
                        it.description?.lowercase()?.contains(currentSearchQuery) == true
            }
        }

        Log.d(TAG, "🔍 Filtro applicato: '$currentSearchQuery'")
        Log.d(TAG, "   Mie liste: ${_filteredMyLists.value?.size} / ${myListsData.size}")
        Log.d(TAG, "   Pubbliche: ${_filteredPublicLists.value?.size} / ${publicListsData.size}")
    }

    /**
     * Pulisci filtro di ricerca
     */
    fun clearSearchFilter() {
        applySearchFilter("")
    }

    // ===== AZIONI LISTE =====

    /**
     * Elimina lista
     */
    fun deleteList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _loading.value = true

                Log.d(TAG, "🗑️ Eliminazione lista: $listId")
                val response = ApiService.apiInterface.deleteList(listId)

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Lista eliminata")
                    loadMyLists() // Ricarica liste
                    onSuccess()
                } else {
                    val errorMsg = "Errore eliminazione: ${response.code()}"
                    Log.e(TAG, "❌ $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Eccezione deleteList", e)
                onError("Errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Segui lista pubblica
     */
    fun followList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("Utente non autenticato")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "👥 Follow lista: $listId")
                val request = mapOf("userId" to userId)
                val response = ApiService.apiInterface.followList(listId, request)

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Lista seguita")
                    loadPublicLists() // Ricarica liste
                    onSuccess()
                } else {
                    val errorMsg = "Errore follow: ${response.code()}"
                    Log.e(TAG, "❌ $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Eccezione followList", e)
                onError("Errore: ${e.message}")
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

    override fun onCleared() {
        super.onCleared()
        context = null
        Log.d(TAG, "SocialViewModel pulito")
    }
}