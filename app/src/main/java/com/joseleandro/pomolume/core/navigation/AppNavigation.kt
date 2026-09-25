package com.joseleandro.pomolume.core.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joseleandro.pomolume.core.design.PomoTheme
import com.joseleandro.pomolume.feature.history.presentation.HistoryScreen
import com.joseleandro.pomolume.feature.history.presentation.HistoryViewModel
import com.joseleandro.pomolume.feature.pomodoro.domain.PomodoroAction
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.pomodoro.presentation.PomodoroScreen
import com.joseleandro.pomolume.feature.pomodoro.presentation.PomodoroViewModel
import com.joseleandro.pomolume.feature.settings.presentation.SettingsScreen
import com.joseleandro.pomolume.feature.settings.presentation.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

private const val TIMER = "pomodoro"
private const val HISTORY = "history"
private const val SETTINGS = "settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomoLumeApp(
    notificationAction: String? = null,
    onNotificationActionConsumed: () -> Unit = {},
    pomodoroViewModel: PomodoroViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val keepScreenOn by pomodoroViewModel.keepScreenOn.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    PomoTheme(settingsState.settings.theme) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: TIMER
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        var permissionAsked by rememberSaveable { mutableStateOf(false) }
        var showRationale by rememberSaveable { mutableStateOf(false) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingAction?.invoke()
            pendingAction = null
        }
        val withPermission: (() -> Unit) -> Unit = { action ->
            val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needed && !permissionAsked) {
                pendingAction = action
                showRationale = true
            } else action()
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle, pomodoroViewModel) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pomodoroViewModel.recover()
                pomodoroViewModel.completions.collect { completion ->
                    snackbar.showSnackbar(
                        if (completion.sessionType == SessionType.FOCUS) "Pomodoro concluído" else "Pausa concluída",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
        LaunchedEffect(notificationAction) {
            if (notificationAction != null) nav.navigate(TIMER) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when (route) { HISTORY -> "Histórico"; SETTINGS -> "Configurações"; else -> "Pomodoro" }) },
                    navigationIcon = {
                        if (route == SETTINGS) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        if (route == TIMER) IconButton(onClick = { nav.navigate(SETTINGS) { launchSingleTop = true } }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Configurações")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                if (route != SETTINGS) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    listOf(TIMER to "Pomodoro", HISTORY to "Histórico").forEach { (destination, label) ->
                        NavigationBarItem(
                            selected = route == destination,
                            onClick = {
                                nav.navigate(destination) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (destination == TIMER) Icons.Outlined.Timer else Icons.Outlined.History, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            NavHost(navController = nav, startDestination = TIMER,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                composable(TIMER) {
                    val state by pomodoroViewModel.uiState.collectAsStateWithLifecycle()
                    PomodoroScreen(
                        state = state,
                        onAction = { action ->
                            if (action == PomodoroAction.START && state.settings.notificationsEnabled) {
                                withPermission { pomodoroViewModel.onAction(action) }
                            } else pomodoroViewModel.onAction(action)
                        },
                        onRetry = pomodoroViewModel::recover,
                        notificationAction = notificationAction,
                        onNotificationActionConsumed = onNotificationActionConsumed
                    )
                }
                composable(HISTORY) {
                    val state by historyViewModel.uiState.collectAsStateWithLifecycle()
                    HistoryScreen(state, historyViewModel::selectFilter, historyViewModel::retry)
                }
                composable(SETTINGS) {
                    SettingsScreen(settingsState, settingsViewModel::update,
                        onEnableNotifications = { withPermission { settingsViewModel.update { it.copy(notificationsEnabled = true) } } },
                        onRetry = settingsViewModel::load)
                }
            }
        }
        if (showRationale) AlertDialog(
            onDismissRequest = {
                showRationale = false
                permissionAsked = true
                pendingAction?.invoke()
                pendingAction = null
            },
            title = { Text("Receber avisos de sessão") },
            text = { Text("Permita notificações para saber quando o foco ou a pausa terminar, mesmo com o app minimizado.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionAsked = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionAsked = true
                    pendingAction?.invoke()
                    pendingAction = null
                }) { Text("Agora não") }
            }
        )
    }
}
