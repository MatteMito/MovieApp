package com.example.movieapp.ui.social

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.databinding.FragmentSocialBinding
import com.google.android.material.tabs.TabLayout

// fragment principale per gestione liste condivise: mie liste, pubbliche, seguite
class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    // viewmodel activity-scoped per condividere stato tra fragment
    private val viewModel: SocialViewModel by activityViewModels()

    // tre adapter separati per le tre tab
    private lateinit var myListsAdapter: SocialListAdapter
    private lateinit var publicListsAdapter: SocialListAdapter
    private lateinit var followedListsAdapter: SocialListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initialize(requireContext())

        setupViews()
        setupObservers()
        setupNavigationListener()
    }

    // listener per refresh quando si torna indietro dalla schermata dettaglio
    private fun setupNavigationListener() {
        val navController = findNavController()
        val currentBackStackEntry = navController.currentBackStackEntry

        // ascolta flag refresh_lists dal back stack
        currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("refresh_lists")?.observe(
            viewLifecycleOwner
        ) { shouldRefresh ->
            if (shouldRefresh == true) {
                // ricarica le liste dopo modifiche
                viewModel.refreshLists()
                // resetta il flag
                currentBackStackEntry.savedStateHandle.remove<Boolean>("refresh_lists")
            }
        }
    }

    private fun setupViews() {
        // fab per creare nuova lista
        binding.fabCreateList.setOnClickListener {
            showCreateListDialog()
        }

        // setup tre tab: mie liste, pubbliche, seguite
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Le Mie Liste"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Liste Pubbliche"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Liste Seguite"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showMyLists()
                    1 -> showPublicLists()
                    2 -> showFollowedLists()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // adapter per mie liste: click, edit, delete
        myListsAdapter = SocialListAdapter(
            onListClick = { list ->
                openListDetail(list.id, list.name, true)
            },
            onEditClick = { list -> showEditListDialog(list) },
            onDeleteClick = { list -> confirmDeleteList(list.id) },
            onFollowClick = null,
            onUnfollowClick = null,
            onCopyClick = null
        )

        // adapter per liste pubbliche: click, follow, copy
        publicListsAdapter = SocialListAdapter(
            onListClick = { list ->
                openListDetail(list.id, list.name, false)
            },
            onEditClick = null,
            onDeleteClick = null,
            onFollowClick = { list -> followList(list) },
            onUnfollowClick = { list -> unfollowList(list) },
            onCopyClick = { list -> copyList(list) }
        )

        // adapter per liste seguite: click, unfollow, copy
        followedListsAdapter = SocialListAdapter(
            onListClick = { list ->
                openListDetail(list.id, list.name, false)
            },
            onEditClick = null,
            onDeleteClick = null,
            onFollowClick = null,
            onUnfollowClick = { list -> unfollowList(list) },
            onCopyClick = { list -> copyList(list) }
        )

        binding.recyclerMyLists.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = myListsAdapter
        }

        binding.recyclerPublicLists.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = publicListsAdapter
        }

        binding.recyclerFollowedLists.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = followedListsAdapter
        }

        // swipe to refresh per ricaricare liste
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshLists()
        }

        // bottone per menu ordinamento
        binding.btnSort.setOnClickListener {
            showSortMenu(it)
        }

        // bottone per menu filtro visibilità
        binding.btnFilter.setOnClickListener {
            showFilterMenu(it)
        }

        // barra ricerca con filtro real-time
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                viewModel.applySearchFilter(query)
            }
        })

        // mostra mie liste di default
        showMyLists()
    }

    private fun setupObservers() {
        // loading state per swipe refresh
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        // liste filtrate per ogni tab
        viewModel.filteredMyLists.observe(viewLifecycleOwner) { lists ->
            myListsAdapter.submitList(lists)
            binding.tvEmptyMyLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.filteredPublicLists.observe(viewLifecycleOwner) { lists ->
            publicListsAdapter.submitList(lists)
            binding.tvEmptyPublicLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.filteredFollowedLists.observe(viewLifecycleOwner) { lists ->
            followedListsAdapter.submitList(lists)
            binding.tvEmptyFollowedLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        // errori generici
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    // mostra/nasconde container per tab mie liste
    private fun showMyLists() {
        binding.containerMyLists.visibility = View.VISIBLE
        binding.containerPublicLists.visibility = View.GONE
        binding.containerFollowedLists.visibility = View.GONE
    }

    // mostra/nasconde container per tab liste pubbliche
    private fun showPublicLists() {
        binding.containerMyLists.visibility = View.GONE
        binding.containerPublicLists.visibility = View.VISIBLE
        binding.containerFollowedLists.visibility = View.GONE
    }

    // mostra/nasconde container per tab liste seguite
    private fun showFollowedLists() {
        binding.containerMyLists.visibility = View.GONE
        binding.containerPublicLists.visibility = View.GONE
        binding.containerFollowedLists.visibility = View.VISIBLE
    }

    // dialog per creare nuova lista
    private fun showCreateListDialog() {
        val dialog = EditListDialogFragment.newInstance(
            listId = null,
            currentName = "",
            currentDescription = "",
            isPublic = false
        )
        dialog.show(childFragmentManager, "CreateListDialog")
    }

    // dialog per modificare lista esistente
    private fun showEditListDialog(list: MovieList) {
        val dialog = EditListDialogFragment.newInstance(
            listId = list.id,
            currentName = list.name,
            currentDescription = list.description ?: "",
            isPublic = list.isPublic
        )
        dialog.show(childFragmentManager, "EditListDialog")
    }

    // naviga a schermata dettaglio lista
    private fun openListDetail(listId: String, listName: String, isOwner: Boolean) {
        val bundle = bundleOf(
            "listId" to listId,
            "listName" to listName,
            "isOwner" to isOwner
        )
        findNavController().navigate(R.id.action_social_to_listDetail, bundle)
    }

    // conferma eliminazione lista con dialog
    private fun confirmDeleteList(listId: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Vuoi eliminare questa lista? Questa azione non può essere annullata.")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteList(listId,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Lista eliminata", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // segui lista pubblica
    private fun followList(list: MovieList) {
        viewModel.followList(list.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Lista seguita!", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    // smetti di seguire lista con conferma
    private fun unfollowList(list: MovieList) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Smetti di seguire")
            .setMessage("Vuoi smettere di seguire la lista \"${list.name}\"?")
            .setPositiveButton("Conferma") { _, _ ->
                viewModel.unfollowList(list.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Non segui più questa lista", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // copia lista con possibilità di rinominarla
    private fun copyList(list: MovieList) {
        val input = android.widget.EditText(requireContext())
        input.hint = list.name

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Copia Lista")
            .setMessage("Inserisci un nuovo nome (opzionale):")
            .setView(input)
            .setPositiveButton("Copia") { _, _ ->
                val newName = input.text.toString().trim().ifBlank { null }
                executeCopyList(list.id, newName)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // esegue copia lista e passa a tab mie liste
    private fun executeCopyList(listId: String, newName: String?) {
        viewModel.copyList(listId, newName,
            onSuccess = { copiedList ->
                Toast.makeText(requireContext(), "Lista copiata: ${copiedList.name}", Toast.LENGTH_SHORT).show()
                viewModel.refreshLists()
                // passa alla tab mie liste per vedere lista copiata
                binding.tabLayout.getTabAt(0)?.select()
            },
            onError = { error ->
                Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    // menu popup per ordinamento liste
    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Nome (A-Z)")
        popup.menu.add(0, 2, 0, "Nome (Z-A)")
        popup.menu.add(0, 3, 0, "Più popolari")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> viewModel.applySortFilter(SocialViewModel.SortType.NAME_ASC)
                2 -> viewModel.applySortFilter(SocialViewModel.SortType.NAME_DESC)
                3 -> viewModel.applySortFilter(SocialViewModel.SortType.POPULARITY)
            }
            true
        }

        popup.show()
    }

    // menu popup per filtro visibilità liste
    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Tutte")
        popup.menu.add(0, 2, 0, "Solo pubbliche")
        popup.menu.add(0, 3, 0, "Solo private")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> viewModel.applyVisibilityFilter(null)
                2 -> viewModel.applyVisibilityFilter(true)
                3 -> viewModel.applyVisibilityFilter(false)
            }
            true
        }

        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // pulisce binding per evitare memory leak
        _binding = null
    }
}