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

// dialog fragment per aggiungere film a una lista condivisa tramite ricerca
class AddMoviesDialogFragment : DialogFragment() {

    private var _binding: DialogAddMoviesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel
    private lateinit var searchAdapter: SearchMovieAdapter

    private var listId: String = ""
    // traccia film aggiunti in questa sessione per evitare duplicati
    private val addedMovieIds = mutableSetOf<String>()

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
        // imposta tema fullscreen per dialog
        setStyle(STYLE_NORMAL, R.style.Theme_MovieApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddMoviesBinding.inflate(inflater, container, false)
        // usa viewmodel del fragment parent per condividere stato
        viewModel = ViewModelProvider(requireParentFragment())[ListDetailViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        // toolbar con bottone chiudi
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }

        // adapter con callback per aggiunta immediata al click
        searchAdapter = SearchMovieAdapter { movie ->
            addMovieDirectly(movie)
        }

        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        // nascondi sezioni non usate in questo dialog
        binding.tvSelectedTitle.isVisible = false
        binding.recyclerSelectedMovies.isVisible = false
        binding.btnSave.isVisible = false

        // ricerca con debounce minimo 2 caratteri
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
    }

    private fun setupObservers() {
        // risultati ricerca filtrati per escludere film già presenti nella lista
        viewModel.searchResults.observe(viewLifecycleOwner) { allMovies ->
            // ottieni film già presenti nella lista corrente
            val currentList = viewModel.currentList.value
            val existingMovieIds = currentList?.movies?.map { it.id }?.toSet() ?: emptySet()

            // filtra risultati escludendo film già nella lista
            val filteredMovies = allMovies.filter { movie ->
                !existingMovieIds.contains(movie.id)
            }

            searchAdapter.submitList(filteredMovies)
            binding.recyclerSearchResults.isVisible = filteredMovies.isNotEmpty()

            // mostra messaggio se tutti i risultati sono già presenti
            if (allMovies.isNotEmpty() && filteredMovies.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "tutti i film trovati sono gia nella lista",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // loading ricerca
        viewModel.searchLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressSearch.isVisible = loading
        }
    }

    // aggiunge film immediatamente alla lista al click
    private fun addMovieDirectly(movie: Movie) {
        // verifica se già aggiunto in questa sessione dialog
        if (addedMovieIds.contains(movie.id)) {
            Toast.makeText(requireContext(), "${movie.title} già aggiunto", Toast.LENGTH_SHORT).show()
            return
        }

        // mostra progress durante aggiunta
        binding.progressSearch.isVisible = true

        viewModel.addMovie(
            listId = listId,
            movie = movie,
            onSuccess = {
                // memorizza id per evitare doppi aggiunte
                addedMovieIds.add(movie.id)
                binding.progressSearch.isVisible = false
                Toast.makeText(requireContext(), "${movie.title} aggiunto!", Toast.LENGTH_SHORT).show()

                // pulisci campo ricerca dopo aggiunta
                binding.etSearchMovie.text?.clear()
            },
            onError = { error ->
                binding.progressSearch.isVisible = false
                Toast.makeText(requireContext(), "errore: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // pulisce binding per evitare memory leak
        _binding = null
    }
}