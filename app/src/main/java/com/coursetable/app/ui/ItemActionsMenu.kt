package com.coursetable.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme

internal data class ItemMenuAction(val label: String, val onClick: () -> Unit, val destructive: Boolean = false)

@Composable
internal fun QuietDeleteButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(Icons.Filled.Delete, description, Modifier.size(20.dp),
            tint = LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

/** Commit after the menu closes so a confirmation never competes with its overlay. */
@Composable
internal fun ItemActionsMenu(
    description: String,
    actions: List<ItemMenuAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var triggerHidden by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val source = rememberMenuSource()
    Box(modifier.onGloballyPositioned { anchor = it.boundsInRoot() }) {
        IconButton(onClick = { expanded = true },
            modifier = Modifier.graphicsLayer { alpha = if (triggerHidden) 0f else 1f },
            enabled = enabled && pendingAction == null) {
            Icon(Icons.Filled.MoreHorizontal, description, Modifier.size(20.dp).captureMenuSource(source, triggerHidden), tint = LiquidTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorBounds = anchor,
            alignment = DropdownMenuAlignment.END,
            sourceContent = source,
            onSourceCovered = { triggerHidden = true },
            onClosed = {
                triggerHidden = false
                val action = pendingAction
                pendingAction = null
                action?.invoke()
            }
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, color = if (action.destructive) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.onSurface) },
                    onClick = { if (enabled) pendingAction = action.onClick; expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun DeleteConfirmationDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButtonRole = DialogActionRole.Destructive,
        confirmButton = {
            val controller = LocalDialogDismissController.current
            TextButton(onClick = { controller?.dismiss(onConfirm) ?: onConfirm() }) { Text("删除") }
        },
        dismissButton = {
            val controller = LocalDialogDismissController.current
            TextButton(onClick = { controller?.dismiss() ?: onDismiss() }) { Text("取消") }
        }
    )
}
