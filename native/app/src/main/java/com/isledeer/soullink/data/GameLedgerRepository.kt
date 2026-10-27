package com.isledeer.soullink.data

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 账本仓库：追加事件、按当前路径派生状态。
 *
 * 状态永远不写回数据库；「当前状态」= 初始状态 + 选中路径上的事件折叠。
 * 因此重新生成 / 分支 / 编辑天然不会重复计入。
 */
class GameLedgerRepository(
    private val sessionDao: GameSessionDao,
    private val eventDao: GameEventDao,
    private val conversationDao: ConversationDao,
    private val tree: MessageTreeRepository,
) {

    private val mutex = Mutex()
    private var lastSeq = 0L

    /** 会话绑定的游戏存档；普通聊天返回 null。 */
    suspend fun sessionOf(conversationId: String): GameSessionEntity? {
        val conversation = conversationDao.findById(conversationId) ?: return null
        val sessionId = conversation.sessionId ?: return null
        return sessionDao.findById(sessionId)
    }

    /** 按当前选中路径派生状态。 */
    suspend fun derive(sessionId: String): GameState? {
        val session = sessionDao.findById(sessionId) ?: return null
        val conversations = conversationDao.loadBySession(sessionId)
        val active = HashSet<String>()
        conversations.forEach { conversation ->
            tree.loadPath(conversation.id).forEach { turn -> active.add(turn.message.id) }
        }
        val events = eventDao.forSession(sessionId)
        return GameStateOps.fold(GameState.fromInitialJson(session.initialStateJson), events, active)
    }

    /**
     * 用一组动作替换某条消息的全部事件（编辑 / 重新生成共用）。
     * 先删旧事件再写新事件，保证同一条消息不会累积两份。
     */
    suspend fun replaceActions(
        session: GameSessionEntity,
        conversationId: String,
        messageId: String,
        nodeId: String,
        actions: List<GameAction>,
        source: String,
    ): GameState {
        eventDao.deleteForMessage(messageId)
        val seeds = actions.flatMap { GameActionEvents.seeds(it) }
        if (seeds.isNotEmpty()) {
            eventDao.insertAll(buildEvents(session.id, conversationId, messageId, nodeId, seeds, source))
        }
        return derive(session.id) ?: GameState.EMPTY
    }

    suspend fun deleteForMessage(messageId: String) = eventDao.deleteForMessage(messageId)

    suspend fun deleteForNode(nodeId: String) = eventDao.deleteForNode(nodeId)

    suspend fun deleteForConversation(conversationId: String) = eventDao.deleteForConversation(conversationId)

    private suspend fun buildEvents(
        sessionId: String,
        conversationId: String,
        messageId: String,
        nodeId: String,
        seeds: List<GameEventSeed>,
        source: String,
    ): List<GameEventEntity> = mutex.withLock {
        val now = System.currentTimeMillis()
        seeds.map { seed ->
            GameEventEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                conversationId = conversationId,
                messageId = messageId,
                nodeId = nodeId,
                seq = nextSeq(),
                kind = seed.kind,
                targetId = seed.targetId,
                payload = seed.payload,
                source = source,
                createdAt = now,
            )
        }
    }

    /** 会话内单调递增（同毫秒多次写入也不会乱序） */
    private fun nextSeq(): Long {
        val candidate = System.currentTimeMillis() * 1000L
        lastSeq = if (candidate > lastSeq) candidate else lastSeq + 1L
        return lastSeq
    }
}
