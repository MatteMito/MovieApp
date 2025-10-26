// FILE: app/src/main/java/com/example/movieapp/ui/social/SocialViewModel.kt
// ViewModel aggiornato con sistema filtri

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

    // Stati UI
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // 🔥 NUOVO: Filtri
    private val _currentFilters = MutableLiveData<ListFilters>(ListFilters.default())
    val currentFilters: LiveData<ListFilters> = _currentFilters

    // Liste RAW (senza filtri)
    private val _rawMyLists = MutableLiveData<List<MovieList>>(emptyList())
    private val _rawPublicLists = MutableLiveData<List<MovieList>>(emptyList())

    // Liste FILTRATE (esposte al Fragment)
    private val _myLists = MutableLiveData<List<MovieList>>(emptyList())
    val myLists: LiveData<List<MovieList>> = _myLists

    private val _publicLists = MutableLiveData<List<MovieList>>(emptyList())
    val publicLists: LiveData<List<MovieList>> = _publicLists

    private var isInitialized = false

    /**
     * Inizializza ViewModel
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
            Log.w(TAG, "⚠️ Utente non autenticato")
            _error.value = "Effettua il login per accedere alle liste"
            return
        }

        Log.d(TAG, "🚀 Inizializzazione SocialViewModel")

        loadMyLists()
        loadPublicLists()

        isInitialized = true
    }

    /**
     * 🔥 NUOVO: Applica filtri
     */
    fun applyFilters(filters: ListFilters) {
        _currentFilters.value = filters
        Log.d(TAG, "🔍 Applicazione filtri: $filters")

        // Applica filtri alle liste raw
        val filteredMyLists = filters.applyTo(_rawMyLists.value ?: emptyList())
        val filteredPublicLists = filters.applyTo(_rawPublicLists.value ?: emptyList())

        _myLists.value = filteredMyLists
        _publicLists.value = filteredPublicLists

        Log.d(TAG, "✅ Le Mie Liste: ${filteredMyLists.size}/${_rawMyLists.value?.size}")
        Log.d(TAG, "✅ Liste Pubbliche: ${filteredPublicLists.size}/${_rawPublicLists.value?.size}")
    }

    /**
     * 🔥 NUOVO: Reset filtri
     */
    fun resetFilters() {
        applyFilters(ListFilters.default())
    }

    /**
     * Ricarica tutte le liste
     */
    fun refreshLists() {
        Log.d(TAG, "🔄 Refresh liste")
        loadMyLists()
        loadPublicLists()
    }

    /**
     * Carica liste dell'utente
     */
    private fun loadMyLists() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val result = ApiService.getUserLists()

                if (result.isSuccess) {
                    val lists = result.getOrNull() ?: emptyList()
                    _rawMyLists.value = lists

                    // Applica filtri correnti
                    val filters = _currentFilters.value ?: ListFilters.default()
                    _myLists.value = filters.applyTo(lists)

                    Log.d(TAG, "✅ ${lists.size} liste personali caricate")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore caricamento liste"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore loadMyLists", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Carica liste pubbliche
     */
    private fun loadPublicLists() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val result = ApiService.getPublicLists(limit = 50)

                if (result.isSuccess) {
                    val lists = result.getOrNull() ?: emptyList()
                    _rawPublicLists.value = lists

                    // Applica filtri correnti
                    val filters = _currentFilters.value ?: ListFilters.default()
                    _publicLists.value = filters.applyTo(lists)

                    Log.d(TAG, "✅ ${lists.size} liste pubbliche caricate")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore caricamento liste pubbliche"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore loadPublicLists", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Crea nuova lista
     */
    fun createList(name: String, description: String?, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "📝 SocialViewModel: Richiesta creazione lista")
                Log.d(TAG, "   Nome: $name")
                Log.d(TAG, "   Pubblica: $isPublic")

                if (!ApiService.isAuthenticated()) {
                    Log.e(TAG, "❌ Utente non autenticato!")
                    _error.value = "Effettua nuovamente il login"
                    _isLoading.value = false
                    return@launch
                }

                if (!ApiService.hasUserId()) {
                    Log.e(TAG, "❌ UserId non disponibile!")
                    _error.value = "Errore dati utente. Rieffettua il login."
                    _isLoading.value = false
                    return@launch
                }

                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "✅ UserId disponibile: $userId")

                val result = ApiService.createList(
                    name = name,
                    description = description,
                    isPublic = isPublic
                )

                if (result.isSuccess) {
                    Log.d(TAG, "✅ Lista creata con successo!")
                    _error.value = null
                    // Ricarica liste
                    loadMyLists()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore creazione lista"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ Errore: $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione: ${e.message}"
                Log.e(TAG, "❌ Eccezione createList", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Aggiorna lista esistente
     */
    fun updateList(listId: String, name: String?, description: String?, isPublic: Boolean?) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "✏️ Aggiornamento lista $listId")

                val result = ApiService.updateList(
                    listId = listId,
                    name = name,
                    description = description,
                    isPublic = isPublic
                )

                if (result.isSuccess) {
                    Log.d(TAG, "✅ Lista aggiornata con successo")
                    // Ricarica liste
                    loadMyLists()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore aggiornamento lista"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore updateList", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Elimina lista
     */
    fun deleteList(listId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "🗑️ Eliminazione lista $listId")

                val result = ApiService.deleteList(listId)

                if (result.isSuccess) {
                    Log.d(TAG, "✅ Lista eliminata con successo")
                    // Ricarica liste
                    loadMyLists()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore eliminazione lista"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore deleteList", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Segui lista pubblica
     */
    fun followList(listId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "👥 Follow lista $listId")

                val result = ApiService.followList(listId)

                if (result.isSuccess) {
                    Log.d(TAG, "✅ Lista seguita con successo")
                    // Ricarica liste pubbliche per aggiornare contatore followers
                    loadPublicLists()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore follow lista"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore followList", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}