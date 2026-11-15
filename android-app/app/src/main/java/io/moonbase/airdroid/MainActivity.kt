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
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.contentDescription
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
    PHOTOS("Photos", R.drawable.ic_image),
    VIDEOS("Videos", R.drawable.ic_videocam),
    FOLDER("Folder", R.drawable.ic_folder),
    CAMERA("Camera", R.drawable.ic_photo_camera),
    AUDIO("Audio", R.drawable.ic_music_note),
    PASTE("Paste", R.drawable.ic_content_paste),
}

private data class StagedItem(val path: String, val name: String, val size: Long)

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
                AirDroidTopBar(pageTab, controller, scope, scrollBehavior)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .fadingEdges(bottom = pageFadeHeight),
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
                                if (transferMode) R.drawable.ic_airdroid_home else R.drawable.ic_wifi_tethering,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (transferMode) "ON" else "OFF")
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
private fun HomeScreen(controller: AirDroidController, scope: CoroutineScope) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sheetPeer by remember { mutableStateOf<Peer?>(null) }
    var sheetItems by remember { mutableStateOf<List<StagedItem>>(emptyList()) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun addStaged(items: List<Pair<File, String>>) {
        if (items.isEmpty()) return
        sheetItems = sheetItems + items.map { (file, name) ->
            StagedItem(file.absolutePath, name, file.length())
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            addStaged(uris.mapNotNull { controller.stageFile(it) })
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
            item(key = "kernel-banner") {
                KernelBanner(controller, Modifier.animateItem())
            }
        }
        // Peers can only be discovered in monitor mode, so hide the section in Wi-Fi mode.
        // Keyed + animateItem: it fades out and everything below slides up smoothly.
        if (controller.kernel.state != MonitorState.STOCK) {
            item(key = "nearby-label") {
                SectionLabel("NEARBY AIRDROP", Modifier.animateItem())
            }
            item(key = "nearby-section") {
                NearbyPeersSection(
                    peers = peers,
                    selectedMac = sheetPeer?.mac,
                    isPinned = { controller.isPinned(it) },
                    onPeerClick = { openPeer(it) },
                    scanningPaused = controller.sending || controller.receiveTransferring,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "home-tabs") {
            Box(Modifier.animateItem()) {
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
                    item(key = "sending-label") { SectionLabel("SENDING", Modifier.animateItem()) }
                    items(sentActive, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem()) {
                            TransferCard(
                                transfer = transfer,
                                onCancel = { controller.cancelTransfer(transfer.id) },
                            )
                        }
                    }
                }
                if (sentDone.isNotEmpty()) {
                    item(key = "sent-label") { SectionLabel("SENT", Modifier.animateItem()) }
                    items(sentDone, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem()) { TransferCard(transfer) }
                    }
                }
                if (sentActive.isEmpty() && sentDone.isEmpty()) {
                    item(key = "sent-empty") {
                        Box(Modifier.animateItem()) {
                            EmptyTransfersCard(
                                title = "Nothing sent yet",
                                subtitle = "Tap a nearby phone to send a file.",
                            )
                        }
                    }
                }
            }
            HomeTab.RECEIVED -> {
                if (receivedActive.isNotEmpty()) {
                    item(key = "receiving-label") { SectionLabel("RECEIVING", Modifier.animateItem()) }
                    items(receivedActive, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem()) { TransferCard(transfer) }
                    }
                }
                if (receivedDone.isNotEmpty()) {
                    item(key = "received-label") { SectionLabel("RECEIVED", Modifier.animateItem()) }
                    items(receivedDone, key = { it.id }) { transfer ->
                        Box(Modifier.animateItem()) {
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
                }
                if (receivedActive.isEmpty() && receivedDone.isEmpty()) {
                    item(key = "received-empty") {
                        Box(Modifier.animateItem()) { EmptyReceivedCard() }
                    }
                }
            }
        }
    }

    sheetPeer?.let { peer ->
        // The sheet stays open when the phone drops out of range. It keeps the last known
        // details, shows a "waiting" state and picks the live data up again if the phone returns.
        val livePeer = controller.peers.firstOrNull { it.mac == peer.mac }
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
            if (selectedItems.isEmpty()) {
                item(key = "emptyItems") {
                    EmptyItemsState(modifier = Modifier.padding(horizontal = 20.dp))
                }
            } else {
                items(selectedItems, key = { it.path }) { item ->
                    FileRow(item = item, onRemove = { onRemoveItem(item) })
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
                            modifier = Modifier.size(24.dp),
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
            enabled = itemCount > 0 && !lost,
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
                    enabled = itemCount > 0 && !lost,
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

private fun peerSubtitle(peer: Peer, channel: Int?): String = buildString {
    append(peer.typeLabel())
    append(" · ")
    append(peer.rssi)
    append(" dBm")
    if (channel != null) {
        append(" · channel ")
        append(channel)
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
            item(key = "kernel-banner") {
                KernelBanner(controller, Modifier.animateItem())
            }
        }
        if (controller.kernel.state != MonitorState.STOCK) {
            item(key = "nearby-section") {
                NearbyPeersSection(
                    peers = controller.peers.toList(),
                    selectedMac = controller.selectedMac,
                    isPinned = { false },
                    onPeerClick = { controller.selectPeer(it) },
                    scanningPaused = controller.sending || controller.receiveTransferring,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "send-label") { SectionLabel("SEND", Modifier.animateItem()) }
        item(key = "send-card") {
            SendCard(
                controller = controller,
                onChooseFile = { picker.launch(arrayOf("*/*")) },
                onSend = { scope.launch { controller.sendPicked() } },
                onCancelSend = { controller.cancelSend() },
            )
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
            EmptyPeopleCard()
        } else {
            val shown = if (peers.isNotEmpty()) peers else lastPeers[0]
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(shown, key = { it.mac }) { peer ->
                    PeerCard(
                        peer = peer,
                        selected = selectedMac == peer.mac,
                        pinned = isPinned(peer.mac),
                        onClick = { onPeerClick(peer) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(300),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                            fadeOutSpec = tween(200),
                        ),
                    )
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
private fun KernelBanner(controller: AirDroidController, modifier: Modifier = Modifier) {
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
                        controller.settings.channel == AirDroidController.AUTO_CHANNEL ->
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
        }
        item { SectionLabel("RECEIVER") }
        item {
            SettingsListGroup {
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
                    supporting = "RenzVillegas24/AirDroid",
                    onClick = { uriHandler.openUri("https://github.com/RenzVillegas24/AirDroid") },
                )
                SettingsListItem(
                    icon = R.drawable.ic_memory,
                    headline = "Version",
                    supporting = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}\n${BuildConfig.APPLICATION_ID}",
                )
                SettingsListItem(
                    icon = R.drawable.ic_smartphone,
                    headline = "Device",
                    supporting = "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.DEVICE}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nKernel ${controller.kernel.release}\nRadio · WCN3990 · monitor + hidden STA",
                )
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
private fun SettingsIconBubble(@DrawableRes icon: Int) {
    val colors = MaterialTheme.colorScheme
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
                    SettingsIconBubble(icon)
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
                peer = Peer("preview", "RenziPhone", 2, -36),
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
