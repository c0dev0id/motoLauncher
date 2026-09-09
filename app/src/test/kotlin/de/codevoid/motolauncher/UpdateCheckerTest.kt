package de.codevoid.motolauncher

import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {

    @Test
    fun parseReleasePicksApkAssetAndDerivesVersion() {
        val json = """
            {"assets":[
              {"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
              {"name":"motoLauncher-dev-abc1234.apk","browser_download_url":"https://x/app.apk"}
            ]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)!!
        assertEquals("dev-abc1234", info.versionName)
        assertEquals("https://x/app.apk", info.apkUrl)
        assertEquals("motoLauncher-dev-abc1234.apk", info.apkName)
    }

    @Test
    fun parseReleaseReturnsNullWithoutApkAsset() {
        assertNull(UpdateChecker.parseRelease("""{"assets":[{"name":"readme.txt"}]}"""))
    }

    @Test
    fun parseReleaseReturnsNullWithoutAssetsArray() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"dev"}"""))
    }

    @Test
    fun clearDownloadsRemovesEveryFileInTheUpdatesDir() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        File(dir, "motoLauncher-dev-old.apk").writeText("x")
        File(dir, "motoLauncher-dev-new.apk").writeText("y")

        UpdateChecker(context).clearDownloads()

        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test
    fun clearDownloadsToleratesMissingDir() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.cacheDir, "updates").deleteRecursively()
        UpdateChecker(context).clearDownloads()
    }

    @Test
    fun isNewerOnlyWhenVersionDiffers() {
        val remote = ReleaseInfo("dev-new", "url", "motoLauncher-dev-new.apk")
        assertTrue(UpdateChecker.isNewer(remote, "dev-old"))
        assertFalse(UpdateChecker.isNewer(remote, "dev-new"))
        assertFalse(UpdateChecker.isNewer(ReleaseInfo("", "u", "n"), "dev-old"))
    }
}
