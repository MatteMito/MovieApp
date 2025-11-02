//file: app/src/main/java/com/example/movieapp/ui/social/SocialListAdapter.kt
//adapter per liste sociali con badge pubblica/privata

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
    private val onDeleteClick: ((MovieList) -> Unit)?,
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
                tvListDescription.visibility = View.VISIBLE

                //badge pubblico/privato
                if (list.isPublic) {
                    badgePublic.text = "PUBBLICA"
                    badgePublic.visibility = View.VISIBLE
                    badgePublic.setBackgroundResource(R.drawable.badge_public_background)
                } else {
                    badgePublic.text = "PRIVATA"
                    badgePublic.visibility = View.VISIBLE
                    badgePublic.setBackgroundResource(R.drawable.badge_private_background)
                }

                //movie count
                tvMovieCount.text = "${list.movies.size}"

                //followers count
                tvFollowersCount.text = list.followersCount.toString()

                //click sulla card
                root.setOnClickListener {
                    onListClick(list)
                }

                //bottone delete
                if (onDeleteClick != null) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener {
                        onDeleteClick.invoke(list)
                    }
                } else {
                    btnDelete.visibility = View.GONE
                }

                //bottone copy
                if (onCopyClick != null) {
                    btnCopy.visibility = View.VISIBLE
                    btnCopy.setOnClickListener {
                        onCopyClick.invoke(list)
                    }
                } else {
                    btnCopy.visibility = View.GONE
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