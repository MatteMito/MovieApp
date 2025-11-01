// file: app/src/main/java/com/example/movieapp/ui/social/SocialFragment.kt
// fragment principale per liste sociali

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
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

        //setup recyclerviews
        myListsAdapter = SocialListAdapter(
            onListClick = { list ->
                //apri dettaglio lista
                Toast.makeText(context, "Apri: ${list.name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { list ->
                deleteList(list.id)
            },
            onCopyClick = { list ->
                copyList(list.id, list.name)
            },
            showCopyButton = false
        )

        publicListsAdapter = SocialListAdapter(
            onListClick = { list ->
                //apri dettaglio lista
                Toast.makeText(context, "Apri: ${list.name}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = null, //non si possono eliminare liste pubbliche altrui
            onCopyClick = { list ->
                copyList(list.id, list.name)
            },
            showCopyButton = true
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

        //mostra le mie liste di default
        showMyLists()
    }

    private fun setupObservers() {
        //mie liste
        viewModel.filteredMyLists.observe(viewLifecycleOwner) { lists ->
            myListsAdapter.submitList(lists)
            binding.tvEmptyMyLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        //liste pubbliche
        viewModel.filteredPublicLists.observe(viewLifecycleOwner) { lists ->
            publicListsAdapter.submitList(lists)
            binding.tvEmptyPublicLists.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        }

        //loading
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        //error
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun showMyLists() {
        binding.recyclerMyLists.visibility = View.VISIBLE
        binding.recyclerPublicLists.visibility = View.GONE
        binding.tvEmptyMyLists.visibility = if (myListsAdapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.tvEmptyPublicLists.visibility = View.GONE
    }

    private fun showPublicLists() {
        binding.recyclerMyLists.visibility = View.GONE
        binding.recyclerPublicLists.visibility = View.VISIBLE
        binding.tvEmptyMyLists.visibility = View.GONE
        binding.tvEmptyPublicLists.visibility = if (publicListsAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun showCreateListDialog() {
        val dialog = CreateListDialogFragment()
        dialog.show(parentFragmentManager, "CreateListDialog")
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
        val newName = "$originalName (Copia)"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}