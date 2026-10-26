package com.isledeer.soullink.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 本地文档解析（TXT / DOCX / PDF）。
 *
 * - TXT：直接读文本
 * - DOCX：它是 zip，直接解 document.xml 提 <w:t> 文本（不引额外依赖）
 * - PDF：用 com.tom-roush:pdfbox-android 提取纯文本
 *
 * 所有结果截断到 MAX_CHARS，避免把整本书塞进上下文。
 */
object DocumentParser {

    const val MAX_CHARS = 3000

    fun extract(context: Context, uri: Uri, fileName: String? = null): String {
        val name = (fileName ?: uri.lastPathSegment ?: "").lowercase()
        val text = when {
            name.endsWith(".pdf") -> extractPdf(context, uri)
            name.endsWith(".docx") -> extractDocx(context, uri)
            else -> extractText(context, uri)
        }
        return text.trim().take(MAX_CHARS)
    }

    private fun extractText(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    }.getOrElse { "（文本读取失败：${it.message}）" }

    private fun extractDocx(context: Context, uri: Uri): String = runCatching {
        // docx 是 zip；document.xml 里正文是 <w:t>…</w:t>
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val temp = File.createTempFile("soullink-docx", ".zip", context.cacheDir)
        FileOutputStream(temp).use { it.write(bytes) }
        ZipFile(temp).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: zip.getEntry("document.xml")
                ?: return ""
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(xml)
                .joinToString("") { match -> match.groupValues[1] }
        }.also { temp.delete() }
    }.getOrElse { "（DOCX 解析失败：${it.message}）" }

    private fun extractPdf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream)
            val text = try {
                com.tom_roush.pdfbox.text.PDFTextStripper().getText(document)
            } finally {
                document.close()
            }
            text
        }.orEmpty()
    }.getOrElse { "（PDF 解析失败：${it.message}）" }
}