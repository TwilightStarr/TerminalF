package com.terminalf.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terminalf.app.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Kısa bilgi mesajı (Flutter `showToast` / SnackBar karşılığı). */
class Toaster(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }
}

val LocalToaster = staticCompositionLocalOf<Toaster> { error("Toaster sağlanmadı") }

class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val danger: Boolean,
    val result: CompletableDeferred<Boolean>,
)

/** Onay diyaloğu (Flutter `confirmDialog` karşılığı): `if (dialogs.confirm(...)) { ... }`. */
class DialogHost {
    var request by mutableStateOf<ConfirmRequest?>(null)
        private set

    suspend fun confirm(
        title: String,
        message: String,
        confirmLabel: String = "Devam",
        danger: Boolean = false,
    ): Boolean {
        val r = ConfirmRequest(title, message, confirmLabel, danger, CompletableDeferred())
        request = r
        try {
            return r.result.await()
        } finally {
            if (request === r) request = null
        }
    }

    internal fun finish(r: ConfirmRequest, value: Boolean) {
        r.result.complete(value)
        if (request === r) request = null
    }
}

val LocalDialogs = staticCompositionLocalOf<DialogHost> { error("DialogHost sağlanmadı") }

@Composable
fun DialogHostContent(host: DialogHost) {
    val r = host.request ?: return
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = { host.finish(r, false) },
        containerColor = c.surfaceHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text(r.title, fontWeight = FontWeight.ExtraBold, color = c.text) },
        text = { Text(r.message, color = c.muted) },
        dismissButton = {
            TextButton(onClick = { host.finish(r, false) }) { Text("Vazgeç", color = c.muted) }
        },
        confirmButton = {
            TextButton(onClick = { host.finish(r, true) }) {
                Text(
                    r.confirmLabel,
                    color = if (r.danger) c.danger else c.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
    )
}

class SheetEntry internal constructor(
    internal val content: @Composable (close: (Any?) -> Unit) -> Unit,
    internal val result: CompletableDeferred<Any?>,
    internal val key: String? = null,
)

/**
 * Alt sayfa (bottom sheet) yöneticisi (Flutter `showModalBottomSheet` karşılığı):
 * `val secim = sheets.show<String> { close -> ...; close("x") }`. Kapatılırsa `null` döner.
 */
class SheetHost {
    val stack = mutableStateListOf<SheetEntry>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> show(key: String? = null, content: @Composable (close: (T?) -> Unit) -> Unit): T? {
        // Aynı anahtarlı sayfa zaten açıksa (çift dokunuş) ikincisi açılmaz.
        if (key != null && stack.any { it.key == key }) return null
        val entry = SheetEntry(
            content = { close -> content { v -> close(v) } },
            result = CompletableDeferred(),
            key = key,
        )
        stack.add(entry)
        try {
            return entry.result.await() as T?
        } finally {
            stack.remove(entry)
        }
    }
}

val LocalSheets = staticCompositionLocalOf<SheetHost> { error("SheetHost sağlanmadı") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHostContent(host: SheetHost) {
    for (entry in host.stack.toList()) {
        androidx.compose.runtime.key(entry) {
            val c = AppTheme.colors
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            val close: (Any?) -> Unit = { value ->
                scope.launch {
                    try {
                        state.hide()
                    } finally {
                        entry.result.complete(value)
                    }
                }
            }
            ModalBottomSheet(
                onDismissRequest = { entry.result.complete(null) },
                sheetState = state,
                containerColor = c.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(Modifier.navigationBarsPadding()) {
                    entry.content(close)
                }
            }
        }
    }
}
