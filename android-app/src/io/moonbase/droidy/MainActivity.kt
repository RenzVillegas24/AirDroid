package io.moonbase.droidy

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    companion object {
        private val ASSETS = arrayOf(
            "owly-nl80211-set-channel",
            "owly-monitor-send-frame",
            "owly-monitor-full",
            "owly-monitor-probe",
            "probe-owly.bin",
            "awdl-mif-k20.bin",
            "owly-k20-helper",
        )
        private const val APP_TMP = "/data/local/tmp/owly-app"
        private const val RECEIVE_DIR = "/sdcard/Download/Droidy"
        private const val PICK_FILE = 4101
        private const val PREFS = "droidy"

        private val PRIMARY = Color.rgb(0x67, 0x50, 0xA4)
        private val ON_PRIMARY = Color.WHITE
        private val PRIMARY_CONTAINER = Color.rgb(0xEA, 0xDD, 0xFF)
        private val ON_PRIMARY_CONTAINER = Color.rgb(0x21, 0x00, 0x5D)
        private val SECONDARY = Color.rgb(0x62, 0x5B, 0x71)
        private val SECONDARY_CONTAINER = Color.rgb(0xE8, 0xDE, 0xF8)
        private val ON_SECONDARY_CONTAINER = Color.rgb(0x1D, 0x19, 0x27)
        private val TERTIARY_CONTAINER = Color.rgb(0xFF, 0xD8, 0xE4)
        private val ON_TERTIARY_CONTAINER = Color.rgb(0x31, 0x11, 0x1F)
        private val SURFACE = Color.rgb(0xFE, 0xF7, 0xFF)
        private val SURFACE_LOW = Color.rgb(0xF7, 0xF2, 0xFA)
        private val SURFACE_VARIANT = Color.rgb(0xE7, 0xE0, 0xEC)
        private val ON_SURFACE = Color.rgb(0x1D, 0x1B, 0x20)
        private val ON_SURFACE_VARIANT = Color.rgb(0x49, 0x45, 0x4F)
        private val OUTLINE = Color.rgb(0x79, 0x74, 0x7E)
        private val SUCCESS = Color.rgb(0x2E, 0x7D, 0x32)
        private val ERROR = Color.rgb(0xB3, 0x26, 0x1E)
    }

    private enum class Tab { SEND, RECEIVE, LOGS, SETTINGS }
    private data class Peer(val mac: String, val name: String, val devclass: Int, val rssi: Int)

    private lateinit var prefs: SharedPreferences
    private lateinit var ble: BleAdvertiser
    private lateinit var binDir: File
    private lateinit var logFile: File
    private lateinit var pageHost: FrameLayout
    private lateinit var statusChip: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var peerList: LinearLayout
    private lateinit var selectedLabel: TextView
    private lateinit var sendStatus: TextView
    private lateinit var receiveStatus: TextView
    private lateinit var receivedList: LinearLayout
    private lateinit var receiveButton: Button
    private lateinit var stopReceiveButton: Button
    private lateinit var channelButton: Button
    private lateinit var bleArmButton: Button
    private lateinit var deviceNameInput: EditText
    private lateinit var navItems: MutableMap<Tab, LinearLayout>
    private lateinit var pages: MutableMap<Tab, View>
    private val assetsReady = CountDownLatch(1)

    private val peers = linkedMapOf<String, Peer>()
    private val receivedFiles = mutableListOf<String>()
    private var selectedMac: String? = null
    private var currentTab = Tab.SEND
    private var channel = 44
    private var bleArm = 0
    private var deviceName = "Droidy K20"
    private var scanning = false
    private var sending = false
    @Volatile private var receiverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        channel = prefs.getInt("channel", 44)
        bleArm = prefs.getInt("bleArm", 0)
        deviceName = prefs.getString("deviceName", "Droidy K20") ?: "Droidy K20"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            root.setPadding(0, dp(24), 0, 0)
        }

        root.addView(topBar())
        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        navItems = mutableMapOf()
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = shape(SURFACE_LOW, 24f, OUTLINE)
        }
        nav.addView(navItem(Tab.SEND, "📤", "Send"), weight())
        nav.addView(navItem(Tab.RECEIVE, "📥", "Receive"), weight())
        nav.addView(navItem(Tab.LOGS, "📜", "Logs"), weight())
        nav.addView(navItem(Tab.SETTINGS, "⚙️", "Settings"), weight())
        root.addView(nav, LinearLayout.LayoutParams.MATCH_PARENT, dp(76))
        setContentView(root)

        pages = mutableMapOf()
        pages[Tab.SEND] = buildSendPage()
        pages[Tab.RECEIVE] = buildReceivePage()
        pages[Tab.LOGS] = buildLogsPage()
        pages[Tab.SETTINGS] = buildSettingsPage()
        for ((_, page) in pages) pageHost.addView(page, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        renderPeers()

        binDir = File(filesDir, "bin")
        logFile = File(filesDir, "owly-radio.log")
        ble = BleAdvertiser(this)

        append("Droidy K20\n")
        append("AirDrop over AWDL · experience mode\n")
        append("Debug log: ${logFile.absolutePath}\n")
        showTab(Tab.SEND)
        extractAssets()
        ensureBlePermissions()
        handleDebugIntent(intent)
    }

    private fun topBar(): View {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(12), dp(20), dp(6))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titles.addView(text("Droidy", 32f, ON_SURFACE, Typeface.BOLD))
        titles.addView(text("AirDrop · over AWDL", 13f, ON_SURFACE_VARIANT, Typeface.NORMAL).apply {
            setPadding(0, dp(2), 0, 0)
        })
        statusChip = text("READY", 11f, ON_PRIMARY_CONTAINER, Typeface.BOLD).apply {
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            background = shape(PRIMARY_CONTAINER, 100f)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        top.addView(titles)
        top.addView(statusChip)
        return top
    }

    private fun navItem(tab: Tab, icon: String, label: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(5))
            background = ripple(Color.TRANSPARENT, 18f)
            setOnClickListener { showTab(tab) }
        }
        item.addView(text(icon, 19f, ON_SURFACE_VARIANT, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
        item.addView(text(label, 11f, ON_SURFACE_VARIANT, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, 0)
        })
        navItems[tab] = item
        return item
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        for ((key, page) in pages) page.visibility = if (key == tab) View.VISIBLE else View.GONE
        for ((key, item) in navItems) {
            val selected = key == tab
            item.background = ripple(
                if (selected) PRIMARY_CONTAINER else Color.TRANSPARENT,
                18f,
            )
            for (i in 0 until item.childCount) {
                (item.getChildAt(i) as? TextView)?.setTextColor(
                    if (selected) ON_PRIMARY_CONTAINER else ON_SURFACE_VARIANT,
                )
            }
        }
    }

    private fun buildSendPage(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(24))
        }
        scroll.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        col.addView(sectionLabel("NEARBY PEOPLE"))
        val peopleCard = card(SURFACE_LOW, ON_SURFACE)
        val peopleHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val peopleTitle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        peopleTitle.addView(text("Who's around", 21f, ON_SURFACE, Typeface.BOLD))
        peopleTitle.addView(text("Tap a person to choose them", 13f, ON_SURFACE_VARIANT, Typeface.NORMAL))
        peopleHead.addView(peopleTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val refresh = actionButton("Refresh", Style.TONAL) { refreshPeers() }
        refresh.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        peopleHead.addView(refresh)
        peopleCard.addView(peopleHead)
        peerList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        peopleCard.addView(peerList)
        col.addView(peopleCard)

        col.addView(sectionLabel("SEND"))
        val sendCard = card(PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER)
        sendCard.addView(text("Send a file", 24f, ON_PRIMARY_CONTAINER, Typeface.BOLD))
        selectedLabel = text("No person selected", 14f, ON_PRIMARY_CONTAINER, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(16))
            alpha = 0.82f
        }
        sendCard.addView(selectedLabel)
        sendCard.addView(actionButton("Choose file & send", Style.FILLED) { chooseFile() })
        sendCard.addView(actionButton("Send owly-test.txt", Style.TONAL) { sendTestFile() })
        sendStatus = text("", 13f, ON_PRIMARY_CONTAINER, Typeface.NORMAL).apply {
            setPadding(0, dp(12), 0, 0)
            alpha = 0.8f
        }
        sendCard.addView(sendStatus)
        col.addView(sendCard)
        return scroll
    }

    private fun buildReceivePage(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(24))
        }
        scroll.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        col.addView(sectionLabel("RECEIVE"))
        val hero = card(SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER)
        val icon = text("📥", 40f, ON_SECONDARY_CONTAINER, Typeface.NORMAL).apply { gravity = Gravity.CENTER }
        hero.addView(icon, LinearLayout.LayoutParams.MATCH_PARENT, dp(58))
        hero.addView(text("Receive mode", 27f, ON_SECONDARY_CONTAINER, Typeface.BOLD))
        hero.addView(text(
            "Let an iPhone discover this phone and send you a file. " +
                "The receiver announces over AWDL and saves everything in " +
                "$RECEIVE_DIR.",
            14f, ON_SECONDARY_CONTAINER, Typeface.NORMAL,
        ).apply {
            setPadding(0, dp(8), 0, dp(16))
            alpha = 0.82f
        })
        receiveStatus = text("Receiver is stopped", 13f, ON_SECONDARY_CONTAINER, Typeface.NORMAL)
        hero.addView(receiveStatus)
        receiveButton = actionButton("Start receiving", Style.FILLED) { startReceiving() }
        stopReceiveButton = actionButton("Stop receiver", Style.OUTLINED) { stopReceiving() }
        hero.addView(receiveButton)
        hero.addView(stopReceiveButton)
        col.addView(hero)

        col.addView(sectionLabel("RECEIVED FILES"))
        val filesCard = card(SURFACE_LOW, ON_SURFACE)
        receivedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        filesCard.addView(receivedList)
        col.addView(filesCard)
        renderReceivedFiles()
        return scroll
    }

    private fun buildLogsPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(ON_SURFACE, 22f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(text("Session log", 18f, Color.WHITE, Typeface.BOLD).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val clear = actionButton("Clear", Style.OUTLINED_DARK) { clearLog() }
        clear.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        head.addView(clear)
        card.addView(head)
        logScroll = ScrollView(this)
        logView = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(0xE6, 0xE0, 0xE6))
            movementMethod = ScrollingMovementMethod()
            setTextIsSelectable(true)
            setPadding(dp(2), dp(10), dp(2), dp(4))
        }
        logScroll.addView(logView)
        card.addView(logScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private fun buildSettingsPage(): View {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(24))
        }
        scroll.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        col.addView(sectionLabel("CONNECTION"))
        val conn = card(SURFACE_LOW, ON_SURFACE)
        conn.addView(text("Radio & wake", 21f, ON_SURFACE, Typeface.BOLD))
        conn.addView(text(
            "The K20 monitor is fixed to one AWDL channel. Match the iPhone's channel " +
                "or the router's 5 GHz channel. The BLE arm is the wake advertisement shape.",
            13f, ON_SURFACE_VARIANT, Typeface.NORMAL,
        ).apply { setPadding(0, dp(6), 0, dp(14)) })
        channelButton = actionButton(channelLabel(), Style.TONAL) {
            channel = when (channel) { 44 -> 6; 6 -> 149; else -> 44 }
            channelButton.text = channelLabel()
        }
        conn.addView(channelButton)
        bleArmButton = actionButton(bleArmLabel(), Style.TONAL) {
            bleArm = (bleArm + 1) % 3
            bleArmButton.text = bleArmLabel()
        }
        conn.addView(bleArmButton)
        col.addView(conn)

        col.addView(sectionLabel("IDENTITY"))
        val identity = card(SURFACE_LOW, ON_SURFACE)
        identity.addView(text("Device name", 21f, ON_SURFACE, Typeface.BOLD))
        identity.addView(text("Shown in the AirDrop prompt and receiver records.", 13f, ON_SURFACE_VARIANT, Typeface.NORMAL)
            .apply { setPadding(0, dp(6), 0, dp(10)) })
        deviceNameInput = EditText(this).apply {
            setText(deviceName)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(ON_SURFACE)
            setHintTextColor(ON_SURFACE_VARIANT)
            background = shape(SURFACE_VARIANT, 16f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        identity.addView(deviceNameInput)
        identity.addView(actionButton("Save settings", Style.FILLED) {
            deviceName = deviceNameInput.text.toString().ifBlank { "Droidy K20" }
            prefs.edit().putInt("channel", channel).putInt("bleArm", bleArm)
                .putString("deviceName", deviceName).apply()
            sendStatus.text = "Settings saved."
            setStatus("READY")
        })
        col.addView(identity)

        col.addView(sectionLabel("RECEIVER"))
        val receiver = card(TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER)
        receiver.addView(text("Incoming storage", 21f, ON_TERTIARY_CONTAINER, Typeface.BOLD))
        receiver.addView(text("$RECEIVE_DIR\nPort 8771 · mDNS service droidy-k20", 13f, ON_TERTIARY_CONTAINER, Typeface.NORMAL)
            .apply { setPadding(0, dp(6), 0, dp(8)) })
        col.addView(receiver)

        col.addView(sectionLabel("ABOUT"))
        val about = card(SURFACE_VARIANT, ON_SURFACE)
        about.addView(text("Droidy K20", 21f, ON_SURFACE, Typeface.BOLD))
        about.addView(text("AirDrop over AWDL · internal WCN3990 radio\nSender and receiver UI", 13f, ON_SURFACE_VARIANT, Typeface.NORMAL)
            .apply { setPadding(0, dp(6), 0, 0) })
        col.addView(about)
        return scroll
    }

    private fun renderPeers() {
        peerList.removeAllViews()
        if (peers.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(24), dp(8), dp(24))
            }
            empty.addView(text("📡", 32f, PRIMARY, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
            empty.addView(text("No one nearby yet", 16f, ON_SURFACE, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            empty.addView(text("Tap Refresh to scan AWDL.", 13f, ON_SURFACE_VARIANT, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
            })
            peerList.addView(empty)
            return
        }
        for (peer in peers.values.sortedByDescending { it.rssi }) peerList.addView(peerCard(peer))
    }

    private fun peerCard(peer: Peer): View {
        val selected = peer.mac == selectedMac
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(
                if (selected) PRIMARY_CONTAINER else SURFACE,
                20f,
                if (selected) PRIMARY else OUTLINE,
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(4)) }
            setOnClickListener {
                selectedMac = peer.mac
                renderPeers()
                updateSelectedLabel()
            }
        }
        val avatar = text(PeerIcon(peer.devclass), 25f, if (selected) ON_PRIMARY_CONTAINER else ON_SURFACE, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            background = shape(
                if (peer.devclass == 2) TERTIARY_CONTAINER else SECONDARY_CONTAINER,
                100f,
            )
        }
        card.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)))
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        names.addView(text(peer.displayName(), 17f, if (selected) ON_PRIMARY_CONTAINER else ON_SURFACE, Typeface.BOLD))
        names.addView(text("${peer.typeLabel()} · ${peer.mac}", 11f, if (selected) ON_PRIMARY_CONTAINER else ON_SURFACE_VARIANT, Typeface.NORMAL).apply {
            setPadding(0, dp(2), 0, 0)
            alpha = 0.78f
        })
        card.addView(names)
        card.addView(text("${peer.rssi} dBm", 12f, if (selected) PRIMARY else ON_SURFACE_VARIANT, Typeface.BOLD))
        if (selected) card.addView(text("✓", 18f, PRIMARY, Typeface.BOLD).apply { setPadding(dp(8), 0, 0, 0) })
        return card
    }

    private fun updateSelectedLabel() {
        val peer = selectedMac?.let { peers[it] }
        selectedLabel.text = if (peer == null) "No person selected" else "Sending to ${peer.displayName()} · ${peer.mac}"
    }

    private fun refreshPeers() {
        if (scanning) return
        scanning = true
        setStatus("SCANNING")
        peers.clear()
        selectedMac = null
        renderPeers()
        updateSelectedLabel()
        startBle(fresh = true)
        append("\nScanning for AirDrop people...\n")
        runRoot(
            "scan peers",
            helperCommand("scan 12 $channel"),
            onLine = ::handlePeerLine,
            onDone = {
                scanning = false
                runOnUiThread {
                    if (peers.isEmpty()) {
                        sendStatus.text = "No people found. Keep the iPhone nearby and AirDrop open."
                        setStatus("NO PEERS")
                    } else {
                        sendStatus.text = "${peers.size} person(s) found."
                        setStatus("READY")
                    }
                }
            },
        )
    }

    private fun handlePeerLine(line: String) {
        if (line.startsWith("PEER ")) {
            val mac = between(line, "mac=", " name=")
            if (mac.isBlank()) return
            val name = between(line, "name=", " devclass=")
            val devclass = between(line, "devclass=", " rssi=").toIntOrNull() ?: 0
            val rssi = line.substringAfter("rssi=").trim().toIntOrNull() ?: 0
            runOnUiThread {
                peers[mac] = Peer(mac, name, devclass, rssi)
                if (selectedMac == null) selectedMac = mac
                renderPeers()
                updateSelectedLabel()
            }
        } else if (line.startsWith("AWDL name:")) {
            val rest = line.removePrefix("AWDL name:").trim()
            val mac = rest.substringBefore("=").trim()
            val name = rest.substringAfter("=").trim()
            if (mac.isNotBlank() && name.isNotBlank()) {
                runOnUiThread {
                    peers[mac]?.let {
                        peers[mac] = it.copy(name = name)
                        renderPeers()
                        updateSelectedLabel()
                    }
                }
            }
        }
    }

    private fun chooseFile() {
        if (selectedMac == null) {
            sendStatus.text = "Select a person first."
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_FILE || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (selectedMac == null) {
            sendStatus.text = "Select a person first."
            return
        }
        setStatus("PREPARING FILE")
        thread {
            try {
                val out = File(filesDir, "outgoing.bin")
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) error("could not open selected file")
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                append("selected file copied to ${out.absolutePath}\n")
                sendPath(out.absolutePath)
            } catch (e: Exception) {
                append("file preparation failed: $e\n")
                setStatus("FILE ERROR")
            }
        }
    }

    private fun sendTestFile() {
        sendPath("/sdcard/Download/owly-test.txt")
    }

    private fun sendPath(path: String) {
        if (sending) return
        val target = selectedMac
        if (target == null) {
            sendStatus.text = "Select a person first."
            return
        }
        if (!File(path).isFile()) {
            sendStatus.text = "File not found: $path"
            return
        }
        sending = true
        setStatus("SENDING")
        startBle(fresh = true)
        sendStatus.text = "Sending ${File(path).name} to ${peers[target]?.displayName() ?: target}..."
        append("\nSending ${File(path).name} to $target\n")
        runRoot(
            "send file",
            helperCommand("send '$path' '$target' $channel '${helperName()}'"),
            onLine = { line ->
                if (line.startsWith("progress") || line.startsWith("receiver answered") ||
                    line.startsWith("receiver accepted") || line.startsWith("DONE") ||
                    line.startsWith("send failed")) {
                    runOnUiThread { sendStatus.text = line.trim() }
                }
            },
            onDone = {
                sending = false
                setStatus("READY")
            },
        )
    }

    private fun startReceiving() {
        if (receiverRunning) return
        receiverRunning = true
        setStatus("RECEIVING")
        receiveStatus.text = "Announcing on AWDL · waiting for a sender"
        receiveButton.isEnabled = false
        append("\nStarting AirDrop receiver on port 8771...\n")
        runRoot(
            "receive mode",
            helperCommand("receive '$RECEIVE_DIR' 8771 $channel '${helperName()}'"),
            onLine = ::handleReceiveLine,
            onDone = {
                receiverRunning = false
                runOnUiThread {
                    receiveButton.isEnabled = true
                    receiveStatus.text = "Receiver stopped"
                    setStatus("READY")
                }
            },
        )
    }

    private fun stopReceiving() {
        receiverRunning = false
        receiveStatus.text = "Stopping receiver..."
        runRoot("stop receiver", "pkill -x owly-k20-helper 2>/dev/null; echo receiver stopped")
        receiveButton.isEnabled = true
        setStatus("READY")
    }

    private fun handleReceiveLine(line: String) {
        if (line.startsWith("RECEIVE_READY")) {
            runOnUiThread {
                receiveStatus.text = "Receiving · ${line.removePrefix("RECEIVE_READY").trim()}"
                setStatus("RECEIVING")
            }
        } else if (line.startsWith("RECEIVE_REQUEST")) {
            runOnUiThread {
                receiveStatus.text = "Incoming request · accepting"
                setStatus("INCOMING")
            }
        } else if (line.startsWith("RECEIVED ")) {
            val path = line.substringAfter("path=").trim()
            if (path.isNotBlank()) {
                runOnUiThread {
                    receivedFiles.add(0, path)
                    renderReceivedFiles()
                    receiveStatus.text = "Received ${File(path).name}"
                    setStatus("RECEIVED")
                }
            }
        } else if (line.startsWith("RECEIVE_FAILED")) {
            runOnUiThread {
                receiveStatus.text = line
                setStatus("RECEIVE ERROR")
            }
        }
    }

    private fun renderReceivedFiles() {
        receivedList.removeAllViews()
        if (receivedFiles.isEmpty()) {
            receivedList.addView(text("Nothing received yet. Start receiving and send from an iPhone.", 13f, ON_SURFACE_VARIANT, Typeface.NORMAL).apply {
                setPadding(0, dp(12), 0, dp(8))
            })
            return
        }
        for ((index, path) in receivedFiles.withIndex()) {
            if (index > 0) receivedList.addView(divider())
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(text("📄", 24f, PRIMARY, Typeface.NORMAL))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(text(File(path).name, 15f, ON_SURFACE, Typeface.BOLD))
            info.addView(text(path, 10f, ON_SURFACE_VARIANT, Typeface.NORMAL))
            row.addView(info)
            receivedList.addView(row)
        }
    }

    private fun clearLog() {
        logView.text = ""
        try {
            if (logFile.exists()) logFile.delete()
        } catch (_: Exception) {
        }
        append("Log cleared.\n")
    }

    private fun ensureBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), 4102)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4102) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                append("BLE permissions granted.\n")
            } else {
                append("BLE permissions denied; wake advertisement unavailable.\n")
            }
        }
    }

    private fun startBle(fresh: Boolean = false) {
        thread {
            val manager = getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = manager?.adapter
            if (adapter != null && !adapter.isEnabled) {
                runRoot("enable bluetooth", "cmd bluetooth_manager enable; sleep 1")
                Thread.sleep(1500)
            }
            val ok = if (fresh) {
                ble.restart(bleArm, true) { line -> append(line + "\n") }
            } else if (!ble.isRunning()) {
                ble.start(bleArm, true) { line -> append(line + "\n") }
            } else true
            if (!ok) {
                append("BLE wake advertisement not running.\n")
                return@thread
            }
            var waited = 0
            while (!ble.isRunning() && waited < 2000) {
                Thread.sleep(100)
                waited += 100
            }
        }
    }

    private fun extractAssets() {
        thread {
            try {
                try {
                    ProcessBuilder("su", "-c", "pkill -x owly-k20-helper 2>/dev/null; sleep 1; true")
                        .redirectErrorStream(true).start().waitFor(4, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
                if (!binDir.exists() && !binDir.mkdirs()) {
                    append("Could not create ${binDir.absolutePath}\n")
                    return@thread
                }
                for (name in ASSETS) {
                    val out = File(binDir, name)
                    assets.open(name).use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                }
                runRoot(
                    "install root tools",
                    "mkdir -p $APP_TMP; cp '${binDir.absolutePath}'/* $APP_TMP/; chmod 755 $APP_TMP/*; ls -l $APP_TMP",
                )
            } catch (e: Exception) {
                append("Asset install failed: $e\n")
                assetsReady.countDown()
            }
        }
    }

    private fun helperName(): String = deviceName.replace("'", "").ifBlank { "Droidy K20" }

    private fun helperCommand(arguments: String): String =
        "pkill -x owly-k20-helper 2>/dev/null; sleep 1; $APP_TMP/owly-k20-helper $arguments"

    private fun runRoot(
        label: String,
        command: String,
        onLine: ((String) -> Unit)? = null,
        onDone: (() -> Unit)? = null,
    ) {
        append("\n\$ $label\n")
        thread {
            try {
                if (label != "install root tools") assetsReady.await(10, TimeUnit.SECONDS)
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        append("$line\n")
                        onLine?.invoke(line)
                    }
                }
                val rc = process.waitFor()
                append("[exit $rc]\n")
                runOnUiThread { setStatus(if (rc == 0) "READY" else "CHECK LOG") }
            } catch (e: Exception) {
                append("ERROR: $e\nIs KernelSU root granted?\n")
                runOnUiThread { setStatus("ROOT ERROR") }
            } finally {
                if (label == "install root tools") assetsReady.countDown()
                onDone?.invoke()
            }
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread {
            statusChip.text = text.uppercase()
            statusChip.background = shape(
                when (text) {
                    "SENDING" -> TERTIARY_CONTAINER
                    "RECEIVING", "INCOMING", "RECEIVED" -> SECONDARY_CONTAINER
                    "ERROR", "ROOT ERROR", "KEEP LOG", "CHECK LOG", "NO PEERS" -> Color.rgb(0xFF, 0xDA, 0xD6)
                    else -> PRIMARY_CONTAINER
                },
                100f,
            )
            statusChip.setTextColor(if (text == "ERROR" || text == "CHECK LOG" || text == "ROOT ERROR") ERROR else ON_PRIMARY_CONTAINER)
        }
    }

    private fun append(text: String) {
        runOnUiThread {
            logView.append(text)
            if (logView.length() > 50000) {
                logView.text = logView.text.subSequence(logView.length() - 30000, logView.length())
            }
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            try {
                logFile.appendText(text)
            } catch (_: Exception) {
            }
        }
    }

    private fun configureEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = SURFACE
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        val cmd = intent?.getStringExtra("cmd") ?: return
        val path = intent.getStringExtra("path")
        when (cmd) {
            "scan" -> refreshPeers()
            "send" -> if (path != null) sendPath(path) else sendTestFile()
            "receive" -> startReceiving()
            "stopreceive" -> stopReceiving()
            "settings" -> showTab(Tab.SETTINGS)
            "logs" -> showTab(Tab.LOGS)
            "ble" -> {
                bleArm = intent?.getIntExtra("arm", 0) ?: 0
                ble.restart(bleArm, true) { line -> append(line + "\n") }
            }
            "log" -> append("log file: ${logFile.absolutePath}\n")
            else -> append("unknown debug command: $cmd\n")
        }
    }

    override fun onDestroy() {
        ble.stop()
        super.onDestroy()
    }

    private fun text(value: String, size: Float, color: Int, style: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", style)
        }

    private fun sectionLabel(value: String): TextView =
        text(value, 11f, ON_SURFACE_VARIANT, Typeface.BOLD).apply {
            letterSpacing = 0.12f
            setPadding(dp(6), dp(18), 0, dp(8))
        }

    private fun card(color: Int, onColor: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(color, 26f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }

    private fun channelLabel(): String = when (channel) {
        6 -> "Channel 6 · 2.4 GHz"
        149 -> "Channel 149 · 5 GHz"
        else -> "Channel 44 · 5 GHz"
    }

    private fun bleArmLabel(): String = when (bleArm) {
        1 -> "BLE arm V2 (iPhone fallback)"
        2 -> "BLE arm B (legacy fallback)"
        else -> "BLE arm A (Everyone)"
    }

    private fun actionButton(label: String, style: Style, onClick: () -> Unit): Button {
        val (bg, fg) = when (style) {
            Style.FILLED -> PRIMARY to ON_PRIMARY
            Style.TONAL -> Color.argb(46, 0x67, 0x50, 0xA4) to ON_SURFACE
            Style.OUTLINED -> Color.TRANSPARENT to ON_SURFACE
            Style.OUTLINED_DARK -> Color.TRANSPARENT to Color.WHITE
        }
        val fill = shape(bg, 100f, if (style == Style.OUTLINED || style == Style.OUTLINED_DARK) OUTLINE else null)
        val ripple = RippleDrawable(ColorStateList.valueOf(Color.argb(30, 0, 0, 0)), fill, null)
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(fg)
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minWidth = 0
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = ripple
            stateListAnimator = null
            elevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(5), 0, 0) }
            setOnClickListener { onClick() }
        }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(Color.argb(28, 0x1D, 0x1B, 0x20))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(2), 0, dp(2))
        }
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

    private fun ripple(color: Int, radius: Float, stroke: Int? = null): RippleDrawable {
        val fill = shape(color, radius, stroke)
        return RippleDrawable(ColorStateList.valueOf(Color.argb(28, 0, 0, 0)), fill, null)
    }

    private fun shape(color: Int, radius: Float, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun between(text: String, start: String, end: String): String {
        val a = text.indexOf(start)
        if (a < 0) return ""
        val from = a + start.length
        val b = text.indexOf(end, from)
        return if (b < 0) text.substring(from).trim() else text.substring(from, b).trim()
    }

    private fun Peer.displayName(): String {
        if (name.isNotBlank() && !name.matches(Regex("[0-9a-fA-F-]{36}"))) return name
        return when (devclass) {
            2 -> "iPhone"
            1 -> "Computer"
            else -> "Nearby device"
        }
    }

    private fun Peer.typeLabel(): String = when (devclass) {
        2 -> "iPhone"
        1 -> "Computer"
        else -> "AirDrop device"
    }

    private fun PeerIcon(devclass: Int): String = when (devclass) {
        2 -> "📱"
        1 -> "💻"
        else -> "📡"
    }

    private enum class Style { FILLED, TONAL, OUTLINED, OUTLINED_DARK }

}
