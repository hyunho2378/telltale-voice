package com.telltalevoice.app.signal

import com.telltalevoice.core.FluidLevel
import com.telltalevoice.core.Gear
import com.telltalevoice.core.PreTripKind
import com.telltalevoice.core.RouteContext
import com.telltalevoice.core.TelltaleReading
import com.telltalevoice.core.Utterance

/** 어댑터가 차 신호를 넣는 곳. EngineHub 가 구현한다. 어느 스레드에서 불러도 된다. */
interface SignalSink {
    fun onTelltales(readings: List<TelltaleReading>)
    fun onBrakeFluid(level: FluidLevel)
    fun onEngineOil(level: FluidLevel)
    /** Safety.hasLowWasherFluidWarning, registerLowWasherFluidWaringState */
    fun onWasherLow(low: Boolean?)
    /** Fuel.getFuelPercent, registerFuelPercent */
    fun onFuelPercent(percent: Int?)
    /** Tire.getTirePressure(바퀴 공기압), getLowTirePressure(저압 기준). 바퀴 이름은 WheelValue 이름 */
    fun onTirePressure(wheel: String, pressure: Float?, lowLimit: Float?)
    /** 차 속도(km/h). 모르면 null */
    fun onSpeed(kmh: Double?)
    /** 차량이 출발 전 점검 신호를 내주지 않음(지원하지 않는 속성). 화면은 그 줄을 신호 없음으로 보인다 */
    fun onPreTripUnsupported(kind: PreTripKind)
    fun onGear(gear: Gear)
    fun onIgnition(on: Boolean)
    fun onParkingBrake(on: Boolean)
    fun onDriverDoorOpen(open: Boolean)
    fun onTrunkOpen(open: Boolean)
    fun onBrakePedal(pressed: Boolean)
    fun onRoute(route: RouteContext)
    fun onLocation(lat: Double, lon: Double, headingDeg: Double)
    fun onArrived()
    fun onAdapterError(source: String, message: String)
}

/** 신호 어댑터 공통. start 는 실패해도 예외를 밖으로 내지 않고 sink.onAdapterError 로 알린다. */
interface SignalAdapter {
    val name: String
    fun start(sink: SignalSink)
    fun stop()
}

/** Pleos Vehicle: 경고등, 오일 수준, 방향지시등(비상등), 도어와 트렁크, 브레이크 페달 */
interface VehicleSignals : SignalAdapter

/** CarPropertyManager: 기어, 시동, 주차 브레이크, 차 속도 */
interface PowertrainSignals : SignalAdapter

/** NaviHelper: 다음 회전, 현재 위치, 도착, 경유지 넣기 */
interface NaviSignals : SignalAdapter {
    /** 경유지 넣기. 넣기를 요청했으면 true */
    fun addWaypoint(lat: Double, lon: Double, name: String): Boolean
}

/** 음성 출력 */
interface Speaker {
    val name: String
    fun start(onReady: (Boolean) -> Unit)
    fun speak(utterance: Utterance, onDone: () -> Unit)
    val speaking: Boolean
    fun stop()
    fun release()
}
