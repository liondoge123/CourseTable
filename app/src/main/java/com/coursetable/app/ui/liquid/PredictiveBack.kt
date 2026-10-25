package com.coursetable.app.ui.liquid

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

// Changes with page transitions. Track readers instead of invalidating every
// course card and label in the entire provided page subtree.
internal val LocalBackLayerActive = compositionLocalOf { true }
internal val LocalBackInOverlay = staticCompositionLocalOf { false }
internal val LocalOverlayBackActive = staticCompositionLocalOf { true }
private val LocalCompletedBack = staticCompositionLocalOf<() -> Unit> { {} }

internal enum class BackPresentation { PAGE, SHEET, DIALOG, MENU }
internal enum class BackPhase { IDLE, TRACKING, RESTORING, COMMITTING, COMPLETED }

/** Business state is untouched until the system commits the gesture. */
@Stable
internal class PredictiveBackState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var exitProgress by mutableFloatStateOf(0f)
        private set
    var direction by mutableFloatStateOf(1f)
        private set
    var phase by mutableStateOf(BackPhase.IDLE)
        private set
    val busy: Boolean get() = phase == BackPhase.TRACKING || phase == BackPhase.RESTORING || phase == BackPhase.COMMITTING
    var completed by mutableStateOf(false)
        private set
    var touchDeltaY by mutableFloatStateOf(0f)
        private set
    private var initialTouchY: Float? = null
    private var lastEventTime = 0L
    var progressVelocity = 0f
        private set
    private var generation = 0L
    private var motionJob: Job? = null
    private var takeover = false
    private var startProgress = 0f
    private var startDirection = 1f
    private var startDeltaY = 0f
    private var gestureOrigin: Float? = null

    fun start(): Long? {
        if (phase == BackPhase.COMMITTING || phase == BackPhase.COMPLETED) return null
        val session = ++generation
        motionJob?.cancel()
        // A new system start can precede delivery of the old flow's cancellation.
        takeover = phase == BackPhase.RESTORING || (phase == BackPhase.TRACKING && progress > 0f)
        startProgress = if (takeover) progress else 0f
        startDirection = direction
        startDeltaY = if (takeover) touchDeltaY else 0f
        phase = BackPhase.TRACKING
        completed = false; exitProgress = 0f
        gestureOrigin = null; initialTouchY = null; lastEventTime = 0L
        if (!takeover) { progress = 0f; touchDeltaY = 0f; progressVelocity = 0f }
        return session
    }
    fun reset() {
        ++generation
        motionJob?.cancel()
        progress = 0f; exitProgress = 0f; completed = false; phase = BackPhase.IDLE
        touchDeltaY = 0f; initialTouchY = null; lastEventTime = 0L; progressVelocity = 0f
    }
    fun deactivate() {
        // Keep an already completed pose until its owner removes the outgoing layer.
        if (phase == BackPhase.COMPLETED) { ++generation; motionJob?.cancel() } else reset()
    }
    fun update(session: Long, event: BackEventCompat, now: Long = SystemClock.uptimeMillis()) {
        if (session != generation || phase != BackPhase.TRACKING || !event.progress.isFinite()) return
        val raw = event.progress.coerceIn(0f, 1f)
        if (gestureOrigin == null) gestureOrigin = if (takeover) raw else 0f
        val origin = gestureOrigin!!
        val fraction = if (origin < 1f) ((raw - origin) / (1f - origin)).coerceIn(0f, 1f) else 0f
        val nextProgress = startProgress + (1f - startProgress) * fraction
        val elapsed = now - lastEventTime
        if (lastEventTime != 0L && elapsed in 1L..100L) {
            val velocity = ((nextProgress - progress) * 1000f / elapsed).coerceIn(-8f, 8f)
            progressVelocity = progressVelocity * 0.5f + velocity * 0.5f
        } else progressVelocity = 0f
        lastEventTime = now
        progress = nextProgress
        if (event.touchY.isFinite()) {
            if (initialTouchY == null) initialTouchY = event.touchY
            touchDeltaY = startDeltaY * (1f - fraction) + event.touchY - initialTouchY!!
        }
        val edgeDirection = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        direction = if (takeover) startDirection + (edgeDirection - startDirection) * fraction else edgeDirection
    }
    fun restore(session: Long, onRestored: () -> Unit = {}) {
        if (session != generation || !scope.isActive || phase != BackPhase.TRACKING) return
        phase = BackPhase.RESTORING
        val startProgress = progress
        val startDeltaY = touchDeltaY
        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animate(progress, 0f, initialVelocity = progressVelocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { value, velocity ->
                if (session == generation) {
                    progress = value.coerceIn(0f, 1f)
                    progressVelocity = velocity
                    touchDeltaY = if (startProgress > 0f) startDeltaY * (progress / startProgress) else 0f
                }
            }
            if (session == generation) {
                progress = 0f; exitProgress = 0f; touchDeltaY = 0f; progressVelocity = 0f
                phase = BackPhase.IDLE
                onRestored()
            }
        }
    }
    fun closeWithoutPreview(session: Long, onBack: () -> Unit) {
        if (session != generation || phase != BackPhase.TRACKING) return
        progress = 0f; touchDeltaY = 0f
        phase = BackPhase.COMPLETED
        onBack()
    }
    fun finish(session: Long, onFinished: () -> Unit) {
        if (session != generation || phase != BackPhase.TRACKING) return
        phase = BackPhase.COMMITTING
        // Material's 150–300ms range, shortened for a progressed or fast gesture.
        val completion = max(backPreviewProgress(progress), (progressVelocity / 4f).coerceIn(0f, 1f))
        val duration = (300f - 150f * completion).toInt()
        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animate(0f, 1f, animationSpec = tween(duration)) { value, _ ->
                if (session == generation) exitProgress = value
            }
            if (session == generation) {
                completed = true
                phase = BackPhase.COMPLETED
                onFinished()
            }
        }
    }
}

