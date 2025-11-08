package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.databinding.DialogEditListBinding

// dialog fragment per creare o modificare una lista condivisa
class EditListDialogFragment : DialogFragment() {

    private var _binding: DialogEditListBinding? = null
    private val binding get() = _binding!!

    // viewmodel activity-scoped per sincronizzazione liste
    private val socialViewModel: SocialViewModel by activityViewModels()
    private lateinit var detailViewModel: ListDetailViewModel

    private var listId: String? = null
    private var currentName: String = ""
    private var currentDescription: String = ""
    private var isPublic: Boolean = false

    companion object {
        // factory method per creazione (listId null) o modifica (listId valorizzato)
        fun newInstance(
            listId: String?,
            currentName: String,
            currentDescription: String,
            isPublic: Boolean
        ): EditListDialogFragment {
            val fragment = EditListDialogFragment()
            val args = Bundle().apply {
                putString("listId", listId)
                putString("currentName", currentName)
                putString("currentDescription", currentDescription)
                putBoolean("isPublic", isPublic)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // recupera parametri da arguments
        arguments?.let {
            listId = it.getString("listId")
            currentName = it.getString("currentName") ?: ""
            currentDescription = it.getString("currentDescription") ?: ""
            isPublic = it.getBoolean("isPublic")
        }
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditListBinding.inflate(inflater, container, false)

        // prova a recuperare detailviewmodel dal parent fragment per modifica
        try {
            detailViewModel = ViewModelProvider(requireParentFragment())[ListDetailViewModel::class.java]
        } catch (e: Exception) {
            // se chiamato da socialfragment per creazione, crea istanza locale
            detailViewModel = ViewModelProvider(this)[ListDetailViewModel::class.java]
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // popola campi con valori correnti
        binding.etListName.setText(currentName)
        binding.etListDescription.setText(currentDescription)
        binding.switchPublic.isChecked = isPublic

        // titolo dinamico in base a creazione/modifica
        binding.tvTitle.text = if (listId == null) "Crea Lista" else "Modifica Lista"

        // bottone salva
        binding.btnSave.setOnClickListener {
            saveList()
        }

        // bottone annulla
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    // salva lista nuova o aggiorna esistente
    private fun saveList() {
        val name = binding.etListName.text?.toString()?.trim()
        val description = binding.etListDescription.text?.toString()?.trim()
        val isPublic = binding.switchPublic.isChecked

        // validazione nome obbligatorio
        if (name.isNullOrBlank()) {
            Toast.makeText(requireContext(), "inserisci un nome per la lista", Toast.LENGTH_SHORT).show()
            return
        }

        val currentListId = listId

        if (currentListId == null) {
            // crea nuova lista tramite socialviewmodel
            socialViewModel.createList(
                name = name,
                description = description?.ifBlank { null },
                isPublic = isPublic,
                onSuccess = { _ ->
                    Toast.makeText(requireContext(), "lista creata!", Toast.LENGTH_SHORT).show()
                    // ricarica liste in socialfragment
                    socialViewModel.refreshLists()
                    dismiss()
                },
                onError = { error ->
                    Toast.makeText(requireContext(), "errore: $error", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            // aggiorna lista esistente tramite detailviewmodel
            detailViewModel.updateList(
                listId = currentListId,
                name = name,
                description = description?.ifBlank { null },
                isPublic = isPublic,
                onSuccess = {
                    Toast.makeText(requireContext(), "lista aggiornata!", Toast.LENGTH_SHORT).show()
                    // ricarica liste in socialfragment
                    socialViewModel.refreshLists()
                    // listdetailfragment si aggiorna automaticamente tramite viewmodel
                    dismiss()
                },
                onError = { error ->
                    Toast.makeText(requireContext(), "errore: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // pulisce binding per evitare memory leak
        _binding = null
    }
}