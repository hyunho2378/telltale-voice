package com.telltalevoice.core

/** 말할 문장 한 개. text 는 화면에 보이는 글, ttsText 는 음성 엔진에 넘길 글. */
data class Utterance(
    val kind: Kind,
    val lampId: String?,
    val text: String,
    val ttsText: String,
    /** 앞에 짧은 알림음(220ms, 1,100Hz)을 붙이는지. 지금 세워야 하는 경고만(Z1 4-1) */
    val chime: Boolean = false,
) {
    enum class Kind {
        WARNING, REST_AREA_DISTANCE, EVACUATION, ARRIVAL,
        /** 출발 전 점검(시동 ON, 기어 P)에서 점검 필요 항목이 있을 때 한 번 */
        PRE_TRIP,
        /** Gleo 로 물은 질문의 답 */
        ANSWER,
    }
}

/**
 * 음성 문장 규칙(V1, X1 4-4).
 * 1. 하십시오체로 끝낸다("~습니다.", "~십시오.", "~입니다.")
 * 2. 핵심 낱말 4개 이하
 * 3. 할 행동을 문장 끝에 둔다
 * 4. 첫 안내에서 묻지 않는다(물음표 금지). 묻기는 두 번째 말의 경유지 제안 하나뿐(Z1 3-2)
 * 5. "위험"은 지금 세워야 하는 경고에만(Z1 2-6)
 */
object SpeechBuilder {
    const val MAX_KEYWORDS = 4

    /** 대피 문장(한국도로공사 비트박스 순서, X1 4-1). */
    const val EVAC_HAZARD = "비상등을 켜십시오."
    const val EVAC_TRUNK = "트렁크를 여십시오."
    const val EVAC_EXIT = "차에서 내려 가드레일 밖으로 피하십시오. 함께 탄 분도 가드레일 밖으로 피하십시오."
    const val EVAC_CALL = "가드레일 밖에서 긴급견인을 부르십시오."

    private val formalEndings = listOf("습니다.", "십시오.", "입니다.")

    /** 영문 약어를 한국어 발음으로 바꾼다. 화면 글은 그대로 둔다. */
    private val ttsReplacements = linkedMapOf(
        "ABS" to "에이비에스",
        "ESC" to "이에스씨",
        "TPMS" to "티피엠에스",
        "LED" to "엘이디",
        "EBD" to "이비디",
        "LPG" to "엘피지",
    )

    fun warning(lamp: WarningLamp): Utterance {
        val first = "${withSubjectParticle(lamp.subject)} ${lamp.predicate}."
        val text = "$first ${lamp.action}."
        val tts = toTts("$first ${withPause(lamp.action)}.")
        return Utterance(Utterance.Kind.WARNING, lamp.id, text, tts, chime = lamp.tier == Tier.STOP_NOW)
    }

    /**
     * 쉼 규칙(외부 자문: 3어절은 쉼 없이, 4어절은 2어절 뒤에 짧게 쉬면 명료도가 높다).
     * Pleos TextToSpeech 3.3.1 에는 쉼 길이를 정하는 공개 함수가 없어(javap 확인) 음성 엔진에 넘기는 글에만 쉼표를 넣는다. 화면 글은 그대로 둔다.
     * 행동 문장(지시문)이 정확히 4어절이면 2어절 뒤에 쉼표를 둔다. 3어절 이하와 5어절 이상은 그대로.
     */
    fun withPause(action: String): String {
        val words = action.trim().split(" ")
        if (words.size != PAUSE_WORDS) return action
        // 셋째 어절이 앞말에 붙는 의존 명사(후, 뒤, 전, 때)면 그 어절까지 묶고 쉰다: "서서히 감속 후, 정차하십시오"
        val cut = if (words[2] in BOUND_NOUNS) 3 else 2
        return words.take(cut).joinToString(" ") + ", " + words.drop(cut).joinToString(" ")
    }

    private val BOUND_NOUNS = setOf("후", "뒤", "전", "때")

