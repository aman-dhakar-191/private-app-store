package com.amandhakar.appstore

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/**
 * App icons. An installed app's own icon is always used (exact, instant, no download). For an app
 * that isn't installed, the icon is downloaded from the store repo (see [IconUrls] and [RemoteImages]). If there is none, the app is simply shown without an icon.
 */
object AppIcons {
    private const val ICON_PX = 192

    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** The icon of an installed app, or null if it isn't installed. [version] refreshes it after an update. */
    fun installed(context: Context, packageName: String, version: String?): Bitmap? {
        val key = "installed:$packageName:$version"
        memory.get(key)?.let { return it }
        return try {
            context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
                .also { memory.put(key, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** The app's icon from the store repo, or null if there is none (or it can't be reached and none was saved). */
    suspend fun remote(context: Context, app: CatalogApp): Bitmap? =
        RemoteImages.load(
            context,
            IconUrls.resolve(BuildConfig.RAW_BASE_URL, app.packageName, app.icon),
            maxBytes = 1_000_000,
            maxSide = 512,
        )
}
