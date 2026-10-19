package de.codevoid.motolauncher.navapp

import android.content.Context
import de.codevoid.motolauncher.apps.AppRepository

fun Context.launchNavApp() {
    val component = NavAppStore(this).navApp ?: return
    AppRepository(this).launchIfInstalled(component)
}
