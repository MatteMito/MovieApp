//file: app/src/main/java/com/example/movieapp/ui/social/CreateListDialogFragment.kt
//dialog per creare nuova lista senza film

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
        //close button
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        //switch pubblico/privato
        binding.switchPublic.setOnCheckedChangeListener { _, isChecked ->
            binding.tvVisibility.text = if (isChecked) "Pubblica" else "Privata"
        }

        //nascondi sezione film
        binding.tvAddMoviesTitle.visibility = View.GONE
        binding.etSearchMovie.visibility = View.GONE
        binding.progressAutocomplete.visibility = View.GONE
        binding.recyclerSearchResults.visibility = View.GONE
        binding.tvSelectedCount.visibility = View.GONE
        binding.tvSelectedMovies.visibility = View.GONE

        //create button
        binding.btnCreate.setOnClickListener {
            createList()
        }
    }

    private fun createList() {
        val name = binding.etListName.text.toString().trim()
        val description = binding.etListDescription.text.toString().trim()
        val isPublic = binding.switchPublic.isChecked

        if (name.isEmpty()) {
            binding.etListName.error = "nome richiesto"
            return
        }

        binding.progressCreate.visibility = View.VISIBLE
        binding.btnCreate.isEnabled = false

        viewModel.createList(
            name = name,
            description = description.ifEmpty { null },
            isPublic = isPublic,
            movieIds = emptyList(),
            onSuccess = { list ->
                Toast.makeText(context, "Lista creata: ${list.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            },
            onError = { error ->
                Toast.makeText(context, "Errore: $error", Toast.LENGTH_LONG).show()
                binding.progressCreate.visibility = View.GONE
                binding.btnCreate.isEnabled = true
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}