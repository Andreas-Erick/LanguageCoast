package com.andreaserick.languagecoast.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.andreaserick.languagecoast.ui.theme.DeepOceanBlue
import com.andreaserick.languagecoast.ui.theme.SandBeige
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows "message + Undo" snackbars from the root of the app. Both the snackbar and the undo action
 * run in the root [scope], so they keep working after the screen that started them is left.
 */
class UndoMessenger(private val hostState: SnackbarHostState, private val scope: CoroutineScope) {

    /** Shows [message] with an Undo action, replacing any snackbar already showing. */
    fun show(message: String, onUndo: suspend () -> Unit) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

val LocalUndoMessenger = staticCompositionLocalOf<UndoMessenger> { error("No UndoMessenger provided") }

/** A snackbar in the app's colours. */
@Composable
fun CoastSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        containerColor = SandBeige,
        contentColor = DeepOceanBlue,
        actionColor = DeepOceanBlue,
        actionContentColor = DeepOceanBlue,
        dismissActionContentColor = DeepOceanBlue
    )
}
