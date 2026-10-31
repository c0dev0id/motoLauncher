package de.codevoid.motolauncher.ui

import android.content.Context
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.NavAppStore

fun Context.launchNavApp() {
    val app = NavAppStore(this).navApp ?: return
    AppRepository(this).launch(app)
}
