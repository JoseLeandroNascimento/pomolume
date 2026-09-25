# Legacy source backup before authorized cleanup

User requested removal of dead code in the refinement round. Imports were checked with IDE search: these files reference each other only; active core/feature/MainActivity use no legacy packages. This snapshot contains IDE buffer contents, including any unsaved edits, for recovery. Each file can be restored using its exact contents below.

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/components/ProgressTimer.kt

```kotlin
package com.joseleandro.pomolume.ui.components

import android.content.res.Configuration
import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.pomolume.ui.theme.PomoLumeTheme

private val ProgressTimerSize = 300.dp
private val ProgressTimerStrokeWidth = 16.dp

@Composable
fun ProgressTimer(
    modifier: Modifier = Modifier,
    size: Dp = ProgressTimerSize,
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
    color: Color = MaterialTheme.colorScheme.primary,
    @FloatRange(from = 0.0, to = 1.0) progress: Float,
    time: String
) {

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(
                        width = ProgressTimerStrokeWidth.toPx(),
                    )
                )

                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(
                        width = ProgressTimerStrokeWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = time,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 60.sp
            )
        )
    }

}

@Preview(name = "light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProgressTimerPreview() {
    PomoLumeTheme(
        dynamicColor = false
    ) {
        ProgressTimer(
            progress = .5f,
            time = "25:00"
        )
    }
}
```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/navigation/NavigationRoute.kt

```kotlin
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

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/navigation/NavigationViewModel.kt

```kotlin
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

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/settings/SettingsScreen.kt

```kotlin
package com.joseleandro.pomolume.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onOpenSection: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onOpenSection("Geral") }) {
            Text("Configurações Gerais")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onOpenSection("Notificações") }) {
            Text("Notificações")
        }
    }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/stats/StatsScreen.kt

```kotlin
package com.joseleandro.pomolume.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = koinViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Estatísticas de Pomodoro",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Total de Sessões Registradas: ${sessions.size}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/stats/StatsViewModel.kt

```kotlin
package com.joseleandro.pomolume.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.usecase.GetPomodoroSessionsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    getSessionsUseCase: GetPomodoroSessionsUseCase
) : ViewModel() {

    val sessions: StateFlow<List<PomodoroSession>> = getSessionsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/theme/Color.kt

```kotlin
package com.joseleandro.pomolume.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/theme/Theme.kt

```kotlin
package com.joseleandro.pomolume.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.joseleandro.pomolume.core.design.PomoTheme
import com.joseleandro.pomolume.feature.settings.domain.AppTheme

@Composable
fun PomoLumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PomoTheme(theme = if (darkTheme) AppTheme.DARK else AppTheme.LIGHT, content = content)
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/theme/Type.kt

```kotlin
package com.joseleandro.pomolume.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/timer/TimerScreen.kt

```kotlin
package com.joseleandro.pomolume.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joseleandro.pomolume.ui.components.ProgressTimer
import org.koin.androidx.compose.koinViewModel

@Composable
fun TimerScreen(
    onOpenTaskDetail: (String) -> Unit, viewModel: TimerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            ProgressTimer(
                progress = .5f,
                size = 300.dp,
                time = "25:00"
            )
        }
    }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/timer/TimerUiState.kt

```kotlin
package com.joseleandro.pomolume.ui.timer

