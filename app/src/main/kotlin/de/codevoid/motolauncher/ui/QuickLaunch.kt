package de.codevoid.motolauncher.ui

import android.content.Context
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore

// The remote's one action beyond launching a focused tile, and still only a launch:
// holding ESC starts the app in the first favourite slot — the top-left home tile.
// An empty slot, or one whose app has been uninstalled, is a silent no-op: there is
// nothing worth showing someone riding with gloves on.
fun Context.launchFirstFavorite() {
    val component = FavoritesStore(this).getSlot(0) ?: return
    AppRepository(this).launchIfInstalled(component)
}
