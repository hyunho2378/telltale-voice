package com.telltalevoice.core

/** 말하기 요청. tier 가 null 이면 갈래 밖 문장(대피, 휴게소 거리, 도착). */
data class SpeechRequest(
    val utterance: Utterance,
    val tier: Tier?,
    val createdAtMs: Long,
    /** 운전자가 직접 다시 듣기를 고른 요청. 횟수 상한과 "그만"을 넘어 한 번 말한다. */
    val bypassCap: Boolean = false,
) {
    /** 미루지 않는 요청: 바로 서야 하는 경고와 대피 문장, 운전자가 Gleo 로 물은 답 */
    val urgent: Boolean
        get() = tier == Tier.STOP_NOW || utterance.kind == Utterance.Kind.EVACUATION || utterance.kind == Utterance.Kind.ANSWER

    /**
     * 한 운전 횟수 상한을 적용하는지. 즉시 정차(STOP_NOW) 경고는 상한에서 뺀다(외부 자문 2026. 11.: "보조 음성에는 상한을 둘 수 있고 즉시 정차 경고만 예외").
     * 운전자가 고른 다시 듣기와 Gleo 답(bypassCap)도 상한을 넘는다. 대피 문장은 기존 규칙(상한 적용) 그대로.
     */
    val capped: Boolean
        get() = !bypassCap && tier != Tier.STOP_NOW

    /** 같은 경고 횟수를 세는 열쇠 */
    val capKey: String
        get() = "${utterance.kind}:${utterance.lampId ?: utterance.text}"
}

/** 말하는 때를 정하는 데 쓰는 차 안 상태. */
data class AudioContext(
    val gleoSpeaking: Boolean = false,
    val otherAudioBusy: Boolean = false,
    val nextTurnMeters: Int? = null,
    /** 운전자가 브레이크를 밟는 중(Pleos Brake.registerBrakePedalStatus) */
    val braking: Boolean = false,
    /** 후진 중(기어 R) */
    val reversing: Boolean = false,
)

sealed interface Decision {
    data object SpeakNow : Decision
    data class Defer(val untilMs: Long) : Decision
    data object WaitAudio : Decision
    /** 후진 중: 말하지 않고 화면에 글만 3초 띄운다(Z1 1-5) */
    data object ScreenOnly : Decision
    data class Drop(val reason: String) : Decision
}

/**
 * 말하는 때 결정기(Z1 음성 설계 규칙서 1장, 3장).
 * - 바로 서야 하는 경고와 대피 문장은 미루지 않는다(Z1 1-2).
 * - 나머지는 다음 회전까지 NEAR_TURN_METERS 미만이거나 브레이크를 밟는 중이면 최대 MAX_DEFER_MS 미룬다(Z1 1-3, ISO 15006 10초).
 * - 후진 중에는 말하지 않고 화면에만 띄운다(Z1 1-5).
 * - Gleo 나 통화가 소리를 내는 중이면 알림음만 내고, 지금 세워야 하는 경고가 아니면 Gleo 가 끝난 뒤 말한다(Z1 1-6).
 * - 같은 경고는 한 운전에 MAX_PER_DRIVE 번까지(Z1 2-9). 즉시 정차 경고는 예외로 상한 없이 STOP_NOW_REPEAT_MS 마다 되풀이하고, 경고가 꺼지거나 기어 P 면 멈춘다. "그만" 한 경고는 다시 먼저 말하지 않는다(Z1 3-3).
 */
