// file: app/src/main/java/com/example/movieapp/ui/social/SocialViewModel.kt
// viewmodel per socialfragment con tutte le funzionalita

package com.example.movieapp.ui.social

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
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

    // ===== AUTOCOMPLETE =====
    private val _autocompleteResults = MutableLiveData<List<Movie>>()
    val autocompleteResults: LiveData<List<Movie>> = _autocompleteResults

    private val _autocompleteLoading = MutableLiveData<Boolean>()
    val autocompleteLoading: LiveData<Boolean> = _autocompleteLoading

    private var context: Context? = null
    private var currentSearchQuery: String = ""

    /**
     * inizializza viewmodel con contesto
     */
    fun initialize(context: Context) {
        this.context = context
        loadMyLists()
        loadPublicLists()
    }

    // ===== CARICAMENTO LISTE =====

    /**
     * carica le mie liste
     */
    fun loadMyLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    _error.value = "utente non autenticato"
                    Log.e(TAG, "userid null")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "carico liste per user: $userId")
                val response = ApiService.apiInterface.getMyLists(userId)

                Log.d(TAG, "response code: ${response.code()}")

                if (response.isSuccessful) {
                    val lists = response.body() ?: emptyList()
                    _myLists.value = lists
                    applySearchFilter(currentSearchQuery)
                    Log.d(TAG, "caricate ${lists.size} liste personali")
                } else {
                    val errorMsg = "errore caricamento liste: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "message: ${response.message()}")
                    Log.e(TAG, "errorbody: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                _error.value = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione loadmylists", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * carica liste pubbliche
     */
    fun loadPublicLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                Log.d(TAG, "carico liste pubbliche (exclude: $userId)")

                val response = ApiService.apiInterface.getPublicLists(20, userId)

                Log.d(TAG, "response code: ${response.code()}")

                if (response.isSuccessful) {
                    val lists = response.body() ?: emptyList()
                    _publicLists.value = lists
                    applySearchFilter(currentSearchQuery)
                    Log.d(TAG, "caricate ${lists.size} liste pubbliche")
                } else {
                    val errorMsg = "errore caricamento liste pubbliche: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "message: ${response.message()}")
                    Log.e(TAG, "errorbody: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                _error.value = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione loadpubliclists", e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * ricarica tutte le liste
     */
    fun refreshLists() {
        loadMyLists()
        loadPublicLists()
    }

    // ===== RICERCA E FILTRI =====

    /**
     * applica filtro di ricerca
     */
    fun applySearchFilter(query: String) {
        currentSearchQuery = query.trim().lowercase()

        //filtra le mie liste
        val myListsData = _myLists.value ?: emptyList()
        _filteredMyLists.value = if (currentSearchQuery.isEmpty()) {
            myListsData
        } else {
            myListsData.filter {
                it.name.lowercase().contains(currentSearchQuery) ||
                        it.description?.lowercase()?.contains(currentSearchQuery) == true
            }
        }

        //filtra liste pubbliche
        val publicListsData = _publicLists.value ?: emptyList()
        _filteredPublicLists.value = if (currentSearchQuery.isEmpty()) {
            publicListsData
        } else {
            publicListsData.filter {
                it.name.lowercase().contains(currentSearchQuery) ||
                        it.description?.lowercase()?.contains(currentSearchQuery) == true
            }
        }

        Log.d(TAG, "filtro applicato: '$currentSearchQuery'")
        Log.d(TAG, "mie liste: ${_filteredMyLists.value?.size} / ${myListsData.size}")
        Log.d(TAG, "pubbliche: ${_filteredPublicLists.value?.size} / ${publicListsData.size}")
    }

    /**
     * pulisci filtro di ricerca
     */
    fun clearSearchFilter() {
        applySearchFilter("")
    }

    // ===== AZIONI LISTE =====

    /**
     * crea nuova lista
     */
    fun createList(
        name: String,
        description: String?,
        isPublic: Boolean,
        movieIds: List<String>,
        onSuccess: (MovieList) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "creazione lista: $name")
                val request = com.example.movieapp.data.network.CreateListRequest(
                    user_id = userId,
                    name = name,
                    description = description,
                    is_public = isPublic,
                    movie_ids = movieIds
                )

                val response = ApiService.apiInterface.createList(request)

                if (response.isSuccessful && response.body() != null) {
                    val newList = response.body()!!
                    Log.d(TAG, "lista creata: ${newList.id}")
                    loadMyLists() //ricarica liste
                    onSuccess(newList)
                } else {
                    val errorMsg = "errore creazione: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione createlist", e)
                onError("errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * elimina lista
     */
    fun deleteList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "eliminazione lista: $listId")
                val response = ApiService.apiInterface.deleteList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista eliminata")
                    loadMyLists() //ricarica liste
                    onSuccess()
                } else {
                    val errorMsg = "errore eliminazione: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione deletelist", e)
                onError("errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * segui lista pubblica
     */
    fun followList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "follow lista: $listId")
                val request = mapOf("userId" to userId)
                val response = ApiService.apiInterface.followList(listId, request)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista seguita")
                    loadPublicLists() //ricarica liste
                    onSuccess()
                } else {
                    val errorMsg = "errore follow: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione followlist", e)
                onError("errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * copia lista pubblica
     */
    fun copyList(listId: String, newName: String?, onSuccess: (MovieList) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _loading.value = true

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "copia lista: $listId")
                val request = com.example.movieapp.data.network.CopyListRequest(
                    userId = userId,
                    newName = newName
                )
                val response = ApiService.apiInterface.copyList(listId, request)

                if (response.isSuccessful && response.body() != null) {
                    val copiedList = response.body()!!
                    Log.d(TAG, "lista copiata: ${copiedList.id}")
                    loadMyLists() //ricarica liste
                    onSuccess(copiedList)
                } else {
                    val errorMsg = "errore copia: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione copylist", e)
                onError("errore: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    // ===== AUTOCOMPLETE FILM =====

    /**
     * cerca film per autocomplete
     */
    fun searchMoviesAutocomplete(query: String) {
        if (query.length < 2) {
            _autocompleteResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                _autocompleteLoading.value = true

                Log.d(TAG, "autocomplete: $query")
                val response = ApiService.apiInterface.autocompleteMovies(query, 10)

                if (response.isSuccessful && response.body()?.success == true) {
                    val movies = response.body()?.data ?: emptyList()
                    _autocompleteResults.value = movies
                    Log.d(TAG, "trovati ${movies.size} film")
                } else {
                    _autocompleteResults.value = emptyList()
                    Log.e(TAG, "errore autocomplete: ${response.code()}")
                }
            } catch (e: Exception) {
                _autocompleteResults.value = emptyList()
                Log.e(TAG, "eccezione autocomplete", e)
            } finally {
                _autocompleteLoading.value = false
            }
        }
    }

    /**
     * pulisci risultati autocomplete
     */
    fun clearAutocompleteResults() {
        _autocompleteResults.value = emptyList()
    }

    /**
     * reset errore
     */
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        context = null
        Log.d(TAG, "socialviewmodel pulito")
    }
}