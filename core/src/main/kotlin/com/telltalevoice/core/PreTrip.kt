package com.telltalevoice.core

/** 출발 전 점검 다섯 줄. 화면 순서와 같다. */
enum class PreTripKind(val label: String) {
    TIRE("타이어 공기압"),
    ENGINE_OIL("엔진 오일"),
    BRAKE_FLUID("브레이크액"),
    WASHER("와셔액"),
    FUEL("연료");

    /** 출발 전 점검 상세의 설명서 뜻(2027 그랜저 설명서 원문). 원문이 없으면 null */
    val manualMeaning: String? get() = when (this) {
        TIRE -> ManualGN7.meaning(13)
        ENGINE_OIL -> ManualGN7.ENGINE_OIL_CHECK_MEANING
        BRAKE_FLUID -> ManualGN7.BRAKE_FLUID_CHECK_MEANING
        WASHER -> null
        FUEL -> ManualGN7.meaning(12)
    }

    /** 출발 전 점검 상세의 해야 할 일(2027 그랜저 설명서 원문). 원문이 없으면 빈 목록 */
    val manualActions: List<String> get() = when (this) {
        TIRE -> ManualGN7.actions(13)
        ENGINE_OIL -> ManualGN7.ENGINE_OIL_CHECK_ACTIONS
        BRAKE_FLUID -> ManualGN7.BRAKE_FLUID_CHECK_ACTIONS
        WASHER -> ManualGN7.WASHER_REFILL_ACTIONS
        FUEL -> ManualGN7.actions(12)
    }

    /** 문장을 가져온 설명서 쪽 주소 */
    val manualUrl: String get() = when (this) {
        TIRE, FUEL -> ManualGN7.URL
        ENGINE_OIL -> ManualGN7.ENGINE_OIL_CHECK_URL
        BRAKE_FLUID -> ManualGN7.BRAKE_FLUID_CHECK_URL
        WASHER -> ManualGN7.WASHER_REFILL_URL
    }
}

enum class PreTripStatus { OK, CHECK, UNKNOWN }

/**
 * 출발 전 점검 한 줄의 신호 상태. status 가 UNKNOWN 일 때 화면이 두 경우를 나눠 보일 수 있게 한다.
 * - RECEIVED: 신호를 받아 status 를 정함(OK 또는 CHECK)
 * - WAITING: 아직 신호가 오지 않음(앱을 막 켰거나 서비스 연결 전)
 * - NO_SIGNAL: 이 차량이 그 신호를 내주지 않음(어댑터가 "지원하지 않는 속성" 오류를 받음). Pleos 에뮬레이터 release_connect_v2.1.0 의 와셔액, 연료 비율, 엔진 오일 수준
 */
enum class PreTripSignal { RECEIVED, WAITING, NO_SIGNAL }

/**
 * 출발 전 점검 한 줄.
 * valueText 는 화면에 보일 한 단어 상태("정상", "부족", "저압", "42%" 등). 신호가 없으면 null. 화면은 값이 없을 때 WAITING 이면 "확인 중", NO_SIGNAL 이면 "확인 불가"를 보인다.
 * lampId 는 같은 내용의 설명서 경고등이 있으면 그 id(없으면 null).
 */
data class PreTripItem(
    val kind: PreTripKind,
    val status: PreTripStatus,
    val valueText: String?,
    val lampId: String? = null,
    /** 신호 상태. status 가 OK 나 CHECK 면 늘 RECEIVED, UNKNOWN 이면 WAITING 또는 NO_SIGNAL */
    val signal: PreTripSignal = if (status == PreTripStatus.UNKNOWN) PreTripSignal.WAITING else PreTripSignal.RECEIVED,
)

/** 타이어 한 바퀴. pressure 는 Tire.getTirePressure, lowLimit 는 Tire.getLowTirePressure 값(단위는 SDK 값 그대로). */
data class TireReading(val wheel: String, val pressure: Float?, val lowLimit: Float?)

