package com.lewis.phoneagent.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lewis.phoneagent.model.ChatMessage

/**
 * Room 数据库：操作历史（按会话分组）+ 用户自定义规则 + 全局记忆 + 错题本。
 */
@Database(
    entities = [
        ChatMessage::class, UserRuleEntity::class, MemoryEntity::class,
        NoteEntity::class, MistakeNoteEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun memoryDao(): MemoryDao
    abstract fun noteDao(): NoteDao
    abstract fun mistakeNoteDao(): MistakeNoteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "phone_agent.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v1 → v2：历史表加会话列；新建记忆表 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS memory (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, " +
                        "count INTEGER NOT NULL, updateTime INTEGER NOT NULL)",
                )
            }
        }

        /** v2 → v3：新建便签表 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS note (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "content TEXT NOT NULL, createTime INTEGER NOT NULL)",
                )
            }
        }

        /** v3 → v4：新建错题本表 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS mistake_note (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "question TEXT NOT NULL, answer TEXT NOT NULL, subject TEXT NOT NULL, " +
                        "imagePath TEXT NOT NULL, stage INTEGER NOT NULL, " +
                        "nextReviewAt INTEGER NOT NULL, wrongCount INTEGER NOT NULL, " +
                        "createTime INTEGER NOT NULL)",
                )
            }
        }
    }
}

/** 错题本 DAO：到期查询驱动复习提醒与复习流程 */
@Dao
interface MistakeNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: MistakeNoteEntity): Long

    /** 到期待复习的题，按到期时间升序 */
    @Query("SELECT * FROM mistake_note WHERE nextReviewAt <= :now ORDER BY nextReviewAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<MistakeNoteEntity>

    /** 到期待复习的题数（提醒通知用） */
    @Query("SELECT COUNT(*) FROM mistake_note WHERE nextReviewAt <= :now")
    suspend fun dueCount(now: Long): Int

    /** 错题本总数 */
    @Query("SELECT COUNT(*) FROM mistake_note")
    suspend fun total(): Int

    /** 关键词搜索（题目/答案/学科都搜） */
    @Query(
        "SELECT * FROM mistake_note WHERE question LIKE '%' || :kw || '%' " +
            "OR answer LIKE '%' || :kw || '%' OR subject LIKE '%' || :kw || '%' " +
            "ORDER BY createTime DESC LIMIT :limit",
    )
    suspend fun search(kw: String, limit: Int): List<MistakeNoteEntity>

    /** 最近存入的题（存完报序号用） */
    @Query("SELECT * FROM mistake_note ORDER BY createTime DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MistakeNoteEntity>

    @Query("SELECT * FROM mistake_note WHERE id = :id")
    suspend fun byId(id: Long): MistakeNoteEntity?

    /** 复习后更新：阶段、下次复习时间；答错累加 wrongCount */
    @Query(
        "UPDATE mistake_note SET stage = :stage, nextReviewAt = :next, " +
            "wrongCount = wrongCount + :wrongDelta WHERE id = :id",
    )
    suspend fun updateReview(id: Long, stage: Int, next: Long, wrongDelta: Int)

    @Query("DELETE FROM mistake_note WHERE id = :id")
    suspend fun delete(id: Long)
}

/** 操作历史 DAO（按会话查询） */
@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    @Query("UPDATE history SET content = :content, contentType = :contentType WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, contentType: String)

    /** 指定会话最近 N 条，时间升序 */
    @Query("SELECT * FROM history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(sessionId: Long, limit: Int): List<ChatMessage>

    /** 所有会话摘要：每会话取最新一条消息（历史记录页用） */
    @Query(
        "SELECT * FROM history WHERE id IN " +
            "(SELECT MAX(id) FROM history GROUP BY sessionId) ORDER BY timestamp DESC",
    )
    suspend fun sessionSummaries(): List<ChatMessage>

    /** 删除整个会话 */
    @Query("DELETE FROM history WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}

/** 用户自定义规则 */
@Dao
interface RuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: UserRuleEntity)

    @Query("SELECT * FROM user_rule WHERE enabled = 1")
    suspend fun enabledRules(): List<UserRuleEntity>
}

/** 全局记忆 */
@Dao
interface MemoryDao {

    /** 相同 (type,key) 记忆累加计数并更新 value */
    @Query(
        "UPDATE memory SET count = count + :delta, value = :value, updateTime = :now " +
            "WHERE type = :type AND key = :key",
    )
    suspend fun bump(type: String, key: String, value: String, delta: Int, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(memory: MemoryEntity): Long

    /** 按使用次数取 top N（快捷指令个性化用） */
    @Query("SELECT * FROM memory ORDER BY count DESC LIMIT :limit")
    suspend fun top(limit: Int): List<MemoryEntity>

    /** 全部记忆（设置页展示） */
    @Query("SELECT * FROM memory ORDER BY updateTime DESC")
    suspend fun all(): List<MemoryEntity>

    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun delete(id: Long)
}

/** 便签 */
@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM note ORDER BY createTime DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NoteEntity>

    @Query("DELETE FROM note WHERE content LIKE '%' || :keyword || '%'")
    suspend fun deleteByKeyword(keyword: String): Int

    @Query("DELETE FROM note")
    suspend fun clear()
}
