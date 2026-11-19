package io.moonbase.airdroid

import android.os.Build
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class AirdropState {
    READY,
    NOT_READY,
    UNKNOWN,
}

data class Peer(
    val mac: String,
    val name: String,
    val devclass: Int,
    val rssi: Int,
    val airdropState: AirdropState = AirdropState.UNKNOWN,
    /** Best supported social channel in the peer's advertised sequence. */
    val awdlChannel: Int = 0,
) {
    val airdropReady: Boolean
        get() = airdropState == AirdropState.READY
}

/** How the helper puts the upload body on the air. */
enum class UploadPolicy(val label: String, val env: String, val summary: String) {
    AUTO(
        "Auto",
        "auto",
        "Compress small files; stream anything over 512 MB.",
    ),
    STREAM(
        "Stream",
        "stream",
        "Store and stream every file. Starts at once, no compression.",
    ),
    SPOOL(
        "Compress",
        "spool",
        "Compress everything first. Smaller on the air, starts later.",
    ),
    TYPE_AWARE(
        "Type",
        "type",
        "Stream already-compressed files; compress the rest.",
    ),
    CHUNKED(
        "Chunked",
        "chunked",
        "Experimental: compress and stream without a length. A receiver may refuse.",
    ),
}

/** One file being added: the same per-file feedback a transfer gets. */
data class StagingJob(
    val id: Long,
    val name: String,
    val size: Long? = null,
    val started: Boolean = false,
    val progress: Float? = null,
    val done: Boolean = false,
)

data class AirDroidSettings(
    val channel: Int = 0,
    // V2 is the best wake arm for the K20/iPhone pair.
    val bleArm: Int = 1,
    val deviceName: String = "AirDroid K20",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val tintMode: TintMode = TintMode.CUSTOM,
    val pauseDiscoveryDuringTransfer: Boolean = true,
    val visibleDuringTransfer: Boolean = true,
    val updateReminders: Boolean = true,
    val autoChannelDwellSecs: Int = 12,
    val uploadPolicy: UploadPolicy = UploadPolicy.AUTO,
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
    val names: List<String> = emptyList(),
)

/** One in-flight scan/send command, matched by the helper's request id. */
private class PendingRequest(
    val id: String,
    val kind: String,
    val onLine: (String) -> Unit,
    val completion: CompletableDeferred<Int> = CompletableDeferred(),
)

private data class PendingReceiveProgress(
    val name: String,
    val bytes: Long,
    val total: Long,
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
enum class KernelSupport(val label: String) {
    SUPPORTED("AirDroid injection support verified"),
    MISSING("No AirDroid injection support"),
    UNKNOWN("Injection support not verified"),
}

data class KernelStatus(
    val release: String,
    val buildVersion: String,
    val state: MonitorState,
    val detail: String,
    val verifiedBy: String? = null,
    val support: KernelSupport = KernelSupport.UNKNOWN,
) {
    val monitorReady: Boolean
        get() = state == MonitorState.MONITOR

    fun withRootCheck(
        cmdline: String,
        conMode: Int?,
        support: KernelSupport = this.support,
    ): KernelStatus {
        val cmdlineMode = Regex("""(?:wlan\.)?con_mode=(\d+)""")
            .find(cmdline)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        val effectiveMode = conMode ?: cmdlineMode
        if (effectiveMode == 4) {
            return copy(
                state = MonitorState.MONITOR,
                detail = if (support == KernelSupport.MISSING) {
                    "Monitor mode is available, but AirDroid injection support was not found."
                } else {
                    "Ready for AirDrop discovery and transfers."
                },
                verifiedBy = "root",
                support = support,
            )
        }
        if (effectiveMode == 0) {
            return copy(
                state = MonitorState.STOCK,
                detail = "Normal Wi-Fi connectivity is available.",
                verifiedBy = "root",
                support = support,
            )
        }
        if (cmdline.isNotBlank() || conMode != null) {
            return copy(
                state = MonitorState.STOCK,
                detail = "Normal Wi-Fi connectivity is available.",
                verifiedBy = "root",
                support = support,
            )
        }
        return this
    }

    companion object {
        /**
         * Never infer capability from a release hash: upstream kernels and
         * rebuilt images get a new git suffix. The root check probes the
         * actual sysfs/kallsyms capability instead.
         */
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
            return KernelStatus(
                release = release,
                buildVersion = version,
                state = MonitorState.UNVERIFIED,
                detail = "Checking kernel capabilities...",
                support = KernelSupport.UNKNOWN,
            )
        }
    }
}

/**
 * Owns the root helper, BLE wake advertisement, and all long-running operations.
 * Compose observes the state properties directly; helper output is appended on
 * the main thread so the UI and logs stay consistent.
 */
class AirDroidController(private val context: Context) {
    companion object {
        private const val APP_TMP = "/data/local/tmp/owly-app"
        const val RECEIVE_DIR = "/sdcard/Download/AirDroid"
        const val AUTO_CHANNEL = 0
        private const val MIN_AUTO_DWELL_SECS = 6
        private const val MAX_AUTO_DWELL_SECS = 30
        // Prefer 5 GHz, but keep 6 as a last fallback for 2.4 GHz-only
        // networks. Blank/transient peers are ignored before locking.
        private val AUTO_CHANNELS = intArrayOf(149, 44, 6)
        private const val SESSION_READY_TIMEOUT_MS = 30_000L
        private const val RECEIVE_READY_TIMEOUT_MS = 30_000L
        private const val SEND_TIMEOUT_MS = 300_000L
        private const val PROGRESS_UI_INTERVAL_MS = 100L
        /** FOUND/LOST debounce: the radio hops, so both flap. */
        private const val AIRDROP_EVENT_DEBOUNCE_MS = 1_200L
        /** Expiry grace: AWDL drops announcements while the radio hops. */
        private const val AIRDROP_LOST_GRACE_MS = 15_000L
        private const val MONITOR_OWNER_PATH = "/sys/module/wlan/parameters/owly_owner_pid"
        private const val WATCHDOG_PID_PATH = "/data/local/tmp/owly-watchdog.pid"
        private const val MODE_SWITCH_TIMEOUT_MS = 60_000L
        private const val BOOT_ENTER_WINDOW_MS = 120_000L
        private const val OWNER_WRITE_TIMEOUT_MS = 5_000L
        private const val WATCHDOG_TIMEOUT_MS = 10_000L
        private const val TIMEOUT_EXIT_CODE = 124
        private val ASSETS = arrayOf(
            "owly-mode.sh",
            "owly-watchdog.sh",
            "owly-k20-helper",
        )
    }

    private val prefs = context.getSharedPreferences("airdroid", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prepareMutex = Mutex()
    private val commandMutex = Mutex()
    private val sessionMutex = Mutex()
    private val receiveMutex = Mutex()
    private val binDir = File(context.filesDir, "bin")
    private var prepared = false
    @Volatile
    private var bleStartFailed = false
    private var helperSession: HelperSession? = null
    private var receiveSession: ReceiveSession? = null
    private var pendingRequest: PendingRequest? = null
    private var requestCounter = 0
    private var sendCancelled = false
    private var sendGeneration = 0
    private val notifications = AirDroidNotifications(context)

    var settings by mutableStateOf(loadSettings())
        private set
    var pinnedPeers by mutableStateOf(
        prefs.getStringSet("pinnedPeers", emptySet())?.toSet() ?: emptySet(),
    )
        private set
    var activeChannel by mutableStateOf(if (settings.channel > 0) settings.channel else 44)
        private set
    var lockedChannel by mutableStateOf<Int?>(null)
        private set
    private var lastAutoChannel = prefs.getInt("lastAutoChannel", 149)
    var peers by mutableStateOf<List<Peer>>(emptyList())
        private set
    var selectedMac by mutableStateOf<String?>(null)
        private set
    /** AirDrop mDNS instance -> AWDL MAC. Used to hide a peer after its service is lost. */
    private val airdropInstances = mutableMapOf<String, String>()
    private val airdropSeenMacs = mutableSetOf<String>()
    private val airdropLostMacs = mutableSetOf<String>()
    private val airdropFoundJobs = mutableMapOf<String, Job>()
    private val airdropLostJobs = mutableMapOf<String, Job>()
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
    private var pendingSendUi: Pair<Long, Long>? = null
    private var sendUiJob: Job? = null
    private var lastSendUiAt = 0L
    private val pendingReceiveUi = mutableMapOf<String, PendingReceiveProgress>()
    private var pendingReceiveAggregate: Pair<Long, Long>? = null
    private var receiveUiJob: Job? = null
    private var lastReceiveUiAt = 0L
    var receiverRunning by mutableStateOf(false)
        private set
    var receiveEnabled by mutableStateOf(true)
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
    var modeAction by mutableStateOf<String?>(null)
        private set
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf<Int?>(null)
        private set
    var updateMessage by mutableStateOf("")
        private set
    var status by mutableStateOf("READY")
        private set
    var sendMessage by mutableStateOf("")
        private set
    var receiveMessage by mutableStateOf("Receiver is stopped")
        private set
    var pickedFile by mutableStateOf<File?>(null)
    /** Files being copied into the outgoing cache, one row each. */
    var stagingJobs by mutableStateOf<List<StagingJob>>(emptyList())
        private set
    val staging: Boolean get() = stagingJobs.isNotEmpty()
    /** Size reported by the picker for a staged path the app may not stat. */
    private val stagedSizes = mutableMapOf<String, Long>()
    private var pickedDisplayName: String? = null

    /** True while MainActivity is started; controls dialog vs notification. */
    var appForeground by mutableStateOf(false)
        private set

    /** Live per-file transfers (WAITING/OFFERED/ACCEPTED/TRANSFERRING). */
    var activeTransfers by mutableStateOf<List<TransferItem>>(emptyList())
        private set

    /** Terminal per-file transfers (COMPLETE/FAILED/CANCELLED). */
    var completedTransfers by mutableStateOf<List<TransferItem>>(emptyList())
        private set

    private var activeSendTransferId: String? = null
    private var sendTransferCounter = 0
    private var receiveFileCounter = 0
    private var incomingRequestNames: List<String> = emptyList()
    private var lastIncomingPeerName: String = ""
    private val transferRequestIds = mutableMapOf<String, String>()
    private val helperFileTransferIds = mutableMapOf<String, String>()
    private val helperSendFileIds = mutableMapOf<String, String>()
    private val originalTransferTotals = mutableMapOf<String, Long>()

    val logs = mutableStateListOf<String>()
    val receivedFiles = mutableStateListOf<String>()

    private val ble = BleAdvertiser(context)

    val selectedPeer: Peer?
        get() = selectedMac?.let { mac ->
            peers.firstOrNull { it.mac.equals(mac, ignoreCase = true) && it.airdropReady }
        }

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
        append("AirDroid K20")
        append("AirDrop over AWDL · Jetpack Compose · Material 3")
        append("Channel ${settings.channel} · BLE arm ${settings.bleArm}")
        append("Kernel ${kernel.release}")
        append(kernel.detail)
        scope.launch { verifyKernelWithRoot() }
        if (settings.updateReminders) checkForUpdates()
    }

    fun selectPeer(peer: Peer) {
        selectedMac = peer.mac
    }

    /** Selects a peer by runtime MAC for the ADB debug interface. */
    fun selectPeerByMac(mac: String): Boolean {
        val peer = peers.firstOrNull { it.mac.equals(mac, ignoreCase = true) } ?: return false
        selectPeer(peer)
        return true
    }

    fun isPinned(mac: String): Boolean = pinnedPeers.contains(mac.lowercase())

    fun togglePinned(mac: String) {
        val key = mac.lowercase()
        pinnedPeers = if (pinnedPeers.contains(key)) {
            pinnedPeers - key
        } else {
            pinnedPeers + key
        }
        prefs.edit().putStringSet("pinnedPeers", pinnedPeers).apply()
    }

