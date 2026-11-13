package io.moonbase.droidy

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Peer(
    val mac: String,
    val name: String,
    val devclass: Int,
    val rssi: Int,
)

data class DroidySettings(
    val channel: Int = 44,
    val bleArm: Int = 0,
    val deviceName: String = "Droidy K20",
)

/**
 * A live `owly-k20-helper session` process. The helper owns the monitor and
 * awdl0 for the whole session, so scans and sends must reuse it instead of
 * starting a second radio instance.
 */
private class HelperSession(
    val process: Process,
    val writer: BufferedWriter,
    val channel: Int,
    val name: String,
    val ready: CompletableDeferred<Boolean> = CompletableDeferred(),
)

/** A live `owly-k20-helper receive` process; its stdin answers requests. */
private class ReceiveSession(
    val process: Process,
    val writer: BufferedWriter,
    val ready: CompletableDeferred<Boolean> = CompletableDeferred(),
)

data class IncomingRequest(
    val id: Long,
    val peer: String,
    val sender: String,
    val fileCount: Int,
    val totalBytes: Long,
)

/** One in-flight scan/send command, matched by the helper's request id. */
private class PendingRequest(
    val id: String,
    val kind: String,
    val onLine: (String) -> Unit,
    val completion: CompletableDeferred<Int> = CompletableDeferred(),
)

enum class MonitorState(val badge: String) {
    MONITOR("READY"),
    STOCK("WIFI MODE"),
    UNVERIFIED("VERIFY KERNEL"),
}

/**
 * The radio only works on the monitor-enabled K20 boot image. Two known
 * monitor builds are recognised by kernel release; any other build can be
 * verified by reading the boot command line through root.
 */
data class KernelStatus(
    val release: String,
    val buildVersion: String,
    val state: MonitorState,
    val detail: String,
    val verifiedBy: String? = null,
) {
    val monitorReady: Boolean
        get() = state == MonitorState.MONITOR

    fun withRootCheck(cmdline: String, conMode: Int?): KernelStatus {
        val cmdlineMode = Regex("""(?:wlan\.)?con_mode=(\d+)""")
            .find(cmdline)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        val effectiveMode = conMode ?: cmdlineMode
        if (effectiveMode == 4) {
            return copy(
                state = MonitorState.MONITOR,
                detail = "Root confirmed con_mode=4: monitor + hidden STA is active.",
                verifiedBy = "root",
            )
        }
        if (effectiveMode == 0) {
            return copy(
                state = MonitorState.STOCK,
                detail = "Root confirmed con_mode=0. Normal Wi-Fi mode.",
                verifiedBy = "root",
            )
        }
        if (cmdline.isNotBlank() || conMode != null) {
            return copy(
                state = MonitorState.STOCK,
                detail = "Root confirmed no monitor con_mode=4. Normal Wi-Fi mode.",
                verifiedBy = "root",
            )
        }
        return this
    }

    companion object {
        private val MONITOR_RELEASES = setOf(
            "4.14.357-openela-perf-g7a7b4d1b6808",
            "4.14.357-openela-perf-g2a67a9737583-dirty",
        )
        private val STOCK_RELEASES = setOf(
            "4.14.357-openela-perf-g2a67a9737583",
        )

        fun detect(): KernelStatus {
            val release: String
            val version: String
            try {
                val uts = Os.uname()
                release = uts.release
                version = uts.version
            } catch (e: Exception) {
                return KernelStatus(
                    release = "unknown",
                    buildVersion = "unknown",
                    state = MonitorState.UNVERIFIED,
                    detail = "Could not read the kernel release: ${e.message}",
                )
            }
            val monitorBuild = release in MONITOR_RELEASES ||
                release.startsWith("4.14.357-openela-perf-g7a7b4d1b6808") ||
                release.startsWith("4.14.357-openela-perf-g4d9c3a067732")
            return when {
                monitorBuild -> KernelStatus(
                    release = release,
                    buildVersion = version,
                    state = MonitorState.UNVERIFIED,
                    detail = "Patched monitor-capable kernel. Reading con_mode with root...",
                )
                release in STOCK_RELEASES -> KernelStatus(
                    release = release,
                    buildVersion = version,
                    state = MonitorState.STOCK,
                    detail = "Normal Wi-Fi boot. Android Wi-Fi is active.",
                )
                else -> KernelStatus(
                    release = release,
                    buildVersion = version,
                    state = MonitorState.UNVERIFIED,
                    detail = "Unknown kernel build. Verify the boot command line with root before using the radio.",
                )
            }
        }
    }
}

/**
 * Owns the root helper, BLE wake advertisement, and all long-running operations.
 * Compose observes the state properties directly; helper output is appended on
 * the main thread so the UI and logs stay consistent.
 */
class DroidyController(private val context: Context) {
    companion object {
        private const val APP_TMP = "/data/local/tmp/owly-app"
        const val RECEIVE_DIR = "/sdcard/Download/Droidy"
        private const val SCAN_SECONDS = 12L
        private const val SESSION_READY_TIMEOUT_MS = 30_000L
        private const val RECEIVE_READY_TIMEOUT_MS = 30_000L
        private const val SCAN_TIMEOUT_MS = (SCAN_SECONDS + 20L) * 1000L
        private const val SEND_TIMEOUT_MS = 300_000L
        private val ASSETS = arrayOf(
            "owly-mode.sh",
            "owly-nl80211-set-channel",
            "owly-monitor-send-frame",
            "owly-monitor-full",
            "owly-monitor-probe",
            "probe-owly.bin",
            "awdl-mif-k20.bin",
            "owly-k20-helper",
        )
    }

