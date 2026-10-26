package com.isledeer.soullink.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 系统 TTS 封装。
 *
 * 初始化是异步的，用户可能在 ready 之前就点了朗读 —— 这里保留一条待播文本，
 * 初始化完成后再补播，避免「第一次点没反应」。
 */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var initializing = false

    fun speak(text: String) {
        if (text.isBlank()) return

        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "soullink-utterance")
            return
        }

        pending = text
        if (!initializing) {
            initializing = true
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.language = Locale.getDefault()
                    pending?.let { queued ->
                        tts?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "soullink-utterance")
                        pending = null
                    }
                } else {
                    Log.w(TAG, "TTS 初始化失败")
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "SoulLinkTTS"
    }
}