// FILE: app/src/main/java/com/example/movieapp/ui/social/SocialViewModel.kt
// ViewModel per gestione stato liste social

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

    // Liste
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
                    _myLists.value = lists
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
                    _publicLists.value = lists
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

                Log.d(TAG, "📝 Creazione lista: $name (pubblica: $isPublic)")

                val result = ApiService.createList(
                    name = name,
                    description = description,
                    isPublic = isPublic
                )

                if (result.isSuccess) {
                    Log.d(TAG, "✅ Lista creata con successo")
                    // Ricarica liste
                    loadMyLists()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Errore creazione lista"
                    _error.value = errorMsg
                    Log.e(TAG, "❌ $errorMsg")
                }
            } catch (e: Exception) {
                _error.value = "Errore di connessione"
                Log.e(TAG, "❌ Errore createList", e)
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