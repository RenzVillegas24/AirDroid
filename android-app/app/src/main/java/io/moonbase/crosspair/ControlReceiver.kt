package io.moonbase.crosspair

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
 * "CrossPairLog".
 *
 * Examples:
 *   adb shell am broadcast -n io.moonbase.crosspair/.ControlReceiver \
 *     -a io.moonbase.crosspair.debug.STATUS
 *   adb shell am broadcast -n io.moonbase.crosspair/.ControlReceiver \
 *     -a io.moonbase.crosspair.debug.SEND --es path /sdcard/Download/file.bin
 */
class ControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STATUS = "io.moonbase.crosspair.debug.STATUS"
        const val ACTION_START_SCAN = "io.moonbase.crosspair.debug.START_SCAN"
        const val ACTION_STOP_SCAN = "io.moonbase.crosspair.debug.STOP_SCAN"
        const val ACTION_ACCEPT = "io.moonbase.crosspair.debug.ACCEPT"
        const val ACTION_DECLINE = "io.moonbase.crosspair.debug.DECLINE"
        const val ACTION_SELECT_PEER = "io.moonbase.crosspair.debug.SELECT_PEER"
        const val ACTION_SEND = "io.moonbase.crosspair.debug.SEND"
        const val ACTION_SEND_TEXT = "io.moonbase.crosspair.debug.SEND_TEXT"
        const val ACTION_CANCEL = "io.moonbase.crosspair.debug.CANCEL"
        const val ACTION_CHANNEL = "io.moonbase.crosspair.debug.CHANNEL"
        const val ACTION_SET_BLE = "io.moonbase.crosspair.debug.SET_BLE"
        const val ACTION_SET_NAME = "io.moonbase.crosspair.debug.SET_NAME"
        const val ACTION_ENTER_TRANSFER = "io.moonbase.crosspair.debug.ENTER_TRANSFER"
        const val ACTION_EXIT_TRANSFER = "io.moonbase.crosspair.debug.EXIT_TRANSFER"
        const val ACTION_REBOOT_ENTER = "io.moonbase.crosspair.debug.REBOOT_ENTER"
        const val ACTION_REBOOT_EXIT = "io.moonbase.crosspair.debug.REBOOT_EXIT"
        const val ACTION_DUMP_LOG = "io.moonbase.crosspair.debug.DUMP_LOG"
        const val ACTION_CLEAR_LOG = "io.moonbase.crosspair.debug.CLEAR_LOG"
        const val ACTION_HISTORY = "io.moonbase.crosspair.debug.HISTORY"
        const val ACTION_PIN_PEER = "io.moonbase.crosspair.debug.PIN_PEER"
        const val ACTION_DELETE_RECEIVED = "io.moonbase.crosspair.debug.DELETE_RECEIVED"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val controller = CrossPairControllerHolder.current
        if (controller == null) {
            Log.i("CrossPairDebug", "controller is not running")
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
                            "CrossPairDebug",
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
                            Log.i("CrossPairDebug", "SEND needs --es path <file>")
                        } else {
                            Log.i("CrossPairDebug", "send $path")
                            controller.sendPath(path)
                        }
                    }
                    ACTION_SEND_TEXT -> {
                        val text = intent.getStringExtra("text").orEmpty()
                        intent.getStringExtra("mac")?.takeIf { it.isNotBlank() }?.let {
                            controller.selectPeerByMac(it)
                        }
                        if (text.isBlank()) {
                            Log.i("CrossPairDebug", "SEND_TEXT needs --es text <text>")
                        } else {
                            Log.i("CrossPairDebug", "send text (${text.length} chars)")
                            controller.sendText(text)
                        }
                    }
                    ACTION_CANCEL -> {
                        val id = intent.getStringExtra("id").orEmpty()
                        Log.i("CrossPairDebug", "cancel $id")
                        controller.cancelTransfer(id)
                    }
                    ACTION_CHANNEL -> {
                        val channel = intent.getIntExtra("channel", controller.settings.channel)
                        val s = controller.settings
                        controller.saveSettings(channel, s.bleArm, s.deviceName, s.themeMode, s.tintMode)
                        Log.i("CrossPairDebug", "channel=$channel")
                    }
                    ACTION_SET_BLE -> {
                        val arm = intent.getIntExtra("arm", controller.settings.bleArm)
                        val s = controller.settings
                        controller.saveSettings(s.channel, arm, s.deviceName, s.themeMode, s.tintMode)
                        Log.i("DroidyDebug", "bleArm=$arm")
                    }
                    ACTION_SET_NAME -> {
                        val name = intent.getStringExtra("name").orEmpty()
                        val s = controller.settings
                        controller.saveSettings(s.channel, s.bleArm, name, s.themeMode, s.tintMode)
                        Log.i("CrossPairDebug", "name=$name")
                    }
                    ACTION_ENTER_TRANSFER -> controller.enterTransferMode()
                    ACTION_EXIT_TRANSFER -> controller.exitTransferMode()
                    ACTION_REBOOT_ENTER -> controller.enterTransferMode(reboot = true)
                    ACTION_REBOOT_EXIT -> controller.exitTransferMode(reboot = true)
                    ACTION_DUMP_LOG -> controller.logs.forEach { Log.i("CrossPairLog", it) }
                    ACTION_CLEAR_LOG -> {
                        controller.clearLogs()
                        Log.i("CrossPairDebug", "logs cleared")
                    }
                    ACTION_HISTORY -> dumpHistory(controller)
                    ACTION_PIN_PEER -> {
                        val mac = intent.getStringExtra("mac").orEmpty()
                        controller.togglePinned(mac)
                        Log.i("CrossPairDebug", "pinned[$mac]=${controller.isPinned(mac)}")
                    }
                    ACTION_DELETE_RECEIVED -> {
                        val path = intent.getStringExtra("path").orEmpty()
                        controller.deleteReceivedFile(path)
                        Log.i("CrossPairDebug", "deleted $path")
                    }
                    else -> Log.i("CrossPairDebug", "unknown action: $action")
                }
            } catch (e: Exception) {
                Log.i("CrossPairDebug", "action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun dumpHistory(controller: CrossPairController) {
        controller.receivedFiles.forEach { Log.i("CrossPairDebug", "file=$it") }
        controller.completedTransfers.forEach { t ->
            Log.i(
                "CrossPairDebug",
                "done id=${t.id} dir=${t.direction} file=${t.fileName} " +
                    "bytes=${t.transferredBytes}/${t.totalBytes} status=${t.status} " +
                    "peer=${t.peerName} path=${t.localPath.orEmpty()}",
            )
        }
    }

    private fun dumpStatus(controller: CrossPairController) {
        Log.i(
            "CrossPairDebug",
            "status=${controller.status} scanning=${controller.scanning} " +
                "receiver=${controller.receiverRunning} " +
                "peers=${controller.peers.count { it.airdropReady }} " +
                "sent=${controller.completedTransfers.count { it.direction == TransferDirection.SENT }} " +
                "received=${controller.completedTransfers.count { it.direction == TransferDirection.RECEIVED }}",
        )
        controller.selectedPeer?.let {
            Log.i(
                "CrossPairDebug",
                "selected=${it.mac} ${it.name} devclass=${it.devclass} rssi=${it.rssi} " +
                    "channel=${it.awdlChannel} airdrop=${it.airdropState}",
            )
        }
        controller.peers.forEach { peer ->
            Log.i(
                "CrossPairDebug",
                "peer=${peer.mac} name=${peer.name} devclass=${peer.devclass} rssi=${peer.rssi} " +
                    "channel=${peer.awdlChannel} airdrop=${peer.airdropState}",
            )
        }
        controller.activeTransfers.forEach { t ->
            Log.i(
                "CrossPairDebug",
                "active id=${t.id} dir=${t.direction} file=${t.fileName} " +
                    "bytes=${t.transferredBytes}/${t.totalBytes} status=${t.status}",
            )
        }
        controller.incomingRequest?.let { req ->
            Log.i("CrossPairDebug", "incoming id=${req.id} files=${req.fileCount} bytes=${req.totalBytes}")
        }
    }
}
