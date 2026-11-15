package com.telltalevoice.app.signal.fake

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.telltalevoice.app.signal.Chime
import com.telltalevoice.app.signal.Speaker
import com.telltalevoice.core.Utterance
import java.util.Locale

/** 시연용 음성. Pleos TTS 서비스가 없는 에뮬레이터나 태블릿에서 안드로이드 기본 음성 엔진으로 말한다. */
class AndroidTtsSpeaker(context: Context) : Speaker {
    override val name = "Android TTS(시연)"
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile override var speaking = false
        private set
    private var done: (() -> Unit)? = null

    override fun start(onReady: (Boolean) -> Unit) {
        tts = TextToSpeech(app) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.language = Locale.KOREAN
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { speaking = true }
                    override fun onDone(id: String?) { finish() }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { finish() }
                })
            }
            onReady(ok)
        }
    }

    private fun finish() { speaking = false; done?.invoke(); done = null }

    override fun speak(utterance: Utterance, onDone: () -> Unit) {
        done = onDone
        speaking = true
        Thread {
            if (utterance.chime) Chime.play()
            val r = tts?.speak(utterance.ttsText, TextToSpeech.QUEUE_FLUSH, null, utterance.hashCode().toString())
            if (r != TextToSpeech.SUCCESS) finish()
        }.start()
    }

    override fun stop() { tts?.stop() }
    override fun release() { tts?.shutdown(); tts = null }
}
