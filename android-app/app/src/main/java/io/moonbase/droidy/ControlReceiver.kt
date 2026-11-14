package io.moonbase.droidy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ADB-only control surface for debugging. Protected by android.permission.DUMP,
 * which the adb shell user has. All output is also written to logcat tag
 * "DroidyLog".
 *
 * Examples:
 *   adb shell am broadcast -n io.moonbase.droidy/.ControlReceiver \
 *     -a io.moonbase.droidy.debug.STATUS
 *   adb shell am broadcast -n io.moonbase.droidy/.ControlReceiver \
 *     -a io.moonbase.droidy.debug.SEND --es path /sdcard/Download/file.bin
 */
class ControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STATUS = "io.moonbase.droidy.debug.STATUS"
        const val ACTION_START_SCAN = "io.moonbase.droidy.debug.START_SCAN"
        const val ACTION_STOP_SCAN = "io.moonbase.droidy.debug.STOP_SCAN"
        const val ACTION_ACCEPT = "io.moonbase.droidy.debug.ACCEPT"
        const val ACTION_DECLINE = "io.moonbase.droidy.debug.DECLINE"
        const val ACTION_SELECT_PEER = "io.moonbase.droidy.debug.SELECT_PEER"
        const val ACTION_SEND = "io.moonbase.droidy.debug.SEND"
        const val ACTION_SEND_TEXT = "io.moonbase.droidy.debug.SEND_TEXT"
        const val ACTION_CANCEL = "io.moonbase.droidy.debug.CANCEL"
        const val ACTION_CHANNEL = "io.moonbase.droidy.debug.CHANNEL"
        const val ACTION_SET_NAME = "io.moonbase.droidy.debug.SET_NAME"
        const val ACTION_ENTER_AIRDROP = "io.moonbase.droidy.debug.ENTER_AIRDROP"
        const val ACTION_EXIT_AIRDROP = "io.moonbase.droidy.debug.EXIT_AIRDROP"
        const val ACTION_REBOOT_ENTER = "io.moonbase.droidy.debug.REBOOT_ENTER"
        const val ACTION_REBOOT_EXIT = "io.moonbase.droidy.debug.REBOOT_EXIT"
        const val ACTION_DUMP_LOG = "io.moonbase.droidy.debug.DUMP_LOG"
        const val ACTION_CLEAR_LOG = "io.moonbase.droidy.debug.CLEAR_LOG"
        const val ACTION_HISTORY = "io.moonbase.droidy.debug.HISTORY"
        const val ACTION_PIN_PEER = "io.moonbase.droidy.debug.PIN_PEER"
        const val ACTION_DELETE_RECEIVED = "io.moonbase.droidy.debug.DELETE_RECEIVED"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val controller = DroidyControllerHolder.current
        if (controller == null) {
            Log.i("DroidyDebug", "controller is not running")
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_STATUS -> dumpStatus(controller)
                    ACTION_START_SCAN -> controller.scan()
                    ACTION_STOP_SCAN -> controller.stopScanning()
                    ACTION_ACCEPT -> controller.acceptIncoming()
                    ACTION_DECLINE -> controller.declineIncoming()
                    ACTION_SELECT_PEER -> {
                        val mac = intent.getStringExtra("mac").orEmpty()
                        Log.i(
                            "DroidyDebug",
                            if (controller.selectPeerByMac(mac)) "selected $mac"
                            else "peer not found: $mac",
                        )
                    }
                    ACTION_SEND -> {
                        val path = intent.getStringExtra("path").orEmpty()
                        intent.getStringExtra("mac")?.takeIf { it.isNotBlank() }?.let {
                            controller.selectPeerByMac(it)
                        }
                        if (path.isBlank()) {
                            Log.i("DroidyDebug", "SEND needs --es path <file>")
                        } else {
                            Log.i("DroidyDebug", "send $path")
                            controller.sendPath(path)
                        }
                    }
                    ACTION_SEND_TEXT -> {
                        val text = intent.getStringExtra("text").orEmpty()
                        intent.getStringExtra("mac")?.takeIf { it.isNotBlank() }?.let {
                            controller.selectPeerByMac(it)
                        }
                        if (text.isBlank()) {
                            Log.i("DroidyDebug", "SEND_TEXT needs --es text <text>")
                        } else {
                            Log.i("DroidyDebug", "send text (${text.length} chars)")
                            controller.sendText(text)
                        }
                    }
                    ACTION_CANCEL -> {
                        val id = intent.getStringExtra("id").orEmpty()
                        Log.i("DroidyDebug", "cancel $id")
                        controller.cancelTransfer(id)
                    }
                    ACTION_CHANNEL -> {
                        val channel = intent.getIntExtra("channel", controller.settings.channel)
                        val s = controller.settings
                        controller.saveSettings(channel, s.bleArm, s.deviceName, s.themeMode, s.tintMode)
                        Log.i("DroidyDebug", "channel=$channel")
                    }
                    ACTION_SET_NAME -> {
                        val name = intent.getStringExtra("name").orEmpty()
                        val s = controller.settings
                        controller.saveSettings(s.channel, s.bleArm, name, s.themeMode, s.tintMode)
                        Log.i("DroidyDebug", "name=$name")
                    }
                    ACTION_ENTER_AIRDROP -> controller.enterAirDropMode()
                    ACTION_EXIT_AIRDROP -> controller.exitAirDropMode()
                    ACTION_REBOOT_ENTER -> controller.enterAirDropMode(reboot = true)
                    ACTION_REBOOT_EXIT -> controller.exitAirDropMode(reboot = true)
                    ACTION_DUMP_LOG -> controller.logs.forEach { Log.i("DroidyLog", it) }
                    ACTION_CLEAR_LOG -> {
                        controller.clearLogs()
                        Log.i("DroidyDebug", "logs cleared")
                    }
                    ACTION_HISTORY -> dumpHistory(controller)
                    ACTION_PIN_PEER -> {
                        val mac = intent.getStringExtra("mac").orEmpty()
                        controller.togglePinned(mac)
                        Log.i("DroidyDebug", "pinned[$mac]=${controller.isPinned(mac)}")
                    }
                    ACTION_DELETE_RECEIVED -> {
                        val path = intent.getStringExtra("path").orEmpty()
                        controller.deleteReceivedFile(path)
                        Log.i("DroidyDebug", "deleted $path")
                    }
                    else -> Log.i("DroidyDebug", "unknown action: $action")
                }
            } catch (e: Exception) {
                Log.i("DroidyDebug", "action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun dumpHistory(controller: DroidyController) {
        controller.receivedFiles.forEach { Log.i("DroidyDebug", "file=$it") }
        controller.completedTransfers.forEach { t ->
            Log.i(
                "DroidyDebug",
                "done id=${t.id} dir=${t.direction} file=${t.fileName} " +
                    "bytes=${t.transferredBytes}/${t.totalBytes} status=${t.status} " +
                    "peer=${t.peerName} path=${t.localPath.orEmpty()}",
            )
        }
    }

    private fun dumpStatus(controller: DroidyController) {
        Log.i(
            "DroidyDebug",
            "status=${controller.status} scanning=${controller.scanning} " +
                "receiver=${controller.receiverRunning} peers=${controller.peers.size} " +
                "sent=${controller.completedTransfers.count { it.direction == TransferDirection.SENT }} " +
                "received=${controller.completedTransfers.count { it.direction == TransferDirection.RECEIVED }}",
        )
        controller.selectedPeer?.let { Log.i("DroidyDebug", "selected=${it.mac} ${it.name} rssi=${it.rssi}") }
        controller.peers.forEach { peer ->
            Log.i("DroidyDebug", "peer=${peer.mac} name=${peer.name} rssi=${peer.rssi}")
        }
        controller.activeTransfers.forEach { t ->
            Log.i(
                "DroidyDebug",
                "active id=${t.id} dir=${t.direction} file=${t.fileName} " +
                    "bytes=${t.transferredBytes}/${t.totalBytes} status=${t.status}",
            )
        }
        controller.incomingRequest?.let { req ->
            Log.i("DroidyDebug", "incoming id=${req.id} files=${req.fileCount} bytes=${req.totalBytes}")
        }
    }
}
