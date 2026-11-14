package io.moonbase.droidy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles Accept/Decline/Cancel actions without opening the activity. */
class TransferActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = DroidyControllerHolder.current
        when (intent.action) {
            DroidyNotifications.ACTION_ACCEPT,
            DroidyNotifications.ACTION_DECLINE,
            -> {
                val requestId = intent.getLongExtra(DroidyNotifications.EXTRA_REQUEST_ID, -1L)
                if (requestId >= 0 && controller != null) {
                    controller.resolveIncomingFromNotification(
                        requestId,
                        intent.action == DroidyNotifications.ACTION_ACCEPT,
                    )
                } else {
                    launchActivity(context, intent)
                }
            }
            DroidyNotifications.ACTION_CANCEL -> {
                val transferId = intent.getStringExtra(DroidyNotifications.EXTRA_CANCEL_TRANSFER)
                if (transferId != null && controller != null) {
                    controller.cancelTransfer(transferId)
                } else {
                    launchActivity(context, intent)
                }
            }
        }
    }

    private fun launchActivity(context: Context, source: Intent) {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = source.action
            source.extras?.let { putExtras(it) }
        }
        context.startActivity(launch)
    }
}
