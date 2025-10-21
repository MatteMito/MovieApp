package com.example.movieapp.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.R
import com.example.movieapp.data.models.MovieList

/**
 * Adapter per visualizzare liste di film
 */
class MovieListAdapter(
    private val onItemClick: (MovieList) -> Unit,
    private val onEditClick: ((MovieList) -> Unit)? = null,
    private val onDeleteClick: ((MovieList) -> Unit)? = null,
    private val onFollowClick: ((MovieList) -> Unit)? = null
) : ListAdapter<MovieList, MovieListAdapter.MovieListViewHolder>(MovieListDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_movie_list, parent, false)
        return MovieListViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieListViewHolder, position: Int) {
        val list = getItem(position)
        holder.bind(list, onItemClick, onEditClick, onDeleteClick, onFollowClick)
    }

    class MovieListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textListName: TextView = itemView.findViewById(R.id.text_list_name)
        private val textListDescription: TextView = itemView.findViewById(R.id.text_list_description)
        private val textMovieCount: TextView = itemView.findViewById(R.id.text_movie_count)
        private val textDeadline: TextView = itemView.findViewById(R.id.text_deadline)
        private val textFollowers: TextView = itemView.findViewById(R.id.text_followers)
        private val badgePublic: TextView = itemView.findViewById(R.id.badge_public)
        private val buttonEdit: Button = itemView.findViewById(R.id.button_edit)
        private val buttonDelete: Button = itemView.findViewById(R.id.button_delete)
        private val buttonFollow: Button = itemView.findViewById(R.id.button_follow)

        fun bind(
            list: MovieList,
            onItemClick: (MovieList) -> Unit,
            onEditClick: ((MovieList) -> Unit)?,
            onDeleteClick: ((MovieList) -> Unit)?,
            onFollowClick: ((MovieList) -> Unit)?
        ) {
            // Nome lista
            textListName.text = list.name

            // Descrizione
            if (list.description.isNullOrBlank()) {
                textListDescription.visibility = View.GONE
            } else {
                textListDescription.text = list.description
                textListDescription.visibility = View.VISIBLE
            }

            // Numero film
            textMovieCount.text = "${list.movieCount} film"

            // Badge pubblico/privato
            if (list.isPublic) {
                badgePublic.visibility = View.VISIBLE
            } else {
                badgePublic.visibility = View.GONE
            }

            // Deadline (se presente)
            if (list.targetDate != null) {
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ITALIAN)
                textDeadline.text = "Scadenza: ${dateFormat.format(list.targetDate)}"
                textDeadline.visibility = View.VISIBLE
            } else {
                textDeadline.visibility = View.GONE
            }

            // Follower count (solo per liste pubbliche)
            if (list.isPublic && list.followerCount > 0) {
                textFollowers.text = "${ list.followerCount} follower"
                textFollowers.visibility = View.VISIBLE
            } else {
                textFollowers.visibility = View.GONE
            }

            // Click su card
            itemView.setOnClickListener {
                onItemClick(list)
            }

            // Bottoni edit/delete (solo per liste dell'utente)
            if (onEditClick != null && onDeleteClick != null) {
                buttonEdit.visibility = View.VISIBLE
                buttonDelete.visibility = View.VISIBLE
                buttonFollow.visibility = View.GONE

                buttonEdit.setOnClickListener { onEditClick.invoke(list) }
                buttonDelete.setOnClickListener { onDeleteClick.invoke(list) }
            } else {
                buttonEdit.visibility = View.GONE
                buttonDelete.visibility = View.GONE
            }

            // Bottone follow (solo per liste pubbliche non dell'utente)
            if (onFollowClick != null) {
                buttonFollow.visibility = View.VISIBLE
                buttonFollow.text = if (list.isFollowing) "Seguito ✓" else "Segui"
                buttonFollow.setOnClickListener { onFollowClick.invoke(list) }
            } else {
                buttonFollow.visibility = View.GONE
            }
        }
    }

    class MovieListDiffCallback : DiffUtil.ItemCallback<MovieList>() {
        override fun areItemsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem == newItem
        }
    }
}