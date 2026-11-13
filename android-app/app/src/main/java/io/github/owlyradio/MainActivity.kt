package io.github.owlyradio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            DroidyTheme {
                val context = LocalContext.current
                val controller = remember { DroidyController(context.applicationContext) }
                val scope = rememberCoroutineScope()
                val permissions = remember {
                    val requested = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= 33) {
                        requested.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        requested.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        requested.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    requested
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { }
                LaunchedEffect(Unit) {
                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                }
                DisposableEffect(Unit) {
                    onDispose { controller.close() }
                }
                DroidyApp(controller = controller, scope = scope)
            }
        }
    }
}

private enum class DroidyTab(
    val label: String,
    @DrawableRes val icon: Int,
) {
    SEND("Send", R.drawable.ic_send),
    RECEIVE("Receive", R.drawable.ic_download),
    LOGS("Logs", R.drawable.ic_receipt_long),
    SETTINGS("Settings", R.drawable.ic_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DroidyApp(controller: DroidyController, scope: CoroutineScope) {
    var tab by remember { mutableStateOf(DroidyTab.SEND) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DroidyTopBar(controller) },
        bottomBar = { DroidyFloatingToolbar(tab) { tab = it } },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                DroidyTab.SEND -> SendScreen(controller, scope)
                DroidyTab.RECEIVE -> ReceiveScreen(controller, scope)
                DroidyTab.LOGS -> LogsScreen(controller)
                DroidyTab.SETTINGS -> SettingsScreen(controller)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DroidyTopBar(controller: DroidyController) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi_tethering),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Droidy",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "AirDrop over AWDL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            StatusPill(controller.effectiveStatus)
            Spacer(Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun StatusPill(status: String) {
    val colors = when (status) {
        "SENDING" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "RECEIVING", "INCOMING", "RECEIVED" ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "NO PEERS", "CHECK LOG", "ROOT ERROR", "RECEIVE ERROR",
        "STOCK KERNEL", "VERIFY KERNEL" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = CircleShape, color = colors.first) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.second.copy(alpha = 0.7f), CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.second,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DroidyFloatingToolbar(selected: DroidyTab, onSelected: (DroidyTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            shape = FloatingToolbarDefaults.ContainerShape,
            expandedShadowElevation = 6.dp,
            collapsedShadowElevation = 3.dp,
        ) {
            DroidyTab.entries.forEach { tab ->
                val isSelected = selected == tab
                IconButton(
                    onClick = { onSelected(tab) },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                ) {
                    Icon(
                        painter = painterResource(tab.icon),
                        contentDescription = tab.label,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SendScreen(controller: DroidyController, scope: CoroutineScope) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { controller.pickFile(uri) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NearbyPeopleHeader(controller, scope) }
        if (!controller.kernel.monitorReady) {
            item { KernelBanner(controller) }
        }
        if (controller.scanning) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
        if (controller.peers.isEmpty()) {
            item { EmptyPeopleCard(controller, scope) }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(controller.peers, key = { it.mac }) { peer ->
                        PeerCard(
                            peer = peer,
                            selected = controller.selectedMac == peer.mac,
                            onClick = { controller.selectPeer(peer) },
                        )
                    }
                }
            }
        }
        item { SectionLabel("SEND") }
        item {
            SendCard(
                controller = controller,
                onChooseFile = { picker.launch(arrayOf("*/*")) },
                onSend = { scope.launch { controller.sendPicked() } },
                onCancelSend = { controller.cancelSend() },
            )
        }
    }
}

@Composable
private fun NearbyPeopleHeader(controller: DroidyController, scope: CoroutineScope) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nearby people",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (controller.scanning) {
                    "Scanning AWDL..."
                } else {
                    "Choose someone, then send a file."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = { scope.launch { controller.scan() } },
            enabled = controller.kernel.monitorReady && !controller.scanning &&
                !controller.sending && !controller.sendCancelling && !controller.receiverRunning,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (controller.scanning) "Scanning" else "Scan")
        }
    }
}

@Composable
private fun EmptyPeopleCard(controller: DroidyController, scope: CoroutineScope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wifi_tethering),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "No one nearby yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Open AirDrop on the iPhone, then scan from this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { scope.launch { controller.scan() } },
                enabled = controller.kernel.monitorReady && !controller.scanning &&
                    !controller.sending && !controller.sendCancelling && !controller.receiverRunning,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Scan for people")
            }
        }
    }
}

