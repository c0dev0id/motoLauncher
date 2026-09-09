package de.codevoid.motolauncher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppRepositoryTest {

    private fun entry(label: String) =
        AppEntry(ComponentName("pkg.$label", "pkg.$label.Main"), label, ColorDrawable())

    @Test
    fun sortsCaseInsensitively() {
        val sorted = AppRepository.sortApps(listOf(entry("banana"), entry("Apple"), entry("cherry")))
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.label })
    }

    @Test
    fun filterMatchesSubstringIgnoringCase() {
        val apps = listOf(entry("Maps"), entry("Camera"), entry("Calculator"))
        val filtered = AppRepository.filterApps(apps, "ca")
        assertEquals(setOf("Camera", "Calculator"), filtered.map { it.label }.toSet())
    }

    @Test
    fun blankQueryReturnsEverything() {
        val apps = listOf(entry("A"), entry("B"))
        assertEquals(apps, AppRepository.filterApps(apps, "  "))
    }
}
