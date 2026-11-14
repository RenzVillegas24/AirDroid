package io.moonbase.airdroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var controller: AirDroidController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = AirDroidControllerHolder.current
            ?: AirDroidController(applicationContext).also { AirDroidControllerHolder.current = it }
        handleNotificationIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            val context = LocalContext.current
            AirDroidTheme(
                themeMode = controller.settings.themeMode,
                tintMode = controller.settings.tintMode,
            ) {
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
                AirDroidApp(controller = controller, scope = scope)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        controller.setForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // While the foreground service is running it owns the controller, so
        // closing it here would stop the background receiver.
        if (AirDroidService.running) return
        if (AirDroidControllerHolder.current === controller) {
            AirDroidControllerHolder.current = null
        }
        controller.close()
    }

    /**
     * Notification actions open MainActivity (there is no background service
     * or receiver to answer Accept/Decline directly). The extras are applied
     * once the controller state is available and then cleared.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val cancelId = intent.getStringExtra(AirDroidNotifications.EXTRA_CANCEL_TRANSFER)
        if (cancelId != null) {
            controller.cancelTransfer(cancelId)
            intent.removeExtra(AirDroidNotifications.EXTRA_CANCEL_TRANSFER)
        }
        val requestId = intent.getLongExtra(AirDroidNotifications.EXTRA_REQUEST_ID, -1L)
        if (requestId >= 0) {
            val accept = intent.getBooleanExtra(AirDroidNotifications.EXTRA_REQUEST_ACCEPT, false)
            controller.resolveIncomingFromNotification(requestId, accept)
            intent.removeExtra(AirDroidNotifications.EXTRA_REQUEST_ID)
            intent.removeExtra(AirDroidNotifications.EXTRA_REQUEST_ACCEPT)
        }
    }
}

private enum class AirDroidTab(
    val label: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
) {
    HOME("AirDroid", "Nearby AirDrop and received files", R.drawable.ic_airdroid_home),
    LOGS("Logs", "Live radio and session output", R.drawable.ic_receipt_long),
    SETTINGS("Settings", "Radio, appearance, and receiver identity", R.drawable.ic_settings),
}

private enum class PeerAction(val label: String, @DrawableRes val icon: Int) {
    FILES("Files", R.drawable.ic_attach_file),
    PHOTOS("Photos", R.drawable.ic_upload_file),
    VIDEOS("Videos", R.drawable.ic_smartphone),
    FOLDER("Folder", R.drawable.ic_folder),
    CAMERA("Camera", R.drawable.ic_smartphone),
    AUDIO("Audio", R.drawable.ic_send),
    PASTE("Paste", R.drawable.ic_description),
}

private enum class HomeTab(val label: String) {
    SENT("Sent"),
    RECEIVED("Received"),
}

private val TOOLBAR_TABS = listOf(AirDroidTab.LOGS, AirDroidTab.HOME, AirDroidTab.SETTINGS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirDroidApp(controller: AirDroidController, scope: CoroutineScope) {
    var tab by remember { mutableStateOf(AirDroidTab.HOME) }
    val pagerState = rememberPagerState(
        initialPage = TOOLBAR_TABS.indexOf(AirDroidTab.HOME),
        pageCount = { TOOLBAR_TABS.size },
    )
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    LaunchedEffect(tab) {
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
    }
    LaunchedEffect(pagerState.currentPage) {
        tab = TOOLBAR_TABS[pagerState.currentPage]
    }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    var tapTarget by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress &&
            tapTarget != null &&
            pagerState.currentPage == tapTarget
        ) {
            tapTarget = null
        }
    }
    fun selectTab(next: AirDroidTab) {
        val target = TOOLBAR_TABS.indexOf(next)
        if (target == pagerState.currentPage && pagerState.currentPageOffsetFraction == 0f) return
        tapTarget = target
        tab = next
        pageJob?.cancel()
        pageJob = scope.launch {
            pagerState.animateScrollToPage(
                page = target,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val pageTab = TOOLBAR_TABS[page]
            Column(modifier = Modifier.fillMaxSize()) {
                AirDroidTopBar(pageTab, controller, scope, scrollBehavior)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    when (pageTab) {
                        AirDroidTab.HOME -> HomeScreen(controller, scope)
                        AirDroidTab.LOGS -> LogsScreen(controller)
                        AirDroidTab.SETTINGS -> SettingsScreen(controller)
                    }
                }
            }
        }
        AirDroidFloatingToolbar(
            selected = tab,
            pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            tapTarget = tapTarget,
            onSelected = ::selectTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        IncomingRequestDialog(controller)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirDroidTopBar(
    tab: AirDroidTab,
    controller: AirDroidController,
    scope: CoroutineScope,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val expandedHeight = 176.dp
    val collapsedHeight = 64.dp
    val rangePx = with(density) { (expandedHeight - collapsedHeight).toPx() }
    LaunchedEffect(rangePx) {
        scrollBehavior.state.heightOffsetLimit = -rangePx
        scrollBehavior.state.heightOffset = 0f
    }
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val headerHeight = expandedHeight + (collapsedHeight - expandedHeight) * collapse + statusTop
    val titleStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = (34f - 12f * collapse).sp,
        lineHeight = (42f - 14f * collapse).sp,
    )
    val subtitleAlpha = (1f - collapse * 1.6f).coerceIn(0f, 1f)
    val subtitleHeight = (20.dp * (1f - collapse)).coerceAtLeast(0.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 20.dp, top = statusTop),
    ) {
        Spacer(Modifier.weight(1f))
        TopBarPage(
            tab = tab,
            titleStyle = titleStyle,
            subtitleAlpha = subtitleAlpha,
            subtitleHeight = subtitleHeight,
            controller = controller,
            scope = scope,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarPage(
    tab: AirDroidTab,
    titleStyle: TextStyle,
    subtitleAlpha: Float,
    subtitleHeight: Dp,
    controller: AirDroidController,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.label,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tab.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .height(subtitleHeight)
                    .alpha(subtitleAlpha)
                    .clipToBounds(),
            )
        }
        when (tab) {
            AirDroidTab.HOME -> {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            if (controller.scanning) {
                                controller.stopScanning()
                            } else {
                                controller.scan()
                            }
                        }
                    },
                    enabled = controller.kernel.monitorReady &&
                        !controller.sending && !controller.sendCancelling,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (controller.scanning) R.drawable.ic_stop_circle else R.drawable.ic_refresh,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (controller.scanning) "Stop" else "Scan")
                }
            }
            AirDroidTab.LOGS -> {
                TextButton(onClick = { controller.clearLogs() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
            }
            AirDroidTab.SETTINGS -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AirDroidFloatingToolbar(
    selected: AirDroidTab,
    pagerPosition: Float,
    tapTarget: Int?,
    onSelected: (AirDroidTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val itemSize = 60.dp
        val selectedIndex = TOOLBAR_TABS.indexOf(selected).coerceAtLeast(0)
        var pressed by remember { mutableStateOf(false) }
        var hoveredIndex by remember { mutableIntStateOf(selectedIndex) }
        var fingerX by remember { mutableFloatStateOf(0f) }
        var fingerY by remember { mutableFloatStateOf(0f) }
        var dragMoved by remember { mutableStateOf(false) }
        var dragDelta by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current
        val fingerOffset = with(density) {
            val raw = fingerX.toDp() - itemSize / 2
            val center = itemSize * selectedIndex
            if (dragMoved) {
                (center + (raw - center) * 0.65f).coerceIn(
                    0.dp,
                    itemSize * (TOOLBAR_TABS.size - 1),
                )
            } else {
                itemSize * hoveredIndex
            }
        }
        val pagerTargetPx = with(density) {
            (itemSize * pagerPosition.coerceIn(0f, TOOLBAR_TABS.lastIndex.toFloat())).toPx()
        }
        val directTargetPx = with(density) {
            val raw = fingerX.toDp() - itemSize / 2
            val center = itemSize * selectedIndex
            if (dragMoved) {
                (center + (raw - center) * 0.65f).coerceIn(
                    0.dp,
                    itemSize * (TOOLBAR_TABS.size - 1),
                ).toPx()
            } else {
                (itemSize * hoveredIndex).toPx()
            }
        }
        val tapTargetPx = tapTarget?.let { target ->
            with(density) { (itemSize * target).toPx() }
        }
        val targetIndicatorPx = when {
            pressed -> directTargetPx
            tapTargetPx != null -> tapTargetPx
            else -> pagerTargetPx
        }
        val animatedIndicatorPx by animateFloatAsState(
            targetValue = targetIndicatorPx,
            animationSpec = when {
                pressed -> spring(dampingRatio = 0.95f, stiffness = 1200f)
                tapTargetPx != null -> spring(dampingRatio = 0.9f, stiffness = 650f)
                else -> spring(dampingRatio = 0.95f, stiffness = 1200f)
            },
            label = "toolbar selection",
        )
        val indicatorOffset = with(density) { animatedIndicatorPx.toDp() }
        val highlightedIndex = with(density) {
            ((animatedIndicatorPx + itemSize.toPx() / 2) / itemSize.toPx())
                .toInt()
                .coerceIn(0, TOOLBAR_TABS.lastIndex)
        }
        val indicatorYOffset = if (pressed && dragMoved) {
            with(density) {
                ((fingerY.toDp() - itemSize / 2) * 0.08f).coerceIn((-2.5).dp, 2.5.dp)
            }
        } else {
            0.dp
        }
        val toolbarScale by animateFloatAsState(
            targetValue = if (pressed) 1.06f else 1f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
            label = "toolbar scale",
        )
        val highlightScale by animateFloatAsState(
            targetValue = if (pressed) 1.04f else 1f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 650f),
            label = "highlight scale",
        )
        val highlightWidth by animateDpAsState(
            targetValue = if (pressed) itemSize + 6.dp else itemSize,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
            label = "highlight width",
        )
        val barShiftX by animateDpAsState(
            targetValue = if (pressed) {
                with(density) {
                    (dragDelta.x.toDp() * 0.06f).coerceIn((-6).dp, 6.dp)
                }
            } else {
                0.dp
            },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 480f),
            label = "toolbar x",
        )
        val barShiftY by animateDpAsState(
            targetValue = if (pressed) {
                with(density) {
                    (dragDelta.y.toDp() * 0.06f).coerceIn((-4).dp, 4.dp)
                }
            } else {
                0.dp
            },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 480f),
            label = "toolbar y",
        )
        val barRotation by animateFloatAsState(
            targetValue = if (pressed) {
                (dragDelta.x / density.density * 0.02f).coerceIn(-2f, 2f)
            } else {
                0f
            },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 480f),
            label = "toolbar tilt",
        )
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.graphicsLayer {
                translationX = barShiftX.toPx()
                translationY = barShiftY.toPx()
                rotationZ = barRotation
                scaleX = toolbarScale
                scaleY = toolbarScale
            },
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface,
                fabContainerColor = MaterialTheme.colorScheme.primary,
                fabContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = FloatingToolbarDefaults.ContainerShape,
            expandedShadowElevation = 6.dp,
            collapsedShadowElevation = 3.dp,
        ) {
            Box(
                modifier = Modifier.pointerInput(itemSize, TOOLBAR_TABS.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun indexAt(x: Float): Int =
                            (x / itemSize.toPx()).toInt().coerceIn(0, TOOLBAR_TABS.lastIndex)
                        val downPosition = down.position
                        fingerX = down.position.x
                        fingerY = down.position.y
                        dragMoved = false
                        dragDelta = Offset.Zero
                        hoveredIndex = indexAt(down.position.x)
                        pressed = true
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                released = true
                            } else {
                                fingerX = change.position.x
                                fingerY = change.position.y
                                dragDelta = change.position - downPosition
                                if (kotlin.math.abs(dragDelta.x) > 4.dp.toPx() ||
                                    kotlin.math.abs(dragDelta.y) > 4.dp.toPx()
                                ) {
                                    dragMoved = true
                                }
                                hoveredIndex = indexAt(change.position.x)
                                released = !change.pressed
                                change.consume()
                            }
                        }
                        pressed = false
                        dragMoved = false
                        dragDelta = Offset.Zero
                        onSelected(TOOLBAR_TABS[hoveredIndex])
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = indicatorOffset - (highlightWidth - itemSize) / 2,
                            y = indicatorYOffset,
                        )
                        .width(highlightWidth)
                        .height(itemSize)
                        .graphicsLayer {
                            scaleX = highlightScale
                            scaleY = highlightScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for ((index, tab) in TOOLBAR_TABS.withIndex()) {
                        val isHighlighted = index == highlightedIndex
                        val tint by animateColorAsState(
                            targetValue = if (isHighlighted) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
                            label = "toolbar icon tint",
                        )
                        Box(
                            modifier = Modifier.size(itemSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(controller: AirDroidController, scope: CoroutineScope) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sheetPeer by remember { mutableStateOf<Peer?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun acceptUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            controller.pickFile(uri)
            controller.sendPicked()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { acceptUri(it) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { acceptUri(it) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { acceptUri(it) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { acceptUri(it) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                val zip = withContext(Dispatchers.IO) { zipDocumentTree(context, uri) }
                if (zip != null) controller.sendPath(zip.absolutePath)
            }
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraFile?.let { controller.sendPath(it.absolutePath) }
        cameraFile = null
    }

    fun beginAction(peer: Peer, action: PeerAction) {
        controller.selectPeer(peer)
        sheetPeer = null
        when (action) {
            PeerAction.FILES -> filePicker.launch(arrayOf("*/*"))
            PeerAction.PHOTOS -> photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            PeerAction.VIDEOS -> videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
            PeerAction.AUDIO -> audioPicker.launch(arrayOf("audio/*"))
            PeerAction.FOLDER -> folderPicker.launch(null)
            PeerAction.CAMERA -> {
                val file = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
                cameraFile = file
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                cameraPicker.launch(uri)
            }
            PeerAction.PASTE -> controller.sendText(clipboard.getText()?.text ?: "")
        }
    }

    var homeTab by remember { mutableStateOf(HomeTab.SENT) }
    val peers = controller.peers.sortedWith(
        compareByDescending<Peer> { controller.isPinned(it.mac) }
            .thenByDescending { it.rssi },
    )
    val sentActive = controller.activeTransfers.filter {
        it.direction == TransferDirection.SENT
    }
    val sentDone = controller.completedTransfers.filter {
        it.direction == TransferDirection.SENT
    }
    val receivedActive = controller.activeTransfers.filter {
        it.direction == TransferDirection.RECEIVED
    }
    val receivedDone = controller.completedTransfers.filter {
        it.direction == TransferDirection.RECEIVED
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!controller.kernel.monitorReady) {
            item { KernelBanner(controller) }
        }
        item { SectionLabel("NEARBY AIRDROP") }
        if (peers.isEmpty()) {
            item { EmptyPeopleCard() }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(peers, key = { it.mac }) { peer ->
                        PeerCard(
                            peer = peer,
                            selected = sheetPeer?.mac == peer.mac,
                            pinned = controller.isPinned(peer.mac),
                            onClick = { sheetPeer = peer },
                        )
                    }
                }
            }
        }
        item {
            HomeTabs(
                selected = homeTab,
                sentCount = sentActive.size + sentDone.size,
                receivedCount = receivedActive.size + receivedDone.size,
                onSelected = { homeTab = it },
            )
        }
        when (homeTab) {
            HomeTab.SENT -> {
                if (sentActive.isNotEmpty()) {
                    item { SectionLabel("SENDING") }
                    items(sentActive, key = { it.id }) { transfer ->
                        TransferCard(
                            transfer = transfer,
                            onCancel = { controller.cancelTransfer(transfer.id) },
                        )
                    }
                }
                if (sentDone.isNotEmpty()) {
                    item { SectionLabel("SENT") }
                    items(sentDone, key = { it.id }) { transfer ->
                        TransferCard(transfer)
                    }
                }
                if (sentActive.isEmpty() && sentDone.isEmpty()) {
                    item {
                        EmptyTransfersCard(
                            title = "Nothing sent yet",
                            subtitle = "Tap a nearby phone to send a file.",
                        )
                    }
                }
            }
            HomeTab.RECEIVED -> {
                if (receivedActive.isNotEmpty()) {
                    item { SectionLabel("RECEIVING") }
                    items(receivedActive, key = { it.id }) { transfer ->
                        TransferCard(transfer)
                    }
                }
                if (receivedDone.isNotEmpty()) {
                    item { SectionLabel("RECEIVED") }
                    items(receivedDone, key = { it.id }) { transfer ->
                        TransferCard(
                            transfer = transfer,
                            onOpen = {
                                transfer.localPath?.let { path ->
                                    scope.launch { openReceivedFile(context, path) }
                                }
                            },
                            onShare = {
                                transfer.localPath?.let { path ->
                                    scope.launch { shareReceivedFile(context, path) }
                                }
                            },
                            onDelete = {
                                transfer.localPath?.let { path ->
                                    controller.deleteReceivedFile(path)
                                }
                            },
                        )
                    }
                }
                if (receivedActive.isEmpty() && receivedDone.isEmpty()) {
                    item { EmptyReceivedCard() }
                }
            }
        }
    }

    sheetPeer?.let { peer ->
        PeerActionsSheet(
            peer = peer,
            pinned = controller.isPinned(peer.mac),
            onDismiss = { sheetPeer = null },
            onTogglePin = { controller.togglePinned(peer.mac) },
            onAction = { action -> beginAction(peer, action) },
        )
    }
}

