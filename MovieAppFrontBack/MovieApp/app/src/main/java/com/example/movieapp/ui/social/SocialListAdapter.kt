package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.databinding.ItemSocialListBinding
import com.example.movieapp.data.models.MovieList

class SocialListAdapter(
    private val onItemClick: (MovieList) -> Unit,
    private val onEditClick: ((MovieList) -> Unit)? = null,
    private val onDeleteClick: ((MovieList) -> Unit)? = null,
    private val onFollowClick: ((MovieList) -> Unit)? = null,
    private val isMyList: Boolean
) : ListAdapter<MovieList, SocialListAdapter.ListViewHolder>(ListDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = ItemSocialListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ListViewHolder(
        private val binding: ItemSocialListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(list: MovieList) {
            binding.apply {
                // Nome lista
                textListName.text = list.name

                // Badge pubblico/privato
                badgePublic.isVisible = list.isPublic
                badgePublic.text = if (list.isPublic) "🌍 PUBBLICA" else "🔒 PRIVATA"

                // Descrizione
                textListDescription.text = list.description ?: ""
                textListDescription.isVisible = !list.description.isNullOrEmpty()

                // Numero film
                val movieCount = list.movies?.size ?: 0
                textMovieCount.text = "$movieCount film"

                // Followers (solo per liste pubbliche)
                textFollowers.isVisible = list.isPublic
                if (list.isPublic) {
                    val followersCount = list.followers?.size ?: 0
                    textFollowers.text = "👥 $followersCount followers"
                }

                // Deadline (se presente)
                textDeadline.isVisible = !list.deadline.isNullOrEmpty()
                if (!list.deadline.isNullOrEmpty()) {
                    textDeadline.text = "📅 ${list.deadline}"
                }

                // Azioni: Edit/Delete (solo per le mie liste)
                layoutActions.isVisible = isMyList
                if (isMyList) {
                    buttonEdit.setOnClickListener {
                        onEditClick?.invoke(list)
                    }
                    buttonDelete.setOnClickListener {
                        onDeleteClick?.invoke(list)
                    }
                }

                // Bottone Follow (solo per liste pubbliche altrui)
                buttonFollow.isVisible = !isMyList && list.isPublic
                if (!isMyList && list.isPublic) {
                    buttonFollow.setOnClickListener {
                        onFollowClick?.invoke(list)
                    }
                }

                // Click su card intera
                root.setOnClickListener {
                    onItemClick(list)
                }
            }
        }
    }

    private class ListDiffCallback : DiffUtil.ItemCallback<MovieList>() {
        override fun areItemsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem == newItem
        }
    }
}