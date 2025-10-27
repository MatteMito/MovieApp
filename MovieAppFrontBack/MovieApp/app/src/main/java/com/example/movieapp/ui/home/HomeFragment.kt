// FILE: app/src/main/java/com/example/movieapp/ui/home/HomeFragment.kt
// OTTIMIZZATO - Progress bar migliore, nessun freeze! 🚀

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
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    // ===== FILE PICKERS =====

    private val imdbWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "📁 IMDB Watched selezionato")
                processImdbWatchedFile(uri)
            }
        }
    }

    private val imdbWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "📁 IMDB Watchlist selezionato")
                processImdbWatchlistFile(uri)
            }
        }
    }

    private val letterboxdWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "📁 Letterboxd Watched selezionato")
                processLetterboxdWatchedFile(uri)
            }
        }
    }

    private val letterboxdWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "📁 Letterboxd Watchlist selezionato")
                processLetterboxdWatchlistFile(uri)
            }
        }
    }

    // ===== LIFECYCLE =====

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

        Log.d(TAG, "✅ HomeFragment creato")
        return binding.root
    }

    // ===== SETUP UI =====

    private fun setupUI() {
        // Bottoni import
        binding.buttonImportImdbWatched.setOnClickListener {
            showImdbWatchedHelp()
        }

        binding.buttonImportImdbWatchlist.setOnClickListener {
            showImdbWatchlistHelp()
        }

        binding.buttonImportLetterboxdWatched.setOnClickListener {
            showLetterboxdWatchedHelp()
        }

        binding.buttonImportLetterboxdWatchlist.setOnClickListener {
            showLetterboxdWatchlistHelp()
        }

        binding.buttonClearAll.setOnClickListener {
            showClearAllConfirmation()
        }

        // Swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "🔄 Refresh manuale")
            homeViewModel.refreshFromBackend()
        }
    }

    // ===== OBSERVERS =====

    private fun setupObservers() {
        // Loading state
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        // Movies - aggiorna stats
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCard(movies)
        }

        // Messages
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                // Mostra solo se non è un messaggio di progress
                if (!message.contains("in corso")) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 🔥 IMPORT STATUS - Gestisce UI progress
        homeViewModel.importStatus.observe(viewLifecycleOwner) { status ->
            updateImportStatusUI(status)
        }

        // 🔥 ENRICHMENT PROGRESS - Aggiorna progress bar
        homeViewModel.enrichmentProgress.observe(viewLifecycleOwner) { (processed, total) ->
            updateProgressBar(processed, total)
        }
    }

    // ===== UI UPDATES =====

    /**
     * Aggiorna stats card permanente
     */
    private fun updateStatsCard(movies: List<com.example.movieapp.data.models.Movie>) {
        val total = movies.size
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }
        val enriched = movies.count { it.tmdbId != null }

        binding.textTotalMovies.text = "$total"
        binding.textWatchedMovies.text = "$watched"
        binding.textWatchlistMovies.text = "$watchlist"
        binding.textEnrichedMovies.text = "$enriched"

        Log.d(TAG, "📊 Stats: $total film ($watched visti, $watchlist da vedere, $enriched enriched)")
    }

    /**
     * 🔥 Aggiorna UI in base allo stato import
     */
    private fun updateImportStatusUI(status: ImportStatus) {
        when (status) {
            is ImportStatus.IDLE -> {
                // Nasconde progress
                binding.layoutImportProgress.isVisible = false
                enableImportButtons(true)
            }

            is ImportStatus.PARSING -> {
                // Mostra parsing
                binding.layoutImportProgress.isVisible = true
                binding.textImportTitle.text = "📖 Lettura file CSV..."
                binding.progressBarImport.isIndeterminate = true
                binding.textProgressImport.text = "Analisi in corso..."
                enableImportButtons(false)
            }

            is ImportStatus.CONNECTING_WEBSOCKET -> {
                binding.layoutImportProgress.isVisible = true
                binding.textImportTitle.text = "🔌 Connessione server..."
                binding.progressBarImport.isIndeterminate = true
                binding.textProgressImport.text = "Preparazione..."
                enableImportButtons(false)
            }

            is ImportStatus.UPLOADING -> {
                // Mostra upload
                binding.layoutImportProgress.isVisible = true
                binding.textImportTitle.text = "📤 Upload al server..."
                binding.progressBarImport.isIndeterminate = false

                val percentage = if (status.total > 0) {
                    (status.uploaded * 100) / status.total
                } else 0

                binding.progressBarImport.progress = percentage
                binding.textProgressImport.text = "${status.uploaded} / ${status.total} film"
                enableImportButtons(false)
            }

            is ImportStatus.ENRICHING -> {
                // Mostra enrichment con dettagli
                binding.layoutImportProgress.isVisible = true
                binding.textImportTitle.text = "✨ Enrichment TMDB..."
                binding.progressBarImport.isIndeterminate = false

                val percentage = if (status.total > 0) {
                    (status.processed * 100) / status.total
                } else 0

                binding.progressBarImport.progress = percentage

                val movieInfo = if (status.currentMovie.isNotEmpty()) {
                    "\n${status.currentMovie}"
                } else ""

                binding.textProgressImport.text =
                    "${status.processed} / ${status.total} (${percentage}%)$movieInfo"
                enableImportButtons(false)
            }

            is ImportStatus.COMPLETED -> {
                // Mostra completamento
                binding.layoutImportProgress.isVisible = true
                binding.textImportTitle.text = "✅ Completato!"
                binding.progressBarImport.isIndeterminate = false
                binding.progressBarImport.progress = 100
                binding.textProgressImport.text = "${status.totalMovies} film importati"

                // Nasconde dopo 3 secondi
                binding.layoutImportProgress.postDelayed({
                    binding.layoutImportProgress.isVisible = false
                    enableImportButtons(true)
                }, 3000)
            }

            is ImportStatus.ERROR -> {
                // Mostra errore
                binding.layoutImportProgress.isVisible = true
                binding.textImportTitle.text = "❌ Errore"
                binding.progressBarImport.isIndeterminate = false
                binding.progressBarImport.progress = 0
                binding.textProgressImport.text = status.message

                Toast.makeText(requireContext(), "Errore: ${status.message}", Toast.LENGTH_LONG).show()

                // Nasconde dopo 5 secondi
                binding.layoutImportProgress.postDelayed({
                    binding.layoutImportProgress.isVisible = false
                    enableImportButtons(true)
                }, 5000)
            }
        }
    }

    /**
     * Aggiorna solo progress bar (chiamata da enrichmentProgress observer)
     */
    private fun updateProgressBar(processed: Int, total: Int) {
        if (total == 0) return

        val percentage = ((processed.toFloat() / total) * 100).toInt().coerceIn(0, 100)

        // Aggiorna solo se layout visibile
        if (binding.layoutImportProgress.isVisible) {
            binding.progressBarImport.progress = percentage
        }
    }

    /**
     * Abilita/disabilita bottoni import
     */
    private fun enableImportButtons(enabled: Boolean) {
        binding.buttonImportImdbWatched.isEnabled = enabled
        binding.buttonImportImdbWatchlist.isEnabled = enabled
        binding.buttonImportLetterboxdWatched.isEnabled = enabled
        binding.buttonImportLetterboxdWatchlist.isEnabled = enabled
        binding.buttonClearAll.isEnabled = enabled
    }

    // ===== FILE SELECTION HELPERS =====

    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📁 Importa IMDB Watched")
            .setMessage("Seleziona il file ratings.csv esportato da IMDB")
            .setPositiveButton("Seleziona File") { _, _ ->
                openFilePicker(imdbWatchedPicker)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📁 Importa IMDB Watchlist")
            .setMessage("Seleziona il file watchlist.csv esportato da IMDB")
            .setPositiveButton("Seleziona File") { _, _ ->
                openFilePicker(imdbWatchlistPicker)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📁 Importa Letterboxd Watched")
            .setMessage("Seleziona il file watched.csv esportato da Letterboxd")
            .setPositiveButton("Seleziona File") { _, _ ->
                openFilePicker(letterboxdWatchedPicker)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📁 Importa Letterboxd Watchlist")
            .setMessage("Seleziona il file watchlist.csv esportato da Letterboxd")
            .setPositiveButton("Seleziona File") { _, _ ->
                openFilePicker(letterboxdWatchlistPicker)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun openFilePicker(picker: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        picker.launch(intent)
    }

    // ===== FILE PROCESSING =====

    private fun processImdbWatchedFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchedCsv(inputStream)
                Log.d(TAG, "✅ Processing IMDB Watched avviato")
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore processing IMDB watched", e)
            Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImdbWatchlistFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchlistCsv(inputStream)
                Log.d(TAG, "✅ Processing IMDB Watchlist avviato")
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore processing IMDB watchlist", e)
            Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLetterboxdWatchedFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchedCsv(inputStream)
                Log.d(TAG, "✅ Processing Letterboxd Watched avviato")
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore processing Letterboxd watched", e)
            Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLetterboxdWatchlistFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchlistCsv(inputStream)
                Log.d(TAG, "✅ Processing Letterboxd Watchlist avviato")
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore processing Letterboxd watchlist", e)
            Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== CLEAR ALL =====

    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🗑️ Elimina tutti i film")
            .setMessage("Sei sicuro di voler eliminare TUTTI i film?\n\nQuesta operazione non può essere annullata.")
            .setPositiveButton("Elimina") { _, _ ->
                homeViewModel.clearAllMovies()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ===== LIFECYCLE =====

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}