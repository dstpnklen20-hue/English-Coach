package com.englishcoach.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Озвучка английского через системный синтез речи (работает офлайн, если голос скачан). */
class Tts(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    var available = true
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            available = false
            return
        }
        available = tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
        ready = available
        pending?.let { speak(it) }
        pending = null
    }

    /** Если движок ещё запускается — фраза запомнится и прозвучит сразу после готовности. */
    fun speak(text: String, slow: Boolean = false) {
        if (!ready) {
            pending = text
            return
        }
        tts.setSpeechRate(if (slow) 0.6f else 0.9f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }
}
