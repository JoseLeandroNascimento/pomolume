package com.joseleandro.pomolume.ui.navigation

import kotlinx.serialization.Serializable

// Top-Level Routes (Tabs)
@Serializable
data object TimerTabRoute

@Serializable
data object StatsTabRoute

@Serializable
data object SettingsTabRoute

// Sub-routes / Detail Screens
@Serializable
data class TaskDetailRoute(val taskId: String)

@Serializable
data class SettingsDetailRoute(val section: String)
