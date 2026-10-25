package com.terminalf.app.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalf.app.LocalContainer
import com.terminalf.app.core.isProtectedPackage
import com.terminalf.app.core.parsePackageList
import com.terminalf.app.data.AppsService
import com.terminalf.app.data.InstalledApp
import com.terminalf.app.state.ControlController
import com.terminalf.app.state.TweakOutcome
import com.terminalf.app.state.getShowSystemApps
import com.terminalf.app.state.setShowSystemApps
import com.terminalf.app.ui.components.DialogHost
import com.terminalf.app.ui.components.LocalDialogs
import com.terminalf.app.ui.components.LocalSheets
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.SheetHost
import com.terminalf.app.ui.components.SoftButton
import com.terminalf.app.ui.components.Toaster
import com.terminalf.app.ui.observe
import com.terminalf.app.ui.sheets.showControlSetup
import com.terminalf.app.ui.sheets.showOutcome
import com.terminalf.app.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Uygulamalar: ara, filtrele, dokun, yönet. Yetki varsa gerçek zorla durdur,
 * önbellek temizleme, dondurma ve kaldırma; yoksa sistem ekranlarına yönlendirir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsPage(control: ControlController) {
    control.observe()
    val c = AppTheme.colors
    val container = LocalContainer.current
    val service = container.appsService
    val store = container.store
    val toast = LocalToaster.current
    val dialogs = LocalDialogs.current
    val sheets = LocalSheets.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }

    suspend fun load(silent: Boolean = false) {
        if (!silent) loading = true
        val result = service.list(includeSystemApps = showSystem)
        apps = result
        loading = false
    }

    LaunchedEffect(Unit) {
        showSystem = store.getShowSystemApps()
        load()
    }

    val filtered: List<InstalledApp> = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    fun openActions(app: InstalledApp) {
        scope.launch {
            val reload = sheets.show<Boolean> { close ->
                ActionsSheet(
                    app = app,
                    control = control,
                    service = service,
                    toast = toast,
                    dialogs = dialogs,
                    sheets = sheets,
                    scope = scope,
                    close = close,
                )
            }
            if (reload == true) load()
        }
    }

    fun openFrozen() {
        scope.launch {
            if (!control.ready) {
                sheets.showControlSetup(control)
                return@launch
            }
            sheets.show<Boolean> { _ ->
                FrozenSheet(control = control, toast = toast, scope = scope)
            }
            load()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Uygulamalar",
                modifier = Modifier.weight(1f),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.text,
            )
            IconButton(onClick = { openFrozen() }) {
                Icon(
                    Icons.Rounded.AcUnit,
                    contentDescription = "Dondurulmuş uygulamalar",
                    tint = c.muted,
                )
            }
            IconButton(
                onClick = { scope.launch { load() } },
                enabled = !loading,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Yenile", tint = c.muted)
            }
        }

        // Arama alanı
        val searchShape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier
                .padding(start = 20.dp, top = 10.dp, end = 20.dp)
                .fillMaxWidth()
                .height(48.dp)
                .onFocusChanged { searchFocused = it.isFocused },
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(searchShape)
                        .background(c.surface, searchShape)
                        .border(
                            1.dp,
                            if (searchFocused) c.primary else c.outline,
                            searchShape,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Uygulama ara", color = c.muted, fontSize = 16.sp, maxLines = 1)
                        }
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Temizle", tint = c.muted)
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (loading) "Yükleniyor…" else "${filtered.size} uygulama",
                modifier = Modifier.weight(1f),
                color = c.muted,
                fontSize = 13.sp,
            )
            Text("Sistem uygulamaları", color = c.muted, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = showSystem,
                onCheckedChange = { value ->
                    showSystem = value
                    scope.launch {
                        store.setShowSystemApps(value)
                        load()
                    }
                },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (apps.isEmpty()) {
                            "Uygulama listesi alınamadı.\nBu özellik yalnızca Android'de çalışır."
                        } else {
                            "Aramanızla eşleşen uygulama yok."
                        },
                        textAlign = TextAlign.Center,
                        color = c.muted,
                        lineHeight = 20.3.sp,
                    )
                }
            } else {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                load(silent = true)
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = refreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = c.surfaceHigh,
                            color = c.primary,
                        )
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppTile(app = app, onTap = { openActions(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: InstalledApp, onTap: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .clickable(onClick = onTap)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(bitmap = app.icon)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    app.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = c.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = c.muted,
                    fontSize = 12.sp,
                )
            }
            Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = c.muted)
        }
    }
}

