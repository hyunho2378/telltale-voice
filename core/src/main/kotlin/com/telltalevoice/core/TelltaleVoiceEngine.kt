package com.telltalevoice.core

/** 화면 버튼. 주행 중 화면은 1개에서 2개만 쓴다. */
enum class ButtonAction(val label: String) {
    CALL_EX_TOW("긴급견인 전화"),
    CALL_INSURANCE("보험사 긴급출동"),
    ADD_REST_AREA("휴게소 들르기"),
    REPEAT("다시 듣기"),
}

sealed interface UiState {
    data object Idle : UiState

    /** 주행 중 화면. lines 는 3줄 이하, buttons 는 1개에서 2개. */
    data class Driving(val lampId: String, val tier: Tier, val lines: List<String>, val buttons: List<ButtonAction>) : UiState

    /** 고속도로 대피 화면(차가 선 후). */
    data class Evacuation(val step: EvacuationStep, val lines: List<String>, val buttons: List<ButtonAction>) : UiState

    /** 정차 후 화면: 원인(설명서 원문), 경고 이력, 서비스센터, 예약 QR 자리. */
    data class ParkedDetail(val items: List<DetailItem>, val arrived: Boolean) : UiState
}

data class DetailItem(
    val lamp: WarningLamp,
    val logLines: List<String>,
    val stillOn: Boolean,
    /** 켜질 때 판정 근거(없으면 null) */
    val basis: DecisionBasis? = null,
)

/**
 * 경고등 음성 안내의 중심. 안드로이드 없이 돈다.
 * 앱은 차 신호를 넣고(on* 함수), 일정 간격으로 tick 을 불러 말할 문장을 받고, uiState 로 화면을 그린다.
 */