data class TimerUiState(
    val timeRemainingInSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val completedSessionsCount: Int = 0
) {
    val formattedTime: String
        get() {
            val minutes = timeRemainingInSeconds / 60
            val seconds = timeRemainingInSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/ui/timer/TimerViewModel.kt

```kotlin
package com.joseleandro.pomolume.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.usecase.GetPomodoroSessionsUseCase
import com.joseleandro.pomolume.domain.usecase.SavePomodoroSessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel(
    private val getSessionsUseCase: GetPomodoroSessionsUseCase,
    private val saveSessionUseCase: SavePomodoroSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            getSessionsUseCase().collect { sessions ->
                _uiState.update { currentState ->
                    currentState.copy(completedSessionsCount = sessions.size)
                }
            }
        }
    }

    fun toggleTimer() {
        _uiState.update { currentState ->
            currentState.copy(isRunning = !currentState.isRunning)
        }
    }

    fun finishSession() {
        viewModelScope.launch {
            saveSessionUseCase(
                PomodoroSession(
                    durationInMinutes = 25,
                    isCompleted = true
                )
            )
            _uiState.update { currentState ->
                currentState.copy(
                    isRunning = false,
                    timeRemainingInSeconds = 25 * 60
                )
            }
        }
    }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/data/local/AppDatabase.kt

```kotlin
package com.joseleandro.pomolume.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.joseleandro.pomolume.data.local.dao.PomodoroDao
import com.joseleandro.pomolume.data.local.entity.PomodoroSessionEntity

@Database(
    entities = [PomodoroSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pomodoroDao(): PomodoroDao
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/data/local/dao/PomodoroDao.kt

```kotlin
package com.joseleandro.pomolume.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joseleandro.pomolume.data.local.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<PomodoroSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSessionEntity)
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/data/local/entity/PomodoroSessionEntity.kt

```kotlin
package com.joseleandro.pomolume.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val durationInMinutes: Int,
    val timestamp: Long,
    val isCompleted: Boolean
)

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/data/local/mapper/PomodoroMapper.kt

```kotlin
package com.joseleandro.pomolume.data.local.mapper

import com.joseleandro.pomolume.data.local.entity.PomodoroSessionEntity
import com.joseleandro.pomolume.domain.model.PomodoroSession

fun PomodoroSessionEntity.toDomain(): PomodoroSession {
    return PomodoroSession(
        id = id,
        durationInMinutes = durationInMinutes,
        timestamp = timestamp,
        isCompleted = isCompleted
    )
}

fun PomodoroSession.toEntity(): PomodoroSessionEntity {
    return PomodoroSessionEntity(
        id = id,
        durationInMinutes = durationInMinutes,
        timestamp = timestamp,
        isCompleted = isCompleted
    )
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/data/repository/PomodoroRepositoryImpl.kt

```kotlin
package com.joseleandro.pomolume.data.repository

import com.joseleandro.pomolume.data.local.dao.PomodoroDao
import com.joseleandro.pomolume.data.local.mapper.toDomain
import com.joseleandro.pomolume.data.local.mapper.toEntity
import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.repository.PomodoroRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PomodoroRepositoryImpl(
    private val dao: PomodoroDao
) : PomodoroRepository {

    override fun getSessions(): Flow<List<PomodoroSession>> {
        return dao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveSession(session: PomodoroSession) {
        dao.insertSession(session.toEntity())
    }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/domain/model/PomodoroSession.kt

```kotlin
package com.joseleandro.pomolume.domain.model

data class PomodoroSession(
    val id: Long = 0,
    val durationInMinutes: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = true
)

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/domain/repository/PomodoroRepository.kt

```kotlin
package com.joseleandro.pomolume.domain.repository

import com.joseleandro.pomolume.domain.model.PomodoroSession
import kotlinx.coroutines.flow.Flow

interface PomodoroRepository {
    fun getSessions(): Flow<List<PomodoroSession>>
    suspend fun saveSession(session: PomodoroSession)
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/domain/usecase/GetPomodoroSessionsUseCase.kt

```kotlin
package com.joseleandro.pomolume.domain.usecase

import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.repository.PomodoroRepository
import kotlinx.coroutines.flow.Flow

class GetPomodoroSessionsUseCase(
    private val repository: PomodoroRepository
) {
    operator fun invoke(): Flow<List<PomodoroSession>> = repository.getSessions()
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/domain/usecase/SavePomodoroSessionUseCase.kt

```kotlin
package com.joseleandro.pomolume.domain.usecase

import com.joseleandro.pomolume.domain.model.PomodoroSession
import com.joseleandro.pomolume.domain.repository.PomodoroRepository

class SavePomodoroSessionUseCase(
    private val repository: PomodoroRepository
) {
    suspend operator fun invoke(session: PomodoroSession) {
        repository.saveSession(session)
    }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/di/DatabaseModule.kt

```kotlin
package com.joseleandro.pomolume.di

import androidx.room.Room
import com.joseleandro.pomolume.data.local.AppDatabase
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidApplication(),
            AppDatabase::class.java,
            "pomolume.db"
        ).build()
    }

    single { get<AppDatabase>().pomodoroDao() }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/di/RepositoryModule.kt

```kotlin
package com.joseleandro.pomolume.di

import com.joseleandro.pomolume.data.repository.PomodoroRepositoryImpl
import com.joseleandro.pomolume.domain.repository.PomodoroRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<PomodoroRepository> { PomodoroRepositoryImpl(get()) }
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/di/UseCaseModule.kt

```kotlin
package com.joseleandro.pomolume.di

import com.joseleandro.pomolume.domain.usecase.GetPomodoroSessionsUseCase
import com.joseleandro.pomolume.domain.usecase.SavePomodoroSessionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCaseModule = module {
    factoryOf(::GetPomodoroSessionsUseCase)
    factoryOf(::SavePomodoroSessionUseCase)
}

```

## C:/Users/leandro/Desktop/projetos/pessoais/projetos/pomoluve/app/src/main/java/com/joseleandro/pomolume/di/ViewModelModule.kt

```kotlin
package com.joseleandro.pomolume.di

import com.joseleandro.pomolume.ui.navigation.NavigationViewModel
import com.joseleandro.pomolume.ui.stats.StatsViewModel
import com.joseleandro.pomolume.ui.timer.TimerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::NavigationViewModel)
    viewModelOf(::TimerViewModel)
    viewModelOf(::StatsViewModel)
}

```