    const val PAUSE_WORDS = 4

    /** 두 번째 말: 다음 휴게소 거리와 제안 하나(Z1 3-2). 거리는 미터로 받는다. */
    fun restAreaDistance(meters: Int): Utterance {
        val text = "다음 휴게소까지 ${formatDistance(meters)}입니다. 경유지로 넣으시겠습니까?"
        return Utterance(Utterance.Kind.REST_AREA_DISTANCE, null, text, toTts(text))
    }

    fun evacuation(step: EvacuationStep): Utterance? {
        val text = when (step) {
            EvacuationStep.ASK_HAZARD -> EVAC_HAZARD
            EvacuationStep.ASK_TRUNK -> EVAC_TRUNK
            EvacuationStep.ASK_EXIT -> EVAC_EXIT
            EvacuationStep.OUTSIDE -> EVAC_CALL
            else -> return null
        }
        return Utterance(Utterance.Kind.EVACUATION, null, text, text)
    }

    /** 도착 후 화면을 띄울 때 한 번 말한다. */
    fun arrival(count: Int): Utterance {
        val text = "운전 중 켜진 경고등 ${count}개를 화면에 띄웠습니다."
        return Utterance(Utterance.Kind.ARRIVAL, null, text, text)
    }

    /**
     * 거리 표기. 950미터 미만은 100미터 단위(최소 100미터), 9.95킬로미터 미만은 소수 한 자리, 그 위는 정수(반올림).
     */
    fun formatDistance(meters: Int): String {
        require(meters >= 0) { "거리는 0 이상" }
        return when {
            meters < 950 -> "${((meters + 50) / 100 * 100).coerceAtLeast(100)}미터"
            meters < 9_950 -> {
                val tenths = (meters + 50) / 100
                if (tenths % 10 == 0) "${tenths / 10}킬로미터" else "${tenths / 10}.${tenths % 10}킬로미터"
            }
            else -> "${(meters + 500) / 1000}킬로미터"
        }
    }

    fun toTts(text: String): String {
        var out = text
        for ((k, v) in ttsReplacements) out = out.replace(k, v)
        return out
    }

    /** 받침이 있으면 "이", 없으면 "가". 한글이 아니면 영문 약어 읽기 기준으로 고른다. */
    fun withSubjectParticle(noun: String): String {
        val last = noun.trim().last()
        return noun + if (hasFinalConsonant(last)) "이" else "가"
    }

    fun hasFinalConsonant(c: Char): Boolean {
        if (c in '\uAC00'..'\uD7A3') return (c.code - 0xAC00) % 28 != 0
        // 영문 약어 끝 글자 읽기: L(엘), M(엠), N(엔), R(알)은 받침, 나머지는 받침 없음
        return c.uppercaseChar() in setOf('L', 'M', 'N', 'R')
    }

    /** 규칙 검사. 단위 시험과 앱 실행 때 모두 쓴다. 지키지 않은 규칙 이름을 돌려준다. */
    fun violations(lamp: WarningLamp, utterance: Utterance = warning(lamp)): List<String> {
        val out = mutableListOf<String>()
        if (lamp.keywords.size > MAX_KEYWORDS) out += "핵심 낱말 ${lamp.keywords.size}개"
        if (formalEndings.none { utterance.text.endsWith(it) }) out += "하십시오체 끝맺음 아님"
        if (utterance.text.contains('?')) out += "물음"
        if (lamp.tier != Tier.STOP_NOW && utterance.text.contains("위험")) out += "위험 낱말"
        val sentences = utterance.text.split(". ").map { it.trim() }
        if (sentences.size != 2) out += "문장 수 ${sentences.size}"
        val actionSentence = sentences.last()
        val actionWord = lamp.keywords.last()
        if (!actionSentence.removeSuffix(".").endsWith(actionWord)) out += "행동 낱말이 끝에 없음"
        lamp.keywords.forEach { if (!utterance.text.contains(it)) out += "핵심 낱말 빠짐: $it" }
        return out
    }
}
