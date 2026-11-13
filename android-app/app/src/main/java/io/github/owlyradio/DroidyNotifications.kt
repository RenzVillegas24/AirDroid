package io.github.owlyradio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Progress notifications for send/receive. No foreground service is needed:
 * a promoted ongoing progress notification is enough while the app owns the
 * helper process.
 */
class DroidyNotifications(private val context: Context) {
    companion object {
        private const val CHANNEL_ID = "droidy_transfers"
        private const val SEND_ID = 4101
        private const val RECEIVE_ID = 4102
    }

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val contentIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File transfers",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "AirDrop send and receive progress"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun updateSend(percent: Int?, text: String, indeterminate: Boolean) {
        post(
            SEND_ID,
            build(
                title = "Sending file",
                text = text,
                icon = R.drawable.ic_send,
                percent = percent,
                indeterminate = indeterminate,
                ongoing = true,
            ),
        )
    }

    fun completeSend(text: String) {
        post(
            SEND_ID,
            build(
                title = "File sent",
                text = text,
                icon = R.drawable.ic_send,
                percent = 100,
                indeterminate = false,
                ongoing = false,
            ),
        )
    }

    fun failSend(text: String) {
        post(
            SEND_ID,
            build(
                title = "Send failed",
                text = text,
                icon = R.drawable.ic_send,
                percent = null,
                indeterminate = false,
                ongoing = false,
            ),
        )
    }

    fun cancelSend() {
        manager.cancel(SEND_ID)
    }

    fun updateReceive(percent: Int?, text: String, indeterminate: Boolean) {
        post(
            RECEIVE_ID,
            build(
                title = "Receiving file",
                text = text,
                icon = R.drawable.ic_download,
                percent = percent,
                indeterminate = indeterminate,
                ongoing = true,
            ),
        )
    }

    fun completeReceive(text: String) {
        post(
            RECEIVE_ID,
            build(
                title = "File received",
                text = text,
                icon = R.drawable.ic_download,
                percent = 100,
                indeterminate = false,
                ongoing = false,
            ),
        )
    }

    fun failReceive(text: String) {
        post(
            RECEIVE_ID,
            build(
                title = "Receive failed",
                text = text,
                icon = R.drawable.ic_download,
                percent = null,
                indeterminate = false,
                ongoing = false,
            ),
        )
    }

    fun cancelReceive() {
        manager.cancel(RECEIVE_ID)
    }

    private fun build(
        title: String,
        text: String,
        icon: Int,
        percent: Int?,
        indeterminate: Boolean,
        ongoing: Boolean,
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setShowWhen(false)
            .setProgress(100, percent ?: 0, indeterminate)
        if (Build.VERSION.SDK_INT >= 36) {
            applyProgressStyle(builder, percent, indeterminate, text)
        }
        return builder.build()
    }

    @RequiresApi(36)
    private fun applyProgressStyle(
        builder: Notification.Builder,
        percent: Int?,
        indeterminate: Boolean,
        text: String,
    ) {
        val style = Notification.ProgressStyle()
        if (indeterminate || percent == null) {
            style.setProgressIndeterminate(true)
        } else {
            style.setProgress(percent.coerceIn(0, 100))
            style.setStyledByProgress(true)
        }
        builder.setStyle(style)
        builder.setShortCriticalText(text.take(100))
        requestPromotedOngoing(builder, style)
    }

    /**
     * `setRequestPromotedOngoing` is present on some API 36 builds but is not
     * in the installed compile SDK's android.jar. Invoke it reflectively so
     * the app still builds and runs on devices that expose it.
     */
    @RequiresApi(36)
    private fun requestPromotedOngoing(builder: Notification.Builder, style: Notification.ProgressStyle) {
        for (target in listOf<Any>(builder, style)) {
            try {
                val method = target.javaClass.getMethod(
                    "setRequestPromotedOngoing",
                    Boolean::class.javaPrimitiveType,
                )
                method.invoke(target, true)
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun post(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: Exception) {
        }
    }
}
