// FILE: app/src/main/java/com/example/movieapp/ui/social/CreateListDialogFragment.kt
// Dialog per creazione nuova lista

package com.example.movieapp.ui.social

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.movieapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog per creare nuova lista
 */
class CreateListDialogFragment : DialogFragment() {

    private var onListCreatedListener: ((name: String, description: String?, isPublic: Boolean) -> Unit)? = null

    fun setOnListCreatedListener(listener: (name: String, description: String?, isPublic: Boolean) -> Unit) {
        onListCreatedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_create_list, null)

        val editName = view.findViewById<EditText>(R.id.edit_list_name)
        val editDescription = view.findViewById<EditText>(R.id.edit_list_description)
        val switchPublic = view.findViewById<Switch>(R.id.switch_public)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Crea Nuova Lista")
            .setView(view)
            .setPositiveButton("Crea") { _, _ ->
                val name = editName.text.toString().trim()
                val description = editDescription.text.toString().trim().takeIf { it.isNotEmpty() }
                val isPublic = switchPublic.isChecked

                if (name.isNotEmpty()) {
                    onListCreatedListener?.invoke(name, description, isPublic)
                }
            }
            .setNegativeButton("Annulla", null)
            .create()
    }
}