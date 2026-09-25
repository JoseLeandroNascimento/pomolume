package com.joseleandro.pomolume.core.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.PomodoroTimer
import com.joseleandro.pomolume.core.design.AppElevation
import com.joseleandro.pomolume.feature.pomodoro.domain.TimerState
import androidx.navigation.NavDestination.Companion.hasRoute
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomoLumeApp(
    notificationAction: String? = null,
    notificationSessionId: String? = null,
    onNotificationActionConsumed: () -> Unit = {},
    pomodoroViewModel: PomodoroViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel? = null,
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    // Capture the original owner before NavHost supplies a destination owner.
    // History is created on demand but retains the same Activity scope and saved state.
    val appViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
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
        val destination = backStack?.destination
        val isHistory = destination?.hasRoute<HistoryRoute>() == true
        val isSettings = destination?.hasRoute<SettingsRoute>() == true
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        val focusCompleted by rememberUpdatedState(stringResource(R.string.pomodoro_focus_completed))
        val breakCompleted by rememberUpdatedState(stringResource(R.string.pomodoro_break_completed))
        var permissionAsked by rememberSaveable { mutableStateOf(false) }
        var showRationale by rememberSaveable { mutableStateOf(false) }
        var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
        val performPermissionAction: (String?) -> Unit = { action ->
            when (action) {
                "START" -> pomodoroViewModel.onAction(PomodoroAction.START)
                "ENABLE_NOTIFICATIONS" -> settingsViewModel.update { it.copy(notificationsEnabled = true) }
            }
        }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            performPermissionAction(pendingAction)
            pendingAction = null
        }
        val withPermission: (String) -> Unit = { action ->
            val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needed && (!permissionAsked || action == "ENABLE_NOTIFICATIONS")) {
                pendingAction = action
                showRationale = true
            } else performPermissionAction(action)
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle, pomodoroViewModel) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pomodoroViewModel.recover()
                pomodoroViewModel.completions.collect { completion ->
                    snackbar.showSnackbar(
                        if (completion.sessionType == SessionType.FOCUS) focusCompleted else breakCompleted,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
        LaunchedEffect(notificationAction) {
            if (notificationAction != null) nav.navigate(PomodoroRoute) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(when { isHistory -> R.string.navigation_history; isSettings -> R.string.navigation_settings; else -> R.string.pomodoro_title })) },
                    navigationIcon = {
                        if (isSettings) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigation_back))
                        }
                    },
                    actions = {
                        if (!isSettings && !isHistory) IconButton(onClick = { nav.navigate(SettingsRoute) { launchSingleTop = true } }) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.navigation_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                if (!isSettings) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = AppElevation.none) {
                    listOf(PomodoroRoute to R.string.pomodoro_title, HistoryRoute to R.string.navigation_history).forEach { (target, label) ->
                        NavigationBarItem(
                            selected = if (target == PomodoroRoute) !isHistory else isHistory,
                            modifier = Modifier.testTag(if (target == PomodoroRoute) "tab_pomodoro" else "tab_history"),
                            onClick = {
                                nav.navigate(target) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (target == PomodoroRoute) Icons.Outlined.Timer else Icons.Outlined.History, contentDescription = null) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            NavHost(navController = nav, startDestination = PomodoroRoute,
                enterTransition = { fadeIn(tween(160)) }, exitTransition = { fadeOut(tween(120)) },
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                composable<PomodoroRoute>() {
                    val state by pomodoroViewModel.layoutState.collectAsStateWithLifecycle()
                    PomodoroScreen(
                        state = state,
                        onAction = { action ->
                            if (action == PomodoroAction.START && state.settings.notificationsEnabled) {
                                withPermission("START")
                            } else if (state.timer.sessionId.isBlank()) {
                                pomodoroViewModel.onAction(action)
                            } else pomodoroViewModel.onActionForSession(action, state.timer.sessionId)
                        },
                        onRetry = pomodoroViewModel::recover,
                        notificationAction = notificationAction,
                        notificationSessionId = notificationSessionId,
                        onNotificationActionConsumed = onNotificationActionConsumed,
                        timerContent = { diameter ->
                            val reading by pomodoroViewModel.uiState.collectAsStateWithLifecycle()
                            PomodoroTimer(reading.timer.remainingTimeMillis, reading.timer.progress, reading.timer.sessionType,
                                reading.timer.timerState == TimerState.PAUSED, diameter, reading.settings.timerAppearance)
                        }
                    )
                }
                composable<HistoryRoute>() {
                    val history = historyViewModel ?: koinViewModel<HistoryViewModel>(viewModelStoreOwner = appViewModelStoreOwner)
                    val state by history.uiState.collectAsStateWithLifecycle()
                    HistoryScreen(state, history::selectFilter, history::retry)
                }
                composable<SettingsRoute>() {
                    SettingsScreen(settingsState, settingsViewModel::update,
                        onEnableNotifications = { withPermission("ENABLE_NOTIFICATIONS") },
                        onRetry = settingsViewModel::load)
                }
            }
        }
        if (showRationale) AlertDialog(
            onDismissRequest = {
                showRationale = false
                permissionAsked = true
                performPermissionAction(pendingAction)
                pendingAction = null
            },
            title = { Text(stringResource(R.string.permission_title)) },
            text = { Text(stringResource(R.string.permission_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionAsked = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        performPermissionAction(pendingAction)
                        pendingAction = null
                    }
                }) { Text(stringResource(R.string.permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionAsked = true
                    performPermissionAction(pendingAction)
                    pendingAction = null
                }) { Text(stringResource(R.string.permission_later)) }
            }
        )
    }
}
