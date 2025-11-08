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
        // carica tutte le liste all'inizializzazione
        loadMyLists()
        loadPublicLists()
        loadFollowedLists()
    }

    // carica liste personali dell'utente corrente
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
                    // applica filtri correnti
                    applyFilters()
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

    // carica liste pubbliche escludendo quelle già seguite
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
                    val allLists = response.body() ?: emptyList()
                    // filtra solo liste non ancora seguite
                    val publicListsNotFollowed = allLists.filter { !it.isFollowing }
                    _publicLists.value = publicListsNotFollowed
                    applyFilters()
                    Log.d(TAG, "caricate ${publicListsNotFollowed.size} liste pubbliche (non seguite)")
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

    // carica liste pubbliche che l'utente sta seguendo
    fun loadFollowedLists() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    _error.value = "utente non autenticato"
                    Log.e(TAG, "userid null per followed lists")
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "carico liste seguite per user: $userId")

                // ottieni tutte le liste pubbliche e filtra quelle seguite
                val response = ApiService.apiInterface.getPublicLists(100, userId)

                if (response.isSuccessful) {
                    val allPublicLists = response.body() ?: emptyList()
                    // filtra solo liste con flag isFollowing true
                    val followedLists = allPublicLists.filter { it.isFollowing }
                    _followedLists.value = followedLists
                    applyFilters()
                    Log.d(TAG, "caricate ${followedLists.size} liste seguite")
                } else {
                    val errorMsg = "errore caricamento liste seguite: ${response.code()}"
                    _error.value = errorMsg
                    Log.e(TAG, errorMsg)
                }
            } catch (e: Exception) {
                _error.value = "errore di rete: ${e.message}"
                Log.e(TAG, "eccezione loadfollowedlists", e)
            } finally {
                _loading.value = false
            }
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

    // applica filtro visibilità (pubbliche/private)
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

    // filtra e ordina lista in base ai filtri correnti
    private fun filterAndSortLists(lists: List<MovieList>): List<MovieList> {
        var result = lists

        // ricerca testuale su nome e descrizione
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter { list ->
                list.name.lowercase().contains(currentSearchQuery) ||
                        list.description?.lowercase()?.contains(currentSearchQuery) == true
            }
        }

        // filtro visibilità pubblico/privato
        when (currentVisibilityFilter) {
            true -> result = result.filter { it.isPublic }
            false -> result = result.filter { !it.isPublic }
            null -> {} // mostra tutte
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

    // azioni crud liste

    // crea nuova lista
    fun createList(
        name: String,
        description: String?,
        isPublic: Boolean,
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

                Log.d(TAG, "creazione lista: $name")

                val request = com.example.movieapp.data.network.CreateListRequest(
                    user_id = userId,
                    name = name,
                    description = description,
                    is_public = isPublic
                )

                val response = ApiService.apiInterface.createList(request)

                if (response.isSuccessful && response.body() != null) {
                    val newList = response.body()!!
                    Log.d(TAG, "lista creata: ${newList.id}")
                    // ricarica mie liste
                    loadMyLists()
                    onSuccess(newList)
                } else {
                    val errorMsg = "errore creazione: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione createlist", e)
                onError("errore: ${e.message}")
            }
        }
    }

    // elimina lista
    fun deleteList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "eliminazione lista: $listId")
                val response = ApiService.apiInterface.deleteList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista eliminata")
                    // ricarica mie liste
                    loadMyLists()
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

    // segui lista pubblica
    fun followList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "follow lista: $listId")
                val body = mapOf("userId" to userId)
                val response = ApiService.apiInterface.followList(listId, body)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista seguita")
                    // ricarica liste pubbliche e seguite
                    loadPublicLists()
                    loadFollowedLists()
                    onSuccess()
                } else {
                    val errorMsg = "errore follow: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione followlist", e)
                onError("errore: ${e.message}")
            }
        }
    }

    // smetti di seguire lista
    fun unfollowList(listId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "unfollow lista: $listId")
                val response = ApiService.apiInterface.unfollowList(listId, userId)

                if (response.isSuccessful) {
                    Log.d(TAG, "lista non seguita piu")
                    // ricarica liste pubbliche e seguite
                    loadPublicLists()
                    loadFollowedLists()
                    onSuccess()
                } else {
                    val errorMsg = "errore unfollow: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione unfollowlist", e)
                onError("errore: ${e.message}")
            }
        }
    }

    // copia lista pubblica nelle proprie liste
    fun copyList(listId: String, newName: String?, onSuccess: (MovieList) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    onError("utente non autenticato")
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
                    // ricarica mie liste per visualizzare copia
                    loadMyLists()
                    onSuccess(copiedList)
                } else {
                    val errorMsg = "errore copia: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "eccezione copylist", e)
                onError("errore: ${e.message}")
            }
        }
    }
}