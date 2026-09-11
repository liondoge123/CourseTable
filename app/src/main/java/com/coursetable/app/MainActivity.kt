package com.coursetable.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.ImportEntry
import com.coursetable.app.ui.ImportScreen
import com.coursetable.app.ui.IncomingFile
import com.coursetable.app.ui.RootDestination
import com.coursetable.app.ui.SettingsScreen
import com.coursetable.app.ui.TimetableScreen
import com.coursetable.app.ui.TimetableViewModel
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.GlassSurface
import com.coursetable.app.ui.liquid.GlassStyle
import com.coursetable.app.ui.liquid.Icon
import com.coursetable.app.ui.liquid.LiquidAmbientBackground
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.LiquidNavigationTabs
import com.coursetable.app.ui.liquid.Surface
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.liquid.fullscreenSubpageTransitionSpec
import com.coursetable.app.ui.liquid.glassBackdropSource
import com.coursetable.app.ui.liquid.navigationGlassBackdropSource
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import com.coursetable.app.ui.theme.ThemeColor
import com.coursetable.app.ui.theme.ThemeMode
import java.time.LocalDate

private val FloatingDockInset = 88.dp
private val FloatingDockHeight = 54.dp
private val DockActionHeight = 36.dp

class MainActivity : ComponentActivity() {

