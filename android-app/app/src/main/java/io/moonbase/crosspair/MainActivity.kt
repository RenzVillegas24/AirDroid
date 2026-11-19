package io.moonbase.crosspair

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private lateinit var controller: CrossPairController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = CrossPairControllerHolder.current
            ?: CrossPairController(applicationContext).also { CrossPairControllerHolder.current = it }
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
            CrossPairTheme(
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
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2400)
                    showSplash = false
                }
                Box(Modifier.fillMaxSize()) {
                    CrossPairApp(controller = controller, scope = scope)
                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut(tween(500)) + scaleOut(tween(500), targetScale = 1.06f),
                    ) {
                        CrossPairSplash()
                    }
                }
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
        if (CrossPairService.running) return
        if (CrossPairControllerHolder.current === controller) {
            CrossPairControllerHolder.current = null
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
        val cancelId = intent.getStringExtra(CrossPairNotifications.EXTRA_CANCEL_TRANSFER)
        if (cancelId != null) {
            controller.cancelTransfer(cancelId)
            intent.removeExtra(CrossPairNotifications.EXTRA_CANCEL_TRANSFER)
        }
        val requestId = intent.getLongExtra(CrossPairNotifications.EXTRA_REQUEST_ID, -1L)
        if (requestId >= 0) {
            val accept = intent.getBooleanExtra(CrossPairNotifications.EXTRA_REQUEST_ACCEPT, false)
            controller.resolveIncomingFromNotification(requestId, accept)
            intent.removeExtra(CrossPairNotifications.EXTRA_REQUEST_ID)
            intent.removeExtra(CrossPairNotifications.EXTRA_REQUEST_ACCEPT)
        }
    }
}

private enum class CrossPairTab(
    val label: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
) {
    HOME("CrossPair", "Nearby AirDrop and received files", R.drawable.ic_crosspair_home),
    LOGS("Logs", "Live radio and session output", R.drawable.ic_receipt_long),
    SETTINGS("Settings", "Radio, appearance, and receiver identity", R.drawable.ic_settings),
}

private enum class PeerAction(val label: String, @DrawableRes val icon: Int) {
    FILES("Files", R.drawable.ic_attach_file),
    PHOTOS("Photos", R.drawable.ic_image),
    VIDEOS("Videos", R.drawable.ic_videocam),
    FOLDER("Folder", R.drawable.ic_folder),
    CAMERA("Camera", R.drawable.ic_photo_camera),
    AUDIO("Audio", R.drawable.ic_music_note),
    PASTE("Paste", R.drawable.ic_content_paste),
}

private data class StagedItem(
    val path: String,
    val name: String,
    val size: Long,
    /** The staging job that produced this file, so its row keeps one identity from add to ready. */
    val jobId: Long? = null,
)

private enum class HomeTab(val label: String) {
    SENT("Sent"),
    RECEIVED("Received"),
}

private val TOOLBAR_TABS = listOf(CrossPairTab.LOGS, CrossPairTab.HOME, CrossPairTab.SETTINGS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrossPairApp(controller: CrossPairController, scope: CoroutineScope) {
    var tab by remember { mutableStateOf(CrossPairTab.HOME) }
    val pagerState = rememberPagerState(
        initialPage = TOOLBAR_TABS.indexOf(CrossPairTab.HOME),
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
    fun selectTab(next: CrossPairTab) {
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
    // Pages fade out above the floating toolbar instead of being cut by a hard edge.
    val pageFadeHeight = MainPageFadeHeight +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
                CrossPairTopBar(pageTab, controller, scope, scrollBehavior)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .fadingEdges(bottom = pageFadeHeight),
                ) {
                    when (pageTab) {
                        CrossPairTab.HOME -> HomeScreen(controller, scope)
                        CrossPairTab.LOGS -> LogsScreen(controller)
                        CrossPairTab.SETTINGS -> SettingsScreen(controller)
                    }
                }
            }
        }
        CrossPairFloatingToolbar(
            selected = tab,
            pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            tapTarget = tapTarget,
            onSelected = ::selectTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        IncomingRequestDialog(controller)
        BluetoothRequiredDialog(controller)
    }
}

