//file: app/src/main/java/com/example/movieapp/ui/social/SocialListAdapter.kt
//adapter per liste sociali con badge pubblica/privata e copia

package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.R
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.databinding.ItemSocialListBinding

class SocialListAdapter(
    private val onListClick: (MovieList) -> Unit,
    private val onEditClick: ((MovieList) -> Unit)?,
    private val onDeleteClick: ((MovieList) -> Unit)?,
    private val onFollowClick: ((MovieList) -> Unit)?,
    private val onCopyClick: ((MovieList) -> Unit)?
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
                //nome lista
                tvListName.text = list.name

                //descrizione
                tvListDescription.text = list.description ?: "Nessuna descrizione"
                tvListDescription.visibility = if (list.description.isNullOrBlank()) View.GONE else View.VISIBLE

                //conteggio film
                tvMovieCount.text = "${list.movies.size} film"

                //badge visibilita
                badgeVisibility.text = if (list.isPublic) "PUBBLICA" else "PRIVATA"
                badgeVisibility.visibility = View.VISIBLE

                //username creatore (per liste pubbliche)
                if (onFollowClick != null && list.username != null) {
                    tvUsername.text = "@${list.username}"
                    tvUsername.visibility = View.VISIBLE
                } else {
                    tvUsername.visibility = View.GONE
                }

                //followers (per liste pubbliche)
                if (onFollowClick != null || onCopyClick != null) {
                    tvFollowers.text = "${list.followersCount} follower"
                    tvFollowers.visibility = View.VISIBLE
                } else {
                    tvFollowers.visibility = View.GONE
                }

                //bottone modifica
                if (onEditClick != null) {
                    btnEdit.visibility = View.VISIBLE
                    btnEdit.setOnClickListener { onEditClick.invoke(list) }
                } else {
                    btnEdit.visibility = View.GONE
                }

                //bottone elimina
                if (onDeleteClick != null) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener { onDeleteClick.invoke(list) }
                } else {
                    btnDelete.visibility = View.GONE
                }

                //bottone follow
                if (onFollowClick != null) {
                    btnFollow.visibility = View.VISIBLE
                    btnFollow.setOnClickListener { onFollowClick.invoke(list) }
                } else {
                    btnFollow.visibility = View.GONE
                }

                //bottone copia
                if (onCopyClick != null) {
                    btnCopy.visibility = View.VISIBLE
                    btnCopy.setOnClickListener { onCopyClick.invoke(list) }
                } else {
                    btnCopy.visibility = View.GONE
                }

                //click sulla card per aprire dettaglio
                root.setOnClickListener {
                    onListClick(list)
                }
            }
        }
    }

    class ListDiffCallback : DiffUtil.ItemCallback<MovieList>() {
        override fun areItemsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem == newItem
        }
    }
}