@Composable
private fun EmptyReceivedCard() {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
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
                    text = "Incoming AirDrop files will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyTransfersCard(title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> AirDroidButtonGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(state) },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        for (index in options.indices) {
            val option = options[index]
            customItem(
                buttonGroupContent = {
                    AirDroidGroupItem(
                        option = option,
                        selected = selected,
                        label = label(option),
                        onSelected = onSelected,
                        index = index,
                        count = options.size,
                    )
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(label(option)) },
                        onClick = {
                            onSelected(option)
                            state.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ButtonGroupScope.AirDroidGroupItem(
    option: T,
    selected: T,
    label: String,
    onSelected: (T) -> Unit,
    index: Int,
    count: Int,
) {
    val interaction = remember { MutableInteractionSource() }
    val shapes = when {
        count == 1 -> ToggleButtonDefaults.shapes()
        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }
    ToggleButton(
        checked = selected == option,
        onCheckedChange = { onSelected(option) },
        modifier = Modifier
            .weight(1f)
            .animateWidth(interaction),
        shapes = shapes,
        interactionSource = interaction,
        contentPadding = ToggleButtonDefaults.ContentPadding,
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTabs(
    selected: HomeTab,
    sentCount: Int,
    receivedCount: Int,
    onSelected: (HomeTab) -> Unit,
) {
    AirDroidButtonGroup(
        options = HomeTab.entries,
        selected = selected,
        label = { tab ->
            val count = when (tab) {
                HomeTab.SENT -> sentCount
                HomeTab.RECEIVED -> receivedCount
            }
            "${tab.label} ($count)"
        },
        onSelected = onSelected,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransferCard(
    transfer: TransferItem,
    onCancel: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val sent = transfer.direction == TransferDirection.SENT
    val icon = when {
        transfer.status == TransferStatus.COMPLETE && sent -> R.drawable.ic_check_circle
        sent -> R.drawable.ic_send
        else -> R.drawable.ic_download
    }
    val iconContainer = if (sent) colors.primaryContainer else colors.tertiaryContainer
    val iconTint = if (sent) colors.onPrimaryContainer else colors.onTertiaryContainer
    val statusColor = when (transfer.status) {
        TransferStatus.FAILED, TransferStatus.CANCELLED -> colors.error
        else -> colors.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = iconContainer,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (sent) {
                            "→ ${transfer.peerName.ifBlank { "AirDrop" }}"
                        } else {
                            "from ${transfer.peerName.ifBlank { "AirDrop" }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (transfer.canCancel && onCancel != null) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stop_circle),
                            contentDescription = "Cancel send",
                            tint = colors.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (transfer.status.isActive) {
                val percent = transfer.progressPercent
                if (percent != null &&
                    (transfer.status == TransferStatus.TRANSFERRING ||
                        transfer.transferredBytes > 0)
                ) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .height(4.dp),
                        trackColor = colors.surfaceContainerHighest,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .height(4.dp),
                        trackColor = colors.surfaceContainerHighest,
                    )
                }
            }
            Text(
                text = transferStatusLine(transfer),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (transfer.status == TransferStatus.COMPLETE &&
                transfer.direction == TransferDirection.RECEIVED &&
                transfer.localPath != null &&
                (onOpen != null || onShare != null || onDelete != null)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onOpen?.let {
                        TextButton(
                            onClick = it,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) { Text("Open") }
                    }
                    onShare?.let {
                        TextButton(
                            onClick = it,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) { Text("Share") }
                    }
                    onDelete?.let {
                        TextButton(
                            onClick = it,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) { Text("Delete", color = colors.error) }
                    }
                }
            }
        }
    }
}

private fun transferStatusLine(transfer: TransferItem): String = when (transfer.status) {
    TransferStatus.WAITING -> "Waiting for receiver"
    TransferStatus.OFFERED -> if (transfer.direction == TransferDirection.SENT) {
        "Waiting for acceptance"
    } else {
        "Waiting for your response"
    }
    TransferStatus.ACCEPTED -> if (transfer.direction == TransferDirection.SENT) {
        "Accepted · starting upload"
    } else {
        "Accepted · starting download"
    }
    TransferStatus.TRANSFERRING -> buildString {
        transfer.progressPercent?.let { append("$it% · ") }
        append(transfer.transferredBytes.readableBytes())
        if (transfer.totalBytes > 0) {
            append(" / ")
            append(transfer.totalBytes.readableBytes())
        }
    }
    TransferStatus.COMPLETE -> buildString {
        append(if (transfer.direction == TransferDirection.SENT) "Delivered" else "Received")
        if (transfer.totalBytes > 0) {
            append(" · ")
            append(transfer.totalBytes.readableBytes())
        }
        transfer.averageBytesPerSecond?.let {
            append(" · ")
            append(it.readableBytes())
            append("/s")
        }
        transfer.completedAt?.let {
            append(" · ")
            append(formatClock(it))
        }
    }
    TransferStatus.FAILED -> transfer.error
        ?.takeIf { it.isNotBlank() }
        ?.let { error ->
            if (error.startsWith("Failed", ignoreCase = true)) error else "Failed · $error"
        }
        ?: "Failed"
    TransferStatus.CANCELLED -> transfer.error
        ?.takeIf { it.isNotBlank() && !it.equals("Cancelled", ignoreCase = true) }
        ?.let { "Cancelled · $it" }
        ?: "Cancelled"
}

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun IncomingRequestDialog(controller: AirDroidController) {
    val request = controller.incomingRequest ?: return
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = null,
            )
        },
        title = { Text("Incoming AirDrop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${request.sender.ifBlank { request.peer }} wants to send:")
                Text(
                    text = buildString {
                        append(
                            if (request.fileCount == 1) {
                                "1 file"
                            } else {
                                "${request.fileCount} files"
                            },
                        )
                        if (request.totalBytes > 0) {
                            append(" · ")
                            append(request.totalBytes.readableBytes())
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                if (request.names.isNotEmpty()) {
                    Text(
                        text = request.names.take(3).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    if (request.names.size > 3) {
                        Text(
                            text = "+${request.names.size - 3} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (controller.receiveDecisionPending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { controller.acceptIncoming() },
                enabled = !controller.receiveDecisionPending,
            ) {
                Text("Accept")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { controller.declineIncoming() },
                enabled = !controller.receiveDecisionPending,
            ) {
                Text("Decline")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerActionsSheet(
    peer: Peer,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onAction: (PeerAction) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = peer.displayName(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            Text(
                text = "${peer.typeLabel()} · ${peer.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = onTogglePin) {
                Icon(
                    painter = painterResource(R.drawable.ic_badge),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (pinned) "Pinned phone" else "Pin this phone")
            }
            HorizontalDivider(color = colors.outlineVariant)
            Text(
                text = "SEND",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            PeerAction.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { action ->
                        OutlinedButton(
                            onClick = { onAction(action) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(action.icon),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(action.label)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private suspend fun openReceivedFile(context: Context, path: String) {
    val uri = shareableUri(context, path) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeTypeFor(path))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareReceivedFile(context: Context, path: String) {
    val uri = shareableUri(context, path) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeFor(path)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        try {
            context.startActivity(Intent.createChooser(intent, "Share ${File(path).name}"))
        } catch (_: Exception) {
            Toast.makeText(context, "No app can share this file", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * FileProvider exposes both the app cache and Download/AirDroid, so received
 * files can be opened or shared without copying them first.
 */
private fun shareableUri(context: Context, path: String): Uri? {
    val file = File(path)
    if (!file.exists()) return null
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }
}

private fun mimeTypeFor(path: String): String {
    val extension = File(path).extension.lowercase(Locale.US)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

private fun zipDocumentTree(context: Context, treeUri: Uri): File? {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    val out = File(context.cacheDir, "folder-${System.currentTimeMillis()}.zip")
    return try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            fun add(directory: DocumentFile, prefix: String) {
                for (child in directory.listFiles()) {
                    if (child.isDirectory) {
                        add(child, "$prefix${child.name}/")
                    } else {
                        zip.putNextEntry(ZipEntry("$prefix${child.name}"))
                        context.contentResolver.openInputStream(child.uri)?.use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            add(root, "")
        }
        out
    } catch (_: Exception) {
        out.delete()
        null
    }
}

@Composable
private fun SendScreen(controller: AirDroidController, scope: CoroutineScope) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { controller.pickFile(uri) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!controller.kernel.monitorReady) {
            item { KernelBanner(controller) }
        }
        if (controller.peers.isEmpty()) {
            item { EmptyPeopleCard() }
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
private fun EmptyPeopleCard() {
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
        }
    }
}

@Composable
private fun PeerCard(peer: Peer, selected: Boolean, pinned: Boolean = false, onClick: () -> Unit) {
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
                if (pinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_badge),
                        contentDescription = "Pinned",
                        tint = surface.primary,
                        modifier = Modifier.size(22.dp),
                    )
                } else if (selected) {
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
private fun KernelBanner(controller: AirDroidController) {
    val kernel = controller.kernel
    if (kernel.monitorReady) return
    val colors = MaterialTheme.colorScheme
    val title = when (kernel.state) {
        MonitorState.STOCK -> "Wi-Fi mode"
        MonitorState.UNVERIFIED -> "Kernel not verified"
        MonitorState.MONITOR -> return
    }
    val wifiMode = kernel.state == MonitorState.STOCK
    val container = if (wifiMode) colors.secondaryContainer else colors.errorContainer
    val onContainer = if (wifiMode) colors.onSecondaryContainer else colors.onErrorContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(if (wifiMode) R.drawable.ic_wifi_tethering else R.drawable.ic_warning),
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (wifiMode) {
                        "${kernel.detail} Enter AirDrop mode when ready; if the live switch fails, reboot into it."
                    } else {
                        "${kernel.detail} Verify with root, then enter AirDrop mode."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.82f),
                )
                if (kernel.state == MonitorState.UNVERIFIED) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Kernel ${kernel.release}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.66f),
                    )
                }
                TextButton(
                    onClick = { controller.enterAirDropMode() },
                    enabled = !controller.modeSwitching,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wifi_tethering),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (controller.modeSwitching) "Switching..." else "Enter AirDrop mode")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendCard(
    controller: AirDroidController,
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
                enabled = !controller.sending && !controller.sendCancelling,
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
                    !controller.sendCancelling && !controller.receiverRunning,
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

@Composable
private fun LogsScreen(controller: AirDroidController) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(controller.logs.size) {
        if (controller.logs.isNotEmpty()) {
            listState.scrollToItem(controller.logs.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(controller.logs) { line ->
            Text(
                text = line.ifEmpty { " " },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.onSurface.copy(alpha = 0.9f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(controller: AirDroidController) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var channel by remember { mutableIntStateOf(controller.settings.channel) }
    var bleArm by remember { mutableIntStateOf(controller.settings.bleArm) }
    var deviceName by remember { mutableStateOf(controller.settings.deviceName) }
    var themeMode by remember { mutableStateOf(controller.settings.themeMode) }
    var tintMode by remember { mutableStateOf(controller.settings.tintMode) }

    LaunchedEffect(deviceName) {
        if (deviceName != controller.settings.deviceName) {
            delay(650)
            controller.saveSettings(channel, bleArm, deviceName, themeMode, tintMode)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 240.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (!it.isFocused && deviceName != controller.settings.deviceName) {
                                controller.saveSettings(channel, bleArm, deviceName, themeMode, tintMode)
                            }
                        },
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Changes save automatically",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
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
                            text = "Radio mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = when (controller.kernel.state) {
                                MonitorState.MONITOR -> "AirDrop mode active"
                                MonitorState.STOCK -> "Wi-Fi mode"
                                MonitorState.UNVERIFIED -> "Unknown mode"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (controller.kernel.state) {
                                MonitorState.MONITOR -> colors.primary
                                MonitorState.STOCK -> colors.tertiary
                                MonitorState.UNVERIFIED -> colors.error
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
                if (controller.kernel.state == MonitorState.UNVERIFIED) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Kernel ${controller.kernel.release}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (!controller.kernel.monitorReady) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { controller.enterAirDropMode() },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "enter",
                            icon = R.drawable.ic_wifi_tethering,
                            label = if (controller.modeAction == "enter") "Entering AirDrop mode..." else "Enter AirDrop mode",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { controller.enterAirDropMode(reboot = true) },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "reboot-enter",
                            icon = R.drawable.ic_refresh,
                            label = if (controller.modeAction == "reboot-enter") "Rebooting into AirDrop mode..." else "Reboot into AirDrop mode",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { controller.verifyKernelWithRoot() },
                        enabled = !controller.verifyingKernel && !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.verifyingKernel,
                            icon = R.drawable.ic_memory,
                            label = if (controller.verifyingKernel) "Checking with root..." else "Verify with root",
                        )
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { controller.exitAirDropMode() },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "exit",
                            icon = R.drawable.ic_stop_circle,
                            label = if (controller.modeAction == "exit") "Exiting AirDrop mode..." else "Exit AirDrop mode",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { controller.exitAirDropMode(reboot = true) },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "reboot-exit",
                            icon = R.drawable.ic_refresh,
                            label = if (controller.modeAction == "reboot-exit") {
                                "Rebooting to exit AirDrop mode..."
                            } else {
                                "Reboot to exit AirDrop mode"
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { controller.verifyKernelWithRoot() },
                        enabled = !controller.verifyingKernel && !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.verifyingKernel,
                            icon = R.drawable.ic_memory,
                            label = if (controller.verifyingKernel) "Checking with root..." else "Verify with root",
                        )
                    }
                }
            }
        }
        item { SectionLabel("APPEARANCE") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Follow the system or force light or dark",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                AirDroidButtonGroup(
                    options = ThemeMode.entries,
                    selected = themeMode,
                    label = { it.label },
                    onSelected = {
                        themeMode = it
                        controller.saveAppearance(it, tintMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tint",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = if (Build.VERSION.SDK_INT >= 31) {
                                "AirDroid palette or Android dynamic color"
                            } else {
                                "AirDroid palette; system tint needs Android 12+"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                AirDroidButtonGroup(
                    options = TintMode.entries,
                    selected = tintMode,
                    label = { it.label },
                    onSelected = {
                        tintMode = it
                        controller.saveAppearance(themeMode, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { SectionLabel("RECEIVER") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsListItem(
                    icon = R.drawable.ic_folder,
                    headline = "Incoming storage",
                    supporting = AirDroidController.RECEIVE_DIR,
                )
                SettingsListItem(
                    icon = R.drawable.ic_sensors,
                    headline = "AirDrop port",
                    supporting = "8771 · AWDL link-local",
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
                AirDroidButtonGroup(
                    options = listOf(0, 44, 6, 149),
                    selected = channel,
                    label = { option ->
                        when (option) {
                            0 -> "Auto"
                            6 -> "6"
                            149 -> "149"
                            else -> "44"
                        }
                    },
                    onSelected = {
                        channel = it
                        controller.saveSettings(it, bleArm, deviceName, themeMode, tintMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Auto tries 44 → 6 → 149 and locks onto the first channel with peers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
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
                val bleOptions = listOf("A", "V2", "B")
                AirDroidButtonGroup(
                    options = bleOptions,
                    selected = bleOptions.getOrElse(bleArm) { "A" },
                    label = { it },
                    onSelected = {
                        bleArm = bleOptions.indexOf(it)
                        controller.saveSettings(channel, bleArm, deviceName, themeMode, tintMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                        text = "AirDroid",
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "DEVELOPER",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "RenzVillegas24",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "GITHUB REPOSITORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "RenzVillegas24/AirDroid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/RenzVillegas24/AirDroid")
                        },
                    )
                    Text(
                        text = "github.com/RenzVillegas24/AirDroid",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    Text(
                        text = BuildConfig.APPLICATION_ID,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.DEVICE}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "Kernel ${controller.kernel.release}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "Radio · WCN3990 · monitor + hidden STA",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeButtonContent(
    loading: Boolean,
    @DrawableRes icon: Int,
    label: String,
) {
    if (loading) {
        LoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalContentColor.current,
        )
    } else {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
    Spacer(Modifier.width(8.dp))
    Text(label)
}

@Composable
private fun SettingsListItem(
    @DrawableRes icon: Int,
    headline: String,
    supporting: String,
    trailing: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        ListItem(
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            },
            trailingContent = trailing?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
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
