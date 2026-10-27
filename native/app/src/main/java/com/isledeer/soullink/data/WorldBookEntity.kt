package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 世界书：规则文档（docJson）+ 关键词条目（world_entries）。
 * 「框架 + AI 填充」：模板只给骨架，内容由用户自己的 AI 填。
 */
@Entity(tableName = "worldbooks")
data class WorldBookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val template: String = "custom",
    val docJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "world_entries", indices = [Index("worldbookId")])
data class WorldEntryEntity(
    @PrimaryKey val id: String,
    val worldbookId: String,
    val keysJson: String = "[]",
    val content: String,
    val alwaysOn: Boolean = false,
    val priority: Int = 0,
)

@Dao
interface WorldBookDao {

    @Query("SELECT * FROM worldbooks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WorldBookEntity>>

    @Query("SELECT * FROM worldbooks ORDER BY updatedAt DESC")
    suspend fun loadAll(): List<WorldBookEntity>

    @Query("SELECT * FROM worldbooks WHERE id = :id")
    suspend fun findById(id: String): WorldBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: WorldBookEntity)

    @Query("DELETE FROM worldbooks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM world_entries WHERE worldbookId = :worldbookId ORDER BY priority DESC, id ASC")
    suspend fun entriesOf(worldbookId: String): List<WorldEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: WorldEntryEntity)

    @Query("DELETE FROM world_entries WHERE worldbookId = :worldbookId")
    suspend fun clearEntries(worldbookId: String)

    @Query("DELETE FROM world_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)
}