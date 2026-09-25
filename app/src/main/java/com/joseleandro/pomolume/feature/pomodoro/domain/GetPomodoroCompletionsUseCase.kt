package com.joseleandro.pomolume.feature.pomodoro.domain

class GetPomodoroCompletionsUseCase(private val repository: PomodoroRepository) {
    operator fun invoke() = repository.completions
}
