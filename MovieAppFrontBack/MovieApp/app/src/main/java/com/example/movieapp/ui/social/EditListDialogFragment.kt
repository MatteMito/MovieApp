// FILE: app/src/main/java/com/example/movieapp/ui/social/EditListDialogFragment.kt
// Dialog per modifica lista esistente

package com.example.movieapp.ui.social

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Switch
import androidx.fragment.app.DialogFragment
import com.example.movieapp.R
import com.example.movieapp.data.models.MovieList
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog per modificare lista esistente
 */
class EditListDialogFragment : DialogFragment() {

    private var onListUpdatedListener: ((name: String, description: String?, isPublic: Boolean) -> Unit)? = null

    companion object {
        fun newInstance(list: MovieList): EditListDialogFragment {
            val fragment = EditListDialogFragment()
            val args = Bundle()
            args.putString("list_id", list.id)
            args.putString("list_name", list.name)
            args.putString("list_description", list.description)
            args.putBoolean("list_is_public", list.isPublic)
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnListUpdatedListener(listener: (name: String, description: String?, isPublic: Boolean) -> Unit) {
        onListUpdatedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_create_list, null)

        val editName = view.findViewById<EditText>(R.id.edit_list_name)
        val editDescription = view.findViewById<EditText>(R.id.edit_list_description)
        val switchPublic = view.findViewById<Switch>(R.id.switch_public)

        // Precompila campi con dati esistenti
        arguments?.let { args ->
            editName.setText(args.getString("list_name", ""))
            editDescription.setText(args.getString("list_description", ""))
            switchPublic.isChecked = args.getBoolean("list_is_public", false)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifica Lista")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val name = editName.text.toString().trim()
                val description = editDescription.text.toString().trim().takeIf { it.isNotEmpty() }
                val isPublic = switchPublic.isChecked

                if (name.isNotEmpty()) {
                    onListUpdatedListener?.invoke(name, description, isPublic)
                }
            }
            .setNegativeButton("Annulla", null)
            .create()
    }
}