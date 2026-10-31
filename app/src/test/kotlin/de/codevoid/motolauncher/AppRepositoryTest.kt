package de.codevoid.motolauncher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppRepositoryTest {

    private fun entry(label: String) =
        AppEntry(ComponentKey(ComponentName("pkg.$label", "pkg.$label.Main"), Process.myUserHandle()), label, ColorDrawable())

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
    fun emptyQueryReturnsEverything() {
        val apps = listOf(entry("A"), entry("B"))
        assertSame(apps, AppRepository.filterApps(apps, ""))
    }
}
