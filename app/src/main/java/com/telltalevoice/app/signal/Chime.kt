package com.telltalevoice.app.signal

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.telltalevoice.core.SpeechScheduler
import kotlin.math.PI
import kotlin.math.sin

/** 지금 세워야 하는 경고 앞 알림음(Z1 4-1: 약 220ms, 1,100Hz). */
object Chime {
    private const val RATE = 44_100

    fun samples(): ShortArray {
        val n = RATE * SpeechScheduler.CHIME_MS / 1000
        val fade = RATE / 100
        return ShortArray(n) { i ->
            val env = minOf(1.0, i / fade.toDouble(), (n - i) / fade.toDouble())
            (sin(2 * PI * SpeechScheduler.CHIME_HZ * i / RATE) * env * Short.MAX_VALUE * 0.6).toInt().toShort()
        }
    }

    fun play() {
        runCatching {
            val data = samples()
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            track.play()
            Thread.sleep(SpeechScheduler.CHIME_MS.toLong() + 30)
            track.release()
        }
    }
}
