//file: app/src/main/java/com/example/movieapp/ui/social/AddMoviesDialogFragment.kt
//dialog per aggiungere film alla lista con aggiunta diretta al click

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

    private var listId: String = ""
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
        setStyle(STYLE_NORMAL, R.style.Theme_MovieApp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddMoviesBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(requireParentFragment())[ListDetailViewModel::class.java]
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

        //adapter ricerca con aggiunta diretta al click
        searchAdapter = SearchMovieAdapter { movie ->
            addMovieDirectly(movie)
        }

        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        //nascondi sezioni non necessarie
        binding.tvSelectedTitle.isVisible = false
        binding.recyclerSelectedMovies.isVisible = false
        binding.btnSave.isVisible = false

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
    }

    private fun setupObservers() {
        //risultati ricerca con filtro film gia presenti
        viewModel.searchResults.observe(viewLifecycleOwner) { allMovies ->
            //ottieni lista corrente per escludere film gia presenti
            val currentList = viewModel.currentList.value
            val existingMovieIds = currentList?.movies?.map { it.id }?.toSet() ?: emptySet()

            //filtra escludendo film gia presenti nella lista
            val filteredMovies = allMovies.filter { movie ->
                !existingMovieIds.contains(movie.id)
            }

            searchAdapter.submitList(filteredMovies)
            binding.recyclerSearchResults.isVisible = filteredMovies.isNotEmpty()

            //mostra messaggio se tutti i risultati sono gia nella lista
            if (allMovies.isNotEmpty() && filteredMovies.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "tutti i film trovati sono gia nella lista",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        //loading
        viewModel.searchLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressSearch.isVisible = loading
        }
    }

    //aggiunge film direttamente alla lista al click
    private fun addMovieDirectly(movie: Movie) {
        //controlla se gia aggiunto in questa sessione
        if (addedMovieIds.contains(movie.id)) {
            Toast.makeText(requireContext(), "${movie.title} già aggiunto", Toast.LENGTH_SHORT).show()
            return
        }

        //disabilita temporaneamente per evitare doppi click
        binding.progressSearch.isVisible = true

        viewModel.addMovie(
            listId = listId,
            movie = movie,
            onSuccess = {
                addedMovieIds.add(movie.id)
                binding.progressSearch.isVisible = false
                Toast.makeText(requireContext(), "${movie.title} aggiunto!", Toast.LENGTH_SHORT).show()

                //pulisci ricerca dopo aggiunta
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
        _binding = null
    }
}