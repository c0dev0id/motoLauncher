package de.codevoid.motolauncher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
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
        binding.appIcon.setImageDrawable(item.icon)

        binding.root.setOnClickListener { item.onClick() }
        binding.root.setOnLongClickListener { item.onLongClick() }
    }
}
