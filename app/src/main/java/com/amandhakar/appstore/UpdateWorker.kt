package com.amandhakar.appstore

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks for updates in the background and posts a notification. It only notifies about
 * versions it hasn't already notified about, so the same update doesn't nag every run.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val snapshot = try {
            Store.load(applicationContext)
        } catch (e: Exception) {
            // Offline or rate limited; the next periodic run will try again.
            return Result.success()
        }

        val updates = buildList {
            snapshot.storeUpdate?.let { add(Update(applicationContext.getString(R.string.app_name), BuildConfig.APPLICATION_ID, it.version)) }
            snapshot.items.filter { it.updateAvailable }.forEach {
                add(Update(it.app.name, it.app.packageName, it.release!!.version))
            }
        }

        val prefs = applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val keys = updates.map { "${it.packageName}@${it.version}" }.toSet()
        val alreadyNotified = prefs.getStringSet(KEY_NOTIFIED, emptySet())!!
        // Remember only current updates, so a version that gets installed and later
        // updated again is notified about once more.
        prefs.edit().putStringSet(KEY_NOTIFIED, keys).apply()

        if (updates.isEmpty()) {
            NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
        } else if (!alreadyNotified.containsAll(keys)) {
            notify(updates)
        }
        return Result.success()
    }

    private data class Update(val name: String, val packageName: String, val version: String)

    private fun notify(updates: List<Update>) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)

        val title = if (updates.size == 1) {
            "Update available: ${updates[0].name}"
        } else {
            "${updates.size} updates available"
        }
        val text = updates.joinToString(", ") { "${it.name} ${it.version}" }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK_NAME = "update-check"
        private const val CHANNEL_ID = "updates"
        private const val NOTIFICATION_ID = 1
        private const val KEY_NOTIFIED = "notified"

        /** Safe to call on every launch; an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
