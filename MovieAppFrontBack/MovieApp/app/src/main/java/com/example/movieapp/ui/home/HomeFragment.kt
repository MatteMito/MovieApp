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
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * homefragment con import in background
 * fix: progress 0-100% completo, bottoni riabilitati automaticamente, refresh stats
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

    private var pendingImportType: ImportType? = null

    //launcher per selezionare file csv
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

        //bottone elimina tutti
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

        //import in corso - mostra/nascondi progress bar
        homeViewModel.isImporting.observe(viewLifecycleOwner) { isImporting ->
            binding.importProgressContainer.isVisible = isImporting
            enableImportButtons(!isImporting)

            Log.d(TAG, "isImporting: $isImporting (bottoni ${if (isImporting) "disabilitati" else "abilitati"})")

            //fix: quando import finisce, riabilita bottoni e nascondi progressbar
            if (!isImporting) {
                binding.importProgressContainer.isVisible = false
                enableImportButtons(true)
                //refresh stats per avere contatori aggiornati
                homeViewModel.refreshFromBackend()
                Log.d(TAG, "import completato -> bottoni riabilitati + stats aggiornate")
            }
        }

        //progress percentuale - aggiorna progress bar e testo (NO MAPPING QUI, GIA' FATTO NEL WORKER!)
        homeViewModel.importProgress.observe(viewLifecycleOwner) { progress ->
            binding.progressBarImport.progress = progress

            val statusText = when {
                progress == 0 -> "Preparazione import..."
                progress < 10 -> "Lettura file: $progress%"
                progress < 40 -> "Caricamento: $progress%"
                progress < 100 -> "Enrichment: $progress%"
                else -> "Completato!"
            }

            binding.textImportStatus.text = statusText
            Log.d(TAG, "import progress: $progress%")
        }

        //osserva websocket updates in tempo reale
        viewLifecycleOwner.lifecycleScope.launch {
            homeViewModel.observeWebSocketUpdates().collect { update ->
                if (update != null && binding.importProgressContainer.isVisible) {

                    when (update.type) {
                        "progress" -> {
                            //aggiorna progress (40-100% mappato da enrichment 0-100%)
                            val uiProgress = 40 + ((update.percentage * 60) / 100)
                            binding.progressBarImport.progress = uiProgress

                            val statusText = "Enrichment: ${update.processed}/${update.total}\n${update.currentMovie}"
                            binding.textImportStatus.text = statusText

                            Log.d(TAG, "ws: ${update.processed}/${update.total} -> $uiProgress%")

                            //fix: rileva automaticamente quando processed = total
                            if (update.processed >= update.total && update.total > 0) {
                                Log.d(TAG, "✅ RILEVATO 100%: ${update.processed}/${update.total}")
                                homeViewModel.onEnrichmentCompleted()
                            }
                        }
                        "completed" -> {
                            Log.d(TAG, "✅ EVENTO COMPLETED RICEVUTO")
                            homeViewModel.onEnrichmentCompleted()
                        }
                        "error" -> {
                            binding.textImportStatus.text = "Errore: ${update.message}"
                            //termina import anche in caso di errore
                            viewLifecycleOwner.lifecycleScope.launch {
                                delay(2000)
                                homeViewModel.onEnrichmentCompleted()
                            }
                        }
                    }
                }
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

        Log.d(TAG, "stats aggiornate: total=$total, watched=$watched, watchlist=$watchlist")
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

        Log.d(TAG, "bottoni import: ${if (enabled) "abilitati" else "disabilitati"}")
    }

    //dialog helper per import imdb watched
    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import IMDb Watched")
            .setMessage(
                "1. vai su imdb.com/list/ratings\n" +
                        "2. clicca sui 3 puntini in alto a destra\n" +
                        "3. clicca 'Export'\n" +
                        "4. seleziona il file scaricato"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImportType = ImportType.IMDB_WATCHED
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
                "1. vai su imdb.com/list/watchlist\n" +
                        "2. clicca sui 3 puntini in alto a destra\n" +
                        "3. clicca 'Export'\n" +
                        "4. seleziona il file scaricato"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImportType = ImportType.IMDB_WATCHLIST
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
                        "2. clicca 'Export Your Data'\n" +
                        "3. scarica il file zip\n" +
                        "4. estrai e seleziona diary.csv"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImportType = ImportType.LETTERBOXD_WATCHED
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
                        "2. clicca 'Export Your Data'\n" +
                        "3. scarica il file zip\n" +
                        "4. estrai e seleziona watchlist.csv"
            )
            .setPositiveButton("Seleziona File") { _, _ ->
                pendingImportType = ImportType.LETTERBOXD_WATCHLIST
                launchFilePicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    //mostra dialog conferma elimina tutti
    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina Tutti i Film")
            .setMessage("Sei sicuro di voler eliminare tutti i film? Questa azione non può essere annullata.")
            .setPositiveButton("Elimina") { _, _ ->
                homeViewModel.clearAllMovies()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    //apri file picker
    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
        }
        pickFileLauncher.launch(intent)
    }

    //processa file selezionato
    private fun processFile(uri: Uri, type: ImportType) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(requireContext(), "Errore lettura file", Toast.LENGTH_SHORT).show()
                return
            }

            //copia file in cache
            val cacheFile = java.io.File(requireContext().cacheDir, "import_${System.currentTimeMillis()}.csv")
            cacheFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val csvType = when (type) {
                ImportType.IMDB_WATCHED -> "IMDB_WATCHED"
                ImportType.IMDB_WATCHLIST -> "IMDB_WATCHLIST"
                ImportType.LETTERBOXD_WATCHED -> "LETTERBOXD_WATCHED"
                ImportType.LETTERBOXD_WATCHLIST -> "LETTERBOXD_WATCHLIST"
            }

            Log.d(TAG, "avvio import: ${cacheFile.absolutePath}, tipo: $csvType")
            homeViewModel.startImport(requireContext(), cacheFile.absolutePath, csvType)

            Toast.makeText(requireContext(), "Import avviato in background", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "errore processFile", e)
            Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}