@Composable
internal fun rememberPredictiveBack(
    enabled: Boolean = true,
    canCommit: () -> Boolean = { true },
    onRejected: () -> Unit = {},
    onBack: () -> Unit
): PredictiveBackState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { PredictiveBackState(scope) }
    val currentCommit by rememberUpdatedState(canCommit)
    val currentRejected by rememberUpdatedState(onRejected)
    val currentBack by rememberUpdatedState(onBack)
    val active = LocalBackLayerActive.current
    val overlay = LocalBackInOverlay.current
    val overlayActive = LocalOverlayBackActive.current
    val overlays = LocalGlassOverlayController.current
    val imeVisible = rememberImeVisible()
    val completedBack = LocalCompletedBack.current
    val handlerEnabled = enabled && active && !imeVisible &&
        (if (overlay) overlayActive else overlays?.entries.isNullOrEmpty())
    val currentEnabled by rememberUpdatedState(handlerEnabled)
    val overlayEntry = LocalGlassOverlayEntry.current
    // Observe the phase during composition. Reading it only inside SideEffect does
    // not subscribe this composition to COMMITTING, so the Dock waits for COMPLETED.
    val overlayBackCommitted = state.phase == BackPhase.COMMITTING || state.completed
    SideEffect {
        if (handlerEnabled) {
            // A confirmed gesture can reveal the Dock while its exit is still running.
            // Tracking/cancelled/rejected gestures continue to hide it.
            overlayEntry?.backCommitted = overlayBackCommitted
        }
    }
    DisposableEffect(state, handlerEnabled) {
        if (!handlerEnabled) state.deactivate()
        onDispose { state.deactivate() }
    }
    PredictiveBackHandler(enabled = handlerEnabled) { events ->
        val session = if (currentEnabled) state.start() else null
        if (session != null) {
            try {
                var hasProgress = false
                events.collect { event -> hasProgress = true; state.update(session, event) }
                if (!currentEnabled) return@PredictiveBackHandler
                if (currentCommit()) {
                    // Three-button navigation has no progress events; retain its ordinary exit.
                    if (hasProgress) {
                        state.finish(session) { completedBack(); currentBack() }
                    } else state.closeWithoutPreview(session) { currentBack() }
                } else {
                    state.restore(session) { if (currentEnabled) currentRejected() }
                }
            } catch (cancelled: CancellationException) {
                if (currentEnabled) state.restore(session)
                throw cancelled
            }
        } else {
            // Committing is irreversible; only its owning callback may remove the layer.
            events.collect { }
        }
    }
    return state
}

