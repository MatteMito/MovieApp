package com.example.movieapp.ui.lists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.databinding.FragmentListsBinding
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.launch
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText

/**
 * Fragment per gestire le liste di film personalizzate
 * Features:
 * - Liste utente personalizzate
 * - Liste pubbliche da seguire
 * - Creazione, modifica, eliminazione liste
 */
class ListsFragment : Fragment() {
    private val TAG = "ListsFragment"

    private var _binding: FragmentListsBinding? = null
    private val binding get() = _binding!!

    private lateinit var userListsAdapter: MovieListAdapter
    private lateinit var publicListsAdapter: MovieListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListsBinding.inflate(inflater, container, false)

        setupRecyclerViews()
        setupButtons()
        loadLists()

        Log.d(TAG, "ListsFragment creato")

        return binding.root
    }

    private fun setupRecyclerViews() {
        // User lists adapter
        userListsAdapter = MovieListAdapter(
            onItemClick = { list -> openList(list) },
            onEditClick = { list -> editList(list) },
            onDeleteClick = { list -> deleteList(list) }
        )

        binding.recyclerUserLists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = userListsAdapter
        }

        // Public lists adapter
        publicListsAdapter = MovieListAdapter(
            onItemClick = { list -> openList(list) },
            onFollowClick = { list -> followList(list) }
        )

        binding.recyclerPublicLists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = publicListsAdapter
        }
    }

    private fun setupButtons() {
        binding.buttonCreateList.setOnClickListener {
            createNewList()
        }

        binding.fabCreateList.setOnClickListener {
            createNewList()
        }
    }

    /**
     * Carica liste utente e pubbliche dal backend
     */
    private fun loadLists() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.textNoLists.visibility = View.GONE

                // Carica liste utente
                val userListsResult = ApiService.getUserLists()
                if (userListsResult.isSuccess) {
                    val userLists = userListsResult.getOrNull() ?: emptyList()
                    userListsAdapter.submitList(userLists)

                    // Mostra messaggio se non ci sono liste
                    if (userLists.isEmpty()) {
                        binding.textNoLists.visibility = View.VISIBLE
                        binding.textNoLists.text = "Nessuna lista creata.\nTocca '+' per creare la tua prima lista!"
                    }

                    Log.d(TAG, "Caricate ${userLists.size} liste utente")
                } else {
                    Log.w(TAG, "Errore caricamento liste utente")
                    Toast.makeText(requireContext(), "Errore caricamento liste utente", Toast.LENGTH_SHORT).show()
                }

                // Carica liste pubbliche
                val publicListsResult = ApiService.getPublicLists(50)
                if (publicListsResult.isSuccess) {
                    val publicLists = publicListsResult.getOrNull() ?: emptyList()
                    publicListsAdapter.submitList(publicLists)
                    Log.d(TAG, "Caricate ${publicLists.size} liste pubbliche")
                } else {
                    Log.w(TAG, "Errore caricamento liste pubbliche")
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "Errore caricamento liste", e)
                binding.progressBar.visibility = View.GONE
                binding.textNoLists.visibility = View.VISIBLE
                binding.textNoLists.text = "Errore di connessione.\nVerifica la tua rete."
                Toast.makeText(requireContext(), "Errore caricamento liste", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Dialog per creare nuova lista
     */
    private fun createNewList() {
        val input = EditText(requireContext()).apply {
            hint = "Nome lista"
            setPadding(60, 40, 60, 40)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Crea Nuova Lista")
            .setMessage("Dai un nome alla tua lista personalizzata")
            .setView(input)
            .setPositiveButton("Crea") { dialog, _ ->
                val listName = input.text.toString().trim()

                if (listName.isEmpty()) {
                    Toast.makeText(requireContext(), "Inserisci un nome", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (listName.length < 3) {
                    Toast.makeText(requireContext(), "Nome troppo corto (min 3 caratteri)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Crea la lista
                createListOnBackend(listName)
                dialog.dismiss()
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Crea lista sul backend
     */
    private fun createListOnBackend(listName: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = ApiService.createList(listName)

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Lista '$listName' creata!", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Lista creata: $listName")

                    // Ricarica liste
                    loadLists()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Errore sconosciuto"
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Errore creazione lista: $error")
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione creazione lista", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Errore di connessione", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Apre dettaglio lista
     */
    private fun openList(list: MovieList) {
        // Naviga al dettaglio lista con i film
        Log.d(TAG, "Apertura lista: ${list.name} (${list.movieCount} film)")

        // Mostra info lista
        Toast.makeText(
            requireContext(),
            "Lista: ${list.name}\n${list.movieCount} film",
            Toast.LENGTH_SHORT
        ).show()

        // TODO: Quando ListDetailFragment sarà pronto, decommentare:
        // val bundle = Bundle().apply {
        //     putString("list_id", list.id)
        //     putString("list_name", list.name)
        // }
        // findNavController().navigate(R.id.action_lists_to_listDetail, bundle)
    }

    /**
     * Modifica nome lista
     */
    private fun editList(list: MovieList) {
        val input = EditText(requireContext()).apply {
            setText(list.name)
            hint = "Nome lista"
            setPadding(60, 40, 60, 40)
            setSelection(list.name.length) // Cursore alla fine
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica Lista")
            .setMessage("Cambia il nome della lista")
            .setView(input)
            .setPositiveButton("Salva") { dialog, _ ->
                val newName = input.text.toString().trim()

                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "Inserisci un nome", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newName.length < 3) {
                    Toast.makeText(requireContext(), "Nome troppo corto (min 3 caratteri)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newName == list.name) {
                    Toast.makeText(requireContext(), "Nessuna modifica", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }

                // Aggiorna lista
                updateListOnBackend(list.id, newName)
                dialog.dismiss()
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Aggiorna lista sul backend
     */
    private fun updateListOnBackend(listId: String, newName: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = ApiService.updateList(listId, newName)

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Lista aggiornata!", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Lista aggiornata: $newName")

                    // Ricarica liste
                    loadLists()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Errore sconosciuto"
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_SHORT).show()
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione aggiornamento lista", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Errore di connessione", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Elimina lista con conferma
     */
    private fun deleteList(list: MovieList) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Elimina Lista")
            .setMessage("Sei sicuro di voler eliminare '${list.name}'?\n\nQuesta azione non può essere annullata.")
            .setPositiveButton("Elimina") { dialog, _ ->
                deleteListOnBackend(list)
                dialog.dismiss()
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Elimina lista dal backend
     */
    private fun deleteListOnBackend(list: MovieList) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = ApiService.deleteList(list.id)

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Lista '${list.name}' eliminata", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Lista eliminata: ${list.name}")

                    // Ricarica liste
                    loadLists()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Errore eliminazione"
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_SHORT).show()
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione eliminazione lista", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Errore di connessione", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Segui lista pubblica
     */
    private fun followList(list: MovieList) {
        val ownerText = list.ownerName ?: "un altro utente"  // ✅ FIXED: Gestito null

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Segui Lista")
            .setMessage("Vuoi seguire la lista '${list.name}' di $ownerText?\n\nRiceverai le notifiche quando viene aggiornata.")
            .setPositiveButton("Segui") { dialog, _ ->
                followListOnBackend(list)
                dialog.dismiss()
            }
            .setNegativeButton("Annulla") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Segui lista sul backend
     */
    private fun followListOnBackend(list: MovieList) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = ApiService.followList(list.id)

                if (result.isSuccess) {
                    Toast.makeText(
                        requireContext(),
                        "Ora segui '${list.name}'!",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Lista seguita: ${list.name}")

                    // Ricarica liste
                    loadLists()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Errore"
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_SHORT).show()
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione follow lista", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Errore di connessione", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}