@Composable
private fun AppIcon(bitmap: ImageBitmap?, size: Dp = 44.dp) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = Modifier.size(size).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Box(
                Modifier.fillMaxSize().background(c.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Android, contentDescription = null, tint = c.muted)
            }
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ActionsSheet(
    app: InstalledApp,
    control: ControlController,
    service: AppsService,
    toast: Toaster,
    dialogs: DialogHost,
    sheets: SheetHost,
    scope: CoroutineScope,
    close: (Boolean?) -> Unit,
) {
    control.observe()
    val c = AppTheme.colors
    val pkg = app.packageName
    var working by remember { mutableStateOf<String?>(null) }

    fun doAction(label: String, reload: Boolean = false, action: suspend () -> TweakOutcome) {
        working = label
        scope.launch {
            val outcome = try {
                action()
            } finally {
                working = null
            }
            showOutcome(toast, outcome)
            if (outcome.ok && reload) close(true)
        }
    }

    fun needControl(): Boolean {
        if (control.ready) return false
        scope.launch { sheets.showControlSetup(control) }
        return true
    }

    fun forceStop() {
        if (!control.ready) {
            service.openSystemSettings(pkg)
            toast.show("Açılan ekranda \"Zorla Durdur\"a basın. Tek dokunuş için tam kontrolü etkinleştirin.")
            return
        }
        doAction("Durdur") { control.forceStop(pkg) }
    }

    fun freeze() {
        if (needControl()) return
        if (isProtectedPackage(pkg)) {
            toast.show("Bu uygulama sistem için kritik; dondurulamaz.")
            return
        }
        scope.launch {
            val ok = dialogs.confirm(
                title = "${app.name} dondurulsun mu?",
                message = "Uygulama devre dışı kalır ve listeden/ekrandan kaybolur. " +
                    "Üstteki ❄ düğmesinden istediğiniz zaman tekrar etkinleştirebilirsiniz.",
                confirmLabel = "Dondur",
            )
            if (!ok) return@launch
            doAction("Dondur", reload = true) { control.freeze(pkg) }
        }
    }

    fun uninstall() {
        if (isProtectedPackage(pkg)) {
            toast.show("Bu uygulama sistem için kritik; kaldırılamaz.")
            return
        }
        scope.launch {
            val ok = dialogs.confirm(
                title = "${app.name} kaldırılsın mı?",
                message = if (control.ready) {
                    "Uygulama bu kullanıcı için kaldırılır. Sistem uygulamalarını ileride geri yüklemek zor olabilir."
                } else {
                    "Sistemin kaldırma onayı açılacak."
                },
                confirmLabel = "Kaldır",
                danger = true,
            )
            if (!ok) return@launch
            if (control.ready) {
                doAction("Kaldır", reload = true) { control.uninstallForUser(pkg) }
            } else {
                val done = service.uninstall(pkg)
                if (done) close(true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, top = 6.dp, end = 22.dp, bottom = 18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(bitmap = app.icon, size = 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = c.muted,
                    fontSize = 12.5.sp,
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                icon = Icons.Rounded.PlayArrow,
                label = "Aç",
                modifier = Modifier.weight(1f),
                onTap = {
                    service.launch(pkg)
                    close(false)
                },
            )
            ActionButton(
                icon = Icons.Outlined.StopCircle,
                label = "Durdur",
                busy = working == "Durdur",
                modifier = Modifier.weight(1f),
                onTap = { forceStop() },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                icon = Icons.Rounded.CleaningServices,
                label = "Önbellek Temizle",
                busy = working == "Önbellek",
                modifier = Modifier.weight(1f),
                onTap = {
                    if (!needControl()) {
                        doAction("Önbellek") { control.clearAppCache(pkg) }
                    }
                },
            )
            ActionButton(
                icon = Icons.Outlined.Info,
                label = "Sistem Bilgisi",
                modifier = Modifier.weight(1f),
                onTap = { service.openSystemSettings(pkg) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                icon = Icons.Rounded.AcUnit,
                label = "Dondur",
                busy = working == "Dondur",
                modifier = Modifier.weight(1f),
                onTap = { freeze() },
            )
            ActionButton(
                icon = Icons.Rounded.DeleteOutline,
                label = "Kaldır",
                danger = true,
                busy = working == "Kaldır",
                modifier = Modifier.weight(1f),
                onTap = { uninstall() },
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    busy: Boolean = false,
) {
    val c = AppTheme.colors
    val color = if (danger) c.danger else c.text
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .aspectRatio(2.6f)
            .clip(shape)
            .background(c.surfaceHigh, shape)
            .border(BorderStroke(1.dp, c.outline), shape)
            .clickable(enabled = !busy, onClick = onTap)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

/** Dondurulmuş (devre dışı) uygulamaların listesi ve tek dokunuşla geri alma. */
@Composable
private fun FrozenSheet(control: ControlController, toast: Toaster, scope: CoroutineScope) {
    val c = AppTheme.colors
    var packages by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            val result = control.exec("pm list packages -d")
            packages = parsePackageList(result.output)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Liste okunamadı."
        }
    }

    LaunchedEffect(Unit) { load() }

    fun enable(pkg: String) {
        working = pkg
        scope.launch {
            val outcome = try {
                control.unfreeze(pkg)
            } finally {
                working = null
            }
            showOutcome(toast, outcome)
            if (outcome.ok) load()
        }
    }

    val list = packages
    val err = error
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f)) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .padding(start = 22.dp, top = 16.dp, end = 22.dp, bottom = 8.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "Dondurulmuş Uygulamalar",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.text,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (err != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(err, color = c.muted)
                }
            } else if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Dondurulmuş uygulama yok.", color = c.muted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp),
                ) {
                    items(list, key = { it }) { pkg ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pkg,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                color = c.text,
                            )
                            Spacer(Modifier.width(10.dp))
                            if (working == pkg) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.2.dp,
                                )
                            } else {
                                SoftButton(label = "Etkinleştir", onClick = { enable(pkg) })
                            }
                        }
                    }
                }
            }
        }
    }
}
