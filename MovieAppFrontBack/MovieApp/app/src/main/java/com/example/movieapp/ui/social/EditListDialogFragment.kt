package com.example.movieapp.ui.social

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.R
import com.example.movieapp.databinding.DialogEditListBinding
import java.text.SimpleDateFormat
import java.util.*

// dialog fragment per creare o modificare una lista con notifiche intelligenti
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
    private var targetDate: String? = null
    private var frequency: String? = null
    private var notificationsEnabled: Boolean = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    companion object {
        // factory method per creazione (listId null) o modifica (listId valorizzato)
        fun newInstance(
            listId: String?,
            currentName: String,
            currentDescription: String,
            isPublic: Boolean,
            targetDate: String? = null,
            frequency: String? = null,
            notificationsEnabled: Boolean = false
        ): EditListDialogFragment {
            val fragment = EditListDialogFragment()
            val args = Bundle().apply {
                putString("listId", listId)
                putString("currentName", currentName)
                putString("currentDescription", currentDescription)
                putBoolean("isPublic", isPublic)
                putString("targetDate", targetDate)
                putString("frequency", frequency)
                putBoolean("notificationsEnabled", notificationsEnabled)
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
            targetDate = it.getString("targetDate")
            frequency = it.getString("frequency")
            notificationsEnabled = it.getBoolean("notificationsEnabled")
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

        setupUI()
        setupNotificationsUI()
    }

    private fun setupUI() {
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

    private fun setupNotificationsUI() {
        // setup spinner frequenza
        val frequencies = arrayOf("Giornaliera", "Settimanale", "Mensile")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, frequencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFrequency.adapter = adapter

        // imposta frequenza corrente
        when (frequency) {
            "daily" -> binding.spinnerFrequency.setSelection(0)
            "weekly" -> binding.spinnerFrequency.setSelection(1)
            "monthly" -> binding.spinnerFrequency.setSelection(2)
        }

        // imposta data target se presente
        targetDate?.let {
            try {
                val date = dateFormat.parse(it)
                date?.let { d ->
                    binding.tvTargetDate.text = displayDateFormat.format(d)
                }
            } catch (e: Exception) {
                binding.tvTargetDate.text = "Seleziona data"
            }
        }

        // switch notifiche
        binding.switchNotifications.isChecked = notificationsEnabled
        toggleNotificationsFields(notificationsEnabled)

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            toggleNotificationsFields(isChecked)
        }

        // bottone seleziona data
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun toggleNotificationsFields(enabled: Boolean) {
        binding.layoutNotificationsFields.isVisible = enabled
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        // se c'è già una data selezionata, usala come default
        targetDate?.let {
            try {
                val date = dateFormat.parse(it)
                date?.let { d -> calendar.time = d }
            } catch (e: Exception) {
                // usa data corrente
            }
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val selectedDate = calendar.time
                binding.tvTargetDate.text = displayDateFormat.format(selectedDate)
                targetDate = dateFormat.format(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // imposta data minima a oggi
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    // salva lista nuova o aggiorna esistente
    private fun saveList() {
        val name = binding.etListName.text?.toString()?.trim()
        val description = binding.etListDescription.text?.toString()?.trim()
        val isPublic = binding.switchPublic.isChecked
        val notificationsEnabled = binding.switchNotifications.isChecked

        // validazione nome obbligatorio
        if (name.isNullOrBlank()) {
            Toast.makeText(requireContext(), "inserisci un nome per la lista", Toast.LENGTH_SHORT).show()
            return
        }

        // validazione notifiche
        var finalTargetDate: String? = null
        var finalFrequency: String? = null

        if (notificationsEnabled) {
            if (targetDate == null) {
                Toast.makeText(requireContext(), "seleziona una data obiettivo", Toast.LENGTH_SHORT).show()
                return
            }

            finalTargetDate = targetDate
            finalFrequency = when (binding.spinnerFrequency.selectedItemPosition) {
                0 -> "daily"
                1 -> "weekly"
                2 -> "monthly"
                else -> "monthly"
            }
        }

        val currentListId = listId

        if (currentListId == null) {
            // crea nuova lista tramite socialviewmodel
            socialViewModel.createList(
                name = name,
                description = description?.ifBlank { null },
                isPublic = isPublic,
                targetDate = finalTargetDate,
                frequency = finalFrequency,
                notificationsEnabled = notificationsEnabled,
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
                targetDate = finalTargetDate,
                frequency = finalFrequency,
                notificationsEnabled = notificationsEnabled,
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