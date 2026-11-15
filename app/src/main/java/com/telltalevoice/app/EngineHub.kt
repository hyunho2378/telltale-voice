package com.telltalevoice.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.telltalevoice.app.signal.NaviSignals
import com.telltalevoice.app.signal.SignalSink
import com.telltalevoice.app.signal.Speaker
import com.telltalevoice.core.BodySignals
import com.telltalevoice.core.FluidLevel
import com.telltalevoice.core.Gear
import com.telltalevoice.core.RouteContext
import com.telltalevoice.core.Scenario
import com.telltalevoice.core.ScenarioEvent
import com.telltalevoice.core.ScenarioRunner
import com.telltalevoice.core.TelltaleReading
import com.telltalevoice.core.TelltaleVoiceEngine
import com.telltalevoice.core.UiState
import com.telltalevoice.core.publicdata.Geo
import com.telltalevoice.core.publicdata.LatLon
import com.telltalevoice.core.publicdata.PublicDataSource
import com.telltalevoice.core.publicdata.RepairShop
import com.telltalevoice.core.publicdata.RestStop
import com.telltalevoice.core.LampCatalog
import com.telltalevoice.core.Catalog95
import com.telltalevoice.core.CatalogEntry
import com.telltalevoice.core.ColorGroup
import com.telltalevoice.core.DecisionBasis
import com.telltalevoice.core.LogRow
import com.telltalevoice.core.PreTripItem
import com.telltalevoice.core.PreTripKind
import com.telltalevoice.core.PreTripSignals
import com.telltalevoice.core.TireReading
import com.telltalevoice.core.Utterance
import com.telltalevoice.core.publicdata.NearbyFinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 안 단 하나의 엔진. 어댑터 신호는 모두 주 스레드로 옮겨 엔진에 넣는다.
 * 0.5초마다 tick 을 돌려 말할 문장을 Speaker 로 보낸다.
 */
