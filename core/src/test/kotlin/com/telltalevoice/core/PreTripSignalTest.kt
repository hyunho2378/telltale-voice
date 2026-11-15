package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 출발 전 점검 신호 상태(신호 없음과 기다림, 받음). Pleos 에뮬레이터 release_connect_v2.1.0 실측 조건을 가짜 신호로 재현한다. */
class PreTripSignalTest {
    private fun of(items: List<PreTripItem>) = items.associate { it.kind to (it.status to it.signal) }

    @Test
    fun `신호가 하나도 없으면 다섯 줄 모두 UNKNOWN 과 WAITING`() {
        val items = PreTrip.items(PreTripSignals(), emptyList())
        items.forEach { assertEquals(PreTripStatus.UNKNOWN to PreTripSignal.WAITING, it.status to it.signal, it.kind.name) }
    }

    @Test
    fun `차량이 내주지 않는 신호는 UNKNOWN 과 NO_SIGNAL`() {
        val unsupported = setOf(PreTripKind.WASHER, PreTripKind.FUEL, PreTripKind.ENGINE_OIL, PreTripKind.TIRE)
        val m = of(PreTrip.items(PreTripSignals(unsupported = unsupported), emptyList()))
        unsupported.forEach { assertEquals(PreTripStatus.UNKNOWN to PreTripSignal.NO_SIGNAL, m[it], it.name) }
        assertEquals(PreTripStatus.UNKNOWN to PreTripSignal.WAITING, m[PreTripKind.BRAKE_FLUID])
    }

    @Test
    fun `Pleos 실측 조건 경고등 목록은 받았고 와셔액 연료 비율 엔진 오일 바퀴 공기압은 없음`() {
        // 경고등 목록에는 켜진 경고등만 오므로 TPMS 와 LOW_FUEL 이 없으면 꺼짐으로 보고 타이어와 연료는 경고등 기준 정상
        val signals = PreTripSignals(
            unsupported = setOf(PreTripKind.WASHER, PreTripKind.FUEL, PreTripKind.ENGINE_OIL, PreTripKind.TIRE),
            telltalesReceived = true,
        )
        val m = of(PreTrip.items(signals, emptyList()))
        assertEquals(PreTripStatus.OK to PreTripSignal.RECEIVED, m[PreTripKind.TIRE])
        assertEquals(PreTripStatus.OK to PreTripSignal.RECEIVED, m[PreTripKind.FUEL])
        assertEquals(PreTripStatus.UNKNOWN to PreTripSignal.NO_SIGNAL, m[PreTripKind.WASHER])
        assertEquals(PreTripStatus.UNKNOWN to PreTripSignal.NO_SIGNAL, m[PreTripKind.ENGINE_OIL])
        assertEquals(PreTripStatus.UNKNOWN to PreTripSignal.WAITING, m[PreTripKind.BRAKE_FLUID])
        val lit = of(PreTrip.items(signals, listOf(TelltaleReading("TPMS", LampState.ON))))
        assertEquals(PreTripStatus.CHECK to PreTripSignal.RECEIVED, lit[PreTripKind.TIRE])
    }

    @Test
    fun `신호를 받으면 NO_SIGNAL 이었어도 RECEIVED`() {
        val m = of(PreTrip.items(PreTripSignals(washerLow = true, unsupported = setOf(PreTripKind.WASHER)), emptyList()))
        assertEquals(PreTripStatus.CHECK to PreTripSignal.RECEIVED, m[PreTripKind.WASHER])
    }

    @Test
    fun `엔진도 경고등 목록을 받은 뒤 타이어를 정상으로 본다`() {
        val e = TelltaleVoiceEngine()
        assertEquals(PreTripStatus.UNKNOWN, e.preTrip().first().status)
        e.onTelltales(emptyList(), FluidLevel.UNKNOWN, 0)
        assertEquals(PreTripStatus.OK, e.preTrip().first().status)
        e.onPreTripSignals(PreTripSignals(unsupported = setOf(PreTripKind.WASHER)), 0)
        assertEquals(PreTripSignal.NO_SIGNAL, e.preTrip()[3].signal)
    }
}
