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
import com.example.movieapp.data.network.CreateListRequest
import com.example.movieapp.data.network.UpdateListRequest
import com.example.movieapp.data.network.CopyListRequest
import kotlinx.coroutines.launch

// viewmodel per gestione liste condivise con ricerca, filtri e ordinamento
class SocialViewModel : ViewModel() {
    private val TAG = "SocialViewModel"

    // enum per tipi di ordinamento
    enum class SortType {
        NAME_ASC,
        NAME_DESC,
        DATE_ASC,
        DATE_DESC,
        POPULARITY
    }

    // livedata liste originali dal backend
    private val _myLists = MutableLiveData<List<MovieList>>()

    private val _publicLists = MutableLiveData<List<MovieList>>()

    private val _followedLists = MutableLiveData<List<MovieList>>()

    // livedata filtrate per ui (dopo ricerca, ordinamento, filtri)
    private val _filteredMyLists = MutableLiveData<List<MovieList>>()
    val filteredMyLists: LiveData<List<MovieList>> = _filteredMyLists

    private val _filteredPublicLists = MutableLiveData<List<MovieList>>()
    val filteredPublicLists: LiveData<List<MovieList>> = _filteredPublicLists

    private val _filteredFollowedLists = MutableLiveData<List<MovieList>>()
    val filteredFollowedLists: LiveData<List<MovieList>> = _filteredFollowedLists

    // stati loading ed errori
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var context: Context? = null
    // stato filtri correnti
    private var currentSearchQuery: String = ""
    private var currentSortType: SortType = SortType.DATE_DESC
    private var currentVisibilityFilter: Boolean? = null

    fun initialize(context: Context) {
        this.context = context
        refreshLists()
    }

