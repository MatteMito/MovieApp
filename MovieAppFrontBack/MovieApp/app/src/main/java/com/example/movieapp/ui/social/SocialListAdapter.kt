// FILE: app/src/main/java/com/example/movieapp/ui/social/SocialListAdapter.kt
// Adapter per visualizzare liste (personali e pubbliche)

package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.databinding.ItemSocialListBinding
import com.example.movieapp.data.models.MovieList
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter per liste social
 *
 * Supporta:
 * - Click su lista
 * - Azioni: Modifica, Elimina (per liste proprie)
 * - Follow (per liste pubbliche)
 * - Badge pubblico/privato
 * - Contatore film e followers
 */
class SocialListAdapter(
    private val onItemClick: (MovieList) -> Unit,
    private val onEditClick: ((MovieList) -> Unit)? = null,
    private val onDeleteClick: ((MovieList) -> Unit)? = null,
    private val onFollowClick: ((MovieList) -> Unit)? = null,
    private val showActions: Boolean = true
) : ListAdapter<MovieList, SocialListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSocialListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSocialListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(list: MovieList) {
            binding.apply {
                // Nome lista
                textListName.text = list.name

                // Descrizione
                if (!list.description.isNullOrEmpty()) {
                    textListDescription.text = list.getShortDescription()
                    textListDescription.visibility = View.VISIBLE
                } else {
                    textListDescription.visibility = View.GONE
                }

                // Contatore film
                textMovieCount.text = "${list.movieCount} film"

                // Badge pubblico/privato
                if (list.isPublic) {
                    badgePublic.visibility = View.VISIBLE
                    badgePublic.text = "🌍 PUBBLICA"
                } else {
                    badgePublic.visibility = View.VISIBLE
                    badgePublic.text = "🔒 PRIVATA"
                }

                // Followers (solo per liste pubbliche)
                if (list.isPublic && list.followersCount > 0) {
                    textFollowers.text = "👥 ${list.followersCount} follower${if (list.followersCount != 1) "s" else ""}"
                    textFollowers.visibility = View.VISIBLE
                } else {
                    textFollowers.visibility = View.GONE
                }

                // Deadline (se presente)
                if (!list.targetDate.isNullOrEmpty()) {
                    textDeadline.text = "📅 ${formatDate(list.targetDate)}"
                    textDeadline.visibility = View.VISIBLE
                } else {
                    textDeadline.visibility = View.GONE
                }

                // Azioni (solo per liste proprie)
                if (showActions && onEditClick != null && onDeleteClick != null) {
                    layoutActions.visibility = View.VISIBLE

                    buttonEdit.setOnClickListener {
                        onEditClick.invoke(list)
                    }

                    buttonDelete.setOnClickListener {
                        onDeleteClick.invoke(list)
                    }
                } else {
                    layoutActions.visibility = View.GONE
                }

                // Bottone Follow (solo per liste pubbliche altrui)
                if (!showActions && onFollowClick != null && list.isPublic) {
                    buttonFollow.visibility = View.VISIBLE
                    buttonFollow.setOnClickListener {
                        onFollowClick.invoke(list)
                    }
                } else {
                    buttonFollow.visibility = View.GONE
                }

                // Click su tutta la card
                root.setOnClickListener {
                    onItemClick(list)
                }
            }
        }

        /**
         * Formatta data ISO in formato leggibile
         */
        private fun formatDate(isoDate: String): String {
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = parser.parse(isoDate)
                formatter.format(date ?: Date())
            } catch (e: Exception) {
                isoDate.substring(0, 10) // Fallback: primi 10 caratteri
            }
        }
    }

    /**
     * DiffCallback per ottimizzazione performance
     */
    class DiffCallback : DiffUtil.ItemCallback<MovieList>() {
        override fun areItemsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem == newItem
        }
    }
}