@Composable
private fun PeerCard(peer: Peer, selected: Boolean, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme
    val avatarColor = when (peer.devclass) {
        2 -> surface.tertiaryContainer
        1 -> surface.secondaryContainer
        else -> surface.primaryContainer
    }
    val avatarTint = when (peer.devclass) {
        2 -> surface.onTertiaryContainer
        1 -> surface.onSecondaryContainer
        else -> surface.onPrimaryContainer
    }
    val icon = when (peer.devclass) {
        2 -> R.drawable.ic_smartphone
        1 -> R.drawable.ic_computer
        else -> R.drawable.ic_wifi_tethering
    }
    val contentColor = if (selected) surface.onPrimaryContainer else surface.onSurface
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.width(174.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) surface.primaryContainer else surface.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (selected) 0.dp else 1.dp,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = avatarColor,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = avatarTint,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = "Selected",
                        tint = surface.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = peer.displayName(),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = peer.typeLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) contentColor.copy(alpha = 0.72f) else surface.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_sensors),
                    contentDescription = null,
                    tint = if (selected) contentColor.copy(alpha = 0.72f) else surface.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${peer.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) contentColor.copy(alpha = 0.72f) else surface.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KernelBanner(controller: DroidyController) {
    val kernel = controller.kernel
    if (kernel.monitorReady) return
    val colors = MaterialTheme.colorScheme
    val title = when (kernel.state) {
        MonitorState.STOCK -> "Stock kernel detected"
        MonitorState.UNVERIFIED -> "Kernel not verified"
        MonitorState.MONITOR -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.errorContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = colors.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onErrorContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${kernel.detail} Flash boot-owly-monitor-fixed.img before scanning or receiving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer.copy(alpha = 0.82f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Kernel ${kernel.release}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onErrorContainer.copy(alpha = 0.66f),
                )
                TextButton(
                    onClick = { controller.verifyKernelWithRoot() },
                    enabled = !controller.verifyingKernel,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_memory),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (controller.verifyingKernel) "Checking with root..." else "Verify with root")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendCard(
    controller: DroidyController,
    onChooseFile: () -> Unit,
    onSend: () -> Unit,
    onCancelSend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val target = controller.selectedPeer
    val picked = controller.pickedFile
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Send a file",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onPrimaryContainer,
                    )
                    Text(
                        text = target?.let { "To ${it.displayName()}" } ?: "Select a person above",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                }
            }
            if (picked != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surface.copy(alpha = 0.58f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_description),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = picked.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = picked.readableSize(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimaryContainer.copy(alpha = 0.68f),
                            )
                        }
                        IconButton(onClick = { controller.clearPickedFile() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Remove file",
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onChooseFile,
                enabled = !controller.sending && !controller.sendCancelling && !controller.scanning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_attach_file),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (picked == null) "Choose file" else "Choose another file")
            }
            Button(
                onClick = onSend,
                enabled = picked != null && target != null &&
                    controller.kernel.monitorReady && !controller.sending &&
                    !controller.sendCancelling && !controller.scanning && !controller.receiverRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        target != null && picked != null -> "Send to ${target.displayName()}"
                        target == null -> "Select a person"
                        else -> "Choose a file"
                    },
                )
            }
            if (controller.sending || controller.sendCancelling) {
                val percent = controller.sendProgressPercent
                if (percent != null && !controller.sendCancelling) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.CenterHorizontally),
                        color = colors.primary,
                    )
                }
                if (controller.sendProgressTotal > 0) {
                    Text(
                        text = "${percent ?: 0}% · " +
                            "${controller.sendProgressBytes.readableBytes()} / " +
                            controller.sendProgressTotal.readableBytes(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                OutlinedButton(
                    onClick = onCancelSend,
                    enabled = !controller.sendCancelling,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, colors.onPrimaryContainer.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stop_circle),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (controller.sendCancelling) "Cancelling..." else "Cancel send")
                }
            }
            if (controller.sendMessage.isNotBlank()) {
                Text(
                    text = controller.sendMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReceiveScreen(controller: DroidyController, scope: CoroutineScope) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Receive",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                )
                Text(
                    text = "Accept a file from an iPhone into a local folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (!controller.kernel.monitorReady) {
            item { KernelBanner(controller) }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primary,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Text(
                        text = "Receive mode",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSecondaryContainer,
                    )
                    Text(
                        text = "The receiver announces this phone over AWDL. " +
                            "Files are saved to ${DroidyController.RECEIVE_DIR}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSecondaryContainer.copy(alpha = 0.78f),
                    )
                    Text(
                        text = controller.receiveMessage,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSecondaryContainer,
                    )
                    if (controller.receiverRunning) {
                        val percent = controller.receiveProgressPercent
                        if (controller.receiveTransferring && percent != null) {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LoadingIndicator(
                                modifier = Modifier
                                    .size(44.dp)
                                    .align(Alignment.CenterHorizontally),
                                color = colors.primary,
                            )
                        }
                        if (controller.receiveTransferring) {
                            Text(
                                text = if (percent != null) {
                                    "$percent% · " +
                                        "${controller.receiveProgressBytes.readableBytes()} / " +
                                        controller.receiveProgressTotal.readableBytes()
                                } else {
                                    "Received ${controller.receiveProgressBytes.readableBytes()} so far"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { scope.launch { controller.startReceiving() } },
                            enabled = controller.kernel.monitorReady && !controller.receiverRunning &&
                                !controller.scanning && !controller.sending && !controller.sendCancelling,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Start")
                        }
                        OutlinedButton(
                            onClick = { scope.launch { controller.stopReceiving() } },
                            enabled = controller.receiverRunning && !controller.receiverStarting &&
                                !controller.receiverStopping,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, colors.onSecondaryContainer.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.onSecondaryContainer,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stop_circle),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (controller.receiverStopping) "Stopping..." else "Stop")
                        }
                    }
                }
            }
        }
        val incoming = controller.incomingRequest
        if (incoming != null) {
            item { IncomingRequestCard(controller, incoming) }
        }
        item { SectionLabel("RECEIVED FILES") }
        if (controller.receivedFiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Nothing received yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Start the receiver and send from AirDrop.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            items(controller.receivedFiles) { path ->
                ReceivedFileCard(path)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IncomingRequestCard(controller: DroidyController, request: IncomingRequest) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = colors.tertiary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_smartphone),
                            contentDescription = null,
                            tint = colors.onTertiary,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Incoming file",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onTertiaryContainer,
                    )
                    Text(
                        text = "From ${request.sender.ifBlank { request.peer }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Text(
                text = buildString {
                    append(if (request.fileCount == 1) "1 file" else "${request.fileCount} files")
                    if (request.totalBytes > 0) {
                        append(" · ")
                        append(request.totalBytes.readableBytes())
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onTertiaryContainer.copy(alpha = 0.85f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { controller.acceptIncoming() },
                    enabled = !controller.receiveDecisionPending,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = { controller.declineIncoming() },
                    enabled = !controller.receiveDecisionPending,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, colors.onTertiaryContainer.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.onTertiaryContainer,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Decline")
                }
            }
            if (controller.receiveDecisionPending) {
                LoadingIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterHorizontally),
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun ReceivedFileCard(path: String) {
    val file = File(path)
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = colors.tertiaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_description),
                        contentDescription = null,
                        tint = colors.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LogsScreen(controller: DroidyController) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(controller.logs.size) {
        if (controller.logs.isNotEmpty()) {
            listState.scrollToItem(controller.logs.lastIndex)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.inverseSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_receipt_long),
                    contentDescription = null,
                    tint = colors.inverseOnSurface,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Session log",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inverseOnSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { controller.clearLogs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = colors.inverseOnSurface.copy(alpha = 0.14f),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(controller.logs) { line ->
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colors.inverseOnSurface.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(controller: DroidyController) {
    val colors = MaterialTheme.colorScheme
    var channel by remember { mutableIntStateOf(controller.settings.channel) }
    var bleArm by remember { mutableIntStateOf(controller.settings.bleArm) }
    var deviceName by remember { mutableStateOf(controller.settings.deviceName) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                )
                Text(
                    text = "Radio, wake advertisement, and receiver identity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item { SectionLabel("CONNECTION") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cell_tower),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AWDL channel",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Fixed monitor channel on the WCN3990 radio",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(44, 6, 149).forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = channel == option,
                            onClick = { channel = option },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            label = {
                                Text(
                                    when (option) {
                                        6 -> "6 · 2.4"
                                        149 -> "149 · 5"
                                        else -> "44 · 5"
                                    },
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bluetooth),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "BLE wake arm",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Apple Continuity advertisement shape",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("A", "V2", "B").forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = bleArm == index,
                            onClick = { bleArm = index },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            label = { Text(option) },
                        )
                    }
                }
            }
        }
        item { SectionLabel("IDENTITY") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_badge),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Device name",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Display name") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi_tethering),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { controller.saveSettings(channel, bleArm, deviceName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Save settings")
                }
                if (controller.sendMessage.startsWith("Settings saved")) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = controller.sendMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                }
            }
        }
        item { SectionLabel("RECEIVER") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            ) {
                ListItem(
                    headlineContent = { Text("Incoming storage") },
                    supportingContent = { Text(DroidyController.RECEIVE_DIR) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = colors.primary,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(color = colors.outlineVariant)
                ListItem(
                    headlineContent = { Text("AirDrop port") },
                    supportingContent = { Text("8771 · AWDL link-local") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_sensors),
                            contentDescription = null,
                            tint = colors.primary,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item { SectionLabel("SYSTEM") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_memory),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monitor kernel",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = when (controller.kernel.state) {
                                MonitorState.MONITOR -> "Monitor boot active"
                                MonitorState.STOCK -> "Stock boot detected"
                                MonitorState.UNVERIFIED -> "Unknown boot image"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (controller.kernel.state) {
                                MonitorState.MONITOR -> colors.primary
                                MonitorState.STOCK -> colors.error
                                MonitorState.UNVERIFIED -> colors.tertiary
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = controller.kernel.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Release ${controller.kernel.release}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                if (!controller.kernel.monitorReady) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { controller.verifyKernelWithRoot() },
                        enabled = !controller.verifyingKernel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_memory),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (controller.verifyingKernel) "Checking with root..." else "Verify with root")
                    }
                } else if (controller.kernel.verifiedBy == "root") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Verified from /proc/cmdline",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }
        }
        item { SectionLabel("ABOUT") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Droidy K20",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "AirDrop over AWDL · internal WCN3990 radio",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Jetpack Compose · Material 3",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            this.content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}
