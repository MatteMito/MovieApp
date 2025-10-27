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

                val response = ApiService.apiService.getMyLists()
                if (response.isSuccessful && response.body() != null) {
                    val lists = response.body()!!
                    _myLists.value = lists
                    applySearchFilter(currentSearchQuery) // Applica filtro corrente
                    Log.d(TAG, "✅ Caricate ${lists.size} liste personali")
                } else {
                    _error.value = "Errore caricamento liste: ${response.code()}"
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

    /**
     * Carica liste pubbliche
     */
    fun loadPublicLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val response = ApiService.apiService.getPublicLists()
                if (response.isSuccessful && response.body() != null) {
                    val lists = response.body()!!
                    _publicLists.value = lists
                    applySearchFilter(currentSearchQuery) // Applica filtro corrente
                    Log.d(TAG, "✅ Caricate ${lists.size} liste pubbliche")
                } else {
                    _error.value = "Errore caricamento liste pubbliche: ${response.code()}"
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

        Log.d(TAG, "🔍 Ricerca '$currentSearchQuery': ${_filteredMyLists.value?.size} mie liste, ${_filteredPublicLists.value?.size} pubbliche")
    }

    /**
     * Pulisci filtro ricerca
     */
    fun clearSearchFilter() {
        applySearchFilter("")
    }

    // ===== GESTIONE LISTE =====

    /**
     * Elimina lista
     */
    fun deleteList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = ApiService.apiService.deleteList(listId)
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Lista $listId eliminata")
                    loadMyLists() // Ricarica liste
                    onSuccess()
                } else {
                    onError("Errore eliminazione: ${response.code()}")
                }
            } catch (e: Exception) {
                onError("Errore: ${e.message}")
                Log.e(TAG, "❌ Errore eliminazione: ${e.message}", e)
            }
        }
    }

    /**
     * Segui lista pubblica
     */
    fun followList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = ApiService.apiService.followList(listId)
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Lista $listId seguita")
                    onSuccess()
                } else {
                    onError("Errore: ${response.code()}")
                }
            } catch (e: Exception) {
                onError("Errore: ${e.message}")
                Log.e(TAG, "❌ Errore follow: ${e.message}", e)
            }
        }
    }

    // ===== UTILITY =====

    /**
     * Reset errore
     */
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        context = null
        Log.d(TAG, "ViewModel pulito")
    }
}