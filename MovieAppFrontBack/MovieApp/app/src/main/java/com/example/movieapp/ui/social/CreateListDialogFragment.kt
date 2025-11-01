// file: app/src/main/java/com/example/movieapp/ui/social/CreateListDialogFragment.kt
// dialog per creare nuova lista con autocomplete

package com.example.movieapp.ui.social

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.data.models.Movie
import com.example.movieapp.databinding.DialogCreateListBinding

class CreateListDialogFragment : DialogFragment() {

    private var _binding: DialogCreateListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SocialViewModel by activityViewModels()

    private lateinit var searchAdapter: SearchMovieAdapter
    private val selectedMovies = mutableListOf<Movie>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupObservers()
    }

    private fun setupViews() {
        //close button
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        //switch pubblico/privato
        binding.switchPublic.setOnCheckedChangeListener { _, isChecked ->
            binding.tvVisibility.text = if (isChecked) "Pubblica" else "Privata"
        }

        //setup autocomplete recyclerview
        searchAdapter = SearchMovieAdapter(
            onMovieClick = { movie ->
                addMovieToList(movie)
            }
        )

        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }

        //autocomplete text watcher
        binding.etSearchMovie.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.length >= 2) {
                    viewModel.searchMoviesAutocomplete(query)
                } else {
                    viewModel.clearAutocompleteResults()
                }
            }
        })

        //create button
        binding.btnCreate.setOnClickListener {
            createList()
        }
    }

    private fun setupObservers() {
        //autocomplete results
        viewModel.autocompleteResults.observe(viewLifecycleOwner) { movies ->
            searchAdapter.submitList(movies)
            binding.recyclerSearchResults.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        //autocomplete loading
        viewModel.autocompleteLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressAutocomplete.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun addMovieToList(movie: Movie) {
        if (!selectedMovies.any { it.id == movie.id }) {
            selectedMovies.add(movie)
            updateSelectedMoviesUi()
            binding.etSearchMovie.text?.clear()
            viewModel.clearAutocompleteResults()
            Toast.makeText(context, "Film aggiunto: ${movie.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Film già aggiunto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectedMoviesUi() {
        binding.tvSelectedCount.text = "${selectedMovies.size} film selezionati"
        binding.tvSelectedMovies.text = selectedMovies.joinToString("\n") { "• ${it.getDisplayTitle()}" }
    }

    private fun createList() {
        val name = binding.etListName.text.toString().trim()
        val description = binding.etListDescription.text.toString().trim()
        val isPublic = binding.switchPublic.isChecked

        if (name.isEmpty()) {
            binding.etListName.error = "nome richiesto"
            return
        }

        val movieIds = selectedMovies.map { it.id }

        binding.progressCreate.visibility = View.VISIBLE
        binding.btnCreate.isEnabled = false

        viewModel.createList(
            name = name,
            description = description.ifEmpty { null },
            isPublic = isPublic,
            movieIds = movieIds,
            onSuccess = { list ->
                Toast.makeText(context, "Lista creata: ${list.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            },
            onError = { error ->
                Toast.makeText(context, "Errore: $error", Toast.LENGTH_LONG).show()
                binding.progressCreate.visibility = View.GONE
                binding.btnCreate.isEnabled = true
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}