package de.codevoid.motolauncher.ui

import android.content.Context
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.NavAppStore

fun Context.launchNavApp() {
    val component = NavAppStore(this).navApp ?: return
    AppRepository(this).launchIfInstalled(component)
}
