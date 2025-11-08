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
import com.example.movieapp.data.models.Movie
import com.example.movieapp.databinding.FragmentListDetailBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ListDetailFragment : Fragment() {

    private val TAG = "ListDetailFragment"

    private var _binding: FragmentListDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel
    private lateinit var moviesAdapter: ListMoviesAdapter

    private var listId: String = ""
    private var listName: String = ""
    private var isOwner: Boolean = false

    //dati originali non filtrati
    private var allMovies: List<Movie> = emptyList()

    //filtri attivi
    private var searchQuery: String = ""
    private var selectedYear: Int? = null
    private var selectedGenre: String? = null
    private var selectedDirector: String? = null

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
        setupFilters()

        if (listId.isNotEmpty()) {
            viewModel.loadList(listId)
        } else {
            Toast.makeText(requireContext(), "errore: lista non trovata", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return binding.root
    }

    private fun setupUI() {
        //setup movies recyclerview
        moviesAdapter = ListMoviesAdapter(
            isOwner = isOwner,
            onRemoveClick = { movie ->
                if (isOwner) {
                    confirmRemoveMovie(movie)
                }
            }
        )

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
        //osserva loading
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        //osserva lista corrente
        viewModel.currentList.observe(viewLifecycleOwner) { list ->
            if (list != null) {
                binding.textListName.text = list.name
                binding.textListDescription.text = list.description ?: ""
                binding.textListDescription.isVisible = !list.description.isNullOrEmpty()

                //mostra conteggio film
                val movieCount = list.movies.size
                binding.textMovieCount.text = "$movieCount film"

                //mostra conteggio follower solo se lista pubblica
                if (list.isPublic) {
                    binding.layoutFollowerCount.visibility = View.VISIBLE
                    val followerCount = list.followersCount
                    binding.textFollowerCount.text = "$followerCount follower"
                } else {
                    binding.layoutFollowerCount.visibility = View.GONE
                }
            }
        }

        //osserva film (dal viewmodel che estrae da currentlist)
        viewModel.currentList.observe(viewLifecycleOwner) { list ->
            if (list != null) {
                allMovies = list.movies
                applyFilters()

                binding.tvFilmTitle.isVisible = list.movies.isNotEmpty()
                binding.textEmpty.isVisible = list.movies.isEmpty()
            }
        }
    }

    private fun setupFilters() {
        //search box con textchanged listener
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })

        //filtri anno
        binding.chipFilterYear.setOnClickListener {
            showYearPicker()
        }

        //filtri genere
        binding.chipFilterGenre.setOnClickListener {
            showGenrePicker()
        }

        //filtri regista
        binding.chipFilterDirector.setOnClickListener {
            showDirectorPicker()
        }

        //reset filtri
        binding.chipFilterReset.setOnClickListener {
            resetAllFilters()
        }
    }

    //applica filtri attivi
    private fun applyFilters() {
        var filtered = allMovies

        //filtro ricerca testuale
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { movie ->
                movie.title.contains(searchQuery, ignoreCase = true) ||
                        movie.director?.contains(searchQuery, ignoreCase = true) == true ||
                        movie.actors.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }

        //filtro anno
        if (selectedYear != null) {
            filtered = filtered.filter { it.year == selectedYear }
        }

        //filtro genere
        if (selectedGenre != null) {
            filtered = filtered.filter { movie ->
                movie.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            }
        }

        //filtro regista
        if (selectedDirector != null) {
            filtered = filtered.filter { it.director?.equals(selectedDirector, ignoreCase = true) == true }
        }

        moviesAdapter.submitList(filtered)
    }

    //mostra picker anno
    private fun showYearPicker() {
        val years = allMovies.mapNotNull { it.year }.distinct().sorted()
        if (years.isEmpty()) {
            Toast.makeText(requireContext(), "nessun anno disponibile", Toast.LENGTH_SHORT).show()
            return
        }

        val yearsArray: Array<CharSequence> = years.map { it.toString() }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("filtra per anno")
            .setItems(yearsArray) { _, which ->
                selectedYear = years[which]
                addFilterChip("anno: $selectedYear", "year")
                applyFilters()
                updateResetButtonVisibility()
            }
            .show()
    }

    //mostra picker genere
    private fun showGenrePicker() {
        val genres = allMovies.flatMap { it.genres }.distinct().sorted()
        if (genres.isEmpty()) {
            Toast.makeText(requireContext(), "nessun genere disponibile", Toast.LENGTH_SHORT).show()
            return
        }

        val genresArray: Array<CharSequence> = genres.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("filtra per genere")
            .setItems(genresArray) { _, which ->
                selectedGenre = genres[which]
                addFilterChip("genere: $selectedGenre", "genre")
                applyFilters()
                updateResetButtonVisibility()
            }
            .show()
    }

    //mostra picker regista
    private fun showDirectorPicker() {
        val directors = allMovies.mapNotNull { it.director }.distinct().sorted()
        if (directors.isEmpty()) {
            Toast.makeText(requireContext(), "nessun regista disponibile", Toast.LENGTH_SHORT).show()
            return
        }

        val directorsArray: Array<CharSequence> = directors.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("filtra per regista")
            .setItems(directorsArray) { _, which ->
                selectedDirector = directors[which]
                addFilterChip("regista: $selectedDirector", "director")
                applyFilters()
                updateResetButtonVisibility()
            }
            .show()
    }

    //aggiungi chip filtro selezionato
    private fun addFilterChip(label: String, type: String) {
        //rimuovi chip precedente dello stesso tipo
        for (i in 0 until binding.chipGroupSelected.childCount) {
            val chip = binding.chipGroupSelected.getChildAt(i) as? Chip
            if (chip?.tag == type) {
                binding.chipGroupSelected.removeView(chip)
                break
            }
        }

        val chip = Chip(requireContext()).apply {
            text = label
            tag = type
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                when (type) {
                    "year" -> selectedYear = null
                    "genre" -> selectedGenre = null
                    "director" -> selectedDirector = null
                }
                binding.chipGroupSelected.removeView(this)
                applyFilters()
                updateResetButtonVisibility()
            }
        }

        binding.chipGroupSelected.addView(chip)
    }

    //reset tutti i filtri
    private fun resetAllFilters() {
        searchQuery = ""
        selectedYear = null
        selectedGenre = null
        selectedDirector = null

        //resetta anche il testo nella search box
        binding.editSearch.setText("")
        binding.chipGroupSelected.removeAllViews()

        applyFilters()
        updateResetButtonVisibility()

        Toast.makeText(requireContext(), "filtri rimossi", Toast.LENGTH_SHORT).show()
    }

    //mostra/nascondi bottone reset
    private fun updateResetButtonVisibility() {
        val hasActiveFilters = searchQuery.isNotEmpty() ||
                selectedYear != null ||
                selectedGenre != null ||
                selectedDirector != null

        binding.chipFilterReset.isVisible = hasActiveFilters
    }

    //apri dialog aggiungi film
    private fun openAddMoviesDialog() {
        val dialog = AddMoviesDialogFragment.newInstance(listId)
        dialog.show(childFragmentManager, "AddMoviesDialog")
    }

    //conferma rimozione film
    private fun confirmRemoveMovie(movie: Movie) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("rimuovi film")
            .setMessage("vuoi rimuovere \"${movie.title}\" dalla lista?")
            .setPositiveButton("rimuovi") { _, _ ->
                viewModel.removeMovie(listId, movie.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "film rimosso", Toast.LENGTH_SHORT).show()
                        //non serve piu chiamare loadList manualmente, lo fa gia il viewmodel
                    },
                    onError = { error ->
                        Toast.makeText(requireContext(), "errore: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    //conferma eliminazione lista
    private fun confirmDeleteList() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("elimina lista")
            .setMessage("sei sicuro? questa azione non può essere annullata.")
            .setPositiveButton("elimina") { _, _ ->
                //torna alla schermata precedente (SocialFragment)
                requireActivity().onBackPressedDispatcher.onBackPressed()
                Toast.makeText(requireContext(), "lista eliminata", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}