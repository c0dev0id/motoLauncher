package de.codevoid.motolauncher.ui

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.databinding.ItemAppTileBinding

class AppTileAdapter(private var items: List<TileItem>) :
    RecyclerView.Adapter<AppTileAdapter.TileViewHolder>() {

    private var touchOnly = false
    // One ID per TileItem.key for the adapter's lifetime.
    private val ids = HashMap<Any, Long>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = ids.getOrPut(items[position].key) { ids.size.toLong() }

    /** Shows [newItems]. [touchOnly] tiles cannot take focus, so the remote cannot reach them. */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<TileItem>, touchOnly: Boolean = false) {
        items = newItems
        this.touchOnly = touchOnly
        notifyDataSetChanged()
    }

    class TileViewHolder(val binding: ItemAppTileBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val binding = ItemAppTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.root.blockKeyLongPress()
        return TileViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val item = items[position]
        val binding = holder.binding

        binding.appLabel.text = item.label
        binding.appLabel.setTextColor(
            ContextCompat.getColor(binding.root.context, if (item.dimmed) R.color.on_surface_muted else R.color.on_surface)
        )
        binding.appIcon.bindOptional(item.icon)
        binding.appIcon.alpha = if (item.dimmed) DIMMED_ICON_ALPHA else 1f
        binding.appSubtitle.bindOptional(item.subtitle)
        binding.root.isFocusable = !touchOnly

        binding.root.setOnClickListener { item.onClick() }
        val onLongClick = item.onLongClick
        if (onLongClick != null) {
            binding.root.setOnLongClickListener { onLongClick() }
        } else {
            binding.root.setOnLongClickListener(null)
            binding.root.isLongClickable = false
        }
    }

    private companion object {
        const val DIMMED_ICON_ALPHA = 0.35f
    }
}

// Recycled view holders carry stale visibility from the previous binding — must reset explicitly.
private fun ImageView.bindOptional(value: Drawable?) {
    setImageDrawable(value)
    visibility = if (value != null) View.VISIBLE else View.GONE
}

private fun TextView.bindOptional(value: String?) {
    text = value
    visibility = if (value != null) View.VISIBLE else View.GONE
}
