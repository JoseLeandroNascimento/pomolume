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
