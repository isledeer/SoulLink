package com.isledeer.soullink.data

/**
 * ⚠️ 这个文件是**空壳**，不要在这里声明任何东西。
 *
 * 历史上 MessageDao 在这里，后来并入了 MessageEntity.kt。
 * 但仓库里可能仍残留这份文件（git 跟踪），一旦保留旧内容就会和
 * MessageEntity.kt 里的 interface MessageDao 冲突（重复声明）。
 *
 * 所以这里刻意留成一个合法但空的 Kotlin 文件：
 *   - 文件存在 → git 覆盖后旧声明自动消失
 *   - 没有任何声明 → 不会再触发 duplicate class
 *
 * 确认所有分支都不再需要它之后，可以安全删除本文件。
 */
internal const val MESSAGE_DAO_LEGACY_FILE = true