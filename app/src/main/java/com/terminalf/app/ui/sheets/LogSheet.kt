package com.terminalf.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.state.ControlController
import com.terminalf.app.state.TweakOutcome
import com.terminalf.app.ui.components.SheetHost
import com.terminalf.app.ui.components.Toaster
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.faded

/** İşlem günlüğü: arayüzü kalabalıklaştırmadan, gerektiğinde açılan alt sayfa. */
suspend fun SheetHost.showLogSheet(control: ControlController) {
    show<Unit>(key = "log") { _ -> LogSheetBody(control) }
}

/** Sonuç için kısa bildirim (başarı/hata ayrımı yok, metin zaten açık). */
fun showOutcome(toast: Toaster, outcome: TweakOutcome) {
    toast.show(outcome.message)
}

@Composable
private fun LogSheetBody(control: ControlController) {
    control.observe()
    val c = AppTheme.colors
    val lines = control.log.reversed()

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 14.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "İşlem Günlüğü",
                modifier = Modifier.weight(1f),
                color = c.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            TextButton(
                onClick = { control.clearLog() },
                enabled = lines.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = c.primary,
                    disabledContentColor = c.muted.faded(0.5f),
                ),
            ) {
                Text("Temizle")
            }
        }
        if (lines.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Henüz bir işlem yapılmadı.", color = c.muted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                itemsIndexed(lines) { _, line ->
                    val color = when {
                        line.contains("✗") -> c.danger
                        line.contains("✓") -> c.good
                        else -> c.muted
                    }
                    Text(
                        line,
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = color,
                        fontSize = 12.5.sp,
                        lineHeight = (12.5 * 1.35).sp,
                    )
                }
            }
        }
    }
}