    // carica mie liste personali (pubbliche e private)
    fun loadMyLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    _error.value = "utente non autenticato"
                    Log.e(TAG, "userid null per my lists")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "carico mie liste per user: $userId")

                val response = ApiService.apiInterface.getMyLists(userId)

                if (response.isSuccessful) {
                    val lists = response.body() ?: emptyList()
                    _myLists.value = lists
                    applyFilters()
                    Log.d(TAG, "caricate ${lists.size} mie liste")
                } else {
                    val errorMsg = "errore caricamento mie liste: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, errorMsg)
                }
            } catch (e: Exception) {
                _error.value = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione loadmylists", e)
            } finally {
                _loading.value = false
            }
        }
    }

    // carica liste pubbliche non ancora seguite (versione sincrona interna)
    private suspend fun loadPublicListsInternal() {
        try {
            val userId = ApiService.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "userid null per public lists")
                return
            }

            Log.d(TAG, "carico liste pubbliche per user: $userId")

            val response = ApiService.apiInterface.getPublicLists(100, userId)

            if (response.isSuccessful) {
                val allLists = response.body() ?: emptyList()
                Log.d(TAG, "=== RISPOSTA BACKEND: ${allLists.size} liste pubbliche totali ===")

                // stampa TUTTE le liste con il loro stato isFollowing
                allLists.forEachIndexed { index, list ->
                    Log.d(TAG, "Lista $index: ${list.name} | isFollowing=${list.isFollowing} | followers=${list.followerIds}")
                }

                // filtra solo liste non ancora seguite
                val publicListsNotFollowed = allLists.filter { !it.isFollowing }
                _publicLists.value = publicListsNotFollowed
                applyFilters()
                Log.d(TAG, "=== DOPO FILTRO: ${publicListsNotFollowed.size} liste pubbliche (non seguite) ===")
            } else {
                val errorMsg = "errore caricamento liste pubbliche: ${response.code()}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
            }
        } catch (e: Exception) {
            _error.value = "errore di rete: ${e.message}"
            Log.e(TAG, "eccezione loadpubliclists", e)
        }
    }

    // versione pubblica per chiamate esterne
    fun loadPublicLists() {
        viewModelScope.launch {
            _loading.value = true
            loadPublicListsInternal()
            _loading.value = false
        }
    }

    // carica liste pubbliche che l'utente sta seguendo (versione sincrona interna)
    private suspend fun loadFollowedListsInternal() {
        try {
            val userId = ApiService.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "userid null per followed lists")
                return
            }

            Log.d(TAG, "carico liste seguite per user: $userId")

            // ottieni tutte le liste pubbliche e filtra quelle seguite
            val response = ApiService.apiInterface.getPublicLists(100, userId)

            if (response.isSuccessful) {
                val allPublicLists = response.body() ?: emptyList()
                Log.d(TAG, "=== RISPOSTA BACKEND: ${allPublicLists.size} liste pubbliche totali ===")

                // stampa TUTTE le liste con il loro stato isFollowing
                allPublicLists.forEachIndexed { index, list ->
                    Log.d(TAG, "Lista $index: ${list.name} | isFollowing=${list.isFollowing} | followers=${list.followerIds}")
                }

                // filtra solo liste con flag isFollowing true
                val followedLists = allPublicLists.filter { it.isFollowing }
                _followedLists.value = followedLists
                applyFilters()
                Log.d(TAG, "=== DOPO FILTRO: ${followedLists.size} liste seguite ===")
            } else {
                val errorMsg = "errore caricamento liste seguite: ${response.code()}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
            }
        } catch (e: Exception) {
            _error.value = "errore di rete: ${e.message}"
            Log.e(TAG, "eccezione loadfollowedlists", e)
        }
    }

    // versione pubblica per chiamate esterne
    fun loadFollowedLists() {
        viewModelScope.launch {
            _loading.value = true
            loadFollowedListsInternal()
            _loading.value = false
        }
    }

    // ricarica tutte le liste
    fun refreshLists() {
        loadMyLists()
        loadPublicLists()
        loadFollowedLists()
    }

    // ricerca e filtri

    // applica filtro ricerca testuale
    fun applySearchFilter(query: String) {
        currentSearchQuery = query.trim().lowercase()
        applyFilters()
    }

    // applica ordinamento
    fun applySortFilter(sortType: SortType) {
        currentSortType = sortType
        applyFilters()
    }

    // applica filtro visibilita (pubbliche/private)
    fun applyVisibilityFilter(isPublic: Boolean?) {
        currentVisibilityFilter = isPublic
        applyFilters()
    }

    // applica tutti i filtri correnti a tutte le liste
    private fun applyFilters() {
        // filtra mie liste
        val myListsData = _myLists.value ?: emptyList()
        val filteredMyLists = filterAndSortLists(myListsData)
        _filteredMyLists.value = filteredMyLists

        // filtra liste pubbliche
        val publicListsData = _publicLists.value ?: emptyList()
        val filteredPublicLists = filterAndSortLists(publicListsData)
        _filteredPublicLists.value = filteredPublicLists

        // filtra liste seguite
        val followedListsData = _followedLists.value ?: emptyList()
        val filteredFollowedLists = filterAndSortLists(followedListsData)
        _filteredFollowedLists.value = filteredFollowedLists
    }

    // filtra e ordina lista in base a filtri correnti
    private fun filterAndSortLists(lists: List<MovieList>): List<MovieList> {
        var result = lists

        // filtro ricerca testuale
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter {
                it.name.lowercase().contains(currentSearchQuery) ||
                        (it.description?.lowercase()?.contains(currentSearchQuery) == true) ||
                        (it.username?.lowercase()?.contains(currentSearchQuery) == true)
            }
        }

        // filtro visibilita
        currentVisibilityFilter?.let { isPublic ->
            result = result.filter { it.isPublic == isPublic }
        }

        // ordinamento
        result = when (currentSortType) {
            SortType.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            SortType.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
            SortType.DATE_ASC -> result.sortedBy { it.createdAt }
            SortType.DATE_DESC -> result.sortedByDescending { it.createdAt }
            SortType.POPULARITY -> result.sortedByDescending { it.followersCount }
        }

        return result
    }

    // operazioni crud liste

    // crea nuova lista
    fun createList(
        name: String,
        description: String?,
        isPublic: Boolean,
        targetDate: String?,
        frequency: String?,
        notificationsEnabled: Boolean,
        onSuccess: (MovieList) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "creo nuova lista: $name (pubblica=$isPublic)")

                val request = CreateListRequest(
                    user_id = userId,
                    name = name,
                    description = description,
                    is_public = isPublic,
                    movie_ids = emptyList(),
                    target_date = targetDate,
                    frequency = frequency,
                    notifications_enabled = notificationsEnabled
                )

                val response = ApiService.apiInterface.createList(request)

                if (response.isSuccessful) {
                    val newList = response.body()
                    if (newList != null) {
                        Log.d(TAG, "lista creata: ${newList.id}")
                        refreshLists()
                        onSuccess(newList)
                    } else {
                        onError("risposta vuota dal server")
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "errore sconosciuto"
                    Log.e(TAG, "errore creazione lista: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione createlist", e)
                onError(errorMsg)
            }
        }
    }

    // elimina lista
    fun deleteList(
        listId: String,
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

                Log.d(TAG, "elimino lista: $listId")

                val response = ApiService.apiInterface.deleteList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista eliminata con successo")
                    refreshLists()
                    onSuccess()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "errore sconosciuto"
                    Log.e(TAG, "errore eliminazione lista: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione deletelist", e)
                onError(errorMsg)
            }
        }
    }

    // segui lista pubblica
    fun followList(
        listId: String,
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

                Log.d(TAG, "=== INIZIO FOLLOW LISTA $listId ===")

                val body = mapOf("userId" to userId)
                val response = ApiService.apiInterface.followList(listId, body)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista seguita con successo sul backend")

                    // aspetta che entrambe le liste si ricarichino completamente
                    Log.d(TAG, "ricarico liste pubbliche...")
                    loadPublicListsInternal()
                    Log.d(TAG, "liste pubbliche ricaricate")

                    Log.d(TAG, "ricarico liste seguite...")
                    loadFollowedListsInternal()
                    Log.d(TAG, "liste seguite ricaricate")

                    Log.d(TAG, "=== FINE FOLLOW LISTA ===")

                    // ora chiama onsuccess
                    onSuccess()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "errore sconosciuto"
                    Log.e(TAG, "errore follow lista: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione followlist", e)
                onError(errorMsg)
            }
        }
    }

    // smetti di seguire lista
    fun unfollowList(
        listId: String,
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

                Log.d(TAG, "=== INIZIO UNFOLLOW LISTA $listId ===")

                val response = ApiService.apiInterface.unfollowList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista non seguita piu sul backend")

                    // aspetta che entrambe le liste si ricarichino completamente
                    Log.d(TAG, "ricarico liste pubbliche...")
                    loadPublicListsInternal()
                    Log.d(TAG, "liste pubbliche ricaricate")

                    Log.d(TAG, "ricarico liste seguite...")
                    loadFollowedListsInternal()
                    Log.d(TAG, "liste seguite ricaricate")

                    Log.d(TAG, "=== FINE UNFOLLOW LISTA ===")

                    // ora chiama onsuccess
                    onSuccess()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "errore sconosciuto"
                    Log.e(TAG, "errore unfollow lista: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione unfollowlist", e)
                onError(errorMsg)
            }
        }
    }

    // copia lista pubblica nelle mie liste
    fun copyList(
        listId: String,
        newName: String,
        onSuccess: (MovieList) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "copio lista: $listId con nome: $newName")

                val request = CopyListRequest(
                    userId = userId,
                    newName = newName
                )

                val response = ApiService.apiInterface.copyList(listId, request)

                if (response.isSuccessful) {
                    val copiedList = response.body()
                    if (copiedList != null) {
                        Log.d(TAG, "lista copiata: ${copiedList.id}")
                        refreshLists()
                        onSuccess(copiedList)
                    } else {
                        onError("risposta vuota dal server")
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "errore sconosciuto"
                    Log.e(TAG, "errore copia lista: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione copylist", e)
                onError(errorMsg)
            }
        }
    }
}