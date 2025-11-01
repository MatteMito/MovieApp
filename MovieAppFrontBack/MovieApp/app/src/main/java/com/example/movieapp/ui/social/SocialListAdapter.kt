// file: app/src/main/java/com/example/movieapp/ui/social/SocialListAdapter.kt
// adapter per liste con pulsanti delete e copy

package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.databinding.ItemSocialListBinding

class SocialListAdapter(
    private val onListClick: (MovieList) -> Unit,
    private val onDeleteClick: ((MovieList) -> Unit)?,
    private val onCopyClick: ((MovieList) -> Unit)?,
    private val showCopyButton: Boolean = false
) : ListAdapter<MovieList, SocialListAdapter.ViewHolder>(ListDiffCallback()) {

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
                //info lista
                tvListName.text = list.name
                tvListDescription.text = list.description ?: "Nessuna descrizione"
                tvMovieCount.text = "${list.getMovieCount()} film"

                //visibilita
                tvVisibility.text = list.getVisibilityText()

                //badge pubblico/privato
                if (list.isPublic) {
                    badgePublic.visibility = View.VISIBLE
                    badgePublic.text = "PUBBLICA"
                } else {
                    badgePublic.visibility = View.GONE
                }

                //followers count (solo per liste pubbliche)
                if (list.isPublic && list.followersCount > 0) {
                    tvFollowers.visibility = View.VISIBLE
                    tvFollowers.text = "${list.followersCount} follower"
                } else {
                    tvFollowers.visibility = View.GONE
                }

                //pulsante delete (solo per proprie liste)
                if (onDeleteClick != null) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener {
                        onDeleteClick.invoke(list)
                    }
                } else {
                    btnDelete.visibility = View.GONE
                }

                //pulsante copy (solo per liste pubbliche altrui)
                if (showCopyButton && onCopyClick != null) {
                    btnCopy.visibility = View.VISIBLE
                    btnCopy.setOnClickListener {
                        onCopyClick.invoke(list)
                    }
                } else {
                    btnCopy.visibility = View.GONE
                }

                //click sulla card
                root.setOnClickListener {
                    onListClick(list)
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