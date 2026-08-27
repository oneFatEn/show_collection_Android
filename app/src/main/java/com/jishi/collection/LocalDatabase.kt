package com.jishi.collection

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class LocalDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                rednote_id TEXT PRIMARY KEY,
                note_url TEXT NOT NULL,
                status TEXT NOT NULL,
                first_seen_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL,
                removed_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_notes_status_last_seen ON notes(status, last_seen_at)")

        db.execSQL(
            """
            CREATE TABLE note_metadata_cache (
                rednote_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                ai_keywords TEXT NOT NULL DEFAULT '',
                author_name TEXT NOT NULL,
                cover_url TEXT NOT NULL,
                cached_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_metadata_expires ON note_metadata_cache(expires_at)")

        db.execSQL(
            """
            CREATE TABLE categories (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                coarse_id TEXT NOT NULL DEFAULT '',
                level INTEGER NOT NULL DEFAULT 1 CHECK(level IN (1, 2)),
                parent_id TEXT,
                system_key TEXT NOT NULL DEFAULT '',
                fission_dimension TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'system',
                status TEXT NOT NULL DEFAULT 'active',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                CHECK((level = 1 AND parent_id IS NULL) OR (level = 2 AND parent_id IS NOT NULL))
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE note_categories (
                note_id TEXT NOT NULL,
                category_id TEXT NOT NULL,
                source TEXT NOT NULL,
                confidence REAL NOT NULL,
                locked_by_user INTEGER NOT NULL,
                processing_status TEXT NOT NULL DEFAULT 'classified',
                classification_version TEXT NOT NULL DEFAULT 'legacy',
                failure_reason TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(note_id, category_id),
                UNIQUE(note_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_note_categories_category ON note_categories(category_id)")
        db.execSQL("CREATE INDEX idx_note_categories_note ON note_categories(note_id)")

        db.execSQL(
            """
            CREATE TABLE pending_category_suggestions (
                id TEXT PRIMARY KEY,
                rednote_ids TEXT NOT NULL,
                suggested_name TEXT NOT NULL,
                reason TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_suggestions_status ON pending_category_suggestions(status)")

        db.execSQL(
            """
            CREATE TABLE sync_state (
                account_user_id TEXT PRIMARY KEY,
                cursor TEXT NOT NULL,
                has_more INTEGER NOT NULL,
                status TEXT NOT NULL,
                last_error TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createAiClassificationQueue(db)
        createConstrainedClassificationTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_state (
                    account_user_id TEXT PRIMARY KEY,
                    cursor TEXT NOT NULL,
                    has_more INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    last_error TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            val now = System.currentTimeMillis()
            val count = db.rawQuery(
                "SELECT COUNT(*) FROM categories WHERE id = ?",
                arrayOf(CATEGORY_INVALID),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
            if (count == 0L) {
                db.insert(
                    "categories",
                    ContentValues().apply {
                        put("id", CATEGORY_INVALID)
                        put("name", "失效收藏")
                        put("type", "system")
                        put("sort_order", 10_000)
                        put("created_at", now)
                        put("updated_at", now)
                    },
                )
            }
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE note_metadata_cache RENAME COLUMN \"desc\" TO description")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE note_metadata_cache ADD COLUMN ai_keywords TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 6) {
            createAiClassificationQueue(db)
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE categories ADD COLUMN coarse_id TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE categories ADD COLUMN level INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE categories ADD COLUMN parent_id TEXT")
            db.execSQL("ALTER TABLE categories ADD COLUMN system_key TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN fission_dimension TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN source TEXT NOT NULL DEFAULT 'system'")
            db.execSQL("ALTER TABLE categories ADD COLUMN status TEXT NOT NULL DEFAULT 'active'")
            db.execSQL("ALTER TABLE note_categories ADD COLUMN processing_status TEXT NOT NULL DEFAULT 'classified'")
            db.execSQL("ALTER TABLE note_categories ADD COLUMN classification_version TEXT NOT NULL DEFAULT 'legacy'")
            db.execSQL("ALTER TABLE note_categories ADD COLUMN failure_reason TEXT NOT NULL DEFAULT ''")
            createConstrainedClassificationTables(db)
            migrateUniqueNoteAssignments(db)
        }
    }

    fun ensureDefaults() {
        writableDatabase.transaction {
            val now = System.currentTimeMillis()
            val defaults = ClassificationTaxonomy.primaryCategories.mapIndexed { index, def ->
                Category(
                    id = def.id,
                    name = def.name,
                    type = "primary",
                    sortOrder = (index + 1) * 10,
                    coarseId = def.id,
                    level = 1,
                    systemKey = def.id,
                    source = "system",
                )
            } + listOf(
                Category(CATEGORY_PENDING, "待整理", "system", 999),
                Category(CATEGORY_INVALID, "失效收藏", "system", 10_000),
            )
            defaults.forEach { category ->
                if (longFor("SELECT COUNT(*) FROM categories WHERE id = ?", category.id) == 0L) {
                    insert("categories", categoryValues(category, now))
                }
            }
            migrateLegacyAssignments(this)
        }
    }

    fun syncNotes(
        notes: List<SyncedNote>,
        pageState: SyncPageState? = null,
    ): SyncResult {
        ensureDefaults()
        if (notes.isEmpty()) {
            pageState?.let { state ->
                writableDatabase.transaction { recordSyncPage(state) }
            }
            return SyncResult(0, 0, accountUserId = pageState?.accountUserId)
        }
        val now = System.currentTimeMillis()
        val incomingIds = notes.map { it.rednoteId }.toSet()
        var incomingChangedIds = emptySet<String>()
        var inserted = 0
        val pendingGroups = 0

        writableDatabase.transaction {
            pageState?.let { recordSyncPage(it) }
            val changedIds = mutableSetOf<String>()
            notes.forEach { note ->
                val existingStatus = stringFor("SELECT status FROM notes WHERE rednote_id = ?", note.rednoteId)
                val isNew = existingStatus == null
                val wasInvalid = existingStatus == NoteStatus.REMOVED.name || existingStatus == NoteStatus.UNAVAILABLE.name
                val noteValues = ContentValues().apply {
                    put("rednote_id", note.rednoteId)
                    put("note_url", note.noteUrl)
                    put("status", NoteStatus.ACTIVE.name)
                    put("last_seen_at", now)
                    if (isNew) put("first_seen_at", now)
                    putNull("removed_at")
                }
                if (isNew) {
                    insert("notes", noteValues)
                    inserted += 1
                    changedIds += note.rednoteId
                } else {
                    update("notes", noteValues, "rednote_id = ?", arrayOf(note.rednoteId))
                    if (wasInvalid) {
                        inserted += 1
                        changedIds += note.rednoteId
                    }
                }

                replace("note_metadata_cache", metadataValues(note, now))

                if (isNew || wasInvalid) {
                    if (wasInvalid) {
                        delete("note_categories", "note_id = ? AND category_id = ?", arrayOf(note.rednoteId, CATEGORY_INVALID))
                    }
                    // 同步只负责入库，不做分类：新笔记先进待整理，等分类按钮或增量匹配处理
                    replace("note_categories", noteCategoryValues(note.rednoteId, CATEGORY_PENDING, "sync", 0.1, false))
                }
            }
            incomingChangedIds = changedIds
        }
        return SyncResult(inserted, pendingGroups, incomingIds, incomingChangedIds, pageState?.accountUserId)
    }

    fun categorySummaries(): List<CategorySummary> {
        ensureDefaults()
        val summariesByCategory = linkedMapOf<String, CategorySummaryDraft>()
        readableDatabase.rawQuery(
            """
            SELECT primary_category.id, primary_category.name, m.cover_url
            FROM note_categories nc
            JOIN categories assigned_category ON assigned_category.id = nc.category_id
            JOIN categories primary_category ON primary_category.id = CASE
                WHEN assigned_category.level = 2 THEN assigned_category.parent_id
                ELSE assigned_category.id
            END
            JOIN notes n ON n.rednote_id = nc.note_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = nc.note_id
            WHERE n.status = ? AND primary_category.id != ?
              AND primary_category.status IN ('active', 'migration_review')
            ORDER BY primary_category.sort_order ASC, primary_category.name ASC, n.last_seen_at DESC
            """.trimIndent(),
            arrayOf(NoteStatus.ACTIVE.name, CATEGORY_INVALID),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val categoryId = cursor.getString(0)
                val draft = summariesByCategory.getOrPut(categoryId) {
                    CategorySummaryDraft(
                        id = categoryId,
                        name = cursor.getString(1),
                    )
                }
                draft.count += 1
                val coverUrl = cursor.getString(2).orEmpty()
                if (coverUrl.isNotBlank() && draft.previews.size < 3) {
                    draft.previews += coverUrl
                }
            }
        }
        val summaries = summariesByCategory.values.map { draft ->
            CategorySummary(draft.id, draft.name, draft.count, draft.previews, isInvalid = false)
        }.toMutableList()
        invalidCategorySummary()?.let { summaries += it }
        return summaries
    }

    private fun invalidCategorySummary(): CategorySummary? {
        val previews = mutableListOf<String>()
        var count = 0
        readableDatabase.rawQuery(
            """
            SELECT c.name, m.cover_url
            FROM note_categories nc
            JOIN categories c ON c.id = nc.category_id
            JOIN notes n ON n.rednote_id = nc.note_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = nc.note_id
            WHERE nc.category_id = ? AND n.status != ?
            ORDER BY n.removed_at DESC, n.last_seen_at DESC
            """.trimIndent(),
            arrayOf(CATEGORY_INVALID, NoteStatus.ACTIVE.name),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                count += 1
                val coverUrl = cursor.getString(1).orEmpty()
                if (coverUrl.isNotBlank() && previews.size < 3) {
                    previews += coverUrl
                }
            }
        }
        if (count == 0) return null
        val name = stringFor("SELECT name FROM categories WHERE id = ?", CATEGORY_INVALID) ?: "失效收藏"
        return CategorySummary(CATEGORY_INVALID, name, count, previews, isInvalid = true)
    }

    fun reconcileCompletedSync(accountUserId: String, syncedIds: Set<String>): Int {
        ensureDefaults()
        val now = System.currentTimeMillis()
        var removed = 0
        writableDatabase.transaction {
            markSyncState(accountUserId, "", false, SyncStatus.COMPLETED, "")
            removed = markMissingAsRemoved(syncedIds, now)
        }
        return removed
    }

    fun failSync(accountUserId: String, error: String) {
        writableDatabase.transaction {
            markSyncState(accountUserId.ifBlank { DEFAULT_ACCOUNT_ID }, "", false, SyncStatus.FAILED, error.sanitizedError())
        }
    }

    fun notesForCategory(categoryId: String, limit: Int = 100, offset: Int = 0): List<NoteCard> {
        val statusFilter = if (categoryId == CATEGORY_INVALID) "n.status != ?" else "n.status = ?"
        val statusArg = if (categoryId == CATEGORY_INVALID) NoteStatus.ACTIVE.name else NoteStatus.ACTIVE.name
        return readableDatabase.rawQuery(
            """
            SELECT n.rednote_id, nc.category_id, m.title, m.description, m.author_name, m.cover_url, n.note_url
            FROM note_categories nc
            JOIN notes n ON n.rednote_id = nc.note_id
            JOIN categories c ON c.id = nc.category_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE (nc.category_id = ? OR c.parent_id = ?) AND $statusFilter
            ORDER BY n.removed_at DESC, n.last_seen_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(categoryId, categoryId, statusArg, limit.toString(), offset.toString()),
        ).useEach { cursor ->
            NoteCard(
                rednoteId = cursor.getString(0),
                categoryId = cursor.getString(1),
                title = cursor.getString(2).orEmpty().ifBlank { "未缓存标题" },
                desc = cursor.getString(3).orEmpty(),
                authorName = cursor.getString(4).orEmpty(),
                coverUrl = cursor.getString(5).orEmpty(),
                noteUrl = cursor.getString(6).orEmpty(),
            )
        }
    }

    fun searchableNotes(limit: Int = 500): List<SearchableNote> {
        return readableDatabase.rawQuery(
            """
            SELECT n.rednote_id, primary_category.id, primary_category.name,
                   m.title, m.description, m.cover_url, n.note_url
            FROM notes n
            JOIN note_categories nc ON nc.note_id = n.rednote_id
            JOIN categories assigned_category ON assigned_category.id = nc.category_id
            JOIN categories primary_category ON primary_category.id = CASE
                WHEN assigned_category.level = 2 THEN assigned_category.parent_id
                ELSE assigned_category.id
            END
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE n.status = ? AND primary_category.id != ?
            ORDER BY n.last_seen_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(NoteStatus.ACTIVE.name, CATEGORY_INVALID, limit.toString()),
        ).useEach { cursor ->
            SearchableNote(
                rednoteId = cursor.getString(0),
                categoryId = cursor.getString(1).orEmpty(),
                categoryName = cursor.getString(2).orEmpty(),
                title = cursor.getString(3).orEmpty().ifBlank { "未缓存标题" },
                desc = cursor.getString(4).orEmpty(),
                coverUrl = cursor.getString(5).orEmpty(),
                noteUrl = cursor.getString(6).orEmpty(),
            )
        }
    }

    fun renameCategory(categoryId: String, name: String) {
        if (categoryId == CATEGORY_INVALID) return
        if (readableDatabase.longFor("SELECT COUNT(*) FROM categories WHERE id = ? AND level = 2", categoryId) == 0L) return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        writableDatabase.update(
            "categories",
            ContentValues().apply {
                put("name", trimmed)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(categoryId),
        )
    }

    fun pendingSuggestions(): List<PendingCategorySuggestion> {
        return readableDatabase.rawQuery(
            """
            SELECT id, rednote_ids, suggested_name, reason
            FROM pending_category_suggestions
            WHERE status = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            arrayOf("pending"),
        ).useEach { cursor ->
            PendingCategorySuggestion(
                id = cursor.getString(0),
                rednoteIds = cursor.getString(1).split(",").filter { it.isNotBlank() },
                suggestedName = cursor.getString(2),
                reason = cursor.getString(3),
            )
        }
    }

    fun acceptSuggestion(suggestionId: String, categoryName: String) {
        writableDatabase.transaction {
            findPendingSuggestion(suggestionId) ?: return@transaction
            markSuggestion(suggestionId, "dismissed")
        }
    }

    fun dismissSuggestion(suggestionId: String) {
        writableDatabase.transaction {
            markSuggestion(suggestionId, "dismissed")
        }
    }

    fun clearMetadataCache() {
        writableDatabase.delete("note_metadata_cache", null, null)
    }

    fun clearInvalidNotes(): Int {
        var deleted = 0
        writableDatabase.transaction {
            val ids = rawQuery(
                """
                SELECT n.rednote_id
                FROM notes n
                JOIN note_categories nc ON nc.note_id = n.rednote_id
                WHERE nc.category_id = ? AND n.status != ?
                """.trimIndent(),
                arrayOf(CATEGORY_INVALID, NoteStatus.ACTIVE.name),
            ).useEach { it.getString(0) }
            removeIdsFromPendingSuggestions(ids.toSet())
            ids.forEach { rednoteId ->
                delete("note_categories", "note_id = ?", arrayOf(rednoteId))
                delete("note_metadata_cache", "rednote_id = ?", arrayOf(rednoteId))
                delete("notes", "rednote_id = ?", arrayOf(rednoteId))
                deleted += 1
            }
        }
        return deleted
    }

    fun clearAllLocalData() {
        writableDatabase.transaction {
            delete("sync_state", null, null)
            delete("ai_classification_queue", null, null)
            delete("creation_suppressions", null, null)
            delete("classification_previews", null, null)
            delete("classification_cache", null, null)
            delete("note_tags", null, null)
            delete("tags", null, null)
            delete("pending_category_suggestions", null, null)
            delete("note_categories", null, null)
            delete("categories", null, null)
            delete("note_metadata_cache", null, null)
            delete("notes", null, null)
        }
        ensureDefaults()
    }

    fun rerunRuleClassification(noteIds: Set<String>? = null): Int {
        ensureDefaults()
        val notes = activeSyncedNotes(noteIds)
        if (notes.isEmpty()) return 0
        var classified = 0
        writableDatabase.transaction {
            notes.forEach { note ->
                val locked = longFor(
                    "SELECT COUNT(*) FROM note_categories WHERE note_id = ? AND locked_by_user = 1",
                    note.rednoteId,
                ) > 0L
                if (!locked) {
                    delete("note_categories", "note_id = ?", arrayOf(note.rednoteId))
                    removeIdsFromPendingSuggestions(setOf(note.rednoteId))
                    val match = classify(note)
                    if (match.categoryId != null) {
                        replace("note_categories", noteCategoryValues(note.rednoteId, match.categoryId, "rule", match.confidence, false))
                    } else {
                        replace("note_categories", noteCategoryValues(note.rednoteId, CATEGORY_PENDING, "rule", 0.1, false))
                    }
                    classified += 1
                }
            }
        }
        return classified
    }

    /** 待粗分类的笔记：没有分类或还在“待整理”里的活跃笔记。 */
    fun aiNotesForCoarseClassification(noteIds: Set<String>? = null): List<AiNote> {
        ensureDefaults()
        val args = mutableListOf(NoteStatus.ACTIVE.name, CATEGORY_PENDING)
        val idFilter = if (noteIds.isNullOrEmpty()) {
            ""
        } else {
            args += noteIds
            " AND n.rednote_id IN (${noteIds.joinToString(",") { "?" }})"
        }
        return readableDatabase.rawQuery(
            """
            SELECT n.rednote_id, m.title, m.description, m.ai_keywords, c.id, c.name, m.author_name
            FROM notes n
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            LEFT JOIN note_categories nc ON nc.note_id = n.rednote_id
            LEFT JOIN categories c ON c.id = nc.category_id
            WHERE n.status = ? AND (c.id IS NULL OR c.id = ?)$idFilter
            ORDER BY n.last_seen_at DESC
            """.trimIndent(),
            args.toTypedArray(),
        ).useEach { cursor ->
            AiNote(
                rednoteId = cursor.getString(0),
                title = cursor.getString(1).orEmpty().ifBlank { "未命名收藏" },
                desc = cursor.getString(2).orEmpty(),
                aiKeywords = cursor.getString(3).orEmpty(),
                categoryId = cursor.getString(4),
                categoryName = cursor.getString(5),
                authorName = cursor.getString(6).orEmpty(),
            )
        }.distinctBy { it.rednoteId }
    }

    fun ensurePrimaryCategories(defs: List<PrimaryCategoryDef>) {
        writableDatabase.transaction {
            val now = System.currentTimeMillis()
            defs.forEachIndexed { index, def ->
                if (longFor("SELECT COUNT(*) FROM categories WHERE id = ?", def.id) == 0L) {
                    insert(
                        "categories",
                        categoryValues(
                            Category(
                                id = def.id,
                                name = def.name,
                                type = "primary",
                                sortOrder = (index + 1) * 10,
                                coarseId = def.id,
                                level = 1,
                                systemKey = def.id,
                            ),
                            now,
                        ),
                    )
                }
            }
        }
    }

    fun aiNotesInCategory(categoryId: String): List<AiNote> {
        return readableDatabase.rawQuery(
            """
            SELECT n.rednote_id, m.title, m.description, m.ai_keywords, c.id, c.name, m.author_name
            FROM note_categories nc
            JOIN notes n ON n.rednote_id = nc.note_id
            JOIN categories c ON c.id = nc.category_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE n.status = ? AND nc.category_id = ?
            ORDER BY n.last_seen_at DESC
            """.trimIndent(),
            arrayOf(NoteStatus.ACTIVE.name, categoryId),
        ).useEach { cursor ->
            AiNote(
                rednoteId = cursor.getString(0),
                title = cursor.getString(1).orEmpty().ifBlank { "未命名收藏" },
                desc = cursor.getString(2).orEmpty(),
                aiKeywords = cursor.getString(3).orEmpty(),
                categoryId = cursor.getString(4),
                categoryName = cursor.getString(5),
                authorName = cursor.getString(6).orEmpty(),
            )
        }
    }

    fun aiCategoryProfiles(): List<AiCategoryProfile> {
        ensureDefaults()
        return readableDatabase.rawQuery(
            """
            SELECT c.id, c.name, c.coarse_id, COUNT(n.rednote_id), GROUP_CONCAT(
                CASE
                    WHEN m.ai_keywords IS NOT NULL AND m.ai_keywords != '' THEN m.ai_keywords
                    ELSE m.title || ' ' || m.description
                END,
                '；'
            )
            FROM categories c
            JOIN note_categories nc ON nc.category_id = c.id
            JOIN notes n ON n.rednote_id = nc.note_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE n.status = ? AND c.id NOT IN (?, ?)
            GROUP BY c.id, c.name, c.coarse_id
            ORDER BY c.sort_order ASC, c.name ASC
            """.trimIndent(),
            arrayOf(NoteStatus.ACTIVE.name, CATEGORY_PENDING, CATEGORY_INVALID),
        ).useEach { cursor ->
            AiCategoryProfile(
                id = cursor.getString(0),
                name = cursor.getString(1),
                coarseId = cursor.getString(2).orEmpty(),
                count = cursor.getInt(3),
                sampleText = cursor.getString(4).orEmpty().take(1200),
            )
        }
    }

    fun updateAiKeywords(keywordsByNoteId: Map<String, String>): Int {
        if (keywordsByNoteId.isEmpty()) return 0
        var updated = 0
        writableDatabase.transaction {
            keywordsByNoteId.forEach { (noteId, keywords) ->
                val affected = update(
                    "note_metadata_cache",
                    ContentValues().apply {
                        put("ai_keywords", keywords.trim().take(240))
                        put("cached_at", System.currentTimeMillis())
                    },
                    "rednote_id = ?",
                    arrayOf(noteId),
                )
                if (affected > 0) updated += 1
            }
        }
        return updated
    }

    fun applyClassifications(classifications: List<ValidatedClassification>): Int {
        if (classifications.isEmpty()) return 0
        var moved = 0
        writableDatabase.transaction {
            classifications.forEach { classification ->
                val locked = longFor(
                    "SELECT COUNT(*) FROM note_categories WHERE note_id = ? AND locked_by_user = 1",
                    classification.noteId,
                ) > 0L
                if (!locked) {
                    val secondaryId = classification.existingSecondaryCategoryId?.takeIf { secondaryId ->
                        longFor(
                            "SELECT COUNT(*) FROM categories WHERE id = ? AND level = 2 AND parent_id = ? AND status = 'active'",
                            secondaryId,
                            classification.primaryCategoryId,
                        ) > 0L
                    }
                    val targetCategoryId = secondaryId ?: classification.primaryCategoryId
                    delete("note_categories", "note_id = ?", arrayOf(classification.noteId))
                    replace(
                        "note_categories",
                        noteCategoryValues(
                            classification.noteId,
                            targetCategoryId,
                            "ai",
                            classification.primaryConfidence,
                            false,
                        ),
                    )
                    replaceNoteTags(classification.noteId, classification.tags, "ai")
                    removeIdsFromPendingSuggestions(setOf(classification.noteId))
                    moved += 1
                }
            }
        }
        return moved
    }

    fun markPendingClassification(noteIds: Set<String>, failureReason: String) {
        if (noteIds.isEmpty()) return
        writableDatabase.transaction {
            noteIds.forEach { noteId ->
                update(
                    "note_categories",
                    ContentValues().apply {
                        put("processing_status", "pending")
                        put("classification_version", CLASSIFICATION_RULE_VERSION)
                        put("failure_reason", failureReason.take(80))
                    },
                    "note_id = ? AND category_id = ? AND locked_by_user = 0",
                    arrayOf(noteId, CATEGORY_PENDING),
                )
            }
        }
    }

    fun classificationCache(inputFingerprint: String, ruleVersion: String, modelVersion: String): String? {
        return readableDatabase.rawQuery(
            """
            SELECT structured_result
            FROM classification_cache
            WHERE input_fingerprint = ? AND rule_version = ? AND model_version = ? AND status = 'valid'
            """.trimIndent(),
            arrayOf(inputFingerprint, ruleVersion, modelVersion),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    fun storeClassificationCache(
        inputFingerprint: String,
        ruleVersion: String,
        modelVersion: String,
        structuredResult: String,
        status: String,
    ) {
        writableDatabase.replace(
            "classification_cache",
            ContentValues().apply {
                put("input_fingerprint", inputFingerprint)
                put("rule_version", ruleVersion)
                put("model_version", modelVersion)
                put("structured_result", structuredResult)
                put("status", status)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun secondaryCategories(parentId: String): List<Category> {
        return readableDatabase.rawQuery(
            """
            SELECT id, name, type, sort_order, coarse_id, level, parent_id, system_key,
                   fission_dimension, source, status
            FROM categories
            WHERE parent_id = ? AND level = 2 AND status = 'active'
            ORDER BY sort_order, name
            """.trimIndent(),
            arrayOf(parentId),
        ).useEach { cursor ->
            Category(
                id = cursor.getString(0),
                name = cursor.getString(1),
                type = cursor.getString(2),
                sortOrder = cursor.getInt(3),
                coarseId = cursor.getString(4),
                level = cursor.getInt(5),
                parentId = cursor.getString(6),
                systemKey = cursor.getString(7),
                fissionDimension = cursor.getString(8),
                source = cursor.getString(9),
                status = cursor.getString(10),
            )
        }
    }

    fun activeNoteCountInPrimary(primaryId: String): Int {
        return readableDatabase.longFor(
            """
            SELECT COUNT(*)
            FROM note_categories nc
            JOIN categories c ON c.id = nc.category_id
            JOIN notes n ON n.rednote_id = nc.note_id
            WHERE n.status = ? AND (c.id = ? OR c.parent_id = ?)
            """.trimIndent(),
            NoteStatus.ACTIVE.name,
            primaryId,
            primaryId,
        ).toInt()
    }

    /** 创建二级分类但永久保留固定一级；只迁移一级下未锁定的笔记。 */
    fun createSecondaryCategories(parentId: String, candidates: List<SecondaryCategoryCandidate>): Int {
        if (parentId !in ClassificationTaxonomy.byId || candidates.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var created = 0
        writableDatabase.transaction {
            val existingCount = longFor(
                "SELECT COUNT(*) FROM categories WHERE parent_id = ? AND level = 2 AND status = 'active'",
                parentId,
            ).toInt()
            val remainingSlots = (ConstrainedClassificationPolicy.MAX_SECONDARY_CATEGORIES - existingCount).coerceAtLeast(0)
            val movedIds = mutableSetOf<String>()
            candidates.take(remainingSlots).forEach { candidate ->
                val movableIds = candidate.noteIds.distinct().filter { noteId ->
                    noteId !in movedIds && longFor(
                        "SELECT COUNT(*) FROM note_categories WHERE note_id = ? AND category_id = ? AND locked_by_user = 0",
                        noteId,
                        parentId,
                    ) > 0L
                }
                if (movableIds.isEmpty()) return@forEach
                val newCategoryId = "cat_${UUID.randomUUID()}"
                insert(
                    "categories",
                    categoryValues(
                        Category(
                            id = newCategoryId,
                            name = candidate.name.trim().take(24),
                            type = "secondary",
                            sortOrder = 100 + existingCount + created,
                            coarseId = parentId,
                            level = 2,
                            parentId = parentId,
                            fissionDimension = candidate.dimension,
                            source = "ai",
                        ),
                        now,
                    ),
                )
                created += 1
                movableIds.forEach { noteId ->
                    delete("note_categories", "note_id = ?", arrayOf(noteId))
                    replace("note_categories", noteCategoryValues(noteId, newCategoryId, "ai_split", candidate.confidence, false))
                    movedIds += noteId
                }
            }
        }
        return created
    }

    private fun SQLiteDatabase.markMissingAsRemoved(incomingIds: Set<String>, now: Long): Int {
        val activeIds = rawQuery(
            "SELECT rednote_id FROM notes WHERE status = ?",
            arrayOf(NoteStatus.ACTIVE.name),
        ).useEach { it.getString(0) }
        val missingIds = activeIds.filterNot { it in incomingIds }
        missingIds.forEach { missingId ->
            val values = ContentValues().apply {
                put("status", NoteStatus.REMOVED.name)
                put("removed_at", now)
            }
            update("notes", values, "rednote_id = ?", arrayOf(missingId))
            delete("note_categories", "note_id = ?", arrayOf(missingId))
            removeIdsFromPendingSuggestions(setOf(missingId))
            replace("note_categories", noteCategoryValues(missingId, CATEGORY_INVALID, "system", 1.0, true))
        }
        return missingIds.size
    }

    private fun activeSyncedNotes(noteIds: Set<String>?): List<SyncedNote> {
        val args = mutableListOf(NoteStatus.ACTIVE.name, CATEGORY_PENDING)
        val idFilter = if (noteIds.isNullOrEmpty()) {
            ""
        } else {
            args += noteIds
            " AND n.rednote_id IN (${noteIds.joinToString(",") { "?" }})"
        }
        return readableDatabase.rawQuery(
            """
            SELECT n.rednote_id, m.title, m.description, m.author_name, m.cover_url, n.note_url
            FROM notes n
            LEFT JOIN note_categories nc ON nc.note_id = n.rednote_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE n.status = ? AND (nc.category_id IS NULL OR nc.category_id = ?)$idFilter
            ORDER BY n.last_seen_at DESC
            """.trimIndent(),
            args.toTypedArray(),
        ).useEach { cursor ->
            SyncedNote(
                rednoteId = cursor.getString(0),
                title = cursor.getString(1).orEmpty().ifBlank { "未命名收藏" },
                desc = cursor.getString(2).orEmpty(),
                authorName = cursor.getString(3).orEmpty(),
                coverUrl = cursor.getString(4).orEmpty(),
                noteUrl = cursor.getString(5).orEmpty(),
            )
        }
    }

    private fun SQLiteDatabase.markSuggestion(id: String, status: String) {
        update(
            "pending_category_suggestions",
            ContentValues().apply { put("status", status) },
            "id = ?",
            arrayOf(id),
        )
    }

    private fun SQLiteDatabase.removeIdsFromPendingSuggestions(rednoteIds: Set<String>) {
        if (rednoteIds.isEmpty()) return
        rawQuery(
            """
            SELECT id, rednote_ids
            FROM pending_category_suggestions
            WHERE status = ?
            """.trimIndent(),
            arrayOf("pending"),
        ).useEach { cursor ->
            cursor.getString(0) to cursor.getString(1)
        }.forEach { (suggestionId, rawIds) ->
            val remainingIds = rawIds.split(",").filter { it.isNotBlank() && it !in rednoteIds }
            if (remainingIds.isEmpty()) {
                delete("pending_category_suggestions", "id = ?", arrayOf(suggestionId))
            } else {
                update(
                    "pending_category_suggestions",
                    ContentValues().apply { put("rednote_ids", remainingIds.joinToString(",")) },
                    "id = ?",
                    arrayOf(suggestionId),
                )
            }
        }
    }

    private fun SQLiteDatabase.findPendingSuggestion(id: String): PendingCategorySuggestion? {
        return rawQuery(
            """
            SELECT id, rednote_ids, suggested_name, reason
            FROM pending_category_suggestions
            WHERE id = ? AND status = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(id, "pending"),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                PendingCategorySuggestion(
                    id = cursor.getString(0),
                    rednoteIds = cursor.getString(1).split(",").filter { it.isNotBlank() },
                    suggestedName = cursor.getString(2),
                    reason = cursor.getString(3),
                )
            }
        }
    }

    private fun SQLiteDatabase.longFor(sql: String, vararg args: String): Long {
        return rawQuery(sql, args.toList().toTypedArray()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun stringFor(sql: String, vararg args: String): String? {
        return readableDatabase.rawQuery(sql, args.toList().toTypedArray()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun SQLiteDatabase.recordSyncPage(pageState: SyncPageState) {
        markSyncState(
            accountUserId = pageState.accountUserId.ifBlank { DEFAULT_ACCOUNT_ID },
            cursor = pageState.cursor,
            hasMore = pageState.hasMore,
            status = SyncStatus.RUNNING,
            lastError = "",
        )
    }

    private fun SQLiteDatabase.markSyncState(
        accountUserId: String,
        cursor: String,
        hasMore: Boolean,
        status: SyncStatus,
        lastError: String,
    ) {
        replace(
            "sync_state",
            ContentValues().apply {
                put("account_user_id", accountUserId.ifBlank { DEFAULT_ACCOUNT_ID })
                put("cursor", cursor)
                put("has_more", if (hasMore) 1 else 0)
                put("status", status.name)
                put("last_error", lastError.sanitizedError())
                put("updated_at", System.currentTimeMillis())
            },
        )
    }

    private fun classify(note: SyncedNote): Classification {
        val text = "${note.title} ${note.desc}".lowercase()
        val rules = listOf(
            Triple("food", "美食", listOf("美食", "餐厅", "咖啡", "蛋糕", "做饭", "食谱", "火锅", "烘焙")),
            Triple("travel", "旅行出行", listOf("旅行", "旅游", "酒店", "攻略", "路线", "景点")),
            Triple("style_beauty", "穿搭美妆", listOf("穿搭", "衣服", "外套", "鞋", "包", "妆容", "护肤", "发型")),
            Triple("home_life", "居家生活", listOf("装修", "家居", "收纳", "家务", "厨房", "客厅")),
            Triple("learning_career", "学习职场", listOf("学习", "读书", "课程", "英语", "效率", "考试", "面试", "简历")),
            Triple("sports_health", "运动健康", listOf("运动", "健身", "跑步", "瑜伽", "睡眠", "养生")),
            Triple("family_pets", "亲子宠物", listOf("育儿", "亲子", "宝宝", "辅食", "宠物", "猫", "狗")),
            Triple("digital_tech", "数码科技", listOf("手机", "电脑", "数码", "软件", "相机", "科技")),
            Triple("entertainment_hobbies", "娱乐兴趣", listOf("电影", "电视剧", "音乐", "游戏", "摄影", "拍照")),
            Triple("emotion_growth", "情感成长", listOf("恋爱", "情感", "心理", "人际", "成长")),
        )
        rules.forEach { (categoryId, _, keywords) ->
            if (keywords.any { text.contains(it.lowercase()) }) {
                return Classification(categoryId, 0.86)
            }
        }
        return Classification(null, 0.25)
    }

    private fun categoryValues(category: Category, now: Long): ContentValues {
        return ContentValues().apply {
            put("id", category.id)
            put("name", category.name)
            put("type", category.type)
            put("sort_order", category.sortOrder)
            put("coarse_id", category.coarseId)
            put("level", category.level)
            if (category.parentId == null) putNull("parent_id") else put("parent_id", category.parentId)
            put("system_key", category.systemKey)
            put("fission_dimension", category.fissionDimension)
            put("source", category.source)
            put("status", category.status)
            put("created_at", now)
            put("updated_at", now)
        }
    }

    private fun metadataValues(note: SyncedNote, now: Long): ContentValues {
        return ContentValues().apply {
            put("rednote_id", note.rednoteId)
            put("title", note.title)
            put("description", note.desc)
            put("ai_keywords", existingAiKeywords(note.rednoteId))
            put("author_name", note.authorName)
            put("cover_url", note.coverUrl)
            put("cached_at", now)
            put("expires_at", now + METADATA_TTL_MS)
        }
    }

    private fun existingAiKeywords(rednoteId: String): String {
        return stringFor("SELECT ai_keywords FROM note_metadata_cache WHERE rednote_id = ?", rednoteId).orEmpty()
    }

    private fun SQLiteDatabase.replaceNoteTags(noteId: String, labels: List<String>, source: String) {
        delete("note_tags", "note_id = ? AND source = ?", arrayOf(noteId, source))
        labels.forEach { label ->
            val normalized = ConstrainedClassificationPolicy.normalizeLabel(label)
            if (normalized.isBlank()) return@forEach
            var tagId = stringFor(
                "SELECT id FROM tags WHERE normalized_name = ? AND type = ?",
                normalized,
                "semantic",
            )
            if (tagId == null) {
                tagId = "tag_${UUID.randomUUID()}"
                insert(
                    "tags",
                    ContentValues().apply {
                        put("id", tagId)
                        put("normalized_name", normalized)
                        put("display_name", label.trim().take(24))
                        put("type", "semantic")
                    },
                )
            }
            replace(
                "note_tags",
                ContentValues().apply {
                    put("note_id", noteId)
                    put("tag_id", tagId)
                    put("source", source)
                },
            )
        }
    }

    private fun noteCategoryValues(
        noteId: String,
        categoryId: String,
        source: String,
        confidence: Double,
        lockedByUser: Boolean,
    ): ContentValues {
        return ContentValues().apply {
            put("note_id", noteId)
            put("category_id", categoryId)
            put("source", source)
            put("confidence", confidence)
            put("locked_by_user", if (lockedByUser) 1 else 0)
            put("processing_status", if (categoryId == CATEGORY_PENDING) "pending" else "classified")
            put("classification_version", CLASSIFICATION_RULE_VERSION)
            put("failure_reason", "")
        }
    }

    companion object {
        private const val DB_NAME = "jishi_local.db"
        private const val DB_VERSION = 8
        private const val METADATA_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DEFAULT_ACCOUNT_ID = "current"
        private const val CLASSIFICATION_RULE_VERSION = "adr-0001-v1"

        const val CATEGORY_PENDING = "cat_pending"
        const val CATEGORY_INVALID = "cat_invalid"
    }

}

private fun createAiClassificationQueue(db: SQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS ai_classification_queue (
            id TEXT PRIMARY KEY,
            task_type TEXT NOT NULL,
            target_id TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_queue_status_created ON ai_classification_queue(status, created_at)")
}

private fun createConstrainedClassificationTables(db: SQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS tags (
            id TEXT PRIMARY KEY,
            normalized_name TEXT NOT NULL,
            display_name TEXT NOT NULL,
            type TEXT NOT NULL,
            UNIQUE(normalized_name, type)
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS note_tags (
            note_id TEXT NOT NULL,
            tag_id TEXT NOT NULL,
            source TEXT NOT NULL,
            PRIMARY KEY(note_id, tag_id)
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS classification_cache (
            input_fingerprint TEXT NOT NULL,
            rule_version TEXT NOT NULL,
            model_version TEXT NOT NULL,
            structured_result TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(input_fingerprint, rule_version, model_version)
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS classification_previews (
            id TEXT PRIMARY KEY,
            batch_id TEXT NOT NULL,
            note_id TEXT NOT NULL,
            before_category_id TEXT,
            after_primary_id TEXT,
            after_secondary_id TEXT,
            operation_type TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS creation_suppressions (
            primary_id TEXT NOT NULL,
            candidate_key TEXT NOT NULL,
            rule_version TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(primary_id, candidate_key, rule_version)
        )
        """.trimIndent(),
    )
}

private fun migrateUniqueNoteAssignments(db: SQLiteDatabase) {
    db.execSQL(
        """
        DELETE FROM note_categories
        WHERE rowid NOT IN (
            SELECT (
                SELECT chosen.rowid
                FROM note_categories chosen
                WHERE chosen.note_id = grouped.note_id
                ORDER BY chosen.locked_by_user DESC,
                         CASE WHEN chosen.category_id = 'cat_pending' THEN 1 ELSE 0 END,
                         chosen.rowid DESC
                LIMIT 1
            )
            FROM note_categories grouped
            GROUP BY grouped.note_id
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_note_categories_unique_note ON note_categories(note_id)")
}

private fun migrateLegacyAssignments(db: SQLiteDatabase) {
    db.execSQL("UPDATE pending_category_suggestions SET status = 'dismissed' WHERE status = 'pending'")
    ClassificationTaxonomy.legacyPrimaryMapping.forEach { (legacyId, primaryId) ->
        db.execSQL(
            "UPDATE note_categories SET category_id = ?, classification_version = ? WHERE category_id = ?",
            arrayOf(primaryId, "adr-0001-v1", legacyId),
        )
        db.execSQL(
            """
            UPDATE categories
            SET coarse_id = ?, parent_id = ?, level = 2, type = 'secondary', updated_at = ?
            WHERE coarse_id = ? AND id != ?
            """.trimIndent(),
            arrayOf(primaryId, primaryId, System.currentTimeMillis(), legacyId, legacyId),
        )
        db.execSQL("UPDATE categories SET status = 'migrated' WHERE id = ?", arrayOf(legacyId))
    }
    listOf("cat_gift", "cat_coarse_shopping").forEach { legacyId ->
        val categoryIds = buildSet {
            add(legacyId)
            addAll(
                db.rawQuery(
                    "SELECT id FROM categories WHERE coarse_id = ? AND id != ?",
                    arrayOf(legacyId, legacyId),
                ).useEach { cursor -> cursor.getString(0) },
            )
        }
        categoryIds.forEach { categoryId ->
            val lockedNoteIds = db.rawQuery(
                "SELECT note_id FROM note_categories WHERE category_id = ? AND locked_by_user = 1",
                arrayOf(categoryId),
            ).useEach { cursor -> cursor.getString(0) }
            lockedNoteIds.forEach { noteId ->
                val previewExists = db.rawQuery(
                    "SELECT COUNT(*) FROM classification_previews WHERE batch_id = ? AND note_id = ? AND before_category_id = ?",
                    arrayOf("legacy-v8", noteId, categoryId),
                ).use { cursor -> cursor.moveToFirst() && cursor.getLong(0) > 0L }
                if (!previewExists) {
                    db.insert(
                        "classification_previews",
                        ContentValues().apply {
                            put("id", "preview_${UUID.randomUUID()}")
                            put("batch_id", "legacy-v8")
                            put("note_id", noteId)
                            put("before_category_id", categoryId)
                            putNull("after_primary_id")
                            putNull("after_secondary_id")
                            put("operation_type", "migration_review")
                            put("status", "pending")
                            put("created_at", System.currentTimeMillis())
                        },
                    )
                }
            }
            db.execSQL(
                """
                UPDATE note_categories
                SET category_id = 'cat_pending', processing_status = 'pending',
                    classification_version = 'adr-0001-v1', failure_reason = 'legacy_category_requires_review'
                WHERE category_id = ? AND locked_by_user = 0
                """.trimIndent(),
                arrayOf(categoryId),
            )
            db.execSQL(
                "UPDATE categories SET status = ?, updated_at = ? WHERE id = ?",
                arrayOf(
                    if (lockedNoteIds.isEmpty()) "migrated" else "migration_review",
                    System.currentTimeMillis(),
                    categoryId,
                ),
            )
        }
    }
}

data class SyncResult(
    val inserted: Int,
    val pendingGroups: Int,
    val syncedIds: Set<String> = emptySet(),
    val changedNoteIds: Set<String> = emptySet(),
    val accountUserId: String? = null,
)

data class SyncPageState(
    val accountUserId: String,
    val cursor: String,
    val hasMore: Boolean,
)

private data class Classification(val categoryId: String?, val confidence: Double)

private data class CategorySummaryDraft(
    val id: String,
    val name: String,
    var count: Int = 0,
    val previews: MutableList<String> = mutableListOf(),
)

private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

private fun SQLiteDatabase.insert(table: String, values: ContentValues): Long {
    return insert(table, null, values)
}

private fun SQLiteDatabase.replace(table: String, values: ContentValues): Long {
    return replace(table, null, values)
}

private fun String.sanitizedError(): String {
    return replace(Regex("""(?i)(cookie|x-s|x-s-common|x-t|a1)=?[^,\s;]*"""), "$1=<redacted>")
        .take(240)
}

private inline fun <T> Cursor.useEach(mapper: (Cursor) -> T): List<T> {
    val result = mutableListOf<T>()
    use { cursor ->
        while (cursor.moveToNext()) {
            result += mapper(cursor)
        }
    }
    return result
}
