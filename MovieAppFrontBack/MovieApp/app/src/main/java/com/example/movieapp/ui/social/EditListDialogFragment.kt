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

class EditListDialogFragment : DialogFragment() {

    private var _binding: DialogEditListBinding? = null
    private val binding get() = _binding!!

    private val socialViewModel: SocialViewModel by activityViewModels()
    private lateinit var detailViewModel: ListDetailViewModel

    private var listId: String? = null
    private var currentName: String = ""
    private var currentDescription: String = ""
    private var isPublic: Boolean = false

    companion object {
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
        detailViewModel = ViewModelProvider(this)[ListDetailViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //imposta valori correnti
        binding.etListName.setText(currentName)
        binding.etListDescription.setText(currentDescription)
        binding.switchPublic.isChecked = isPublic

        //cambia titolo se e' creazione o modifica
        binding.tvTitle.text = if (listId == null) "Crea Lista" else "Modifica Lista"

        //bottone salva
        binding.btnSave.setOnClickListener {
            saveList()
        }

        //bottone annulla
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun saveList() {
        val name = binding.etListName.text?.toString()?.trim()
        val description = binding.etListDescription.text?.toString()?.trim()
        val isPublic = binding.switchPublic.isChecked

        if (name.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Inserisci un nome per la lista", Toast.LENGTH_SHORT).show()
            return
        }

        val currentListId = listId

        if (currentListId == null) {
            //crea nuova lista
            socialViewModel.createList(
                name = name,
                description = description?.ifBlank { null },
                isPublic = isPublic,
                onSuccess = { newList ->
                    Toast.makeText(requireContext(), "Lista creata!", Toast.LENGTH_SHORT).show()
                    dismiss()
                },
                onError = { error ->
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            //aggiorna lista esistente
            detailViewModel.updateList(
                listId = currentListId,
                name = name,
                description = description?.ifBlank { null },
                isPublic = isPublic,
                onSuccess = {
                    Toast.makeText(requireContext(), "Lista aggiornata!", Toast.LENGTH_SHORT).show()
                    dismiss()
                },
                onError = { error ->
                    Toast.makeText(requireContext(), "Errore: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}