package com.isledeer.soullink.data

/**
 * ⚠️ 空壳文件，刻意不声明任何类型。
 *
 * v3 起消息改为「节点 + 变体」结构（见 MessageNodeEntity.kt / MessageTreeRepository.kt），
 * 旧的扁平 MessageEntity / MessageDao 已删除。
 *
 * 保留这个文件是为了防呆：如果 git 仓库里仍残留旧版本（带 MessageEntity + MessageDao），
 * 覆盖后旧声明自动消失，不会出现重复声明或 DEFAULT_CONVERSATION 缺失。
 */
internal const val MESSAGE_ENTITY_LEGACY_FILE = true
