//file: app/src/main/java/com/example/movieapp/ui/social/EditListDialogFragment.kt
//dialog per modificare nome e descrizione lista

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.databinding.DialogEditListBinding

class EditListDialogFragment : DialogFragment() {

    private var _binding: DialogEditListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel

    private var listId: String = ""
    private var currentName: String = ""
    private var currentDescription: String = ""
    private var isPublic: Boolean = false

    companion object {
        fun newInstance(
            listId: String,
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
            listId = it.getString("listId") ?: ""
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
        viewModel = ViewModelProvider(requireParentFragment())[ListDetailViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
    }

    private fun setupViews() {
        //imposta valori correnti
        binding.etListName.setText(currentName)
        binding.etListDescription.setText(currentDescription)
        binding.switchPublic.isChecked = isPublic
        binding.tvVisibility.text = if (isPublic) "Pubblica" else "Privata"

        //switch
        binding.switchPublic.setOnCheckedChangeListener { _, isChecked ->
            binding.tvVisibility.text = if (isChecked) "Pubblica" else "Privata"
        }

        //bottone annulla
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        //bottone salva
        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun saveChanges() {
        val newName = binding.etListName.text.toString().trim()
        val newDescription = binding.etListDescription.text.toString().trim()
        val newIsPublic = binding.switchPublic.isChecked

        if (newName.isEmpty()) {
            binding.etListName.error = "nome richiesto"
            return
        }

        binding.progressSave.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        viewModel.updateList(
            listId = listId,
            name = newName,
            description = newDescription.ifEmpty { null },
            isPublic = newIsPublic,
            onSuccess = {
                Toast.makeText(context, "Lista aggiornata", Toast.LENGTH_SHORT).show()
                viewModel.loadList(listId)
                dismiss()
            },
            onError = { error ->
                Toast.makeText(context, "Errore: $error", Toast.LENGTH_LONG).show()
                binding.progressSave.visibility = View.GONE
                binding.btnSave.isEnabled = true
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}