    private val prefs = context.getSharedPreferences("droidy", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prepareMutex = Mutex()
    private val commandMutex = Mutex()
    private val sessionMutex = Mutex()
    private val receiveMutex = Mutex()
    private val binDir = File(context.filesDir, "bin")
    private var prepared = false
    private var helperSession: HelperSession? = null
    private var receiveSession: ReceiveSession? = null
    private var pendingRequest: PendingRequest? = null
    private var requestCounter = 0
    private var sendCancelled = false
    private var sendGeneration = 0
    private val notifications = DroidyNotifications(context)

    var settings by mutableStateOf(loadSettings())
        private set
    var peers by mutableStateOf<List<Peer>>(emptyList())
        private set
    var selectedMac by mutableStateOf<String?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var sendProgressBytes by mutableStateOf(0L)
        private set
    var sendProgressTotal by mutableStateOf(0L)
        private set
    var sendCancelling by mutableStateOf(false)
        private set
    var receiverRunning by mutableStateOf(false)
        private set
    var receiverStarting by mutableStateOf(false)
        private set
    var receiverStopping by mutableStateOf(false)
        private set
    var incomingRequest by mutableStateOf<IncomingRequest?>(null)
        private set
    var receiveDecisionPending by mutableStateOf(false)
        private set
    var receiveTransferring by mutableStateOf(false)
        private set
    var receiveProgressBytes by mutableStateOf(0L)
        private set
    var receiveProgressTotal by mutableStateOf(0L)
        private set
    var kernel by mutableStateOf(KernelStatus.detect())
        private set
    var verifyingKernel by mutableStateOf(false)
        private set
    var modeSwitching by mutableStateOf(false)
        private set
    var status by mutableStateOf("READY")
        private set
    var sendMessage by mutableStateOf("")
        private set
    var receiveMessage by mutableStateOf("Receiver is stopped")
        private set
    var pickedFile by mutableStateOf<File?>(null)
        private set

    val logs = mutableStateListOf<String>()
    val receivedFiles = mutableStateListOf<String>()

    private val ble = BleAdvertiser(context)

    val selectedPeer: Peer?
        get() = selectedMac?.let { mac -> peers.firstOrNull { it.mac == mac } }

    val effectiveStatus: String
        get() = when {
            scanning || sending || receiverRunning || verifyingKernel || modeSwitching -> status
            !kernel.monitorReady -> kernel.state.badge
            else -> status
        }

    val sendProgressPercent: Int?
        get() = if (sendProgressTotal > 0) {
            ((sendProgressBytes * 100) / sendProgressTotal).toInt().coerceIn(0, 100)
        } else {
            null
        }

    val receiveProgressPercent: Int?
        get() = if (receiveProgressTotal > 0) {
            ((receiveProgressBytes * 100) / receiveProgressTotal).toInt().coerceIn(0, 100)
        } else {
            null
        }

    init {
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        append("Droidy K20")
        append("AirDrop over AWDL · Jetpack Compose · Material 3")
        append("Channel ${settings.channel} · BLE arm ${settings.bleArm}")
        append("Kernel ${kernel.release}")
        append(kernel.detail)
        scope.launch { verifyKernelWithRoot() }
    }

    fun selectPeer(peer: Peer) {
        selectedMac = peer.mac
    }

    fun clearPickedFile() {
        pickedFile = null
        sendMessage = ""
    }

    fun clearLogs() {
        logs.clear()
        append("Log cleared")
    }

    fun saveSettings(channel: Int, bleArm: Int, deviceName: String) {
        val previous = settings
        val cleanName = deviceName.trim().ifBlank { "Droidy K20" }
        settings = DroidySettings(channel, bleArm, cleanName)
        prefs.edit()
            .putInt("channel", channel)
            .putInt("bleArm", bleArm)
            .putString("deviceName", cleanName)
            .apply()
        sendMessage = "Settings saved · channel $channel"
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        append("Settings saved: channel=$channel bleArm=$bleArm name=$cleanName")

        // Channel and identity are fixed when the session helper starts awdl0.
        // Drop the live session so the next scan/send recreates it with the new
        // values; a BLE-arm change only needs the advertisement restarted.
        val identityChanged = channel != previous.channel || cleanName != previous.deviceName
        if (identityChanged && helperSession != null) {
            scope.launch {
                stopSession()
                append("Live session dropped; the next operation uses channel $channel as $cleanName.")
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
            }
        }
        if (bleArm != previous.bleArm && ble.isRunning()) {
            scope.launch { startBle(fresh = true) }
        }
    }

    private fun loadSettings(): DroidySettings = DroidySettings(
        channel = prefs.getInt("channel", 44),
        bleArm = prefs.getInt("bleArm", 0),
        deviceName = prefs.getString("deviceName", "Droidy K20") ?: "Droidy K20",
    )

    suspend fun prepare() {
        if (prepared) return
        prepareMutex.withLock {
            if (prepared) return
            withContext(Dispatchers.IO) {
                try {
                    ProcessBuilder("su", "-c", "pkill -x owly-k20-helper 2>/dev/null; pkill -x owly-monitor-full 2>/dev/null; sleep 1; true")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                    binDir.mkdirs()
                    for (name in ASSETS) {
                        val out = File(binDir, name)
                        context.assets.open(name).use { input ->
                            FileOutputStream(out).use { output -> input.copyTo(output) }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        append("Asset install failed: ${e.message}")
                        status = "ROOT ERROR"
                    }
                }
            }
            val rc = execRoot(
                label = "install root tools",
                command = "mkdir -p $APP_TMP; cp '${binDir.absolutePath}'/* $APP_TMP/; " +
                    "chmod 755 $APP_TMP/*; ls -l $APP_TMP",
                requirePrepared = false,
            )
            prepared = rc == 0
        }
    }

    suspend fun scan() {
        if (scanning || sending || sendCancelling || receiverRunning) return
        if (!requireMonitorKernel()) return
        scanning = true
        status = "SCANNING"
        peers = emptyList()
        selectedMac = null
        append("")
        append("Scanning for AirDrop people...")
        val rc = runSessionCommand(
            kind = "scan",
            timeoutMs = SCAN_TIMEOUT_MS,
            command = { id -> "scan $SCAN_SECONDS $id" },
        )
        scanning = false
        status = when {
            rc != 0 -> "CHECK LOG"
            peers.isEmpty() -> "NO PEERS"
            !kernel.monitorReady -> kernel.state.badge
            else -> "READY"
        }
        if (rc == 0) {
            sendMessage = if (peers.isEmpty()) {
                "No people found. Keep the iPhone nearby and AirDrop open."
            } else {
                "${peers.size} person(s) found."
            }
        } else if (!sendMessage.startsWith("Helper session ended")) {
            sendMessage = "Scan did not complete; check the log."
        }
    }

    suspend fun pickFile(uri: Uri) {
        val picked = withContext(Dispatchers.IO) {
            try {
                val displayName = queryDisplayName(uri) ?: "airdrop-file"
                val cleanName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val out = File(context.cacheDir, "outgoing-${System.currentTimeMillis()}-$cleanName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                } ?: return@withContext null
                out
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { append("Could not read file: ${e.message}") }
                null
            }
        }
        pickedFile = picked
        sendMessage = when {
            picked == null -> "Could not open that file."
            else -> "Ready to send ${picked.name}."
        }
    }

    suspend fun sendPicked() {
        val file = pickedFile ?: return
        sendFile(file.absolutePath)
    }

    private suspend fun sendFile(path: String) {
        if (sending || sendCancelling || scanning || receiverRunning) return
        if (!requireMonitorKernel()) return
        val target = selectedPeer
        if (target == null) {
            sendMessage = "Select a person first."
            return
        }
        val cleanPath = path.replace('\n', ' ').replace('\r', ' ')
        val generation = ++sendGeneration
        sendCancelled = false
        sendProgressBytes = 0
        sendProgressTotal = 0
        sending = true
        sendCancelling = false
        status = "SENDING"
        sendMessage = "Sending ${File(cleanPath).name} to ${target.displayName()}..."
        notifications.updateSend(null, sendMessage, indeterminate = true)
        val rc = runSessionCommand(
            kind = "send",
            timeoutMs = SEND_TIMEOUT_MS,
            command = { id -> "send $id ${target.mac} $cleanPath" },
        ) { line -> handleSendProgress(line) }

        if (generation != sendGeneration) return
        if (sendCancelled) {
            stopSession()
            sending = false
            sendCancelling = false
            sendProgressBytes = 0
            sendProgressTotal = 0
            sendMessage = "Send cancelled."
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
            notifications.cancelSend()
            return
        }

        sending = false
        sendCancelling = false
        status = if (rc == 0) {
            if (kernel.monitorReady) "READY" else kernel.state.badge
        } else {
            "CHECK LOG"
        }
        if (rc == 0) {
            sendMessage = "Delivered ${File(cleanPath).name}."
            notifications.completeSend(sendMessage)
        } else {
            if (sendMessage.startsWith("Sending ")) {
                sendMessage = "Send did not complete; check the log."
            }
            notifications.failSend(sendMessage)
        }
    }

    private fun handleSendProgress(line: String) {
        val match = Regex("""progress\s+(\d+)/(\d+)""").find(line)
        if (match != null) {
            val sent = match.groupValues[1].toLongOrNull() ?: return
            val total = match.groupValues[2].toLongOrNull() ?: 0L
            sendProgressBytes = sent
            sendProgressTotal = total
            val percent = if (total > 0) {
                ((sent * 100) / total).toInt().coerceIn(0, 100)
            } else {
                null
            }
            sendMessage = if (percent != null) {
                "Sending $percent% · ${sent.readableBytes()} / ${total.readableBytes()}"
            } else {
                "Sending ${sent.readableBytes()}"
            }
            notifications.updateSend(percent, sendMessage, indeterminate = percent == null)
            return
        }
        if (line.startsWith("receiver answered") ||
            line.startsWith("receiver accepted") ||
            line.startsWith("DONE") ||
            line.startsWith("send failed")
        ) {
            sendMessage = line.trim()
            notifications.updateSend(null, sendMessage, indeterminate = true)
        }
    }

    /** Cancels an in-flight send and tears down the helper session. */
    fun cancelSend() {
        if (!sending || sendCancelling) return
        val generation = sendGeneration
        sendCancelled = true
        sendCancelling = true
        sendProgressBytes = 0
        sendProgressTotal = 0
        sendMessage = "Cancelling send..."
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        notifications.cancelSend()
        append("")
        append("Send cancelled; stopping the helper session...")
        scope.launch {
            if (sendGeneration != generation) return@launch
            stopSession()
            if (sendGeneration != generation) return@launch
            if (sendCancelled) {
                sending = false
                sendCancelling = false
                sendProgressBytes = 0
                sendProgressTotal = 0
                sendMessage = "Send cancelled."
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
                append("Send cancelled; helper session stopped.")
            }
        }
    }

    suspend fun startReceiving() {
        if (receiverRunning || scanning || sending || sendCancelling) return
        if (!requireMonitorKernel()) return
        // Claim receive mode before tearing the session down so Scan/Send stay
        // disabled while awdl0 changes hands.
        receiverRunning = true
        receiverStarting = true
        receiverStopping = false
        incomingRequest = null
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        status = "RECEIVING"
        receiveMessage = "Announcing on AWDL · waiting for a sender"
        notifications.updateReceive(null, receiveMessage, indeterminate = true)
        prepare()
        // awdl0 can only belong to one process: tear the scan/send session down
        // before the receive helper starts its own radio.
        stopSession()
        append("")
        append("Starting AirDrop receiver on port 8771...")
        val session = receiveMutex.withLock { startReceiveLocked() }
        if (session == null) {
            receiverStarting = false
            receiverRunning = false
            status = "CHECK LOG"
            receiveMessage = "Receiver could not start."
            notifications.cancelReceive()
            return
        }
        val ready = withTimeoutOrNull(RECEIVE_READY_TIMEOUT_MS) { session.ready.await() }
        if (ready != true) {
            append("Receiver did not become ready.")
            receiveMutex.withLock { stopReceiveLocked(session) }
            receiverStarting = false
            receiverRunning = false
            status = "CHECK LOG"
            receiveMessage = "Receiver did not become ready."
            notifications.cancelReceive()
            return
        }
        receiverStarting = false
        if (incomingRequest == null) status = "RECEIVING"
    }

    suspend fun stopReceiving() {
        if (!receiverRunning || receiverStarting || receiverStopping) return
        receiverStopping = true
        receiveMessage = "Stopping receiver..."
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        notifications.cancelReceive()
        receiveMutex.withLock { stopReceiveLocked() }
        ble.stop()
        incomingRequest = null
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        receiverStarting = false
        receiverRunning = false
        receiverStopping = false
        receiveMessage = "Receiver stopped"
        append("[receiver stopped]")
    }

    /** Sends accept/decline for the currently shown incoming request. */
    fun acceptIncoming() {
        val request = incomingRequest ?: return
        if (receiveDecisionPending) return
        if (!sendReceiveCommand("accept ${request.id}")) {
            receiveMessage = "Could not accept: receiver is not running."
            return
        }
        receiveDecisionPending = true
        receiveMessage = "Accepting request from ${request.sender.ifBlank { "the sender" }}..."
        status = "INCOMING"
        notifications.updateReceive(null, receiveMessage, indeterminate = true)
    }

    fun declineIncoming() {
        val request = incomingRequest ?: return
        if (receiveDecisionPending) return
        if (!sendReceiveCommand("decline ${request.id}")) {
            receiveMessage = "Could not decline: receiver is not running."
            return
        }
        receiveDecisionPending = true
        receiveMessage = "Declining request from ${request.sender.ifBlank { "the sender" }}..."
        status = "INCOMING"
        notifications.updateReceive(null, receiveMessage, indeterminate = true)
    }

    private fun requireMonitorKernel(): Boolean {
        if (kernel.monitorReady) return true
        status = kernel.state.badge
        sendMessage = "Enter AirDrop mode to use the radio."
        append("")
        append("Radio action blocked: ${kernel.detail}")
        append("Use Enter AirDrop mode; if the live switch fails, use Reboot into AirDrop mode.")
        return false
    }

    fun verifyKernelWithRoot() {
        if (verifyingKernel) return
        scope.launch {
            verifyingKernel = true
            status = "CHECKING KERNEL"
            var cmdline = ""
            var conMode: Int? = null
            val rc = execRoot(
                label = "kernel check",
                command = "printf 'CMDLINE='; cat /proc/cmdline; " +
                    "printf '\\nCON_MODE='; cat /sys/module/wlan/parameters/con_mode 2>/dev/null; echo",
            ) { line ->
                when {
                    line.startsWith("CMDLINE=") -> cmdline = line.removePrefix("CMDLINE=").trim()
                    line.startsWith("CON_MODE=") -> conMode = line.removePrefix("CON_MODE=").trim().toIntOrNull()
                }
            }
            verifyingKernel = false
            if (rc == 0) {
                val updated = kernel.withRootCheck(cmdline, conMode)
                kernel = updated
                status = if (updated.monitorReady) "READY" else updated.state.badge
                append("Kernel check: ${updated.state.badge} · ${updated.detail}")
            } else {
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
                append("Kernel check did not complete; keeping ${kernel.state.badge}.")
            }
        }
    }

    fun enterAirDropMode(reboot: Boolean = false) {
        if (modeSwitching || scanning || sending || receiverRunning) return
        scope.launch {
            modeSwitching = true
            status = if (reboot) "REBOOTING" else "ENTERING AIRDROP"
            val action = if (reboot) "reboot-enter" else "enter"
            append("")
            append(if (reboot) "Rebooting into AirDrop mode..." else "Entering AirDrop mode...")
            val rc = execRoot(
                label = "enter AirDrop mode",
                command = "$APP_TMP/owly-mode.sh $action",
            )
            modeSwitching = false
            if (reboot && rc == 0) return@launch
            if (rc == 0) {
                refreshKernelStatus()
                append("AirDrop mode: ${kernel.detail}")
            } else {
                status = "WIFI MODE"
                sendMessage = "Could not enter AirDrop mode. Use Reboot into AirDrop mode."
                append("Enter AirDrop mode failed; the live switch can wedge this firmware. Reboot into AirDrop mode instead.")
            }
        }
    }

    fun exitAirDropMode(reboot: Boolean = false) {
        if (modeSwitching || scanning || sending || receiverRunning) return
        scope.launch {
            modeSwitching = true
            status = if (reboot) "REBOOTING" else "LEAVING AIRDROP"
            val action = if (reboot) "reboot-exit" else "exit"
            append("")
            append(if (reboot) "Rebooting back to Wi-Fi..." else "Leaving AirDrop mode...")
            val rc = execRoot(
                label = "exit AirDrop mode",
                command = "$APP_TMP/owly-mode.sh $action",
            )
            modeSwitching = false
            if (reboot && rc == 0) return@launch
            if (rc == 0) {
                refreshKernelStatus()
                append("Wi-Fi mode: ${kernel.detail}")
            } else {
                status = "AIRDROP"
                sendMessage = "Could not restore Wi-Fi. Reboot to Wi-Fi mode."
                append("Exit AirDrop mode failed; reboot to restore Wi-Fi.")
            }
        }
    }

    private suspend fun refreshKernelStatus() {
        var cmdline = ""
        var conMode: Int? = null
        val rc = execRoot(
            label = "kernel check",
            command = "printf 'CMDLINE='; cat /proc/cmdline; " +
                "printf '\\nCON_MODE='; cat /sys/module/wlan/parameters/con_mode 2>/dev/null; echo",
        ) { line ->
            when {
                line.startsWith("CMDLINE=") -> cmdline = line.removePrefix("CMDLINE=").trim()
                line.startsWith("CON_MODE=") -> conMode = line.removePrefix("CON_MODE=").trim().toIntOrNull()
            }
        }
        if (rc == 0) {
            kernel = kernel.withRootCheck(cmdline, conMode)
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
        }
    }

    private fun handlePeerLine(line: String) {
        if (line.startsWith("PEER ")) {
            val mac = between(line, "mac=", " name=")
            if (mac.isBlank()) return
            val name = between(line, "name=", " devclass=")
            val devclass = between(line, "devclass=", " rssi=").toIntOrNull() ?: 0
            val rssi = line.substringAfter("rssi=").trim().toIntOrNull() ?: 0
            val existing = peers.toMutableList()
            existing.removeAll { it.mac == mac }
            existing.add(Peer(mac, name, devclass, rssi))
            peers = existing
            if (selectedMac == null) selectedMac = mac
        } else if (line.startsWith("PEER_GONE ")) {
            val mac = line.substringAfter("mac=", "").substringBefore(' ').trim()
            if (mac.isNotBlank()) {
                peers = peers.filterNot { it.mac == mac }
                if (selectedMac == mac) selectedMac = peers.firstOrNull()?.mac
            }
        } else if (line.startsWith("AWDL name:")) {
            val rest = line.removePrefix("AWDL name:").trim()
            val mac = rest.substringBefore("=").trim()
            val name = rest.substringAfter("=").trim()
            if (mac.isNotBlank() && name.isNotBlank()) {
                peers = peers.map { if (it.mac == mac) it.copy(name = name) else it }
            }
        }
    }

    private fun handleReceiveLine(session: ReceiveSession, line: String) {
        append(line)
        // Ignore output still draining from a receiver we already replaced.
        if (receiveSession !== session) return
        when {
            line.startsWith("RECEIVE_READY") -> {
                if (!session.ready.isCompleted) session.ready.complete(true)
                receiverStarting = false
                status = "RECEIVING"
                receiveMessage = "Receiver ready · waiting for a sender"
                notifications.updateReceive(null, receiveMessage, indeterminate = true)
            }
            line.startsWith("RECEIVE_REQUEST ") -> {
                val id = between(line, "id=", " peer=").toLongOrNull() ?: return
                val peer = between(line, "peer=", " files=")
                val fileCount = between(line, "files=", " total=").toIntOrNull() ?: 0
                val total = between(line, "total=", " sender=").toLongOrNull() ?: 0L
                val sender = line.substringAfter("sender=", "").trim()
                incomingRequest = IncomingRequest(id, peer, sender, fileCount, total)
                receiveDecisionPending = false
                receiveTransferring = false
                receiveProgressBytes = 0
                receiveProgressTotal = total
                status = "INCOMING"
                receiveMessage = "Incoming request from ${sender.ifBlank { peer }}"
                notifications.updateReceive(null, receiveMessage, indeterminate = true)
            }
            line.startsWith("RECEIVE_REQUEST_FILES ") -> {}
            line.startsWith("RECEIVE_ACCEPTED") -> {
                val id = between(line, "id=", " total=").toLongOrNull()
                    ?: line.substringAfter("id=", "").substringBefore(' ').toLongOrNull()
                val total = between(line, "total=", "").toLongOrNull() ?: 0L
                if (incomingRequest == null || id == null || incomingRequest?.id == id) {
                    incomingRequest = null
                }
                receiveDecisionPending = false
                receiveTransferring = true
                receiveProgressBytes = 0
                if (total > 0) receiveProgressTotal = total
                status = "RECEIVING"
                receiveMessage = "Accepted · receiving file..."
                notifications.updateReceive(null, receiveMessage, indeterminate = true)
            }
            line.startsWith("RECEIVE_DECLINED") -> {
                incomingRequest = null
                receiveDecisionPending = false
                receiveTransferring = false
                receiveProgressBytes = 0
                receiveProgressTotal = 0
                status = "RECEIVING"
                receiveMessage = "Request declined · waiting for a sender"
                notifications.updateReceive(null, receiveMessage, indeterminate = true)
            }
            line.startsWith("RECEIVE_WITHDRAWN") -> {
                incomingRequest = null
                receiveDecisionPending = false
                receiveTransferring = false
                receiveProgressBytes = 0
                receiveProgressTotal = 0
                status = "RECEIVING"
                receiveMessage = "Request withdrawn · waiting for a sender"
                notifications.updateReceive(null, receiveMessage, indeterminate = true)
            }
            line.startsWith("RECEIVE_PROGRESS") -> {
                val bytes = between(line, "bytes=", " total=").toLongOrNull() ?: 0L
                val total = between(line, "total=", " files=").toLongOrNull() ?: 0L
                receiveProgressBytes = bytes
                if (total > 0) receiveProgressTotal = total
                receiveTransferring = true
                status = "RECEIVING"
                val percent = if (receiveProgressTotal > 0) {
                    ((bytes * 100) / receiveProgressTotal).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                receiveMessage = if (percent != null) {
                    "Receiving $percent% · ${bytes.readableBytes()} / ${receiveProgressTotal.readableBytes()}"
                } else {
                    "Receiving ${bytes.readableBytes()}"
                }
                notifications.updateReceive(percent, receiveMessage, indeterminate = percent == null)
            }
            line.startsWith("RECEIVE_URLS") -> {
                receiveTransferring = true
                status = "RECEIVING"
                receiveMessage = "Preparing upload..."
                notifications.updateReceive(null, receiveMessage, indeterminate = true)
            }
            line.startsWith("RECEIVED ") -> {
                val path = line.substringAfter("path=").trim()
                if (path.isNotBlank()) {
                    receivedFiles.add(0, path)
                    receiveMessage = "Received ${File(path).name}"
                    status = "RECEIVED"
                }
                incomingRequest = null
                receiveDecisionPending = false
                receiveTransferring = false
                receiveProgressBytes = receiveProgressTotal
                notifications.completeReceive(receiveMessage)
            }
            line.startsWith("RECEIVE_FAILED") -> {
                val reason = line.substringAfter("reason=", line).trim()
                incomingRequest = null
                receiveDecisionPending = false
                receiveTransferring = false
                receiveProgressBytes = 0
                receiveProgressTotal = 0
                status = "RECEIVE ERROR"
                receiveMessage = "Receive failed: $reason"
                notifications.failReceive(receiveMessage)
            }
            line.startsWith("RECEIVE_ERROR") -> {
                incomingRequest = null
                receiveDecisionPending = false
                receiveTransferring = false
                receiveProgressBytes = 0
                receiveProgressTotal = 0
                status = "RECEIVE ERROR"
                receiveMessage = line
                notifications.failReceive(receiveMessage)
            }
        }
    }

    /** Starts the receive helper process; caller holds [receiveMutex]. */
    private suspend fun startReceiveLocked(): ReceiveSession? {
        val existing = receiveSession
        if (existing != null) stopReceiveLocked(existing)

        val command = helper(
            "receive '$RECEIVE_DIR' 8771 ${settings.channel} '${helperName()}'",
        )
        append("")
        append("$ starting receive helper")
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            append("ERROR: ${e.message}")
            append("Is KernelSU root granted?")
            status = "ROOT ERROR"
            return null
        }
        val session = ReceiveSession(
            process = process,
            writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8)),
        )
        receiveSession = session
        scope.launch(Dispatchers.IO) { readReceive(session) }
        return session
    }

    private suspend fun readReceive(session: ReceiveSession) {
        try {
            session.process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    withContext(Dispatchers.Main) { handleReceiveLine(session, line) }
                }
            }
        } catch (_: Exception) {
        }
        withContext(Dispatchers.Main) { handleReceiveEnded(session) }
    }

    private fun handleReceiveEnded(session: ReceiveSession) {
        if (!session.ready.isCompleted) session.ready.complete(false)
        if (receiveSession !== session) return
        receiveSession = null
        receiverRunning = false
        receiverStarting = false
        receiverStopping = false
        incomingRequest = null
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        if (status == "RECEIVING" || status == "INCOMING" || status == "RECEIVED") {
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
        }
        receiveMessage = "Receiver stopped"
        notifications.cancelReceive()
        append("[receiver stopped]")
    }

    /** Stops the receive helper, or only [expected] when given. */
    private suspend fun stopReceiveLocked(expected: ReceiveSession? = null) {
        val session = receiveSession
        if (session == null || (expected != null && session !== expected)) return
        receiveSession = null
        try {
            session.writer.write("quit")
            session.writer.newLine()
            session.writer.flush()
        } catch (_: Exception) {
        }
        val exited = withContext(Dispatchers.IO) {
            try {
                session.process.waitFor(1500, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            }
        }
        if (!exited) {
            session.process.destroy()
            val destroyed = withContext(Dispatchers.IO) {
                try {
                    session.process.waitFor(1500, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    false
                }
            }
            if (!destroyed) session.process.destroyForcibly()
            withContext(Dispatchers.IO) {
                try {
                    ProcessBuilder(
                        "su",
                        "-c",
                        "pkill -x owly-k20-helper 2>/dev/null; " +
                            "pkill -x owly-monitor-full 2>/dev/null; true",
                    )
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(3, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
            }
        }
        try {
            session.writer.close()
        } catch (_: Exception) {
        }
        if (!session.ready.isCompleted) session.ready.complete(false)
        append("[receive helper stopped]")
    }

    private fun sendReceiveCommand(command: String): Boolean {
        val session = receiveSession ?: return false
        return try {
            session.writer.write(command)
            session.writer.newLine()
            session.writer.flush()
            true
        } catch (e: Exception) {
            append("Receive command failed: ${e.message}")
            false
        }
    }

    /**
     * Runs one command against the long-lived helper session. The command
     * mutex serialises scan/send so they never compete for awdl0, and the
     * request id keeps a late reply from a timed-out command from completing
     * the next one.
     */
    private suspend fun runSessionCommand(
        kind: String,
        timeoutMs: Long,
        command: (String) -> String,
        onLine: (String) -> Unit = {},
    ): Int = commandMutex.withLock {
        if (kind == "send" && sendCancelled) return@withLock -1
        val session = sessionMutex.withLock { ensureSessionLocked() } ?: return@withLock -1
        if (kind == "send" && sendCancelled) {
            sessionMutex.withLock { stopSessionLocked(session) }
            return@withLock -1
        }
        val id = "$kind-${++requestCounter}"
        val pending = PendingRequest(id, kind, onLine)
        pendingRequest = pending
        val written = try {
            session.writer.write(command(id))
            session.writer.newLine()
            session.writer.flush()
            true
        } catch (e: Exception) {
            append("Helper write failed: ${e.message}")
            false
        }
        if (!written) {
            finishPending(pending, -1)
            sessionMutex.withLock { stopSessionLocked(session) }
            return@withLock -1
        }
        val rc = withTimeoutOrNull(timeoutMs) { pending.completion.await() }
        if (rc == null) {
            append("Helper timed out after ${timeoutMs / 1000} s waiting for $id.")
            finishPending(pending, -1)
            sessionMutex.withLock { stopSessionLocked(session) }
            -1
        } else {
            rc
        }
    }

    /** Starts the session helper if needed. Caller must hold [sessionMutex]. */
    private suspend fun ensureSessionLocked(): HelperSession? {
        val current = helperSession
        if (current != null && current.process.isAlive &&
            current.channel == settings.channel && current.name == helperName()
        ) {
            return current
        }
        if (current != null) stopSessionLocked()

        prepare()
        startBle(fresh = false)
        val channel = settings.channel
        val name = helperName()
        val command = "$APP_TMP/owly-k20-helper session $channel '$name'"
        append("")
        append("$ starting helper session")
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            append("ERROR: ${e.message}")
            append("Is KernelSU root granted?")
            status = "ROOT ERROR"
            return null
        }
        val session = HelperSession(
            process = process,
            writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8)),
            channel = channel,
            name = name,
        )
        helperSession = session
        scope.launch(Dispatchers.IO) { readSession(session) }
        val ready = withTimeoutOrNull(SESSION_READY_TIMEOUT_MS) { session.ready.await() }
        if (ready != true) {
            append("Helper session did not become ready.")
            if (helperSession === session) stopSessionLocked(session) else quitSession(session)
            return null
        }
        return session
    }

    private suspend fun readSession(session: HelperSession) {
        try {
            session.process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    withContext(Dispatchers.Main) { handleSessionLine(session, line) }
                }
            }
        } catch (_: Exception) {
        }
        withContext(Dispatchers.Main) { handleSessionEnded(session) }
    }

    private fun handleSessionLine(session: HelperSession, line: String) {
        append(line)
        // Ignore output still draining from a session we already replaced.
        if (helperSession !== session) return
        when {
            line.startsWith("SESSION_READY") -> {
                if (!session.ready.isCompleted) session.ready.complete(true)
            }
            line.startsWith("PEER ") || line.startsWith("PEER_GONE ") ||
                line.startsWith("AWDL name:") -> handlePeerLine(line)
        }

        val pending = pendingRequest ?: return
        pending.onLine(line)
        val tokens = line.trim().split(Regex("\\s+"), limit = 3)
        if (tokens.size < 2 || tokens[1] != pending.id) return
        when (tokens[0]) {
            "SCAN_DONE" -> if (pending.kind == "scan") finishPending(pending, 0)
            "SEND_OK" -> if (pending.kind == "send") finishPending(pending, 0)
            "SEND_ERR" -> if (pending.kind == "send") {
                val message = if (tokens.size >= 3) tokens[2].trim() else ""
                if (message.isNotBlank()) sendMessage = message
                finishPending(pending, -1)
            }
        }
    }

    private fun handleSessionEnded(session: HelperSession) {
        if (!session.ready.isCompleted) session.ready.complete(false)
        if (helperSession !== session) return
        helperSession = null
        append("[helper session ended]")
        pendingRequest?.let { pending ->
            if (!pending.completion.isCompleted) pending.completion.complete(-1)
            pendingRequest = null
            clearBusy(pending.kind)
            sendMessage = when (pending.kind) {
                "scan" -> "Helper session ended during scan."
                "send" -> "Helper session ended during send."
                else -> sendMessage
            }
        }
        scanning = false
        sending = false
        if (!receiverRunning) {
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
        }
    }

    private fun finishPending(pending: PendingRequest, rc: Int) {
        if (pendingRequest === pending) pendingRequest = null
        if (!pending.completion.isCompleted) pending.completion.complete(rc)
        clearBusy(pending.kind)
    }

    private fun clearBusy(kind: String) {
        when (kind) {
            "scan" -> scanning = false
            "send" -> sending = false
        }
    }

    /** Fails any in-flight command and shuts the session helper down. */
    private suspend fun stopSession() {
        pendingRequest?.let { pending ->
            if (!pending.completion.isCompleted) pending.completion.complete(-1)
            pendingRequest = null
            clearBusy(pending.kind)
        }
        sessionMutex.withLock { stopSessionLocked() }
    }

    /**
     * Stops [expected] (or the current session when null). Passing the session
     * that issued a command prevents a late timeout from killing a newer one.
     */
    private suspend fun stopSessionLocked(expected: HelperSession? = null) {
        val session = helperSession
        if (session == null || (expected != null && session !== expected)) return
        helperSession = null
        pendingRequest?.let { pending ->
            if (!pending.completion.isCompleted) pending.completion.complete(-1)
            pendingRequest = null
            clearBusy(pending.kind)
        }
        quitSession(session)
    }

    private suspend fun quitSession(session: HelperSession) {
        try {
            session.writer.write("quit")
            session.writer.newLine()
            session.writer.flush()
        } catch (_: Exception) {
        }
        val exited = withContext(Dispatchers.IO) {
            try {
                session.process.waitFor(1500, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            }
        }
        if (!exited) {
            session.process.destroy()
            val destroyed = withContext(Dispatchers.IO) {
                try {
                    session.process.waitFor(1500, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    false
                }
            }
            if (!destroyed) session.process.destroyForcibly()
            // A graceful quit lets the helper kill its monitor sniffer. After a
            // forced stop, remove the orphaned child by name.
            withContext(Dispatchers.IO) {
                try {
                    ProcessBuilder(
                        "su",
                        "-c",
                        "pkill -x owly-k20-helper 2>/dev/null; " +
                            "pkill -x owly-monitor-full 2>/dev/null; true",
                    )
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(3, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
            }
        }
        try {
            session.writer.close()
        } catch (_: Exception) {
        }
        if (!session.ready.isCompleted) session.ready.complete(false)
        append("[helper session stopped]")
    }

    private suspend fun startBle(fresh: Boolean) {
        withContext(Dispatchers.IO) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter != null && !adapter.isEnabled) {
                try {
                    ProcessBuilder("su", "-c", "cmd bluetooth_manager enable; sleep 1")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                    Thread.sleep(1200)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { append("Could not enable Bluetooth: ${e.message}") }
                }
            }
            val callback: (String) -> Unit = { line -> scope.launch { append(line) } }
            val ok = if (fresh) {
                ble.restart(settings.bleArm, true, callback)
            } else if (!ble.isRunning()) {
                ble.start(settings.bleArm, true, callback)
            } else {
                true
            }
            if (!ok) {
                withContext(Dispatchers.Main) {
                    append("BLE wake advertisement unavailable.")
                }
            }
        }
    }

    private suspend fun execRoot(
        label: String,
        command: String,
        requirePrepared: Boolean = true,
        onLine: (String) -> Unit = {},
    ): Int {
        if (requirePrepared) prepare()
        append("")
        append("$ $label")
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        withContext(Dispatchers.Main) {
                            append(line)
                            onLine(line)
                        }
                    }
                }
                val rc = process.waitFor()
                withContext(Dispatchers.Main) { append("[exit $rc]") }
                rc
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    append("ERROR: ${e.message}")
                    append("Is KernelSU root granted?")
                    status = "ROOT ERROR"
                }
                -1
            }
        }
    }

    private fun helper(arguments: String): String =
        "pkill -x owly-k20-helper 2>/dev/null; " +
            "pkill -x owly-monitor-full 2>/dev/null; sleep 1; " +
            "$APP_TMP/owly-k20-helper $arguments"

    private fun helperName(): String =
        settings.deviceName.replace("'", "").ifBlank { "Droidy K20" }

    private fun append(line: String) {
        if (line.isEmpty()) {
            logs.add("")
        } else {
            logs.addAll(line.split('\n'))
        }
        if (logs.size > 2500) logs.removeRange(0, logs.size - 2000)
    }

    private fun queryDisplayName(uri: Uri): String? {
        var result: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) result = cursor.getString(index)
        }
        return result
    }

    private fun between(text: String, start: String, end: String): String {
        val a = text.indexOf(start)
        if (a < 0) return ""
        val from = a + start.length
        val b = text.indexOf(end, from)
        return if (b < 0) text.substring(from).trim() else text.substring(from, b).trim()
    }

    fun close() {
        pendingRequest?.let { pending ->
            if (!pending.completion.isCompleted) pending.completion.complete(-1)
        }
        pendingRequest = null
        scanning = false
        sending = false
        sendCancelling = false
        receiverRunning = false
        receiverStarting = false
        receiverStopping = false
        incomingRequest = null
        receiveDecisionPending = false
        receiveTransferring = false
        helperSession?.let { session ->
            helperSession = null
            try {
                session.writer.write("quit")
                session.writer.newLine()
                session.writer.flush()
            } catch (_: Exception) {
            }
            try {
                session.process.destroy()
            } catch (_: Exception) {
            }
            try {
                session.writer.close()
            } catch (_: Exception) {
            }
        }
        receiveSession?.let { session ->
            receiveSession = null
            try {
                session.writer.write("quit")
                session.writer.newLine()
                session.writer.flush()
            } catch (_: Exception) {
            }
            try {
                session.process.destroy()
            } catch (_: Exception) {
            }
            try {
                session.writer.close()
            } catch (_: Exception) {
            }
        }
        notifications.cancelSend()
        notifications.cancelReceive()
        ble.stop()
        scope.cancel()
        try {
            ProcessBuilder(
                "su",
                "-c",
                "pkill -x owly-k20-helper 2>/dev/null; " +
                    "pkill -x owly-monitor-full 2>/dev/null; true",
            )
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
        }
    }
}

fun Peer.displayName(): String {
    if (name.isNotBlank() && !name.matches(Regex("[0-9a-fA-F-]{36}"))) return name
    return when (devclass) {
        2 -> "iPhone"
        1 -> "Computer"
        else -> "Nearby device"
    }
}

fun Peer.typeLabel(): String = when (devclass) {
    2 -> "iPhone · AWDL"
    1 -> "Computer · AWDL"
    else -> "AirDrop device"
}

fun File.readableSize(): String = length().readableBytes()

fun Long.readableBytes(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
