package de.codevoid.motolauncher.data

import android.content.Context

/**
 * Tile size on Home and in the app list, chosen to suit the screen. Home shows the same
 * number of tiles in both orientations: its portrait grid is the landscape grid transposed.
 */
enum class TileSize(
    private val homeColumnsLandscape: Int,
    private val homeRowsLandscape: Int,
    private val listColumnsLandscape: Int,
    private val listColumnsPortrait: Int,
) {
    LARGE(4, 2, 4, 3),
    MEDIUM(4, 3, 5, 4),
    SMALL(5, 3, 6, 5);

    val homeTileCount: Int get() = homeColumnsLandscape * homeRowsLandscape

    fun homeColumns(isPortrait: Boolean): Int =
        if (isPortrait) homeRowsLandscape else homeColumnsLandscape

    fun homeRows(isPortrait: Boolean): Int =
        if (isPortrait) homeColumnsLandscape else homeRowsLandscape

    fun listColumns(isPortrait: Boolean): Int =
        if (isPortrait) listColumnsPortrait else listColumnsLandscape
}

class TileSizeStore(context: Context) {
    private val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    var tileSize: TileSize
        get() = prefs.getString(KEY, null)
            ?.let { runCatching { TileSize.valueOf(it) }.getOrNull() }
            ?: TileSize.MEDIUM
        set(value) { prefs.edit().putString(KEY, value.name).apply() }

    companion object {
        private const val KEY = "tile_size"
    }
}
