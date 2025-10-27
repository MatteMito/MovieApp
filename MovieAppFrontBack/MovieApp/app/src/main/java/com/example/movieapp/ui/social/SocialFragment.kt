package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentSocialBinding
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.network.ApiService
import android.util.Log
import com.google.android.material.tabs.TabLayout

class SocialFragment : Fragment() {
    private val TAG = "SocialFragment"

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SocialViewModel
    private lateinit var myListsAdapter: SocialListAdapter
    private lateinit var publicListsAdapter: SocialListAdapter

    private var currentTab = 0 // 0 = Le Mie Liste, 1 = Liste Pubbliche

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[SocialViewModel::class.java]

        setupUI()
        setupObservers()

        // Verifica autenticazione
        if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
            showNotAuthenticatedState()
            return binding.root
        }

        viewModel.initialize(requireContext())
        Log.d(TAG, "SocialFragment creato")
        return binding.root
    }

    // ===== SETUP UI =====

    private fun setupUI() {
        setupTabs()
        setupRecyclerViews()
        setupSearchView()
        setupButtons()
        setupSwipeRefresh()
    }

    /**
     * Setup Tab Layout
     */
    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        currentTab = 0
                        showMyLists()
                    }
                    1 -> {
                        currentTab = 1
                        showPublicLists()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * Setup RecyclerView
     */
    private fun setupRecyclerViews() {
        // Adapter Le Mie Liste
        myListsAdapter = SocialListAdapter(
            onItemClick = { list -> navigateToListDetail(list) },
            onEditClick = { list -> navigateToEditList(list) },
            onDeleteClick = { list -> confirmDeleteList(list) },
            isMyList = true
        )

        binding.recyclerMyLists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = myListsAdapter
        }

        // Adapter Liste Pubbliche
        publicListsAdapter = SocialListAdapter(
            onItemClick = { list -> navigateToListDetail(list) },
            onFollowClick = { list -> followList(list) },
            isMyList = false
        )

        binding.recyclerPublicLists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = publicListsAdapter
        }
    }

    /**
     * Setup SearchView - MIGLIORATA
     */
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.applySearchFilter(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.applySearchFilter(newText ?: "")
                return true
            }
        })

        // Icona clear nella SearchView
        binding.searchView.setOnCloseListener {
            viewModel.clearSearchFilter()
            false
        }
    }

    /**
     * Setup bottoni
     */
    private fun setupButtons() {
        // FAB Crea Lista
        binding.fabCreateList.setOnClickListener {
            navigateToCreateList()
        }

        // Bottone "Crea Prima Lista" (empty state)
        binding.buttonCreateFirst.setOnClickListener {
            navigateToCreateList()
        }
    }

    /**
     * Setup SwipeRefresh
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshLists()
        }
    }

    // ===== OBSERVERS =====

    private fun setupObservers() {
        // Le Mie Liste (filtrate)
        viewModel.filteredMyLists.observe(viewLifecycleOwner) { lists ->
            updateMyListsUI(lists)
        }

        // Liste Pubbliche (filtrate)
        viewModel.filteredPublicLists.observe(viewLifecycleOwner) { lists ->
            updatePublicListsUI(lists)
        }

        // Loading
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        // Errori
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    // ===== AGGIORNAMENTO UI =====

    /**
     * Aggiorna UI Le Mie Liste
     */
    private fun updateMyListsUI(lists: List<MovieList>) {
        myListsAdapter.submitList(lists)

        val isEmpty = lists.isEmpty()
        binding.emptyStateMyLists.isVisible = isEmpty
        binding.recyclerMyLists.isVisible = !isEmpty

        // Mostra SearchView solo se ci sono liste
        updateSearchViewVisibility()

        Log.d(TAG, "📋 Le Mie Liste: ${lists.size}")
    }

    /**
     * Aggiorna UI Liste Pubbliche
     */
    private fun updatePublicListsUI(lists: List<MovieList>) {
        publicListsAdapter.submitList(lists)

        val isEmpty = lists.isEmpty()
        binding.emptyStatePublicLists.isVisible = isEmpty
        binding.recyclerPublicLists.isVisible = !isEmpty

        // Mostra SearchView solo se ci sono liste
        updateSearchViewVisibility()

        Log.d(TAG, "🌍 Liste Pubbliche: ${lists.size}")
    }

    /**
     * Mostra/nascondi SearchView in base a contenuto - KEY FIX! 🔥
     */
    private fun updateSearchViewVisibility() {
        val myListsCount = viewModel.myLists.value?.size ?: 0
        val publicListsCount = viewModel.publicLists.value?.size ?: 0

        // Mostra SearchView solo se almeno un tab ha contenuto
        val shouldShowSearch = when (currentTab) {
            0 -> myListsCount > 0  // Tab "Le Mie Liste"
            1 -> publicListsCount > 0  // Tab "Liste Pubbliche"
            else -> false
        }

        binding.searchFilterContainer.isVisible = shouldShowSearch
    }

    /**
     * Mostra tab Le Mie Liste
     */
    private fun showMyLists() {
        binding.recyclerMyLists.isVisible = true
        binding.emptyStateMyLists.isVisible = false
        binding.recyclerPublicLists.isVisible = false
        binding.emptyStatePublicLists.isVisible = false
        binding.textNotAuthenticated.isVisible = false

        // Aggiorna visibilità in base ai dati
        val lists = viewModel.filteredMyLists.value ?: emptyList()
        updateMyListsUI(lists)
    }

    /**
     * Mostra tab Liste Pubbliche
     */
    private fun showPublicLists() {
        binding.recyclerMyLists.isVisible = false
        binding.emptyStateMyLists.isVisible = false
        binding.recyclerPublicLists.isVisible = true
        binding.emptyStatePublicLists.isVisible = false
        binding.textNotAuthenticated.isVisible = false

        // Aggiorna visibilità in base ai dati
        val lists = viewModel.filteredPublicLists.value ?: emptyList()
        updatePublicListsUI(lists)
    }

    /**
     * Mostra stato non autenticato
     */
    private fun showNotAuthenticatedState() {
        binding.recyclerMyLists.isVisible = false
        binding.recyclerPublicLists.isVisible = false
        binding.emptyStateMyLists.isVisible = false
        binding.emptyStatePublicLists.isVisible = false
        binding.searchFilterContainer.isVisible = false
        binding.fabCreateList.isVisible = false
        binding.textNotAuthenticated.isVisible = true
        binding.textNotAuthenticated.text = "⚠️ Effettua il login per accedere alle liste"
    }

    // ===== AZIONI =====

    private fun navigateToListDetail(list: MovieList) {
        val action = SocialFragmentDirections.actionNavigationSocialToListDetailFragment(list.id)
        findNavController().navigate(action)
    }

    private fun navigateToCreateList() {
        val action = SocialFragmentDirections.actionNavigationSocialToCreateListDialog()
        findNavController().navigate(action)
    }

    private fun navigateToEditList(list: MovieList) {
        val action = SocialFragmentDirections.actionNavigationSocialToEditListDialog(list.id)
        findNavController().navigate(action)
    }

    private fun confirmDeleteList(list: MovieList) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Vuoi davvero eliminare \"${list.name}\"?")
            .setPositiveButton("Elimina") { _, _ ->
                deleteList(list)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteList(list: MovieList) {
        viewModel.deleteList(
            listId = list.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Lista eliminata", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun followList(list: MovieList) {
        viewModel.followList(
            listId = list.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Ora segui questa lista!", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }

    // ===== LIFECYCLE =====

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}