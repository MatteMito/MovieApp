package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.movieapp.databinding.DialogCreateListBinding

class CreateListDialogFragment : DialogFragment() {

    private var _binding: DialogCreateListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SocialViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
    }

    private fun setupViews() {
        //bottone crea
        binding.btnCreate.setOnClickListener {
            createList()
        }

        //bottone annulla - verifica se esiste nel layout
        binding.root.findViewById<View>(com.example.movieapp.R.id.btnCancel)?.setOnClickListener {
            dismiss()
        }
    }

    private fun createList() {
        val name = binding.etListName.text?.toString()?.trim()
        val description = binding.etListDescription.text?.toString()?.trim()
        val isPublic = binding.switchPublic.isChecked

        if (name.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Inserisci un nome per la lista", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.createList(
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}