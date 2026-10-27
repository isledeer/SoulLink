package com.isledeer.soullink.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 游戏存档 = 一本账的容器。
 * worldbookId / characterId 至少一个非空时才启用游戏层。
 */
@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val worldbookId: String? = null,
    val characterId: String? = null,
    val initialStateJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
)

/** 游戏层开关：有世界书或有角色卡才算游戏存档。 */
val GameSessionEntity.isGameEnabled: Boolean
    get() = !worldbookId.isNullOrBlank() || !characterId.isNullOrBlank()

@Dao
interface GameSessionDao {

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    suspend fun findById(id: String): GameSessionEntity?

    @Query("SELECT * FROM game_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GameSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: GameSessionEntity)

    @Query("DELETE FROM game_sessions WHERE id = :id")
    suspend fun delete(id: String)
}
