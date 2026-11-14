package io.moonbase.droidy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the radio session and controller alive while the activity is in the
 * background. Without a foreground service Android freezes the app and stops
 * delivering helper events, so incoming AirDrop requests would be lost.
 *
 * The notification is deliberately quiet and is not a discovery/readiness
 * event; it is the platform-required foreground-service indicator.
 */
class DroidyService : Service() {
    companion object {
        const val ACTION_START = "io.moonbase.droidy.service.START"
        const val ACTION_STOP = "io.moonbase.droidy.service.STOP"
        private const val CHANNEL_ID = "droidy_background"
        private const val NOTIFICATION_ID = 4099
        private const val STOP_REQUEST = 4099

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, DroidyService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DroidyService::class.java))
        }
    }

    private var ownsController = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        running = true
        if (DroidyControllerHolder.current == null) {
            ownsController = true
            DroidyControllerHolder.current = DroidyController(applicationContext).also {
                it.verifyKernelWithRoot()
            }
        }
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val controller = DroidyControllerHolder.current
            serviceScope.launch {
                controller?.stopScanning()
                stopForeground(true)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        serviceScope.cancel()
        if (ownsController) {
            val controller = DroidyControllerHolder.current
            if (controller != null) {
                controller.close()
                DroidyControllerHolder.current = null
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background AirDrop",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps the AirDrop receiver running in the background."
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            STOP_REQUEST,
            Intent(this, DroidyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return builder
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Droidy is listening")
            .setContentText("Background AirDrop receiver is active")
            .setContentIntent(content)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(true)
            .addAction(0, "Stop", stop)
            .build()
    }
}
