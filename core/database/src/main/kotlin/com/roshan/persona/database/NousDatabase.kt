// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.roshan.persona.database.dao.ConversationStateDao
import com.roshan.persona.database.dao.EpisodicMemoryDao
import com.roshan.persona.database.dao.KgDao
import com.roshan.persona.database.dao.LlmCacheDao
import com.roshan.persona.database.dao.MemoryEmbeddingDao
import com.roshan.persona.database.dao.NativeCrashLogDao
import com.roshan.persona.database.dao.SemanticFactDao
import com.roshan.persona.database.dao.UndoActionDao
import com.roshan.persona.database.dao.UserFactDao
import com.roshan.persona.database.entity.*

@Database(
    entities = [
        EpisodicMemoryEntity::class,
        SemanticFactEntity::class,
        ProceduralMemoryEntity::class,
        ProspectiveMemoryEntity::class,
        KgEntityEntity::class,
        KgEdgeEntity::class,
        ConversationSessionEntity::class,
        ConversationTurnEntity::class,
        ConversationStateEntity::class,
        UserFactEntity::class,
        UserPreferenceEntity::class,
        SkillPerformanceEntity::class,
        BrainTraceEntity::class,
        WorkflowTraceEntity::class,
        LlmCacheEntity::class,
        MemoryEmbeddingEntity::class,
        UndoActionEntity::class,
        NativeCrashLogEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class NousDatabase : RoomDatabase() {
    abstract fun episodicMemoryDao(): EpisodicMemoryDao
    abstract fun semanticFactDao(): SemanticFactDao
    abstract fun kgDao(): KgDao
    abstract fun userFactDao(): UserFactDao
    abstract fun memoryEmbeddingDao(): MemoryEmbeddingDao
    abstract fun llmCacheDao(): LlmCacheDao
    abstract fun conversationStateDao(): ConversationStateDao
    abstract fun undoActionDao(): UndoActionDao
    abstract fun nativeCrashLogDao(): NativeCrashLogDao

    companion object {
        const val DATABASE_NAME = "nous.db"
        const val DATABASE_VERSION = 3
    }
}
