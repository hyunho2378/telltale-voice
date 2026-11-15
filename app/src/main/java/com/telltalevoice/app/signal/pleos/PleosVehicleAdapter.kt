package com.telltalevoice.app.signal.pleos

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.TirePressureListener
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleColor
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import com.telltalevoice.app.signal.SignalSink
import com.telltalevoice.app.signal.VehicleSignals
import com.telltalevoice.core.FluidLevel
import com.telltalevoice.core.LampColor
import com.telltalevoice.core.LampState
import com.telltalevoice.core.PreTripKind
import com.telltalevoice.core.TelltaleReading

/**
 * Pleos Vehicle SDK 2.3.2 어댑터.
 * - 경고등: registerTelltales 로 바뀔 때 알림을 받고, 알림 권한이 없을 수 있어 POLL_MS 마다 getTelltalesInfo 로 다시 읽는다(U3, W1 [실측]).
 * - 브레이크액과 엔진 오일: registerBrakeFluidLevelState, registerEngineOilLevelState
 * - 운전석 문과 트렁크: Door.registerDoorPosition(ROW_1_LEFT, REAR). 위치 값이 0보다 크면 열림으로 본다 [실측].
 * - 비상등: 읽는 함수가 없어 경고등 목록의 TURN_SIGNAL_LEFT, TURN_SIGNAL_RIGHT 가 함께 켜지면 비상등으로 본다(core).
 * - 출발 전 점검(v2): Safety.registerLowWasherFluidWaringState(와셔액), Fuel.registerFuelPercent(연료 비율, CAR_ENERGY),
 *   Tire.registerTirePressure(바퀴 공기압, PRIVATE_CAR_SAFETY)와 Tire.getLowTirePressure(저압 기준, CAR_SAFETY). 함수 이름은 Vehicle-2.3.2.aar javap 로 확인.
 *   getLowTirePressure 값의 뜻은 문서 본문이 비어 있어 이름으로만 판단했다(에뮬레이터 확인 필요).
 */
