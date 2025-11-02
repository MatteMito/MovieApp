//file: app/src/main/java/com/example/movieapp/ui/social/AddMoviesDialogFragment.kt
//dialog per aggiungere film alla lista

package com.example.movieapp.ui.social

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.data.models.Movie
import com.example.movieapp.databinding.DialogAddMoviesBinding

class AddMoviesDialogFragment : DialogFragment() {

    private var _binding: DialogAddMoviesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel
    private lateinit var searchAdapter: SearchMovieAdapter
    private lateinit var selectedAdapter: SearchMovieAdapter

    private var listId: String = ""
    private val selectedMovies = mutableListOf<Movie>()

    companion object {
        fun newInstance(listId: String): AddMoviesDialogFragment {
            val fragment = AddMoviesDialogFragment()
            val args = Bundle().apply {
                putString("listId", listId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listId = arguments?.getString("listId") ?: ""
        setStyle(STYLE_NORMAL, R.style.Theme_MovieApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddMoviesBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[ListDetailViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        //toolbar
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }

        //adapter ricerca
        searchAdapter = SearchMovieAdapter { movie ->
            addMovieToSelection(movie)
        }

        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        //adapter film selezionati
        selectedAdapter = SearchMovieAdapter { movie ->
            removeMovieFromSelection(movie)
        }

        binding.recyclerSelectedMovies.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = selectedAdapter
        }

        //ricerca
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

        //bottone salva
        binding.btnSave.setOnClickListener {
            saveMoviesToList()
        }
    }

    private fun setupObservers() {
        //risultati ricerca
        viewModel.searchResults.observe(viewLifecycleOwner) { movies ->
            searchAdapter.submitList(movies)
            binding.recyclerSearchResults.isVisible = movies.isNotEmpty()
        }

        //loading
        viewModel.searchLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressSearch.isVisible = loading
        }
    }

    private fun addMovieToSelection(movie: Movie) {
        if (!selectedMovies.any { it.id == movie.id }) {
            selectedMovies.add(movie)
            updateSelectedMovies()
        }
    }

    private fun removeMovieFromSelection(movie: Movie) {
        selectedMovies.removeAll { it.id == movie.id }
        updateSelectedMovies()
    }

    private fun updateSelectedMovies() {
        selectedAdapter.submitList(selectedMovies.toList())
        binding.tvSelectedTitle.isVisible = selectedMovies.isNotEmpty()
        binding.recyclerSelectedMovies.isVisible = selectedMovies.isNotEmpty()
        binding.btnSave.isVisible = selectedMovies.isNotEmpty()
    }

    private fun saveMoviesToList() {
        if (selectedMovies.isEmpty()) {
            Toast.makeText(requireContext(), "Seleziona almeno un film", Toast.LENGTH_SHORT).show()
            return
        }

        var savedCount = 0
        var errorOccurred = false

        selectedMovies.forEach { movie ->
            viewModel.addMovie(listId, movie.id,
                onSuccess = {
                    savedCount++
                    if (savedCount == selectedMovies.size && !errorOccurred) {
                        Toast.makeText(requireContext(), "$savedCount film aggiunti", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                },
                onError = { error ->
                    errorOccurred = true
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}