object EngineHub : SignalSink {
    private const val TAG = "EngineHub"
    private val main = Handler(Looper.getMainLooper())
    private val engine = TelltaleVoiceEngine().also {
        // 점등 기록이 부팅 뒤 경과 시간이 아니라 실제 날짜와 시각으로 보이도록 부팅 시각을 더한다. 엔진 시각은 그대로 두어 스케줄에 영향이 없다.
        it.warningLog.wallOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _spoken = MutableStateFlow<String?>(null)
    val spoken: StateFlow<String?> = _spoken.asStateFlow()
    private val _status = MutableStateFlow<List<String>>(emptyList())
    val status: StateFlow<List<String>> = _status.asStateFlow()
    private val _demo = MutableStateFlow<String?>(null)
    val demo: StateFlow<String?> = _demo.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    // V2 화면 데이터(V2_CONTRACT.md)
    private val _basis = MutableStateFlow<DecisionBasis?>(null)
    /** 판정 근거: 켜진 경고등 중 가장 급한 것의 판정, 없으면 마지막 판정. 새 운전(시동)에서 null */
    val basis: StateFlow<DecisionBasis?> = _basis.asStateFlow()
    private val _preTrip = MutableStateFlow<List<PreTripItem>>(engine.preTrip())
    /** 출발 전 점검 다섯 줄(타이어 공기압, 엔진 오일, 브레이크액, 와셔액, 연료) */
    val preTrip: StateFlow<List<PreTripItem>> = _preTrip.asStateFlow()
    private val _logRows = MutableStateFlow<List<LogRow>>(emptyList())
    /** 기록 목록(최근 순) */
    val logRows: StateFlow<List<LogRow>> = _logRows.asStateFlow()
    private val _repeatWaiting = MutableStateFlow(false)
    /** 달리는 중에 다시 듣기를 눌러 정차 뒤 말하려고 기다리는 중 */
    val repeatWaiting: StateFlow<Boolean> = _repeatWaiting.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(true)
    /** 안내 음성 켜짐(기본 true, 기기에 저장). 꺼도 바로 정차 경고와 대피 문장은 말한다 */
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()
    private val _mutedLampIds = MutableStateFlow<Set<String>>(emptySet())
    /** 이번 운전에서 "그만"을 고른 경고등 id */
    val mutedLampIds: StateFlow<Set<String>> = _mutedLampIds.asStateFlow()
    private var prefs: android.content.SharedPreferences? = null

    /** 저장된 설정을 읽는다. GuardService 와 MainActivity 가 부른다(여러 번 불러도 됨). */
    fun attach(context: android.content.Context) = onMain {
        if (prefs != null) return@onMain
        val p = context.applicationContext.getSharedPreferences("telltale_settings", android.content.Context.MODE_PRIVATE)
        prefs = p
        val on = p.getBoolean(KEY_VOICE, true)
        engine.voiceEnabled = on
        _voiceEnabled.value = on
    }

    fun setVoiceEnabled(on: Boolean) = onMain {
        engine.voiceEnabled = on
        _voiceEnabled.value = on
        prefs?.edit()?.putBoolean(KEY_VOICE, on)?.apply()
    }

    fun unmute(lampId: String) = onMain { engine.unmute(lampId); refresh() }
    fun unmuteAll() = onMain { engine.unmuteAll(); refresh() }

    private const val KEY_VOICE = "voice_enabled"

    /** 경고등 도감 95종(SDK 순서) */
    val catalog: List<CatalogEntry> get() = Catalog95.all
    /** 색 묶음별 도감(빨강, 노랑, 초록과 파랑) */
    val catalogByColor: Map<ColorGroup, List<CatalogEntry>> get() = Catalog95.grouped

    var speaker: Speaker? = null
    var navi: NaviSignals? = null
    var publicData: PublicDataSource? = null
        private set
    private var restStops: List<RestStop> = emptyList()
    private var shops: List<RepairShop> = emptyList()
    private val _nearbyShops = MutableStateFlow<List<String>>(emptyList())
    /** 정차 뒤 화면에 보일 가까운 정비소(이름에 "블루핸즈"가 들어간 곳 먼저) */
    val nearbyShops: StateFlow<List<String>> = _nearbyShops.asStateFlow()

    /** 뒤 스레드에서 부른다. 파싱이 끝나면 주 스레드로 옮겨 담는다. */
    fun loadPublicData(src: PublicDataSource) {
        val r = src.restStops(); val s = src.repairShops()
        onMain {
            publicData = src; restStops = r; shops = s
            note("공공데이터: 휴게시설 ${r.size}곳, 정비업체 ${s.size}곳")
        }
    }

    private var telltales: List<TelltaleReading> = emptyList()
    private var brakeFluid = FluidLevel.UNKNOWN
    private var body = BodySignals()
    private var location: Pair<LatLon, Double>? = null
    private var ticking = false
    private val demoRunner = ScenarioRunner(engine)

    private fun now() = SystemClock.elapsedRealtime()

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun refresh() {
        val state = engine.uiState()
        if (state is UiState.ParkedDetail && _ui.value !is UiState.ParkedDetail) updateNearbyShops()
        _ui.value = state
        _basis.value = engine.basis()
        _preTrip.value = engine.preTrip()
        _logRows.value = engine.logRows(now())
        _repeatWaiting.value = engine.repeatWaiting
        _mutedLampIds.value = engine.mutedLampIds()
    }

    private var preTripSignals = PreTripSignals()
    private val tires = linkedMapOf<String, TireReading>()
    private fun pushPreTrip() { engine.onPreTripSignals(preTripSignals.copy(tires = tires.values.toList()), now()); refresh() }

    private var nearbyAt = 0L
    /** 가까운 졸음쉼터와 휴게소, 정비소를 판정 근거용으로 고른다. 정비업체 3만여 곳을 훑어 NEARBY_EVERY_MS 마다 한 번만 한다. */
    private fun updateNearby(force: Boolean = false) {
        val (pos, heading) = location ?: return
        val t = now()
        if (!force && t - nearbyAt < NEARBY_EVERY_MS) return
        nearbyAt = t
        val tire = LampCatalog.LOW_TIRE_PRESSURE.id in engine.activeLampIds()
        engine.onNearby(NearbyFinder.find(pos, heading, restStops, shops, tire))
    }
    private const val NEARBY_EVERY_MS = 15_000L

    private fun updateNearbyShops() {
        val pos = location?.first ?: run { _nearbyShops.value = emptyList(); return }
        _nearbyShops.value = Geo.nearestShops(pos, shops, k = 3).map {
            "${it.place.name}, ${"%.1f".format(it.distanceMeters / 1000)}킬로미터" + (it.place.phone?.let { p -> ", $p" } ?: "")
        }
    }

    fun note(line: String) {
        Log.i(TAG, line)
        _status.value = (_status.value + line).takeLast(12)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val sp = speaker
            if (sp == null || !sp.speaking) {
                engine.tick(now())?.let { u ->
                    _spoken.value = u.text
                    _speaking.value = sp != null
                    sp?.speak(u) { onMain { _speaking.value = false; refresh() } } ?: note("음성 출력 없음: ${u.text}")
                }
            }
            refresh()
            if (ticking) main.postDelayed(this, ScenarioRunner.TICK_MS)
        }
    }

