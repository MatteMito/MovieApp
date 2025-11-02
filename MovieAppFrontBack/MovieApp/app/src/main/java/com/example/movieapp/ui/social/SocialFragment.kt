//file: app/src/main/java/com/example/movieapp/ui/social/SocialFragment.kt
//fragment principale per liste sociali

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
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
                showListOptionsDialog(list.id, list.name, true)
            },
            onDeleteClick = { list -> confirmDeleteList(list.id) },
            onCopyClick = null
        )

        publicListsAdapter = SocialListAdapter(
            onListClick = { list ->
                showListOptionsDialog(list.id, list.name, false)
            },
            onDeleteClick = null,
            onCopyClick = { list -> copyList(list.id, list.name) }
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
        val dialog = CreateListDialogFragment()
        dialog.show(childFragmentManager, "CreateListDialog")
    }

    private fun showListOptionsDialog(listId: String, listName: String, isOwner: Boolean) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Apri: $listName")
            .setMessage("Vuoi aprire questa lista?")
            .setPositiveButton("Apri") { _, _ ->
                navigateToListDetail(listId, listName, isOwner)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun navigateToListDetail(listId: String, listName: String, isOwner: Boolean) {
        val bundle = Bundle().apply {
            putString("listId", listId)
            putString("listName", listName)
            putBoolean("isOwner", isOwner)
        }

        findNavController().navigate(R.id.listDetailFragment, bundle)
    }

    private fun confirmDeleteList(listId: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Vuoi eliminare questa lista?")
            .setPositiveButton("Elimina") { _, _ ->
                deleteList(listId)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteList(listId: String) {
        viewModel.deleteList(
            listId = listId,
            onSuccess = {
                Toast.makeText(context, "Lista eliminata", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(context, "Errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun copyList(listId: String, originalName: String) {
        val newName = "Copia di $originalName"
        viewModel.copyList(
            listId = listId,
            newName = newName,
            onSuccess = { copiedList ->
                Toast.makeText(context, "Lista copiata: ${copiedList.name}", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(context, "Errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLists()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}