/** 출발 전 점검 입력. 신호를 받지 못한 칸은 null 또는 UNKNOWN. */
data class PreTripSignals(
    val engineOil: FluidLevel = FluidLevel.UNKNOWN,
    val brakeFluid: FluidLevel = FluidLevel.UNKNOWN,
    /** Safety.hasLowWasherFluidWarning */
    val washerLow: Boolean? = null,
    /** Fuel.getFuelPercent */
    val fuelPercent: Int? = null,
    val tires: List<TireReading> = emptyList(),
    /** 차량이 내주지 않는 신호(어댑터가 지원하지 않는 속성 오류를 받은 항목) */
    val unsupported: Set<PreTripKind> = emptySet(),
    /**
     * 경고등 목록을 한 번이라도 받았는지. Pleos 경고등 목록에는 켜진 경고등만 들어 있어(2026. 11. 14. 실측, 하나도 없으면 빈 목록),
     * 목록을 받은 뒤 TPMS 와 LOW_FUEL 이 없으면 그 경고등은 꺼져 있다고 본다.
     */
    val telltalesReceived: Boolean = false,
)

/**
 * 출발 전 점검 규칙.
 * - 타이어: 경고등 TPMS 가 켜지거나 깜빡이면 점검 필요. 꺼짐이 보이면 정상. 바퀴마다 공기압과 저압 기준이 함께 오고 공기압이 기준보다 낮으면 점검 필요
 * - 엔진 오일과 브레이크액: Pleos OilLevelState. NORMAL 정상, LOW와 CRITICALLY_LOW와 HIGH 점검 필요, ERROR와 UNKNOWN 알 수 없음
 * - 와셔액: hasLowWasherFluidWarning true 면 점검 필요
 * - 연료: 경고등 LOW_FUEL 이 켜지면 점검 필요. 꺼짐이 보이거나 연료 비율이 오면 정상(비율 기준 숫자는 두지 않음)
 * 말하기: 점검 필요 항목 중 설명서 문장이 있는 첫 항목의 문장 하나만 말한다(브레이크액, 타이어, 연료 순). 엔진 오일 수준과 와셔액은 설명서 경고등 문장이 없어 화면에만 둔다.
 */
object PreTrip {
    /** 설명서 문장(LampCatalog 원문에서 그대로 자른 문장). */
    val BRAKE_FLUID_SENTENCE: String = sentenceOf(LampCatalog.BRAKE.manualActions.joinToString(" "), "즉시 브레이크액을 보충하십시오.")
    val TIRE_SENTENCE: String = sentenceOf(LampCatalog.LOW_TIRE_PRESSURE.manualActions.joinToString(" "), "타이어 공기압을 확인한 후 필요 시 공기를 보충하십시오.")
    val FUEL_SENTENCE: String = sentenceOf(LampCatalog.LOW_FUEL.manualActions.joinToString(" "), "연료 부족 경고등이 켜지면 즉시 연료를 보충하십시오.")

    private fun sentenceOf(source: String, sentence: String): String {
        require(source.contains(sentence)) { "설명서 원문에 없는 문장: $sentence" }
        return sentence
    }

    /** 점검 문장과 경고 안내가 같은 항목의 경고등(브레이크액, 저압 타이어, TPMS 이상) */
    val DEDUP_LAMP_IDS = setOf(LampCatalog.BRAKE.id, LampCatalog.LOW_TIRE_PRESSURE.id, LampCatalog.TPMS_FAULT.id)

    private val SPEAK_ORDER = listOf(PreTripKind.BRAKE_FLUID, PreTripKind.TIRE, PreTripKind.FUEL)

    fun items(signals: PreTripSignals, readings: List<TelltaleReading>): List<PreTripItem> {
        fun lampOf(type: String) = readings.firstOrNull { it.type == type }
            ?: if (signals.telltalesReceived) TelltaleReading(type, LampState.OFF) else null
        val tpms = lampOf("TPMS")
        val lowFuel = lampOf("LOW_FUEL")
        return listOf(
            tire(tpms, signals.tires),
            fluid(PreTripKind.ENGINE_OIL, signals.engineOil, null),
            fluid(PreTripKind.BRAKE_FLUID, signals.brakeFluid, LampCatalog.BRAKE.id),
            washer(signals.washerLow),
            fuel(lowFuel, signals.fuelPercent),
        ).map { item ->
            if (item.status == PreTripStatus.UNKNOWN && item.kind in signals.unsupported) item.copy(signal = PreTripSignal.NO_SIGNAL) else item
        }
    }

