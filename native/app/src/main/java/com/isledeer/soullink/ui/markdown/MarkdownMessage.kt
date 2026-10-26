package com.isledeer.soullink.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 轻量 Markdown 渲染器（零第三方依赖版）。
 *
 * 支持：段落 / **粗体** / *斜体* / `行内代码` / ``` 代码块（右上复制）/
 * ```mermaid 图表（WebView 注入 mermaid.js 渲染）/ > 引用折叠成段落。
 *
 * 暂时不做：语法高亮（60+ 语言要引库，等 CI 环境验证后换
 * com.mikepenz:multiplatform-markdown-renderer 或 halilibo richtext）、
 * 表格、任务列表。
 */
sealed interface MdBlock {
    data class Text(val content: AnnotatedString) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Mermaid(val code: String) : MdBlock
}

fun parseMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += MdBlock.Text(inlineStyle(paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flushParagraph()
                val language = line.trim().removePrefix("```").trim()
                val buffer = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    buffer.appendLine(lines[i])
                    i++
                }
                i++ // 吃掉收尾围栏
                if (language.equals("mermaid", ignoreCase = true)) {
                    blocks += MdBlock.Mermaid(buffer.toString().trim())
                } else {
                    blocks += MdBlock.Code(language, buffer.toString().trimEnd())
                }
                continue
            }

            line.isBlank() -> flushParagraph()

            else -> {
                val content = if (line.trimStart().startsWith("> ")) line.trim().removePrefix("> ") else line
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(content)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

/** 行内样式：**bold** / *italic* / `code` */
private fun inlineStyle(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }

            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x22FFFFFF),
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }

            text.startsWith("*", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }

            else -> {
                append(text[i]); i++
            }
        }
    }
}

@Composable
fun MarkdownMessage(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFF1F5F9),
    fontSize: TextUnit = 15.sp,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Text -> Text(
                    text = block.content,
                    color = textColor,
                    fontSize = fontSize,
                    lineHeight = 23.sp,
                )

                is MdBlock.Code -> CodeBlockView(code = block.code)

                is MdBlock.Mermaid -> MermaidView(code = block.code)
            }
        }
    }
}

@Composable
fun CodeBlockView(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1AFFFFFF))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("代码", color = Color(0x99CBD5E1), fontSize = 10.sp)
            Text(
                text = if (copied) "已复制 ✓" else "复制",
                color = Color(0x99CBD5E1),
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            text = code,
            color = Color(0xFFE2E8F0),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
        )
    }
}

/** Mermaid 图表：WebView 注入 mermaid.js 渲染。离线时展示原始代码。 */
@Composable
fun MermaidView(code: String, modifier: Modifier = Modifier) {
    val html = remember(code) { mermaidHtml(code) }
    AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(
                    "https://cdn.jsdelivr.net/npm/",
                    html,
                    "text/html; charset=utf-8",
                    "utf-8",
                    null,
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}

private fun mermaidHtml(code: String): String {
    val escaped = code.replace("</", "<\\/")
    return """<!doctype html><html><head><meta charset="utf-8">
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<style>body{margin:0;background:transparent;display:flex;justify-content:center}</style>
</head><body><pre class="mermaid">$escaped</pre>
<script>mermaid.initialize({startOnLoad:true,theme:'dark'});</script></body></html>"""
}