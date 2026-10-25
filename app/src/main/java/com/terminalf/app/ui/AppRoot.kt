package com.terminalf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import com.terminalf.app.AppContainer
import com.terminalf.app.state.Haptics
import com.terminalf.app.state.Motion
import com.terminalf.app.ui.components.DialogHost
import com.terminalf.app.ui.components.DialogHostContent
import com.terminalf.app.ui.components.LocalDialogs
import com.terminalf.app.ui.components.LocalSheets
import com.terminalf.app.ui.components.LocalToaster
import com.terminalf.app.ui.components.SheetHost
import com.terminalf.app.ui.components.SheetHostContent
import com.terminalf.app.ui.components.Toaster
import com.terminalf.app.ui.components.EaseOutCubicCurve
import com.terminalf.app.ui.pages.AppsPage
import com.terminalf.app.ui.pages.DevicePage
import com.terminalf.app.ui.pages.GamePage
import com.terminalf.app.ui.pages.HomePage
import com.terminalf.app.ui.pages.OptimizePage
import com.terminalf.app.ui.sheets.showWelcomeSheet
import com.terminalf.app.ui.theme.AppTheme
import com.terminalf.app.ui.theme.TerminalFTheme
import com.terminalf.app.ui.theme.faded
import kotlinx.coroutines.launch

/** Tema + ortak sunucular (toast, diyalog, alt sayfa) + gezinme kabuğu. */
@Composable
fun AppRoot(container: AppContainer) {
    val settings = container.settings.observe()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(snackbar, scope) }
    val dialogs = remember { DialogHost() }
    val sheets = remember { SheetHost() }

    TerminalFTheme(palette = settings.palette, logoArgb = settings.logoColors) {
        CompositionLocalProvider(
            LocalToaster provides toaster,
            LocalDialogs provides dialogs,
            LocalSheets provides sheets,
        ) {
            AppShell(container, snackbar)
            DialogHostContent(dialogs)
            SheetHostContent(sheets)
        }
    }
}

private class Tab(val label: String, val outlined: ImageVector, val rounded: ImageVector)

/**
 * Alt gezinme çubuğu + sayfalar. Sayfalar ilk açıldıklarında oluşturulur (uygulama hızlı açılır)
 * ve sonra bellekte tutulur (sekme değişince yeniden yüklenmez, uygulama listesi yeniden taranmaz).
 */
@Composable
private fun AppShell(container: AppContainer, snackbar: SnackbarHostState) {
    val c = AppTheme.colors
    val sheets = LocalSheets.current
    var index by remember { mutableIntStateOf(0) }
    val visited = remember { mutableStateOf(setOf(0)) }
    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val tabs = remember {
        listOf(
            Tab("Ana Sayfa", Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard),
            Tab("Optimize", Icons.Outlined.Tune, Icons.Rounded.Tune),
            Tab("Uygulamalar", Icons.Outlined.Apps, Icons.Rounded.Apps),
            Tab("Oyun", Icons.Outlined.SportsEsports, Icons.Rounded.SportsEsports),
            Tab("Cihaz", Icons.Outlined.Smartphone, Icons.Rounded.Smartphone),
        )
    }

    fun select(i: Int) {
        if (i != index) {
            Haptics.tap()
            if (Motion.enabled) {
                scope.launch {
                    fade.snapTo(0f)
                    fade.animateTo(1f, tween(240, easing = EaseOutCubicCurve))
                }
            }
        }
        index = i
        visited.value = visited.value + i
    }

    // İlk açılışta bir kez karşılama ve bildirim izni.
    LaunchedEffect(Unit) {
        container.settings.load()
        if (!container.settings.onboarded) {
            container.settings.markOnboarded()
            sheets.showWelcomeSheet(container.notifier)
        }
    }

    // Geri tuşu: önce Ana Sayfa'ya döner, orada uygulamadan çıkar.
    BackHandler(enabled = index != 0) { select(0) }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, tab ->
                    val selected = i == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { select(i) },
                        icon = { Icon(if (selected) tab.rounded else tab.outlined, contentDescription = tab.label) },
                        label = {
                            // Etiket tek satırda kalır: yazı tipi ölçeğinden bağımsız sabit boyut
                            // (dp -> sp), sığmazsa kırpılır; "Uygulamalar" iki satıra bölünmez.
                            Text(
                                tab.label,
                                fontSize = with(LocalDensity.current) { 11.dp.toSp() },
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.primary,
                            selectedTextColor = c.primary,
                            unselectedIconColor = c.muted,
                            unselectedTextColor = c.muted,
                            indicatorColor = c.primary.faded(0.16f),
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .graphicsLayer {
                    alpha = fade.value
                    translationY = (1f - fade.value) * 10.dp.toPx()
                },
        ) {
            for (i in 0 until 5) {
                if (i !in visited.value) continue
                val selected = i == index
                // Seçili olmayan sayfa boyutsuz ve yerleştirilmemiş: çizilmez, dokunmaya kapalı;
                // ama kompozisyonu (kaydırma konumu, yüklenen liste) korunur.
                Box(
                    if (selected) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.layout { measurable, constraints ->
                            measurable.measure(constraints)
                            layout(0, 0) {}
                        }
                    },
                ) {
                    when (i) {
                        0 -> HomePage(live = container.live, control = container.control)
                        1 -> OptimizePage(control = container.control)
                        2 -> AppsPage(control = container.control)
                        3 -> GamePage(control = container.control, game = container.game)
                        else -> DevicePage(live = container.live, settings = container.settings, notifier = container.notifier)
                    }
                }
            }
        }
    }
}