    fun startTicking() = onMain {
        if (ticking) return@onMain
        ticking = true
        engine.onDriveStart(now())
        main.post(tickRunnable)
    }

    fun stopTicking() = onMain { ticking = false; main.removeCallbacks(tickRunnable) }

    // 시연 모드: core 시나리오를 시간표대로 엔진에 넣는다(에뮬레이터와 Pleos 서비스 없이 돈다)
    fun playDemo(s: Scenario) = onMain {
        updateNearbyShops()
        main.removeCallbacksAndMessages(DEMO_TOKEN)
        _demo.value = s.title
        val start = now()
        for (step in s.steps) {
            main.postAtTime({
                if (step.event is ScenarioEvent.Telltales) {
                    telltales = (step.event as ScenarioEvent.Telltales).readings
                    brakeFluid = (step.event as ScenarioEvent.Telltales).brakeFluid
                }
                if (step.event is ScenarioEvent.Body) body = (step.event as ScenarioEvent.Body).signals
                demoRunner.apply(step.event, now())
                note("시연 ${step.atMs / 1000}초: ${step.note}")
                refresh()
            }, DEMO_TOKEN, start + step.atMs)
        }
        main.postAtTime({ _demo.value = null }, DEMO_TOKEN, start + s.durationMs)
    }

    fun stopDemo() = onMain {
        main.removeCallbacksAndMessages(DEMO_TOKEN)
        _demo.value = null
        telltales = emptyList(); brakeFluid = FluidLevel.UNKNOWN; body = BodySignals()
        engine.onSpeed(null)
        engine.onDriveStart(now())
        refresh()
    }

    private val DEMO_TOKEN = Any()

    fun repeat() = onMain { engine.repeatCurrent(now()) }

    /** 다시 듣기: 마지막 안내를 다시 말한다(바로 정차와 대피는 바로, 나머지는 정차 뒤). 마지막 안내가 없으면 아무것도 하지 않는다. */
    fun repeatLast() = onMain { engine.repeatLast(now()); refresh() }

    // Gleo 질문 답(GleoActionReceiver). 주 스레드에서 부르고 말할 글을 돌려준다.
    /** "지금 켜진 경고등 알려 줘": 가장 급한 경고 문장, 없으면 "현재 켜진 경고등이 없습니다." */
    fun gleoActiveLamps(): Utterance { val u = engine.answerActiveLamps(now()); refresh(); return u }
    /** "방금 안내 다시 말해 줘": 마지막 안내가 없으면 null */
    fun gleoRepeatLast(): String? {
        val last = engine.lastSpoken?.utterance?.text ?: return null
        engine.repeatLast(now()); refresh()
        return last
    }
    /** 우리 음성 출력이 붙어 있는지(Gleo 답 mode 를 정할 때 씀) */
    val speakerReady: Boolean get() = speaker != null
    fun activeLampCount(): Int = engine.activeLampIds().size
    /** "가까운 정비소 알려 줘": 위치나 공공데이터가 없으면 null */
    fun gleoRepairShop(): Utterance? { updateNearby(force = true); return engine.answerRepairShop(now()) }
    fun mute(lampId: String) = onMain { engine.mute(lampId); refresh() }
    fun warningLogText(): String = engine.warningLog.serialize()

    /** "휴게소 들르기". 공공데이터 휴게시설에서 진행 방향 앞쪽 가장 가까운 곳을 경유지로 넣는다. */
    fun addRestAreaWaypoint(): String {
        val (pos, heading) = location ?: return "현재 위치를 아직 받지 못했습니다."
        val tire = LampCatalog.LOW_TIRE_PRESSURE.id in engine.activeLampIds()
        // 저압 타이어는 앞쪽 휴게소 중 경정비가능여부 Y 를 먼저 고른다
        val next = (if (tire) Geo.restAreaForTire(pos, heading, restStops)
            else Geo.nextRestStop(pos, heading, restStops, RestStop.Kind.SERVICE_AREA)) ?: return "앞쪽 휴게소 자료가 없습니다."
        val ok = navi?.addWaypoint(next.place.position.lat, next.place.position.lon, next.place.name) ?: false
        return if (ok) "${next.place.name}을 경유지로 넣었습니다." else "경유지를 넣지 못했습니다."
    }

