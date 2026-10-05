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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.max

internal val LocalBackLayerActive = staticCompositionLocalOf { true }
internal val LocalBackInOverlay = staticCompositionLocalOf { false }
internal val LocalOverlayBackActive = staticCompositionLocalOf { true }
private val LocalCompletedBack = staticCompositionLocalOf<() -> Unit> { {} }

internal enum class BackPresentation { PAGE, SHEET, DIALOG, MENU }

/** Business state is untouched until the system commits the gesture. */
@Stable
internal class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
        private set
    var exitProgress by mutableFloatStateOf(0f)
        private set
    var direction by mutableFloatStateOf(1f)
        private set
    var busy by mutableStateOf(false)
        private set
    var completed by mutableStateOf(false)
        private set
    var touchDeltaY by mutableFloatStateOf(0f)
        private set
    private var initialTouchY: Float? = null
    private var lastEventTime = 0L
    private var progressVelocity = 0f

    fun start() {
        busy = true; completed = false; exitProgress = 0f
        initialTouchY = null; touchDeltaY = 0f; lastEventTime = 0L; progressVelocity = 0f
    }
    fun reset() {
        progress = 0f; exitProgress = 0f; completed = false; busy = false
        touchDeltaY = 0f; initialTouchY = null; lastEventTime = 0L; progressVelocity = 0f
    }
    fun update(event: BackEventCompat) {
        val nextProgress = if (event.progress.isFinite()) event.progress.coerceIn(0f, 1f) else progress
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastEventTime
        if (lastEventTime != 0L && elapsed in 1L..100L) {
            val velocity = ((nextProgress - progress) * 1000f / elapsed).coerceIn(0f, 8f)
            progressVelocity = progressVelocity * 0.5f + velocity * 0.5f
        } else progressVelocity = 0f
        lastEventTime = now
        progress = nextProgress
        if (event.touchY.isFinite()) {
            if (initialTouchY == null) initialTouchY = event.touchY
            touchDeltaY = event.touchY - initialTouchY!!
        }
        direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    }
    suspend fun restore() {
        val startProgress = progress
        val startDeltaY = touchDeltaY
        animate(progress, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { value, _ ->
            progress = value
            touchDeltaY = if (startProgress > 0f) startDeltaY * (value / startProgress) else 0f
        }
        progress = 0f
        exitProgress = 0f
        touchDeltaY = 0f
        busy = false
    }
    /** Check legacy guards at the resting pose: they may present a confirmation dialog. */
    fun checkAtRest(check: () -> Boolean): Boolean {
        val previous = progress
        progress = 0f
        return check().also { if (it) progress = previous }
    }
    suspend fun finish() {
        // Material's 150–300ms range, shortened for a progressed or fast gesture.
        val completion = max(backPreviewProgress(progress), (progressVelocity / 4f).coerceIn(0f, 1f))
        val duration = (300f - 150f * completion).toInt()
        animate(0f, 1f, animationSpec = tween(duration)) { value, _ -> exitProgress = value }
        completed = true
        busy = false
    }
}

@Composable
internal fun rememberPredictiveBack(
    enabled: Boolean = true,
    canCommit: () -> Boolean = { true },
    onRejected: () -> Unit = {},
    onBack: () -> Unit
): PredictiveBackState {
    val state = remember { PredictiveBackState() }
    val currentCommit by rememberUpdatedState(canCommit)
    val currentRejected by rememberUpdatedState(onRejected)
    val currentBack by rememberUpdatedState(onBack)
    val active = LocalBackLayerActive.current
    val overlay = LocalBackInOverlay.current
    val overlayActive = LocalOverlayBackActive.current
    val overlays = LocalGlassOverlayController.current
    val imeVisible = rememberImeVisible()
    val completedBack = LocalCompletedBack.current
    PredictiveBackHandler(enabled = enabled && active && !imeVisible &&
        (if (overlay) overlayActive else overlays?.entries.isNullOrEmpty())) { events ->
        if (!state.busy && !state.completed) {
            state.start()
            try {
                var hasProgress = false
                events.collect { event -> hasProgress = true; state.update(event) }
                if (state.checkAtRest(currentCommit)) {
                    // Three-button navigation has no progress events; retain its ordinary exit.
                    if (hasProgress) {
                        state.finish()
                        completedBack()
                    } else state.restore()
                    currentBack()
                } else {
                    state.restore()
                    currentRejected()
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { state.restore() }
                throw cancelled
            }
        } else {
            // Consume the flow even if a rapid second back arrives during the settling animation.
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
    content: @Composable (Boolean) -> Unit
) {
    val parentActive = LocalBackLayerActive.current
    val opening = remember { Animatable(if (targetState) 1f else 0f) }
    var childCompleted by remember { mutableStateOf(false) }
    val ancestorCompleted = LocalCompletedBack.current
    val back = rememberPredictiveBack(enabled = targetState, onBack = onBack)
    val dimColor = libraryOverlayDimColor()
    LaunchedEffect(targetState, childCompleted) {
        if (targetState && childCompleted) {
            childCompleted = false
            return@LaunchedEffect
        }
        if (targetState) back.reset()
        if (!targetState && (back.completed || childCompleted)) opening.snapTo(0f)
        else opening.animateTo(if (targetState) 1f else 0f, tween(320))
    }
    val mounted = targetState || opening.value > 0f
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
