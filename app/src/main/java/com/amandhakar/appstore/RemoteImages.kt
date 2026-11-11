package com.amandhakar.appstore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections

/**
 * Pictures downloaded for the store (icons and screenshots). Each is kept on disk for a day and in
 * memory, an address that answered "not found" isn't asked again while the store is open, and an
 * older saved copy is used when the network fails. Big pictures are shrunk while decoding.
 */
object RemoteImages {
    private const val FRESH_MS = 24L * 60 * 60 * 1000

    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * The picture at [url] (https only), or null if there is none or it can't be had. Files bigger
     * than [maxBytes] are refused; the picture is decoded no bigger than about [maxSide] pixels.
     */
    suspend fun load(context: Context, url: String, maxBytes: Int, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        memory.get(url)?.let { return@withContext it }
        if (url in missing) return@withContext null

        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, Checksums.hex(MessageDigest.getInstance("SHA-256").digest(url.toByteArray())).take(24))
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS
        if (!fresh) {
            when (download(url, file, maxBytes)) {
                Download.Saved -> Unit
                Download.NotFound -> {
                    file.delete()
                    missing += url
                }
                Download.Failed -> Unit // offline or rate limited: an older copy is still fine
            }
        }
        val bitmap = if (file.exists()) decode(file, maxSide) else null
        if (bitmap != null) memory.put(url, bitmap)
        bitmap
    }

    private fun decode(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private enum class Download { Saved, NotFound, Failed }

    private fun download(url: String, file: File, maxBytes: Int): Download {
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
                    val bytes = readUpTo(conn.inputStream, maxBytes)
                    if (bytes == null) {
                        Download.Failed // too big to be what it should be
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
