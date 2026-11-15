package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 정차 뒤 화면 허용 = 시속 5km 이하이고 (기어 P 또는 주차 브레이크 체결). Pleos 개발 문서 주행 규제 흐름. */
class DrivingRestrictionTest {
    private fun engineWithLamp(gear: Gear): TelltaleVoiceEngine {
        val e = TelltaleVoiceEngine()
        e.onDriveStart(0)
        e.onBody(BodySignals(gear = Gear.DRIVE), 0)
        e.onTelltales(listOf(TelltaleReading("ENGINE_OIL_PRESSURE", LampState.ON)), FluidLevel.NORMAL, 1_000)
        e.onBody(BodySignals(gear = gear), 2_000)
        return e
    }

    @Test
    fun `기어 P 이고 속도 모름이면 정차 뒤 화면`() {
        val e = engineWithLamp(Gear.PARK)
        assertTrue(e.parkedForScreen())
        assertTrue(e.uiState() is UiState.ParkedDetail)
    }

    @Test
    fun `기어 D 라도 주차 브레이크 체결이고 시속 5km 이하면 정차 뒤 화면`() {
        val e = engineWithLamp(Gear.DRIVE)
        assertFalse(e.uiState() is UiState.ParkedDetail)
        e.onParkingBrake(true)
        e.onSpeed(0.0)
        assertTrue(e.uiState() is UiState.ParkedDetail)
        e.onSpeed(5.0)
        assertTrue(e.uiState() is UiState.ParkedDetail)
    }

    @Test
    fun `시속 5km 초과면 기어 P 나 주차 브레이크여도 막는다`() {
        val e = engineWithLamp(Gear.PARK)
        e.onParkingBrake(true)
        e.onSpeed(5.1)
        assertFalse(e.parkedForScreen())
        assertTrue(e.uiState() is UiState.Driving)
    }

    @Test
    fun `기어 D 이고 주차 브레이크 해제면 정지해도 주행 화면`() {
        val e = engineWithLamp(Gear.DRIVE)
        e.onSpeed(0.0)
        assertFalse(e.parkedForScreen())
        assertTrue(e.uiState() is UiState.Driving)
    }
}
