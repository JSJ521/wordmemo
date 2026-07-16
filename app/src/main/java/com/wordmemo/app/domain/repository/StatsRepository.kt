package com.wordmemo.app.domain.repository

import com.wordmemo.app.domain.model.Stats
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeStats(): Flow<Stats>
    suspend fun refreshStats(): Stats
}
