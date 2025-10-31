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
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * homefragment con import in background
 * fix: bottone elimina tutti funzionante
 */
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

    private var pendingImport: Pair<Uri, ImportType>? = null

    //launcher per selezionare file csv
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingImport?.let { (_, type) ->
                    processFile(uri, type)
                    pendingImport = null
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

        //fix: bottone elimina tutti funzionante
        binding.buttonClearAll.setOnClickListener {
            Log.d(TAG, "click: clear all")
            showClearAllConfirmation()
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

        //movies - aggiorna card statistiche
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCard(movies)
            Log.d(TAG, "movies aggiornati: ${movies.size} film")
        }

        //messages
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "message: $message")
            }
        }

        //import in corso
        homeViewModel.isImporting.observe(viewLifecycleOwner) { isImporting ->
            binding.progressBarImport.isVisible = isImporting
            enableImportButtons(!isImporting)

            Log.d(TAG, "isImporting: $isImporting")

            if (!isImporting) {
                binding.progressBarImport.progress = 0
            }
        }

        //progress percentuale
        homeViewModel.importProgress.observe(viewLifecycleOwner) { progress ->
            if (progress > 0) {
                binding.progressBarImport.progress = progress
                Log.d(TAG, "import progress: $progress%")
            }
        }
    }

    /**
     * aggiorna card statistiche
     */
    private fun updateStatsCard(movies: List<com.example.movieapp.data.models.Movie>) {
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }
        val total = movies.size

        binding.textTotalMovies.text = total.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()

        Log.d(TAG, "stats: total=$total, watched=$watched, watchlist=$watchlist")
    }

    /**
     * abilita/disabilita bottoni import
     */
    private fun enableImportButtons(enabled: Boolean) {
        binding.buttonImportImdbWatched.isEnabled = enabled
        binding.buttonImportImdbWatchlist.isEnabled = enabled
        binding.buttonImportLetterboxdWatched.isEnabled = enabled
        binding.buttonImportLetterboxdWatchlist.isEnabled = enabled
        binding.buttonClearAll.isEnabled = enabled
    }

    //dialog helper per import imdb watched
    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDb Watched")
            .setMessage(
                "1. vai su www.imdb.com/list/ratings\n" +
                        "2. clicca sui tre puntini in alto a destra\n" +
                        "3. seleziona 'Export'\n" +
                        "4. salva il file ratings.csv"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImport = null to ImportType.IMDB_WATCHED
                launchFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    //dialog helper per import imdb watchlist
    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDb Watchlist")
            .setMessage(
                "1. vai su www.imdb.com/list/watchlist\n" +
                        "2. clicca sui tre puntini in alto a destra\n" +
                        "3. seleziona 'Export'\n" +
                        "4. salva il file watchlist.csv"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImport = null to ImportType.IMDB_WATCHLIST
                launchFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    //dialog helper per import letterboxd watched
    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Letterboxd Watched")
            .setMessage(
                "1. vai su letterboxd.com/settings/data\n" +
                        "2. clicca su 'Export your data'\n" +
                        "3. scarica il file zip\n" +
                        "4. estrai e seleziona diary.csv"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImport = null to ImportType.LETTERBOXD_WATCHED
                launchFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    //dialog helper per import letterboxd watchlist
    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Letterboxd Watchlist")
            .setMessage(
                "1. vai su letterboxd.com/settings/data\n" +
                        "2. clicca su 'Export your data'\n" +
                        "3. scarica il file zip\n" +
                        "4. estrai e seleziona watchlist.csv"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImport = null to ImportType.LETTERBOXD_WATCHLIST
                launchFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * fix: mostra dialog conferma eliminazione tutti i film
     */
    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Elimina tutti i film")
            .setMessage(
                "Sei sicuro di voler eliminare TUTTI i film importati?\n\n" +
                        "Questa operazione:\n" +
                        "• Eliminerà tutti i film visti e da vedere\n" +
                        "• Eliminerà tutte le statistiche\n" +
                        "• Non può essere annullata\n\n" +
                        "Dovrai importare nuovamente i film."
            )
            .setPositiveButton("Elimina Tutto") { _, _ ->
                Log.d(TAG, "conferma: eliminazione tutti i film")
                homeViewModel.clearAllMovies()
            }
            .setNegativeButton("Annulla", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    //lancia file picker
    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickFileLauncher.launch(intent)
    }

    //processa file selezionato
    private fun processFile(uri: Uri, type: ImportType) {
        val csvType = when (type) {
            ImportType.IMDB_WATCHED -> "IMDB_WATCHED"
            ImportType.IMDB_WATCHLIST -> "IMDB_WATCHLIST"
            ImportType.LETTERBOXD_WATCHED -> "LETTERBOXD_WATCHED"
            ImportType.LETTERBOXD_WATCHLIST -> "LETTERBOXD_WATCHLIST"
        }

        val filePath = copyUriToInternalStorage(uri)
        if (filePath != null) {
            homeViewModel.startImport(requireContext(), filePath, csvType)
        } else {
            Toast.makeText(requireContext(), "Errore lettura file", Toast.LENGTH_SHORT).show()
        }
    }

    //copia uri file in storage interno
    private fun copyUriToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val tempFile = java.io.File(requireContext().cacheDir, "temp_import.csv")

            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "errore copia file: ${e.message}", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}