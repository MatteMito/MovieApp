package com.example.movieapp.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    private enum class ImportType {
        IMDB_WATCHED,
        IMDB_WATCHLIST,
        LETTERBOXD_WATCHED,
        LETTERBOXD_WATCHLIST
    }

    private var pendingImportType: ImportType? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingImportType?.let { type ->
                    processFile(uri, type)
                    pendingImportType = null
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupUI()
        setupObservers()
        homeViewModel.initialize(requireContext())

        Log.d(TAG, "homefragment creato")
        return binding.root
    }

    private fun setupUI() {
        binding.buttonImportImdbWatched.setOnClickListener {
            Log.d(TAG, "click: import imdb watched")
            showImdbWatchedHelp()
        }
        binding.buttonImportImdbWatchlist.setOnClickListener {
            Log.d(TAG, "click: import imdb watchlist")
            showImdbWatchlistHelp()
        }
        binding.buttonImportLetterboxdWatched.setOnClickListener {
            Log.d(TAG, "click: import letterboxd watched")
            showLetterboxdWatchedHelp()
        }
        binding.buttonImportLetterboxdWatchlist.setOnClickListener {
            Log.d(TAG, "click: import letterboxd watchlist")
            showLetterboxdWatchlistHelp()
        }

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "swipe refresh")
            homeViewModel.refreshFromBackend()
        }
    }

    private fun setupObservers() {
        //loading generale
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            Log.d(TAG, "loading: $isLoading")
        }

        //aggiorna statistiche
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCard(movies)
            Log.d(TAG, "movies aggiornati: ${movies.size} film")
        }

        //import in corso
        homeViewModel.isImporting.observe(viewLifecycleOwner) { isImporting ->
            binding.importLoadingContainer.isVisible = isImporting
            enableImportButtons(!isImporting)

            if (isImporting) {
                binding.textImportStatus.text = "Import in corso..."
            }

            Log.d(TAG, "isImporting: $isImporting (bottoni ${if (isImporting) "disabilitati" else "abilitati"})")
        }

        //ascolta websocket per completion
        viewLifecycleOwner.lifecycleScope.launch {
            homeViewModel.observeWebSocketUpdates().collect { update ->
                if (update != null && update.type == "completed") {
                    Log.d(TAG, "websocket: enrichment completed!")
                    homeViewModel.onEnrichmentCompleted()
                }
            }
        }

        //messaggio di completamento
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "messaggio: $message")
            }
        }
    }

    private fun updateStatsCard(movies: List<com.example.movieapp.data.models.Movie>) {
        val total = movies.size
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }

        binding.textTotalMovies.text = total.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()

        Log.d(TAG, "stats: total=$total, watched=$watched, watchlist=$watchlist")
    }

    private fun enableImportButtons(enabled: Boolean) {
        binding.buttonImportImdbWatched.isEnabled = enabled
        binding.buttonImportImdbWatchlist.isEnabled = enabled
        binding.buttonImportLetterboxdWatched.isEnabled = enabled
        binding.buttonImportLetterboxdWatchlist.isEnabled = enabled
    }

    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDb Watched")
            .setMessage("Vai su IMDb → Your Ratings → Export → scarica il CSV e selezionalo")
            .setPositiveButton("OK") { _, _ ->
                pendingImportType = ImportType.IMDB_WATCHED
                openFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDb Watchlist")
            .setMessage("Vai su IMDb → Your Watchlist → Export → scarica il CSV e selezionalo")
            .setPositiveButton("OK") { _, _ ->
                pendingImportType = ImportType.IMDB_WATCHLIST
                openFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Letterboxd Watched")
            .setMessage("Vai su Letterboxd → Settings → Import & Export → Export Your Data → scarica watched.csv")
            .setPositiveButton("OK") { _, _ ->
                pendingImportType = ImportType.LETTERBOXD_WATCHED
                openFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Letterboxd Watchlist")
            .setMessage("Vai su Letterboxd → Settings → Import & Export → Export Your Data → scarica watchlist.csv")
            .setPositiveButton("OK") { _, _ ->
                pendingImportType = ImportType.LETTERBOXD_WATCHLIST
                openFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickFileLauncher.launch(intent)
    }

    private fun processFile(uri: Uri, type: ImportType) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(requireContext(), "Errore lettura file", Toast.LENGTH_SHORT).show()
                return
            }

            val tempFile = createTempFile("import_", ".csv", requireContext().cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val csvType = when (type) {
                ImportType.IMDB_WATCHED -> "IMDB_WATCHED"
                ImportType.IMDB_WATCHLIST -> "IMDB_WATCHLIST"
                ImportType.LETTERBOXD_WATCHED -> "LETTERBOXD_WATCHED"
                ImportType.LETTERBOXD_WATCHLIST -> "LETTERBOXD_WATCHLIST"
            }

            Log.d(TAG, "avvio import: $csvType")
            homeViewModel.startImport(tempFile.absolutePath, csvType)

        } catch (e: Exception) {
            Log.e(TAG, "errore processamento file", e)
            Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}