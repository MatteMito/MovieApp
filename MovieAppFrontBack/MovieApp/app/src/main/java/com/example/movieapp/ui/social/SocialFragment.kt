// FILE: app/src/main/java/com/example/movieapp/ui/social/SocialFragment.kt
// Fragment principale per aspetto social con liste pubbliche/private

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

/**
 * Fragment Social - Hub delle liste film
 *
 * Features:
 * - Tab: Le Mie Liste / Liste Pubbliche
 * - Creazione nuove liste (pubblica/privata)
 * - Visualizzazione e gestione liste
 * - Follow liste pubbliche
 * - Navigazione a dettaglio lista
 */
class SocialFragment : Fragment() {
    private val TAG = "SocialFragment"

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SocialViewModel

    private lateinit var myListsAdapter: SocialListAdapter
    private lateinit var publicListsAdapter: SocialListAdapter

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

        // Inizializza ViewModel
        viewModel.initialize(requireContext())

        Log.d(TAG, "SocialFragment creato")
        return binding.root
    }

    private fun setupUI() {
        setupTabs()
        setupRecyclerViews()
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
                    0 -> showMyLists()
                    1 -> showPublicLists()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * Setup RecyclerViews
     */
    private fun setupRecyclerViews() {
        // Adapter Le Mie Liste
        myListsAdapter = SocialListAdapter(
            onItemClick = { list -> openListDetail(list) },
            onEditClick = { list -> editList(list) },
            onDeleteClick = { list -> deleteList(list) },
            showActions = true
        )

        binding.recyclerMyLists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = myListsAdapter
        }

        // Adapter Liste Pubbliche
        publicListsAdapter = SocialListAdapter(
            onItemClick = { list -> openListDetail(list) },
            onFollowClick = { list -> followList(list) },
            showActions = false
        )

        binding.recyclerPublicLists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = publicListsAdapter
        }
    }

    /**
     * Setup Buttons
     */
    private fun setupButtons() {
        binding.fabCreateList.setOnClickListener {
            openCreateListDialog()
        }

        binding.buttonCreateFirst.setOnClickListener {
            openCreateListDialog()
        }
    }

    /**
     * Setup Swipe Refresh
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshLists()
        }
    }

    /**
     * Setup Observers
     */
    private fun setupObservers() {
        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading

            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }

        // Le Mie Liste
        viewModel.myLists.observe(viewLifecycleOwner) { lists ->
            myListsAdapter.submitList(lists)

            if (lists.isEmpty() && binding.tabLayout.selectedTabPosition == 0) {
                binding.emptyStateMyLists.visibility = View.VISIBLE
                binding.recyclerMyLists.visibility = View.GONE
            } else {
                binding.emptyStateMyLists.visibility = View.GONE
                binding.recyclerMyLists.visibility = View.VISIBLE
            }

            Log.d(TAG, "✅ ${lists.size} liste personali caricate")
        }

        // Liste Pubbliche
        viewModel.publicLists.observe(viewLifecycleOwner) { lists ->
            publicListsAdapter.submitList(lists)

            if (lists.isEmpty() && binding.tabLayout.selectedTabPosition == 1) {
                binding.emptyStatePublicLists.visibility = View.VISIBLE
                binding.recyclerPublicLists.visibility = View.GONE
            } else {
                binding.emptyStatePublicLists.visibility = View.GONE
                binding.recyclerPublicLists.visibility = View.VISIBLE
            }

            Log.d(TAG, "✅ ${lists.size} liste pubbliche caricate")
        }

        // Errori
        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Mostra tab Le Mie Liste
     */
    private fun showMyLists() {
        binding.recyclerMyLists.visibility = View.VISIBLE
        binding.recyclerPublicLists.visibility = View.GONE
        binding.emptyStatePublicLists.visibility = View.GONE

        if (myListsAdapter.itemCount == 0) {
            binding.emptyStateMyLists.visibility = View.VISIBLE
        } else {
            binding.emptyStateMyLists.visibility = View.GONE
        }
    }

    /**
     * Mostra tab Liste Pubbliche
     */
    private fun showPublicLists() {
        binding.recyclerMyLists.visibility = View.GONE
        binding.recyclerPublicLists.visibility = View.VISIBLE
        binding.emptyStateMyLists.visibility = View.GONE

        if (publicListsAdapter.itemCount == 0) {
            binding.emptyStatePublicLists.visibility = View.VISIBLE
        } else {
            binding.emptyStatePublicLists.visibility = View.GONE
        }
    }

    /**
     * Dialog creazione nuova lista
     */
    private fun openCreateListDialog() {
        val dialog = CreateListDialogFragment()
        dialog.setOnListCreatedListener { name, description, isPublic ->
            viewModel.createList(name, description, isPublic)
        }
        dialog.show(childFragmentManager, "CreateListDialog")
    }

    /**
     * Apri dettaglio lista
     */
    private fun openListDetail(list: MovieList) {
        Log.d(TAG, "📄 Apertura lista: ${list.name}")

        val bundle = Bundle().apply {
            putString("list_id", list.id)
            putString("list_name", list.name)
            putBoolean("is_owner", list.userId == ApiService.getCurrentUserId())
        }

        findNavController().navigate(
            R.id.action_social_to_listDetail,
            bundle
        )
    }

    /**
     * Modifica lista
     */
    private fun editList(list: MovieList) {
        val dialog = EditListDialogFragment.newInstance(list)
        dialog.setOnListUpdatedListener { name, description, isPublic ->
            viewModel.updateList(list.id, name, description, isPublic)
        }
        dialog.show(childFragmentManager, "EditListDialog")
    }

    /**
     * Elimina lista con conferma
     */
    private fun deleteList(list: MovieList) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Sei sicuro di voler eliminare \"${list.name}\"?\nQuesta azione è irreversibile.")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteList(list.id)
                Toast.makeText(requireContext(), "Lista eliminata", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Segui lista pubblica
     */
    private fun followList(list: MovieList) {
        viewModel.followList(list.id)
        Toast.makeText(
            requireContext(),
            "Ora segui: ${list.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Mostra stato non autenticato
     */
    private fun showNotAuthenticatedState() {
        binding.apply {
            tabLayout.visibility = View.GONE
            fabCreateList.visibility = View.GONE
            recyclerMyLists.visibility = View.GONE
            recyclerPublicLists.visibility = View.GONE
            emptyStateMyLists.visibility = View.GONE
            emptyStatePublicLists.visibility = View.GONE

            // Mostra messaggio
            textNotAuthenticated.visibility = View.VISIBLE
            textNotAuthenticated.text = "⚠️ Effettua il login per accedere alle liste"
        }
    }

    override fun onResume() {
        super.onResume()

        // Ricarica liste quando si torna al fragment
        if (ApiService.isAuthenticated() && ApiService.hasUserId()) {
            viewModel.refreshLists()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}