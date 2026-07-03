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
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
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
                PRIMARY KEY(note_id, category_id)
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
                db.insert("categories", categoryValues(Category(CATEGORY_INVALID, "失效收藏", "system", 10_000), now))
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
    }

    fun ensureDefaults() {
        writableDatabase.transaction {
            val now = System.currentTimeMillis()
            listOf(
                Category(CATEGORY_FOOD, "美食", "system", 10),
                Category(CATEGORY_TRAVEL, "旅行", "system", 20),
                Category(CATEGORY_STUDY, "学习", "system", 30),
                Category(CATEGORY_OUTFIT, "穿搭", "system", 40),
                Category(CATEGORY_GIFT, "礼物", "system", 50),
                Category(CATEGORY_PENDING, "待整理", "system", 999),
                Category(CATEGORY_INVALID, "失效收藏", "system", 10_000),
            ).forEach { category ->
                if (longFor("SELECT COUNT(*) FROM categories WHERE id = ?", category.id) == 0L) {
                    insert("categories", categoryValues(category, now))
                }
            }
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
            SELECT c.id, c.name, m.cover_url
            FROM categories c
            JOIN note_categories nc ON nc.category_id = c.id
            JOIN notes n ON n.rednote_id = nc.note_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = nc.note_id
            WHERE n.status = ? AND c.id != ?
            ORDER BY c.sort_order ASC, c.name ASC, n.last_seen_at DESC
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
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE nc.category_id = ? AND $statusFilter
            ORDER BY n.removed_at DESC, n.last_seen_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(categoryId, statusArg, limit.toString(), offset.toString()),
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
            SELECT n.rednote_id, c.id, c.name, m.title, m.description, m.cover_url, n.note_url
            FROM notes n
            JOIN note_categories nc ON nc.note_id = n.rednote_id
            JOIN categories c ON c.id = nc.category_id
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE n.status = ? AND c.id != ?
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
        val now = System.currentTimeMillis()
        writableDatabase.transaction {
            val suggestion = findPendingSuggestion(suggestionId) ?: return@transaction
            val categoryId = "cat_${UUID.randomUUID()}"
            insert("categories", categoryValues(Category(categoryId, categoryName.ifBlank { suggestion.suggestedName }, "manual", 100), now))
            suggestion.rednoteIds.forEach { rednoteId ->
                delete("note_categories", "note_id = ? AND category_id = ?", arrayOf(rednoteId, CATEGORY_PENDING))
                replace("note_categories", noteCategoryValues(rednoteId, categoryId, "manual", 1.0, true))
            }
            markSuggestion(suggestionId, "accepted")
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
        val now = System.currentTimeMillis()
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
                        createPendingSuggestion(note, match.suggestedName, match.reason, now)
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
            SELECT n.rednote_id, m.title, m.description, m.ai_keywords, c.id, c.name
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
            )
        }.distinctBy { it.rednoteId }
    }

    fun ensureCoarseCategories(defs: List<CoarseCategoryDef>) {
        writableDatabase.transaction {
            val now = System.currentTimeMillis()
            defs.forEachIndexed { index, def ->
                if (longFor("SELECT COUNT(*) FROM categories WHERE id = ?", def.id) == 0L) {
                    insert("categories", categoryValues(Category(def.id, def.name, "coarse", 100 + index, coarseId = def.id), now))
                }
            }
        }
    }

    fun aiNotesInCategory(categoryId: String): List<AiNote> {
        return readableDatabase.rawQuery(
            """
            SELECT n.rednote_id, m.title, m.description, m.ai_keywords, c.id, c.name
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

    fun assignNotesToCategories(assignments: Map<String, Pair<String, Double>>): Int {
        if (assignments.isEmpty()) return 0
        var moved = 0
        writableDatabase.transaction {
            assignments.forEach { (noteId, target) ->
                val locked = longFor(
                    "SELECT COUNT(*) FROM note_categories WHERE note_id = ? AND locked_by_user = 1",
                    noteId,
                ) > 0L
                if (!locked) {
                    delete("note_categories", "note_id = ?", arrayOf(noteId))
                    replace("note_categories", noteCategoryValues(noteId, target.first, "ai", target.second, false))
                    removeIdsFromPendingSuggestions(setOf(noteId))
                    moved += 1
                }
            }
        }
        return moved
    }

    /**
     * 把一个分类裂变为多个细分分类。groups 中未覆盖的笔记留在原分类；
     * 细分分类继承原分类的 coarse_id；原分类清空后删除。
     */
    fun splitCategoryInto(categoryId: String, groups: List<AiSeedCategory>): Int {
        if (categoryId == CATEGORY_PENDING || categoryId == CATEGORY_INVALID) return 0
        val validGroups = groups
            .map { it.copy(name = it.name.trim(), noteIds = it.noteIds.filter(String::isNotBlank).distinct()) }
            .filter { it.name.isNotBlank() && it.noteIds.isNotEmpty() }
        if (validGroups.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var created = 0
        writableDatabase.transaction {
            val coarseId = stringFor("SELECT coarse_id FROM categories WHERE id = ?", categoryId).orEmpty()
            val originalOrder = longFor("SELECT sort_order FROM categories WHERE id = ?", categoryId).toInt()
            val movedIds = mutableSetOf<String>()
            validGroups.forEach { group ->
                val movableIds = group.noteIds.filter { noteId ->
                    noteId !in movedIds && longFor(
                        "SELECT COUNT(*) FROM note_categories WHERE note_id = ? AND category_id = ? AND locked_by_user = 0",
                        noteId,
                        categoryId,
                    ) > 0L
                }
                if (movableIds.isEmpty()) return@forEach
                val newCategoryId = "cat_${UUID.randomUUID()}"
                insert("categories", categoryValues(Category(newCategoryId, group.name.take(24), "ai", originalOrder + created, coarseId), now))
                created += 1
                movableIds.forEach { noteId ->
                    delete("note_categories", "note_id = ? AND category_id = ?", arrayOf(noteId, categoryId))
                    replace("note_categories", noteCategoryValues(noteId, newCategoryId, "ai_split", 1.0, false))
                    movedIds += noteId
                }
            }
            if (longFor("SELECT COUNT(*) FROM note_categories WHERE category_id = ?", categoryId) == 0L) {
                delete("categories", "id = ?", arrayOf(categoryId))
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
        val args = mutableListOf(NoteStatus.ACTIVE.name)
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
            LEFT JOIN note_metadata_cache m ON m.rednote_id = n.rednote_id
            WHERE n.status = ?$idFilter
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

    private fun SQLiteDatabase.createPendingSuggestion(note: SyncedNote, suggestedName: String, reason: String, now: Long) {
        val existing = rawQuery(
            """
            SELECT id, rednote_ids
            FROM pending_category_suggestions
            WHERE suggested_name = ? AND status = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(suggestedName, "pending"),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
        }

        if (existing == null) {
            insert(
                "pending_category_suggestions",
                ContentValues().apply {
                    put("id", "suggestion_${UUID.randomUUID()}")
                    put("rednote_ids", note.rednoteId)
                    put("suggested_name", suggestedName)
                    put("reason", reason)
                    put("status", "pending")
                    put("created_at", now)
                },
            )
        } else {
            val updatedIds = (existing.second.split(",") + note.rednoteId).distinct().joinToString(",")
            update(
                "pending_category_suggestions",
                ContentValues().apply { put("rednote_ids", updatedIds) },
                "id = ?",
                arrayOf(existing.first),
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
            Triple(CATEGORY_FOOD, "美食", listOf("美食", "餐厅", "咖啡", "蛋糕", "做饭", "食谱", "火锅", "烘焙")),
            Triple(CATEGORY_TRAVEL, "旅行", listOf("旅行", "旅游", "酒店", "攻略", "城市", "路线", "景点", "周末")),
            Triple(CATEGORY_STUDY, "学习", listOf("学习", "读书", "课程", "英语", "笔记", "效率", "考试")),
            Triple(CATEGORY_OUTFIT, "穿搭", listOf("穿搭", "衣服", "外套", "鞋", "包", "妆容", "发型")),
            Triple(CATEGORY_GIFT, "礼物", listOf("礼物", "生日", "纪念日", "送", "清单")),
        )
        rules.forEach { (categoryId, _, keywords) ->
            if (keywords.any { text.contains(it.lowercase()) }) {
                return Classification(categoryId, 0.86, "", "")
            }
        }
        val guessedName = when {
            text.contains("家") || text.contains("收纳") -> "家居"
            text.contains("运动") || text.contains("健身") -> "运动"
            text.contains("摄影") || text.contains("拍照") -> "摄影"
            else -> "新分类"
        }
        return Classification(null, 0.25, guessedName, "未明显命中现有分类规则")
    }

    private fun categoryValues(category: Category, now: Long): ContentValues {
        return ContentValues().apply {
            put("id", category.id)
            put("name", category.name)
            put("type", category.type)
            put("sort_order", category.sortOrder)
            put("coarse_id", category.coarseId)
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
        }
    }

    companion object {
        private const val DB_NAME = "jishi_local.db"
        private const val DB_VERSION = 7
        private const val METADATA_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DEFAULT_ACCOUNT_ID = "current"

        const val CATEGORY_FOOD = "cat_food"
        const val CATEGORY_TRAVEL = "cat_travel"
        const val CATEGORY_STUDY = "cat_study"
        const val CATEGORY_OUTFIT = "cat_outfit"
        const val CATEGORY_GIFT = "cat_gift"
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

private data class Classification(
    val categoryId: String?,
    val confidence: Double,
    val suggestedName: String,
    val reason: String,
)

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