@Composable
private fun BluetoothRequiredDialog(controller: CrossPairController) {
    if (controller.status != "BLUETOOTH OFF") return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { controller.dismissBluetoothWarning() },
        title = { Text("Bluetooth required") },
        text = {
            Text(
                "AirDrop uses Bluetooth Low Energy to wake nearby iPhones. " +
                    "Turn on Bluetooth, then retry. Transfer mode will not start " +
                    "without a working BLE advertisement.",
            )
        },
        confirmButton = {
            TextButton(onClick = { controller.retryBluetooth() }) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    controller.dismissBluetoothWarning()
                },
            ) {
                Text("Bluetooth settings")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrossPairTopBar(
    tab: CrossPairTab,
    controller: CrossPairController,
    scope: CoroutineScope,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val expandedHeight = 140.dp
    val collapsedHeight = 64.dp
    val rangePx = with(density) { (expandedHeight - collapsedHeight).toPx() }
    LaunchedEffect(rangePx) {
        scrollBehavior.state.heightOffsetLimit = -rangePx
        scrollBehavior.state.heightOffset = 0f
    }
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val headerHeight = expandedHeight + (collapsedHeight - expandedHeight) * collapse + statusTop
    val titleStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = (30f - 8f * collapse).sp,
        lineHeight = (37f - 9f * collapse).sp,
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBarPage(
    tab: CrossPairTab,
    titleStyle: TextStyle,
    subtitleAlpha: Float,
    subtitleHeight: Dp,
    controller: CrossPairController,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val saveLogs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = controller.saveLogs(uri)
                Toast.makeText(
                    context,
                    if (saved) "Logs saved" else "Could not save logs",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
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
            CrossPairTab.HOME -> {
                val transferMode = controller.kernel.state == MonitorState.MONITOR
                FilledTonalButton(
                    onClick = {
                        if (transferMode) {
                            controller.exitTransferMode()
                        } else {
                            controller.enterTransferMode()
                        }
                    },
                    enabled = !controller.modeSwitching &&
                        !controller.sending && !controller.sendCancelling,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (controller.modeSwitching) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (transferMode) R.drawable.ic_crosspair_home else R.drawable.ic_wifi_tethering,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (transferMode) "ON" else "OFF")
                }
            }
            CrossPairTab.LOGS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            saveLogs.launch("crosspair-logs-$stamp.txt")
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
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
            }
            CrossPairTab.SETTINGS -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CrossPairFloatingToolbar(
    selected: CrossPairTab,
    pagerPosition: Float,
    tapTarget: Int?,
    onSelected: (CrossPairTab) -> Unit,
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
                // Following the finger: quick, with a little give.
                pressed -> spring(dampingRatio = 0.72f, stiffness = 900f)
                // Tap: a softer, bouncier travel that overshoots slightly and settles.
                tapTargetPx != null -> spring(dampingRatio = 0.6f, stiffness = 380f)
                // Following the pager while swiping: tight enough to stay in sync.
                else -> spring(dampingRatio = 0.78f, stiffness = 800f)
            },
            label = "toolbar selection",
        )
        // The highlight stretches while it travels and squashes a little, like a jelly drop;
        // it is derived from the spring itself, so it relaxes back as the spring settles.
        val stretchDp = with(density) {
            (kotlin.math.abs(targetIndicatorPx - animatedIndicatorPx) * 0.16f)
                .coerceAtMost(itemSize.toPx() * 0.45f)
                .toDp()
        }
        val squash = 1f - (stretchDp / itemSize) * 0.14f
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
            animationSpec = spring(dampingRatio = 0.52f, stiffness = 420f),
            label = "toolbar scale",
        )
        val highlightScale by animateFloatAsState(
            targetValue = if (pressed) 1.04f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
            label = "highlight scale",
        )
        val highlightBaseWidth by animateDpAsState(
            targetValue = if (pressed) itemSize + 6.dp else itemSize,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
            label = "highlight width",
        )
        val highlightWidth = highlightBaseWidth + stretchDp
        val barShiftX by animateDpAsState(
            targetValue = if (pressed) {
                with(density) {
                    (dragDelta.x.toDp() * 0.06f).coerceIn((-6).dp, 6.dp)
                }
            } else {
                0.dp
            },
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
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
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
            label = "toolbar y",
        )
        val barRotation by animateFloatAsState(
            targetValue = if (pressed) {
                (dragDelta.x / density.density * 0.02f).coerceIn(-2f, 2f)
            } else {
                0f
            },
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
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
                            scaleY = highlightScale * squash
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
                        val iconScale by animateFloatAsState(
                            targetValue = if (isHighlighted) 1.14f else 1f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                            label = "toolbar icon scale",
                        )
                        Box(
                            modifier = Modifier.size(itemSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
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
private fun HomeScreen(controller: CrossPairController, scope: CoroutineScope) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sheetPeer by remember { mutableStateOf<Peer?>(null) }
    var sheetItems by remember { mutableStateOf<List<StagedItem>>(emptyList()) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun addStaged(items: List<Pair<File, String>>, jobId: Long? = null) {
        if (items.isEmpty()) return
        sheetItems = sheetItems + items.map { (file, name) ->
            StagedItem(
                file.absolutePath,
                name,
                controller.stagedSize(file.absolutePath, file.length()),
                jobId,
            )
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            controller.stageFiles(uris) { file, jobId -> addStaged(listOf(file), jobId) }
        }
    }

    fun openPeer(peer: Peer) {
        if (sheetPeer?.mac != peer.mac) sheetItems = emptyList()
        sheetPeer = peer
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { addUris(it) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addUris(listOf(uri))
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addUris(listOf(uri))
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addUris(listOf(uri))
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                val zip = withContext(Dispatchers.IO) { zipDocumentTree(context, uri) }
                if (zip != null) addStaged(listOf(zip to zip.name))
            }
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraFile?.let { addStaged(listOf(it to it.name)) }
        cameraFile = null
    }

    fun beginAction(peer: Peer, action: PeerAction) {
        controller.selectPeer(peer)
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
            PeerAction.PASTE -> scope.launch {
                controller.stageText(clipboard.getText()?.text ?: "")?.let {
                    addStaged(listOf(it))
                }
            }
        }
    }

    var homeTab by remember { mutableStateOf(HomeTab.SENT) }
    LaunchedEffect(controller.sending) {
        if (controller.sending) homeTab = HomeTab.SENT
    }
    LaunchedEffect(controller.receiveTransferring) {
        if (controller.receiveTransferring) homeTab = HomeTab.RECEIVED
    }
    val myName = controller.settings.deviceName.ifBlank { Build.MODEL }
    val peers = controller.peers.filter { it.airdropReady }.sortedWith(
        // Stable order: pinned first, then discovery order. Sorting by rssi
        // made the cards swap places whenever a beacon moved a few dB.
        compareByDescending<Peer> { controller.isPinned(it.mac) },
    )
    val sentActive = controller.activeTransfers
        .filter { it.direction == TransferDirection.SENT }
        .sortedWith(activeTransferOrder)
    val sentDone = controller.completedTransfers
        .filter { it.direction == TransferDirection.SENT }
        .sortedWith(finishedTransferOrder)
    val receivedAll = groupReceivedTransfers(
        controller.activeTransfers.filter { it.direction == TransferDirection.RECEIVED } +
            controller.completedTransfers.filter { it.direction == TransferDirection.RECEIVED },
        controller,
    )
    val receivedActive = receivedAll
        .filter { it.status.isActive }
        .sortedWith(activeTransferOrder)
    val receivedDone = receivedAll
        .filter { it.status.isTerminal }
        .sortedWith(finishedTransferOrder)
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val updateRelease = if (controller.settings.updateReminders) {
        (controller.updateState as? UpdateState.Available)?.release
    } else {
        null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!controller.kernel.monitorReady) {
            item(key = "kernel-banner") {
                KernelBanner(controller, Modifier.animateItem().padding(horizontal = 20.dp))
            }
        }
        updateRelease?.let { release ->
            item(key = "update-banner") {
                UpdateBanner(controller, release, Modifier.animateItem().padding(horizontal = 20.dp))
            }
        }
        // Peers can only be discovered in monitor mode, so hide the section in Wi-Fi mode.
        // Keyed + animateItem: it fades out and everything below slides up smoothly.
        if (controller.kernel.state != MonitorState.STOCK) {
            item(key = "nearby-label") {
                SectionLabel("NEARBY AIRDROP", Modifier.animateItem().padding(horizontal = 20.dp))
            }
            item(key = "nearby-section") {
                NearbyPeersSection(
                    peers = peers,
                    selectedMac = sheetPeer?.mac,
                    isPinned = { controller.isPinned(it) },
                    onPeerClick = { openPeer(it) },
                    scanningPaused = controller.settings.pauseDiscoveryDuringTransfer &&
                        (controller.sending || controller.receiveTransferring),
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "home-tabs") {
            Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                HomeTabs(
                    selected = homeTab,
                    sentCount = sentActive.size + sentDone.size,
                    receivedCount = receivedActive.size + receivedDone.size,
                    onSelected = { homeTab = it },
                )
            }
        }
        when (homeTab) {
            HomeTab.SENT -> {
                if (sentActive.isNotEmpty()) {
                    item(key = "sending-label") { SectionLabel("SENDING", Modifier.animateItem().padding(horizontal = 20.dp)) }
                    items(sentActive, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                            TransferCard(
                                transfer = transfer,
                                localName = myName,
                                onCancel = { controller.cancelTransfer(transfer.id) },
                                expanded = expandedGroups[transfer.id] == true,
                                onToggleGroup = {
                                    expandedGroups[transfer.id] =
                                        expandedGroups[transfer.id] != true
                                },
                            )
                        }
                    }
                }
                if (sentDone.isNotEmpty()) {
                    item(key = "sent-label") { SectionLabel("SENT", Modifier.animateItem().padding(horizontal = 20.dp)) }
                    items(sentDone, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                            TransferCard(
                                transfer = transfer,
                                localName = myName,
                                expanded = expandedGroups[transfer.id] == true,
                                onToggleGroup = {
                                    expandedGroups[transfer.id] =
                                        expandedGroups[transfer.id] != true
                                },
                            )
                        }
                    }
                }
                if (sentActive.isEmpty() && sentDone.isEmpty()) {
                    item(key = "sent-empty") {
                        Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                            EmptyTransfersCard(
                                title = "Nothing sent yet",
                                subtitle = "Tap a nearby phone to send a file.",
                            )
                        }
                    }
                }
            }
            HomeTab.RECEIVED -> {
                if (controller.receiverRunning && !controller.receiveEnabled) {
                    item(key = "receiver-paused") {
                        ReceiverPausedRow(controller, scope, Modifier.animateItem().padding(horizontal = 20.dp))
                    }
                }
                if (receivedActive.isNotEmpty()) {
                    item(key = "receiving-label") { SectionLabel("RECEIVING", Modifier.animateItem().padding(horizontal = 20.dp)) }
                    items(receivedActive, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                            val activeFile = transfer.files.firstOrNull {
                                it.status.isActive
                            }
                            TransferCard(
                                transfer = transfer,
                                localName = myName,
                                onCancel = if (transfer.canCancel) {
                                    {
                                        controller.cancelTransfer(
                                            activeFile?.id ?: transfer.id,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onOpenFile = { file ->
                                    file.localPath?.let { path ->
                                        scope.launch { openReceivedFile(context, path) }
                                    }
                                },
                                expanded = expandedGroups[transfer.id] == true,
                                onToggleGroup = {
                                    expandedGroups[transfer.id] =
                                        expandedGroups[transfer.id] != true
                                },
                            )
                        }
                    }
                }
                if (receivedDone.isNotEmpty()) {
                    item(key = "received-label") { SectionLabel("RECEIVED", Modifier.animateItem().padding(horizontal = 20.dp)) }
                    items(receivedDone, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                            val paths = transfer.files.mapNotNull { it.localPath }
                            TransferCard(
                                transfer = transfer,
                                localName = myName,
                                onOpen = transfer.localPath?.let { path ->
                                    { scope.launch { openReceivedFile(context, path) } }
                                },
                                onShare = transfer.localPath?.let { path ->
                                    { scope.launch { shareReceivedFile(context, path) } }
                                },
                                onDelete = transfer.localPath?.let { path ->
                                    { controller.deleteReceivedFile(path) }
                                },
                                onOpenFile = { file ->
                                    file.localPath?.let { path ->
                                        scope.launch { openReceivedFile(context, path) }
                                    }
                                },
                                onShareGroup = if (paths.isNotEmpty()) {
                                    { scope.launch { shareReceivedFiles(context, paths) } }
                                } else {
                                    null
                                },
                                onDeleteGroup = if (paths.isNotEmpty()) {
                                    { paths.forEach { controller.deleteReceivedFile(it) } }
                                } else {
                                    null
                                },
                                expanded = expandedGroups[transfer.id] == true,
                                onToggleGroup = {
                                    expandedGroups[transfer.id] =
                                        expandedGroups[transfer.id] != true
                                },
                            )
                        }
                    }
                }
                if (receivedActive.isEmpty() && receivedDone.isEmpty()) {
                    item(key = "received-empty") {
                        Box(Modifier.animateItem().padding(horizontal = 20.dp)) { EmptyReceivedCard() }
                    }
                }
            }
        }
    }

    sheetPeer?.let { peer ->
        // The sheet stays open when the phone drops out of range. It keeps the last known
        // details, shows a "waiting" state and picks the live data up again if the phone returns.
        val livePeer = controller.peers.firstOrNull {
            it.mac == peer.mac
        }
        PeerActionsSheet(
            peer = livePeer ?: peer,
            lost = livePeer == null,
            channel = if (controller.kernel.state == MonitorState.MONITOR) {
                controller.lockedChannel
            } else {
                null
            },
            items = sheetItems,
            pinned = controller.isPinned(peer.mac),
            onDismiss = {
                sheetPeer = null
                sheetItems = emptyList()
            },
            onTogglePin = { controller.togglePinned(peer.mac) },
            onRemoveItem = { item -> sheetItems = sheetItems - item },
            onAddFiles = { beginAction(peer, PeerAction.FILES) },
            onAction = { action -> beginAction(peer, action) },
            stagingJobs = controller.stagingJobs,
            onRemoveStaging = { controller.cancelStagingJob(it) },
            onSend = {
                controller.selectPeer(peer)
                controller.sendStaged(sheetItems.map { it.path to it.name })
                sheetPeer = null
                sheetItems = emptyList()
            },
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
private fun ReceiverPausedRow(
    controller: CrossPairController,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sensors),
            contentDescription = null,
            tint = colors.tertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Receiving paused",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = "Nearby AirDrop discovery is still running.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        TextButton(onClick = { scope.launch { controller.startReceiving() } }) {
            Text("Resume")
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
private fun <T> CrossPairButtonGroup(
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
                    CrossPairGroupItem(
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
private fun <T> ButtonGroupScope.CrossPairGroupItem(
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
    CrossPairButtonGroup(
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

/** Smoothed live transfer speed, measured from how fast transferredBytes grows. */
@Stable
private class TransferRateState {
    var bytesPerSecond by mutableDoubleStateOf(0.0)
    var lastBytes = -1L
    var lastMs = 0L
}

@Composable
private fun rememberLiveRate(key: Any, transferredBytes: Long, active: Boolean): Double {
    val state = remember(key) { TransferRateState() }
    LaunchedEffect(transferredBytes, active) {
        if (!active) {
            state.bytesPerSecond = 0.0
            state.lastBytes = -1L
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        if (state.lastBytes < 0 || transferredBytes < state.lastBytes) {
            state.lastBytes = transferredBytes
            state.lastMs = now
        } else {
            val dt = now - state.lastMs
            if (dt >= 400 && transferredBytes > state.lastBytes) {
                val instant = (transferredBytes - state.lastBytes) * 1000.0 / dt
                state.bytesPerSecond = if (state.bytesPerSecond <= 0.0) {
                    instant
                } else {
                    state.bytesPerSecond * 0.65 + instant * 0.35
                }
                state.lastBytes = transferredBytes
                state.lastMs = now
            }
        }
    }
    return state.bytesPerSecond
}

private fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
        else -> "${s / 3600}h ${((s % 3600) / 60).toString().padStart(2, '0')}m"
    }
}

private fun formatClockSeconds(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

/** "Finished at 14:32:05", or "Finished Nov 15 · 14:32:05" when it wasn't today. */
private fun formatEndedAt(verb: String, timestamp: Long): String =
    if (DateUtils.isToday(timestamp)) {
        "$verb at ${formatClockSeconds(timestamp)}"
    } else {
        val day = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        "$verb $day · ${formatClockSeconds(timestamp)}"
    }

@Composable
private fun BigPercent(progress: Float, color: Color) {
    val value = (progress * 100f).toInt().coerceIn(0, 100)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)) {
                append(value.toString())
            }
            withStyle(
                SpanStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color.copy(alpha = 0.8f),
                ),
            ) { append("%") }
        },
        style = MaterialTheme.typography.displayMedium.copy(
            fontFeatureSettings = "tnum",
            letterSpacing = (-1).sp,
            lineHeight = 36.sp,
        ),
    )
}

@Composable
private fun RouteEnd(name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_smartphone),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "My phone → their phone" (or the reverse for received files). */
@Composable
private fun TransferRoute(from: String, to: String, arrowColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RouteEnd(from, Modifier.weight(1f, fill = false))
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = arrowColor,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        RouteEnd(to, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun StatusChip(text: String, @DrawableRes icon: Int, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

/** In-progress cards first, queued cards next, finished cards last. */
private fun transferQueueRank(item: TransferItem): Int = when {
    item.status == TransferStatus.TRANSFERRING ||
        item.files.any { it.status == TransferStatus.TRANSFERRING } -> 0
    item.status.isActive -> 1
    else -> 2
}

private val activeTransferOrder = compareBy<TransferItem> { transferQueueRank(it) }
    .thenByDescending { it.startedAt }
    .thenBy { it.id }

private val finishedTransferOrder =
    compareByDescending<TransferItem> { it.completedAt ?: it.startedAt }
        .thenBy { it.id }

/**
 * Turns the controller's per-file receive rows into one card per `/Ask`.
 * A one-file request keeps its existing card.
 */
private fun groupReceivedTransfers(
    items: List<TransferItem>,
    controller: CrossPairController,
): List<TransferItem> {
    if (items.size <= 1) return items
    return items.groupBy { controller.batchKeyFor(it.id) ?: "single:${it.id}" }
        .values
        .map { group ->
            if (group.size == 1) return@map group.first()
            // Per-file receive rows are upserted by their stable ids, and the
            // active list is sorted by time/id. Numeric suffix order restores
            // the sender's actual file order (0, 1, 2, ... 10) instead of the
            // lexicographic order (0, 1, 10, 11, 2, ...).
            val ordered = group.sortedWith(
                compareBy<TransferItem> { receivedFileIndex(it.id) }
                    .thenBy { it.startedAt },
            )
            val first = ordered.first()
            val anyActive = ordered.any { it.status.isActive }
            val status = when {
                anyActive -> ordered.firstOrNull { it.status.isActive }?.status ?: TransferStatus.TRANSFERRING
                ordered.all { it.status == TransferStatus.COMPLETE } -> TransferStatus.COMPLETE
                ordered.any { it.status == TransferStatus.CANCELLED } -> TransferStatus.CANCELLED
                else -> TransferStatus.FAILED
            }
            first.copy(
                id = "rx-group-${controller.batchKeyFor(first.id) ?: first.id}",
                fileName = "${ordered.size} files",
                totalBytes = ordered.sumOf { it.totalBytes },
                transferredBytes = ordered.sumOf { it.transferredBytes },
                status = status,
                localPath = null,
                completedAt = if (status.isTerminal) {
                    ordered.mapNotNull { it.completedAt }.maxOrNull() ?: System.currentTimeMillis()
                } else {
                    null
                },
                uploadStartedAt = ordered.mapNotNull { it.uploadStartedAt }.minOrNull(),
                files = ordered.map { item ->
                    TransferFile(
                        id = item.id,
                        name = item.fileName,
                        totalBytes = item.totalBytes,
                        transferredBytes = item.transferredBytes,
                        status = item.status,
                        localPath = item.localPath,
                        completedAt = item.completedAt,
                    )
                },
            )
        }
}

/** `rx-1-10` → 10, falling back to a stable last-position order. */
private fun receivedFileIndex(id: String): Int =
    id.substringAfterLast('-').toIntOrNull() ?: Int.MAX_VALUE

/** Which body the transfer card shows. Used as the AnimatedContent key so the body cross-fades. */
private enum class TransferPhase { PROGRESS, PENDING, DONE, FAILED }

/** Progress bar thickness (was the 4dp default) and the container height that fits the wave. */
private val TransferBarStroke = 7.dp
private val TransferBarHeight = 14.dp

/** Same grouped-list geometry as the Settings screen: 16dp outer corners, 4dp inner, 2dp gaps. */
private val TransferGroupShape = RoundedCornerShape(16.dp)
private val TransferItemShape = RoundedCornerShape(4.dp)
private val TransferItemGap = 2.dp
private val TransferFileRowHeight = 72.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TransferCard(
    transfer: TransferItem,
    localName: String = Build.MODEL,
    onCancel: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpenFile: ((TransferFile) -> Unit)? = null,
    onShareGroup: (() -> Unit)? = null,
    onDeleteGroup: (() -> Unit)? = null,
    expanded: Boolean = false,
    onToggleGroup: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val sent = transfer.direction == TransferDirection.SENT
    val status = transfer.status
    val active = status.isActive
    val transferring = status == TransferStatus.TRANSFERRING
    val failed = status == TransferStatus.FAILED || status == TransferStatus.CANCELLED
    val complete = status == TransferStatus.COMPLETE
    val fileCount = transfer.files.size
    val multi = fileCount > 1
    val doneCount = transfer.files.count { it.status == TransferStatus.COMPLETE }

    val accent = if (sent) colors.primary else colors.tertiary
    val avatarContainer by animateColorAsState(
        targetValue = when {
            failed -> colors.errorContainer
            sent -> colors.primaryContainer
            else -> colors.tertiaryContainer
        },
        animationSpec = tween(350),
        label = "avatarContainer",
    )
    val avatarTint by animateColorAsState(
        targetValue = when {
            failed -> colors.onErrorContainer
            sent -> colors.onPrimaryContainer
            else -> colors.onTertiaryContainer
        },
        animationSpec = tween(350),
        label = "avatarTint",
    )
    val icon = when {
        failed -> R.drawable.ic_warning
        sent -> R.drawable.ic_send
        else -> R.drawable.ic_download
    }
    val peer = transfer.peerName.ifBlank { "AirDrop" }
    val me = localName.ifBlank { Build.MODEL }

    // A group that finishes collapses to the finished summary. If the user
    // had expanded it while live, revealing every finished row at once reads
    // like completed items suddenly reappearing.
    LaunchedEffect(complete) {
        if (complete && expanded) onToggleGroup?.invoke()
    }

    val hasProgress = active &&
        transfer.progressPercent != null &&
        (transferring || transfer.transferredBytes > 0)
    val targetProgress = if (transfer.totalBytes > 0) {
        (transfer.transferredBytes.toFloat() / transfer.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        (transfer.progressPercent ?: 0) / 100f
    }
    // A spring keeps its velocity when the target moves, so frequent byte updates glide
    // instead of restarting an ease curve every time.
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
            visibilityThreshold = 0.001f,
        ),
        label = "transferProgress",
    )

    val density = LocalDensity.current
    val barStroke = remember(density) {
        Stroke(width = with(density) { TransferBarStroke.toPx() }, cap = StrokeCap.Round)
    }

    val liveRate = rememberLiveRate(transfer.id, transfer.transferredBytes, transferring)
    // Sparse progress events (especially during AWDL slowdowns) can leave the
    // live smoother without a sample. Fall back to the transfer average so the
    // card never falls back to "Measuring speed…" / "Estimating time…".
    val rateStart = transfer.uploadStartedAt ?: transfer.startedAt
    val rateElapsedMs = (System.currentTimeMillis() - rateStart).coerceAtLeast(1L)
    val averageRate = if (transfer.transferredBytes > 0) {
        transfer.transferredBytes * 1000.0 / rateElapsedMs
    } else {
        0.0
    }
    val rate = liveRate.takeIf { it > 0.0 } ?: averageRate
    val remainingBytes = (transfer.totalBytes - transfer.transferredBytes).coerceAtLeast(0)
    val etaSeconds: Long? = if (rate > 0.0 && transfer.totalBytes > 0) {
        kotlin.math.ceil(remainingBytes / rate).toLong()
    } else {
        null
    }

    val phase = when {
        hasProgress -> TransferPhase.PROGRESS
        active -> TransferPhase.PENDING
        complete -> TransferPhase.DONE
        else -> TransferPhase.FAILED
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (complete &&
                    transfer.direction == TransferDirection.RECEIVED &&
                    transfer.files.isEmpty() &&
                    onOpen != null
                ) {
                    Modifier.clickable(onClick = onOpen)
                } else {
                    Modifier
                },
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: avatar (with a file-count badge for batches), title, route, cancel.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(
                        shape = CircleShape,
                        color = avatarContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Crossfade(
                                targetState = icon,
                                animationSpec = tween(250),
                                label = "avatarIcon",
                            ) { res ->
                                Icon(
                                    painter = painterResource(res),
                                    contentDescription = null,
                                    tint = avatarTint,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                    if (multi) {
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceContainerLow,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 5.dp, y = 5.dp)
                                .size(21.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (failed) colors.error else accent,
                                    modifier = Modifier.size(17.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (fileCount > 99) "99+" else fileCount.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                lineHeight = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = if (failed) {
                                                colors.onError
                                            } else if (sent) {
                                                colors.onPrimary
                                            } else {
                                                colors.onTertiary
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    if (sent) {
                        TransferRoute(from = me, to = peer, arrowColor = accent)
                    } else {
                        TransferRoute(from = peer, to = me, arrowColor = accent)
                    }
                }
                if (transfer.canCancel && onCancel != null) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stop_circle),
                            contentDescription = if (sent) "Cancel send" else "Stop receiving",
                            tint = colors.error,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                }
            }

            // Status body. Cross-fades with a small upward slide when the phase changes
            // (waiting -> live -> finished). Its own size change is snapped because the card's
            // animateContentSize already eases the height.
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (
                        fadeIn(tween(260, delayMillis = 90)) +
                            slideInVertically(tween(260, delayMillis = 90)) { it / 6 }
                        ).togetherWith(fadeOut(tween(120)))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "transferPhase",
            ) { target ->
                when (target) {
                    // Live transfer: big percentage, speed + ETA, a thick wavy bar, byte counts.
                    TransferPhase.PROGRESS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                BigPercent(progress = progress, color = accent)
                                Spacer(Modifier.weight(1f))
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    Text(
                                        text = if (rate > 0.0) {
                                            "${rate.toLong().readableBytes()}/s"
                                        } else {
                                            "—"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        text = etaSeconds?.let { "${formatDuration(it)} left" } ?: "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                            LinearWavyProgressIndicator(
                                progress = { progress },
                                color = accent,
                                trackColor = colors.surfaceContainerHighest,
                                stroke = barStroke,
                                trackStroke = barStroke,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TransferBarHeight),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = buildString {
                                        append(transfer.transferredBytes.readableBytes())
                                        if (transfer.totalBytes > 0) {
                                            append(" of ")
                                            append(transfer.totalBytes.readableBytes())
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (multi) {
                                    Text(
                                        text = "$doneCount of $fileCount files",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accent,
                                    )
                                }
                            }
                        }
                    }
                    // Waiting / offered / accepted: status text with an indeterminate bar.
                    TransferPhase.PENDING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = transferStatusLine(transfer),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            LinearWavyProgressIndicator(
                                color = accent,
                                trackColor = colors.surfaceContainerHighest,
                                stroke = barStroke,
                                trackStroke = barStroke,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TransferBarHeight),
                            )
                        }
                    }
                    // Finished: status chip, size / average speed / duration.
                    TransferPhase.DONE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusChip(
                                    text = if (sent) "Delivered" else "Received",
                                    icon = R.drawable.ic_check_circle,
                                    container = if (sent) colors.primaryContainer else colors.tertiaryContainer,
                                    content = if (sent) colors.onPrimaryContainer else colors.onTertiaryContainer,
                                )
                                if (transfer.totalBytes > 0) MetaChip(transfer.totalBytes.readableBytes())
                                transfer.averageBytesPerSecond?.let { avg ->
                                    MetaChip("${avg.readableBytes()}/s avg")
                                    val avgValue = avg.toDouble()
                                    if (avgValue > 0.0 && transfer.totalBytes > 0) {
                                        MetaChip(
                                            "took ${formatDuration(kotlin.math.ceil(transfer.totalBytes / avgValue).toLong())}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TransferPhase.FAILED -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.errorContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 4.dp),
                        ) {
                            Text(
                                text = transferStatusLine(transfer),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            if (transfer.files.isNotEmpty() &&
                !(transfer.direction == TransferDirection.SENT &&
                    transfer.files.size == 1 &&
                    transfer.status.isActive)
            ) {
                TransferFileList(
                    transfer = transfer,
                    expanded = expanded,
                    onToggle = onToggleGroup,
                    onOpenFile = onOpenFile,
                )
            }

            if (complete &&
                transfer.direction == TransferDirection.RECEIVED &&
                transfer.files.isNotEmpty() &&
                (onShareGroup != null || onDeleteGroup != null)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onShareGroup?.let {
                        FilledTonalButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Share")
                        }
                    }
                    onDeleteGroup?.let {
                        FilledTonalButton(
                            onClick = it,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.errorContainer,
                                contentColor = colors.onErrorContainer,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Delete")
                        }
                    }
                }
            }

            if (complete &&
                transfer.direction == TransferDirection.RECEIVED &&
                transfer.files.isEmpty() &&
                transfer.localPath != null &&
                (onOpen != null || onShare != null || onDelete != null)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onShare?.let {
                        FilledTonalButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Share")
                        }
                    }
                    onDelete?.let {
                        FilledTonalButton(
                            onClick = it,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = colors.errorContainer,
                                contentColor = colors.onErrorContainer,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Delete")
                        }
                    }
                }
            }

            transfer.completedAt?.let { ended ->
                Text(
                    text = formatEndedAt(
                        when (status) {
                            TransferStatus.CANCELLED -> "Cancelled"
                            TransferStatus.FAILED -> "Failed"
                            else -> "Finished"
                        },
                        ended,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun fileQueueRank(file: TransferFile): Int = when (file.status) {
    TransferStatus.TRANSFERRING -> 0
    TransferStatus.WAITING,
    TransferStatus.OFFERED,
    TransferStatus.ACCEPTED -> 1
    else -> 2
}

/** The one just-finished row that gets a short blink before leaving the queue. */
private data class RecentDone(val id: String, val at: Long)

private const val BlinkHalfPeriodMs = 250
private const val BlinkCount = 3
private const val BlinkLifetimeMs = 1_600L

/** Shown in place of the file type icon for the three blinks of a just-added file. */
@DrawableRes
private val DoneIconRes = R.drawable.ic_check_circle

/**
 * The slide a row still has to play after it was re-slotted (e.g. progress -> done).
 *
 * The offset is written synchronously in the same frame the order changes, so a row never
 * flashes in its final slot first. The decay runs on its own coroutine, so every row slides
 * at the same time instead of waiting for the row before it to settle.
 */
private class SlotShift {
    var px by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null

    /** Adds [deltaPx] on top of whatever is left of a running slide and eases back to 0. */
    fun shiftBy(deltaPx: Float, scope: CoroutineScope) {
        job?.cancel()
        px += deltaPx
        job = scope.launch {
            animate(
                initialValue = px,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
            ) { value, _ -> px = value }
        }
    }
}

/** The order and visible set the list was last laid out with. */
private class SlotLayout(var order: List<String>, var visible: List<String>)

/**
 * A fixed-height row slot that keeps its visibility animation state while the ordered list
 * changes. [shift] slides a row from its old slot to its new queue/done slot; the bouncy
 * show/hide stays on the AnimatedVisibility.
 */
@Composable
private fun AnimatedFileSlot(
    file: TransferFile,
    received: Boolean,
    visible: Boolean,
    targetIndex: Int,
    hasHeader: Boolean,
    justCompleted: Boolean,
    onOpen: (() -> Unit)?,
    shift: SlotShift,
    visibility: MutableTransitionState<Boolean>,
    stagedStyle: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    visibility.targetState = visible
    // Remember the last real slot so a row that is leaving keeps its gap while it shrinks
    // instead of jumping 2dp when its index becomes -1.
    val paddedIndex = remember { intArrayOf(targetIndex) }
    if (targetIndex >= 0) paddedIndex[0] = targetIndex
    Box(
        modifier = Modifier.offset {
            IntOffset(0, shift.px.roundToInt())
        },
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(220, delayMillis = 40)) +
                expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Top,
                ),
            exit = fadeOut(tween(160)) +
                shrinkVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
        ) {
            Box(
                modifier = Modifier.padding(
                    top = if (hasHeader || paddedIndex[0] > 0) TransferItemGap else 0.dp,
                ),
            ) {
                TransferFileRow(
                    file = file,
                    received = received,
                    justCompleted = justCompleted,
                    onOpen = onOpen,
                    onRemove = onRemove,
                    stagedStyle = stagedStyle,
                )
            }
        }
    }
}

/**
 * The per-file list of a transfer, laid out like the Settings groups: one clipped 16dp group,
 * 4dp-cornered items, 2dp gaps. Every file stays composed and slides/fades in or out as it
 * enters or leaves the visible set (e.g. a finished file leaves the live list).
 */
@Composable
private fun TransferFileList(
    transfer: TransferItem,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    onOpenFile: ((TransferFile) -> Unit)?,
) {
    val received = transfer.direction == TransferDirection.RECEIVED
    val active = transfer.status.isActive
    val files = transfer.files
    val hasHeader = files.size > 1 && onToggle != null
    // Keep the file that is moving at the top, queued files next, and
    // finished files at the bottom of an expanded group.
    val orderedFiles = files.sortedWith(
        compareBy<TransferFile> { fileQueueRank(it) }
            .thenByDescending { it.completedAt ?: 0L },
    )
    val scope = rememberCoroutineScope()
    var recentDone by remember { mutableStateOf<RecentDone?>(null) }
    val expandedBlinks = remember { mutableStateMapOf<String, Long>() }
    val previousStatuses = remember { mutableMapOf<String, TransferStatus>() }

    LaunchedEffect(files, active, expanded) {
        val ids = files.mapTo(mutableSetOf()) { it.id }
        previousStatuses.keys.retainAll(ids)
        files.forEach { file ->
            val previous = previousStatuses[file.id]
            if (previous != null && previous.isActive &&
                file.status == TransferStatus.COMPLETE
            ) {
                val stamp = SystemClock.elapsedRealtime()
                if (expanded) {
                    // In the expanded list every newly finished row gets its
                    // own one-second blink, but stays in place.
                    expandedBlinks[file.id] = stamp
                    scope.launch {
                        delay(BlinkLifetimeMs)
                        if (expandedBlinks[file.id] == stamp) {
                            expandedBlinks.remove(file.id)
                        }
                    }
                } else if (active) {
                    // Collapsed: only the newest finished file gets the longer
                    // blink and the bottom row. An older hold is replaced.
                    recentDone = RecentDone(file.id, stamp)
                    scope.launch {
                        delay(BlinkLifetimeMs)
                        if (recentDone?.id == file.id && recentDone?.at == stamp) {
                            recentDone = null
                        }
                    }
                }
            }
            previousStatuses[file.id] = file.status
        }
        if (!active) {
            recentDone = null
            expandedBlinks.clear()
        } else if (expanded) {
            recentDone = null
        } else {
            expandedBlinks.clear()
        }
    }

    val activeIds = orderedFiles.filter { it.status.isActive }.map { it.id }
    val recentDoneId = recentDone?.id
        ?.takeIf { id -> orderedFiles.any { it.id == id && it.status == TransferStatus.COMPLETE } }
    // On the first frame after a completion, `recentDone` has not been set by
    // LaunchedEffect yet. Keep that terminal row visible immediately so it can
    // animate from its live slot into the done slot instead of disappearing
    // and reappearing.
    val pendingDoneId = files
        .filter { it.status.isTerminal && previousStatuses[it.id]?.isActive == true }
        .maxByOrNull { it.completedAt ?: 0L }
        ?.id
    val visibleDoneId = recentDoneId ?: pendingDoneId
    val visibleIds: List<String> = when {
        expanded -> orderedFiles.map { it.id }
        // While collapsed, keep the current row and the actual next queued row
        // visible, and use the third slot for the just-finished blink. This
        // keeps the group at exactly three rows.
        active && visibleDoneId != null -> activeIds.take(2) + visibleDoneId
        active -> activeIds.take(3)
        else -> emptyList()
    }
    val visibleSet = visibleIds.toHashSet()
    val orderedIds = orderedFiles.map { it.id }
    val density = LocalDensity.current
    val stridePx = with(density) { (TransferFileRowHeight + TransferItemGap).toPx() }
    val shifts = remember { mutableMapOf<String, SlotShift>() }
    val visibilityStates = remember {
        mutableMapOf<String, MutableTransitionState<Boolean>>()
    }
    val lastLayout = remember { SlotLayout(orderedIds, visibleIds) }

    // Runs after composition but before layout of the same frame, so the slide starts
    // from the old position with no one-frame jump.
    //
    // Every row that was taking up space keeps its old slot (its index among the rows that
    // were visible) and is moved to where the new order puts it, counting only rows that
    // already had height. Rows that are appearing or disappearing are left to their own
    // bouncy expand/shrink, and they push their neighbours naturally. That is why expanding
    // or collapsing the group no longer throws the finished row up and over the others.
    SideEffect {
        if (lastLayout.order != orderedIds || lastLayout.visible != visibleIds) {
            val oldSlot = HashMap<String, Int>()
            val wasVisible = lastLayout.visible.toHashSet()
            lastLayout.order
                .filter { it in wasVisible }
                .forEachIndexed { index, id -> oldSlot[id] = index }
            var occupiedAbove = 0
            orderedIds.forEach { id ->
                val oldIndex = oldSlot[id]
                if (oldIndex != null) {
                    val delta = oldIndex - occupiedAbove
                    if (delta != 0) {
                        shifts.getOrPut(id) { SlotShift() }.shiftBy(delta * stridePx, scope)
                    }
                    occupiedAbove++
                }
            }
            val idSet = orderedIds.toHashSet()
            shifts.keys.retainAll(idSet)
            visibilityStates.keys.retainAll(idSet)
            lastLayout.order = orderedIds
            lastLayout.visible = visibleIds
        }
    }

    // The card around this list already eases its own height with animateContentSize, and
    // every row animates its own height with the bouncy expand/shrink. A third
    // animateContentSize here made the group trail behind the rows and clip their bounce,
    // which is what made the queue feel delayed.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TransferGroupShape),
    ) {
        if (hasHeader && onToggle != null) {
            FileGroupHeader(
                done = files.count { it.status == TransferStatus.COMPLETE },
                total = files.size,
                active = active,
                received = received,
                expanded = expanded,
                onClick = onToggle,
            )
        }
        // Keep every row composed. This is what lets the bouncy expand run for
        // both unfinished and finished rows, while the slot shift animates
        // progress-to-done movement.
        orderedFiles.forEach { file ->
            key(file.id) {
                val visible = file.id in visibleSet
                val visibility = visibilityStates.getOrPut(file.id) {
                    MutableTransitionState(visible)
                }
                val shift = shifts.getOrPut(file.id) { SlotShift() }
                AnimatedFileSlot(
                    file = file,
                    received = received,
                    visible = visible,
                    targetIndex = visibleIds.indexOf(file.id),
                    hasHeader = hasHeader,
                    justCompleted = !file.status.isActive &&
                        (recentDone?.id == file.id ||
                            expandedBlinks.containsKey(file.id)),
                    onOpen = if (onOpenFile != null &&
                        file.status == TransferStatus.COMPLETE
                    ) {
                        { onOpenFile(file) }
                    } else {
                        null
                    },
                    shift = shift,
                    visibility = visibility,
                )
            }
        }
    }
}

/** First item of the group: "2 of 5 files done" + a Show all / Hide toggle with a turning chevron. */
@Composable
private fun FileGroupHeader(
    done: Int,
    total: Int,
    active: Boolean,
    received: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    toggleable: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (received) colors.tertiary else colors.primary
    val tint = (if (received) colors.tertiaryContainer else colors.primaryContainer)
        .copy(alpha = 0.6f)
        .compositeOver(colors.surfaceContainerLow)
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "groupChevron",
    )
    Surface(
        shape = TransferItemShape,
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                // The finished count rolls upward each time another file completes.
                AnimatedContent(
                    targetState = done,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { it } + fadeIn(tween(260)))
                            .togetherWith(slideOutVertically(tween(260)) { -it } + fadeOut(tween(160)))
                    },
                    label = "doneCount",
                ) { count ->
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = accent,
                    )
                }
                Text(
                    text = " of $total files done",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
            } else {
                Text(
                    text = "$total files",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            if (toggleable) {
                Text(
                    text = if (expanded) "Hide" else "Show all",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevron),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransferFileRow(
    file: TransferFile,
    received: Boolean,
    justCompleted: Boolean,
    onOpen: (() -> Unit)?,
    onRemove: (() -> Unit)? = null,
    /** Sheet rows: no trailing done mark; the file icon turns into a check during the blink. */
    stagedStyle: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val transferring = file.status == TransferStatus.TRANSFERRING
    val queued = file.status == TransferStatus.WAITING ||
        file.status == TransferStatus.OFFERED ||
        file.status == TransferStatus.ACCEPTED
    val failed = file.status == TransferStatus.FAILED || file.status == TransferStatus.CANCELLED
    val percent = if (file.totalBytes > 0) {
        (file.transferredBytes * 100 / file.totalBytes).toInt().coerceIn(0, 100)
    } else {
        null
    }
    val accent = if (received) colors.tertiary else colors.primary
    val accentContainer = if (received) colors.tertiaryContainer else colors.primaryContainer

    val fraction by animateFloatAsState(
        targetValue = if (file.totalBytes > 0) {
            (file.transferredBytes.toFloat() / file.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
            visibilityThreshold = 0.001f,
        ),
        label = "fileProgress",
    )
    // The file being transferred right now gets a soft accent wash that fades back out
    // when it finishes.
    val baseRowColor by animateColorAsState(
        targetValue = if (transferring) {
            accentContainer.copy(alpha = 0.45f).compositeOver(colors.surfaceContainerHigh)
        } else {
            colors.surfaceContainerHigh
        },
        animationSpec = tween(350),
        label = "fileRowColor",
    )
    // Exactly three smooth on/off pulses, then back to the normal row color.
    val blink = remember { Animatable(0f) }
    var checkShown by remember { mutableStateOf(false) }
    LaunchedEffect(justCompleted) {
        blink.snapTo(0f)
        checkShown = stagedStyle && justCompleted
        if (justCompleted) {
            repeat(BlinkCount) {
                blink.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(BlinkHalfPeriodMs, easing = LinearEasing),
                )
                blink.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(BlinkHalfPeriodMs, easing = LinearEasing),
                )
            }
            // Three blinks done: the check turns back into the file type icon.
            checkShown = false
        }
    }
    val rowColor = if (blink.value > 0f) {
        lerp(
            baseRowColor,
            accentContainer.copy(alpha = 0.65f).compositeOver(colors.surfaceContainerHigh),
            blink.value * 0.7f,
        )
    } else {
        baseRowColor
    }
    val bubbleColor by animateColorAsState(
        targetValue = if (failed) colors.errorContainer else accentContainer,
        animationSpec = tween(300),
        label = "fileBubble",
    )
    val density = LocalDensity.current
    val ringStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round)
    }

    Surface(
        shape = TransferItemShape,
        color = rowColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(TransferFileRowHeight)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(60.dp),
            ) {
                if (queued) {
                    // Queued rows use the expressive loading indicator as the
                    // icon container. It is intentionally larger than the
                    // circular progress bar; the file glyph stays the same.
                    LoadingIndicator(
                        // This is the icon container, not a status/progress
                        // accent: keep the same background colour as the
                        // circular file bubble.
                        color = bubbleColor,
                        modifier = Modifier.size(60.dp),
                    )
                    Icon(
                        painter = painterResource(fileIcon(file.name)),
                        contentDescription = null,
                        tint = if (received) {
                            colors.onTertiaryContainer
                        } else {
                            colors.onPrimaryContainer
                        },
                        modifier = Modifier.size(19.dp),
                    )
                } else {
                    // The progress ring exists only while this file is in flight.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = transferring,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f),
                        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.7f),
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { fraction },
                            color = accent,
                            trackColor = colors.surfaceContainerHighest.copy(alpha = 0.6f),
                            stroke = ringStroke,
                            trackStroke = ringStroke,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = bubbleColor,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Crossfade(
                                targetState = if (checkShown) DoneIconRes else fileIcon(file.name),
                                animationSpec = tween(160),
                                label = "fileIcon",
                            ) { iconRes ->
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = if (failed) {
                                        colors.onErrorContainer
                                    } else if (received) {
                                        colors.onTertiaryContainer
                                    } else {
                                        colors.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (file.status) {
                        TransferStatus.COMPLETE -> file.totalBytes.readableBytes()
                        TransferStatus.TRANSFERRING -> if (file.totalBytes > 0) {
                            "${file.transferredBytes.readableBytes()} of ${file.totalBytes.readableBytes()}"
                        } else {
                            "Transferring…"
                        }
                        TransferStatus.FAILED -> "Failed"
                        TransferStatus.CANCELLED -> "Cancelled"
                        else -> "Queued"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Percent only while in progress. Finished files show no mark at all.
            AnimatedVisibility(
                visible = transferring,
                enter = fadeIn(tween(200)) + expandHorizontally(),
                exit = fadeOut(tween(150)) + shrinkHorizontally(),
            ) {
                Text(
                    text = percent?.let { "$it%" } ?: "…",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(44.dp)
                        .padding(start = 4.dp),
                )
            }
            when (file.status) {
                TransferStatus.COMPLETE -> if (!stagedStyle) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = "Complete",
                        tint = colors.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                TransferStatus.CANCELLED -> Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Cancelled",
                    tint = colors.error,
                    modifier = Modifier.size(21.dp),
                )
                TransferStatus.FAILED -> Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = "Failed",
                    tint = colors.error,
                    modifier = Modifier.size(21.dp),
                )
                else -> {}
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Remove ${file.name}",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
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
    TransferStatus.FAILED -> humanizeTransferError(transfer.error)
    TransferStatus.CANCELLED -> humanizeTransferError(transfer.error)
}

/** Short, plain-language failure text instead of raw helper/protocol errors. */
private fun humanizeTransferError(error: String?): String {
    val text = error?.trim().orEmpty()
    if (text.isBlank()) return "Transfer failed. Try again."
    val lower = text.lowercase(Locale.getDefault())
    return when {
        lower.contains("cancel") -> "Cancelled"
        lower.contains("declin") -> "Declined"
        lower.contains("timed out") || lower.contains("timeout") ->
            "Couldn’t connect. Try again."
        lower.contains("connection closed") ||
            lower.contains("connection refused") ||
            lower.contains("connection reset") ||
            lower.contains("connection aborted") ->
            "Connection lost. Try again."
        lower.contains("no airdrop receiver") ||
            lower.contains("no peer") ||
            lower.contains("didn't respond") ->
            "Phone didn’t respond. Try again."
        lower.contains("helper session") ->
            "Connection lost. Try again."
        else -> "Transfer failed. Try again."
    }
}

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun IncomingRequestDialog(controller: CrossPairController) {
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

/** Space the list keeps free at its end so the last row can scroll clear of the attach buttons. */
private val AttachBarClearance = 88.dp

/** Height of the soft fade that keeps rows from colliding visually with the attach buttons. */
private val AttachFadeHeight = 80.dp

/** Fade height on the main pages, on top of the navigation-bar inset, to clear the floating toolbar. */
private val MainPageFadeHeight = 104.dp

// Alpha ramps for [fadingEdges]. Several stops give an eased ramp instead of a flat linear one.
private val FadeOutStops = arrayOf(
    0f to Color.Black,
    0.30f to Color.Black.copy(alpha = 0.86f),
    0.60f to Color.Black.copy(alpha = 0.42f),
    0.85f to Color.Black.copy(alpha = 0.10f),
    1f to Color.Transparent,
)
private val FadeInStops = arrayOf(
    0f to Color.Transparent,
    0.15f to Color.Black.copy(alpha = 0.10f),
    0.40f to Color.Black.copy(alpha = 0.42f),
    0.70f to Color.Black.copy(alpha = 0.86f),
    1f to Color.Black,
)

/**
 * Fades the content to real transparency at the top and/or bottom edge (alpha mask, not a
 * painted overlay), so whatever is behind the page shows through.
 */
private fun Modifier.fadingEdges(top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = top.toPx().coerceIn(0f, size.height / 2f)
            val bottomPx = bottom.toPx().coerceIn(0f, size.height / 2f)
            if (topPx > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(*FadeInStops, startY = 0f, endY = topPx),
                    size = Size(size.width, topPx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomPx > 0f) {
                val startY = size.height - bottomPx
                drawRect(
                    brush = Brush.verticalGradient(*FadeOutStops, startY = startY, endY = size.height),
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, bottomPx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/**
 * Attach menu order, top to bottom. [FloatingActionButtonMenu] lays items out in declaration
 * order with the last one closest to the toggle, so Files sits right where the collapsed
 * Files button was.
 */
private val AttachMenuActions: List<PeerAction> =
    PeerAction.entries.filter { it != PeerAction.FILES }.asReversed() + PeerAction.FILES

/**
 * Current top edge of the sheet in pixels, measured from the top of the window.
 * Returns 0 while the sheet has no anchors yet (first frame, previews).
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun SheetState.topOffsetPx(): Int {
    val offset = try {
        requireOffset()
    } catch (_: IllegalStateException) {
        0f
    }
    return if (offset.isNaN()) 0 else offset.roundToInt()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PeerActionsSheet(
    peer: Peer,
    lost: Boolean = false,
    channel: Int?,
    items: List<StagedItem>,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onRemoveItem: (StagedItem) -> Unit,
    onAddFiles: () -> Unit,
    onAction: (PeerAction) -> Unit,
    onSend: () -> Unit,
    stagingJobs: List<StagingJob> = emptyList(),
    onRemoveStaging: (Long) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var fabExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The attach menu stacks seven chips above the buttons. That needs more room than the
    // partially expanded sheet has, so grow the sheet while the menu opens.
    LaunchedEffect(fabExpanded) {
        if (fabExpanded && sheetState.currentValue != SheetValue.Expanded) {
            sheetState.expand()
        }
    }

    // contentWindowInsets is left at the Material default on purpose: the sheet consumes the
    // status-bar inset as it reaches the top, so the drag handle no longer sits under the clock.
    ModalBottomSheet(
        // While the attach menu is open, a tap outside closes the menu instead of the sheet.
        onDismissRequest = { if (fabExpanded) fabExpanded = false else onDismiss() },
        sheetState = sheetState,
        // Themed, translucent scrim instead of the default black dim.
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
        // No swipe-to-collapse/dismiss while the attach menu is open.
        sheetGesturesEnabled = !fabExpanded,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        PeerSheetContent(
            peer = peer,
            lost = lost,
            channel = channel,
            selectedItems = items,
            pinned = pinned,
            listState = listState,
            sheetState = sheetState,
            fabExpanded = fabExpanded,
            onFabExpandedChange = { fabExpanded = it },
            onTogglePin = onTogglePin,
            onRemoveItem = onRemoveItem,
            onAddFiles = onAddFiles,
            onAction = onAction,
            onSend = onSend,
            stagingJobs = stagingJobs,
            onRemoveStaging = onRemoveStaging,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PeerSheetContent(
    peer: Peer,
    lost: Boolean = false,
    channel: Int?,
    selectedItems: List<StagedItem>,
    pinned: Boolean,
    listState: LazyListState,
    sheetState: SheetState,
    fabExpanded: Boolean,
    onFabExpandedChange: (Boolean) -> Unit,
    onTogglePin: () -> Unit,
    onRemoveItem: (StagedItem) -> Unit,
    onAddFiles: () -> Unit,
    onAction: (PeerAction) -> Unit,
    onSend: () -> Unit,
    stagingJobs: List<StagingJob> = emptyList(),
    onRemoveStaging: (Long) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var largeHeaderHeightPx by remember { mutableIntStateOf(0) }

    // Registered inside the sheet window so Back closes the menu before it dismisses the sheet.
    BackHandler(enabled = fabExpanded) { onFabExpandedChange(false) }

    // 0f = large header fully visible, 1f = large header scrolled away and the compact header
    // fully shown. The value is scroll-linked, so the swap animates with the finger.
    val headerCollapse = remember(listState) {
        derivedStateOf {
            val height = largeHeaderHeightPx
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                height <= 0 -> 0f
                else -> {
                    val start = height * 0.35f
                    val end = height * 0.85f
                    ((listState.firstVisibleItemScrollOffset - start) / (end - start))
                        .coerceIn(0f, 1f)
                }
            }
        }
    }
    val compactShown by remember(headerCollapse) {
        derivedStateOf { headerCollapse.value > 0.01f }
    }

    // Fade the buttons out while the sheet is being dismissed so they don't hang in the air.
    val attachAlpha = animateFloatAsState(
        targetValue = if (
            sheetState.targetValue == SheetValue.Hidden &&
            sheetState.currentValue != SheetValue.Hidden
        ) {
            0f
        } else {
            1f
        },
        animationSpec = tween(durationMillis = 120),
        label = "attach alpha",
    )

    // The sheet is laid out at full height and then translated down while it is partially
    // expanded. Sizing the content to the part that is actually on screen keeps the list
    // viewport, the bottom fade and the attach buttons aligned to the real bottom edge in
    // every state, without any counter-offset hack on the buttons.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val bounded = constraints.hasBoundedHeight
                val full = if (bounded) constraints.maxHeight else 0
                val childConstraints = if (bounded) {
                    val visible = (full - sheetState.topOffsetPx())
                        .coerceIn(constraints.minHeight, full)
                    constraints.copy(minHeight = visible, maxHeight = visible)
                } else {
                    constraints
                }
                val placeable = measurable.measure(childConstraints)
                layout(placeable.width, if (bounded) full else placeable.height) {
                    placeable.place(0, 0)
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AttachBarClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "largePeerHeader") {
                Box(
                    modifier = Modifier
                        .onSizeChanged { largeHeaderHeightPx = it.height }
                        .graphicsLayer { alpha = 1f - headerCollapse.value },
                ) {
                    LargePeerHeader(
                        peer = peer,
                        lost = lost,
                        channel = channel,
                        itemCount = selectedItems.size,
                        pinned = pinned,
                        onTogglePin = onTogglePin,
                        onSend = onSend,
                    )
                }
            }
            // The group is always in the list, so a removed last row can still shrink away.
            item(key = "files-group") {
                FilesGroup(
                    staged = selectedItems,
                    jobs = stagingJobs,
                    onRemoveStaged = onRemoveItem,
                    onRemoveStaging = onRemoveStaging,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 20.dp),
                )
            }
            if (selectedItems.isEmpty() && stagingJobs.isEmpty()) {
                item(key = "emptyItems") {
                    EmptyItemsState(modifier = Modifier.animateItem().padding(horizontal = 20.dp))
                }
            }
        }

        // Rows fade out softly before they reach the buttons instead of being cut by a hard edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AttachFadeHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.surfaceContainerLow.copy(alpha = 0f),
                            colors.surfaceContainerLow,
                        ),
                    ),
                ),
        )

        // Small header, cross-faded in as the large one scrolls away. It stays pinned to the
        // top of the visible sheet area in both the partially and fully expanded states.
        if (compactShown) {
            CompactPeerHeader(
                peer = peer,
                lost = lost,
                channel = channel,
                itemCount = selectedItems.size,
                onSend = onSend,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val progress = headerCollapse.value
                        alpha = progress
                        translationY = -(1f - progress) * 10.dp.toPx()
                    },
            )
        }

        AttachScrim(
            visible = fabExpanded,
            onDismiss = { onFabExpandedChange(false) },
        )

        AttachControls(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .graphicsLayer { alpha = attachAlpha.value },
            fabExpanded = fabExpanded,
            onFabExpandedChange = onFabExpandedChange,
            onAddFiles = onAddFiles,
            onAction = onAction,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LargePeerHeader(
    peer: Peer,
    lost: Boolean = false,
    channel: Int?,
    itemCount: Int,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val avatarColor = when (peer.devclass) {
        2 -> colors.tertiaryContainer
        1 -> colors.secondaryContainer
        else -> colors.primaryContainer
    }
    val avatarTint = when (peer.devclass) {
        2 -> colors.onTertiaryContainer
        1 -> colors.onSecondaryContainer
        else -> colors.onPrimaryContainer
    }
    val icon = when (peer.devclass) {
        2 -> R.drawable.ic_smartphone
        1 -> R.drawable.ic_computer
        else -> R.drawable.ic_wifi_tethering
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = avatarColor,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Swaps to a loading indicator while the phone is out of range, as if waiting
                // for the signal to come back.
                Crossfade(targetState = lost, label = "peer avatar") { isLost ->
                    if (isLost) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = avatarTint,
                        )
                    } else {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = avatarTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = peer.displayName(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (lost) PEER_LOST_MESSAGE else peerSubtitle(peer, channel),
            style = MaterialTheme.typography.bodySmall,
            color = if (lost) colors.error else colors.onSurfaceVariant,
        )
        TextButton(
            onClick = onTogglePin,
            modifier = Modifier.heightIn(min = 34.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (pinned) "Pinned phone" else "Pin this phone")
        }
        Button(
            onClick = onSend,
            enabled = itemCount > 0 && !lost && peer.airdropReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (itemCount == 0) "Send" else "Send $itemCount files")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompactPeerHeader(
    peer: Peer,
    lost: Boolean = false,
    channel: Int?,
    itemCount: Int,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val avatarColor = when (peer.devclass) {
        2 -> colors.tertiaryContainer
        1 -> colors.secondaryContainer
        else -> colors.primaryContainer
    }
    val avatarTint = when (peer.devclass) {
        2 -> colors.onTertiaryContainer
        1 -> colors.onSecondaryContainer
        else -> colors.onPrimaryContainer
    }
    val icon = when (peer.devclass) {
        2 -> R.drawable.ic_smartphone
        1 -> R.drawable.ic_computer
        else -> R.drawable.ic_wifi_tethering
    }
    Surface(
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = avatarColor,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Crossfade(targetState = lost, label = "peer avatar compact") { isLost ->
                            if (isLost) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = avatarTint,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(icon),
                                    contentDescription = null,
                                    tint = avatarTint,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peer.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (lost) PEER_LOST_MESSAGE else peerSubtitle(peer, channel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lost) colors.error else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onSend,
                    enabled = itemCount > 0 && !lost && peer.airdropReady,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(if (itemCount == 0) "Send" else "Send $itemCount")
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

private const val PEER_LOST_MESSAGE = "No longer nearby · waiting for the signal…"

private fun peerSubtitle(peer: Peer, channel: Int?): String {
    if (peer.airdropState == AirdropState.NOT_READY) {
        return "Nearby · AirDrop not ready"
    }
    if (peer.airdropState == AirdropState.UNKNOWN) {
        return "Nearby · checking AirDrop…"
    }
    return buildString {
        append(peer.typeLabel())
        append(" · ")
        append(peer.rssi)
        append(" dBm")
        if (channel != null) {
            append(" · channel ")
            append(channel)
        }
    }
}

@Composable
private fun FileRow(item: StagedItem, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(fileIcon(item.name)),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = middleEllipsis(item.name, 30),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = item.size.readableBytes(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Remove ${item.name}",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Wash behind the attach menu. It is tinted with the themed sheet background (not black) and
 * stays translucent, so the content fades back without the sheet getting darker. Tapping it
 * closes the menu.
 */
@Composable
private fun AttachScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val washColor = MaterialTheme.colorScheme.surfaceContainerLow
    val washAlpha = animateFloatAsState(
        targetValue = if (visible) 0.72f else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "attach scrim",
    )
    // Stay composed until the fade-out has finished.
    if (visible || washAlpha.value > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(washColor.copy(alpha = washAlpha.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = visible,
                    onClick = onDismiss,
                ),
        )
    }
}

private val AttachButtonHeight = 56.dp
private val AttachOuterCorner = AttachButtonHeight / 2
private val AttachInnerCorner = 4.dp
private val AttachInnerCornerPressed = 2.dp

/**
 * Split button following the Material 3 spec: a leading action ("Files") and a trailing toggle
 * joined by a 2dp gap, with small inner corners. The trailing part morphs to a full circle while
 * checked and its chevron turns 180 degrees.
 *
 * The two halves are drawn here (rather than with SplitButtonDefaults) so the height is always
 * [AttachButtonHeight], the corners are always applied and the toggle stays visible while the
 * leading part is collapsed.
 */
@Composable
private fun AttachSplitButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Read only inside graphicsLayer, so the chevron animates without recomposing.
    val toggle = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "attach toggle",
    )
    val leadingSource = remember { MutableInteractionSource() }
    val trailingSource = remember { MutableInteractionSource() }
    val leadingPressed by leadingSource.collectIsPressedAsState()
    val trailingPressed by trailingSource.collectIsPressedAsState()
    val cornerSpec = spring<Dp>(dampingRatio = 0.6f, stiffness = 500f)
    val leadingInner by animateDpAsState(
        targetValue = if (leadingPressed) AttachInnerCornerPressed else AttachInnerCorner,
        animationSpec = cornerSpec,
        label = "leading inner corner",
    )
    val trailingStart by animateDpAsState(
        targetValue = when {
            expanded -> AttachOuterCorner
            trailingPressed -> AttachInnerCornerPressed
            else -> AttachInnerCorner
        },
        animationSpec = cornerSpec,
        label = "trailing start corner",
    )
    val gap by animateDpAsState(
        targetValue = if (expanded) 0.dp else 2.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "split gap",
    )
    val trailingContainer by animateColorAsState(
        targetValue = if (expanded) colors.primary else colors.secondaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "split trailing container",
    )
    val trailingContent by animateColorAsState(
        targetValue = if (expanded) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "split trailing content",
    )

    Row(
        modifier = modifier.height(AttachButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(tween(160)) + expandHorizontally(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                expandFrom = Alignment.End,
            ),
            exit = fadeOut(tween(120)) + shrinkHorizontally(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.End,
            ),
        ) {
            Surface(
                onClick = onFiles,
                modifier = Modifier.height(AttachButtonHeight),
                shape = RoundedCornerShape(
                    topStart = AttachOuterCorner,
                    topEnd = leadingInner.coerceAtLeast(0.dp),
                    bottomEnd = leadingInner.coerceAtLeast(0.dp),
                    bottomStart = AttachOuterCorner,
                ),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                shadowElevation = 4.dp,
                interactionSource = leadingSource,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 22.dp, end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_attach_file),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        Surface(
            checked = expanded,
            onCheckedChange = onExpandedChange,
            modifier = Modifier
                .size(AttachButtonHeight)
                .semantics {
                    contentDescription = if (expanded) {
                        "Close attach menu"
                    } else {
                        "Open attach menu"
                    }
                },
            shape = RoundedCornerShape(
                topStart = trailingStart.coerceAtLeast(0.dp),
                topEnd = AttachOuterCorner,
                bottomEnd = AttachOuterCorner,
                bottomStart = trailingStart.coerceAtLeast(0.dp),
            ),
            color = trailingContainer,
            contentColor = trailingContent,
            shadowElevation = 4.dp,
            interactionSource = trailingSource,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_less),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = 180f * toggle.value },
                )
            }
        }
    }
}

/**
 * The attach button and its menu, in one [FloatingActionButtonMenu] so the chips are laid out
 * by the menu itself and always share the trailing edge of the toggle. The menu is composed
 * inline in the sheet window (no Popup), so there is no extra window or pixel offset to drift.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttachControls(
    modifier: Modifier = Modifier,
    fabExpanded: Boolean,
    onFabExpandedChange: (Boolean) -> Unit,
    onAddFiles: () -> Unit,
    onAction: (PeerAction) -> Unit,
) {
    FloatingActionButtonMenu(
        expanded = fabExpanded,
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        button = {
            AttachSplitButton(
                expanded = fabExpanded,
                onExpandedChange = onFabExpandedChange,
                onFiles = onAddFiles,
            )
        },
    ) {
        AttachMenuActions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onFabExpandedChange(false)
                    onAction(action)
                },
                text = { Text(action.label) },
                icon = {
                    Icon(
                        painter = painterResource(action.icon),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

/** One row of the sheet's file list. */
private class StagedRow(val file: TransferFile, val onRemove: () -> Unit)

/** A row that left the list and stays composed until its shrink animation ends. */
private class RowGhost(val row: StagedRow, val index: Int)

/**
 * Staged files and files still being added, animated like the Send and Receive lists: rows
 * grow in with the bouncy expand, slide to their done slot when they finish, blink three times
 * (the file icon turns into a check for those blinks) and shrink away when removed.
 * There is no "n of n done" header.
 */
@Composable
private fun FilesGroup(
    staged: List<StagedItem>,
    jobs: List<StagingJob>,
    onRemoveStaged: (StagedItem) -> Unit,
    onRemoveStaging: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stridePx = with(density) { (TransferFileRowHeight + TransferItemGap).toPx() }

    // Live rows. A file that is ready replaces its own job row (same key), so one row follows
    // the file from "adding" to "ready". Files being added sit on top, ready files below.
    val stagedRows = staged.map { item -> StagedRow(item.asTransferFile()) { onRemoveStaged(item) } }
    val stagedKeys = stagedRows.mapTo(HashSet()) { it.file.id }
    val jobRows = jobs
        .map { job -> StagedRow(job.asTransferFile()) { onRemoveStaging(job.id) } }
        .filter { it.file.id !in stagedKeys }
    val live = (jobRows + stagedRows).sortedBy { fileQueueRank(it.file) }
    val liveIds = live.mapTo(HashSet()) { it.file.id }

    // Rows that just left stay composed as ghosts so they can shrink instead of vanishing.
    val initialIds = remember { liveIds.toSet() }
    val ghosts = remember { LinkedHashMap<String, RowGhost>() }
    val previous = remember { ArrayList<StagedRow>() }
    var ghostVersion by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val ghostTick = ghostVersion
    previous.forEachIndexed { index, row ->
        if (row.file.id !in liveIds && row.file.id !in ghosts) {
            ghosts[row.file.id] = RowGhost(StagedRow(row.file) {}, index)
        }
    }
    ghosts.keys.removeAll(liveIds)
    val display = ArrayList<StagedRow>(live)
    ghosts.values.sortedBy { it.index }.forEach { ghost ->
        display.add(ghost.index.coerceAtMost(display.size), ghost.row)
    }
    previous.clear()
    previous.addAll(display)

    val orderedIds = display.map { it.file.id }
    val visibleIds = display.map { it.file.id }.filter { it in liveIds }

    // Blink: a row that finishes (or a ready file that arrives on its own) blinks in place.
    val blinks = remember { mutableStateMapOf<String, Long>() }
    val seenStatuses = remember { mutableMapOf<String, TransferStatus>() }
    val statusKey = live.map { it.file.id to it.file.status }
    LaunchedEffect(statusKey) {
        seenStatuses.keys.retainAll(liveIds)
        live.forEach { row ->
            val file = row.file
            val before = seenStatuses[file.id]
            val finished = file.status == TransferStatus.COMPLETE && (
                (before != null && before.isActive) ||
                    (before == null && file.id !in initialIds)
                )
            if (finished) {
                val stamp = SystemClock.elapsedRealtime()
                blinks[file.id] = stamp
                scope.launch {
                    delay(BlinkLifetimeMs)
                    if (blinks[file.id] == stamp) blinks.remove(file.id)
                }
            }
            seenStatuses[file.id] = file.status
        }
    }

    // Same slot-shift bookkeeping as the transfer list: runs before layout, so a row that
    // changes slot slides from where it was instead of jumping.
    val shifts = remember { mutableMapOf<String, SlotShift>() }
    val visibilityStates = remember { mutableMapOf<String, MutableTransitionState<Boolean>>() }
    val lastLayout = remember { SlotLayout(orderedIds, visibleIds) }
    SideEffect {
        if (lastLayout.order != orderedIds || lastLayout.visible != visibleIds) {
            val oldSlot = HashMap<String, Int>()
            val wasVisible = lastLayout.visible.toHashSet()
            lastLayout.order
                .filter { it in wasVisible }
                .forEachIndexed { index, id -> oldSlot[id] = index }
            var occupiedAbove = 0
            orderedIds.forEach { id ->
                val oldIndex = oldSlot[id]
                if (oldIndex != null) {
                    val delta = oldIndex - occupiedAbove
                    if (delta != 0) {
                        shifts.getOrPut(id) { SlotShift() }.shiftBy(delta * stridePx, scope)
                    }
                    occupiedAbove++
                }
            }
            val idSet = orderedIds.toHashSet()
            shifts.keys.retainAll(idSet)
            visibilityStates.keys.retainAll(idSet)
            lastLayout.order = orderedIds
            lastLayout.visible = visibleIds
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TransferGroupShape),
    ) {
        display.forEach { row ->
            val id = row.file.id
            key(id) {
                val visible = id in liveIds
                // Rows present when the sheet opened start shown; later ones grow in.
                val visibility = visibilityStates.getOrPut(id) {
                    MutableTransitionState(id in initialIds)
                }
                val shift = shifts.getOrPut(id) { SlotShift() }
                AnimatedFileSlot(
                    file = row.file,
                    received = false,
                    visible = visible,
                    targetIndex = visibleIds.indexOf(id),
                    hasHeader = false,
                    justCompleted = blinks.containsKey(id),
                    onOpen = null,
                    shift = shift,
                    visibility = visibility,
                    stagedStyle = true,
                    onRemove = row.onRemove,
                )
                if (!visible) {
                    LaunchedEffect(Unit) {
                        snapshotFlow { visibility.isIdle && !visibility.currentState }.first { it }
                        ghosts.remove(id)
                        ghostVersion++
                    }
                }
            }
        }
    }
}

/** A staging row as the transfer row it looks like. */
private fun StagingJob.asTransferFile(): TransferFile {
    val total = size ?: 0L
    val moved = if (total > 0) (((progress ?: 0f) * total).toLong()).coerceAtMost(total) else 0L
    return TransferFile(
        id = "file-$id",
        name = name,
        totalBytes = total,
        transferredBytes = moved,
        status = when {
            done -> TransferStatus.COMPLETE
            started -> TransferStatus.TRANSFERRING
            else -> TransferStatus.WAITING
        },
    )
}

/** A staged file as a completed transfer row. */
private fun StagedItem.asTransferFile(): TransferFile = TransferFile(
    id = jobId?.let { "file-$it" } ?: "staged-$path",
    name = name,
    totalBytes = size,
    transferredBytes = size,
    status = TransferStatus.COMPLETE,
)

@Composable
private fun EmptyItemsState(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_attach_file),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Nothing selected yet",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = "Use Files or the chevron to add photos, videos, folders, or text.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun middleEllipsis(value: String, maxChars: Int): String {
    if (value.length <= maxChars) return value
    val keep = (maxChars - 1) / 2
    return value.take(keep) + "…" + value.takeLast(keep)
}

@DrawableRes
private fun fileIcon(name: String): Int {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
    return when (extension) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> R.drawable.ic_image
        "mp4", "mov", "mkv", "webm", "avi" -> R.drawable.ic_videocam
        "mp3", "m4a", "wav", "flac", "ogg", "aac" -> R.drawable.ic_music_note
        "zip", "7z", "rar", "tar", "gz" -> R.drawable.ic_folder
        else -> R.drawable.ic_description
    }
}

private suspend fun openReceivedFile(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "That file is no longer on this phone", Toast.LENGTH_SHORT).show()
        }
        return
    }
    val mime = mimeTypeFor(path)
    // Galleries only open MediaStore entries; a FileProvider URI gets
    // "Media not found" from them. Ask the scanner for the media URI first.
    val scanned = withTimeoutOrNull(3_000) {
        suspendCancellableCoroutine<Uri?> { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
    }
    val mediaUri = scanned?.takeIf { it.scheme == "content" }
    val uri = mediaUri ?: shareableUri(context, path)
    if (uri == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
        return
    }
    Log.i("CrossPairOpen", "open path=$path uri=$uri mime=$mime media=${mediaUri != null}")
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        try {
            context.startActivity(
                if (mediaUri != null) intent else Intent.createChooser(intent, "Open with"),
            )
        } catch (_: Exception) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareReceivedFile(context: Context, path: String) {
    shareReceivedFiles(context, listOf(path))
}

private suspend fun shareReceivedFiles(context: Context, paths: List<String>) {
    val uris = paths.mapNotNull { shareableUri(context, it) }
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(paths.first())
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    withContext(Dispatchers.Main) {
        try {
            context.startActivity(Intent.createChooser(intent, "Share received files"))
        } catch (_: Exception) {
            Toast.makeText(context, "No app can share these files", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * FileProvider exposes both the app cache and Download/CrossPair, so received
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
private fun SendScreen(controller: CrossPairController, scope: CoroutineScope) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { controller.pickFile(uri) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!controller.kernel.monitorReady) {
            item(key = "kernel-banner") {
                KernelBanner(controller, Modifier.animateItem().padding(horizontal = 20.dp))
            }
        }
        if (controller.kernel.state != MonitorState.STOCK) {
            item(key = "nearby-section") {
                NearbyPeersSection(
                    peers = controller.peers.filter { it.airdropReady },
                    selectedMac = controller.selectedMac,
                    isPinned = { false },
                    onPeerClick = { controller.selectPeer(it) },
                    scanningPaused = controller.settings.pauseDiscoveryDuringTransfer &&
                        (controller.sending || controller.receiveTransferring),
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "send-label") { SectionLabel("SEND", Modifier.animateItem().padding(horizontal = 20.dp)) }
        item(key = "send-card") {
            Box(Modifier.animateItem().padding(horizontal = 20.dp)) {
                SendCard(
                    controller = controller,
                    onChooseFile = { picker.launch(arrayOf("*/*")) },
                    onSend = { scope.launch { controller.sendPicked() } },
                    onCancelSend = { controller.cancelSend() },
                )
            }
        }
    }
}

/**
 * Nearby peers row. Cross-animates between the empty state and the list, and inside the list
 * every device fades/scales in when it shows up and fades out (with neighbours sliding over)
 * when it disappears.
 */
@Composable
private fun NearbyPeersSection(
    peers: List<Peer>,
    selectedMac: String?,
    isPinned: (String) -> Boolean,
    onPeerClick: (Peer) -> Unit,
    scanningPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    // Keep the last non-empty list around so the row can still render while it fades out.
    val lastPeers = remember { arrayOf(peers) }
    if (peers.isNotEmpty()) lastPeers[0] = peers
    AnimatedContent(
        modifier = modifier,
        targetState = peers.isEmpty(),
        transitionSpec = {
            (fadeIn(tween(300, delayMillis = 90)) + scaleIn(tween(300, delayMillis = 90), initialScale = 0.92f))
                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f))
                .using(SizeTransform(clip = false))
        },
        label = "nearbyPeersSection",
    ) { empty ->
        if (empty) {
            EmptyPeopleCard(Modifier.padding(horizontal = 20.dp))
        } else {
            val shown = if (peers.isNotEmpty()) peers else lastPeers[0]
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 2.dp),
            ) {
                items(shown, key = { it.mac }) { peer ->
                    val itemModifier = Modifier.animateItem(
                        fadeInSpec = tween(300),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                        fadeOutSpec = tween(200),
                    )
                    // A nearby AirDrop device whose name has not arrived yet:
                    // show a placeholder, never a bare card or a UUID.
                    if (!peer.hasDisplayName()) {
                        PeerLoadingCard(modifier = itemModifier)
                    } else {
                        PeerCard(
                            peer = peer,
                            selected = selectedMac == peer.mac,
                            pinned = isPinned(peer.mac),
                            onClick = { onPeerClick(peer) },
                            modifier = itemModifier,
                        )
                    }
                }
                // Discovery is paused by the helper while a transfer is running, so the
                // scanning tile should disappear instead of implying that peers are still
                // being discovered.
                if (!scanningPaused) {
                    item(key = "scanning-tile") {
                        ScanningPeerTile(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(300),
                                placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                                fadeOutSpec = tween(200),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningPeerTile(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(120.dp)
            .height(132.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchingSignalIcon(bubbleSize = 48.dp, iconSize = 30.dp, waveReach = 18.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Scanning…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Launch splash. Same language as the "No one nearby yet" card: the rotating, morphing app-icon
 * bubble with signal waves washing out across the whole screen behind it.
 */
@Composable
private fun CrossPairSplash() {
    val colors = MaterialTheme.colorScheme
    val enter = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { enter.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f)) }
        delay(350)
        text.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                Brush.radialGradient(
                    colors = listOf(colors.primaryContainer.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            // Swallow touches so the app underneath can't be tapped while the splash is up.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.graphicsLayer {
                    val sc = 0.5f + 0.5f * enter.value
                    scaleX = sc
                    scaleY = sc
                    alpha = enter.value.coerceIn(0f, 1f)
                },
            ) {
                SearchingSignalIcon(
                    bubbleSize = 128.dp,
                    iconSize = 80.dp,
                    waveReach = 700.dp,
                    waveCount = 5,
                    wavePeriodMillis = 4800,
                )
            }
            Spacer(Modifier.height(36.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 24.dp.toPx()
                },
            ) {
                Text(
                    text = "CrossPair",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "AirDrop for Android",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Empty-state icon. Echoes the Material 3 expressive LoadingIndicator (a continuously rotating,
 * morphing container) and adds radio waves rippling out of it, so it reads as "scanning for
 * signals".
 */
@Composable
private fun SearchingSignalIcon(
    bubbleSize: Dp = 72.dp,
    iconSize: Dp = 44.dp,
    waveReach: Dp = 24.dp,
    waveCount: Int = 3,
    wavePeriodMillis: Int = 2400,
) {
    val colors = MaterialTheme.colorScheme
    val waveColor = colors.primary
    val transition = rememberInfiniteTransition(label = "searchingSignal")

    // Container: slow rotation + corner morph (circle <-> soft squircle), like LoadingIndicator.
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "rotation",
    )
    val cornerPercent by transition.animateFloat(
        initialValue = 50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                50f at 0 using FastOutSlowInEasing
                30f at 800 using FastOutSlowInEasing
                50f at 1200 using FastOutSlowInEasing
                30f at 2000 using FastOutSlowInEasing
                50f at 2400
            },
        ),
        label = "corners",
    )
    // Gentle "transmit" pulse of the container.
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                1f at 0 using FastOutSlowInEasing
                1.08f at 300 using FastOutSlowInEasing
                1f at 900
            },
        ),
        label = "pulse",
    )
    // Three waves, evenly staggered in phase.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(wavePeriodMillis, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(bubbleSize)) {
            val minR = size.minDimension / 2f
            val maxR = minR + waveReach.toPx()
            // Drawn past the canvas bounds on purpose: the parent card clips them, so on a
            // big waveReach the waves wash over the whole card as a background.
            repeat(waveCount) { i ->
                val t = (phase + i / waveCount.toFloat()) % 1f
                val eased = LinearOutSlowInEasing.transform(t)
                drawCircle(
                    color = waveColor.copy(alpha = (1f - t) * (1f - t) * 0.35f),
                    radius = minR + (maxR - minR) * eased,
                    style = Stroke(width = (3f * (1f - t) + 0.75f).dp.toPx()),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(percent = cornerPercent.roundToInt()),
            color = colors.primaryContainer,
            modifier = Modifier
                .size(bubbleSize)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    rotationZ = rotation
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Counter-rotate so the icon stays upright while the container turns.
                val upright = Modifier
                    .size(iconSize)
                    .graphicsLayer { rotationZ = -rotation }
                Box(
                    modifier = upright,
                    contentAlignment = Alignment.Center,
                ) {
                    // The launcher foreground has adaptive-icon safe-zone padding. Draw it
                    // oversized and clip to the bubble so the artwork itself fills the icon.
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.requiredSize(iconSize * 2.2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPeopleCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SearchingSignalIcon(
                waveReach = 280.dp,
                waveCount = 4,
                wavePeriodMillis = 4400,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "No one nearby yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Open AirDrop on your iPhone, set it to Everyone for 10 Minutes, then scan from this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Placeholder for a device that is AirDrop-ready but has not announced its name
 * yet. Same footprint as [PeerCard] so the row does not jump when the name
 * arrives.
 */
@Composable
private fun PeerLoadingCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "peerLoading")
    val alpha by pulse.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "peerLoadingAlpha",
    )
    fun bar(width: Dp, height: Dp, shape: Dp) = Modifier
        .width(width)
        .height(height)
        .clip(RoundedCornerShape(shape))
        .background(colors.onSurface.copy(alpha = alpha * 0.25f))

    ElevatedCard(
        modifier = modifier.width(152.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant.copy(alpha = alpha * 0.6f)),
            )
            Spacer(Modifier.height(9.dp))
            Box(Modifier.then(bar(84.dp, 12.dp, 6.dp)))
            Spacer(Modifier.height(7.dp))
            Box(Modifier.then(bar(52.dp, 9.dp, 4.dp)))
        }
    }
}

@Composable
private fun PeerCard(
    peer: Peer,
    selected: Boolean,
    pinned: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme
    // Pop in when the device first shows up.
    val appear = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
    }
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
        modifier = modifier
            .width(152.dp)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) surface.primaryContainer else surface.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (selected) 0.dp else 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = avatarColor,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = avatarTint,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
                if (pinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = "Pinned",
                        tint = surface.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = peer.displayName(),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = peer.typeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) contentColor.copy(alpha = 0.72f) else surface.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_sensors),
                    contentDescription = null,
                    tint = if (selected) contentColor.copy(alpha = 0.72f) else surface.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
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
private fun KernelBanner(controller: CrossPairController, modifier: Modifier = Modifier) {
    val kernel = controller.kernel
    if (kernel.monitorReady) return
    val colors = MaterialTheme.colorScheme
    val title = when (kernel.state) {
        MonitorState.STOCK -> "Wi-Fi mode"
        MonitorState.UNVERIFIED -> "Kernel not verified"
        MonitorState.MONITOR -> return
    }
    val wifiMode = kernel.state == MonitorState.STOCK
    val container by animateColorAsState(
        targetValue = if (wifiMode) colors.secondaryContainer else colors.errorContainer,
        animationSpec = tween(350),
        label = "bannerContainer",
    )
    val onContainer by animateColorAsState(
        targetValue = if (wifiMode) colors.onSecondaryContainer else colors.onErrorContainer,
        animationSpec = tween(350),
        label = "bannerOnContainer",
    )
    // Springy pop-in of the card, and of its icon a beat later.
    val appear = remember { Animatable(0f) }
    val iconAppear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
        delay(120)
        iconAppear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val sc = 0.94f + 0.06f * appear.value
                scaleX = sc
                scaleY = sc
            }
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Crossfade(targetState = wifiMode, label = "bannerIcon") { wifi ->
                Icon(
                    painter = painterResource(if (wifi) R.drawable.ic_wifi_tethering else R.drawable.ic_warning),
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconAppear.value
                            scaleY = iconAppear.value
                        },
                )
            }
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
                        "${kernel.detail} Tap the ON/OFF button above to switch to Transfer mode."
                    } else {
                        "${kernel.detail} Tap the ON/OFF button above to switch back to Wi-Fi."
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateBanner(
    controller: CrossPairController,
    release: AppRelease,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update available",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (controller.updateDownloading) {
                        controller.updateMessage.ifBlank {
                            "Downloading CrossPair ${release.version}..."
                        }
                    } else if (release.apkUrl != null) {
                        "CrossPair ${release.version} is available. Review the changes before updating."
                    } else {
                        "CrossPair ${release.version} is available. Review the release page."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryContainer.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { showUpdateDialog = true },
                enabled = !controller.updateDownloading,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                if (controller.updateDownloading) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current,
                    )
                } else {
                    Text("Update")
                }
            }
        }
    }
    if (showUpdateDialog) {
        UpdateAvailableDialog(
            release = release,
            isUpdateAvailable = true,
            onUpdate = {
                if (release.apkUrl != null) {
                    controller.downloadUpdate()
                } else {
                    uriHandler.openUri(release.releaseUrl)
                }
                showUpdateDialog = false
            },
            onCancel = { showUpdateDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendCard(
    controller: CrossPairController,
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
            if (controller.stagingJobs.isNotEmpty()) {
                FilesGroup(
                    staged = emptyList(),
                    jobs = controller.stagingJobs,
                    onRemoveStaged = {},
                    onRemoveStaging = { controller.cancelStagingJob(it) },
                )
            }
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
                                text = controller.stagedSize(picked.absolutePath, picked.length()).readableBytes(),
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
                    !controller.sendCancelling && !controller.receiveTransferring,
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
                    LinearWavyProgressIndicator(
                        progress = { percent / 100f },
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
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
private fun LogsScreen(controller: CrossPairController) {
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
private fun SettingsScreen(controller: CrossPairController) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var channel by remember { mutableIntStateOf(controller.settings.channel) }
    var autoChannelDwellSecs by remember { mutableIntStateOf(controller.settings.autoChannelDwellSecs) }
    var uploadPolicy by remember { mutableStateOf(controller.settings.uploadPolicy) }
    var bleArm by remember { mutableIntStateOf(controller.settings.bleArm) }
    var deviceName by remember { mutableStateOf(controller.settings.deviceName) }
    var themeMode by remember { mutableStateOf(controller.settings.themeMode) }
    var tintMode by remember { mutableStateOf(controller.settings.tintMode) }
    var pauseDiscovery by remember { mutableStateOf(controller.settings.pauseDiscoveryDuringTransfer) }
    var visibleDuringTransfer by remember { mutableStateOf(controller.settings.visibleDuringTransfer) }
    var pendingChannel by remember { mutableStateOf<Int?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var removalNote by remember { mutableStateOf<String?>(null) }
    val settingsScope = rememberCoroutineScope()
    val settingsContext = LocalContext.current
    var updateCheckPending by remember { mutableStateOf(false) }
    val updateAvailable = controller.updateState is UpdateState.Available
    val dialogRelease = when (val state = controller.updateState) {
        is UpdateState.Available -> state.release
        is UpdateState.UpToDate -> state.release
        else -> null
    }

    LaunchedEffect(controller.updateState) {
        if (updateCheckPending &&
            (controller.updateState is UpdateState.Available ||
                controller.updateState is UpdateState.UpToDate)
        ) {
            showUpdateDialog = true
            updateCheckPending = false
        } else if (controller.updateState is UpdateState.Failed) {
            updateCheckPending = false
        }
    }

    LaunchedEffect(deviceName) {
        if (deviceName != controller.settings.deviceName) {
            delay(650)
            controller.saveSettings(
                channel,
                bleArm,
                deviceName,
                themeMode,
                tintMode,
                pauseDiscovery,
                visibleDuringTransfer,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 240.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionLabel("IDENTITY") }
        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIconBubble(R.drawable.ic_badge)
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
                    SettingsIconBubble(R.drawable.ic_memory)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Radio mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = when (controller.kernel.state) {
                                MonitorState.MONITOR -> "Transfer mode active"
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
                if (controller.kernel.state == MonitorState.MONITOR) {
                    Spacer(Modifier.height(4.dp))
                    val channelText = when {
                        controller.lockedChannel != null ->
                            "Locked channel ${controller.lockedChannel}"
                        controller.settings.channel == CrossPairController.AUTO_CHANNEL ->
                            "Auto · scanning channel ${controller.activeChannel}"
                        else -> "Channel ${controller.settings.channel} · fixed"
                    }
                    Text(
                        text = channelText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
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
                        onClick = { controller.enterTransferMode() },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "enter",
                            icon = R.drawable.ic_wifi_tethering,
                            label = if (controller.modeAction == "enter") "Entering Transfer mode..." else "Enter Transfer mode",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { controller.enterTransferMode(reboot = true) },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "reboot-enter",
                            icon = R.drawable.ic_refresh,
                            label = if (controller.modeAction == "reboot-enter") "Rebooting into Transfer mode..." else "Reboot into Transfer mode",
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
                        onClick = { controller.exitTransferMode() },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "exit",
                            icon = R.drawable.ic_stop_circle,
                            label = if (controller.modeAction == "exit") "Exiting Transfer mode..." else "Exit Transfer mode",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { controller.exitTransferMode(reboot = true) },
                        enabled = !controller.modeSwitching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModeButtonContent(
                            loading = controller.modeSwitching && controller.modeAction == "reboot-exit",
                            icon = R.drawable.ic_refresh,
                            label = if (controller.modeAction == "reboot-exit") {
                                "Rebooting to exit Transfer mode..."
                            } else {
                                "Reboot to exit Transfer mode"
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
            SettingsListGroup {
                SettingsCard(grouped = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBubble(R.drawable.ic_tune)
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
                    CrossPairButtonGroup(
                        options = ThemeMode.entries,
                        selected = themeMode,
                        label = { it.label },
                        onSelected = {
                            themeMode = it
                            controller.saveAppearance(it, tintMode)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsCard(grouped = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBubble(R.drawable.ic_tune)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Tint",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = if (Build.VERSION.SDK_INT >= 31) {
                                    "CrossPair palette or Android dynamic color"
                                } else {
                                    "CrossPair palette; system tint needs Android 12+"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CrossPairButtonGroup(
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
        }
        item { SectionLabel("RECEIVER") }
        item {
            SettingsListGroup {
                SettingsListItem(
                    icon = R.drawable.ic_folder,
                    headline = "Incoming storage",
                    supporting = CrossPairController.RECEIVE_DIR,
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
            SettingsListGroup {
                SettingsCard(grouped = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBubble(R.drawable.ic_cell_tower)
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
                    CrossPairButtonGroup(
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
                            if (it == 0) {
                                channel = 0
                                controller.saveSettings(0, bleArm, deviceName, themeMode, tintMode)
                            } else {
                                pendingChannel = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when (channel) {
                            0 -> "Auto uses 5 GHz when available and falls back to 2.4 GHz when needed."
                            6 -> "Using channel 6 · 2.4 GHz. Auto is recommended."
                            else -> "Using channel $channel · 5 GHz. Auto is recommended."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                SettingsCard(grouped = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBubble(R.drawable.ic_tune)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Auto channel dwell",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "How long Auto listens on each channel before switching",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CrossPairButtonGroup(
                        options = listOf(6, 12, 18, 24, 30),
                        selected = autoChannelDwellSecs,
                        label = { "${it}s" },
                        onSelected = {
                            autoChannelDwellSecs = it
                            controller.setAutoChannelDwellSecs(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "12s is the default. Longer dwell helps older iPhones; shorter cycles channels faster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                SettingsCard(grouped = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBubble(R.drawable.ic_send)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Upload mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "How the archive is prepared before it goes on the air",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    UploadModeList(
                        options = UploadPolicy.entries,
                        selected = uploadPolicy,
                        onSelected = {
                            uploadPolicy = it
                            val current = controller.settings
                            controller.saveSettings(
                                current.channel,
                                current.bleArm,
                                current.deviceName,
                                current.themeMode,
                                current.tintMode,
                                current.pauseDiscoveryDuringTransfer,
                                current.visibleDuringTransfer,
                                it,
                            )
                        },
                    )
                }
                SettingsCard(grouped = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBubble(R.drawable.ic_bluetooth)
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
                    CrossPairButtonGroup(
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
        }
        item { SectionLabel("TRANSFER") }
        item {
            SettingsListGroup {
                SettingsCard(grouped = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIconBubble(R.drawable.ic_sensors)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pause peer discovery",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Stop nearby peer updates while transferring",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = pauseDiscovery,
                            onCheckedChange = {
                                pauseDiscovery = it
                                controller.saveSettings(
                                    channel,
                                    bleArm,
                                    deviceName,
                                    themeMode,
                                    tintMode,
                                    it,
                                    visibleDuringTransfer,
                                )
                            },
                        )
                    }
                }
                SettingsCard(grouped = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIconBubble(R.drawable.ic_wifi_tethering)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stay visible in AirDrop",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Keep this phone advertised while transferring",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = visibleDuringTransfer,
                            onCheckedChange = {
                                visibleDuringTransfer = it
                                controller.saveSettings(
                                    channel,
                                    bleArm,
                                    deviceName,
                                    themeMode,
                                    tintMode,
                                    pauseDiscovery,
                                    it,
                                )
                            },
                        )
                    }
                }
            }
        }
        item { SectionLabel("UPDATES") }
        item {
            SettingsListGroup {
                SettingsCard(grouped = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIconBubble(R.drawable.ic_notifications)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Update reminders",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Check GitHub on launch and show available updates",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = controller.settings.updateReminders,
                            onCheckedChange = { controller.setUpdateReminders(it) },
                        )
                    }
                }
                SettingsListItem(
                    icon = R.drawable.ic_refresh,
                    headline = "App updates",
                    busy = controller.updateState is UpdateState.Checking,
                    supporting = updateSupporting(controller),
                    trailing = when (controller.updateState) {
                        UpdateState.Checking -> "Checking"
                        is UpdateState.Available -> "Available"
                        is UpdateState.UpToDate -> "Notes"
                        else -> "Check"
                    },
                    onClick = {
                        if (controller.updateState is UpdateState.Available ||
                            controller.updateState is UpdateState.UpToDate
                        ) {
                            showUpdateDialog = true
                        } else {
                            updateCheckPending = true
                            controller.checkForUpdates(force = true)
                        }
                    },
                )
            }
        }
        item { SectionLabel("REMOVE") }
        item {
            SettingsListGroup {
                SettingsListItem(
                    icon = R.drawable.ic_delete,
                    headline = "Remove CrossPair",
                    supporting = removalNote
                        ?: "Stops Transfer mode, deletes the app and its private files. " +
                        "Received files stay in Download/CrossPair.",
                    busy = removing,
                    onClick = { if (!removing) showRemoveDialog = true },
                )
            }
        }
        item { SectionLabel("ABOUT") }
        item {
            SettingsListGroup {
                SettingsListItem(
                    icon = R.drawable.ic_badge,
                    headline = "Developer",
                    supporting = "RenzVillegas24",
                    avatarRes = R.drawable.dev_avatar,
                    onClick = {
                        uriHandler.openUri("https://github.com/RenzVillegas24?tab=repositories")
                    },
                )
                SettingsListItem(
                    icon = R.drawable.ic_computer,
                    headline = "GitHub repository",
                    supporting = "RenzVillegas24/CrossPair",
                    onClick = { uriHandler.openUri("https://github.com/RenzVillegas24/CrossPair") },
                )
                SettingsListItem(
                    icon = R.drawable.ic_memory,
                    headline = "Version",
                    supporting = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}\n${BuildConfig.APPLICATION_ID}",
                )
                SettingsListItem(
                    icon = R.drawable.ic_smartphone,
                    headline = "Device",
                    supporting = "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.DEVICE}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nKernel ${controller.kernel.release}\nCrossPair support · ${controller.kernel.support.label}\nRadio · WCN3990 · monitor + hidden STA",
                )
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove CrossPair?") },
            text = {
                Text(
                    "Transfer mode is stopped and Wi-Fi restored first. The app, its " +
                        "scripts and its AirDrop identity are deleted. Received files in " +
                        "Download/CrossPair are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        settingsScope.launch {
                            removing = true
                            removalNote = "Stopping Transfer mode and cleaning up…"
                            controller.prepareForUninstall()
                            removalNote = "Cleanup done. Confirm the uninstall in the system dialog."
                            removing = false
                            val pkg = Uri.parse("package:${settingsContext.packageName}")
                            val opened = runCatching {
                                settingsContext.startActivity(Intent(Intent.ACTION_DELETE, pkg))
                            }.isSuccess
                            if (!opened) {
                                runCatching {
                                    settingsContext.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            pkg,
                                        ),
                                    )
                                }
                                removalNote = "Open App info → Uninstall to remove CrossPair."
                            }
                        }
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showUpdateDialog && dialogRelease != null) {
        UpdateAvailableDialog(
            release = dialogRelease,
            isUpdateAvailable = updateAvailable,
            onUpdate = {
                if (updateAvailable && dialogRelease.apkUrl != null) {
                    controller.downloadUpdate()
                } else {
                    uriHandler.openUri(dialogRelease.releaseUrl)
                }
                showUpdateDialog = false
            },
            onCancel = { showUpdateDialog = false },
        )
    }

    pendingChannel?.let { target ->
        val is24 = target == 6
        AlertDialog(
            onDismissRequest = { pendingChannel = null },
            title = {
                Text(if (is24) "Use 2.4 GHz Wi-Fi?" else "Use a fixed 5 GHz channel?")
            },
            text = {
                Text(
                    if (is24) {
                        "Channel 6 uses the older 2.4 GHz Wi-Fi band. It has longer range " +
                            "but is slower, and transfers may stall if the iPhone is using " +
                            "5 GHz. Use Auto unless your Wi-Fi only supports 2.4 GHz."
                    } else {
                        "Auto is recommended because it chooses the best channel for the " +
                            "iPhone. A fixed 5 GHz channel may be less reliable if the " +
                            "iPhone changes channels."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        channel = target
                        controller.saveSettings(
                            target,
                            bleArm,
                            deviceName,
                            themeMode,
                            tintMode,
                        )
                        pendingChannel = null
                    },
                ) {
                    Text(if (is24) "Use 2.4 GHz" else "Use channel $target")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        channel = 0
                        controller.saveSettings(0, bleArm, deviceName, themeMode, tintMode)
                        pendingChannel = null
                    },
                ) {
                    Text("Use Auto")
                }
            },
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    release: AppRelease,
    isUpdateAvailable: Boolean,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
) {
    val notes = release.notes.trim()
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (isUpdateAvailable) {
                    "CrossPair ${release.version} is available"
                } else {
                    "CrossPair ${release.version} release notes"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (isUpdateAvailable) {
                        "Installed ${BuildConfig.VERSION_NAME} · ${release.tag}"
                    } else {
                        "You're up to date · ${release.tag}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                if (notes.isBlank()) {
                    Text(
                        text = "No release notes were provided for this build.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    MarkdownText(notes)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(
                    if (isUpdateAvailable && release.apkUrl != null) {
                        "Update"
                    } else {
                        "Open release"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(if (isUpdateAvailable) "Cancel" else "Close")
            }
        },
    )
}

private fun updateSupporting(controller: CrossPairController): String {
    if (controller.updateDownloading) {
        return controller.updateMessage.ifBlank { "Downloading update..." }
    }
    if (controller.updateMessage.isNotBlank()) return controller.updateMessage
    return when (val state = controller.updateState) {
        UpdateState.Idle -> "Check GitHub for the latest release"
        UpdateState.Checking -> "Checking GitHub..."
        is UpdateState.UpToDate -> "Latest GitHub release: v${state.latestVersion}"
        is UpdateState.Available -> "v${state.release.version} is available"
        is UpdateState.Failed -> "Could not check: ${state.message}"
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
private fun SettingsIconBubble(@DrawableRes icon: Int, busy: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = colors.primaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = colors.onPrimaryContainer,
                )
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun UploadModeList(
    options: List<UploadPolicy>,
    selected: UploadPolicy,
    onSelected: (UploadPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh,
                border = if (isSelected) BorderStroke(1.5.dp, colors.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                        )
                        Text(
                            text = option.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsListGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsListItem(
    @DrawableRes icon: Int,
    headline: String,
    supporting: String,
    trailing: String? = null,
    avatarRes: Int? = null,
    busy: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        ListItem(
            leadingContent = {
                if (avatarRes != null) {
                    Image(
                        painter = painterResource(avatarRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
                } else {
                    SettingsIconBubble(icon, busy)
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
private fun SettingsCard(
    grouped: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = if (grouped) RoundedCornerShape(4.dp) else MaterialTheme.shapes.medium,
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
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Preview(name = "Peer sheet - 0 files", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun PeerSheetPreviewEmpty() {
    PeerSheetPreview(itemCount = 0, menuExpanded = false)
}

@Preview(name = "Peer sheet - 5 files", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun PeerSheetPreviewFive() {
    PeerSheetPreview(itemCount = 5, menuExpanded = false)
}

@Preview(name = "Peer sheet - 40 files", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun PeerSheetPreviewForty() {
    PeerSheetPreview(itemCount = 40, menuExpanded = false)
}

@Preview(name = "Peer sheet - menu expanded", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun PeerSheetPreviewMenu() {
    PeerSheetPreview(itemCount = 5, menuExpanded = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerSheetPreview(itemCount: Int, menuExpanded: Boolean) {
    val items = List(itemCount) { index ->
        StagedItem(
            path = "/preview/file-$index.bin",
            name = when (index % 5) {
                0 -> "droidy-2mb-random.bin"
                1 -> "droidy-20mb-random2.bin"
                2 -> "droidy-test.txt"
                3 -> "nl80211-scan"
                else -> "photo-${index}.jpg"
            },
            size = (index + 1) * 4096L,
        )
    }
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(760.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            PeerSheetContent(
                peer = Peer("preview", "iPhone", 2, -36),
                channel = 149,
                selectedItems = items,
                pinned = false,
                listState = rememberLazyListState(),
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
                fabExpanded = menuExpanded,
                onFabExpandedChange = {},
                onTogglePin = {},
                onRemoveItem = {},
                onAddFiles = {},
                onAction = {},
                onSend = {},
            )
        }
    }
}