    private fun tire(tpms: TelltaleReading?, tires: List<TireReading>): PreTripItem {
        val lit = tpms != null && tpms.state != LampState.OFF
        val compared = tires.filter { it.pressure != null && it.lowLimit != null }
        val low = compared.any { it.pressure!! < it.lowLimit!! }
        return when {
            lit && tpms?.state == LampState.BLINK -> PreTripItem(PreTripKind.TIRE, PreTripStatus.CHECK, "점검 필요", LampCatalog.TPMS_FAULT.id)
            lit || low -> PreTripItem(PreTripKind.TIRE, PreTripStatus.CHECK, "저압", LampCatalog.LOW_TIRE_PRESSURE.id)
            tpms != null || compared.isNotEmpty() -> PreTripItem(PreTripKind.TIRE, PreTripStatus.OK, "정상")
            else -> PreTripItem(PreTripKind.TIRE, PreTripStatus.UNKNOWN, null)
        }
    }

    private fun fluid(kind: PreTripKind, level: FluidLevel, lampId: String?): PreTripItem = when (level) {
        FluidLevel.NORMAL -> PreTripItem(kind, PreTripStatus.OK, "정상")
        FluidLevel.LOW -> PreTripItem(kind, PreTripStatus.CHECK, "낮음", lampId)
        FluidLevel.CRITICALLY_LOW -> PreTripItem(kind, PreTripStatus.CHECK, "매우 낮음", lampId)
        FluidLevel.HIGH -> PreTripItem(kind, PreTripStatus.CHECK, "높음")
        FluidLevel.ERROR, FluidLevel.UNKNOWN -> PreTripItem(kind, PreTripStatus.UNKNOWN, null)
    }

    private fun washer(low: Boolean?): PreTripItem = when (low) {
        true -> PreTripItem(PreTripKind.WASHER, PreTripStatus.CHECK, "부족")
        false -> PreTripItem(PreTripKind.WASHER, PreTripStatus.OK, "정상")
        null -> PreTripItem(PreTripKind.WASHER, PreTripStatus.UNKNOWN, null)
    }

    private fun fuel(lamp: TelltaleReading?, percent: Int?): PreTripItem {
        val text = percent?.let { "$it%" }
        return when {
            lamp != null && lamp.state != LampState.OFF -> PreTripItem(PreTripKind.FUEL, PreTripStatus.CHECK, text ?: "부족", LampCatalog.LOW_FUEL.id)
            lamp != null || percent != null -> PreTripItem(PreTripKind.FUEL, PreTripStatus.OK, text ?: "정상")
            else -> PreTripItem(PreTripKind.FUEL, PreTripStatus.UNKNOWN, null)
        }
    }

    /**
     * 말할 한 문장. 설명서 문장이 있는 점검 필요 항목이 없으면 null(화면에만 표시).
     * warnedLampIds 는 이번 주행에서 이미 경고 안내가 나간 경고등 id. 점검 문장과 경고 안내가 같은 항목(브레이크액, 저압 타이어)이면
     * 그 항목의 점검 문장은 말하지 않는다(DEDUP_LAMP_IDS). 연료는 점검 문장과 경고 문장이 달라 그대로 말한다.
     */
    fun sentence(items: List<PreTripItem>, warnedLampIds: Set<String> = emptySet()): String? =
        speakItem(items, warnedLampIds)?.let { kind -> sentenceOf(kind, items) }

    /** 말할 항목. 없으면 null */
    fun speakItem(items: List<PreTripItem>, warnedLampIds: Set<String> = emptySet()): PreTripKind? {
        val skip = warnedLampIds intersect DEDUP_LAMP_IDS
        val check = items.filter { it.status == PreTripStatus.CHECK && it.lampId !in skip }.map { it.kind }.toSet()
        return SPEAK_ORDER.firstOrNull { it in check }
    }

    private fun sentenceOf(kind: PreTripKind, items: List<PreTripItem>): String? {
        return when (kind) {
            PreTripKind.BRAKE_FLUID -> BRAKE_FLUID_SENTENCE
            PreTripKind.TIRE -> if (items.first { it.kind == PreTripKind.TIRE }.lampId == LampCatalog.LOW_TIRE_PRESSURE.id) TIRE_SENTENCE
                else LampCatalog.TPMS_FAULT.manualActions[0]
            PreTripKind.FUEL -> FUEL_SENTENCE
            else -> null
        }
    }

    fun utterance(items: List<PreTripItem>, warnedLampIds: Set<String> = emptySet()): Utterance? = sentence(items, warnedLampIds)?.let {
        Utterance(Utterance.Kind.PRE_TRIP, null, it, SpeechBuilder.toTts(it))
    }
}
