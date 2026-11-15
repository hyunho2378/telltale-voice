package com.telltalevoice.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EvacuationTest {
    private val P = Gear.PARK
    private fun armed(): EvacuationMachine = EvacuationMachine().also {
        it.onSignals(BodySignals(gear = Gear.DRIVE))
        it.onStopWarning(true, onHighway = true)
    }

    @Test
    fun `대피 문장 고정`() {
        assertEquals("비상등을 켜십시오.", SpeechBuilder.evacuation(EvacuationStep.ASK_HAZARD)!!.text)
        assertEquals("트렁크를 여십시오.", SpeechBuilder.evacuation(EvacuationStep.ASK_TRUNK)!!.text)
        assertEquals("차에서 내려 가드레일 밖으로 피하십시오. 함께 탄 분도 가드레일 밖으로 피하십시오.", SpeechBuilder.evacuation(EvacuationStep.ASK_EXIT)!!.text)
        assertEquals("가드레일 밖에서 긴급견인을 부르십시오.", SpeechBuilder.evacuation(EvacuationStep.OUTSIDE)!!.text)
        assertNull(SpeechBuilder.evacuation(EvacuationStep.IDLE))
        assertNull(SpeechBuilder.evacuation(EvacuationStep.ARMED))
    }

    @Test
    fun `긴급견인 번호 고정`() = assertEquals("1588-2504", EvacuationMachine.EX_CALL_CENTER)

    @Test
    fun `고속도로가 아니면 대피 안내 안 함`() {
        val m = EvacuationMachine()
        assertNull(m.onStopWarning(true, onHighway = false))
        assertNull(m.onSignals(BodySignals(gear = P)))
        assertEquals(EvacuationStep.IDLE, m.step)
    }

    @Test
    fun `고속도로 STOP_NOW 경고면 ARMED`() {
        val m = EvacuationMachine()
        val t = m.onStopWarning(true, onHighway = true)!!
        assertEquals(EvacuationTransition(EvacuationStep.IDLE, EvacuationStep.ARMED, null), t)
    }

    @Test
    fun `순서대로 비상등 트렁크 내리기 밖`() {
        val m = armed()
        assertEquals("비상등을 켜십시오.", m.onSignals(BodySignals(gear = P))!!.utterance!!.text)
        assertEquals("트렁크를 여십시오.", m.onSignals(BodySignals(gear = P, hazardOn = true))!!.utterance!!.text)
        assertEquals("차에서 내려 가드레일 밖으로 피하십시오. 함께 탄 분도 가드레일 밖으로 피하십시오.", m.onSignals(BodySignals(gear = P, hazardOn = true, trunkOpen = true))!!.utterance!!.text)
        val last = m.onSignals(BodySignals(gear = P, hazardOn = true, trunkOpen = true, driverDoorOpen = true))!!
        assertEquals(EvacuationStep.OUTSIDE, last.to)
        assertEquals(EvacuationStep.OUTSIDE, m.step)
    }

    @ParameterizedTest(name = "P 에 설 때 비상등 {0} 트렁크 {1} 문 {2} -> {3}")
    @CsvSource(
        "false, false, false, ASK_HAZARD",
        "true, false, false, ASK_TRUNK",
        "true, true, false, ASK_EXIT",
        "true, true, true, OUTSIDE",
        "false, true, false, ASK_HAZARD",
        "false, true, true, ASK_HAZARD",
        "true, false, true, ASK_TRUNK",
    )
    fun `P 에 설 때 이미 한 단계는 건너뜀`(hazard: Boolean, trunk: Boolean, door: Boolean, expected: EvacuationStep) {
        val m = armed()
        m.onSignals(BodySignals(gear = P, hazardOn = hazard, trunkOpen = trunk, driverDoorOpen = door))
        assertEquals(expected, m.step)
    }

    @Test
    fun `이미 P 에 서 있을 때 경고가 켜지면 바로 첫 단계`() {
        val m = EvacuationMachine()
        m.onSignals(BodySignals(gear = P))
        val t = m.onStopWarning(true, onHighway = true)!!
        assertEquals(EvacuationStep.ASK_HAZARD, t.to)
    }

    @Test
    fun `비상등 기다리는 동안 다른 신호는 무시`() {
        val m = armed()
        m.onSignals(BodySignals(gear = P))
        assertNull(m.onSignals(BodySignals(gear = P, trunkOpen = true)))
        assertEquals(EvacuationStep.ASK_HAZARD, m.step)
    }

    @Test
    fun `트렁크 단계에서 비상등이 꺼지면 되돌림`() {
        val m = armed()
        m.onSignals(BodySignals(gear = P, hazardOn = true))
        assertEquals(EvacuationStep.ASK_HAZARD, m.onSignals(BodySignals(gear = P, hazardOn = false))!!.to)
    }

    @Test
    fun `내리기 단계에서 비상등이 꺼지면 되돌림`() {
        val m = armed()
        m.onSignals(BodySignals(gear = P, hazardOn = true, trunkOpen = true))
        assertEquals(EvacuationStep.ASK_HAZARD, m.onSignals(BodySignals(gear = P, trunkOpen = true))!!.to)
    }

    @Test
    fun `되돌린 뒤 비상등을 다시 켜면 남은 단계로`() {
        val m = armed()
        m.onSignals(BodySignals(gear = P, hazardOn = true, trunkOpen = true))
        m.onSignals(BodySignals(gear = P, trunkOpen = true))
        assertEquals(EvacuationStep.ASK_EXIT, m.onSignals(BodySignals(gear = P, hazardOn = true, trunkOpen = true))!!.to)
    }

    @ParameterizedTest(name = "{0} 단계에서 다시 출발하면 ARMED")
    @CsvSource("false, false", "true, false", "true, true")
    fun `P 에서 벗어나면 ARMED`(hazard: Boolean, trunk: Boolean) {
        val m = armed()
        m.onSignals(BodySignals(gear = P, hazardOn = hazard, trunkOpen = trunk))
        val t = m.onSignals(BodySignals(gear = Gear.DRIVE, hazardOn = hazard, trunkOpen = trunk))!!
        assertEquals(EvacuationStep.ARMED, t.to)
        assertNull(t.utterance)
    }

    @Test
    fun `ARMED 에서 주행 신호는 변화 없음`() {
        val m = armed()
        assertNull(m.onSignals(BodySignals(gear = Gear.DRIVE, hazardOn = true)))
        assertEquals(EvacuationStep.ARMED, m.step)
    }

    @Test
    fun `밖으로 나간 뒤에는 신호가 바뀌어도 그대로`() {
        val m = armed()
        m.onSignals(BodySignals(gear = P, hazardOn = true, trunkOpen = true, driverDoorOpen = true))
        assertNull(m.onSignals(BodySignals(gear = P)))
        assertEquals(EvacuationStep.OUTSIDE, m.step)
    }

    @Test
    fun `ARMED 에서 경고가 꺼지면 IDLE`() {
        val m = armed()
        assertEquals(EvacuationStep.IDLE, m.onStopWarning(false, true)!!.to)
    }

    @Test
    fun `대피 중에는 경고가 꺼져도 계속`() {
        val m = armed()
        m.onSignals(BodySignals(gear = P))
        assertNull(m.onStopWarning(false, true))
        assertEquals(EvacuationStep.ASK_HAZARD, m.step)
    }

    @Test
    fun `IDLE 에서 신호만 바뀌면 아무 일 없음`() {
        val m = EvacuationMachine()
        assertNull(m.onSignals(BodySignals(gear = P, hazardOn = true)))
    }

    @Test
    fun `ARMED 에서 다시 경고가 와도 그대로`() {
        val m = armed()
        assertNull(m.onStopWarning(true, true))
        assertEquals(EvacuationStep.ARMED, m.step)
    }

    @Test
    fun `reset 은 IDLE`() {
        val m = armed()
        m.reset()
        assertEquals(EvacuationStep.IDLE, m.step)
        assertEquals(BodySignals(), m.currentSignals)
    }
}