class TelltaleVoiceEngine(
    private val log: WarningLog = WarningLog(),
    val scheduler: SpeechScheduler = SpeechScheduler(),
) {
    private val resolver = TelltaleResolver()
    val evacuation = EvacuationMachine()

    var route: RouteContext = RouteContext()
        private set
    var audio: AudioContext = AudioContext()
        private set
    var body: BodySignals = BodySignals()
        private set
    private var directBody = BodySignals()
    private var telltaleBody = BodySignals()
    var arrived: Boolean = false
        private set

    /** 켜진 경고등. 값은 상황에 맞춘 경고등(갈래와 음성 문장이 판정에 따라 바뀜) */
    private val activeLamps = linkedMapOf<String, WarningLamp>()
    /** 이번 주행에서 경고등마다 마지막 판정 근거 */
    private val bases = linkedMapOf<String, DecisionBasis>()
    var lastBasis: DecisionBasis? = null
        private set
    /** 앱이 공공데이터로 찾은 가까운 장소 */
    var nearby: NearbyPlaces = NearbyPlaces()
        private set
    /** 마지막으로 말한 요청(다시 듣기용) */
    var lastSpoken: SpeechRequest? = null
        private set
    /** 주행 중에 고른 다시 듣기. 정차 후 한 번 말한다. */
    private var repeatAfterStop: SpeechRequest? = null
    var ignitionOn: Boolean = false
        private set
    private var preTripSignals = PreTripSignals()
    private var lastReadings: List<TelltaleReading> = emptyList()
    /** 차 신호로 경고등 목록을 한 번이라도 받았는지(출발 전 점검의 꺼짐 판단) */
    private var telltalesReceived = false
    private var lastBrakeFluid: FluidLevel = FluidLevel.UNKNOWN
    private var preTripSpoken = false
    /** 이번 주행에서 경고 안내(WARNING)를 말한 경고등 */
    private val warnedLampIds = mutableSetOf<String>()
    /** 줄에 넣은 출발 전 점검 문장이 가리키는 경고등(말하기 직전에 다시 확인) */
    private var preTripLampId: String? = null
    private val announcedAfterArrival = mutableSetOf<String>()
    private val lastStopNowSpokenAt = mutableMapOf<String, Long>()
    private val drivenLampIds = linkedSetOf<String>()
    private var arrivalAnnounced = false

    val warningLog: WarningLog get() = log
    fun activeLampIds(): List<String> = activeLamps.keys.toList()

    fun onDriveStart(nowMs: Long) {
        resolver.resetDriveCycle()
        scheduler.onDriveStart()
        evacuation.reset()
        activeLamps.clear()
        bases.clear()
        lastBasis = null
        lastSpoken = null
        repeatAfterStop = null
        preTripSpoken = false
        warnedLampIds.clear()
        preTripLampId = null
        announcedAfterArrival.clear()
        lastStopNowSpokenAt.clear()
        drivenLampIds.clear()
        arrived = false
        arrivalAnnounced = false
    }

    fun onRoute(next: RouteContext) {
        route = next
        audio = audio.copy(nextTurnMeters = next.nextTurnMeters)
    }

    fun onAudio(gleoSpeaking: Boolean, otherAudioBusy: Boolean = false) {
        audio = audio.copy(gleoSpeaking = gleoSpeaking, otherAudioBusy = otherAudioBusy)
    }

    fun onTelltales(readings: List<TelltaleReading>, brakeFluid: FluidLevel, nowMs: Long) {
        lastReadings = readings
        lastBrakeFluid = brakeFluid
        telltalesReceived = true
        for (change in resolver.update(readings, brakeFluid)) {
            if (change.on) lampOn(change.lamp, nowMs) else lampOff(change.lamp, nowMs)
        }
        telltaleBody = BodySignals(
            hazardOn = TelltaleResolver.hazardOn(readings),
            trunkOpen = TelltaleResolver.trunkOpen(readings),
            driverDoorOpen = TelltaleResolver.doorOpen(readings),
        )
        applyBody(nowMs)
    }

    /** 기어(CarPropertyManager)와 문, 트렁크 위치(Pleos Door)를 넣는다. 비상등은 경고등 목록에서 읽는다. */
    fun onBody(next: BodySignals, nowMs: Long) {
        directBody = next
        applyBody(nowMs)
    }

    private fun applyBody(nowMs: Long) {
        val merged = BodySignals(
            gear = directBody.gear,
            hazardOn = directBody.hazardOn || telltaleBody.hazardOn,
            trunkOpen = directBody.trunkOpen || telltaleBody.trunkOpen,
            driverDoorOpen = directBody.driverDoorOpen || telltaleBody.driverDoorOpen,
        )
        if (merged == body) return
        val wasPark = body.gear == Gear.PARK
        // 기어가 P 로 바뀌는 순간 직전 차속은 믿지 않는다. Pleos 에뮬레이터 실측(2026. 11. 15.): 기어 P 는 즉시 오지만 차속 0 은 차속 콜백 주기(약 2.1초) 때문에
        // 약 0.84초 늦게 와서, 그 사이 마지막 차속(시속 40)으로 주행 중 화면이 남았다. P 로 들어올 때 직전 차속이 시속 5km 를 넘으면 차속을 모름(null)으로 두고 다음 차속 신호를 다시 따른다.
        if (!wasPark && merged.gear == Gear.PARK) dropStaleSpeed()
        body = merged
        audio = audio.copy(reversing = merged.gear == Gear.REVERSE)
        evacuation.onSignals(merged)?.let { submitEvac(it, nowMs) }
        if (!wasPark && merged.gear == Gear.PARK && arrived) announceArrival(nowMs)
        maybePreTrip(nowMs)
    }

    /** 가까운 장소(앱이 위치가 바뀔 때 넣는다). 다음 판정부터 쓴다. */
    fun onNearby(next: NearbyPlaces) { nearby = next }

    /** 시동. 꺼짐에서 켜짐으로 바뀌면 새 운전을 시작한다. */
    fun onIgnition(on: Boolean, nowMs: Long) {
        if (on && !ignitionOn) onDriveStart(nowMs)
        ignitionOn = on
        maybePreTrip(nowMs)
    }

    /** 출발 전 점검 신호(엔진 오일과 와셔액, 연료 비율, 타이어 공기압). 브레이크액과 경고등은 onTelltales 값을 쓴다. */
    fun onPreTripSignals(next: PreTripSignals, nowMs: Long) {
        preTripSignals = next
        maybePreTrip(nowMs)
    }

    /** 출발 전 점검 다섯 줄. */
    fun preTrip(): List<PreTripItem> = PreTrip.items(
        preTripSignals.copy(
            brakeFluid = if (lastBrakeFluid != FluidLevel.UNKNOWN) lastBrakeFluid else preTripSignals.brakeFluid,
            telltalesReceived = telltalesReceived,
        ),
        lastReadings,
    )

    /** 시동 ON 이고 기어 P 일 때 점검 필요 항목의 설명서 문장 하나를 한 운전에 한 번 말한다. */
    private fun maybePreTrip(nowMs: Long) {
        if (preTripSpoken || !ignitionOn || body.gear != Gear.PARK) return
        val items = preTrip()
        val warned = warnedLampIds + pendingWarningLampIds()
        val u = PreTrip.utterance(items, warned) ?: return
        if (scheduler.submit(SpeechRequest(u, null, nowMs))) {
            preTripSpoken = true
            preTripLampId = PreTrip.speakItem(items, warned)?.let { k -> items.first { it.kind == k }.lampId }
        }
    }

    private fun pendingWarningLampIds(): Set<String> =
        scheduler.pendingRequests().filter { it.utterance.kind == Utterance.Kind.WARNING }.mapNotNull { it.utterance.lampId }.toSet()

    /** 이번 주행에서 경고 안내를 말한 경고등 id */
    fun warnedLamps(): Set<String> = warnedLampIds.toSet()

    private fun dropStalePreTrip() {
        val id = preTripLampId?.takeIf { it in PreTrip.DEDUP_LAMP_IDS } ?: return
        if (id in warnedLampIds || id in pendingWarningLampIds()) {
            scheduler.cancelKind(Utterance.Kind.PRE_TRIP)
            preTripLampId = null
        }
    }

    /** 판정 근거: 켜진 경고등 중 가장 급한 것, 없으면 마지막 판정. */
    fun basis(): DecisionBasis? = mostUrgent()?.let { bases[it.id] } ?: lastBasis

    fun basisFor(lampId: String): DecisionBasis? = bases[lampId]

    /** 기록 목록(최근 순). */
    fun logRows(nowMs: Long): List<LogRow> = log.rows(nowMs)

    /**
     * 다시 듣기. 마지막 안내를 다시 말한다.
     * - 즉시 정차 경고와 대피 문장(급한 요청)은 횟수 상한과 "그만"을 넘어 바로 말한다
     * - 나머지는 정차 후 화면 허용 상태면 바로, 달리는 중이면 정차 후에 한 번 더 말한다
     * - 마지막 안내가 없으면 말하지 않고 false
     */
    fun repeatLast(nowMs: Long): Boolean {
        val last = lastSpoken ?: return false
        val again = last.copy(createdAtMs = nowMs, bypassCap = true)
        if (last.urgent || parkedForScreen()) return scheduler.submit(again)
        repeatAfterStop = again
        return true
    }

    /** 정차 후 말하려고 기다리는 다시 듣기가 있는지. */
    val repeatWaiting: Boolean get() = repeatAfterStop != null

    /** Gleo 질문 "지금 켜진 경고등 알려 줘"의 답. 켜진 경고등이 있으면 가장 급한 경고 문장. */
    fun answerActiveLamps(nowMs: Long): Utterance {
        val lamp = mostUrgent()
        val text = lamp?.let { SpeechBuilder.warning(it).text } ?: ANSWER_NO_LAMP
        val u = Utterance(Utterance.Kind.ANSWER, null, text, SpeechBuilder.toTts(text), chime = lamp?.tier == Tier.STOP_NOW)
        scheduler.submit(SpeechRequest(u, null, nowMs, bypassCap = true))
        return u
    }

    /** Gleo 질문 "가까운 서비스센터 알려 줘"의 답. 서비스센터를 모르면 null(말하지 않음). */
    fun answerRepairShop(nowMs: Long): Utterance? {
        val shop = nearby.repairShop ?: return null
        val text = "가까운 서비스센터는 ${shop.name}, ${SpeechBuilder.formatDistance(shop.meters)}입니다."
        val u = Utterance(Utterance.Kind.ANSWER, null, text, SpeechBuilder.toTts(text))
        scheduler.submit(SpeechRequest(u, null, nowMs, bypassCap = true))
        return u
    }

    /** Pleos Brake.registerBrakePedalStatus. 밟는 중이면 급하지 않은 말을 미룬다(Z1 1-3). */
    /** 주차 브레이크(CarPropertyManager PARKING_BRAKE_ON). 정차 후 화면 허용 판단에 쓴다. */
    var parkingBrakeOn: Boolean = false
        private set

    fun onParkingBrake(on: Boolean) {
        // 주차 브레이크 체결도 기어 P 와 같은 정차 신호다(Pleos 주행 규제 해제 기준). 체결 순간 직전 차속은 믿지 않는다.
        if (on && !parkingBrakeOn) dropStaleSpeed()
        parkingBrakeOn = on
    }

    /**
     * 정차 신호(기어 P, 주차 브레이크 체결)가 올 때 지난 차속을 버린다.
     * 차속 신호가 약 2.1초 간격으로 오므로 마지막 값(시속 40)을 그대로 믿으면 정차 신호 후에도 주행 중으로 남는다.
     * 모름(null)으로 두면 다음 차속 신호가 올 때 다시 그 값을 따른다.
     */
    private fun dropStaleSpeed() {
        if ((speedKmh ?: 0.0) > PARKED_MAX_KMH) speedKmh = null
    }

    /** 차 속도(km/h). 모르면 null. 시속 5km 초과면 정차 후 화면을 막는다. */
    var speedKmh: Double? = null
        private set

    fun onSpeed(kmh: Double?) { speedKmh = kmh }

    /**
     * 정차 후 화면 허용. Pleos 개발 문서 주행 규제 흐름: 시속 5km 초과면 규제, 그 아래에서 기어 P 또는 주차 브레이크 체결이면 해제.
     * 속도를 모르면 기어 P 또는 주차 브레이크만으로 판단한다.
     */
    fun parkedForScreen(): Boolean {
        val slow = speedKmh?.let { it <= PARKED_MAX_KMH } ?: true
        return slow && (body.gear == Gear.PARK || parkingBrakeOn)
    }

    fun onBrakePedal(pressed: Boolean) {
        audio = audio.copy(braking = pressed)
    }

    /** 운전자가 "그만"을 고른 경고(Z1 3-3). */
    fun mute(lampId: String) = scheduler.mute(lampId)
    fun unmute(lampId: String) = scheduler.unmute(lampId)
    fun unmuteAll() = scheduler.unmuteAll()
    fun mutedLampIds(): Set<String> = scheduler.mutedLampIds()

    /** 안내 음성 켜기와 끄기. 끄면 즉시 정차 경고와 대피 문장, 직접 고른 다시 듣기만 말한다. 화면 상태는 그대로. */
    var voiceEnabled: Boolean
        get() = scheduler.voiceEnabled
        set(v) { scheduler.voiceEnabled = v }

    /** NaviHelper onDestinationArrived. */
    fun onArrived(nowMs: Long) {
        arrived = true
        if (body.gear == Gear.PARK) announceArrival(nowMs)
    }

    /** 일정 간격(앱은 0.5초)으로 부른다. 말할 문장이 있으면 돌려준다. */
    fun tick(nowMs: Long): Utterance? {
        repeatStopNow(nowMs)
        repeatAfterStop?.let { if (parkedForScreen()) { scheduler.submit(it.copy(createdAtMs = nowMs)); repeatAfterStop = null } }
        dropStalePreTrip()
        val req = scheduler.tick(nowMs, audio) ?: return null
        if (req.utterance.kind == Utterance.Kind.WARNING) req.utterance.lampId?.let {
            warnedLampIds += it
            log.markSpoken(it, req.utterance.text)
        }
        if (req.utterance.kind == Utterance.Kind.PRE_TRIP) preTripLampId = null
        req.utterance.lampId?.let { if (req.tier == Tier.STOP_NOW) lastStopNowSpokenAt[it] = nowMs }
        if (req.utterance.kind != Utterance.Kind.ANSWER) lastSpoken = req.copy(bypassCap = false)
        return req.utterance
    }

    /** "휴게소 들르기" 버튼. 앱은 돌려받은 휴게소를 NaviHelper.addWaypoint 로 넣는다. */
    fun restAreaForWaypoint(): TurnItem? = route.nextRestArea

    fun repeatCurrent(nowMs: Long): Boolean {
        val lamp = mostUrgent() ?: return false
        return scheduler.submit(SpeechRequest(SpeechBuilder.warning(lamp), lamp.tier, nowMs))
    }

    private fun lampOn(catalogLamp: WarningLamp, nowMs: Long) {
        val decision = DecisionRules.decide(catalogLamp, route, speedKmh, nearby, nowMs)
        val lamp = decision.lamp
        activeLamps[lamp.id] = lamp
        bases[lamp.id] = decision.basis
        lastBasis = decision.basis
        drivenLampIds += lamp.id
        log.onLampOn(lamp.id, nowMs, snapshot(), lamp.tier)
        when (lamp.tier) {
            Tier.STOP_NOW -> {
                scheduler.submit(SpeechRequest(SpeechBuilder.warning(lamp), lamp.tier, nowMs))
                evacuation.onStopWarning(true, route.onHighway)?.let { submitEvac(it, nowMs) }
            }
            Tier.REST_AREA -> {
                scheduler.submit(SpeechRequest(SpeechBuilder.warning(lamp), lamp.tier, nowMs))
                route.nextRestArea?.let {
                    scheduler.submit(SpeechRequest(SpeechBuilder.restAreaDistance(it.distanceMeters.toInt()), lamp.tier, nowMs + 1))
                }
            }
            Tier.AFTER_ARRIVAL -> if (announcedAfterArrival.add(lamp.id)) {
                scheduler.submit(SpeechRequest(SpeechBuilder.warning(lamp), lamp.tier, nowMs))
            }
        }
    }

    private fun lampOff(catalogLamp: WarningLamp, nowMs: Long) {
        val lamp = activeLamps.remove(catalogLamp.id) ?: catalogLamp
        log.onLampOff(lamp.id, nowMs, snapshot())
        scheduler.cancelLamp(lamp.id)
        lastStopNowSpokenAt.remove(lamp.id)
        if (lamp.tier == Tier.STOP_NOW && activeLamps.values.none { it.tier == Tier.STOP_NOW }) {
            evacuation.onStopWarning(false, route.onHighway)
        }
        if (lamp.tier == Tier.REST_AREA && activeLamps.values.none { it.tier == Tier.REST_AREA }) {
            scheduler.cancelKind(Utterance.Kind.REST_AREA_DISTANCE)
        }
    }

    private fun repeatStopNow(nowMs: Long) {
        if (body.gear == Gear.PARK) return
        for ((id, at) in lastStopNowSpokenAt.toMap()) {
            val lamp = activeLamps[id] ?: continue
            if (nowMs - at >= SpeechScheduler.STOP_NOW_REPEAT_MS) {
                if (scheduler.submit(SpeechRequest(SpeechBuilder.warning(lamp), lamp.tier, nowMs))) {
                    lastStopNowSpokenAt[id] = nowMs
                }
            }
        }
    }

    private fun submitEvac(t: EvacuationTransition, nowMs: Long) {
        scheduler.cancelKind(Utterance.Kind.EVACUATION)
        t.utterance?.let { scheduler.submit(SpeechRequest(it, null, nowMs)) }
    }

    private fun announceArrival(nowMs: Long) {
        if (drivenLampIds.isEmpty() || arrivalAnnounced) return
        arrivalAnnounced = true
        scheduler.submit(SpeechRequest(SpeechBuilder.arrival(drivenLampIds.size), null, nowMs))
    }

    private fun snapshot() = RouteSnapshot(
        roadLabel = route.roadLabel, destinationName = route.destinationName, onHighway = route.onHighway,
    )

    private fun mostUrgent(): WarningLamp? = activeLamps.values.minByOrNull { LampCatalog.urgency(it.tier) }

    fun uiState(): UiState {
        val step = evacuation.step
        if (step in EVAC_SCREEN_STEPS) {
            val lines = listOfNotNull(
                SpeechBuilder.evacuation(step)?.text,
                "긴급견인 ${EvacuationMachine.EX_CALL_CENTER}",
            )
            return UiState.Evacuation(step, lines, listOf(ButtonAction.CALL_EX_TOW, ButtonAction.CALL_INSURANCE))
        }
        if (parkedForScreen() && drivenLampIds.isNotEmpty()) {
            val items = drivenLampIds.mapNotNull { LampCatalog.byId(it) }
                .sortedBy { LampCatalog.urgency(it.tier) }
                .map { lamp -> DetailItem(lamp, log.forLamp(lamp.id).map(log::describe), lamp.id in activeLamps, bases[lamp.id]) }
            return UiState.ParkedDetail(items, arrived)
        }
        val lamp = mostUrgent() ?: return UiState.Idle
        val w = SpeechBuilder.warning(lamp)
        val first = w.text.substringBefore(". ") + "."
        val second = w.text.substringAfter(". ")
        return when (lamp.tier) {
            Tier.STOP_NOW -> UiState.Driving(lamp.id, lamp.tier, listOf(first, second),
                listOf(ButtonAction.CALL_EX_TOW, ButtonAction.CALL_INSURANCE))
            Tier.REST_AREA -> {
                val rest = route.nextRestArea
                val lines = listOfNotNull(first, second, rest?.let { SpeechBuilder.restAreaDistance(it.distanceMeters.toInt()).text })
                val buttons = if (rest != null) listOf(ButtonAction.ADD_REST_AREA) else listOf(ButtonAction.REPEAT)
                UiState.Driving(lamp.id, lamp.tier, lines, buttons)
            }
            Tier.AFTER_ARRIVAL -> UiState.Driving(lamp.id, lamp.tier, listOf(first, second), listOf(ButtonAction.REPEAT))
        }
    }

    companion object {
        const val PARKED_MAX_KMH = 5.0
        /** Gleo 질문에 켜진 경고등이 없을 때 답(화면 문구와 같음) */
        const val ANSWER_NO_LAMP = "현재 켜진 경고등이 없습니다."
        const val MAX_DRIVING_LINES = 3
        const val MAX_DRIVING_BUTTONS = 2
        val EVAC_SCREEN_STEPS = setOf(EvacuationStep.ASK_HAZARD, EvacuationStep.ASK_TRUNK, EvacuationStep.ASK_EXIT, EvacuationStep.OUTSIDE)
    }
}
