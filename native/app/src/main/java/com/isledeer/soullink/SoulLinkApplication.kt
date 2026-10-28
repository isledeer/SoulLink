package com.isledeer.soullink

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import com.isledeer.soullink.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用入口 + 全局崩溃兜底（白屏闭环）。
 *
 * 未捕获异常 → 追加写入 files/crash.log → 交回系统默认处理器。
 * 下次启动时 ChatScreen 检测到 crash.log 会弹「上次崩溃」卡片，可一键导出。
 */
class SoulLinkApplication : Application() {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        // 全局异常兜底：先写日志再交默认处理器（不吞崩溃）
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.append(applicationContext, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
            // 兜底：确保进程退出
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        startKoin {
            androidContext(this@SoulLinkApplication)
            modules(appModule)
        }
    }
}

/** 崩溃日志文件（读写 + 分享） */
object CrashLog {

    fun file(context: android.content.Context): File =
        File(context.filesDir, "crash.log")

    fun read(context: android.content.Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: android.content.Context) {
        runCatching { file(context).delete() }
    }

    fun append(
        context: android.content.Context,
        thread: Thread?,
        throwable: Throwable,
    ) {
        runCatching {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            val line = "\n===== $time thread=${thread?.name ?: "?"} =====\n" +
                "${throwable.javaClass.name}: ${throwable.message}\n${writer}\n"
            file(context).appendText(line)
        }
    }

    /** 分享崩溃日志（ACTION_SEND，走 FileProvider） */
    fun share(context: android.content.Context) {
        val target = file(context)
        if (!target.exists()) return
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出崩溃日志"))
        }
    }
}