@Composable
internal fun Modifier.predictiveBackTransform(
    state: PredictiveBackState,
    presentation: BackPresentation,
    origin: TransformOrigin = TransformOrigin.Center
): Modifier {
    val corners = if (presentation == BackPresentation.PAGE) rememberBackWindowCorners() else BackWindowCorners()
    return graphicsLayer {
    val p = backPreviewProgress(state.progress)
    val exit = state.exitProgress
    val shrink = when (presentation) {
        BackPresentation.PAGE -> 0.10f
        BackPresentation.SHEET -> 0.04f
        else -> 0.05f
    }
    scaleX = 1f - shrink * p
    scaleY = scaleX
    transformOrigin = origin
    translationX = when (presentation) {
        BackPresentation.PAGE -> 0f
        BackPresentation.SHEET -> 0f
        else -> state.direction * 12.dp.toPx() * p
    }
    translationY = if (presentation == BackPresentation.SHEET) 48.dp.toPx() * p + size.height * exit else 0f
    alpha = 1f - exit
    if (presentation == BackPresentation.PAGE) {
        val page = backPageTransform(size.width, size.height, density, state.progress, state.direction, state.touchDeltaY, exit)
        scaleX = page.scale
        scaleY = page.scale
        translationX = page.x
        translationY = page.y
        clip = p > 0f || exit > 0f
        if (clip) shape = AbsoluteRoundedCornerShape(
            topLeft = CornerSize(corners.topLeft),
            topRight = CornerSize(corners.topRight),
            bottomRight = CornerSize(corners.bottomRight),
            bottomLeft = CornerSize(corners.bottomLeft)
        )
    }
    }
}

internal fun Modifier.predictiveBackScrim(state: PredictiveBackState): Modifier = graphicsLayer {
    alpha = (1f - 0.5f * backPreviewProgress(state.progress)) * (1f - state.exitProgress)
}

/** Parent stays in its original composition slot, preserving scroll and navigation state. */
@Composable
internal fun PredictivePageTransition(
    targetState: Boolean,
    onBack: () -> Unit,
    onMountedChanged: (Boolean) -> Unit = {},
    content: @Composable (Boolean) -> Unit
) {
    val parentActive = LocalBackLayerActive.current
    val opening = remember { Animatable(if (targetState) 1f else 0f) }
    var childCompleted by remember { mutableStateOf(false) }
    val ancestorCompleted = LocalCompletedBack.current
    val back = rememberPredictiveBack(enabled = targetState, onBack = onBack)
    val hideDock = parentActive && targetState &&
        back.phase != BackPhase.COMMITTING && !back.completed
    PageDockVisibility(hideDock)
    val dimColor = libraryOverlayDimColor()
    // A descendant completion only skips an ancestor's exit if that same commit
    // actually closes it. Consume the notification in this composition, never later.
    val skipExit = back.completed || childCompleted
    SideEffect { childCompleted = false }
    LaunchedEffect(targetState) {
        if (targetState) back.reset()
        if (!targetState && skipExit) opening.snapTo(0f)
        else opening.animateTo(if (targetState) 1f else 0f, tween(320))
    }
    val mounted = targetState || opening.value > 0f
    val currentMountedChanged by rememberUpdatedState(onMountedChanged)
    LaunchedEffect(mounted) { currentMountedChanged(mounted) }
    DisposableEffect(Unit) { onDispose { currentMountedChanged(false) } }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBackLayerActive provides (parentActive && !mounted)) {
            Box(Modifier.fillMaxSize().then(if (mounted) Modifier.clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                } else Modifier)) {
                content(false)
            }
        }
        if (mounted) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                // Match dialog dimming. Keep half its strength during the preview instead of
                // fading the parent to full brightness as soon as the page begins shrinking.
                alpha = opening.value * (1f - 0.5f * backPreviewProgress(back.progress)) * (1f - back.exitProgress)
            }.background(dimColor))
            CompositionLocalProvider(
                LocalBackLayerActive provides (parentActive && targetState),
                LocalCompletedBack provides { childCompleted = true; ancestorCompleted() }
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = size.width * (1f - opening.value)
                }.predictiveBackTransform(back, BackPresentation.PAGE)) {
                    content(true)
                }
            }
        }
    }
}

/** Visual occlusion is independent of the page's retained interaction/lifecycle state. */
@Composable
internal fun PageDockVisibility(hidden: Boolean) {
    val overlays = LocalGlassOverlayController.current
    val request = remember { Any() }
    SideEffect { overlays?.setPageDockHidden(request, hidden) }
    DisposableEffect(overlays, request) {
        onDispose { overlays?.setPageDockHidden(request, false) }
    }
}
