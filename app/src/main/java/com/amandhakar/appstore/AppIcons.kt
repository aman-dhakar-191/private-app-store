package com.amandhakar.appstore

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * App icons. An installed app's own icon is always used (exact, instant, no download). For an app
 * that isn't installed, the icon is downloaded from the store repo (see [IconUrls]) and kept for a
 * day. If there is none, the app is simply shown without an icon.
 */
object AppIcons {
    private const val ICON_PX = 192
    private const val MAX_BYTES = 1_000_000
    private const val FRESH_MS = 24L * 60 * 60 * 1000

    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Addresses that answered "not found", so a list of apps without icons doesn't ask again on every scroll.
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

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
    suspend fun remote(context: Context, app: CatalogApp): Bitmap? = withContext(Dispatchers.IO) {
        val url = IconUrls.resolve(BuildConfig.RAW_BASE_URL, app.packageName, app.icon)
        memory.get(url)?.let { return@withContext it }
        if (url in missing) return@withContext null

        val dir = File(context.cacheDir, "icons").apply { mkdirs() }
        val file = File(dir, "${app.packageName}-${url.hashCode()}.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS
        if (!fresh) {
            when (download(url, file)) {
                Download.Saved -> Unit
                Download.NotFound -> {
                    file.delete()
                    missing += url
                }
                Download.Failed -> Unit // offline or rate limited: an older copy is still fine
            }
        }
        val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (bitmap != null) memory.put(url, bitmap)
        bitmap
    }

    private enum class Download { Saved, NotFound, Failed }

    private fun download(url: String, file: File): Download {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Download.Failed
        }
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            when (conn.responseCode) {
                404 -> Download.NotFound
                in 200..299 -> {
                    val bytes = readUpTo(conn.inputStream, MAX_BYTES)
                    if (bytes == null) {
                        Download.Failed // far too big to be an icon
                    } else {
                        val temp = File(file.path + ".part")
                        temp.writeBytes(bytes)
                        if (temp.renameTo(file)) {
                            Download.Saved
                        } else {
                            temp.delete()
                            Download.Failed
                        }
                    }
                }
                else -> Download.Failed
            }
        } catch (e: IOException) {
            Download.Failed
        } finally {
            conn.disconnect()
        }
    }

    /** Everything in [stream], or null if it is longer than [limit] bytes. */
    private fun readUpTo(stream: InputStream, limit: Int): ByteArray? = stream.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return@use null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}