    private val incoming = mutableStateOf<IncomingFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        incoming.value = IncomingFile.fromIntent(intent)
        setContent {
            val app = applicationContext as CourseApp
            val themeSettings by app.settingsRepository.settings
                .collectAsState(initial = null)
            CourseTableTheme(
                themeMode = ThemeMode.fromKey(themeSettings?.themeMode),
                themeColor = ThemeColor.fromKey(themeSettings?.themeColor)
            ) {
                MainScreen(
                    incoming = incoming.value,
                    onIncomingConsumed = { incoming.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = IncomingFile.fromIntent(intent)
    }
}

@Composable
private fun MainScreen(
    incoming: IncomingFile?,
    onIncomingConsumed: () -> Unit
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as CourseApp }
    val vm: TimetableViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TimetableViewModel(app) }
        }
    )
    val settings by vm.settings.collectAsState()
    val courses by vm.courses.collectAsState()
    var destination by rememberSaveable { mutableStateOf(RootDestination.TIMETABLE) }
    var activeImport by rememberSaveable { mutableStateOf<ImportEntry?>(null) }
    var nestedPageOpen by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(incoming) {
        if (incoming != null) {
            destination = RootDestination.IMPORT
            activeImport = ImportEntry.INCOMING
        }
    }

    BackHandler(enabled = activeImport != null) {
        activeImport = null
    }

    LiquidBackdropHost(Modifier.fillMaxSize()) {
        LiquidAmbientBackground(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationGlassBackdropSource()
        ) {
            // The navigation glass records this complete scene, including real page
            // content. A separate ambient source remains behind it for glass controls
            // rendered inside the page, avoiding RenderNode self-sampling.
            LiquidAmbientBackground(Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                AnimatedContent(
                    targetState = activeImport != null,
                    transitionSpec = fullscreenSubpageTransitionSpec(),
                    label = "MainToImport"
                ) { hasImport ->
                    if (hasImport) {
                        ImportScreen(
                            incoming = incoming,
                            onConsumed = {
                                onIncomingConsumed()
                            },
                            initialEntry = activeImport ?: ImportEntry.HUB,
                            onBack = {
                                activeImport = null
                            }
                        )
                    } else {
                        AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (slideInHorizontally(
                                    initialOffsetX = { (it * 0.20f * direction).toInt() },
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                ) + fadeIn(
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                )).togetherWith(
                                    slideOutHorizontally(
                                        targetOffsetX = { (-it * 0.20f * direction).toInt() },
                                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    ) + fadeOut(
                                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                    )
                                )
                            },
                            label = "RootTabTransition"
                        ) { dest ->
                            when (dest) {
                                RootDestination.TIMETABLE -> TimetableScreen(vm, bottomContentPadding = FloatingDockInset)
                                RootDestination.COURSES -> CourseManageScreen(
                                    courses = courses,
                                    totalWeeks = settings.totalWeeks,
                                    periodCount = settings.periods.size.coerceAtLeast(1),
                                    onBack = {},
                                    onSave = vm::saveCourse,
                                    onDelete = vm::deleteCourse,
                                    rootMode = true,
                                    bottomContentPadding = FloatingDockInset
                                )
                                RootDestination.IMPORT -> ImportScreen(
                                    bottomContentPadding = FloatingDockInset,
                                    onSubpageChanged = { nestedPageOpen = it }
                                )
                                RootDestination.SETTINGS -> SettingsScreen(
                                    bottomContentPadding = FloatingDockInset,
                                    onSubpageChanged = { nestedPageOpen = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        val dockVisible = activeImport == null && !imeVisible && !nestedPageOpen
        AnimatedVisibility(
            visible = dockVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingNavigationDock(
                selectedTab = destination,
                onTabSelected = {
                    nestedPageOpen = false
                    destination = it
                },
                onActionClick = {
                    val day = if (destination == RootDestination.TIMETABLE) {
                        LocalDate.now().dayOfWeek.value
                    } else {
                        1
                    }
                    vm.openCellEditor(day, 1)
                }
            )
        }

        vm.editingCourse?.let { editing ->
            if (vm.editorOpen) {
                CourseEditorDialog(
                    course = editing,
                    totalWeeks = settings.totalWeeks,
                    periodCount = settings.periods.size.coerceAtLeast(1),
                    onDismiss = { vm.closeEditor() },
                    onSave = { vm.saveCourse(it) }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigationDock(
    selectedTab: RootDestination,
    onTabSelected: (RootDestination) -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 20.dp)
    ) {
        val selectedIndex = when (selectedTab) {
            RootDestination.TIMETABLE -> 0
            RootDestination.COURSES -> 1
            RootDestination.IMPORT -> 3
            RootDestination.SETTINGS -> 4
        }
        LiquidNavigationTabs(
            selectedIndex = selectedIndex,
            onSelected = { index ->
                val destination = when (index) {
                    0 -> RootDestination.TIMETABLE
                    1 -> RootDestination.COURSES
                    3 -> RootDestination.IMPORT
                    4 -> RootDestination.SETTINGS
                    else -> null
                }
                destination?.let(onTabSelected)
            },
            tabCount = 5,
            selectableIndices = listOf(0, 1, 3, 4),
            actionIndex = 2,
            onAction = onActionClick,
            actionContentDescription = "添加课程",
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingDockHeight)
        ) { contentColor, itemScale, selectTab ->
            DockItem(
                selected = selectedTab == RootDestination.TIMETABLE,
                onClick = { selectTab(0) },
                icon = Icons.Filled.DateRange,
                label = "课表",
                contentColor = contentColor,
                itemScale = itemScale
            )
            DockItem(
                selected = selectedTab == RootDestination.COURSES,
                onClick = { selectTab(1) },
                icon = Icons.Filled.BookOpen,
                label = "课程",
                contentColor = contentColor,
                itemScale = itemScale
            )
            DockActionVisual(itemScale = itemScale)
            DockItem(
                selected = selectedTab == RootDestination.IMPORT,
                onClick = { selectTab(3) },
                icon = Icons.Filled.CloudDownload,
                label = "导入",
                contentColor = contentColor,
                itemScale = itemScale
            )
            DockItem(
                selected = selectedTab == RootDestination.SETTINGS,
                onClick = { selectTab(4) },
                icon = Icons.Filled.Settings,
                label = "设置",
                contentColor = contentColor,
                itemScale = itemScale
            )
        }
    }
}

@Composable
private fun RowScope.DockActionVisual(itemScale: Float) {
    val actionContainerColor = LiquidTheme.colorScheme.primary.copy(alpha = 0.78f)
    val actionContentColor = LiquidTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = DockActionHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(actionContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = actionContentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RowScope.DockItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentColor: Color,
    itemScale: Float
) {
    val itemShape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(itemShape)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                onClick = onClick,
                role = Role.Tab
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = LiquidTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor
        )
    }
}
