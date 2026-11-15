package com.telltalevoice.app.signal.pleos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.telltalevoice.app.signal.Chime
import com.telltalevoice.app.signal.Speaker
import com.telltalevoice.core.Utterance
import java.util.Locale

/**
 * Pleos TextToSpeech 3.3.1, ON_DEVICE 모드(차 안 엔진, 네트워크와 앱 등록 불필요, 02번 문서).
 * 오류가 나면 말하는 중 상태와 onBusy 를 함께 풀어 다음 안내가 멈추지 않게 한다.
 * 오디오(2026. 11. 14. Pleos 에뮬레이터 실측 뒤 고침): 기본값은 음성 서비스가 USAGE_MEDIA 로 재생하고 포커스를 요청하지 않아
 * 음악과 같은 묶음으로 섞였다. 안내 음성은 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE(NAVIGATION 컨텍스트)로 재생하고,
 * 말하는 동안 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 을 요청해 Pleos 오디오 포커스 문서의 C(동시, duck) 조건을 맞춘다.
 */
class PleosTtsSpeaker(context: Context, private val onBusy: (Boolean) -> Unit) : Speaker {
    override val name = "Pleos TTS ON_DEVICE"
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()
    @Volatile override var speaking = false
        private set
    private var done: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onStart() { speaking = true; onBusy(false) }
        override fun onReady() = Unit
        override fun onDone() { finish() }
        override fun onStop() { finish() }
        // 오류가 나도 말하는 중 상태와 다른 음성 사용 중 상태를 함께 풀어, 급하지 않은 안내가 줄에서 멈추지 않게 한다.
        override fun onError(errorType: ErrorType) { finish(); onBusy(false) }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private fun finish() {
        if (speaking) Log.i(TAG, "음성 끝")
        speaking = false
        runCatching { audio.abandonAudioFocusRequest(focus) }
        done?.invoke(); done = null
    }

    override fun start(onReady: (Boolean) -> Unit) {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { tts.setLocale(Locale.KOREA) }
                    runCatching { tts.setAudioAttributes(attrs) }.onFailure { Log.w(TAG, "음성 용도 설정 실패: ${it.message}") }
                    tts.addEventListener(events)
                    onReady(true)
                }
                override fun onFailure(errorType: ErrorType) { onReady(false) }
            })
        } catch (e: Throwable) {
            onReady(false)
        }
    }

    override fun speak(utterance: Utterance, onDone: () -> Unit) {
        done = onDone
        speaking = true
        val f = runCatching { audio.requestAudioFocus(focus) }.getOrDefault(-1)
        Log.i(TAG, "포커스 요청 MAY_DUCK 결과=$f")
        if (utterance.chime) { Log.i(TAG, "알림음 시작"); Chime.play(); Log.i(TAG, "알림음 끝") }
        Log.i(TAG, "음성 요청: ${utterance.text}")
        runCatching { tts.speak(utterance.ttsText) }.onFailure { finish() }
    }

    override fun stop() { runCatching { tts.stop() } }
    companion object { private const val TAG = "PleosTts" }

    override fun release() { runCatching { tts.removeEventListener(events); tts.release() } }
}
