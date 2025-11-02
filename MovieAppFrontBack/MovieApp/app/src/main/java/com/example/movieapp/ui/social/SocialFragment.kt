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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.databinding.FragmentSocialBinding
import com.google.android.material.tabs.TabLayout

class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SocialViewModel by activityViewModels()

    private lateinit var myListsAdapter: SocialListAdapter
    private lateinit var publicListsAdapter: SocialListAdapter

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
    }

    private fun setupViews() {
        //fab create list
        binding.fabCreateList.setOnClickListener {
            showCreateListDialog()
        }

        //tabs
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Le Mie Liste"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Liste Pubbliche"))

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

        //setup adapters
        myListsAdapter = SocialListAdapter(
            onListClick = { list ->
                openListDetail(list.id, list.name, true)
            },
            onEditClick = { list -> showEditListDialog(list) },
            onDeleteClick = { list -> confirmDeleteList(list.id) },
            onFollowClick = null,
            onCopyClick = null
        )

        publicListsAdapter = SocialListAdapter(
            onListClick = { list ->
                openListDetail(list.id, list.name, false)
            },
            onEditClick = null,
            onDeleteClick = null,
            onFollowClick = { list -> followList(list) },
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

        //swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshLists()
        }

        //bottone ordina
        binding.btnSort.setOnClickListener {
            showSortMenu(it)
        }

        //bottone filtra (per ora stesso del sort)
        binding.btnFilter.setOnClickListener {
            showFilterMenu(it)
        }

        //barra di ricerca
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                viewModel.applySearchFilter(query)
            }
        })

        //show my lists by default
        showMyLists()
    }

    private fun setupObservers() {
        //loading
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        //filtered my lists
        viewModel.filteredMyLists.observe(viewLifecycleOwner) { lists ->
            myListsAdapter.submitList(lists)
            binding.tvEmptyMyLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        //filtered public lists
        viewModel.filteredPublicLists.observe(viewLifecycleOwner) { lists ->
            publicListsAdapter.submitList(lists)
            binding.tvEmptyPublicLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        //errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMyLists() {
        binding.containerMyLists.visibility = View.VISIBLE
        binding.containerPublicLists.visibility = View.GONE
    }

    private fun showPublicLists() {
        binding.containerMyLists.visibility = View.GONE
        binding.containerPublicLists.visibility = View.VISIBLE
    }

    private fun showCreateListDialog() {
        val dialog = EditListDialogFragment.newInstance(
            listId = null,
            currentName = "",
            currentDescription = "",
            isPublic = false
        )
        dialog.show(childFragmentManager, "CreateListDialog")
    }

    private fun showEditListDialog(list: MovieList) {
        val dialog = EditListDialogFragment.newInstance(
            listId = list.id,
            currentName = list.name,
            currentDescription = list.description ?: "",
            isPublic = list.isPublic
        )
        dialog.show(childFragmentManager, "EditListDialog")
    }

    private fun openListDetail(listId: String, listName: String, isOwner: Boolean) {
        val bundle = bundleOf(
            "listId" to listId,
            "listName" to listName,
            "isOwner" to isOwner
        )
        findNavController().navigate(R.id.action_social_to_listDetail, bundle)
    }

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

    private fun executeCopyList(listId: String, newName: String?) {
        viewModel.copyList(listId, newName,
            onSuccess = { copiedList ->
                Toast.makeText(requireContext(), "Lista copiata: ${copiedList.name}", Toast.LENGTH_SHORT).show()
                viewModel.refreshLists()
                //passa alla tab le mie liste
                binding.tabLayout.getTabAt(0)?.select()
            },
            onError = { error ->
                Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Nome (A-Z)")
        popup.menu.add(0, 2, 0, "Nome (Z-A)")
        popup.menu.add(0, 3, 0, "Più recenti")
        popup.menu.add(0, 4, 0, "Più vecchie")
        popup.menu.add(0, 5, 0, "Più popolari")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> viewModel.applySortFilter(SocialViewModel.SortType.NAME_ASC)
                2 -> viewModel.applySortFilter(SocialViewModel.SortType.NAME_DESC)
                3 -> viewModel.applySortFilter(SocialViewModel.SortType.DATE_DESC)
                4 -> viewModel.applySortFilter(SocialViewModel.SortType.DATE_ASC)
                5 -> viewModel.applySortFilter(SocialViewModel.SortType.POPULARITY)
            }
            true
        }

        popup.show()
    }

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
        _binding = null
    }
}