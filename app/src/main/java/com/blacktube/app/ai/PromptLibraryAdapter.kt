package com.blacktube.app.ai

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

private const val TYPE_HEADER = 0
private const val TYPE_PROMPT = 1

private sealed class ListItem {
    data class Header(val category: PromptCategory, val isFavorites: Boolean = false) : ListItem()
    data class PromptItem(val prompt: BuiltInPrompt, val isFav: Boolean, val isActive: Boolean) : ListItem()
}

class PromptLibraryAdapter(
    private val onUse: (BuiltInPrompt) -> Unit,
    private val onFavorite: (BuiltInPrompt) -> Unit,
    private val onDuplicate: (BuiltInPrompt) -> Unit,
    private val onEdit: (BuiltInPrompt) -> Unit,
    private val onDelete: (BuiltInPrompt) -> Unit,
    private val isFavorite: (BuiltInPrompt) -> Boolean,
    private val isActive: (BuiltInPrompt) -> Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(prompts: List<BuiltInPrompt>, favIds: Set<String>, activeId: String?) {
        items.clear()

        // Favorites section first
        val favs = prompts.filter { favIds.contains(it.id) }
        if (favs.isNotEmpty()) {
            items.add(ListItem.Header(PromptCategory.YOUTUBE, isFavorites = true)) // category unused for fav header
            favs.forEach { items.add(ListItem.PromptItem(it, isFav = true, isActive = it.id == activeId)) }
        }

        // Group remaining by category
        val remaining = prompts.filter { !favIds.contains(it.id) }
        val grouped = remaining.groupBy { it.category }
        PromptCategory.values().forEach { cat ->
            val catItems = grouped[cat] ?: return@forEach
            items.add(ListItem.Header(cat))
            catItems.forEach { items.add(ListItem.PromptItem(it, isFav = false, isActive = it.id == activeId)) }
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.PromptItem -> TYPE_PROMPT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_prompt_category_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_prompt_card, parent, false)
            PromptViewHolder(view)
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ListItem.PromptItem -> (holder as PromptViewHolder).bind(item)
        }
    }

    private inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHeader: TextView = itemView.findViewById(R.id.tv_category_header)

        fun bind(header: ListItem.Header) {
            tvHeader.text = if (header.isFavorites) {
                "⭐ Favorites"
            } else {
                "${header.category.emoji} ${header.category.displayName}"
            }
        }
    }

    private inner class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tv_prompt_emoji)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_prompt_title)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_prompt_desc)
        private val chipActive: TextView = itemView.findViewById(R.id.chip_active)
        private val chipBuiltin: TextView = itemView.findViewById(R.id.chip_builtin)
        private val btnFav: ImageButton = itemView.findViewById(R.id.btn_favorite)
        private val btnUse: Button = itemView.findViewById(R.id.btn_use)
        private val btnDuplicate: Button = itemView.findViewById(R.id.btn_duplicate)
        private val btnEdit: Button = itemView.findViewById(R.id.btn_edit)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete)

        fun bind(item: ListItem.PromptItem) {
            val p = item.prompt
            tvEmoji.text = p.category.emoji
            tvTitle.text = p.title
            tvDesc.text = p.description

            chipActive.visibility = if (item.isActive) View.VISIBLE else View.GONE
            chipBuiltin.visibility = if (p.isBuiltIn) View.VISIBLE else View.GONE

            btnFav.setImageResource(
                if (item.isFav) R.drawable.ic_stars else R.drawable.ic_stars
            )
            btnFav.alpha = if (item.isFav) 1f else 0.3f
            btnFav.setOnClickListener { onFavorite(p) }

            btnUse.setOnClickListener { onUse(p) }

            if (p.isBuiltIn) {
                btnDuplicate.visibility = View.VISIBLE
                btnEdit.visibility = View.GONE
                btnDelete.visibility = View.GONE
                btnDuplicate.setOnClickListener { onDuplicate(p) }
            } else {
                btnDuplicate.visibility = View.GONE
                btnEdit.visibility = View.VISIBLE
                btnDelete.visibility = View.VISIBLE
                btnEdit.setOnClickListener { onEdit(p) }
                btnDelete.setOnClickListener { onDelete(p) }
            }
        }
    }
}
