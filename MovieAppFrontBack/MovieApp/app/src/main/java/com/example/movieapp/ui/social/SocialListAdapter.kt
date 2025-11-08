package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.R
import com.example.movieapp.data.models.MovieList
import com.example.movieapp.databinding.ItemSocialListBinding

// adapter versatile per liste condivise, mostra bottoni diversi in base al contesto (mie/pubbliche/seguite)
class SocialListAdapter(
    private val onListClick: (MovieList) -> Unit,
    // callback nullable per mostrare bottoni solo quando necessario
    private val onEditClick: ((MovieList) -> Unit)?,
    private val onDeleteClick: ((MovieList) -> Unit)?,
    private val onFollowClick: ((MovieList) -> Unit)?,
    private val onUnfollowClick: ((MovieList) -> Unit)?,
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
                // nome lista
                tvListName.text = list.name

                // descrizione opzionale
                tvListDescription.text = list.description ?: "nessuna descrizione"
                tvListDescription.visibility = if (list.description.isNullOrBlank()) View.GONE else View.VISIBLE

                // numero film nella lista
                tvMovieCount.text = "${list.movies.size} film"

                // badge visibilità pubblica/privata
                badgeVisibility.text = if (list.isPublic) "PUBBLICA" else "PRIVATA"
                badgeVisibility.visibility = View.VISIBLE

                // username creatore, mostrato solo per liste pubbliche
                if (onFollowClick != null && list.username != null) {
                    tvUsername.text = "@${list.username}"
                    tvUsername.visibility = View.VISIBLE
                } else {
                    tvUsername.visibility = View.GONE
                }

                // contatore followers, mostrato solo per liste pubbliche
                if (list.isPublic) {
                    tvFollowers.text = "${list.followersCount} follower"
                    tvFollowers.visibility = View.VISIBLE
                } else {
                    tvFollowers.visibility = View.GONE
                }

                // bottone modifica, visibile solo per mie liste
                if (onEditClick != null) {
                    btnEdit.visibility = View.VISIBLE
                    btnEdit.setOnClickListener { onEditClick.invoke(list) }
                } else {
                    btnEdit.visibility = View.GONE
                }

                // bottone elimina, visibile solo per mie liste
                if (onDeleteClick != null) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener { onDeleteClick.invoke(list) }
                } else {
                    btnDelete.visibility = View.GONE
                }

                // bottone follow/unfollow con stato dinamico
                if (onFollowClick != null || onUnfollowClick != null) {
                    btnFollow.visibility = View.VISIBLE

                    if (list.isFollowing) {
                        // stato: già seguito, mostra bottone "seguito" per unfollow
                        btnFollow.text = "seguito"
                        btnFollow.isEnabled = true
                        btnFollow.setIconResource(R.drawable.ic_notifications)
                        btnFollow.strokeColor = ContextCompat.getColorStateList(root.context, R.color.primary_blue)
                        btnFollow.setTextColor(ContextCompat.getColor(root.context, R.color.primary_blue))
                        btnFollow.setOnClickListener { onUnfollowClick?.invoke(list) }
                    } else {
                        // stato: non seguito, mostra bottone "segui" per follow
                        btnFollow.text = "segui"
                        btnFollow.isEnabled = true
                        btnFollow.setIconResource(R.drawable.ic_add)
                        btnFollow.strokeColor = ContextCompat.getColorStateList(root.context, R.color.primary_blue)
                        btnFollow.setTextColor(ContextCompat.getColor(root.context, R.color.primary_blue))
                        btnFollow.setOnClickListener { onFollowClick?.invoke(list) }
                    }
                } else {
                    btnFollow.visibility = View.GONE
                }

                // bottone copia, per duplicare lista pubblica/seguita
                if (onCopyClick != null) {
                    btnCopy.visibility = View.VISIBLE
                    btnCopy.setOnClickListener { onCopyClick.invoke(list) }
                } else {
                    btnCopy.visibility = View.GONE
                }

                // click su card per aprire dettaglio lista
                root.setOnClickListener {
                    onListClick(list)
                }
            }
        }
    }

    // diffutil per ottimizzare aggiornamenti recyclerview
    class ListDiffCallback : DiffUtil.ItemCallback<MovieList>() {
        override fun areItemsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MovieList, newItem: MovieList): Boolean {
            return oldItem == newItem
        }
    }
}