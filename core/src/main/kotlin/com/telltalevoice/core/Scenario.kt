package com.telltalevoice.core

/** 음성 미리 듣기 장면 한 단계. atMs 는 시나리오 시작부터 흐른 시간. */
sealed interface ScenarioEvent {
    data object DriveStart : ScenarioEvent
    data class Telltales(val readings: List<TelltaleReading>, val brakeFluid: FluidLevel = FluidLevel.NORMAL) : ScenarioEvent
    data class Body(val signals: BodySignals) : ScenarioEvent
    data class Route(val context: RouteContext) : ScenarioEvent
    data class Audio(val gleoSpeaking: Boolean) : ScenarioEvent
    /** 차 속도(km/h) */
    data class Speed(val kmh: Double?) : ScenarioEvent
    data object Arrive : ScenarioEvent
}

data class ScenarioStep(val atMs: Long, val event: ScenarioEvent, val note: String)

data class Scenario(val id: String, val title: String, val summary: String, val steps: List<ScenarioStep>) {
    val durationMs: Long get() = steps.maxOf { it.atMs } + 5_000
}

data class SpokenAt(val atMs: Long, val utterance: Utterance)

/** 장면을 엔진에 차례로 넣고 tick 을 돌려 말한 문장을 모은다. 앱의 가짜 어댑터와 단위 시험이 함께 쓴다. */
class ScenarioRunner(val engine: TelltaleVoiceEngine = TelltaleVoiceEngine(), private val tickMs: Long = TICK_MS) {
    private var lastTelltales: List<TelltaleReading> = emptyList()
    private var lastBrake: FluidLevel = FluidLevel.NORMAL

    fun apply(e: ScenarioEvent, nowMs: Long) {
        when (e) {
            ScenarioEvent.DriveStart -> engine.onDriveStart(nowMs)
            is ScenarioEvent.Telltales -> {
                lastTelltales = e.readings; lastBrake = e.brakeFluid
                engine.onTelltales(e.readings, e.brakeFluid, nowMs)
            }
            is ScenarioEvent.Body -> engine.onBody(e.signals, nowMs)
            is ScenarioEvent.Route -> engine.onRoute(e.context)
            is ScenarioEvent.Audio -> engine.onAudio(e.gleoSpeaking)
            is ScenarioEvent.Speed -> engine.onSpeed(e.kmh)
            ScenarioEvent.Arrive -> engine.onArrived(nowMs)
        }
    }

    fun run(s: Scenario, startMs: Long = 0L): List<SpokenAt> {
        val out = mutableListOf<SpokenAt>()
        val steps = s.steps.sortedBy { it.atMs }
        var i = 0
        var t = 0L
        while (t <= s.durationMs) {
            while (i < steps.size && steps[i].atMs <= t) { apply(steps[i].event, startMs + t); i++ }
            engine.tick(startMs + t)?.let { out += SpokenAt(t, it) }
            t += tickMs
        }
        return out
    }

    companion object { const val TICK_MS = 500L }
}

/** 음성 미리 듣기 장면. 화면의 장면 목록과 단위 시험이 같은 값을 쓴다. */
object Scenarios {
    private fun tt(vararg r: Pair<String, LampState>, color: LampColor = LampColor.UNKNOWN) =
        r.map { TelltaleReading(it.first, it.second, color) }

    private val hazard = listOf(
        TelltaleReading("TURN_SIGNAL_LEFT", LampState.BLINK), TelltaleReading("TURN_SIGNAL_RIGHT", LampState.BLINK),
    )

    private fun highway(nextTurnM: Double, restAreaM: Double?) = RouteContext(
        turns = listOfNotNull(
            TurnItem("TURN_KEEP_R", nextTurnM, "분기점"),
            TurnItem("TURN_TOLL", 25_000.0, "요금소"),
            restAreaM?.let { TurnItem("TURN_ENTER_SERVICEAREA", it, "휴게소") },
        ),
        guiding = true, destinationName = "부산", roadLabel = "경부고속도로",
    )

