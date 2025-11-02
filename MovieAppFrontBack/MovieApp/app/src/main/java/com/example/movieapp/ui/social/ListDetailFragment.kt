//file: app/src/main/java/com/example/movieapp/ui/social/ListDetailFragment.kt
//fragment per dettaglio lista con autocomplete semplificato

package com.example.movieapp.ui.social

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var searchAdapter: SearchMovieAdapter

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

        //setup search recyclerview con click diretto
        searchAdapter = SearchMovieAdapter { movie ->
            addMovieToList(movie)
        }

        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        //mostra/nascondi bottoni se proprietario
        if (isOwner) {
            binding.fabAddMovie.visibility = View.VISIBLE
            binding.btnEditList.visibility = View.VISIBLE
            binding.btnDeleteList.visibility = View.VISIBLE
        } else {
            binding.fabAddMovie.visibility = View.GONE
            binding.btnEditList.visibility = View.GONE
            binding.btnDeleteList.visibility = View.GONE
        }

        //fab aggiungi film
        binding.fabAddMovie.setOnClickListener {
            toggleSearchMode()
        }

        //search autocomplete
        binding.etSearchMovie.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.length >= 2) {
                    viewModel.searchMovies(query)
                } else {
                    viewModel.clearSearchResults()
                }
            }
        })

        //bottone close search
        binding.btnCloseSearch.setOnClickListener {
            hideSearchMode()
        }

        //bottone edit list
        binding.btnEditList.setOnClickListener {
            showEditListDialog()
        }

        //bottone delete list
        binding.btnDeleteList.setOnClickListener {
            confirmDeleteList()
        }
    }

    private fun setupObservers() {
        //loading
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        //lista dettagli
        viewModel.list.observe(viewLifecycleOwner) { list ->
            binding.toolbar.title = list.name

            binding.textDescription.text = list.description ?: "Nessuna descrizione"
            binding.textDescription.isVisible = true

            //badge visibilita
            binding.badgeVisibility.text = if (list.isPublic) "PUBBLICA" else "PRIVATA"
            binding.badgeVisibility.visibility = View.VISIBLE

            binding.textMovieCount.text = "${list.movies.size} film"

            moviesAdapter.submitList(list.movies)
            binding.textEmpty.isVisible = list.movies.isEmpty()
        }

        //search results
        viewModel.searchResults.observe(viewLifecycleOwner) { movies ->
            searchAdapter.submitList(movies)
            binding.recyclerSearchResults.isVisible = movies.isNotEmpty()
        }

        //search loading
        viewModel.searchLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressSearch.isVisible = loading
        }

        //errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleSearchMode() {
        if (binding.searchContainer.visibility == View.VISIBLE) {
            hideSearchMode()
        } else {
            showSearchMode()
        }
    }

    private fun showSearchMode() {
        binding.searchContainer.visibility = View.VISIBLE
        binding.etSearchMovie.requestFocus()

        //mostra tastiera
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.etSearchMovie, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearchMode() {
        binding.searchContainer.visibility = View.GONE
        binding.etSearchMovie.text?.clear()
        viewModel.clearSearchResults()

        //nascondi tastiera
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearchMovie.windowToken, 0)
    }

    private fun addMovieToList(movie: Movie) {
        viewModel.addMovie(listId, movie.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Film aggiunto: ${movie.title}", Toast.LENGTH_SHORT).show()
                hideSearchMode()
                viewModel.loadList(listId)
            },
            onError = { error ->
                Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun confirmRemoveMovie(movie: Movie) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rimuovi Film")
            .setMessage("Vuoi rimuovere \"${movie.title}\" dalla lista?")
            .setPositiveButton("Rimuovi") { _, _ ->
                removeMovie(movie)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun removeMovie(movie: Movie) {
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

    private fun showEditListDialog() {
        val currentList = viewModel.list.value ?: return

        val dialog = EditListDialogFragment.newInstance(
            listId = currentList.id,
            currentName = currentList.name,
            currentDescription = currentList.description ?: "",
            isPublic = currentList.isPublic
        )

        dialog.show(childFragmentManager, "EditListDialog")
    }

    private fun confirmDeleteList() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Vuoi eliminare questa lista? Questa azione non può essere annullata.")
            .setPositiveButton("Elimina") { _, _ ->
                deleteList()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun deleteList() {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}