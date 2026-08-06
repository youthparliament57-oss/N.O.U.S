// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * NOUS — Migration from v1 (empty schema) to v2 (16 tables + FTS4 + triggers).
 *
 * Creates all 16 tables, indexes, FTS4 virtual tables, and synchronization
 * triggers (episodic_memories_fts + semantic_facts_fts).
 *
 * @see <a href="docs/strategy/module-3-strategy-v2.md">§11 Migration Framework</a>
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.tag("NOUS.Database").i("Starting migration from v1 to v2...")
        
        try {
            // ─── 1. Episodic memories ─────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS episodic_memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    summary TEXT,
                    timestamp INTEGER NOT NULL,
                    importance REAL NOT NULL DEFAULT 0.5,
                    initial_importance REAL NOT NULL DEFAULT 0.5,
                    last_accessed INTEGER NOT NULL,
                    last_reinforced INTEGER NOT NULL,
                    decay_constant_days REAL NOT NULL DEFAULT 7.0,
                    is_flashbulb INTEGER NOT NULL DEFAULT 0,
                    is_consolidated INTEGER NOT NULL DEFAULT 0,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    is_hot INTEGER NOT NULL DEFAULT 0,
                    emotional_valence REAL,
                    emotional_valence_pending INTEGER NOT NULL DEFAULT 0,
                    tags TEXT NOT NULL,
                    correlation_id TEXT,
                    session_id TEXT,
                    embedding_status TEXT NOT NULL DEFAULT 'pending',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodic_timestamp ON episodic_memories(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodic_importance ON episodic_memories(importance)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodic_session ON episodic_memories(session_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodic_hot ON episodic_memories(is_hot)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodic_deleted ON episodic_memories(is_deleted)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_episodic_emb_status ON episodic_memories(embedding_status)")

            // ─── 2. KG entities ───────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS kg_entities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    canonical_name TEXT NOT NULL,
                    aliases TEXT,
                    metadata TEXT,
                    confidence_alpha REAL NOT NULL DEFAULT 1.0,
                    confidence_beta REAL NOT NULL DEFAULT 1.0,
                    mention_count INTEGER NOT NULL DEFAULT 0,
                    last_mentioned INTEGER,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_kg_entities_type ON kg_entities(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_kg_entities_name ON kg_entities(canonical_name)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_entities_unique ON kg_entities(type, canonical_name)")

            // ─── 3. KG edges ──────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS kg_edges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    source_entity_id INTEGER NOT NULL,
                    target_entity_id INTEGER NOT NULL,
                    edge_type TEXT NOT NULL,
                    confidence_alpha REAL NOT NULL DEFAULT 1.0,
                    confidence_beta REAL NOT NULL DEFAULT 1.0,
                    valid_from INTEGER NOT NULL,
                    valid_to INTEGER,
                    superseded_by INTEGER,
                    source TEXT NOT NULL,
                    metadata TEXT,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (source_entity_id) REFERENCES kg_entities(id) ON DELETE CASCADE,
                    FOREIGN KEY (target_entity_id) REFERENCES kg_entities(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_kg_edges_source ON kg_edges(source_entity_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_kg_edges_target ON kg_edges(target_entity_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_kg_edges_type ON kg_edges(edge_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_kg_edges_superseded ON kg_edges(superseded_by)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edges_unique ON kg_edges(source_entity_id, target_entity_id, edge_type)")

            // ─── 4. Semantic facts ────────────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS semantic_facts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    subject_entity_id INTEGER,
                    predicate TEXT NOT NULL,
                    object_value TEXT NOT NULL,
                    object_entity_id INTEGER,
                    confidence_alpha REAL NOT NULL DEFAULT 1.0,
                    confidence_beta REAL NOT NULL DEFAULT 1.0,
                    source TEXT NOT NULL,
                    valid_from INTEGER NOT NULL,
                    valid_to INTEGER,
                    superseded_by INTEGER,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (subject_entity_id) REFERENCES kg_entities(id) ON DELETE SET NULL,
                    FOREIGN KEY (object_entity_id) REFERENCES kg_entities(id) ON DELETE SET NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_subject ON semantic_facts(subject_entity_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_predicate ON semantic_facts(predicate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_superseded ON semantic_facts(superseded_by)")

            // ─── 5. Memory embeddings (separate table — OOM prevention) ─
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS memory_embeddings (
                    memory_id INTEGER PRIMARY KEY NOT NULL,
                    embedding BLOB NOT NULL,
                    embedding_model_version TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (memory_id) REFERENCES episodic_memories(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_embeddings_version ON memory_embeddings(embedding_model_version)")

            // ─── 6-10. Remaining tables ───────────────────────────────
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS procedural_memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    skill_id TEXT NOT NULL,
                    intent_type TEXT NOT NULL,
                    procedure TEXT NOT NULL,
                    success_count INTEGER NOT NULL DEFAULT 0,
                    failure_count INTEGER NOT NULL DEFAULT 0,
                    last_used INTEGER,
                    confidence_alpha REAL NOT NULL DEFAULT 1.0,
                    confidence_beta REAL NOT NULL DEFAULT 1.0,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS prospective_memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    description TEXT NOT NULL,
                    trigger_type TEXT NOT NULL,
                    trigger_data TEXT NOT NULL,
                    is_recurring INTEGER NOT NULL DEFAULT 0,
                    recurrence_rule TEXT,
                    status TEXT NOT NULL DEFAULT 'pending',
                    created_at INTEGER NOT NULL,
                    fired_at INTEGER,
                    expires_at INTEGER
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    persona_id TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    turn_count INTEGER NOT NULL DEFAULT 0,
                    summary TEXT,
                    created_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_started ON conversation_sessions(started_at)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_turns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    session_id TEXT NOT NULL,
                    turn_number INTEGER NOT NULL,
                    user_text TEXT NOT NULL,
                    brain_response TEXT NOT NULL,
                    intent_label TEXT,
                    layer_label TEXT,
                    correlation_id TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES conversation_sessions(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_turns_session ON conversation_turns(session_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_turns_timestamp ON conversation_turns(timestamp)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_states (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    session_id TEXT NOT NULL,
                    correlation_id TEXT NOT NULL,
                    skill_id TEXT NOT NULL,
                    intent TEXT NOT NULL,
                    stage TEXT NOT NULL,
                    partial_output TEXT,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_states_unique ON conversation_states(session_id, correlation_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_states_expires ON conversation_states(expires_at)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_facts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    confidence_alpha REAL NOT NULL DEFAULT 1.0,
                    confidence_beta REAL NOT NULL DEFAULT 1.0,
                    source TEXT NOT NULL,
                    learned_at INTEGER NOT NULL,
                    last_confirmed INTEGER,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_user_facts_key ON user_facts(key)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_preferences (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    category TEXT NOT NULL,
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    weight REAL NOT NULL DEFAULT 0.5,
                    updated_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_prefs_unique ON user_preferences(category, key)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS skill_performance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    skill_id TEXT NOT NULL,
                    invoked_at INTEGER NOT NULL,
                    success INTEGER NOT NULL,
                    latency_ms INTEGER NOT NULL,
                    cost_usd REAL NOT NULL DEFAULT 0,
                    correlation_id TEXT
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_skill_perf ON skill_performance(skill_id, invoked_at)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS brain_traces (
                    correlation_id TEXT PRIMARY KEY NOT NULL,
                    session_id TEXT NOT NULL,
                    input_redacted TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    completed_at INTEGER NOT NULL,
                    total_duration_ms INTEGER NOT NULL,
                    layers_json TEXT NOT NULL,
                    final_status TEXT NOT NULL,
                    final_output TEXT,
                    final_error TEXT,
                    total_cost_usd REAL NOT NULL DEFAULT 0,
                    total_tokens INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_traces_session ON brain_traces(session_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_traces_created ON brain_traces(created_at)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS workflow_traces (
                    workflow_id TEXT PRIMARY KEY NOT NULL,
                    correlation_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    user_input TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER NOT NULL,
                    total_duration_ms INTEGER NOT NULL,
                    total_cost_usd REAL NOT NULL DEFAULT 0,
                    steps_json TEXT NOT NULL,
                    final_status TEXT NOT NULL,
                    final_answer TEXT,
                    failure_reason TEXT,
                    created_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_workflow_corr ON workflow_traces(correlation_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_workflow_created ON workflow_traces(created_at)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS llm_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    prompt_hash TEXT NOT NULL,
                    prompt_preview TEXT,
                    prompt_embedding BLOB NOT NULL,
                    response TEXT NOT NULL,
                    model_id TEXT NOT NULL,
                    embedding_model_version TEXT NOT NULL,
                    is_volatile INTEGER NOT NULL DEFAULT 0,
                    hit_count INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    last_accessed INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_cache_hash ON llm_cache(prompt_hash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_expires ON llm_cache(expires_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_model ON llm_cache(model_id)")

            // ─── FTS4 Virtual Tables + Triggers ──────────────────────
            
            // Episodic memories FTS
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS episodic_memories_fts USING fts4(
                    content, summary, tokens='simple', content='episodic_memories'
                )
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS episodic_memories_ai AFTER INSERT ON episodic_memories BEGIN
                    INSERT INTO episodic_memories_fts(docid, content, summary)
                    VALUES (new.id, new.content, new.summary);
                END
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS episodic_memories_ad AFTER DELETE ON episodic_memories BEGIN
                    INSERT INTO episodic_memories_fts(docid, content, summary)
                    VALUES (old.id, 'delete', 'delete');
                END
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS episodic_memories_au AFTER UPDATE ON episodic_memories BEGIN
                    INSERT INTO episodic_memories_fts(docid, content, summary)
                    VALUES (old.id, 'delete', 'delete');
                    INSERT INTO episodic_memories_fts(docid, content, summary)
                    VALUES (new.id, new.content, new.summary);
                END
            """)

            // Semantic facts FTS
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS semantic_facts_fts USING fts4(
                    object_value, tokens='simple', content='semantic_facts'
                )
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS semantic_facts_ai AFTER INSERT ON semantic_facts BEGIN
                    INSERT INTO semantic_facts_fts(docid, object_value)
                    VALUES (new.id, new.object_value);
                END
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS semantic_facts_ad AFTER DELETE ON semantic_facts BEGIN
                    INSERT INTO semantic_facts_fts(docid, object_value)
                    VALUES (old.id, 'delete');
                END
            """)
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS semantic_facts_au AFTER UPDATE ON semantic_facts BEGIN
                    INSERT INTO semantic_facts_fts(docid, object_value)
                    VALUES (old.id, 'delete');
                    INSERT INTO semantic_facts_fts(docid, object_value)
                    VALUES (new.id, new.object_value);
                END
            """)

            Timber.tag("NOUS.Database").i("Migration from v1 to v2 completed successfully")

        } catch (e: Exception) {
            Timber.tag("NOUS.Database").e(e, "Migration 1→2 failed - database may be corrupt")
            throw e
        }
    }
}

/**
 * NOUS — Migration from v2 to v3 (Module 10 — adds undo_actions table).
 *
 * Creates the `undo_actions` table for [com.roshan.persona.database.entity.UndoActionEntity],
 * used by Module 10's PersistentUndoStack to persist reversible system operations
 * across app restarts.
 *
 * @see <a href="docs/strategy/module-10-system-strategy-v2.md">§Persistent Undo Stack</a>
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.tag("NOUS.Database").i("Starting migration from v2 to v3...")
        
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS undo_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    operation_type TEXT NOT NULL,
                    operation_json TEXT NOT NULL,
                    pre_state_json TEXT,
                    handler_id TEXT NOT NULL,
                    timestamp_ms INTEGER NOT NULL,
                    description TEXT NOT NULL
                )
            """)
            
            // Index on timestamp for "recent actions" queries.
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS index_undo_actions_timestamp_ms
                ON undo_actions(timestamp_ms)
            """)
            
            Timber.tag("NOUS.Database").i("Migration from v2 to v3 completed successfully")
            
        } catch (e: Exception) {
            Timber.tag("NOUS.Database").e(e, "Migration 2→3 failed - undo actions table may not be available")
            throw e
        }
    }
}