    val BRAKE_HIGHWAY = Scenario(
        id = "brake_highway", title = "브레이크 경고, 고속도로",
        summary = "고속도로에서 브레이크액이 부족해진 후 갓길에 서고 비상등, 트렁크, 문 순서로 대피",
        steps = listOf(
            ScenarioStep(0, ScenarioEvent.DriveStart, "시동"),
            ScenarioStep(0, ScenarioEvent.Body(BodySignals(gear = Gear.DRIVE)), "기어 D"),
            ScenarioStep(0, ScenarioEvent.Speed(110.0), "시속 110km"),
            ScenarioStep(0, ScenarioEvent.Route(highway(4_000.0, 18_000.0)), "고속도로 길 안내"),
            ScenarioStep(2_000, ScenarioEvent.Telltales(emptyList(), FluidLevel.LOW), "브레이크액 부족"),
            ScenarioStep(20_000, ScenarioEvent.Speed(0.0), "정지"),
            ScenarioStep(20_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK)), "갓길 정차, 기어 P"),
            ScenarioStep(26_000, ScenarioEvent.Telltales(hazard, FluidLevel.LOW), "비상등 켬"),
            ScenarioStep(32_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK, trunkOpen = true)), "트렁크 엶"),
            ScenarioStep(38_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK, trunkOpen = true, driverDoorOpen = true)), "운전석 문 엶"),
        ),
    )

    val LOW_TIRE = Scenario(
        id = "low_tire", title = "저압 타이어 경고",
        summary = "회전 직전에 켜진 저압 경고를 회전 후에 말하고, 다음 휴게소 거리를 알린 후 휴게소에 정차",
        steps = listOf(
            ScenarioStep(0, ScenarioEvent.DriveStart, "시동"),
            ScenarioStep(0, ScenarioEvent.Body(BodySignals(gear = Gear.DRIVE)), "기어 D"),
            ScenarioStep(0, ScenarioEvent.Route(highway(200.0, 12_000.0)), "분기점 200미터 앞, 휴게소 12킬로미터"),
            ScenarioStep(1_000, ScenarioEvent.Telltales(tt("TPMS" to LampState.ON)), "저압 타이어 경고등 켜짐"),
            ScenarioStep(6_000, ScenarioEvent.Route(highway(3_000.0, 11_800.0)), "분기점 지남"),
            ScenarioStep(40_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK)), "휴게소 정차"),
        ),
    )

    val ENGINE_ARRIVAL = Scenario(
        id = "engine_arrival", title = "엔진 경고, 도착 후 점검",
        summary = "Gleo 가 말하는 동안 기다렸다 한 번만 말하고, 켜졌다 꺼진 기록을 도착 후 화면에 띄움",
        steps = listOf(
            ScenarioStep(0, ScenarioEvent.DriveStart, "시동"),
            ScenarioStep(0, ScenarioEvent.Body(BodySignals(gear = Gear.DRIVE)), "기어 D"),
            ScenarioStep(0, ScenarioEvent.Speed(70.0), "시속 70km"),
            ScenarioStep(0, ScenarioEvent.Route(RouteContext(listOf(TurnItem("TURN_RIGHT", 1_500.0)), true, "강릉 시내", "국도 7호선")), "일반 도로 길 안내"),
            ScenarioStep(4_000, ScenarioEvent.Audio(true), "Gleo 말하는 중"),
            ScenarioStep(5_000, ScenarioEvent.Telltales(tt("ENGINE" to LampState.ON)), "엔진 경고등 켜짐"),
            ScenarioStep(9_000, ScenarioEvent.Audio(false), "Gleo 끝남"),
            ScenarioStep(25_000, ScenarioEvent.Telltales(emptyList()), "엔진 경고등 꺼짐"),
            ScenarioStep(35_000, ScenarioEvent.Telltales(tt("ENGINE" to LampState.BLINK)), "엔진 경고등 다시 깜빡임"),
            ScenarioStep(45_000, ScenarioEvent.Arrive, "목적지 도착"),
            ScenarioStep(45_000, ScenarioEvent.Speed(0.0), "정지"),
            ScenarioStep(46_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK)), "기어 P"),
        ),
    )

    val ENGINE_OIL = Scenario(
        id = "engine_oil", title = "엔진 오일 압력 경고",
        summary = "일반 도로에서 엔진 오일 압력 경고가 켜져 알림음과 함께 바로 말하고, 차를 세우면 설명서 문장을 화면에 띄움",
        steps = listOf(
            ScenarioStep(0, ScenarioEvent.DriveStart, "시동"),
            ScenarioStep(0, ScenarioEvent.Body(BodySignals(gear = Gear.DRIVE)), "기어 D"),
            ScenarioStep(0, ScenarioEvent.Speed(50.0), "시속 50km"),
            ScenarioStep(0, ScenarioEvent.Route(RouteContext(listOf(TurnItem("TURN_LEFT", 120.0)), true, "시내", "시내 도로")), "좌회전 120미터 앞"),
            ScenarioStep(2_000, ScenarioEvent.Telltales(tt("ENGINE_OIL_PRESSURE" to LampState.ON)), "엔진 오일 압력 경고등 켜짐"),
            ScenarioStep(15_000, ScenarioEvent.Speed(0.0), "정지"),
            ScenarioStep(15_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK)), "도로 옆 정차, 기어 P"),
        ),
    )

    /** 같은 저압 타이어 경고등이 시내 저속에서는 서비스센터까지 서행으로 안내된다(판정 규칙 TIRE_GENERAL, 갈래는 고속도로와 같은 REST_AREA). */
    val LOW_TIRE_CITY = Scenario(
        id = "low_tire_city", title = "저압 타이어 경고(시내)",
        summary = "시내 일반도로 저속 주행 중 저압 경고가 켜져 속도를 낮추라고 말하고, 휴게소 대신 가까운 서비스센터를 안내한 후 정차",
        steps = listOf(
            ScenarioStep(0, ScenarioEvent.DriveStart, "시동"),
            ScenarioStep(0, ScenarioEvent.Body(BodySignals(gear = Gear.DRIVE)), "기어 D"),
            ScenarioStep(0, ScenarioEvent.Speed(30.0), "시속 30km"),
            ScenarioStep(0, ScenarioEvent.Route(RouteContext(listOf(TurnItem("TURN_RIGHT", 800.0)), true, "시내", "시내 도로")), "일반도로 길 안내"),
            ScenarioStep(1_000, ScenarioEvent.Telltales(tt("TPMS" to LampState.ON)), "저압 타이어 경고등 켜짐"),
            ScenarioStep(20_000, ScenarioEvent.Speed(0.0), "정지"),
            ScenarioStep(20_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK)), "도로 옆 정차, 기어 P"),
        ),
    )

    /** 같은 저압 타이어 경고등이 고속도로 시속 120km에서는 속도를 낮춰 앞쪽 휴게소까지(판정 규칙 TIRE_HIGHWAY). */
    val LOW_TIRE_HIGHWAY = Scenario(
        id = "low_tire_highway", title = "저압 타이어 경고(고속도로)",
        summary = "고속도로 시속 120km 주행 중 저압 경고가 켜져 속도를 낮추라고 말하고 다음 휴게소 거리를 알린 후 휴게소에 정차",
        steps = listOf(
            ScenarioStep(0, ScenarioEvent.DriveStart, "시동"),
            ScenarioStep(0, ScenarioEvent.Body(BodySignals(gear = Gear.DRIVE)), "기어 D"),
            ScenarioStep(0, ScenarioEvent.Speed(120.0), "시속 120km"),
            ScenarioStep(0, ScenarioEvent.Route(highway(3_000.0, 12_000.0)), "고속도로, 휴게소 12킬로미터"),
            ScenarioStep(1_000, ScenarioEvent.Telltales(tt("TPMS" to LampState.ON)), "저압 타이어 경고등 켜짐"),
            ScenarioStep(30_000, ScenarioEvent.Speed(0.0), "정지"),
            ScenarioStep(30_000, ScenarioEvent.Body(BodySignals(gear = Gear.PARK)), "휴게소 정차"),
        ),
    )

    /** 음성 미리 듣기 순서: 엔진 오일, 저압 타이어, 엔진 체크, 고속도로 정차 대피 */
    val all = listOf(ENGINE_OIL, LOW_TIRE, ENGINE_ARRIVAL, BRAKE_HIGHWAY)

    /** v2 미리 듣기 장면: 엔진 오일, 저압 타이어(시내), 저압 타이어(고속도로), 엔진 체크, 고속도로 정차 대피 */
    val previews = listOf(ENGINE_OIL, LOW_TIRE_CITY, LOW_TIRE_HIGHWAY, ENGINE_ARRIVAL, BRAKE_HIGHWAY)

    fun byId(id: String) = (all + previews).firstOrNull { it.id == id }

    private val basisCache = mutableMapOf<String, DecisionBasis?>()

    /** 장면을 끝까지 돌렸을 때 첫 경고의 판정 근거. 화면의 장면 카드가 미리 보여 준다. */
    fun basisFor(id: String): DecisionBasis? = synchronized(basisCache) {
        basisCache.getOrPut(id) {
            val s = byId(id) ?: return@getOrPut null
            val runner = ScenarioRunner()
            runner.run(s)
            val first = runner.engine.warningLog.all().minByOrNull { it.onAtMs } ?: return@getOrPut null
            runner.engine.basisFor(first.lampId)
        }
    }
}
