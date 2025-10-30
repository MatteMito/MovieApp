package com.example.movieapp.ui.home

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * homefragment con import in background e notifiche
 */
class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    //file pickers
    private val imdbWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "file selezionato: imdb watched")
                checkNotificationPermissionAndProcess(uri, ImportType.IMDB_WATCHED)
            }
        }
    }

    private val imdbWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "file selezionato: imdb watchlist")
                checkNotificationPermissionAndProcess(uri, ImportType.IMDB_WATCHLIST)
            }
        }
    }

    private val letterboxdWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "file selezionato: letterboxd watched")
                checkNotificationPermissionAndProcess(uri, ImportType.LETTERBOXD_WATCHED)
            }
        }
    }

    private val letterboxdWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "file selezionato: letterboxd watchlist")
                checkNotificationPermissionAndProcess(uri, ImportType.LETTERBOXD_WATCHLIST)
            }
        }
    }

    //permission launcher per notifiche (android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "permesso notifiche concesso")
            pendingImport?.let { (uri, type) ->
                processFile(uri, type)
                pendingImport = null
            }
        } else {
            Log.w(TAG, "permesso notifiche negato")
            Toast.makeText(
                requireContext(),
                "le notifiche sono disabilitate. l'import continuera in background",
                Toast.LENGTH_LONG
            ).show()
            pendingImport?.let { (uri, type) ->
                processFile(uri, type)
                pendingImport = null
            }
        }
    }

    private var pendingImport: Pair<Uri, ImportType>? = null

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

        //movies
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCard(movies)
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
                Log.d(TAG, "progress: $progress%")
            }
        }
    }

    /**
     * aggiorna card statistiche
     */
    private fun updateStatsCard(movies: List<com.example.movieapp.data.models.Movie>) {
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }

        binding.textTotalMovies.text = movies.size.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()

        // calcola e aggiorna la percentuale di completamento
        val percentage = if (movies.isNotEmpty()) {
            (watched.toFloat() / movies.size * 100).toInt()
        } else {
            0
        }
        binding.progressStats.progress = percentage
        binding.textProgressPercentage.text = "$percentage% completato"

        Log.d(TAG, "stats aggiornate: ${movies.size} film totali, $watched visti ($percentage%)")
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

    /**
     * controlla permesso notifiche e processa file
     */
    private fun checkNotificationPermissionAndProcess(uri: Uri, type: ImportType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    //permesso gia concesso
                    processFile(uri, type)
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    //mostra rationale
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("permesso notifiche")
                        .setMessage("l'app ha bisogno del permesso per mostrarti notifiche durante l'import dei film in background")
                        .setPositiveButton("concedi") { _, _ ->
                            pendingImport = uri to type
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("continua senza") { _, _ ->
                            processFile(uri, type)
                        }
                        .show()
                }
                else -> {
                    //richiedi permesso
                    pendingImport = uri to type
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            //android 12 e precedenti non richiedono permesso
            processFile(uri, type)
        }
    }

    /**
     * processa file csv
     */
    private fun processFile(uri: Uri, type: ImportType) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                when (type) {
                    ImportType.IMDB_WATCHED -> homeViewModel.processImdbWatchedCsv(stream)
                    ImportType.IMDB_WATCHLIST -> homeViewModel.processImdbWatchlistCsv(stream)
                    ImportType.LETTERBOXD_WATCHED -> homeViewModel.processLetterboxdWatchedCsv(stream)
                    ImportType.LETTERBOXD_WATCHLIST -> homeViewModel.processLetterboxdWatchlistCsv(stream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore lettura file: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "errore lettura file: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    //help dialogs
    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDB Watched")
            .setMessage("Come esportare i tuoi film visti da IMDB:\n\n" +
                    "1. Vai su www.imdb.com e fai login\n" +
                    "2. Clicca sul tuo nome in alto a destra\n" +
                    "3. Seleziona 'Your Ratings'\n" +
                    "4. Clicca sui 3 puntini (⋯) in alto a destra\n" +
                    "5. Seleziona 'Export'\n" +
                    "6. Salva il file ratings.csv\n" +
                    "7. Seleziona quel file qui sotto")
            .setPositiveButton("Seleziona File") { _, _ -> openImdbWatchedPicker() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDB Watchlist")
            .setMessage("Come esportare la tua watchlist da IMDB:\n\n" +
                    "1. Vai su www.imdb.com e fai login\n" +
                    "2. Clicca sul tuo nome in alto a destra\n" +
                    "3. Seleziona 'Your Watchlist'\n" +
                    "4. Clicca sui 3 puntini (⋯) in alto a destra\n" +
                    "5. Seleziona 'Export'\n" +
                    "6. Salva il file watchlist.csv\n" +
                    "7. Seleziona quel file qui sotto")
            .setPositiveButton("Seleziona File") { _, _ -> openImdbWatchlistPicker() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Letterboxd Watched")
            .setMessage("Come esportare i tuoi film da Letterboxd:\n\n" +
                    "1. Vai su letterboxd.com e fai login\n" +
                    "2. Clicca sul tuo profilo in alto a destra\n" +
                    "3. Vai su 'Settings' → 'Import & Export'\n" +
                    "4. Clicca su 'Export Your Data'\n" +
                    "5. Riceverai una email con un link\n" +
                    "6. Scarica il file ZIP ed estrailo\n" +
                    "7. Seleziona 'watched.csv' qui sotto")
            .setPositiveButton("Seleziona File") { _, _ -> openLetterboxdWatchedPicker() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Letterboxd Watchlist")
            .setMessage("Come esportare la tua watchlist da Letterboxd:\n\n" +
                    "1. Vai su letterboxd.com e fai login\n" +
                    "2. Clicca sul tuo profilo in alto a destra\n" +
                    "3. Vai su 'Settings' → 'Import & Export'\n" +
                    "4. Clicca su 'Export Your Data'\n" +
                    "5. Riceverai una email con un link\n" +
                    "6. Scarica il file ZIP ed estrailo\n" +
                    "7. Seleziona 'watchlist.csv' qui sotto")
            .setPositiveButton("Seleziona File") { _, _ -> openLetterboxdWatchlistPicker() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    //file pickers
    private fun openImdbWatchedPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        imdbWatchedPicker.launch(intent)
    }

    private fun openImdbWatchlistPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        imdbWatchlistPicker.launch(intent)
    }

    private fun openLetterboxdWatchedPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        letterboxdWatchedPicker.launch(intent)
    }

    private fun openLetterboxdWatchlistPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        letterboxdWatchlistPicker.launch(intent)
    }

    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("elimina tutti i film")
            .setMessage("sei sicuro di voler eliminare tutti i film? questa azione non puo essere annullata")
            .setPositiveButton("elimina") { _, _ ->
                Log.d(TAG, "confermata eliminazione")
                homeViewModel.clearAllMovies()
            }
            .setNegativeButton("annulla") { dialog, _ ->
                Log.d(TAG, "eliminazione annullata")
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "fragment resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "fragment paused")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "fragment destroyed")
    }
}

/**
 * enum tipi import
 */
enum class ImportType {
    IMDB_WATCHED,
    IMDB_WATCHLIST,
    LETTERBOXD_WATCHED,
    LETTERBOXD_WATCHLIST
}