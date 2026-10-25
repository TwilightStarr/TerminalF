package com.terminalf.app.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.LocalContainer
import com.terminalf.app.data.InstalledApp
import com.terminalf.app.state.SavedGame
import com.terminalf.app.ui.theme.AppTheme

/**
 * Yüklü uygulamalardan oyun seçme sayfasının içeriği (Flutter `_GamePicker`). `existing`, zaten
 * eklenmiş paketlerdir (yanlarında onay işareti gösterilir). Seçilince [close] ile döner.
 */
@Composable
internal fun GamePickerSheet(
    existing: Set<String>,
    close: (SavedGame?) -> Unit,
) {
    val c = AppTheme.colors
    val service = LocalContainer.current.appsService

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val result = service.list(includeSystemApps = false)
        apps = result
        loading = false
    }

    val q = query.trim().lowercase()
    val list: List<InstalledApp> = if (q.isEmpty()) {
        apps
    } else {
        apps.filter { it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .imePadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 2.dp, end = 22.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Oyun seç", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        val fieldShape = RoundedCornerShape(14.dp)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .height(56.dp),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(fieldShape)
                        .background(c.surfaceHigh, fieldShape),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text("Oyun ara", color = c.muted, fontSize = 16.sp, maxLines = 1)
                        }
                        inner()
                    }
                }
            },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary)
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Uygulama bulunamadı.", color = c.muted)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.packageName }) { a ->
                        GamePickerRow(
                            app = a,
                            added = existing.contains(a.packageName),
                            onClick = { close(SavedGame(a.packageName, a.name)) },
                        )
                    }
                }
            }
        }
    }
}

/** Flutter `ListTile` (iki satırlı): simge, ad, paket adı, eklendiyse onay işareti. */
@Composable
private fun GamePickerRow(app: InstalledApp, added: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val icon = app.icon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == null) {
                Icon(Icons.Rounded.Android, contentDescription = null, tint = c.muted)
            } else {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.name,
                color = c.text,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                color = c.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (added) {
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.good)
        }
    }
}
