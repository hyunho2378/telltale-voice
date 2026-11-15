package com.telltalevoice.app.signal.pleos

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.telltalevoice.app.signal.PowertrainSignals
import com.telltalevoice.app.signal.SignalSink
import com.telltalevoice.core.Gear

/**
 * AAOS 표준 속성 어댑터(R3 2-2 표: GEAR_SELECTION, IGNITION_STATE, PARKING_BRAKE_ON 은 CAR_POWERTRAIN normal, PERF_VEHICLE_SPEED 는 CAR_SPEED dangerous).
 * 휴대폰처럼 android.car 가 없는 기기에서는 오류만 알리고 멈춘다.
 */
class CarPowertrainAdapter(context: Context) : PowertrainSignals {
    override val name = "CarPropertyManager"
    private val app = context.applicationContext
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var sink: SignalSink? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val s = sink ?: return
            if (value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED || BuildConfigLog.speedLogDue()) {
                Log.i(TAG, "${VehiclePropertyIds.toString(value.propertyId)} = ${value.value}")
            }
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> s.onGear(mapGear(value.value as? Int))
                VehiclePropertyIds.IGNITION_STATE -> s.onIgnition((value.value as? Int ?: 0) >= IGNITION_ON)
                VehiclePropertyIds.PARKING_BRAKE_ON -> s.onParkingBrake(value.value as? Boolean == true)
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> s.onSpeed(speedKmh(value.value as? Float))
            }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            sink?.onAdapterError(name, "속성 $propertyId 오류")
        }
    }

    override fun start(sink: SignalSink) {
        this.sink = sink
        try {
            val c = Car.createCar(app) ?: run { sink.onAdapterError(name, "Car 연결 없음"); return }
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            for (id in listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.IGNITION_STATE, VehiclePropertyIds.PARKING_BRAKE_ON)) {
                val ok = m.getCarPropertyConfig(id) != null &&
                    runCatching { m.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE) }.getOrDefault(false)
                if (!ok) sink.onAdapterError(name, "속성 ${VehiclePropertyIds.toString(id)} 없음")
                // Pleos Connect 에뮬레이터 release_connect_v2.1.0 은 IGNITION_STATE 를 내주지 않는다(2026. 11. 14. 실측).
                // 차량 화면이 켜져 앱이 도는 동안은 시동이 켜진 것으로 보고 출발 전 점검을 한다.
                if (!ok && id == VehiclePropertyIds.IGNITION_STATE) sink.onIgnition(true)
            }
            // 차 속도: Pleos Vehicle App FAQ 가 안내하는 PERF_VEHICLE_SPEED(m/s, 연속 갱신). CAR_SPEED 는 dangerous 권한이라 실행 중 승인이 필요하다.
            runCatching { m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
                .onFailure { sink.onAdapterError(name, "차 속도 등록 실패: ${it.message}") }
        } catch (e: Throwable) {
            sink.onAdapterError(name, "사용 불가: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    override fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null; car = null
    }

    /** 차 속도는 연속 갱신이라 로그를 2초에 한 줄만 남긴다. */
    private object BuildConfigLog {
        private var last = 0L
        fun speedLogDue(): Boolean {
            val now = android.os.SystemClock.elapsedRealtime()
            return if (now - last >= 2_000L) { last = now; true } else false
        }
    }

    companion object {
        private const val TAG = "CarPowertrain"
        /** VehicleIgnitionState.ON = 4 */
        const val IGNITION_ON = 4

        /** m/s 를 km/h 로. 뒤로 가면 음수라 크기만 쓴다. 값이 없으면 null */
        fun speedKmh(mps: Float?): Double? = mps?.let { kotlin.math.abs(it.toDouble()) * 3.6 }

        fun mapGear(v: Int?): Gear = when (v) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE
        }
    }
}
