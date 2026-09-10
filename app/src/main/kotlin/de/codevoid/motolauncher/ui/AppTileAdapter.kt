package de.codevoid.motolauncher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.codevoid.motolauncher.databinding.ItemAppTileBinding

class AppTileAdapter(private var items: List<TileItem>) :
    RecyclerView.Adapter<AppTileAdapter.TileViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<TileItem>) {
        items = newItems
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
        // ViewHolder reuse: restore VISIBLE before binding, then hide for icon-less/subtitle-less tiles.
        val icon = item.icon
        if (icon != null) {
            binding.appIcon.setImageDrawable(icon)
            binding.appIcon.visibility = View.VISIBLE
        } else {
            binding.appIcon.visibility = View.GONE
        }
        val subtitle = item.subtitle
        if (subtitle != null) {
            binding.appSubtitle.text = subtitle
            binding.appSubtitle.visibility = View.VISIBLE
        } else {
            binding.appSubtitle.visibility = View.GONE
        }

        binding.root.setOnClickListener { item.onClick() }
        val onLongClick = item.onLongClick
        if (onLongClick != null) {
            binding.root.setOnLongClickListener { onLongClick() }
        } else {
            binding.root.setOnLongClickListener(null)
            binding.root.isLongClickable = false
        }
    }
}
