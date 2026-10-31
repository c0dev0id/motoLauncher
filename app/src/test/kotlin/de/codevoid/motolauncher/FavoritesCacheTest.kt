package de.codevoid.motolauncher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesCacheTest {

    private val cache = FavoritesCache()
    private val loaded = mutableListOf<List<ComponentKey>>()

    private fun app(pkg: String) = ComponentKey(ComponentName(pkg, "$pkg.Main"), Process.myUserHandle())

    // Resolves every key except the uninstalled one.
    private fun resolve(vararg keys: ComponentKey, generation: Int = 0) =
        cache.resolve(keys.toList(), generation) { wanted ->
            loaded += wanted
            wanted.filter { it != app("gone") }.associateWith { AppEntry(it, it.component.packageName, ColorDrawable()) }
        }

    @Test
    fun aNewFavouriteResolvesOnlyItself() {
        resolve(app("maps"), app("music"))
        val apps = resolve(app("maps"), app("music"), app("radio"))

        assertEquals(listOf(listOf(app("maps"), app("music")), listOf(app("radio"))), loaded)
        assertEquals("radio", apps[app("radio")]?.label)
    }

    @Test
    fun anAppThatDidNotResolveIsNotAskedAgainInTheSameGeneration() {
        resolve(app("gone"))
        val apps = resolve(app("gone"))

        assertEquals(1, loaded.size)
        assertNull(apps[app("gone")])
    }

    @Test
    fun aPackageChangeResolvesEverythingAgain() {
        resolve(app("maps"), app("gone"))
        resolve(app("maps"), app("gone"), generation = 1)

        assertEquals(2, loaded.size)
        assertEquals(listOf(app("maps"), app("gone")), loaded[1])
    }
}
