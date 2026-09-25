package com.joseleandro.pomolume.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {

    // Lista de abas principais padrão
    val topLevelRoutes: List<Any> = listOf(
        TimerTabRoute,
        StatsTabRoute,
        SettingsTabRoute
    )

    // Stacks para cada aba principal
    private val topLevelStacks: LinkedHashMap<Any, SnapshotStateList<Any>> = linkedMapOf(
        TimerTabRoute to mutableStateListOf(TimerTabRoute)
    )

    // Aba selecionada atualmente
    var currentTab by mutableStateOf<Any>(TimerTabRoute)
        private set

    // Backstack unificado consumido pelo NavDisplay da Navigation 3
    val backStack: SnapshotStateList<Any> = mutableStateListOf(TimerTabRoute)

    private fun updateBackStack() {
        backStack.clear()
        backStack.addAll(topLevelStacks.flatMap { it.value })
    }

    // Seleciona / troca de aba mantendo o histórico de cada uma
    fun selectTab(tabRoute: Any) {
        if (topLevelStacks[tabRoute] == null) {
            topLevelStacks[tabRoute] = mutableStateListOf(tabRoute)
        } else {
            topLevelStacks.remove(tabRoute)?.let { stack ->
                topLevelStacks[tabRoute] = stack
            }
        }
        currentTab = tabRoute
        updateBackStack()
    }

    // Navega para uma sub-tela dentro da aba atual
    fun navigateTo(route: Any) {
        topLevelStacks[currentTab]?.add(route)
        updateBackStack()
    }

    // Volta no histórico de telas (pop)
    fun pop(): Boolean {
        val currentStack = topLevelStacks[currentTab] ?: return false
        if (currentStack.size > 1) {
            currentStack.removeLastOrNull()
            updateBackStack()
            return true
        } else if (topLevelStacks.size > 1) {
            // Se estiver na raiz da aba e existirem outras abas no histórico, volta para a aba anterior
            topLevelStacks.remove(currentTab)
            currentTab = topLevelStacks.keys.last()
            updateBackStack()
            return true
        }
        return false // Permite fechar o app se estiver na raiz da primeira aba
    }
}
