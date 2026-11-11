package com.amandhakar.appstore

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** The downloaded file is not the one GitHub says was released. */
class ChecksumException : IOException("The downloaded file doesn't match its checksum, so it was not installed.")

object Installer {

    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Messages about finished (or failed) installs, for showing to the user. */
    val results: SharedFlow<String> = _results

    internal fun report(message: String) {
        _results.tryEmit(message)
    }

    // Install sessions waiting for Android's answer, so a caller can wait for it.
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    /** Called when Android finishes (or gives up on) an install session. */
    internal fun finished(sessionId: Int, success: Boolean) {
        waiting.remove(sessionId)?.complete(success)
    }

    /**
     * Downloads the APK, reporting how many bytes are done of how many ([total] is 0 when the size
     * isn't known). Cancelling the caller stops the download and deletes the partly downloaded file.
     * When [expectedSha256] is a valid SHA-256 the file must match it, otherwise it is deleted and
     * [ChecksumException] is thrown. With none (an older release GitHub gave no checksum for) the
     * file is not checked.
     */
    suspend fun download(
        context: Context,
        url: String,
        expectedSha256: String = "",
        onProgress: (done: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "download.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) {
                throw GitHubException("${conn.responseCode}: download failed")
            }
            val total = maxOf(conn.contentLengthLong, 0L)
            onProgress(0, total)
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive() // stops here when the download is cancelled
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        val expected = Checksums.normalise(expectedSha256)
        if (expected.isNotEmpty() && !Checksums.matches(expected, Checksums.hex(digest.digest()))) {
            file.delete()
            throw ChecksumException()
        }
        file
    }

    /**
     * Hands the APK to Android, which then shows its own install confirmation. Returns at once with a
     * result that completes with true once Android has installed it, or false if it was cancelled
     * or failed.
     */
    suspend fun install(context: Context, apk: File): Deferred<Boolean> = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        // Registered before committing, so a quick answer can't be missed.
        val result = CompletableDeferred<Boolean>()
        waiting[sessionId] = result
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("app.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                // The system adds the result extras, so the intent must be mutable.
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
        result
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Installer.report("Installed")
                Installer.finished(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1), true)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Installer.report("Install cancelled")
                Installer.finished(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1), false)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
                    " (the installed copy is signed with a different key; uninstall it first)"
                } else {
                    ""
                }
                Installer.report("Install failed: ${message ?: status}$hint")
                Installer.finished(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1), false)
            }
        }
    }
}
