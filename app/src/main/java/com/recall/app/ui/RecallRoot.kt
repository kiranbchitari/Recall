package com.recall.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recall.app.data.AppSettings
import com.recall.app.ui.components.container
import com.recall.app.ui.components.softShadow
import com.recall.app.ui.detail.ItemDetailScreen
import com.recall.app.ui.home.HomeScreen
import com.recall.app.ui.inbox.InboxScreen
import com.recall.app.ui.review.ReviewScreen
import com.recall.app.ui.settings.SettingsScreen
import com.recall.app.ui.theme.Recall
import com.recall.app.ui.theme.RecallTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** Emits the current time now and then every half minute, so relative dates and snoozes stay honest. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(30_000)
    }
}

enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    INBOX("inbox", "Inbox", Icons.Outlined.Inbox, Icons.Rounded.Inbox),
    REVIEW("review", "Review", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    ME("me", "Me", Icons.Outlined.Person, Icons.Rounded.Person),
}

/** A one-off request to go somewhere, from a notification or the widget. */
sealed interface Launch {
    data class OpenItem(val id: Long) : Launch
    data object OpenReview : Launch
}

private const val ITEM_ROUTE = "item/{id}"

private fun NavController.switchTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavController.openItem(id: Long) = navigate("item/$id") { launchSingleTop = true }

@Composable
fun RecallRoot(launch: Launch?, onLaunchHandled: () -> Unit) {
    val container = container()
    val loaded by container.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    // Wait for the saved theme rather than flashing the default one.
    val settings: AppSettings = loaded ?: return

    RecallTheme(settings.theme, settings.accent) {
        val p = Recall.colors
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val activity = LocalContext.current as? ComponentActivity
        DisposableEffect(p.dark) {
            val clear = Color.Transparent.toArgb()
            val style = if (p.dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
            activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }

        LaunchedEffect(Unit) {
            container.messages.flow.collect { message ->
                // The newest confirmation replaces the previous one rather than queueing behind it.
                snackbar.currentSnackbarData?.dismiss()
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = message.text,
                        actionLabel = if (message.undo != null) "Undo" else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) container.scope.launch { message.undo?.invoke() }
                }
            }
        }

        LaunchedEffect(launch) {
            when (launch) {
                is Launch.OpenItem -> nav.openItem(launch.id)
                Launch.OpenReview -> nav.switchTab(Tab.REVIEW)
                null -> return@LaunchedEffect
            }
            onLaunchHandled()
        }

        val entry by nav.currentBackStackEntryAsState()
        val tab = Tab.entries.firstOrNull { it.route == entry?.destination?.route }

        Box(Modifier.fillMaxSize().background(p.background)) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = p.ink,
                bottomBar = {
                    AnimatedVisibility(
                        visible = tab != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        BottomBar(tab, onSelect = { nav.switchTab(it) })
                    }
                },
                snackbarHost = {
                    SnackbarHost(snackbar, Modifier.imePadding()) { data ->
                        Snackbar(
                            snackbarData = data,
                            shape = RoundedCornerShape(18.dp),
                            containerColor = if (p.dark) p.surfaceSoft else p.ink,
                            contentColor = if (p.dark) p.ink else Color.White,
                            actionColor = if (p.dark) p.primary else Color(0xFFCFC5FF),
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = Tab.HOME.route,
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable(Tab.HOME.route) {
                        HomeScreen(
                            contentPadding = padding,
                            onOpenItem = nav::openItem,
                            onOpenReview = { nav.switchTab(Tab.REVIEW) },
                            onOpenMe = { nav.switchTab(Tab.ME) },
                        )
                    }
                    composable(Tab.INBOX.route) { InboxScreen(padding, onOpenItem = nav::openItem) }
                    composable(Tab.REVIEW.route) {
                        ReviewScreen(padding, onOpenItem = nav::openItem, onOpenSettings = { nav.switchTab(Tab.ME) })
                    }
                    composable(Tab.ME.route) { SettingsScreen(padding) }
                    composable(
                        route = ITEM_ROUTE,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        enterTransition = { slideInVertically(tween(220)) { it / 10 } + fadeIn(tween(220)) },
                        popExitTransition = { slideOutVertically(tween(180)) { it / 10 } + fadeOut(tween(180)) },
                    ) { backStackEntry ->
                        ItemDetailScreen(
                            id = backStackEntry.arguments?.getLong("id") ?: -1,
                            contentPadding = padding,
                            // Guarded so a second "back" (e.g. after a delete) can never pop a tab.
                            onBack = { if (nav.currentDestination?.route == ITEM_ROUTE) nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit) {
    val p = Recall.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .softShadow(shape, 18.dp)
            .clip(shape)
            .background(p.surface)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val color = if (selected) p.primary else p.inkSoft
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) p.primarySoft else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (selected) tab.selectedIcon else tab.icon, null, tint = color, modifier = Modifier.size(26.dp))
                Text(tab.label, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