    /**
     * MainActivity calls this from onStart/onStop. A pending incoming request
     * is shown as a dialog in the foreground and as a high-priority
     * notification in the background.
     */
    fun setForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        if (foreground) {
            notifications.cancelIncomingRequest()
        } else if (!receiveDecisionPending) {
            incomingRequest?.let { notifications.showIncomingRequest(it) }
        }
    }

    /** Cancels an active outgoing transfer (used by cards and notifications). */
    fun cancelTransfer(id: String) {
        val transfer = findTransfer(id) ?: return
        if (!transfer.canCancel) return
        if (activeSendTransferId == id) {
            cancelSend()
        } else if (transfer.direction == TransferDirection.RECEIVED) {
            // Abort only this upload's receiver connection; the receiver and
            // radio stay up for other transfers.
            val requestId = transferRequestIds[transfer.id]
                ?: transfer.id.removePrefix("rx-").substringBefore("-")
            if (requestId.isBlank()) return
            receiveMessage = "Stopping receive..."
            scope.launch {
                if (!sendReceiveCommand("abort-receive $requestId")) {
                    receiveMessage = "Could not stop the receive."
                }
            }
        } else {
            finishTransfer(id, TransferStatus.CANCELLED, error = "Cancelled")
        }
    }

    /** The receive batch key for grouping per-file rows in the UI. */
    fun batchKeyFor(id: String): String? = transferRequestIds[id]

    /** Deletes a completed received file and its history entry. */
    fun deleteReceivedFile(path: String) {
        val file = File(path)
        if (file.exists()) file.delete()
        receivedFiles.remove(path)
        val removed = completedTransfers.filter {
            it.direction == TransferDirection.RECEIVED && it.localPath == path
        }
        completedTransfers = completedTransfers.filterNot {
            it.direction == TransferDirection.RECEIVED && it.localPath == path
        }
        removed.forEach { notifications.cancelTransfer(it.id) }
        notifications.updateTransferSummary(visibleActiveTransferCount())
    }

    /** Applies Accept/Decline from a request notification action. */
    fun resolveIncomingFromNotification(requestId: Long, accept: Boolean) {
        val request = incomingRequest
        if (request == null || request.id != requestId) {
            notifications.cancelIncomingRequest()
            return
        }
        if (accept) acceptIncoming() else declineIncoming()
    }

    fun clearPickedFile() {
        pickedFile = null
        pickedDisplayName = null
        sendMessage = ""
    }

    fun clearLogs() {
        logs.clear()
        append("Log cleared")
    }

    /**
     * Writes the current in-memory log to a user-selected document. The
     * snapshot is taken before the IO hop so the UI list cannot change while
     * it is being serialized.
     */
    suspend fun saveLogs(uri: Uri): Boolean {
        val snapshot = logs.toList()
        val header = buildString {
            appendLine("# AirDroid ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("# Kernel ${kernel.release}")
            appendLine("# Saved ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine()
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.bufferedWriter().use { writer ->
                        writer.write(header)
                        snapshot.forEach {
                            writer.write(it)
                            writer.newLine()
                        }
                    }
                } != null
            }.getOrDefault(false)
        }
    }

    fun setUpdateReminders(enabled: Boolean) {
        if (settings.updateReminders == enabled) return
        settings = settings.copy(updateReminders = enabled)
        prefs.edit().putBoolean("updateReminders", enabled).apply()
        append("Update reminders ${if (enabled) "enabled" else "disabled"}.")
        if (enabled) checkForUpdates(force = true)
    }

    fun setAutoChannelDwellSecs(seconds: Int) {
        val clean = seconds.coerceIn(MIN_AUTO_DWELL_SECS, MAX_AUTO_DWELL_SECS)
        if (settings.autoChannelDwellSecs == clean) return
        settings = settings.copy(autoChannelDwellSecs = clean)
        prefs.edit().putInt("autoChannelDwellSecs", clean).apply()
        append("Auto channel dwell set to ${clean}s.")
        sendMessage = if (scanning) {
            "Auto channel dwell is now ${clean}s for the next channel."
        } else {
            "Auto channel dwell is now ${clean}s."
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        if (updateState is UpdateState.Checking) return
        if (!force && updateState is UpdateState.UpToDate) return
        updateState = UpdateState.Checking
        updateMessage = "Checking GitHub..."
        scope.launch {
            val result = AppUpdates.check(BuildConfig.VERSION_NAME)
            updateState = result
            updateMessage = when (result) {
                is UpdateState.UpToDate -> "Latest GitHub release is v${result.latestVersion}."
                is UpdateState.Available -> "v${result.release.version} is available."
                is UpdateState.Failed -> result.message
                else -> ""
            }
        }
    }

    fun downloadUpdate() {
        val release = (updateState as? UpdateState.Available)?.release ?: return
        if (release.apkUrl == null || updateDownloading) return
        updateDownloading = true
        updateProgress = null
        updateMessage = "Downloading ${release.version}..."
        scope.launch {
            try {
                val apk = AppUpdates.download(context, release) { percent ->
                    withContext(Dispatchers.Main) {
                        updateProgress = percent
                        updateMessage = if (percent == null) {
                            "Downloading ${release.version}..."
                        } else {
                            "Downloading ${release.version}... $percent%"
                        }
                    }
                }
                updateMessage = "Opening installer..."
                updateMessage = AppUpdates.install(context, apk)
                    ?: "Installer opened. Follow the Android prompts to update."
            } catch (e: Exception) {
                updateMessage = "Update failed: ${e.message}"
            } finally {
                updateDownloading = false
                updateProgress = null
            }
        }
    }

    fun saveAppearance(themeMode: ThemeMode, tintMode: TintMode) {
        settings = settings.copy(themeMode = themeMode, tintMode = tintMode)
        prefs.edit()
            .putString("themeMode", themeMode.name)
            .putString("tintMode", tintMode.name)
            .apply()
    }

    fun saveSettings(
        channel: Int,
        bleArm: Int,
        deviceName: String,
        themeMode: ThemeMode = settings.themeMode,
        tintMode: TintMode = settings.tintMode,
        pauseDiscoveryDuringTransfer: Boolean = settings.pauseDiscoveryDuringTransfer,
        visibleDuringTransfer: Boolean = settings.visibleDuringTransfer,
        uploadPolicy: UploadPolicy = settings.uploadPolicy,
    ) {
        val previous = settings
        val cleanName = deviceName.trim().ifBlank { "AirDroid K20" }
        val effectiveChannel = channel
        settings = settings.copy(
            channel = effectiveChannel,
            bleArm = bleArm,
            deviceName = cleanName,
            themeMode = themeMode,
            tintMode = tintMode,
            pauseDiscoveryDuringTransfer = pauseDiscoveryDuringTransfer,
            visibleDuringTransfer = visibleDuringTransfer,
        )
        // Auto means "whatever channel the live scan locked". Saving an
        // unrelated toggle must not move activeChannel away from the helper's
        // actual channel, or the next send fails the locked-channel check.
        if (effectiveChannel != previous.channel) {
            activeChannel = if (effectiveChannel > 0) effectiveChannel else 44
        }
        settings = settings.copy(uploadPolicy = uploadPolicy)
        prefs.edit()
            .putInt("channel", effectiveChannel)
            .putInt("bleArm", bleArm)
            .putString("deviceName", cleanName)
            .putString("themeMode", themeMode.name)
            .putString("tintMode", tintMode.name)
            .putBoolean("pauseDiscoveryDuringTransfer", pauseDiscoveryDuringTransfer)
            .putBoolean("visibleDuringTransfer", visibleDuringTransfer)
            .putString("uploadPolicy", uploadPolicy.name)
            .apply()
        val channelLabel = if (effectiveChannel == AUTO_CHANNEL) "Auto" else effectiveChannel.toString()
        sendMessage = "Settings saved · channel $channelLabel"
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        append(
            "Settings saved: channel=$channelLabel bleArm=$bleArm name=$cleanName " +
                "theme=${themeMode.name} tint=${tintMode.name} " +
                "pauseDiscovery=$pauseDiscoveryDuringTransfer visibleDuringTransfer=$visibleDuringTransfer",
        )
        scope.launch {
            writeSessionCommand(
                "set-transfer-policy pause_discovery=${if (pauseDiscoveryDuringTransfer) 1 else 0} " +
                    "visible_during_transfer=${if (visibleDuringTransfer) 1 else 0}",
            )
        }

        // Channel and identity are fixed when the session helper starts awdl0.
        // Drop the live session so the next scan/send recreates it with the new
        // values; a BLE-arm change only needs the advertisement restarted.
        val identityChanged = effectiveChannel != previous.channel ||
            cleanName != previous.deviceName || uploadPolicy != previous.uploadPolicy
        if (identityChanged) {
            scanning = false
            scope.launch {
                stopSession()
                append("Live session dropped; the next operation uses channel $effectiveChannel as $cleanName.")
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
                // Changing the channel/name also drops discovery. Start it again so a
                // later send cannot create a session on a channel that was never scanned.
                if (kernel.monitorReady && !sending && !sendCancelling && !modeSwitching) {
                    scan()
                }
            }
        }
        if (bleArm != previous.bleArm && ble.isRunning()) {
            scope.launch {
                if (!startBle(fresh = true)) {
                    status = "BLUETOOTH OFF"
                    sendMessage = "Could not restart BLE with the new wake arm."
                }
            }
        }
    }

    private fun loadSettings(): AirDroidSettings = AirDroidSettings(
        channel = prefs.getInt("channel", AUTO_CHANNEL),
        bleArm = prefs.getInt("bleArm", 1),
        deviceName = prefs.getString("deviceName", "AirDroid K20") ?: "AirDroid K20",
        themeMode = prefs.getString("themeMode", ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        tintMode = prefs.getString("tintMode", TintMode.CUSTOM.name)
            ?.let { runCatching { TintMode.valueOf(it) }.getOrNull() }
            ?: TintMode.CUSTOM,
        pauseDiscoveryDuringTransfer = prefs.getBoolean("pauseDiscoveryDuringTransfer", true),
        visibleDuringTransfer = prefs.getBoolean("visibleDuringTransfer", true),
        updateReminders = prefs.getBoolean("updateReminders", true),
        autoChannelDwellSecs = prefs.getInt("autoChannelDwellSecs", 12)
            .coerceIn(MIN_AUTO_DWELL_SECS, MAX_AUTO_DWELL_SECS),
        uploadPolicy = prefs.getString("uploadPolicy", UploadPolicy.AUTO.name)
            ?.let { runCatching { UploadPolicy.valueOf(it) }.getOrNull() }
            ?: UploadPolicy.AUTO,
    )

    suspend fun prepare() {
        if (prepared) return
        prepareMutex.withLock {
            if (prepared) return
            // The assets are copied first, with no root involved: `su` may not
            // be granted yet at boot or after a reinstall, and failing the
            // root pkill must not leave the previous install's helper staged.
            val assetsCopied = withContext(Dispatchers.IO) {
                try {
                    binDir.mkdirs()
                    for (name in ASSETS) {
                        val out = File(binDir, name)
                        context.assets.open(name).use { input ->
                            FileOutputStream(out).use { output -> input.copyTo(output) }
                        }
                    }
                    true
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        append("Asset install failed: ${e.message}")
                        status = "ROOT ERROR"
                    }
                    false
                }
            }
            if (!assetsCopied) return
            withContext(Dispatchers.IO) {
                runCatching {
                    ProcessBuilder(
                        "su",
                        "-c",
                        "pkill -x owly-k20-helper 2>/dev/null; " +
                            "pkill -x owly-monitor-full 2>/dev/null; sleep 1; true",
                    )
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                }
            }
            val rc = execRoot(
                label = "install root tools",
                // Keep only what the APK ships, plus the helper's own state,
                // so a phone that once carried investigation binaries does not
                // silently depend on them.
                command = "mkdir -p $APP_TMP; " +
                    "find $APP_TMP -mindepth 1 -maxdepth 1 " +
                    "! -name state ! -name owly-mode.sh ! -name owly-watchdog.sh " +
                    "! -name owly-k20-helper -exec rm -rf {} +; " +
                    "cp '${binDir.absolutePath}'/* $APP_TMP/ || exit 1; " +
                    "chmod 755 $APP_TMP/*; ls -l $APP_TMP",
                requirePrepared = false,
            )
            prepared = rc == 0
        }
    }

    suspend fun scan() {
        if (sending || sendCancelling) return
        if (!requireMonitorKernel()) return
        if (scanning) {
            append("Live scan is already running.")
            return
        }
        scanning = true
        status = "SCANNING"
        lockedChannel = null
        peers = emptyList()
        selectedMac = null
        clearAirdropServices()
        append("")
        runCatching { AirDroidService.start(context) }
        if (settings.channel == AUTO_CHANNEL) {
            scanAuto()
            return
        }
        activeChannel = settings.channel
        lockedChannel = activeChannel
        append("Starting live AirDrop scan on channel $activeChannel...")
        val session = sessionMutex.withLock { ensureSessionLocked() }
        if (session == null) {
            scanning = false
            status = if (bleStartFailed) "BLUETOOTH OFF" else "CHECK LOG"
            sendMessage = if (bleStartFailed) {
                "Turn on Bluetooth to use AirDrop discovery."
            } else {
                "Live scan could not start; check the log."
            }
            runCatching { AirDroidService.stop(context) }
            return
        }
        status = "SCANNING"
        sendMessage = "Live scan running. Nearby peers update automatically."
        append("Live scan running; peer updates are continuous.")
    }

    private fun autoChannelDwellMs(): Long =
        settings.autoChannelDwellSecs.coerceIn(MIN_AUTO_DWELL_SECS, MAX_AUTO_DWELL_SECS) * 1000L

    private suspend fun scanAuto() {
        // Owly exclusive mode gets very few iPhone slots on 2.4 GHz channel 6.
        // Prefer 5 GHz (44/149) and only fall back to 6 if no 5 GHz peer appears.
        val preferred = if (lastAutoChannel == 6) 44 else lastAutoChannel
        val channels = listOf(preferred) + AUTO_CHANNELS.filter { it != preferred }
        val order = channels.joinToString(" → ")
        sendMessage = "Auto scan: trying $order..."
        append("Auto channel scan: $order")
        while (scanning) {
            for (channel in channels) {
                if (!scanning) break
                val dwellMs = autoChannelDwellMs()
                activeChannel = channel
                stopSession()
                val session = sessionMutex.withLock { ensureSessionLocked() }
                if (session == null) {
                    scanning = false
                    status = if (bleStartFailed) "BLUETOOTH OFF" else "CHECK LOG"
                    sendMessage = if (bleStartFailed) {
                        "Turn on Bluetooth to use AirDrop discovery."
                    } else {
                        "Live scan could not start; check the log."
                    }
                    runCatching { AirDroidService.stop(context) }
                    return
                }
                if (!scanning) {
                    stopSession()
                    runCatching { AirDroidService.stop(context) }
                    return
                }
                append("Auto scan on channel $channel (${dwellMs / 1000}s dwell)")
                val started = System.currentTimeMillis()
                // A blank raw peer is often a Continuity device, not an
                // AirDrop sender. A named peer is enough for /Discover.
                while (scanning && peers.none { it.airdropReady } &&
                    System.currentTimeMillis() - started < dwellMs
                ) {
                    delay(250)
                }
                val candidates = peers.filter { it.airdropReady }
                if (candidates.isNotEmpty()) {
                    val candidate = candidates.first()
                    // The iPhone can be visible on 6 while its advertised
                    // sequence spends most slots on 149. A fixed monitor must
                    // move to that dominant channel; otherwise TCP stalls at
                    // the few slots where the phone visits 6.
                    val target = candidate.awdlChannel
                    if (target in AUTO_CHANNELS && target != channel) {
                        append(
                            "Peer advertises channel $target while scanning $channel; matching it.",
                        )
                        activeChannel = target
                        stopSession()
                        val switched = sessionMutex.withLock { ensureSessionLocked() }
                        if (switched == null) {
                            scanning = false
                            status = if (bleStartFailed) "BLUETOOTH OFF" else "CHECK LOG"
                            sendMessage = if (bleStartFailed) {
                                "Turn on Bluetooth to use AirDrop discovery."
                            } else {
                                "Live scan could not start; check the log."
                            }
                            runCatching { AirDroidService.stop(context) }
                            return
                        }
                        val switchedAt = System.currentTimeMillis()
                        while (scanning &&
                            System.currentTimeMillis() - switchedAt < dwellMs &&
                            peers.none {
                                it.mac == candidate.mac && it.airdropReady
                            }
                        ) {
                            delay(250)
                        }
                        if (peers.any { it.mac == candidate.mac && it.airdropReady }) {
                            lastAutoChannel = target
                            prefs.edit().putInt("lastAutoChannel", target).apply()
                            status = "SCANNING"
                            lockedChannel = target
                            sendMessage = "Matched AirDrop channel $target."
                            append("Auto channel locked on $target (peer advertised $target).")
                            return
                        }
                        append(
                            "Peer did not appear on advertised channel $target; continuing.",
                        )
                        continue
                    }
                    delay(600)
                    if (peers.any { it.mac == candidate.mac && it.airdropReady }) {
                        lastAutoChannel = channel
                        prefs.edit().putInt("lastAutoChannel", channel).apply()
                        status = "SCANNING"
                        lockedChannel = channel
                        sendMessage = "${candidates.size} person(s) found on channel $channel."
                        append("Auto channel locked on $channel.")
                        return
                    }
                    append("Auto channel $channel peer was transient; continuing.")
                }
            }
        }
    }

    suspend fun stopScanning(stopService: Boolean = true) {
        if (!scanning || sending || sendCancelling) return
        append("Stopping live scan...")
        stopSession()
        scanning = false
        lockedChannel = null
        peers = emptyList()
        selectedMac = null
        clearAirdropServices()
        resetReceiverState()
        if (stopService) runCatching { AirDroidService.stop(context) }
        status = when {
            peers.isEmpty() -> "NO PEERS"
            !kernel.monitorReady -> kernel.state.badge
            else -> "READY"
        }
        sendMessage = "Scanning stopped."
        append("Live scan stopped.")
    }

    /**
     * Stops active transfers and then the radio session. Used by the
     * foreground-service Stop action: unlike stopScanning(), this must not
     * return early while a send is in flight.
     */
    suspend fun stopScanningAndTransfers(stopService: Boolean = true) {
        if (sending) {
            cancelSend()
            withTimeoutOrNull(3_000) {
                while (sending) delay(100)
                true
            }
        }
        activeTransfers.toList().forEach { transfer ->
            if (transfer.direction == TransferDirection.RECEIVED) {
                cancelTransfer(transfer.id)
            }
        }
        if (sending || sendCancelling) {
            // The helper did not acknowledge in time; close its session so a
            // foreground Stop can never leave the upload running.
            stopSession()
            sending = false
            sendCancelling = false
            sendProgressBytes = 0
            sendProgressTotal = 0
            sendMessage = "Stopped."
        }
        if (scanning) {
            stopScanning(stopService)
        } else {
            stopSession()
            resetReceiverState()
        }
        notifications.cancelAllTransfers()
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        append("Stop action: transfers and radio session stopped.")
    }

    /** Notification Stop: stop transfers and leave monitor/Transfer mode. */
    suspend fun stopEverythingAndRestoreWifi() {
        stopScanningAndTransfers(stopService = false)
        if (!kernel.monitorReady) {
            notifications.cancelAllTransfers()
            return
        }
        val rc = execRoot(
            label = "notification stop: exit Transfer mode",
            command = "$APP_TMP/owly-mode.sh exit",
            timeoutMs = MODE_SWITCH_TIMEOUT_MS,
        )
        if (rc == 0) {
            setMonitorOwner(0)
            refreshKernelStatus()
            sendMessage = "Stopped. Wi-Fi restored."
            append("Notification Stop: Transfer mode exited and Wi-Fi restored.")
        } else {
            sendMessage = "Transfers stopped, but Wi-Fi restore failed."
            append("Notification Stop: failed to restore Wi-Fi.")
        }
        notifications.cancelAllTransfers()
    }

    /**
     * A unique cache directory whose file can keep the real display name.
     * The AirDrop archive uses the basename, so putting the timestamp on a
     * directory instead of the file prevents outgoing-<timestamp>- prefixes
     * from reaching the receiver.
     */
    private fun outgoingStagingFile(name: String): File {
        val dir = File(context.cacheDir, "outgoing/${System.currentTimeMillis()}")
        dir.mkdirs()
        return File(dir, name)
    }

    /**
     * A picker result as a real filesystem path, when the provider exposes one.
     *
     * Nothing here is required for correctness: an unresolved URI falls back to
     * the cache copy. It only avoids copying files that the root helper can
     * already read, which is what makes adding a 1 GB file instant.
     */
    private fun resolveDirectPath(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return null
        return try {
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val parts = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
                    if (parts.size != 2) return null
                    val volume = if (parts[0].equals("primary", ignoreCase = true)) {
                        "emulated/0"
                    } else {
                        parts[0]
                    }
                    "/storage/$volume/${parts[1]}"
                }
                "com.android.providers.downloads.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri)
                    if (id.startsWith("raw:")) {
                        id.removePrefix("raw:")
                    } else {
                        queryMediaPath(MediaStore.Files.getContentUri("external"), id.substringAfterLast(':'))
                    }
                }
                "com.android.providers.media.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri)
                    val type = id.substringBefore(':')
                    val fileId = id.substringAfter(':')
                    val base = when (type) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> return null
                    }
                    queryMediaPath(base, fileId)
                }
                "media" -> queryMediaPath(
                    MediaStore.Files.getContentUri("external"),
                    uri.lastPathSegment ?: return null,
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryMediaColumn(base: Uri, id: String, column: String): String? = try {
        context.contentResolver.query(
            base,
            arrayOf(column),
            "_id=?",
            arrayOf(id),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The path behind a MediaStore id. `_data` is hidden from apps on scoped
     * storage, so rebuild it from `relative_path` + `display_name`, which the
     * provider still reports. The caller verifies it as root before using it.
     */
    private fun queryMediaPath(base: Uri, id: String): String? {
        queryMediaColumn(base, id, MediaStore.MediaColumns.DATA)
            ?.takeIf { it.startsWith("/") }
            ?.let { return it }
        if (Build.VERSION.SDK_INT < 29) return null
        val name = queryMediaColumn(base, id, MediaStore.MediaColumns.DISPLAY_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val relative = queryMediaColumn(base, id, MediaStore.MediaColumns.RELATIVE_PATH) ?: ""
        return if (relative.startsWith("/")) {
            relative.trimEnd('/') + "/" + name
        } else {
            "/storage/emulated/0/" + relative.trim('/').let { if (it.isEmpty()) "" else "$it/" } + name
        }
    }

    /**
     * Finds a public download by its display name as root, matching the size
     * the picker reported. MediaStore hides `_data` and `relative_path` from an
     * app without storage permissions, but the file itself is readable by the
     * helper, so the search runs through `su` and never guesses: a name match
     * with a different size is ignored.
     */
    private fun findPublicFileAsRoot(name: String, size: Long?): String? = try {
        val quoted = "'" + name.replace("'", "'\\''") + "'"
        val sizeCheck = if (size != null && size > 0) {
            " && [ \"\$(stat -c %s \"\$p\")\" = \"$size\" ]"
        } else {
            ""
        }
        val script = "for p in /storage/emulated/0/Download/$quoted " +
            "/storage/emulated/0/Documents/$quoted /storage/emulated/0/$quoted; do " +
            "[ -f \"\$p\" ]$sizeCheck && { echo \"\$p\"; exit 0; }; done; " +
            "find /storage/emulated/0 -maxdepth 5 -type f -name $quoted 2>/dev/null | " +
            "while read -r p; do [ \"\$(stat -c %s \"\$p\")\" = \"${size ?: -1}\" ] && " +
            "echo \"\$p\"; done | head -n 1"
        val process = ProcessBuilder("su", "-c", script)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readLine()?.trim()
        process.waitFor(5, TimeUnit.SECONDS)
        out?.takeIf { it.startsWith("/") }
    } catch (_: Exception) {
        null
    }

    fun stagedSize(path: String, fallback: Long): Long =
        stagedSizes[path]?.takeIf { it > 0 } ?: fallback

    /** Whether the root helper can open [path]; checked before skipping the copy. */
    private fun rootCanRead(path: String): Boolean = try {
        val quoted = "'" + path.replace("'", "'\\''") + "'"
        val process = ProcessBuilder("su", "-c", "test -r $quoted")
            .redirectErrorStream(true)
            .start()
        if (process.waitFor(3, TimeUnit.SECONDS)) {
            process.exitValue() == 0
        } else {
            process.destroyForcibly()
            false
        }
    } catch (_: Exception) {
        false
    }

    /** Copies a picker result into the outgoing cache and returns (file, name). */
    private suspend fun stageFile(
        uri: Uri,
        onProgress: (Float?) -> Unit = {},
    ): Pair<File, String>? = withContext(Dispatchers.IO) {
        try {
            val displayName = queryDisplayName(uri) ?: "airdrop-file"
            val cleanName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            Log.i("AirDroidSend", "stageFile display=$displayName clean=$cleanName uri=$uri")
            // Prefer the provider's own file when the helper can read it: no
            // copy, so a 1 GB pick is added in milliseconds instead of a
            // minute of writing.
            var direct = resolveDirectPath(uri)
            if (direct == null) {
                val authority = uri.authority.orEmpty()
                if (authority.contains("downloads.documents") || authority == "media") {
                    direct = findPublicFileAsRoot(cleanName, querySize(uri))
                }
            }
            if (direct != null) {
                // The app may not be allowed to stat the path, so existence and
                // readability are checked as root, the process that reads it.
                val usable = rootCanRead(direct)
                Log.i(
                    "AirDroidSend",
                    "stageFile candidate=$direct usable=$usable size=${querySize(uri)}",
                )
                if (usable) {
                    stagedSizes[direct] = querySize(uri) ?: 0L
                    return@withContext File(direct) to cleanName
                }
            } else {
                Log.i("AirDroidSend", "stageFile no path for uri=$uri")
            }
            Log.i("AirDroidSend", "stageFile copying uri=$uri")
            val out = outgoingStagingFile(cleanName)
            val total = querySize(uri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total != null && total > 0) {
                            onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } ?: return@withContext null
            out to cleanName
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { append("Could not read file: ${e.message}") }
            null
        }
    }

    suspend fun stageText(text: String): Pair<File, String>? {
        if (text.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val file = outgoingStagingFile("Clipboard.txt")
            file.writeText(text)
            file to "Clipboard.txt"
        }
    }

    suspend fun pickFile(uri: Uri) {
        var picked: Pair<File, String>? = null
        stageFiles(listOf(uri)) { picked = it }
        pickedFile = picked?.first
        pickedDisplayName = picked?.second
        sendMessage = when {
            picked == null -> "Could not open that file."
            else -> "Ready to send ${picked!!.second}."
        }
    }

    /**
     * Stages picker results one by one, reporting each as it goes. A 1 GB
     * pick is a full copy into the outgoing cache, so every file gets the
     * same per-file progress and done state a transfer row has.
     *
     * [onReady] runs as soon as a file is ready, so the caller can add it to
     * the list without waiting for the rest.
     */
    suspend fun stageFiles(
        uris: List<Uri>,
        onReady: suspend (Pair<File, String>) -> Unit,
    ): Int {
        if (uris.isEmpty()) return 0
        var ready = 0
        // Every pick gets a row at once, so the queue is visible before the
        // first copy starts.
        val jobs = uris.mapIndexed { index, uri ->
            StagingJob(
                id = System.nanoTime() + index,
                name = queryDisplayName(uri) ?: "file ${index + 1}",
                size = querySize(uri),
            )
        }
        stagingJobs = stagingJobs + jobs
        for ((index, uri) in uris.withIndex()) {
            val job = jobs[index]
            stagingJobs = stagingJobs.map {
                if (it.id == job.id) it.copy(started = true) else it
            }
            try {
                val result = stageFile(uri) { progress ->
                    stagingJobs = stagingJobs.map {
                        if (it.id == job.id) it.copy(progress = progress) else it
                    }
                }
                if (result != null) {
                    // Let the check show before the file moves into the list.
                    stagingJobs = stagingJobs.map {
                        if (it.id == job.id) it.copy(progress = 1f, done = true) else it
                    }
                    delay(350)
                    onReady(result)
                    ready++
                }
            } finally {
                stagingJobs = stagingJobs.filterNot { it.id == job.id }
            }
        }
        return ready
    }

    /** Sends a staged list as one AirDrop offer: one accept, one upload. */
    fun sendStaged(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        scope.launch { sendFiles(items) }
    }

    suspend fun sendPicked() {
        val file = pickedFile ?: return
        Log.i("AirDroidSend", "sendPicked file=${file.name} display=$pickedDisplayName")
        sendFile(file.absolutePath, pickedDisplayName)
    }

    fun sendPath(path: String) {
        scope.launch {
            pickedFile = File(path)
            sendFile(path, File(path).name)
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) {
            sendMessage = "Clipboard has no text."
            return
        }
        scope.launch {
            val file = outgoingStagingFile("Clipboard.txt")
            withContext(Dispatchers.IO) { file.writeText(text) }
            pickedFile = file
            sendFile(file.absolutePath, "Clipboard.txt")
        }
    }

    private suspend fun sendFile(path: String, displayName: String? = null) {
        sendFiles(listOf(path to (displayName ?: File(path).name)))
    }

    private suspend fun sendFiles(items: List<Pair<String, String>>) {
        if (sending || sendCancelling) return
        if (!requireMonitorKernel()) return
        val target = selectedPeer
        if (target == null) {
            val nearby = selectedMac?.let { mac ->
                peers.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
            }
            sendMessage = when {
                nearby != null -> "Peer is nearby but AirDrop is not ready."
                selectedMac != null -> "Peer is no longer nearby. Scan again."
                else -> "Select a person first."
            }
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
            return
        }
        if (lockedChannel == null || lockedChannel != activeChannel) {
            sendMessage = "Peer is not locked to this channel. Scan again."
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
            return
        }

        val cleanItems = items.map { (path, name) ->
            path.replace('\n', ' ').replace('\r', ' ') to name
        }
        val files = cleanItems.map { (path, _) -> File(path) }
        val total = files.sumOf { it.length().coerceAtLeast(0) }
        val singleName = cleanItems.firstOrNull()?.let { (_, name) ->
            if (name.isNotBlank()) name else files.first().name
        } ?: "file"
        val shownName = if (cleanItems.size == 1) singleName else "${cleanItems.size} files"

        // Owly starts a fresh BLE wake advert for every send. Reusing the one
        // from session startup is not the same wake and lets the iPhone stop
        // answering new /Ask connections after the first transfer.
        if (!startBle(fresh = true)) {
            status = "BLUETOOTH OFF"
            sendMessage = "Turn on Bluetooth to send with AirDrop."
            append("Send blocked: BLE wake advertisement is not ready.")
            return
        }
        Log.i(
            "AirDroidSend",
            "sendFiles count=${cleanItems.size} shown=$shownName files=${cleanItems.map { it.first }}",
        )
        val generation = ++sendGeneration
        sendCancelled = false
        sendProgressBytes = 0
        sendProgressTotal = 0
        sending = true
        sendCancelling = false
        status = "SENDING"
        sendMessage = if (cleanItems.size == 1) {
            "Sending $shownName to ${target.displayName()}..."
        } else {
            "Sending ${cleanItems.size} files to ${target.displayName()}..."
        }
        val transferId = "tx-${System.currentTimeMillis()}-${++sendTransferCounter}"
        val childFiles = cleanItems.mapIndexed { index, (_, name) ->
            TransferFile(
                id = "$transferId-$index",
                name = name.ifBlank { files[index].name },
                totalBytes = files[index].length().coerceAtLeast(0),
            )
        }
        val transfer = TransferItem(
            id = transferId,
            direction = TransferDirection.SENT,
            peerName = target.displayName(),
            fileName = shownName,
            totalBytes = total,
            transferredBytes = 0,
            status = TransferStatus.WAITING,
            localPath = cleanItems.singleOrNull()?.first,
            startedAt = System.currentTimeMillis(),
            files = childFiles,
        )
        activeSendTransferId = transfer.id
        originalTransferTotals[transfer.id] = total
        upsertTransfer(transfer)

        // JSON keeps paths with spaces and quotes intact. The helper treats
        // the whole list as one Payload::Files offer, so the iPhone prompts
        // once for the batch.
        val jsonPaths = org.json.JSONArray(cleanItems.map { it.first }).toString()
        val rc = runSessionCommand(
            kind = "send",
            timeoutMs = SEND_TIMEOUT_MS,
            requestId = transfer.id,
            command = { id -> "send-batch $id ${target.mac} $jsonPaths" },
        ) { line -> handleSendProgress(line) }

        if (generation != sendGeneration) return
        if (sendCancelled) {
            finishTransfer(transfer.id, TransferStatus.CANCELLED, error = "Cancelled")
            activeSendTransferId = null
            sending = false
            sendCancelling = false
            sendProgressBytes = 0
            sendProgressTotal = 0
            sendMessage = "Send cancelled."
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
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
            finishTransfer(transfer.id, TransferStatus.COMPLETE)
            sendMessage = if (cleanItems.size == 1) {
                "Delivered $shownName."
            } else {
                "Delivered ${cleanItems.size} files."
            }
        } else {
            if (sendMessage.startsWith("Sending ")) {
                sendMessage = "Send did not complete; check the log."
            }
            finishTransfer(transfer.id, TransferStatus.FAILED, error = sendMessage)
        }
        activeSendTransferId = null
    }

    private fun handleSendProgress(line: String) {
        val match = if (line.startsWith("progress ")) {
            Regex("""progress\s+(\d+)/(\d+)""").find(line)
        } else {
            null
        }
        if (match != null) {
            val sent = match.groupValues[1].toLongOrNull() ?: return
            val total = match.groupValues[2].toLongOrNull() ?: 0L
            scheduleSendProgress(sent, total)
            return
        }
        if (line.startsWith("receiver answered")) {
            sendMessage = line.trim()
            activeSendTransferId?.let { id ->
                updateTransfer(id) { item ->
                    if (item.status == TransferStatus.WAITING) {
                        item.copy(status = TransferStatus.OFFERED)
                    } else {
                        item
                    }
                }
            }
            return
        }
        if (line.startsWith("receiver accepted")) {
            sendMessage = line.trim()
            activeSendTransferId?.let { id ->
                updateTransfer(id) { item ->
                    if (item.status.isActive && item.status != TransferStatus.TRANSFERRING) {
                        item.copy(
                            status = TransferStatus.ACCEPTED,
                            uploadStartedAt = item.uploadStartedAt ?: System.currentTimeMillis(),
                        )
                    } else {
                        item
                    }
                }
            }
            return
        }
        if (line.startsWith("DONE")) {
            sendMessage = line.trim()
            return
        }
        if (line.startsWith("send failed")) {
            sendMessage = line.trim()
            activeSendTransferId?.let { id ->
                finishTransfer(id, TransferStatus.FAILED, error = line.trim())
            }
        }
    }

    private fun scheduleSendProgress(sent: Long, total: Long) {
        pendingSendUi = sent to total
        if (sendUiJob?.isActive == true) return
        val wait = (PROGRESS_UI_INTERVAL_MS -
            (System.currentTimeMillis() - lastSendUiAt)).coerceIn(0L, PROGRESS_UI_INTERVAL_MS)
        sendUiJob = scope.launch {
            if (wait > 0) delay(wait)
            val pending = pendingSendUi ?: return@launch
            pendingSendUi = null
            lastSendUiAt = System.currentTimeMillis()
            applySendProgress(pending.first, pending.second)
        }
    }

    private fun applySendProgress(sent: Long, total: Long) {
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
        activeSendTransferId?.let { id ->
            updateTransfer(id) { item ->
                if (item.status.isTerminal) {
                    item
                } else {
                    item.copy(
                        transferredBytes = sent,
                        totalBytes = if (total > 0) total else item.totalBytes,
                        status = if (item.status == TransferStatus.WAITING ||
                            item.status == TransferStatus.OFFERED
                        ) {
                            TransferStatus.TRANSFERRING
                        } else {
                            item.status
                        },
                        uploadStartedAt = item.uploadStartedAt ?: System.currentTimeMillis(),
                        files = progressChildren(item.files, sent, total),
                    )
                }
            }
        }
    }

    private fun clearSendProgressUi() {
        sendUiJob?.cancel()
        sendUiJob = null
        pendingSendUi = null
    }

    /** Cancels an in-flight send without tearing down discovery/receiver. */
    fun cancelSend() {
        if (!sending || sendCancelling) return
        val generation = sendGeneration
        val id = activeSendTransferId
        sendCancelled = true
        sendCancelling = true
        clearSendProgressUi()
        sendProgressBytes = 0
        sendProgressTotal = 0
        sendMessage = "Cancelling send..."
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        append("")
        append("Send cancelled; asking the helper to abort the transfer...")
        scope.launch {
            if (sendGeneration != generation) return@launch
            val sent = id != null && writeSessionCommand("cancel $id")
            if (!sent) {
                fallbackCancel(generation, id)
                return@launch
            }
            // The helper keeps the AWDL session and receiver alive; only the
            // send task is aborted. If it does not acknowledge, fall back to a
            // full session restart so discovery cannot stay stuck.
            val acknowledged = withTimeoutOrNull(3_000) {
                while (sending && sendGeneration == generation) delay(100)
                true
            } ?: false
            if (!acknowledged && sendGeneration == generation) {
                fallbackCancel(generation, id)
            }
        }
    }

    private suspend fun fallbackCancel(generation: Int, transferId: String?) {
        if (sendGeneration != generation) return
        stopSession()
        if (sendGeneration != generation) return
        transferId?.let { id ->
            finishTransfer(id, TransferStatus.CANCELLED, error = "Cancelled")
        }
        activeSendTransferId = null
        sending = false
        sendCancelling = false
        sendProgressBytes = 0
        sendProgressTotal = 0
        scanning = false
        resetReceiverState()
        peers = emptyList()
        selectedMac = null
        clearAirdropServices()
        sendMessage = "Send cancelled; restarting discovery."
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
        append("Send cancelled; helper did not acknowledge, restarting discovery.")
        scan()
    }

    /** Writes a control line to the live helper session, if any. */
    private suspend fun writeSessionCommand(command: String): Boolean {
        val session = sessionMutex.withLock { helperSession } ?: return false
        return withContext(Dispatchers.IO) {
            try {
                session.writer.write(command)
                session.writer.newLine()
                session.writer.flush()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun startReceiving() {
        if (sending || sendCancelling) return
        if (receiverRunning && receiveEnabled) return
        if (receiverRunning) {
            receiveEnabled = true
            receiverStopping = false
            receiveMessage = "Receiver resumed · waiting for a sender"
            status = if (scanning) "SCANNING" else "RECEIVING"
            append("[receiving resumed]")
            return
        }
        if (!requireMonitorKernel()) return
        receiverStarting = true
        receiverStopping = false
        receiveEnabled = true
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        status = "RECEIVING"
        receiveMessage = "Announcing on AWDL · waiting for a sender"
        val session = sessionMutex.withLock { ensureSessionLocked() }
        if (session == null) {
            receiverStarting = false
            status = "CHECK LOG"
            receiveMessage = "Receiver could not start."
            return
        }
        receiverRunning = true
        receiverStarting = false
        if (incomingRequest == null) status = "RECEIVING"
    }

    /**
     * Pauses receiving without stopping the shared helper session. AWDL
     * discovery and nearby peers stay up; new AirDrop offers are declined
     * until the receiver is resumed.
     */
    suspend fun stopReceiving() {
        if (!receiverRunning || receiverStarting || receiverStopping || !receiveEnabled) return
        receiverStopping = true
        receiveMessage = "Pausing receiver..."
        notifications.cancelIncomingRequest()

        incomingRequest?.let { request ->
            sendReceiveCommand("decline ${request.id}")
        }
        activeTransfers
            .filter { it.direction == TransferDirection.RECEIVED }
            .forEach { transfer ->
                val requestId = transferRequestIds[transfer.id]
                    ?: transfer.id.removePrefix("rx-").substringBefore("-")
                if (requestId.isNotBlank()) {
                    sendReceiveCommand("abort-receive $requestId")
                }
                finishTransfer(
                    transfer.id,
                    TransferStatus.CANCELLED,
                    error = "Receiving paused",
                )
            }

        receiveEnabled = false
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        receiverStarting = false
        receiverStopping = false
        receiveMessage = "Receiving paused · discovery continues"
        status = when {
            scanning -> "SCANNING"
            kernel.monitorReady -> "READY"
            else -> kernel.state.badge
        }
        clearReceiveProgressUi()
        removePendingIncomingOffers()
        notifications.cancelIncomingRequest()
        notifications.updateTransferSummary(visibleActiveTransferCount())
        append("[receiving paused; discovery continues]")
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
        notifications.cancelIncomingRequest()
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
        notifications.cancelIncomingRequest()
    }

    private fun requireMonitorKernel(): Boolean {
        if (kernel.monitorReady && kernel.support != KernelSupport.MISSING) return true
        if (kernel.monitorReady && kernel.support == KernelSupport.MISSING) {
            status = "KERNEL SUPPORT MISSING"
            sendMessage = "This kernel does not include AirDroid injection support."
            append("Radio action blocked: ${kernel.support.label}.")
            return false
        }
        status = kernel.state.badge
        sendMessage = "Enter Transfer mode to use the radio."
        append("")
        append("Radio action blocked: ${kernel.detail}")
        append("Use Enter Transfer mode; if the live switch fails, use Reboot into Transfer mode.")
        return false
    }

    private fun kernelCheckCommand(): String =
        "printf 'CMDLINE='; cat /proc/cmdline; " +
            "printf '\\nCON_MODE='; cat /sys/module/wlan/parameters/con_mode 2>/dev/null; " +
            "printf '\\nOWLY_OWNER='; [ -e $MONITOR_OWNER_PATH ] && echo 1 || echo 0; " +
            "printf 'OWLY_INJECT='; grep -qw wma_inject_frame_via_vdev /proc/kallsyms 2>/dev/null && echo 1 || echo 0; " +
            "echo"

    private fun supportFrom(
        hasOwner: Boolean,
        hasInject: Boolean,
        conMode: Int?,
        cmdline: String,
    ): KernelSupport = when {
        hasOwner || hasInject -> KernelSupport.SUPPORTED
        conMode != null || cmdline.isNotBlank() -> KernelSupport.MISSING
        else -> KernelSupport.UNKNOWN
    }

    fun verifyKernelWithRoot() {
        if (verifyingKernel) return
        scope.launch {
            verifyingKernel = true
            status = "CHECKING KERNEL"
            var cmdline = ""
            var conMode: Int? = null
            var hasOwner = false
            var hasInject = false
            val rc = execRoot(
                label = "kernel check",
                command = kernelCheckCommand(),
            ) { line ->
                when {
                    line.startsWith("CMDLINE=") -> cmdline = line.removePrefix("CMDLINE=").trim()
                    line.startsWith("CON_MODE=") -> conMode = line.removePrefix("CON_MODE=").trim().toIntOrNull()
                    line.startsWith("OWLY_OWNER=") -> hasOwner = line.removePrefix("OWLY_OWNER=").trim() == "1"
                    line.startsWith("OWLY_INJECT=") -> hasInject = line.removePrefix("OWLY_INJECT=").trim() == "1"
                }
            }
            verifyingKernel = false
            if (rc == 0) {
                val updated = kernel.withRootCheck(
                    cmdline,
                    conMode,
                    supportFrom(hasOwner, hasInject, conMode, cmdline),
                )
                kernel = updated
                status = if (updated.monitorReady) "READY" else updated.state.badge
                append("Kernel check: ${updated.state.badge} · ${updated.detail}")
                if (updated.state == MonitorState.MONITOR) {
                    if (!setMonitorOwner(AndroidProcess.myPid())) {
                        append("WARNING: root Wi-Fi watchdog did not start; only the kernel owner is armed.")
                    }
                }
                if (updated.monitorReady && !scanning && !sending && !receiverRunning) {
                    scan()
                }
            } else {
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
                append("Kernel check did not complete; keeping ${kernel.state.badge}.")
            }
        }
    }

    fun retryBluetooth() {
        if (modeSwitching) return
        scope.launch {
            status = "CHECKING BLUETOOTH"
            sendMessage = "Starting Bluetooth..."
            if (startBle(fresh = false)) {
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
                sendMessage = if (kernel.monitorReady) {
                    "Bluetooth ready. Starting scan..."
                } else {
                    "Bluetooth ready. Enter Transfer mode."
                }
                append("Bluetooth is ready.")
                if (kernel.monitorReady) scan()
            } else {
                status = "BLUETOOTH OFF"
                sendMessage = "Bluetooth is still off. Turn it on and retry."
            }
        }
    }

    fun dismissBluetoothWarning() {
        if (status == "BLUETOOTH OFF") {
            status = if (kernel.monitorReady) "READY" else kernel.state.badge
        }
    }

    fun enterTransferMode(reboot: Boolean = false, boot: Boolean = false) {
        Log.i(
            "AirDroidLog",
            "enterTransferMode(reboot=$reboot boot=$boot) modeSwitching=$modeSwitching " +
                "sending=$sending verifyingKernel=$verifyingKernel support=${kernel.support}",
        )
        if (modeSwitching || sending) return
        scope.launch {
            modeSwitching = true
            // A boot-enter can race the launch-time kernel check; let it settle
            // so it cannot start a scan in the middle of the switch.
            var waited = 0
            while (verifyingKernel && waited < 5_000) {
                delay(100)
                waited += 100
            }
            // A boot-enter that sat behind a slow `su` must not fire minutes
            // later when the app is opened: it was a one-shot request for that
            // boot only.
            val uptime = SystemClock.elapsedRealtime()
            if (boot && uptime > BOOT_ENTER_WINDOW_MS) {
                modeSwitching = false
                modeAction = null
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
                append(
                    "Boot Transfer mode skipped: ${uptime / 1000}s after boot " +
                        "(limit ${BOOT_ENTER_WINDOW_MS / 1000}s).",
                )
                sendMessage = "Start Transfer mode from Settings."
                return@launch
            }
            if (kernel.support == KernelSupport.MISSING) {
                modeSwitching = false
                modeAction = null
                status = "KERNEL SUPPORT MISSING"
                sendMessage = "This kernel does not include AirDroid injection support."
                append("Transfer mode blocked: ${kernel.support.label}.")
                return@launch
            }
            status = if (reboot) "REBOOTING" else "ENTERING TRANSFER"
            val action = if (reboot) "reboot-enter" else "enter"
            modeAction = action
            append("")
            append(if (reboot) "Rebooting into Transfer mode..." else "Entering Transfer mode...")
            if (!reboot) {
                // Install the assets first, then arm both watchdogs before the
                // switch. The script also arms the kernel owner before
                // con_mode=4; the root watchdog covers the Wi-Fi restore if the
                // switch is interrupted. Without the root watchdog the switch
                // must not start: a force-stop would leave monitor mode on.
                prepare()
                if (!startBle(fresh = false)) {
                    modeAction = null
                    modeSwitching = false
                    status = "BLUETOOTH OFF"
                    sendMessage = "Turn on Bluetooth to enter Transfer mode."
                    append("Transfer mode blocked: Bluetooth/BLE is not ready.")
                    return@launch
                }
                if (!setMonitorOwner(AndroidProcess.myPid())) {
                    setMonitorOwner(0)
                    modeAction = null
                    modeSwitching = false
                    status = "WIFI MODE"
                    sendMessage = "Transfer-mode watchdog is not armed; staying on Wi-Fi."
                    append("Kernel owner or root watchdog did not arm; not entering Transfer mode.")
                    return@launch
                }
            }
            val rc = execRoot(
                label = "enter Transfer mode",
                // The PID lets owly-mode.sh arm the kernel owner before it
                // writes con_mode=4 and self-restore if this process dies.
                command = if (reboot) {
                    "$APP_TMP/owly-mode.sh $action"
                } else {
                    "$APP_TMP/owly-mode.sh $action ${AndroidProcess.myPid()}"
                },
                timeoutMs = if (reboot) 0L else MODE_SWITCH_TIMEOUT_MS,
            )
            modeAction = null
            modeSwitching = false
            if (reboot && rc == 0) return@launch
            if (rc == 0) {
                if (!setMonitorOwner(AndroidProcess.myPid())) {
                    append("WARNING: root Wi-Fi watchdog did not start; only the kernel owner is armed.")
                }
                var conMode = readConMode()
                if (conMode != null && conMode != 4) {
                    // An in-flight kernel revert can drop monitor mode right
                    // after the switch; retry once instead of claiming success.
                    append("Monitor mode dropped to con_mode=$conMode after enter; re-entering once.")
                    val retryRc = execRoot(
                        label = "re-enter Transfer mode",
                        command = "$APP_TMP/owly-mode.sh enter ${AndroidProcess.myPid()}",
                        timeoutMs = MODE_SWITCH_TIMEOUT_MS,
                    )
                    if (retryRc == 0) {
                        setMonitorOwner(AndroidProcess.myPid())
                        conMode = readConMode()
                    }
                }
                if (conMode != null && conMode != 4) {
                    // Not monitor after the retry: restore Wi-Fi and report a
                    // failure instead of leaving the app in Transfer mode.
                    execRoot(
                        label = "restore Wi-Fi after failed enter",
                        command = "$APP_TMP/owly-mode.sh exit",
                        timeoutMs = MODE_SWITCH_TIMEOUT_MS,
                    )
                    setMonitorOwner(0)
                    refreshKernelStatus()
                    sendMessage = "Transfer mode did not stay active. Try again."
                    append("Monitor mode did not stay active (con_mode=$conMode); Wi-Fi restored.")
                } else {
                    if (conMode == null) {
                        append("Could not re-read con_mode; trusting the mode switch.")
                    }
                    refreshKernelStatus()
                    append("Transfer mode: ${kernel.detail}")
                    receiveEnabled = true
                    scan()
                }
            } else {
                refreshKernelStatus()
                when {
                    rc == TIMEOUT_EXIT_CODE -> {
                        // The switch may still be running in the background, so
                        // leave both watchdogs armed instead of clearing them.
                        append("Enter Transfer mode timed out; keeping the watchdog armed.")
                        sendMessage = "Transfer mode switch timed out. Check the log."
                    }
                    kernel.monitorReady -> {
                        // The switch failed after monitor mode came up; keep the
                        // watchdogs armed so a force-stop still restores Wi-Fi.
                        setMonitorOwner(AndroidProcess.myPid())
                        sendMessage = "Monitor mode is up but the switch did not finish."
                        append("Monitor mode is still up; keeping the watchdog armed.")
                    }
                    else -> {
                        setMonitorOwner(0)
                        status = "WIFI MODE"
                        sendMessage = "Could not enter Transfer mode. Try again."
                        append("Enter Transfer mode failed; check the log and try again.")
                    }
                }
            }
        }
    }

    fun exitTransferMode(reboot: Boolean = false) {
        if (modeSwitching || sending) return
        scope.launch {
            modeSwitching = true
            status = if (reboot) "REBOOTING" else "LEAVING TRANSFER"
            val action = if (reboot) "reboot-exit" else "exit"
            modeAction = action
            append("")
            append(if (reboot) "Rebooting back to Wi-Fi..." else "Leaving Transfer mode...")
            if (reboot) {
                // reboot-exit reboots; the kernel state resets with the device.
                setMonitorOwner(0)
            } else {
                stopSession()
                scanning = false
                lockedChannel = null
                peers = emptyList()
                selectedMac = null
                clearAirdropServices()
                resetReceiverState()
            }
            val rc = execRoot(
                label = "exit Transfer mode",
                command = "$APP_TMP/owly-mode.sh $action",
                timeoutMs = if (reboot) 0L else MODE_SWITCH_TIMEOUT_MS,
            )
            if (!reboot && rc == 0) {
                // Keep the watchdogs armed until exit succeeds. The watchdog
                // skips the Wi-Fi cycle when con_mode is already 0, so a
                // force-stop here cannot leave monitor mode unguarded.
                setMonitorOwner(0)
            }
            modeAction = null
            modeSwitching = false
            if (reboot && rc == 0) return@launch
            if (rc == 0) {
                refreshKernelStatus()
                append("Wi-Fi mode: ${kernel.detail}")
                // Leaving Transfer mode cancels any pending one-shot
                // boot-enter, so the next reboot starts with normal Wi-Fi.
                runCatching {
                    withContext(Dispatchers.IO) {
                        ProcessBuilder("su", "-c", "rm -f /data/adb/owly-transfer-mode")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    }
                }
            } else {
                status = "TRANSFER"
                sendMessage = "Could not restore Wi-Fi. Reboot to Wi-Fi mode."
                append("Exit Transfer mode failed; reboot to restore Wi-Fi.")
            }
            if (!reboot) {
                // Stop the service after the switch: onDestroy restores Wi-Fi
                // when the controller is still in monitor mode, so stopping it
                // earlier would run a second owly-mode.sh exit.
                runCatching { AirDroidService.stop(context) }
            }
        }
    }

    private suspend fun refreshKernelStatus() {
        var cmdline = ""
        var conMode: Int? = null
        var hasOwner = false
        var hasInject = false
        val rc = execRoot(
            label = "kernel check",
            command = kernelCheckCommand(),
        ) { line ->
            when {
                line.startsWith("CMDLINE=") -> cmdline = line.removePrefix("CMDLINE=").trim()
                line.startsWith("CON_MODE=") -> conMode = line.removePrefix("CON_MODE=").trim().toIntOrNull()
                line.startsWith("OWLY_OWNER=") -> hasOwner = line.removePrefix("OWLY_OWNER=").trim() == "1"
                line.startsWith("OWLY_INJECT=") -> hasInject = line.removePrefix("OWLY_INJECT=").trim() == "1"
            }
        }
        if (rc == 0) {
            val updated = kernel.withRootCheck(
                cmdline,
                conMode,
                supportFrom(hasOwner, hasInject, conMode, cmdline),
            )
            kernel = updated
            status = if (updated.monitorReady) "READY" else updated.state.badge
        }
    }

    private fun handlePeerLine(line: String) {
        if (line.startsWith("PEER ")) {
            val mac = between(line, "mac=", " name=").lowercase()
            if (!validMac(mac)) return
            val name = between(line, "name=", " devclass=").trim().take(80)
            val devclass = between(line, "devclass=", " rssi=").toIntOrNull()
                ?.coerceIn(0, 255)
                ?: 0
            val rssi = (
                between(line, "rssi=", " channel=").toIntOrNull()
                    ?: line.substringAfter("rssi=").substringBefore(' ').trim().toIntOrNull()
                )?.coerceIn(-120, 0) ?: 0
            val rawChannel = line.substringAfterLast("channel=", "")
                .substringBefore(' ')
                .trim()
                .toIntOrNull()
                ?: 0
            val awdlChannel = if (rawChannel in setOf(6, 44, 149)) rawChannel else 0
            val seen = airdropSeenMacs.any { it.equals(mac, ignoreCase = true) }
            val lost = airdropLostMacs.any { it.equals(mac, ignoreCase = true) }
            val existing = peers.toMutableList()
            val currentState = existing.firstOrNull {
                it.mac.equals(mac, ignoreCase = true)
            }?.airdropState
            // Readiness comes from the AirDrop service only. A name is not a
            // readiness signal, and an update without a fresh FOUND/LOST keeps
            // whatever the last service event decided.
            val state = when {
                lost -> AirdropState.NOT_READY
                seen -> AirdropState.READY
                else -> currentState ?: AirdropState.UNKNOWN
            }
            val wasReady = existing.any {
                it.mac.equals(mac, ignoreCase = true) && it.airdropReady
            }
            val previousName = existing.firstOrNull {
                it.mac.equals(mac, ignoreCase = true)
            }?.name
            // Replace in place: rebuilding the list on every rssi update moved
            // the card to the end, which the list showed as peers swapping.
            val updated = Peer(
                mac,
                name,
                devclass,
                rssi,
                airdropState = state,
                awdlChannel = awdlChannel,
            )
            val at = existing.indexOfFirst { it.mac.equals(mac, ignoreCase = true) }
            if (at >= 0) {
                existing[at] = updated
            } else {
                existing.add(updated)
            }
            peers = existing
            if (previousName != name && name.isNotBlank()) {
                append("Peer name: $name")
            }
            if (!wasReady && state == AirdropState.READY) {
                append(
                    "Peer ready: ${name.ifBlank { "unnamed" }} " +
                        "channel=$awdlChannel rssi=$rssi",
                )
            }
            if (selectedMac == null && state == AirdropState.READY) selectedMac = mac
        } else if (line.startsWith("PEER_GONE ")) {
            val mac = line.substringAfter("mac=", "").substringBefore(' ').trim().lowercase()
            if (validMac(mac)) {
                val goneName = peers.firstOrNull {
                    it.mac.equals(mac, ignoreCase = true)
                }?.displayName()
                peers = peers.filterNot { it.mac.equals(mac, ignoreCase = true) }
                clearAirdropForMac(mac)
                append("Peer left: ${goneName ?: "unnamed"}")
                if (selectedMac.equals(mac, ignoreCase = true)) {
                    selectedMac = peers.firstOrNull { it.airdropReady }?.mac
                }
            }
        } else if (line.startsWith("AWDL name:")) {
            val rest = line.removePrefix("AWDL name:").trim()
            val mac = rest.substringBefore("=").trim().lowercase()
            val name = rest.substringAfter("=").trim().take(80)
            if (validMac(mac) && name.isNotBlank()) {
                // A learned name is not a readiness signal: keep the state the
                // service events decided.
                peers = peers.map {
                    if (it.mac.equals(mac, ignoreCase = true)) {
                        it.copy(name = name)
                    } else {
                        it
                    }
                }
            }
        }
    }

    /**
     * mDNS AirDrop services are the readiness signal. A peer can remain visible as a raw
     * AWDL peer (for Continuity) after AirDrop is turned off, so PEER/PEER_GONE alone
     * cannot drive the nearby list.
     */
    private fun handleAirdropServiceLine(line: String) {
        if (line.startsWith("FOUND ")) {
            val instance = between(line, "instance=\"", "\"")
            val addr = between(line, "addr=", " port=")
            val mac = macFromLinkLocal(addr) ?: return
            val firstInstance =
                instance.isNotBlank() && airdropInstances[instance] == null
            airdropSeenMacs.add(mac)
            airdropLostJobs.remove(mac)?.cancel()
            if (firstInstance) append("AirDrop service FOUND $mac")
            if (instance.isNotBlank()) airdropInstances[instance] = mac
            if (airdropLostMacs.any { it.equals(mac, ignoreCase = true) }) {
                // LOST and FOUND can arrive in quick succession when AirDrop is
                // turned off. Do not flash the peer back for one frame: only
                // restore it if FOUND survives the short debounce.
                airdropFoundJobs.remove(mac)?.cancel()
                airdropFoundJobs[mac] = scope.launch {
                    delay(AIRDROP_EVENT_DEBOUNCE_MS)
                    if (airdropLostMacs.any { it.equals(mac, ignoreCase = true) }) {
                        airdropLostMacs.removeAll { it.equals(mac, ignoreCase = true) }
                        peers = peers.map {
                            if (it.mac.equals(mac, ignoreCase = true)) {
                                it.copy(airdropState = AirdropState.READY)
                            } else {
                                it
                            }
                        }
                        if (selectedMac == null) selectedMac = mac
                    }
                    airdropFoundJobs.remove(mac)
                }
            } else {
                airdropLostMacs.removeAll { it.equals(mac, ignoreCase = true) }
                peers = peers.map {
                    if (it.mac.equals(mac, ignoreCase = true)) it.copy(airdropState = AirdropState.READY) else it
                }
                if (selectedMac == null) selectedMac = mac
            }
        } else if (line.startsWith("WITHDRAWN ") || line.startsWith("LOST ")) {
            // A goodbye means the receiver is leaving on purpose; a plain
            // expiry also happens while it is still there, because AWDL drops
            // announcements while the radio hops.
            val withdrawn = line.startsWith("WITHDRAWN ")
            val instance = between(line, "instance=\"", "\"")
            val mac = airdropInstances.remove(instance) ?: return
            val stillAdvertised = airdropInstances.values.any { it.equals(mac, ignoreCase = true) }
            if (!stillAdvertised) {
                append(
                    if (withdrawn) "AirDrop service withdrawn $mac" else "AirDrop service LOST $mac",
                )
                airdropFoundJobs.remove(mac)?.cancel()
                // Not advertised any more: a later PEER line must not turn the
                // peer ready through `seen` while the grace is running.
                airdropSeenMacs.removeAll { it.equals(mac, ignoreCase = true) }
                airdropLostJobs.remove(mac)?.cancel()
                airdropLostJobs[mac] = scope.launch {
                    delay(
                        if (withdrawn) AIRDROP_EVENT_DEBOUNCE_MS else AIRDROP_LOST_GRACE_MS,
                    )
                    markAirdropNotReady(mac)
                    airdropLostJobs.remove(mac)
                }
            }
        }
    }

    /** Marks a peer not AirDrop-ready without dropping it from the AWDL list. */
    private fun markAirdropNotReady(mac: String) {
        airdropLostMacs.add(mac)
        peers = peers.map {
            if (it.mac.equals(mac, ignoreCase = true)) {
                it.copy(airdropState = AirdropState.NOT_READY)
            } else {
                it
            }
        }
        if (selectedMac.equals(mac, ignoreCase = true)) {
            selectedMac = peers.firstOrNull { it.airdropReady }?.mac
        }
    }

    private fun clearAirdropForMac(mac: String) {
        airdropFoundJobs.remove(mac)?.cancel()
        airdropLostJobs.remove(mac)?.cancel()
        airdropInstances.entries.removeAll { it.value.equals(mac, ignoreCase = true) }
        airdropSeenMacs.removeAll { it.equals(mac, ignoreCase = true) }
        airdropLostMacs.removeAll { it.equals(mac, ignoreCase = true) }
    }

    private fun clearAirdropServices() {
        airdropFoundJobs.values.forEach { it.cancel() }
        airdropFoundJobs.clear()
        airdropLostJobs.values.forEach { it.cancel() }
        airdropLostJobs.clear()
        airdropInstances.clear()
        airdropSeenMacs.clear()
        airdropLostMacs.clear()
    }

    /** A completed transfer can leave the last LOST mark stuck if no fresh FOUND follows. */
    private fun recoverAirdropReadiness() {
        if (airdropLostMacs.isEmpty() && peers.all { it.airdropReady }) return
        airdropFoundJobs.values.forEach { it.cancel() }
        airdropFoundJobs.clear()
        airdropLostJobs.values.forEach { it.cancel() }
        airdropLostJobs.clear()
        airdropLostMacs.clear()
        // Restore only peers that have advertised AirDrop at least once. A raw
        // AWDL peer must not become a send target through this recovery path.
        peers = peers.map {
            if (airdropSeenMacs.any { mac -> mac.equals(it.mac, ignoreCase = true) }) {
                it.copy(airdropState = AirdropState.READY)
            } else {
                it
            }
        }
        if (selectedMac == null) selectedMac = peers.firstOrNull { it.airdropReady }?.mac
    }

    private fun validMac(mac: String): Boolean =
        Regex("""^[0-9a-f]{2}(:[0-9a-f]{2}){5}$""").matches(mac)

    /** Extracts the AWDL MAC from an EUI-64 link-local address. */
    private fun macFromLinkLocal(addr: String): String? {
        val raw = addr.substringBefore('%').trim()
        if (raw.isBlank()) return null
        val bytes = runCatching { InetAddress.getByName(raw).address }.getOrNull() ?: return null
        if (bytes.size != 16) return null
        // EUI-64: fc:7d:cd:ff:fe:64:f3:f8 -> 48-bit MAC fe:7d:cd:64:f3:f8.
        // Drop the ff:fe marker and flip the U/L bit in the first octet.
        val eui = bytes.copyOfRange(8, 16)
        val mac = byteArrayOf(
            (eui[0].toInt() xor 0x02).toByte(),
            eui[1],
            eui[2],
            eui[5],
            eui[6],
            eui[7],
        )
        return mac.joinToString(":") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun handleReceiveLine(session: ReceiveSession, line: String) {
        appendHelperLine(line)
        // Ignore output still draining from a receiver we already replaced.
        if (receiveSession !== session) return
        if (line.startsWith("RECEIVE_READY") && !session.ready.isCompleted) {
            session.ready.complete(true)
        }
        handleReceiveStateLine(line)
    }

    private fun handleReceiveStateLine(line: String) {
        when {
            line.startsWith("RECEIVE_READY") -> {
                receiverRunning = true
                receiverStarting = false
                receiverStopping = false
                status = when {
                    !receiveEnabled && scanning -> "SCANNING"
                    !receiveEnabled -> "READY"
                    else -> "RECEIVING"
                }
                receiveMessage = if (receiveEnabled) {
                    "Receiver ready · waiting for a sender"
                } else {
                    "Receiving paused · discovery continues"
                }
                // Deliberately silent: readiness and discovery must not notify.
            }
            line.startsWith("RECEIVE_REQUEST ") -> handleIncomingRequest(line)
            line.startsWith("RECEIVE_REQUEST_FILES ") -> handleIncomingRequestFiles(line)
            line.startsWith("RECEIVE_ABORTING") -> {
                receiveMessage = "Stopping receive..."
            }
            line.startsWith("RECEIVE_ABORTED") -> handleReceiveAborted(line)
            line.startsWith("RECEIVE_ABORT_ERR") -> {
                receiveMessage = "Could not stop the receive."
            }
            line.startsWith("RECEIVE_ACCEPTED") -> handleIncomingAccepted(line)
            line.startsWith("RECEIVE_DECLINED") -> handleIncomingDeclined()
            line.startsWith("RECEIVE_WITHDRAWN") -> handleIncomingWithdrawn()
            line.startsWith("RECEIVE_FILE_START ") -> handleReceiveFileStart(line)
            line.startsWith("RECEIVE_FILE_PROGRESS ") -> handleReceiveFileProgress(line)
            line.startsWith("RECEIVE_FILE_DONE ") -> handleReceiveFileDone(line)
            line.startsWith("RECEIVE_PROGRESS") -> handleReceiveAggregateProgress(line)
            line.startsWith("RECEIVE_URLS") -> {
                receiveTransferring = true
                status = "RECEIVING"
                receiveMessage = "Preparing upload..."
            }
            line.startsWith("RECEIVED ") -> handleReceivedFile(line)
            line.startsWith("RECEIVE_DONE") -> handleReceiveDone(line)
            line.startsWith("RECEIVE_FAILED") -> handleReceiveFailed(
                line,
                line.substringAfter("reason=", line).trim(),
            )
            line.startsWith("RECEIVE_ERROR") -> handleReceiveFailed(line, line.trim())
            line.startsWith("RECEIVE_STOPPED") -> {
                receiverRunning = false
                receiverStarting = false
                receiverStopping = false
                clearReceiveProgressUi()
                receiveMessage = "Receiver stopped"
                incomingRequest = null
                incomingRequestNames = emptyList()
                receiveDecisionPending = false
                receiveTransferring = false
                notifications.cancelIncomingRequest()
                removePendingIncomingOffers()
                activeTransfers
                    .filter { it.direction == TransferDirection.RECEIVED }
                    .forEach { transfer ->
                        finishTransfer(
                            transfer.id,
                            TransferStatus.FAILED,
                            error = "Receiver stopped",
                        )
                    }
                status = if (kernel.monitorReady) "READY" else kernel.state.badge
            }
        }
    }

    private fun handleReceiveAborted(line: String) {
        val requestKey = eventId(line).toLongOrNull()?.toString() ?: return
        activeTransfers
            .filter {
                it.direction == TransferDirection.RECEIVED &&
                    transferRequestIds[it.id] == requestKey
            }
            .forEach { transfer ->
                finishTransfer(
                    transfer.id,
                    TransferStatus.CANCELLED,
                    error = "Stopped",
                )
            }
        clearReceiveProgressUi()
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        receiveMessage = if (receiveEnabled) "Receive stopped" else {
            "Receiving paused · discovery continues"
        }
        status = when {
            !receiveEnabled && scanning -> "SCANNING"
            !receiveEnabled -> if (kernel.monitorReady) "READY" else kernel.state.badge
            receiverRunning -> "RECEIVING"
            kernel.monitorReady -> "READY"
            else -> kernel.state.badge
        }
        notifications.cancelIncomingRequest()
        notifications.updateTransferSummary(visibleActiveTransferCount())
        append("[receive aborted]")
    }

    private fun handleIncomingRequest(line: String) {
        val id = eventId(line).toLongOrNull() ?: return
        if (!receiveEnabled) {
            sendReceiveCommand("decline $id")
            append("Incoming request auto-declined (receiving paused).")
            return
        }
        val peer = between(line, "peer=", " files=")
        val fileCount = between(line, "files=", " total=").toIntOrNull() ?: 0
        val total = between(line, "total=", " sender=").toLongOrNull() ?: 0L
        val sender = line.substringAfter("sender=", "").trim()
        val request = IncomingRequest(id, peer, sender, fileCount, total)
        val peerName = sender.ifBlank { peer }
        lastIncomingPeerName = peerName
        incomingRequest = request
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = total
        status = "INCOMING"
        receiveMessage = "Incoming request from ${sender.ifBlank { peer }}"

        // Replace any older offer that can no longer be answered.
        removePendingIncomingOffers()
        val transferId = "rx-$id"
        transferRequestIds[transferId] = id.toString()
        upsertTransfer(
            TransferItem(
                id = transferId,
                direction = TransferDirection.RECEIVED,
                peerName = peerName.ifBlank { "AirDrop" },
                fileName = when {
                    fileCount == 1 -> "Incoming file"
                    fileCount > 1 -> "$fileCount incoming files"
                    else -> "Incoming files"
                },
                totalBytes = total,
                transferredBytes = 0,
                status = TransferStatus.OFFERED,
                startedAt = System.currentTimeMillis(),
            ),
            notify = false,
        )
        if (appForeground) {
            notifications.cancelIncomingRequest()
        } else {
            notifications.showIncomingRequest(request)
        }
    }

    private fun handleIncomingRequestFiles(line: String) {
        val id = eventId(line).toLongOrNull() ?: return
        if (!receiveEnabled) return
        val names = parseNames(line.substringAfter("names=", ""))
        if (names.isEmpty()) return
        incomingRequestNames = names
        incomingRequest = incomingRequest?.let { request ->
            if (request.id == id) request.copy(names = names) else request
        }
        if (!appForeground && !receiveDecisionPending) {
            incomingRequest?.let { notifications.showIncomingRequest(it) }
        }

        val requestKey = id.toString()
        val items = transfersForRequest(requestKey).filter { it.status.isActive }
        if (items.isEmpty()) return
        val placeholder = items.firstOrNull { it.id == "rx-$id" } ?: items.firstOrNull()
        val fileCount = maxOf(names.size, incomingRequest?.fileCount ?: 0)
        if (fileCount <= 1) {
            placeholder?.let { item ->
                updateTransfer(item.id) { it.copy(fileName = names.first()) }
            }
            return
        }

        val total = incomingRequest?.totalBytes ?: placeholder?.totalBytes ?: 0L
        val perFile = if (total > 0) total / fileCount else 0L
        val remainder = if (total > 0) total - perFile * fileCount else 0L
        val startedAt = placeholder?.startedAt ?: System.currentTimeMillis()
        val status = placeholder?.status?.takeIf { it != TransferStatus.OFFERED }
            ?: TransferStatus.OFFERED
        val peerName = placeholder?.peerName ?: incomingPeerName()

        val oldIds = items.map { it.id }.toSet()
        activeTransfers = activeTransfers.filterNot { it.id in oldIds }
        completedTransfers = completedTransfers.filterNot { it.id in oldIds }
        oldIds.forEach { oldId ->
            transferRequestIds.remove(oldId)
            notifications.cancelTransfer(oldId)
        }

        repeat(fileCount) { index ->
            val transferId = "rx-$id-$index"
            transferRequestIds[transferId] = requestKey
            upsertTransfer(
                TransferItem(
                    id = transferId,
                    direction = TransferDirection.RECEIVED,
                    peerName = peerName.ifBlank { "AirDrop" },
                    fileName = names.getOrNull(index) ?: "File ${index + 1}",
                    totalBytes = perFile + if (index == fileCount - 1) remainder else 0L,
                    transferredBytes = 0,
                    status = status,
                    startedAt = startedAt,
                ),
                notify = false,
            )
        }
    }

    private fun handleIncomingAccepted(line: String) {
        val id = eventId(line).toLongOrNull()
        val total = after(line, "total=").toLongOrNull() ?: 0L
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = true
        receiveProgressBytes = 0
        if (total > 0) receiveProgressTotal = total
        status = "RECEIVING"
        receiveMessage = "Accepted · receiving file..."
        notifications.cancelIncomingRequest()

        if (id != null) {
            val requestKey = id.toString()
            val items = transfersForRequest(requestKey).filter { it.status.isActive }
            if (items.isEmpty()) {
                val transferId = "rx-$id"
                transferRequestIds[transferId] = requestKey
                upsertTransfer(
                    TransferItem(
                        id = transferId,
                        direction = TransferDirection.RECEIVED,
                        peerName = incomingPeerName(),
                        fileName = "Incoming file",
                        totalBytes = total,
                        transferredBytes = 0,
                        status = TransferStatus.ACCEPTED,
                        startedAt = System.currentTimeMillis(),
                    ),
                    notify = false,
                )
            } else {
                items.forEach { item ->
                    updateTransfer(item.id, notify = false) {
                        it.copy(
                            status = TransferStatus.ACCEPTED,
                            totalBytes = if (it.totalBytes > 0) it.totalBytes else total,
                        )
                    }
                }
            }
        } else {
            activeTransfers
                .filter {
                    it.direction == TransferDirection.RECEIVED &&
                        it.status == TransferStatus.OFFERED
                }
                .forEach { item ->
                    updateTransfer(item.id, notify = false) {
                        it.copy(status = TransferStatus.ACCEPTED)
                    }
                }
        }
    }

    private fun handleIncomingDeclined() {
        val requestKey = incomingRequest?.id?.toString()
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        status = "RECEIVING"
        receiveMessage = "Request declined · waiting for a sender"
        notifications.cancelIncomingRequest()
        if (requestKey != null) {
            removeTransfersForRequest(requestKey)
        } else {
            removePendingIncomingOffers()
        }
    }

    private fun handleIncomingWithdrawn() {
        val requestKey = incomingRequest?.id?.toString()
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        status = "RECEIVING"
        receiveMessage = "Request withdrawn · waiting for a sender"
        notifications.cancelIncomingRequest()
        if (requestKey != null) {
            removeTransfersForRequest(requestKey)
        } else {
            removePendingIncomingOffers()
        }
    }

    private fun handleReceiveFileStart(line: String) {
        val helperId = eventId(line)
        val name = between(line, "name=", " total=")
        val total = after(line, "total=").toLongOrNull() ?: 0L
        val transferId = resolveReceiveFileTransferId(helperId, name) ?: return
        if (total > 0) originalTransferTotals[transferId] = total
        updateTransfer(transferId) { item ->
            item.copy(
                fileName = name.ifBlank { item.fileName },
                totalBytes = if (total > 0) total else item.totalBytes,
                status = if (item.status == TransferStatus.OFFERED) {
                    TransferStatus.ACCEPTED
                } else {
                    item.status
                },
                uploadStartedAt = item.uploadStartedAt ?: System.currentTimeMillis(),
            )
        }
    }

    private fun handleReceiveFileProgress(line: String) {
        val helperId = eventId(line)
        val name = between(line, "name=", " bytes=")
        val bytes = between(line, "bytes=", " total=").toLongOrNull() ?: 0L
        val total = after(line, "total=").toLongOrNull() ?: 0L
        val transferId = resolveReceiveFileTransferId(helperId, name) ?: return
        pendingReceiveUi[transferId] = PendingReceiveProgress(name, bytes, total)
        scheduleReceiveUi()
    }

    private fun handleReceiveFileDone(line: String) {
        val helperId = eventId(line)
        val name = between(line, "name=", " path=")
        val path = line.substringAfter("path=", "").trim()
        val transferId = resolveReceiveFileTransferId(helperId, name) ?: return
        // The receiver emits one completion before the batch ends, and the
        // final Received event names the same path again.
        if (completedTransfers.any { it.id == transferId && it.status == TransferStatus.COMPLETE }) return
        val fileName = name.ifBlank { if (path.isNotBlank()) File(path).name else "" }
        // FileComplete carries the staging-relative path. The final RECEIVED
        // event supplies the absolute path; don't expose the relative one.
        val absolutePath = path.takeIf { it.startsWith("/") }
        finishTransfer(
            transferId,
            TransferStatus.COMPLETE,
            localPath = absolutePath,
            fileName = fileName.ifBlank { null },
        )
        if (absolutePath != null) addReceivedPath(absolutePath)
    }

    private fun handleReceiveAggregateProgress(line: String) {
        val bytes = between(line, "bytes=", " total=").toLongOrNull() ?: 0L
        val total = between(line, "total=", " files=").toLongOrNull() ?: 0L
        pendingReceiveAggregate = bytes to total
        scheduleReceiveUi()
    }

    private fun scheduleReceiveUi() {
        if (receiveUiJob?.isActive == true) return
        val wait = (PROGRESS_UI_INTERVAL_MS -
            (System.currentTimeMillis() - lastReceiveUiAt)).coerceIn(0L, PROGRESS_UI_INTERVAL_MS)
        receiveUiJob = scope.launch {
            if (wait > 0) delay(wait)
            val files = pendingReceiveUi.toMap()
            pendingReceiveUi.clear()
            val aggregate = pendingReceiveAggregate
            pendingReceiveAggregate = null
            lastReceiveUiAt = System.currentTimeMillis()

            files.forEach { (id, progress) ->
                updateTransfer(id) { item ->
                    if (item.status.isTerminal) {
                        item
                    } else {
                        item.copy(
                            fileName = progress.name.ifBlank { item.fileName },
                            // Per-file progress can race the aggregate ticker.
                            // Keep the high-water mark so rows never move back.
                            transferredBytes = maxOf(item.transferredBytes, progress.bytes),
                            totalBytes = if (progress.total > 0) {
                                progress.total
                            } else {
                                item.totalBytes
                            },
                            status = TransferStatus.TRANSFERRING,
                            uploadStartedAt = item.uploadStartedAt ?: System.currentTimeMillis(),
                        )
                    }
                }
            }

            if (aggregate != null) {
                val (bytes, total) = aggregate
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
                    "Receiving $percent% · ${bytes.readableBytes()} / " +
                        receiveProgressTotal.readableBytes()
                } else {
                    "Receiving ${bytes.readableBytes()}"
                }
            } else if (files.isNotEmpty()) {
                receiveProgressBytes = activeTransfers
                    .filter { it.direction == TransferDirection.RECEIVED }
                    .sumOf { it.transferredBytes }
                if (receiveProgressTotal <= 0) {
                    receiveProgressTotal = activeTransfers
                        .filter { it.direction == TransferDirection.RECEIVED }
                        .sumOf { it.totalBytes }
                }
            }
        }
    }

    private fun clearReceiveProgressUi() {
        receiveUiJob?.cancel()
        receiveUiJob = null
        pendingReceiveUi.clear()
        pendingReceiveAggregate = null
    }

    private fun handleReceivedFile(line: String) {
        val id = eventId(line).toLongOrNull()
        val requestKey = id?.toString() ?: incomingRequest?.id?.toString()
        val path = line.substringAfter("path=", "").trim()
        if (path.isNotBlank()) {
            addReceivedPath(path)
            receiveMessage = "Received ${File(path).name}"
            status = "RECEIVED"
        }
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        // Do not finish the whole receive on the first `RECEIVED` line: one
        // line is emitted per file, and RECEIVE_DONE closes the batch.
        notifications.cancelIncomingRequest()

        if (path.isNotBlank()) {
            val fileName = File(path).name
            val candidates = if (requestKey != null) transfersForRequest(requestKey) else emptyList()
            val match = candidates.firstOrNull { it.fileName == fileName }
                ?: candidates.firstOrNull { it.localPath == path }
                ?: candidates.firstOrNull { it.status.isActive }
                // A duplicate destination name (for example "file (2).mov")
                // changes the final basename after the per-file completion
                // already created this request's single row. Reuse that row
                // instead of creating a second card.
                ?: candidates.singleOrNull()
                ?: candidates.firstOrNull {
                    it.direction == TransferDirection.RECEIVED &&
                        it.status == TransferStatus.COMPLETE &&
                        it.localPath.isNullOrBlank()
                }
            if (match != null) {
                finishTransfer(
                    match.id,
                    TransferStatus.COMPLETE,
                    localPath = path,
                    fileName = fileName,
                )
            } else {
                val transferId = "rx-${requestKey ?: "file"}-${++receiveFileCounter}"
                if (requestKey != null) transferRequestIds[transferId] = requestKey
                val completed = TransferItem(
                    id = transferId,
                    direction = TransferDirection.RECEIVED,
                    peerName = incomingPeerName(),
                    fileName = fileName,
                    totalBytes = File(path).length().coerceAtLeast(0),
                    transferredBytes = File(path).length().coerceAtLeast(0),
                    status = TransferStatus.COMPLETE,
                    localPath = path,
                    startedAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis(),
                )
                upsertTransfer(completed)
                logTransferStats(completed)
            }
        }
    }

    private fun handleReceiveDone(line: String) {
        val id = eventId(line).toLongOrNull()
        val requestKey = id?.toString() ?: incomingRequest?.id?.toString()
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        notifications.cancelIncomingRequest()
        if (requestKey != null) {
            transfersForRequest(requestKey)
                .filter { it.status.isActive }
                .forEach { item ->
                    finishTransfer(item.id, TransferStatus.COMPLETE)
                }
        }
        clearReceiveProgressUi()
        if (status != "RECEIVED") status = "RECEIVING"
        receiveMessage = "Transfer complete · waiting for a sender"
        receiveProgressBytes = receiveProgressTotal
    }

    private fun handleReceiveFailed(line: String, reason: String) {
        val id = eventId(line).toLongOrNull()
        val requestKey = id?.toString() ?: incomingRequest?.id?.toString()
        val items = if (requestKey != null) {
            transfersForRequest(requestKey).filter { it.status.isActive }
        } else {
            emptyList()
        }
        val wasAccepted = receiveTransferring ||
            items.any { it.status == TransferStatus.ACCEPTED || it.status == TransferStatus.TRANSFERRING }
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        clearReceiveProgressUi()
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        status = "RECEIVE ERROR"
        receiveMessage = if (reason.startsWith("Receive failed")) reason else "Receive failed: $reason"
        notifications.cancelIncomingRequest()
        if (requestKey != null) {
            if (wasAccepted) {
                items.forEach { item ->
                    finishTransfer(item.id, TransferStatus.FAILED, error = reason)
                }
            } else {
                removeTransfersForRequest(requestKey)
            }
        }
    }

    private fun updateIncomingAggregateProgress(id: Long?, bytes: Long, total: Long) {
        val requestKey = id?.toString() ?: incomingRequest?.id?.toString()
        val items = if (requestKey != null) {
            transfersForRequest(requestKey).filter {
                it.status == TransferStatus.OFFERED ||
                    it.status == TransferStatus.ACCEPTED ||
                    it.status == TransferStatus.TRANSFERRING
            }
        } else {
            emptyList()
        }
        if (items.isEmpty()) return
        if (items.size == 1) {
            val item = items.first()
            updateTransfer(item.id) {
                it.copy(
                    transferredBytes = bytes,
                    totalBytes = if (total > 0) total else it.totalBytes,
                    status = TransferStatus.TRANSFERRING,
                )
            }
            return
        }
        var remaining = bytes
        items.sortedBy { it.startedAt }.forEach { item ->
            val cap = if (item.totalBytes > 0) item.totalBytes else Long.MAX_VALUE
            val take = remaining.coerceAtMost(cap)
            updateTransfer(item.id) {
                it.copy(
                    transferredBytes = take,
                    status = TransferStatus.TRANSFERRING,
                )
            }
            remaining -= take
        }
    }

    private fun resolveReceiveFileTransferId(helperId: String, name: String): String? {
        val cleanName = name.trim()
        val known = activeTransfers + completedTransfers
        // The helper uses the request id for every per-file event, so a
        // request id must be resolved by file name each time. Only genuinely
        // per-file helper ids may be cached.
        val isRequestId = helperId.isNotBlank() && (
            incomingRequest?.id?.toString() == helperId ||
                known.any { transferRequestIds[it.id] == helperId }
            )
        if (helperId.isNotBlank() && !isRequestId) {
            helperFileTransferIds[helperId]?.let { return it }
        }
        val requestKey = helperId.takeIf { it.isNotBlank() }
            ?: incomingRequest?.id?.toString()
        val candidates = if (requestKey != null) {
            transfersForRequest(requestKey)
        } else {
            known.filter { it.direction == TransferDirection.RECEIVED }
        }
        val exact = candidates.firstOrNull {
            it.direction == TransferDirection.RECEIVED && it.status.isActive &&
                cleanName.isNotBlank() && it.fileName == cleanName
        } ?: candidates.firstOrNull {
            it.direction == TransferDirection.RECEIVED &&
                cleanName.isNotBlank() && it.fileName == cleanName
        }
        if (exact != null) {
            if (helperId.isNotBlank() && !isRequestId) {
                helperFileTransferIds[helperId] = exact.id
            }
            return exact.id
        }
        if (cleanName.isBlank()) {
            val single = candidates.filter {
                it.direction == TransferDirection.RECEIVED && it.status.isActive
            }.singleOrNull()
            if (single != null) {
                if (helperId.isNotBlank() && !isRequestId) {
                    helperFileTransferIds[helperId] = single.id
                }
                return single.id
            }
        }
        val placeholder = candidates.firstOrNull {
            it.direction == TransferDirection.RECEIVED && it.status.isActive &&
                isGenericIncomingName(it.fileName)
        }
        if (placeholder != null) {
            if (helperId.isNotBlank() && !isRequestId) {
                helperFileTransferIds[helperId] = placeholder.id
            }
            return placeholder.id
        }
        val activeSingle = candidates.filter {
            it.direction == TransferDirection.RECEIVED && it.status.isActive
        }.singleOrNull()
        if (activeSingle != null) {
            if (helperId.isNotBlank() && !isRequestId) {
                helperFileTransferIds[helperId] = activeSingle.id
            }
            return activeSingle.id
        }

        // A helper may emit file events with per-file IDs before names are
        // known. Match a global name first, then create a new row.
        val globalExact = known.firstOrNull {
            it.direction == TransferDirection.RECEIVED &&
                cleanName.isNotBlank() && it.fileName == cleanName
        }
        if (globalExact != null) {
            if (helperId.isNotBlank() && !isRequestId) {
                helperFileTransferIds[helperId] = globalExact.id
            }
            return globalExact.id
        }
        val globalPlaceholder = known.firstOrNull {
            it.direction == TransferDirection.RECEIVED && it.status.isActive &&
                isGenericIncomingName(it.fileName)
        }
        if (globalPlaceholder != null) {
            if (helperId.isNotBlank() && !isRequestId) {
                helperFileTransferIds[helperId] = globalPlaceholder.id
            }
            return globalPlaceholder.id
        }

        val transferId = "rx-${requestKey ?: "file"}-${++receiveFileCounter}"
        if (requestKey != null) transferRequestIds[transferId] = requestKey
        if (helperId.isNotBlank() && !isRequestId) {
            helperFileTransferIds[helperId] = transferId
        }
        val baseStart = candidates.firstOrNull { it.status.isActive }?.startedAt
            ?: candidates.firstOrNull()?.startedAt
            ?: System.currentTimeMillis()
        upsertTransfer(
            TransferItem(
                id = transferId,
                direction = TransferDirection.RECEIVED,
                peerName = incomingPeerName(),
                fileName = cleanName.ifBlank { "Incoming file" },
                totalBytes = 0,
                transferredBytes = 0,
                status = TransferStatus.ACCEPTED,
                startedAt = baseStart,
            ),
            notify = false,
        )
        return transferId
    }

    private fun parseNames(raw: String): List<String> {
        val cleaned = raw.trim().removePrefix("[").removeSuffix("]")
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(',')
            .map { it.trim().trim('"', '\'', '[', ']') }
            .filter { it.isNotBlank() }
            .take(50)
    }

    private fun isGenericIncomingName(name: String): Boolean =
        name.startsWith("Incoming") ||
            name.endsWith("incoming files") ||
            name.matches(Regex("""File \d+"""))

    private fun addReceivedPath(path: String) {
        receivedFiles.remove(path)
        receivedFiles.add(0, path)
    }

    private fun incomingPeerName(): String {
        val request = incomingRequest
        if (request != null) {
            val name = request.sender.ifBlank { request.peer }
            if (name.isNotBlank()) return name
        }
        return lastIncomingPeerName.ifBlank { "AirDrop" }
    }

    private fun transfersForRequest(requestKey: String): List<TransferItem> =
        (activeTransfers + completedTransfers).filter {
            transferRequestIds[it.id] == requestKey
        }

    private fun removeTransfersForRequest(requestKey: String) {
        val ids = transfersForRequest(requestKey).map { it.id }.toSet()
        if (ids.isEmpty()) return
        activeTransfers = activeTransfers.filterNot { it.id in ids }
        completedTransfers = completedTransfers.filterNot { it.id in ids }
        ids.forEach { id ->
            transferRequestIds.remove(id)
            originalTransferTotals.remove(id)
            notifications.cancelTransfer(id)
        }
        notifications.updateTransferSummary(visibleActiveTransferCount())
    }

    private fun removePendingIncomingOffers() {
        val ids = activeTransfers
            .filter { it.direction == TransferDirection.RECEIVED && it.status == TransferStatus.OFFERED }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) return
        activeTransfers = activeTransfers.filterNot { it.id in ids }
        ids.forEach { id ->
            transferRequestIds.remove(id)
            originalTransferTotals.remove(id)
            notifications.cancelTransfer(id)
        }
        notifications.updateTransferSummary(visibleActiveTransferCount())
    }

    private fun findTransfer(id: String): TransferItem? =
        activeTransfers.firstOrNull { it.id == id }
            ?: completedTransfers.firstOrNull { it.id == id }

    private fun upsertTransfer(item: TransferItem, notify: Boolean = true) {
        val active = activeTransfers.filterNot { it.id == item.id }.toMutableList()
        val completed = completedTransfers.filterNot { it.id == item.id }.toMutableList()
        if (item.status.isActive) {
            active.add(item)
            active.sortWith(
                compareByDescending<TransferItem> { it.startedAt }.thenBy { it.id },
            )
        } else {
            completed.add(0, item)
            if (completed.size > 100) {
                completed.subList(100, completed.size).clear()
            }
        }
        activeTransfers = active
        completedTransfers = completed
        if (notify) notifications.updateTransfer(item)
        notifications.updateTransferSummary(visibleActiveTransferCount())
    }

    private fun updateTransfer(
        id: String,
        notify: Boolean = true,
        transform: (TransferItem) -> TransferItem,
    ): TransferItem? {
        val current = findTransfer(id) ?: return null
        val next = transform(current)
        if (next == current) return current
        upsertTransfer(next, notify)
        return next
    }

    /**
     * Maps aggregate compressed progress onto the batch's source files.
     *
     * `sent` counts compressed upload bytes, so it cannot be compared with a
     * source file's size directly. Convert it to the source-size equivalent
     * first; otherwise a small compressed batch can finish the visible queue
     * in the wrong order (or leave the first queued file stuck forever).
     */
    private fun progressChildren(
        files: List<TransferFile>,
        sent: Long,
        uploadTotal: Long,
    ): List<TransferFile> {
        val sourceTotal = files.sumOf { it.totalBytes }
        val sourceProgress = if (uploadTotal > 0) {
            (sourceTotal * (sent.toDouble() / uploadTotal.toDouble()))
                .toLong()
                .coerceIn(0L, sourceTotal)
        } else {
            sent.coerceIn(0L, sourceTotal)
        }
        var remaining = sourceProgress
        val completedNow = System.currentTimeMillis()
        return files.map { file ->
            val take = remaining.coerceAtMost(file.totalBytes)
            remaining -= take
            val status = when {
                file.totalBytes > 0 && take >= file.totalBytes -> TransferStatus.COMPLETE
                take > 0 -> TransferStatus.TRANSFERRING
                else -> TransferStatus.WAITING
            }
            file.copy(
                transferredBytes = take,
                status = status,
                completedAt = if (status.isTerminal) {
                    file.completedAt ?: completedNow
                } else {
                    file.completedAt
                },
            )
        }
    }

    private fun finishTransfer(
        id: String,
        status: TransferStatus,
        error: String? = null,
        localPath: String? = null,
        fileName: String? = null,
        notify: Boolean = true,
    ): TransferItem? {
        val current = findTransfer(id) ?: return null
        val wasTerminal = current.status.isTerminal
        val originalTotal = if (status == TransferStatus.COMPLETE) {
            originalTransferTotals[id]
        } else {
            null
        }
        val finalTotal = originalTotal?.takeIf { it > 0 } ?: current.totalBytes
        val next = current.copy(
            status = status,
            error = error ?: current.error,
            localPath = localPath ?: current.localPath,
            fileName = fileName?.takeIf { it.isNotBlank() } ?: current.fileName,
            totalBytes = finalTotal,
            transferredBytes = if (status == TransferStatus.COMPLETE) {
                if (finalTotal > 0) finalTotal else current.transferredBytes
            } else {
                current.transferredBytes
            },
            completedAt = current.completedAt ?: System.currentTimeMillis(),
            files = current.files.map { file ->
                val nextStatus = if (status.isTerminal) status else file.status
                file.copy(
                    status = nextStatus,
                    transferredBytes = if (status == TransferStatus.COMPLETE) {
                        file.totalBytes
                    } else {
                        file.transferredBytes
                    },
                    completedAt = if (nextStatus.isTerminal) {
                        file.completedAt ?: System.currentTimeMillis()
                    } else {
                        file.completedAt
                    },
                )
            },
        )
        if (status.isTerminal) originalTransferTotals.remove(id)
        if (next == current) return current
        upsertTransfer(next, notify)
        if (!wasTerminal && next.status.isTerminal) logTransferStats(next)
        if (next.status.isTerminal && next.direction == TransferDirection.SENT) {
            clearSendProgressUi()
        }
        // A cancel/failure can leave the last LOST mark stuck, hiding a peer
        // that is still in the AWDL peer list. Recover on every terminal state.
        if (status.isTerminal) recoverAirdropReadiness()
        return next
    }

    /**
     * One final, human-readable line per received/sent file. Live progress
     * stays on the transfer card; the log only records the outcome and stats.
     */
    private fun logTransferStats(transfer: TransferItem) {
        val outcome = when (transfer.status) {
            TransferStatus.COMPLETE -> "complete"
            TransferStatus.CANCELLED -> "cancelled"
            TransferStatus.FAILED -> "failed"
            else -> return
        }
        val direction = if (transfer.direction == TransferDirection.SENT) "Send" else "Receive"
        val bytes = if (transfer.status == TransferStatus.COMPLETE) {
            maxOf(transfer.totalBytes, transfer.transferredBytes)
        } else {
            transfer.transferredBytes
        }
        val total = transfer.totalBytes
        val started = transfer.uploadStartedAt ?: transfer.startedAt
        val elapsedMs = ((transfer.completedAt ?: System.currentTimeMillis()) - started)
            .coerceAtLeast(0L)
        val seconds = elapsedMs / 1000.0
        val speed = if (seconds > 0.0) (bytes / seconds).toLong() else 0L
        val size = if (total > 0 && total != bytes) {
            "${bytes.readableBytes()} / ${total.readableBytes()}"
        } else {
            bytes.readableBytes()
        }
        val duration = when {
            elapsedMs < 1000 -> "${elapsedMs}ms"
            elapsedMs < 60_000 -> "${elapsedMs / 1000}s"
            else -> "${elapsedMs / 60_000}m ${(elapsedMs / 1000) % 60}s"
        }
        val detail = transfer.error?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        append(
            "$direction $outcome: ${transfer.fileName} · $size · " +
                "$duration · ${speed.readableBytes()}/s$detail",
        )
    }

    private fun visibleActiveTransferCount(): Int = activeTransfers.count {
        it.direction == TransferDirection.SENT || it.status != TransferStatus.OFFERED
    }

    /** Starts the receive helper process; caller holds [receiveMutex]. */
    private suspend fun startReceiveLocked(): ReceiveSession? {
        val existing = receiveSession
        if (existing != null) stopReceiveLocked(existing)

        val command = helper(
            "receive '$RECEIVE_DIR' 8771 $activeChannel '${helperName()}'",
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
        notifications.cancelIncomingRequest()
        removePendingIncomingOffers()
        activeTransfers
            .filter { it.direction == TransferDirection.RECEIVED }
            .forEach { transfer ->
                finishTransfer(
                    transfer.id,
                    TransferStatus.FAILED,
                    error = "Receiver stopped",
                )
            }
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
        val writer = receiveSession?.writer ?: helperSession?.writer ?: return false
        return try {
            writer.write(command)
            writer.newLine()
            writer.flush()
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
        requestId: String? = null,
        command: (String) -> String,
        onLine: (String) -> Unit = {},
    ): Int = commandMutex.withLock {
        if (kind == "send" && sendCancelled) return@withLock -1
        val session = sessionMutex.withLock { ensureSessionLocked() } ?: return@withLock -1
        if (kind == "send" && sendCancelled) {
            sessionMutex.withLock { stopSessionLocked(session) }
            return@withLock -1
        }
        val id = requestId ?: "$kind-${++requestCounter}"
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
            current.channel == activeChannel && current.name == helperName()
        ) {
            return current
        }
        if (current != null) stopSessionLocked()

        prepare()
        if (!startBle(fresh = false)) {
            withContext(Dispatchers.Main) {
                status = "BLUETOOTH OFF"
                sendMessage = "Turn on Bluetooth to use AirDrop discovery."
            }
            append("BLE wake advertisement is not ready; not starting the radio session.")
            return null
        }
        val channel = activeChannel
        val name = helperName()
        // Let the hidden STA vdev do normal 802.11 ACK/retry. The ACK-less
        // path turns every lost unicast frame into a TCP retransmission and
        // collapses to the observed 64 KiB staircase.
        val command = "OWLY_TX_NO_ACK=0 OWLY_UPLOAD=${settings.uploadPolicy.env} " +
            "$APP_TMP/owly-k20-helper session $channel '$name' '$RECEIVE_DIR' 8771"
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
        runCatching {
            session.writer.write(
                "set-transfer-policy " +
                    "pause_discovery=${if (settings.pauseDiscoveryDuringTransfer) 1 else 0} " +
                    "visible_during_transfer=${if (settings.visibleDuringTransfer) 1 else 0}",
            )
            session.writer.newLine()
            session.writer.flush()
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
        appendHelperLine(line)
        // Ignore output still draining from a session we already replaced.
        if (helperSession !== session) return
        when {
            line.startsWith("SESSION_READY") -> {
                if (!session.ready.isCompleted) session.ready.complete(true)
            }
            line.startsWith("PEER ") || line.startsWith("PEER_GONE ") ||
                line.startsWith("AWDL name:") -> handlePeerLine(line)
            line.startsWith("FOUND ") || line.startsWith("LOST ") ||
                line.startsWith("WITHDRAWN ") -> handleAirdropServiceLine(line)
            line.startsWith("RECEIVE_") || line.startsWith("RECEIVED ") ->
                handleReceiveStateLine(line)
            line.startsWith("SEND_FILE_") -> handleSendFileEvent(line)
        }

        val pending = pendingRequest ?: return
        pending.onLine(line)
        val tokens = line.trim().split(Regex("\\s+"), limit = 3)
        if (tokens.size < 2 || tokens[1] != pending.id) return
        when (tokens[0]) {
            "SCAN_DONE" -> if (pending.kind == "scan") finishPending(pending, 0)
            "SEND_OK" -> if (pending.kind == "send") {
                activeSendTransferId?.let { id ->
                    finishTransfer(id, TransferStatus.COMPLETE)
                }
                finishPending(pending, 0)
            }
            "SEND_ERR" -> if (pending.kind == "send") {
                val message = if (tokens.size >= 3) tokens[2].trim() else ""
                if (!sendCancelled) {
                    if (message.isNotBlank()) sendMessage = message
                    activeSendTransferId?.let { id ->
                        finishTransfer(
                            id,
                            TransferStatus.FAILED,
                            error = message.ifBlank { "Send failed" },
                        )
                    }
                }
                finishPending(pending, -1)
            }
            "SEND_CANCELLED" -> if (pending.kind == "send") {
                finishPending(pending, -1)
            }
        }
    }

    private fun handleSendFileEvent(line: String) {
        when {
            line.startsWith("SEND_FILE_START ") -> {
                val helperId = eventId(line)
                val total = after(line, "total=").toLongOrNull() ?: 0L
                val transferId = resolveSendTransferId(helperId) ?: return
                updateTransfer(transferId) { item ->
                    // `name=` is the cache basename, not the user's source
                    // filename; keep the name chosen by the picker.
                    item.copy(
                        totalBytes = if (total > 0) total else item.totalBytes,
                    )
                }
            }
            line.startsWith("SEND_FILE_PROGRESS ") -> {
                val helperId = eventId(line)
                val bytes = between(line, "bytes=", " total=").toLongOrNull() ?: 0L
                val total = after(line, "total=").toLongOrNull() ?: 0L
                val transferId = resolveSendTransferId(helperId) ?: return
                sendProgressBytes = bytes
                if (total > 0) sendProgressTotal = total
                updateTransfer(transferId) { item ->
                    item.copy(
                        transferredBytes = bytes,
                        totalBytes = if (total > 0) total else item.totalBytes,
                        status = TransferStatus.TRANSFERRING,
                    )
                }
            }
            line.startsWith("SEND_FILE_DONE ") -> {
                val helperId = eventId(line)
                val path = line.substringAfter("path=", "").trim()
                val transferId = resolveSendTransferId(helperId) ?: return
                finishTransfer(
                    transferId,
                    TransferStatus.COMPLETE,
                    localPath = path.ifBlank { null },
                )
            }
            line.startsWith("SEND_FILE_CANCELLED ") -> {
                val helperId = eventId(line)
                val transferId = resolveSendTransferId(helperId) ?: return
                finishTransfer(
                    transferId,
                    TransferStatus.CANCELLED,
                    error = "Cancelled",
                )
            }
            line.startsWith("SEND_FILE_ERR ") -> {
                if (sendCancelled) return
                val helperId = eventId(line)
                val reason = line.substringAfter("reason=", "Send failed").trim()
                val transferId = resolveSendTransferId(helperId) ?: return
                finishTransfer(
                    transferId,
                    TransferStatus.FAILED,
                    error = reason.ifBlank { "Send failed" },
                )
            }
        }
    }

    private fun resolveSendTransferId(helperId: String): String? {
        if (helperId.isNotBlank()) {
            helperSendFileIds[helperId]?.let { return it }
            findTransfer(helperId)?.let { transfer ->
                if (transfer.direction == TransferDirection.SENT) return transfer.id
            }
        }
        val active = activeSendTransferId?.takeIf { id ->
            findTransfer(id)?.status?.isActive == true
        } ?: return null
        if (helperId.isNotBlank()) helperSendFileIds[helperId] = active
        return active
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
        lockedChannel = null
        peers = emptyList()
        selectedMac = null
        clearAirdropServices()
        receiverRunning = false
        receiverStarting = false
        receiverStopping = false
        clearReceiveProgressUi()
        activeSendTransferId?.let { id ->
            finishTransfer(
                id,
                TransferStatus.FAILED,
                error = "Helper session ended during send",
            )
        }
        activeSendTransferId = null
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        receiveMessage = "Receiver stopped"
        notifications.cancelIncomingRequest()
        removePendingIncomingOffers()
        activeTransfers
            .filter { it.direction == TransferDirection.RECEIVED }
            .forEach { transfer ->
                finishTransfer(
                    transfer.id,
                    TransferStatus.FAILED,
                    error = "Receiver stopped",
                )
            }
        status = if (kernel.monitorReady) "READY" else kernel.state.badge
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

    /** Clears receiver UI/state when the shared helper session is gone. */
    private fun resetReceiverState() {
        receiverRunning = false
        receiverStarting = false
        receiverStopping = false
        receiveEnabled = true
        clearReceiveProgressUi()
        incomingRequest = null
        incomingRequestNames = emptyList()
        receiveDecisionPending = false
        receiveTransferring = false
        receiveProgressBytes = 0
        receiveProgressTotal = 0
        receiveMessage = "Receiver stopped"
        notifications.cancelIncomingRequest()
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
        // The session owns the channel and its discovery state. Never let a selected peer
        // survive a channel/session switch and turn into a send on the wrong channel.
        peers = emptyList()
        selectedMac = null
        clearAirdropServices()
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

    /**
     * Enables Bluetooth if needed, starts the BLE wake advertisement, and
     * waits for the advertiser to report success. Returns false when the
     * radio must not start without BLE.
     */
    private suspend fun startBle(fresh: Boolean): Boolean = withContext(Dispatchers.IO) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            bleStartFailed = true
            withContext(Dispatchers.Main) { append("BLE: Bluetooth adapter is not available.") }
            return@withContext false
        }

        fun bluetoothOn(): Boolean = runCatching { adapter.isEnabled }.getOrDefault(false)

        if (!bluetoothOn()) {
            withContext(Dispatchers.Main) {
                append("BLE: Bluetooth is off; enabling it before discovery...")
            }
            try {
                val enable = ProcessBuilder(
                    "su",
                    "-c",
                    "cmd bluetooth_manager enable",
                ).redirectErrorStream(true).start()
                if (!enable.waitFor(5, TimeUnit.SECONDS)) enable.destroyForcibly()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    append("BLE: could not enable Bluetooth: ${e.message}")
                }
            }
            val deadline = System.currentTimeMillis() + 8_000
            while (!bluetoothOn() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            if (!bluetoothOn()) {
                bleStartFailed = true
                withContext(Dispatchers.Main) {
                    append("BLE: Bluetooth did not turn on; Transfer mode blocked.")
                }
                return@withContext false
            }
            withContext(Dispatchers.Main) {
                append("BLE: Bluetooth is now on.")
            }
        }

        val callback: (String) -> Unit = { line -> scope.launch { append(line) } }
        val accepted = if (fresh) {
            ble.restart(settings.bleArm, true, callback)
        } else if (!ble.isRunning()) {
            ble.start(settings.bleArm, true, callback)
        } else {
            true
        }
        if (!accepted) {
            bleStartFailed = true
            withContext(Dispatchers.Main) {
                append("BLE: advertisement start was rejected; Transfer mode blocked.")
            }
            return@withContext false
        }

        // startAdvertising() is asynchronous. Wait for the success callback
        // instead of assuming the advertisement is already on air.
        val deadline = System.currentTimeMillis() + 5_000
        while (!ble.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        val ready = ble.isRunning()
        bleStartFailed = !ready
        withContext(Dispatchers.Main) {
            append(
                if (ready) {
                    "BLE: advertisement is ready."
                } else {
                    "BLE: advertisement did not start; Transfer mode blocked."
                },
            )
        }
        ready
    }

    /**
     * Runs [command] through `su`. A [timeoutMs] above zero bounds the wait so a
     * stuck root command cannot block the caller forever; the timeout is
     * reported as [TIMEOUT_EXIT_CODE] instead of being hidden.
     */
    private suspend fun execRoot(
        label: String,
        command: String,
        requirePrepared: Boolean = true,
        timeoutMs: Long = 0L,
        onLine: (String) -> Unit = {},
    ): Int {
        if (requirePrepared) prepare()
        append("")
        append("$ $label")
        return withContext(Dispatchers.IO) {
            try {
                if (timeoutMs <= 0L) {
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
                    return@withContext rc
                }
                // Android has no ProcessHandle.descendants(), so the shell
                // records its own PID and the tree is killed through /proc.
                val pidFile = File(context.cacheDir, "exec-${System.nanoTime()}.pid")
                val process = ProcessBuilder("su", "-c", "echo \$\$ > '${pidFile.absolutePath}'; $command")
                    .redirectErrorStream(true)
                    .start()
                // Drain on a helper thread: a stuck su can keep the pipe open,
                // so reading inline would defeat the timeout.
                val output = ConcurrentLinkedQueue<String>()
                val drain = thread(isDaemon = true, name = "execRoot-drain") {
                    runCatching {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { output.add(it) }
                        }
                    }
                }
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    // Kill the su descendants too, otherwise the shell or the
                    // mode script keeps running after su is gone.
                    val shellPid = runCatching { pidFile.readText().trim().toLong() }.getOrNull()
                    if (shellPid != null) killProcessTree(shellPid)
                    process.destroyForcibly()
                    runCatching { process.waitFor(2, TimeUnit.SECONDS) }
                }
                pidFile.delete()
                drain.join(2_000)
                val rc = if (finished) process.waitFor() else TIMEOUT_EXIT_CODE
                withContext(Dispatchers.Main) {
                    output.forEach { line ->
                        append(line)
                        onLine(line)
                    }
                    if (!finished) {
                        append("TIMEOUT: $label did not finish in ${timeoutMs / 1000}s; killed su")
                    }
                    append("[exit $rc]")
                }
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

    /**
     * Kills a root command's process tree. Android has no
     * `ProcessHandle.descendants()`, so /proc/<pid>/task/<pid>/children is
     * walked from a root shell instead.
     */
    private fun killProcessTree(pid: Long) {
        val script = "kill_tree() { " +
            "for c in \$(cat /proc/\$1/task/\$1/children 2>/dev/null); do " +
            "kill_tree \"\$c\"; kill -9 \"\$c\" 2>/dev/null; done; }; " +
            "kill_tree $pid"
        runCatching {
            val killer = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            if (!killer.waitFor(5, TimeUnit.SECONDS)) killer.destroyForcibly()
        }
    }

    private fun helper(arguments: String): String =
        "pkill -x owly-k20-helper 2>/dev/null; " +
            "pkill -x owly-monitor-full 2>/dev/null; sleep 1; " +
            "$APP_TMP/owly-k20-helper $arguments"

    private fun helperName(): String =
        settings.deviceName.replace("'", "").ifBlank { "AirDroid K20" }

    private fun append(line: String) {
        if (line.isEmpty()) {
            logs.add("")
        } else {
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val lines = line.split('\n').map { "$stamp  $it" }
            logs.addAll(lines)
            lines.forEach { Log.i("AirDroidLog", it) }
        }
        if (logs.size > 2500) logs.removeRange(0, logs.size - 2000)
    }

    /**
     * Filters high-frequency helper telemetry out of the UI log. Peer frames,
     * progress ticks, and mDNS refreshes can arrive many times per second;
     * their state changes are summarized separately, and writing every raw
     * line makes the visible log and the main thread fall behind.
     */
    private fun appendHelperLine(line: String) {
        if (line.isBlank()) return
        when {
            line.startsWith("PEER ") -> return
            line.startsWith("PEER_GONE ") -> return
            line.startsWith("AWDL peers now") -> return
            line.startsWith("FOUND ") -> return
            line.startsWith("LOST ") -> return
            line.startsWith("SEND_FILE_") -> return
            line.startsWith("SEND_OK ") -> return
            line.startsWith("SEND_ERR ") -> return
            line.startsWith("SEND_CANCELLED ") -> return
            line.startsWith("progress ") -> return
            line.startsWith("RECEIVE_FILE_START ") -> return
            line.startsWith("RECEIVE_FILE_DONE ") -> return
            line.startsWith("RECEIVE_FILE_PROGRESS ") -> return
            line.startsWith("RECEIVE_ACCEPTED ") -> return
            line.startsWith("RECEIVE_ABORTING ") -> return
            line.startsWith("RECEIVE_ABORTED ") -> return
            line.startsWith("RECEIVE_PROGRESS") -> return
            line.startsWith("RECEIVED ") -> return
            line.startsWith("RECEIVE_DONE ") -> return
        }
        append(line)
    }

    /** Strips the `outgoing-<timestamp>-` cache prefix if state was lost. */
    private fun sourceDisplayName(cacheName: String): String {
        if (!cacheName.startsWith("outgoing-")) return cacheName
        val rest = cacheName.removePrefix("outgoing-")
        val separator = rest.indexOf('-')
        return if (separator >= 0) rest.substring(separator + 1) else cacheName
    }

    /**
     * Registers this app process with the kernel monitor watchdog and starts the
     * root Wi-Fi watchdog. Returns false when the owner write or the root
     * watchdog did not take effect. A kernel without owly_owner_pid is logged
     * and stays non-fatal.
     */
    private suspend fun setMonitorOwner(pid: Int): Boolean {
        var ownerNodeAbsent = false
        val ownerRc = execRoot(
            label = "monitor owner",
            command = "if [ -e $MONITOR_OWNER_PATH ]; then echo $pid > $MONITOR_OWNER_PATH; " +
                "else echo 'owner node absent (old kernel)'; fi",
            requirePrepared = false,
            timeoutMs = OWNER_WRITE_TIMEOUT_MS,
        ) { line ->
            if (line.contains("owner node absent")) ownerNodeAbsent = true
        }
        if (ownerRc != 0 && !ownerNodeAbsent) {
            append("Kernel owner write failed; the kernel watchdog is not armed.")
            return false
        }
        if (pid <= 0) {
            execRoot(
                label = "stop Wi-Fi watchdog",
                command = "old=$(cat $WATCHDOG_PID_PATH 2>/dev/null); " +
                    "[ -n \"\$old\" ] && kill \"\$old\" 2>/dev/null; " +
                    "rm -f $WATCHDOG_PID_PATH; true",
                requirePrepared = false,
                timeoutMs = WATCHDOG_TIMEOUT_MS,
            )
            return true
        }
        var started = false
        execRoot(
            label = "start Wi-Fi watchdog",
            command = "old=$(cat $WATCHDOG_PID_PATH 2>/dev/null); " +
                "[ -n \"\$old\" ] && kill \"\$old\" 2>/dev/null; " +
                "rm -f $WATCHDOG_PID_PATH; " +
                "setsid $APP_TMP/owly-watchdog.sh $pid >/dev/null 2>&1 < /dev/null & " +
                "i=0; while [ \$i -lt 20 ]; do " +
                "p=\$(cat $WATCHDOG_PID_PATH 2>/dev/null); " +
                "if [ -n \"\$p\" ] && kill -0 \"\$p\" 2>/dev/null; then " +
                "echo \"watchdog_pid=\$p\"; exit 0; fi; " +
                "sleep 0.1; i=\$((i+1)); done; " +
                "echo watchdog_pid=none; exit 1",
            requirePrepared = false,
            timeoutMs = WATCHDOG_TIMEOUT_MS,
        ) { line ->
            if (line.startsWith("watchdog_pid=") && line != "watchdog_pid=none") started = true
        }
        return started
    }

    /** Reads the live con_mode value through root; null when the read failed. */
    private suspend fun readConMode(): Int? {
        var mode: Int? = null
        val rc = execRoot(
            label = "read con_mode",
            command = "printf 'CON_MODE='; cat /sys/module/wlan/parameters/con_mode 2>/dev/null; echo",
            requirePrepared = false,
            timeoutMs = OWNER_WRITE_TIMEOUT_MS,
        ) { line ->
            if (line.startsWith("CON_MODE=")) mode = line.removePrefix("CON_MODE=").trim().toIntOrNull()
        }
        return if (rc == 0) mode else null
    }

    private fun restoreWifiOnCloseBlocking() {
        if (kernel.state != MonitorState.MONITOR) return
        try {
            ProcessBuilder("su", "-c", "$APP_TMP/owly-mode.sh exit")
                .redirectErrorStream(true)
                .start()
                .waitFor(8, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }

    private fun querySize(uri: Uri): Long? {
        var result: Long? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                result = cursor.getLong(index)
            }
        }
        return result
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

    /** Returns everything after [marker], for fields at the end of a line. */
    private fun after(text: String, marker: String): String {
        val index = text.indexOf(marker)
        if (index < 0) return ""
        return text.substring(index + marker.length).trim()
    }

    /** Reads `id=<value>` up to the next space, independent of field order. */
    private fun eventId(text: String): String {
        val start = text.indexOf("id=")
        if (start < 0) return ""
        val from = start + 3
        val end = text.indexOf(' ', from)
        return if (end < 0) text.substring(from).trim() else text.substring(from, end).trim()
    }

    fun close() {
        restoreWifiOnCloseBlocking()
        if (AirDroidService.running) {
            runCatching { AirDroidService.stop(context) }
        }
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
        activeSendTransferId = null
        incomingRequestNames = emptyList()
        notifications.cancelAllTransfers()
        notifications.cancelIncomingRequest()
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

/** iOS advertises a UUID as its AWDL host name; it is not a display name. */
private val PEER_UUID = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

/** Whether the peer has announced the name a user should see. */
fun Peer.hasDisplayName(): Boolean = name.isNotBlank() && !PEER_UUID.matches(name.trim())

fun Peer.displayName(): String {
    if (hasDisplayName()) return name
    return when (devclass) {
        2 -> "Phone"
        1 -> "Computer"
        else -> "Nearby device"
    }
}

fun Peer.typeLabel(): String = when (devclass) {
    2 -> "Phone"
    1 -> "Computer"
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