    // SignalSink
    // 경고 안내 미리 듣기가 도는 동안은 실제 차 신호를 엔진에 넣지 않는다. 실제 신호(기어 P, 빈 경고등 목록)가 미리 듣기 장면을 덮어쓰기 때문이다.
    private val demoActive: Boolean get() = _demo.value != null

    override fun onTelltales(readings: List<TelltaleReading>) = onMain {
        if (demoActive) return@onMain
        telltales = readings
        engine.onTelltales(readings, brakeFluid, now()); refresh()
    }

    override fun onBrakeFluid(level: FluidLevel) = onMain {
        if (demoActive) return@onMain
        brakeFluid = level
        engine.onTelltales(telltales, level, now()); refresh()
    }

    override fun onEngineOil(level: FluidLevel) = onMain { preTripSignals = preTripSignals.copy(engineOil = level); pushPreTrip() }
    override fun onPreTripUnsupported(kind: PreTripKind) = onMain {
        preTripSignals = preTripSignals.copy(unsupported = preTripSignals.unsupported + kind); pushPreTrip()
    }
    override fun onWasherLow(low: Boolean?) = onMain { preTripSignals = preTripSignals.copy(washerLow = low); pushPreTrip() }
    override fun onFuelPercent(percent: Int?) = onMain { preTripSignals = preTripSignals.copy(fuelPercent = percent); pushPreTrip() }
    override fun onTirePressure(wheel: String, pressure: Float?, lowLimit: Float?) = onMain {
        val old = tires[wheel]
        tires[wheel] = TireReading(wheel, pressure ?: old?.pressure, lowLimit ?: old?.lowLimit)
        pushPreTrip()
    }

    private fun pushBody(next: BodySignals) { body = next; engine.onBody(next, now()); refresh() }
    override fun onGear(gear: Gear) = onMain { if (demoActive) return@onMain; pushBody(body.copy(gear = gear)) }
    override fun onIgnition(on: Boolean) = onMain { if (demoActive) return@onMain; engine.onIgnition(on, now()); note("시동 ${if (on) "켜짐" else "꺼짐"}"); refresh() }
    override fun onParkingBrake(on: Boolean) = onMain { engine.onParkingBrake(on); note("주차 브레이크 ${if (on) "체결" else "해제"}"); refresh() }

    /** 차 속도(km/h). CarPropertyManager PERF_VEHICLE_SPEED(m/s)를 어댑터가 km/h 로 바꿔 부른다. 시연 중에는 시연 속도를 쓴다. */
    override fun onSpeed(kmh: Double?) = onMain { if (demoActive) return@onMain; engine.onSpeed(kmh); refresh() }
    override fun onDriverDoorOpen(open: Boolean) = onMain { if (demoActive) return@onMain; pushBody(body.copy(driverDoorOpen = open)) }
    override fun onTrunkOpen(open: Boolean) = onMain { if (demoActive) return@onMain; pushBody(body.copy(trunkOpen = open)) }
    override fun onBrakePedal(pressed: Boolean) = onMain { if (demoActive) return@onMain; engine.onBrakePedal(pressed) }
    override fun onRoute(route: RouteContext) = onMain { if (demoActive) return@onMain; engine.onRoute(route); refresh() }
    override fun onLocation(lat: Double, lon: Double, headingDeg: Double) = onMain {
        location = runCatching { LatLon(lat, lon) }.getOrNull()?.let { it to headingDeg }
        updateNearby()
        // 미리 듣기 주행 화면과 대피 화면의 갈 곳 타일도 쓰므로 화면 상태와 상관없이 채운다(위치가 없으면 빈 목록)
        updateNearbyShops()
    }
    override fun onArrived() = onMain { if (demoActive) return@onMain; engine.onArrived(now()); refresh() }
    override fun onAdapterError(source: String, message: String) = onMain { note("$source: $message") }

    /** Gleo 나 다른 오디오 상태. 음성 출력이 거절되면 Speaker 가 부른다. */
    fun onAudioBusy(busy: Boolean) = onMain { engine.onAudio(gleoSpeaking = busy) }
}