class PleosVehicleAdapter(context: Context) : VehicleSignals {
    override val name = "Pleos Vehicle"
    private val app = context.applicationContext
    private var vehicle: Vehicle? = null
    private var sink: SignalSink? = null
    private val handler = Handler(Looper.getMainLooper())

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) { telltales?.let { publish(it) } }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "경고등 알림: ${e.message}") }
    }
    private val brakeFluidListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) { sink?.onBrakeFluid(map(state)) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "브레이크액: ${e.message}") }
    }
    private val engineOilListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) { sink?.onEngineOil(map(state)) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "엔진 오일: ${e.message}") }
    }
    private val driverDoorListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) { sink?.onDriverDoorOpen((pos ?: 0) > 0) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "운전석 문: ${e.message}") }
    }
    private val trunkListener = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) { sink?.onTrunkOpen((pos ?: 0) > 0) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "트렁크: ${e.message}") }
    }
    private val brakePedalListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) { sink?.onBrakePedal(pressed == true) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "브레이크 페달: ${e.message}") }
    }

    private val washerListener = object : LowWasherFluidWaringListener {
        override fun onLowWasherFluidWaringUpdated(low: Boolean?) { sink?.onWasherLow(low) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "와셔액: ${e.message}") }
    }
    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(percent: Int?) { sink?.onFuelPercent(percent) }
        override fun onFailed(e: Exception) { sink?.onAdapterError(name, "연료 비율: ${e.message}") }
    }
    /** 속성을 지원하지 않는다는 오류인지(Pleos 에뮬레이터 실측 문구 "is not in carPropertyConfig list, and so it is not supported") */
    private fun unsupported(e: Throwable?) = e?.message?.contains("not supported") == true

    private fun readFailed(kind: PreTripKind, what: String, e: Throwable) {
        sink?.onAdapterError(name, "$what: ${e.message}")
        if (unsupported(e)) sink?.onPreTripUnsupported(kind)
    }

    private val tireUnsupported = mutableSetOf<WheelValue>()

    private val wheels = listOf(WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR)
    private val tireListeners: Map<WheelValue, TirePressureListener> = wheels.associateWith { w ->
        object : TirePressureListener {
            override fun onTirePressureUpdated(p: Float?) { sink?.onTirePressure(w.name, p, null) }
            override fun onFailed(e: Exception) {
                sink?.onAdapterError(name, "타이어 공기압 ${w.name}: ${e.message}")
                // 네 바퀴 모두 공기압 속성이 없으면 바퀴 공기압 신호 없음(타이어 줄은 TPMS 경고등으로 판단)
                if (unsupported(e) && tireUnsupported.add(w) && tireUnsupported.size == 4) sink?.onPreTripUnsupported(PreTripKind.TIRE)
            }
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            vehicle?.let { v ->
                runCatching {
                    v.getSafety().getTelltalesInfo({ publish(it) }, { sink?.onAdapterError(name, "경고등 읽기: ${it.message}") })
                }.onFailure { sink?.onAdapterError(name, "경고등 읽기: ${it.message}") }
                handler.postDelayed(this, POLL_MS)
            }
        }
    }

    private fun publish(t: Telltales) {
        val readings = t.telltales.map { d ->
            TelltaleReading(
                type = d.type.name,
                state = when (d.state) { TelltaleState.ON -> LampState.ON; TelltaleState.BLINK -> LampState.BLINK; else -> LampState.OFF },
                color = colorOf(d.color),
            )
        }
        sink?.onTelltales(readings)
    }

    private fun map(s: OilLevelState?): FluidLevel = FluidLevel.entries.firstOrNull { it.name == s?.name } ?: FluidLevel.UNKNOWN

    /** TelltaleData.color 는 정수 id. SDK 의 id 조회 함수가 internal 이라 enum 값의 id 를 반사로 읽어 맞춘다. */
    private val colorIds: Map<Int, LampColor> by lazy {
        TelltaleColor.entries.mapNotNull { c ->
            runCatching {
                val id = TelltaleColor::class.java.getMethod("getId\$Vehicle_release").invoke(c) as Int
                id to (LampColor.entries.firstOrNull { it.name == c.name } ?: LampColor.UNKNOWN)
            }.getOrNull()
        }.toMap()
    }

    private fun colorOf(id: Int): LampColor = colorIds[id] ?: LampColor.UNKNOWN

    override fun start(sink: SignalSink) {
        this.sink = sink
        val v = try {
            Vehicle(app).also { it.initialize() }
        } catch (e: Throwable) {
            sink.onAdapterError(name, "초기화 실패: ${e.message}")
            return
        }
        vehicle = v
        fun attempt(what: String, block: () -> Unit) =
            runCatching(block).onFailure { sink.onAdapterError(name, "$what 등록 실패: ${it.message}") }
        attempt("경고등") { v.getSafety().registerTelltales(telltaleListener) }
        attempt("브레이크액") { v.getSafety().registerBrakeFluidLevelState(brakeFluidListener) }
        attempt("엔진 오일") { v.getSafety().registerEngineOilLevelState(engineOilListener) }
        attempt("운전석 문") { v.getDoor().registerDoorPosition(DoorArea(DoorValue.ROW_1_LEFT), driverDoorListener) }
        attempt("트렁크") { v.getDoor().registerDoorPosition(DoorArea(DoorValue.REAR), trunkListener) }
        attempt("브레이크 페달") { v.getBrake().registerBrakePedalStatus(brakePedalListener) }
        attempt("와셔액") {
            v.getSafety().registerLowWasherFluidWaringState(washerListener)
            v.getSafety().hasLowWasherFluidWarning({ sink.onWasherLow(it) }, { readFailed(PreTripKind.WASHER, "와셔액 읽기", it) })
        }
        attempt("연료 비율") {
            v.getFuel().registerFuelPercent(fuelListener)
            v.getFuel().getFuelPercent({ sink.onFuelPercent(it) }, { readFailed(PreTripKind.FUEL, "연료 비율 읽기", it) })
        }
        attempt("엔진 오일 읽기") {
            v.getSafety().getEngineOilLevelState({ sink.onEngineOil(map(it)) }, { readFailed(PreTripKind.ENGINE_OIL, "엔진 오일 읽기", it) })
        }
        for (w in wheels) {
            val area = WheelArea(w)
            attempt("저압 기준 ${w.name}") {
                v.getTire().getLowTirePressure(area, { sink.onTirePressure(w.name, null, it) }, { sink.onAdapterError(name, "저압 기준 ${w.name}: ${it.message}") })
            }
            attempt("타이어 공기압 ${w.name}") { v.getTire().registerTirePressure(area, tireListeners.getValue(w)) }
        }
        handler.post(poll)
    }

    override fun stop() {
        handler.removeCallbacks(poll)
        val v = vehicle ?: return
        runCatching { v.getSafety().unregisterTelltales(telltaleListener) }
        runCatching { v.getSafety().unregisterBrakeFluidLevelState(brakeFluidListener) }
        runCatching { v.getSafety().unregisterEngineOilLevelState(engineOilListener) }
        runCatching { v.getDoor().unregisterDoorPosition(DoorArea(DoorValue.ROW_1_LEFT), driverDoorListener) }
        runCatching { v.getDoor().unregisterDoorPosition(DoorArea(DoorValue.REAR), trunkListener) }
        runCatching { v.getBrake().unregisterBrakePedalStatus(brakePedalListener) }
        runCatching { v.getSafety().unregisterLowWasherFluidWaringState(washerListener) }
        runCatching { v.getFuel().unregisterFuelPercent(fuelListener) }
        for (w in wheels) runCatching { v.getTire().unregisterTirePressure(WheelArea(w), tireListeners.getValue(w)) }
        runCatching { v.release() }
        vehicle = null
    }

    companion object { const val POLL_MS = 3_000L }
}
