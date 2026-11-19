package io.moonbase.airdroid

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
 * "AirDroidLog".
 *
 * Examples:
 *   adb shell am broadcast -n io.moonbase.airdroid/.ControlReceiver \
 *     -a io.moonbase.airdroid.debug.STATUS
 *   adb shell am broadcast -n io.moonbase.airdroid/.ControlReceiver \
 *     -a io.moonbase.airdroid.debug.SEND --es path /sdcard/Download/file.bin
 */
class ControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STATUS = "io.moonbase.airdroid.debug.STATUS"
        const val ACTION_START_SCAN = "io.moonbase.airdroid.debug.START_SCAN"
        const val ACTION_STOP_SCAN = "io.moonbase.airdroid.debug.STOP_SCAN"
        const val ACTION_ACCEPT = "io.moonbase.airdroid.debug.ACCEPT"
        const val ACTION_DECLINE = "io.moonbase.airdroid.debug.DECLINE"
        const val ACTION_SELECT_PEER = "io.moonbase.airdroid.debug.SELECT_PEER"
        const val ACTION_SEND = "io.moonbase.airdroid.debug.SEND"
        const val ACTION_SEND_TEXT = "io.moonbase.airdroid.debug.SEND_TEXT"
        const val ACTION_CANCEL = "io.moonbase.airdroid.debug.CANCEL"
        const val ACTION_CHANNEL = "io.moonbase.airdroid.debug.CHANNEL"
        const val ACTION_SET_BLE = "io.moonbase.airdroid.debug.SET_BLE"
        const val ACTION_SET_NAME = "io.moonbase.airdroid.debug.SET_NAME"
        const val ACTION_ENTER_TRANSFER = "io.moonbase.airdroid.debug.ENTER_TRANSFER"
        const val ACTION_EXIT_TRANSFER = "io.moonbase.airdroid.debug.EXIT_TRANSFER"
        const val ACTION_REBOOT_ENTER = "io.moonbase.airdroid.debug.REBOOT_ENTER"
        const val ACTION_REBOOT_EXIT = "io.moonbase.airdroid.debug.REBOOT_EXIT"
        const val ACTION_DUMP_LOG = "io.moonbase.airdroid.debug.DUMP_LOG"
        const val ACTION_CLEAR_LOG = "io.moonbase.airdroid.debug.CLEAR_LOG"
        const val ACTION_HISTORY = "io.moonbase.airdroid.debug.HISTORY"
        const val ACTION_PIN_PEER = "io.moonbase.airdroid.debug.PIN_PEER"
        const val ACTION_DELETE_RECEIVED = "io.moonbase.airdroid.debug.DELETE_RECEIVED"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val controller = AirDroidControllerHolder.current
        if (controller == null) {
            Log.i("AirDroidDebug", "controller is not running")
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
                            "AirDroidDebug",
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
                            Log.i("AirDroidDebug", "SEND needs --es path <file>")
                        } else {
                            Log.i("AirDroidDebug", "send $path")
                            controller.sendPath(path)
                        }
                    }
                    ACTION_SEND_TEXT -> {
                        val text = intent.getStringExtra("text").orEmpty()
                        intent.getStringExtra("mac")?.takeIf { it.isNotBlank() }?.let {
                            controller.selectPeerByMac(it)
                        }
                        if (text.isBlank()) {
                            Log.i("AirDroidDebug", "SEND_TEXT needs --es text <text>")
                        } else {
                            Log.i("AirDroidDebug", "send text (${text.length} chars)")
                            controller.sendText(text)
                        }
                    }
                    ACTION_CANCEL -> {
                        val id = intent.getStringExtra("id").orEmpty()
                        Log.i("AirDroidDebug", "cancel $id")
                        controller.cancelTransfer(id)
                    }
                    ACTION_CHANNEL -> {
                        val channel = intent.getIntExtra("channel", controller.settings.channel)
                        val s = controller.settings
                        controller.saveSettings(channel, s.bleArm, s.deviceName, s.themeMode, s.tintMode)
                        Log.i("AirDroidDebug", "channel=$channel")
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
                        Log.i("AirDroidDebug", "name=$name")
                    }
                    ACTION_ENTER_TRANSFER -> controller.enterTransferMode()
                    ACTION_EXIT_TRANSFER -> controller.exitTransferMode()
                    ACTION_REBOOT_ENTER -> controller.enterTransferMode(reboot = true)
                    ACTION_REBOOT_EXIT -> controller.exitTransferMode(reboot = true)
                    ACTION_DUMP_LOG -> controller.logs.forEach { Log.i("AirDroidLog", it) }
                    ACTION_CLEAR_LOG -> {
                        controller.clearLogs()
                        Log.i("AirDroidDebug", "logs cleared")
                    }
                    ACTION_HISTORY -> dumpHistory(controller)
                    ACTION_PIN_PEER -> {
                        val mac = intent.getStringExtra("mac").orEmpty()
                        controller.togglePinned(mac)
                        Log.i("AirDroidDebug", "pinned[$mac]=${controller.isPinned(mac)}")
                    }
                    ACTION_DELETE_RECEIVED -> {
                        val path = intent.getStringExtra("path").orEmpty()
                        controller.deleteReceivedFile(path)
                        Log.i("AirDroidDebug", "deleted $path")
                    }
                    else -> Log.i("AirDroidDebug", "unknown action: $action")
                }
            } catch (e: Exception) {
                Log.i("AirDroidDebug", "action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun dumpHistory(controller: AirDroidController) {
        controller.receivedFiles.forEach { Log.i("AirDroidDebug", "file=$it") }
        controller.completedTransfers.forEach { t ->
            Log.i(
                "AirDroidDebug",
                "done id=${t.id} dir=${t.direction} file=${t.fileName} " +
                    "bytes=${t.transferredBytes}/${t.totalBytes} status=${t.status} " +
                    "peer=${t.peerName} path=${t.localPath.orEmpty()}",
            )
        }
    }

    private fun dumpStatus(controller: AirDroidController) {
        Log.i(
            "AirDroidDebug",
            "status=${controller.status} scanning=${controller.scanning} " +
                "receiver=${controller.receiverRunning} " +
                "peers=${controller.peers.count { it.airdropReady }} " +
                "sent=${controller.completedTransfers.count { it.direction == TransferDirection.SENT }} " +
                "received=${controller.completedTransfers.count { it.direction == TransferDirection.RECEIVED }}",
        )
        controller.selectedPeer?.let {
            Log.i(
                "AirDroidDebug",
                "selected=${it.mac} ${it.name} devclass=${it.devclass} rssi=${it.rssi} " +
                    "channel=${it.awdlChannel} airdrop=${it.airdropState}",
            )
        }
        controller.peers.forEach { peer ->
            Log.i(
                "AirDroidDebug",
                "peer=${peer.mac} name=${peer.name} devclass=${peer.devclass} rssi=${peer.rssi} " +
                    "channel=${peer.awdlChannel} airdrop=${peer.airdropState}",
            )
        }
        controller.activeTransfers.forEach { t ->
            Log.i(
                "AirDroidDebug",
                "active id=${t.id} dir=${t.direction} file=${t.fileName} " +
                    "bytes=${t.transferredBytes}/${t.totalBytes} status=${t.status}",
            )
        }
        controller.incomingRequest?.let { req ->
            Log.i("AirDroidDebug", "incoming id=${req.id} files=${req.fileCount} bytes=${req.totalBytes}")
        }
    }
}
