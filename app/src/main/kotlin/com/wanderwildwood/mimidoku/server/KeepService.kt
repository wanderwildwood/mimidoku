package com.wanderwildwood.mimidoku.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wanderwildwood.mimidoku.MainActivity
import com.wanderwildwood.mimidoku.R
import com.wanderwildwood.mimidoku.data.LibraryDatabase
import com.wanderwildwood.mimidoku.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fetches books with the app closed.
 *
 * A book is minutes of wifi, and nobody sits watching a shelf for that long: they put the phone
 * down, and the screen goes off. When the download lived in the screen, either of those ended it.
 * Here it belongs to a service that says what it is doing in a notification, holds the phone awake
 * and the wifi up until it is done, and then goes away.
 *
 * One book at a time, in the order asked for. Two at once would take as long in total and finish
 * neither sooner.
 */
class KeepService : Service() {

    /** A book being fetched, and how far along it is. */
    data class Keeping(val bookUri: String, val title: String, val done: Int, val total: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val waiting = ArrayDeque<Pair<String, String>>()
    private var worker: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var lastStart = 0
    // Why the last download failed, for the notification that says it did.
    private var reason: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStart = startId
        // Promised to the system the moment it is started, whatever it was started for: a service
        // started in the foreground that does not say so within a few seconds is killed.
        goForeground(notification(_now.value))

        when (intent?.action) {
            STOP -> {
                waiting.clear()
                worker?.cancel()
            }
            KEEP -> intent.getStringExtra(BOOK)?.let { uri ->
                val title = intent.getStringExtra(TITLE).orEmpty()
                val already = _now.value?.bookUri == uri || waiting.any { it.first == uri }
                if (!already) waiting.addLast(uri to title)
            }
        }
        when {
            worker?.isActive == true -> Unit
            waiting.isEmpty() -> finish()
            else -> {
                // A download just stopped may still be letting go. The next one waits for it,
                // rather than having its locks released and its service stopped from under it.
                val previous = worker
                worker = scope.launch {
                    previous?.join()
                    work()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun work() {
        holdAwake()
        try {
            while (true) {
                val (uri, title) = waiting.removeFirstOrNull() ?: break
                _now.value = Keeping(uri, title, 0, 0)
                show(notification(_now.value))
                val said = keep(uri, title)
                _now.value = null
                tell(said)
            }
        } finally {
            if (worker === currentCoroutineContext()[Job]) {
                _now.value = null
                letGo()
                finish()
            }
        }
    }

    /**
     * Stops only if nothing has been asked since the last start: a book asked for in the moment
     * between the last download ending and the service going away would otherwise go with it.
     */
    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStart)
    }

    private suspend fun keep(uri: String, title: String): String {
        val preferences = Preferences.of(this)
        val client = AbsClient(AbsServer(preferences.serverUrl, preferences.serverToken), resources)
        val dao = LibraryDatabase.get(this).dao()
        var said: String? = null
        preferences.failedDownloads -= uri
        try {
            val kept = AbsDownloader.downloadBook(this, client, dao, uri) { done, total, _ ->
                _now.value = Keeping(uri, title, done, total)
                show(notification(_now.value))
            }
            said = when (kept) {
                is AbsResult.Failure -> {
                    // The reason goes in the notification and the log; the shelf only needs to
                    // know that it failed, and keeps saying so until the book is asked for again.
                    reason = kept.message
                    Log.w(TAG, "download failed: ${kept.message}")
                    preferences.failedDownloads += uri
                    getString(R.string.download_failed, title)
                }
                is AbsResult.Success -> getString(R.string.library_kept, title)
            }
        } finally {
            // A book is on the phone or it is not. One that failed or was stopped part-way takes
            // its chapters with it, rather than sitting on the card as a book that cannot play.
            if (said == null || !AbsDownloader.isKept(dao, uri)) {
                withContext(NonCancellable) { AbsDownloader.remove(this@KeepService, dao, uri) }
            }
            if (said == null) tell(getString(R.string.keep_stopped, title))
        }
        return said
    }

    /**
     * Said on the shelf when the app is open to hear it, and in a notification when it is not: a
     * download that finished while the reader was elsewhere is still news when they come back.
     */
    private fun tell(said: String) {
        val why = reason
        reason = null
        if (_said.subscriptionCount.value > 0) {
            _said.tryEmit(said)
        } else {
            getSystemService(NotificationManager::class.java)
                .notify(
                    DONE_ID,
                    NotificationCompat.Builder(this, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(said)
                        .setContentText(why)
                        .setContentIntent(openApp())
                        .setAutoCancel(true)
                        .build(),
                )
        }
    }

    private fun notification(now: Keeping?) =
        NotificationCompat.Builder(this, ensureChannel())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(now?.title?.ifEmpty { null } ?: getString(R.string.library_keeping))
            .setContentText(
                if (now != null && now.total > 0) {
                    getString(R.string.library_keeping_progress, now.done, now.total)
                } else {
                    getString(R.string.library_keeping)
                },
            )
            .apply { if (now != null && now.total > 0) setProgress(now.total, now.done, false) }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(
                0,
                getString(R.string.keep_stop),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, KeepService::class.java).setAction(STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun goForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            ONGOING_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun show(notification: android.app.Notification) {
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, notification)
    }

    private fun ensureChannel(): String {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.keep_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        return CHANNEL
    }

    /**
     * The screen going off is what a download has to survive. Without these the phone sleeps the
     * processor and powers down the radio a minute later, and the socket stalls until it times out.
     */
    private fun holdAwake() {
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mimidoku:keep")
            .apply { acquire(WAKE_LIMIT_MS) }
        @Suppress("DEPRECATION")
        wifi = applicationContext.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mimidoku:keep")
            ?.apply { acquire() }
    }

    private fun letGo() {
        wake?.takeIf { it.isHeld }?.release()
        wifi?.takeIf { it.isHeld }?.release()
        wake = null
        wifi = null
    }

    /** Android 15 caps how long a data-sync service may run in a day; past it, stop cleanly. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        waiting.clear()
        worker?.cancel()
    }

    override fun onDestroy() {
        letGo()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEEP = "com.wanderwildwood.mimidoku.KEEP"
        private const val STOP = "com.wanderwildwood.mimidoku.STOP_KEEPING"
        private const val BOOK = "book"
        private const val TITLE = "title"
        private const val CHANNEL = "keeping"
        private const val ONGOING_ID = 2
        private const val DONE_ID = 3
        private const val TAG = "mimidoku"

        /** Long enough for any one book over a slow home network; a backstop, not a schedule. */
        private const val WAKE_LIMIT_MS = 6 * 60 * 60 * 1000L

        private val _now = MutableStateFlow<Keeping?>(null)
        private val _said = MutableSharedFlow<String>(extraBufferCapacity = 8)

        /** What is being fetched right now, for the shelf row it belongs to. */
        val now: StateFlow<Keeping?> = _now

        /** What a finished or failed download had to say, while the app is open to hear it. */
        val said: SharedFlow<String> = _said

        /** Stops whatever is being fetched and forgets what was waiting. */
        fun stop(context: Context) {
            context.startService(Intent(context, KeepService::class.java).setAction(STOP))
        }

        fun keep(context: Context, bookUri: String, title: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, KeepService::class.java)
                    .setAction(KEEP)
                    .putExtra(BOOK, bookUri)
                    .putExtra(TITLE, title),
            )
        }
    }
}
