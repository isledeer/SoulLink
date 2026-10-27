package com.isledeer.soullink.data

/**
 * 内置技能注册表。
 * 技能 = 一段注入提示词；助手勾选后，请求时拼进 system prompt。
 * v1 只有内置四项；将来可扩展为用户自定义技能。
 */
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val instruction: String,
)

object SkillsRegistry {

    val all: List<Skill> = listOf(
        Skill(
            id = "translator",
            name = "翻译员",
            description = "翻译时只输出译文，不加解释",
            instruction = "当用户要求翻译时，只输出译文本身，不要加任何解释或前后缀。",
        ),
        Skill(
            id = "proofreader",
            name = "校对",
            description = "检查错别字与语法并给出修改建议",
            instruction = "回答前检查文本中的错别字与语法问题，必要时顺带给出修改建议。",
        ),
        Skill(
            id = "summarizer",
            name = "总结员",
            description = "把长内容压缩成编号要点",
            instruction = "当用户要求总结时，用编号要点输出，控制在 300 字以内，先给结论。",
        ),
        Skill(
            id = "step_reasoner",
            name = "推理拆解",
            description = "复杂问题先分步再回答",
            instruction = "面对复杂问题，先拆解成步骤逐步推理，最后单独给出结论。",
        ),
    )

    fun byId(id: String): Skill? = all.firstOrNull { it.id == id }

    /** 组装选中技能的注入段 */
    fun buildSection(enabledIds: List<String>): String {
        val selected = enabledIds.mapNotNull { byId(it) }
        if (selected.isEmpty()) return ""
        return selected.joinToString("\n") { skill -> "【技能：${skill.name}】${skill.instruction}" }
    }
}