class SpeechScheduler(
    val nearTurnMeters: Int = NEAR_TURN_METERS,
    val maxDeferMs: Long = MAX_DEFER_MS,
    val maxPerDrive: Int = MAX_PER_DRIVE,
) {
    private val counts = mutableMapOf<String, Int>()
    private val pending = mutableListOf<SpeechRequest>()
    private val muted = mutableSetOf<String>()

    /** 운전자가 "그만"이라고 한 경고. 이번 주행에서 다시 먼저 말하지 않는다. */
    fun mute(lampId: String) {
        muted += lampId
        pending.removeAll { it.utterance.lampId == lampId }
    }

    fun isMuted(lampId: String) = lampId in muted

    /** 이번 주행에서 "그만"을 고른 경고 */
    fun mutedLampIds(): Set<String> = muted.toSet()

    fun unmute(lampId: String) { muted -= lampId }

    fun unmuteAll() { muted.clear() }

    /**
     * 안내 음성 끄기(설정). 꺼져 있으면 급하지 않은 문장은 말하지 않고 버린다.
     * 즉시 정차 경고와 대피 문장, 운전자가 직접 고른 다시 듣기와 Gleo 답(급한 요청과 bypassCap)은 끈 상태에서도 말한다.
     */
    var voiceEnabled: Boolean = true

    val pendingCount: Int get() = pending.size
    fun pendingRequests(): List<SpeechRequest> = pending.toList()

    fun spokenCount(capKey: String): Int = counts[capKey] ?: 0

    fun spokenCountForLamp(lampId: String): Int = spokenCount("${Utterance.Kind.WARNING}:$lampId")

    fun onDriveStart() {
        counts.clear()
        pending.clear()
        muted.clear()
    }

    fun decide(req: SpeechRequest, ctx: AudioContext, nowMs: Long): Decision {
        if (req.capped && spokenCount(req.capKey) >= maxPerDrive) return Decision.Drop(DROP_LIMIT)
        if (!req.bypassCap && req.utterance.lampId != null && req.utterance.lampId in muted) return Decision.Drop(DROP_MUTED)
        if (!voiceEnabled && !req.urgent && !req.bypassCap) return Decision.Drop(DROP_VOICE_OFF)
        if (ctx.reversing) return Decision.ScreenOnly
        if (req.urgent) return Decision.SpeakNow
        if (ctx.gleoSpeaking || ctx.otherAudioBusy) return Decision.WaitAudio
        val near = (ctx.nextTurnMeters?.let { it < nearTurnMeters } ?: false) || ctx.braking
        if (!near) return Decision.SpeakNow
        val deadline = req.createdAtMs + maxDeferMs
        return if (nowMs >= deadline) Decision.SpeakNow else Decision.Defer(deadline)
    }

    /** 요청을 줄에 넣는다. 이미 횟수를 다 썼거나 같은 문장이 줄에 있으면 넣지 않는다. */
    fun submit(req: SpeechRequest): Boolean {
        if (req.capped && spokenCount(req.capKey) >= maxPerDrive) return false
        if (pending.any { it.capKey == req.capKey }) return false
        pending += req
        return true
    }

    /** 경고가 꺼지면 줄에 남은 같은 경고 문장을 지운다. */
    fun cancelLamp(lampId: String) {
        pending.removeAll { it.utterance.lampId == lampId }
    }

    fun cancelKind(kind: Utterance.Kind) {
        pending.removeAll { it.utterance.kind == kind }
    }

    /**
     * 지금 말할 문장 하나를 고른다. 말하는 사람은 하나라 한 번에 하나만 돌려준다.
     * 급한 요청 먼저, 같은 급이면 갈래 순, 그다음 먼저 들어온 순.
     */
    fun tick(nowMs: Long, ctx: AudioContext): SpeechRequest? {
        pending.removeAll { decide(it, ctx, nowMs) is Decision.Drop }
        val ready = pending.filter { decide(it, ctx, nowMs) == Decision.SpeakNow }
        val pick = ready.minWithOrNull(
            compareBy<SpeechRequest>({ !it.urgent }, { it.tier?.let(LampCatalog::urgency) ?: 3 }, { it.createdAtMs })
        ) ?: return null
        pending.remove(pick)
        counts[pick.capKey] = spokenCount(pick.capKey) + 1
        return pick
    }

    companion object {
        /** 설계 값: 다음 회전까지 이 거리 미만이면 "회전이 가깝다"로 본다. */
        const val NEAR_TURN_METERS = 300
        /** ISO 15006: 급하지 않은 안내는 최대 10초 미룬다. */
        const val MAX_DEFER_MS = 10_000L
        /** 같은 경고는 한 운전에 3번까지(V1). 즉시 정차 경고는 예외. */
        const val MAX_PER_DRIVE = 3
        /** 설계 값: 바로 서야 하는 경고를 되풀이하는 간격. */
        const val STOP_NOW_REPEAT_MS = 60_000L
        const val DROP_LIMIT = "LIMIT"
        const val DROP_MUTED = "MUTED"
        const val DROP_VOICE_OFF = "VOICE_OFF"
        /** 지금 세워야 하는 경고 앞 알림음(Z1 4-1) */
        const val CHIME_MS = 220
        const val CHIME_HZ = 1_100
        /** 후진 중 화면 글 표시 시간(Z1 1-5) */
        const val SCREEN_ONLY_MS = 3_000L
    }
}
