package io.moonbase.airdroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Transfer and incoming-request notifications.
 *
 * Progress notifications are grouped per transfer and use an ID allocated from
 * the stable transfer ID. Discovery, receiver readiness, and channel search
 * never post here; only real transfers and real incoming `/Ask` requests do.
 */
class AirDroidNotifications(private val context: Context) {
    companion object {
        const val CHANNEL_TRANSFERS = "airdroid_transfers"
        const val CHANNEL_REQUESTS = "airdroid_requests"

        const val EXTRA_REQUEST_ID = "io.moonbase.airdroid.extra.REQUEST_ID"
        const val EXTRA_REQUEST_ACCEPT = "io.moonbase.airdroid.extra.REQUEST_ACCEPT"
        const val EXTRA_CANCEL_TRANSFER = "io.moonbase.airdroid.extra.CANCEL_TRANSFER"

        const val ACTION_ACCEPT = "io.moonbase.airdroid.action.ACCEPT"
        const val ACTION_DECLINE = "io.moonbase.airdroid.action.DECLINE"
        const val ACTION_CANCEL = "io.moonbase.airdroid.action.CANCEL"

        private const val GROUP_KEY = "io.moonbase.airdroid.TRANSFERS"
        private const val SUMMARY_ID = 4000
        private const val REQUEST_ID = 4100
        private const val FIRST_TRANSFER_ID = 4200
    }

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val transferIds = mutableMapOf<String, Int>()
    private var nextTransferId = FIRST_TRANSFER_ID
    private var currentRequestId: Long? = null

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
            val transfers = NotificationChannel(
                CHANNEL_TRANSFERS,
                "File transfers",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "AirDrop send and receive progress"
                setShowBadge(false)
            }
            val requests = NotificationChannel(
                CHANNEL_REQUESTS,
                "Incoming AirDrop requests",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming AirDrop requests that need Accept or Decline"
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannels(listOf(transfers, requests))
        }
    }

    /** Stable notification ID for one transfer. */
    fun notificationId(transferId: String): Int =
        transferIds.getOrPut(transferId) { nextTransferId++ }

    /**
     * Updates the live progress notification for [transfer], or posts a
     * completion/failure notification when the transfer is terminal.
     */
    fun updateTransfer(transfer: TransferItem) {
        // An offered receive is represented by the high-priority request
        // notification/dialog, not by a transfer-progress notification.
        if (transfer.direction == TransferDirection.RECEIVED &&
            transfer.status == TransferStatus.OFFERED
        ) {
            return
        }

        val id = notificationId(transfer.id)
        if (transfer.status.isActive) {
            post(id, buildProgress(transfer))
        } else {
            post(id, buildTerminal(transfer))
        }
    }

    /** Group summary for active transfers; removed when nothing is active. */
    fun updateTransferSummary(activeCount: Int) {
        if (activeCount <= 0) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val text = if (activeCount == 1) "1 active transfer" else "$activeCount active transfers"
        val builder = baseBuilder(
            CHANNEL_TRANSFERS,
            "AirDroid transfers",
            text,
            R.drawable.ic_sensors,
        )
        builder
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setAutoCancel(false)
        post(SUMMARY_ID, builder.build())
    }

    fun cancelTransfer(transferId: String) {
        manager.cancel(notificationId(transferId))
    }

    fun cancelAllTransfers() {
        transferIds.keys.toList().forEach { key ->
            manager.cancel(transferIds.getValue(key))
        }
        manager.cancel(SUMMARY_ID)
    }

    /** High-priority request notification used while the app is backgrounded. */
    fun showIncomingRequest(request: IncomingRequest) {
        val isNewRequest = currentRequestId != request.id
        currentRequestId = request.id
        val sender = request.sender.ifBlank { request.peer }
        val title = "Incoming AirDrop from $sender"
        val summary = buildString {
            append(if (request.fileCount == 1) "1 file" else "${request.fileCount} files")
            if (request.totalBytes > 0) {
                append(" · ")
                append(request.totalBytes.readableBytes())
            }
        }
        val bigText = buildString {
            append(summary)
            if (request.names.isNotEmpty()) {
                append('\n')
                append(request.names.take(4).joinToString(", "))
            }
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL_REQUESTS)
        } else {
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
        }
        builder
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!isNewRequest)
            .setShowWhen(false)
            .setNumber(request.fileCount)
            .addAction(0, "Decline", requestDecisionIntent(request.id, accept = false))
            .addAction(0, "Accept", requestDecisionIntent(request.id, accept = true))
        post(REQUEST_ID, builder.build())
    }

    fun cancelIncomingRequest() {
        manager.cancel(REQUEST_ID)
        currentRequestId = null
    }

    private fun buildProgress(transfer: TransferItem): Notification {
        val title = when (transfer.direction) {
            TransferDirection.SENT -> "Sending ${transfer.fileName}"
            TransferDirection.RECEIVED -> "Receiving ${transfer.fileName}"
        }
        val text = buildString {
            append(transfer.peerName.ifBlank { "AirDrop" })
            if (transfer.totalBytes > 0) {
                append(" · ")
                append(transfer.transferredBytes.readableBytes())
                append(" / ")
                append(transfer.totalBytes.readableBytes())
            } else if (transfer.transferredBytes > 0) {
                append(" · ")
                append(transfer.transferredBytes.readableBytes())
            }
        }
        val icon = when (transfer.direction) {
            TransferDirection.SENT -> R.drawable.ic_send
            TransferDirection.RECEIVED -> R.drawable.ic_download
        }
        val percent = transfer.progressPercent
        val builder = baseBuilder(CHANNEL_TRANSFERS, title, text, icon)
        builder
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setProgress(100, percent ?: 0, percent == null)
        if (transfer.canCancel) {
            builder.addAction(0, "Cancel", cancelTransferIntent(transfer.id))
        }
        if (Build.VERSION.SDK_INT >= 36) {
            applyProgressStyle(builder, percent, percent == null, text)
        }
        return builder.build()
    }

    private fun buildTerminal(transfer: TransferItem): Notification {
        val title = when (transfer.status) {
            TransferStatus.COMPLETE -> when (transfer.direction) {
                TransferDirection.SENT -> "File sent"
                TransferDirection.RECEIVED -> "File received"
            }
            TransferStatus.FAILED -> when (transfer.direction) {
                TransferDirection.SENT -> "Send failed"
                TransferDirection.RECEIVED -> "Receive failed"
            }
            TransferStatus.CANCELLED -> when (transfer.direction) {
                TransferDirection.SENT -> "Send cancelled"
                TransferDirection.RECEIVED -> "Receive cancelled"
            }
            else -> "AirDroid transfer"
        }
        val text = when {
            transfer.status == TransferStatus.COMPLETE &&
                transfer.direction == TransferDirection.SENT ->
                "${transfer.fileName} → ${transfer.peerName.ifBlank { "AirDrop" }}"
            transfer.status == TransferStatus.COMPLETE ->
                "${transfer.fileName} from ${transfer.peerName.ifBlank { "AirDrop" }}"
            transfer.status == TransferStatus.FAILED ->
                "${transfer.fileName} · ${transfer.error?.takeIf { it.isNotBlank() } ?: "Failed"}"
            transfer.status == TransferStatus.CANCELLED ->
                "${transfer.fileName} · ${transfer.error?.takeIf { it.isNotBlank() } ?: "Cancelled"}"
            else -> transfer.fileName
        }
        val icon = when (transfer.direction) {
            TransferDirection.SENT -> R.drawable.ic_send
            TransferDirection.RECEIVED -> R.drawable.ic_download
        }
        val builder = baseBuilder(CHANNEL_TRANSFERS, title, text, icon)
        builder
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setWhen(transfer.completedAt ?: System.currentTimeMillis())
            .setShowWhen(true)
        return builder.build()
    }

    private fun baseBuilder(
        channelId: String,
        title: String,
        text: String,
        icon: Int,
    ): Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId)
        } else {
            Notification.Builder(context).setPriority(Notification.PRIORITY_LOW)
        }
        builder
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setGroup(GROUP_KEY)
            .setShowWhen(false)
        return builder
    }

    private fun requestDecisionIntent(requestId: Long, accept: Boolean): PendingIntent {
        val intent = Intent(context, TransferActionReceiver::class.java).apply {
            action = if (accept) ACTION_ACCEPT else ACTION_DECLINE
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_REQUEST_ACCEPT, accept)
        }
        val requestCode = (requestId.hashCode() * 31 + if (accept) 1 else 0) and 0x7fffffff
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelTransferIntent(transferId: String): PendingIntent {
        val intent = Intent(context, TransferActionReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_CANCEL_TRANSFER, transferId)
        }
        return PendingIntent.getBroadcast(
            context,
            (notificationId(transferId) + 100_000) and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
    private fun requestPromotedOngoing(
        builder: Notification.Builder,
        style: Notification.ProgressStyle,
    ) {
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
