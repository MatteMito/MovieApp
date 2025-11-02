//file: app/src/main/java/com/example/movieapp/ui/social/ListDetailFragment.kt
//fragment per dettaglio lista con fab che apre dialog

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.data.models.Movie
import com.example.movieapp.databinding.FragmentListDetailBinding

class ListDetailFragment : Fragment() {

    private val TAG = "ListDetailFragment"

    private var _binding: FragmentListDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel
    private lateinit var moviesAdapter: ListMoviesAdapter

    private var listId: String = ""
    private var listName: String = ""
    private var isOwner: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listId = arguments?.getString("listId") ?: ""
        listName = arguments?.getString("listName") ?: ""
        isOwner = arguments?.getBoolean("isOwner") ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListDetailBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[ListDetailViewModel::class.java]

        setupUI()
        setupObservers()

        if (listId.isNotEmpty()) {
            viewModel.loadList(listId)
        } else {
            Toast.makeText(requireContext(), "Errore: Lista non trovata", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return binding.root
    }

    private fun setupUI() {
        //toolbar
        binding.toolbar.title = listName
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        //setup movies recyclerview
        moviesAdapter = ListMoviesAdapter { movie ->
            if (isOwner) {
                confirmRemoveMovie(movie)
            }
        }

        binding.recyclerMovies.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }

        //mostra/nascondi bottoni e fab se proprietario
        if (isOwner) {
            binding.fabAddMovie.visibility = View.VISIBLE
            binding.btnEditList.visibility = View.VISIBLE
            binding.btnDeleteList.visibility = View.VISIBLE
        } else {
            binding.fabAddMovie.visibility = View.GONE
            binding.btnEditList.visibility = View.GONE
            binding.btnDeleteList.visibility = View.GONE
        }

        //fab apre dialog aggiungi film
        binding.fabAddMovie.setOnClickListener {
            openAddMoviesDialog()
        }

        //bottone modifica lista
        binding.btnEditList.setOnClickListener {
            val list = viewModel.currentList.value
            if (list != null) {
                val dialog = EditListDialogFragment.newInstance(
                    listId = list.id,
                    currentName = list.name,
                    currentDescription = list.description ?: "",
                    isPublic = list.isPublic
                )
                dialog.show(childFragmentManager, "EditListDialog")
            }
        }

        //bottone elimina lista
        binding.btnDeleteList.setOnClickListener {
            confirmDeleteList()
        }
    }

    private fun setupObservers() {
        //loading
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
        }

        //list data
        viewModel.currentList.observe(viewLifecycleOwner) { list ->
            binding.textDescription.text = list.description ?: "Nessuna descrizione"
            binding.textDescription.isVisible = true

            //badge visibilita
            binding.badgeVisibility.text = if (list.isPublic) "PUBBLICA" else "PRIVATA"
            binding.badgeVisibility.visibility = View.VISIBLE

            binding.textMovieCount.text = "${list.movies.size} film"

            //mostra titolo sezione film se ci sono film
            binding.tvFilmTitle.isVisible = list.movies.isNotEmpty()

            moviesAdapter.submitList(list.movies)
            binding.textEmpty.isVisible = list.movies.isEmpty()
        }

        //errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openAddMoviesDialog() {
        val dialog = AddMoviesDialogFragment.newInstance(listId)
        dialog.show(childFragmentManager, "AddMoviesDialog")

        //ricarica lista dopo chiusura dialog
        childFragmentManager.setFragmentResultListener("movies_added", viewLifecycleOwner) { _, _ ->
            viewModel.loadList(listId)
        }
    }

    private fun confirmRemoveMovie(movie: Movie) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rimuovi Film")
            .setMessage("Vuoi rimuovere \"${movie.title}\" dalla lista?")
            .setPositiveButton("Rimuovi") { _, _ ->
                viewModel.removeMovie(listId, movie.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Film rimosso", Toast.LENGTH_SHORT).show()
                        viewModel.loadList(listId)
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confirmDeleteList() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Vuoi eliminare questa lista? Questa azione non può essere annullata.")
            .setPositiveButton("Elimina") { _, _ ->
                viewModel.deleteList(listId,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Lista eliminata", Toast.LENGTH_SHORT).show()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}