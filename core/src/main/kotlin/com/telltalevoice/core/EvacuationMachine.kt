package com.telltalevoice.core

/** 대피 단계. */
enum class EvacuationStep {
    /** 대피 안내 대상 아님(바로 서야 하는 경고가 없거나 고속도로가 아님) */
    IDLE,
    /** 고속도로에서 바로 서야 하는 경고가 켜짐. 차가 P 에 서기를 기다림 */
    ARMED,
    ASK_HAZARD,
    ASK_TRUNK,
    ASK_EXIT,
    /** 운전석 문이 열림. 화면에 긴급견인 전화 버튼만 남김 */
    OUTSIDE,
}

/** Pleos VehicleGear 이름에서 필요한 값만. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 대피 상태기계 입력. */
data class BodySignals(
    val gear: Gear = Gear.UNKNOWN,
    val hazardOn: Boolean = false,
    val trunkOpen: Boolean = false,
    val driverDoorOpen: Boolean = false,
)

data class EvacuationTransition(val from: EvacuationStep, val to: EvacuationStep, val utterance: Utterance?)

/**
 * 고속도로 고장 정차 대피 상태기계(한국도로공사 "비상등 켜고, 트렁크 열고, 가드레일 밖으로 대피 후, 스마트폰으로 신고").
 * 운전자는 화면을 누르지 않는다. 기어 P, 비상등, 트렁크, 문 신호가 바뀌면 다음 단계로 넘어간다.
 *
 * 갈래
 * - IDLE: 고속도로 위에서 STOP_NOW 경고가 켜지면 ARMED
 * - ARMED: 기어 P 가 되면 아직 안 한 첫 단계로(비상등, 트렁크, 내리기 순)
 * - ASK_HAZARD: 비상등이 켜지면 다음 단계
 * - ASK_TRUNK: 트렁크가 열리면 ASK_EXIT, 비상등이 꺼지면 ASK_HAZARD 로 되돌림
 * - ASK_EXIT: 운전석 문이 열리면 OUTSIDE, 비상등이 꺼지면 ASK_HAZARD 로 되돌림
 * - 기어가 P 에서 벗어나면(다시 출발) ARMED 로 돌아감
 * - STOP_NOW 경고가 모두 꺼지면 IDLE(차가 서 있을 때만; 대피 중이면 끝까지 둠)
 */
class EvacuationMachine {
    var step: EvacuationStep = EvacuationStep.IDLE
        private set
    private var signals = BodySignals()

    val currentSignals: BodySignals get() = signals

    /** 바로 서야 하는 경고가 켜지거나 꺼질 때 부른다. */
    fun onStopWarning(active: Boolean, onHighway: Boolean): EvacuationTransition? {
        if (active && onHighway && step == EvacuationStep.IDLE) {
            val t = move(EvacuationStep.ARMED)
            // 이미 P 에 서 있으면 바로 다음 단계로
            return if (signals.gear == Gear.PARK) onSignals(signals) ?: t else t
        }
        if (!active && step == EvacuationStep.ARMED) return move(EvacuationStep.IDLE)
        return null
    }

    fun onSignals(next: BodySignals): EvacuationTransition? {
        signals = next
        if (step == EvacuationStep.IDLE || step == EvacuationStep.OUTSIDE) return null
        if (next.gear != Gear.PARK) {
            return if (step != EvacuationStep.ARMED) move(EvacuationStep.ARMED) else null
        }
        val target = when (step) {
            EvacuationStep.ARMED -> firstPending(next)
            EvacuationStep.ASK_HAZARD -> if (next.hazardOn) firstPending(next) else null
            EvacuationStep.ASK_TRUNK -> when {
                !next.hazardOn -> EvacuationStep.ASK_HAZARD
                next.trunkOpen -> firstPending(next)
                else -> null
            }
            EvacuationStep.ASK_EXIT -> when {
                !next.hazardOn -> EvacuationStep.ASK_HAZARD
                next.driverDoorOpen -> EvacuationStep.OUTSIDE
                else -> null
            }
            else -> null
        }
        return if (target != null && target != step) move(target) else null
    }

    fun reset() {
        step = EvacuationStep.IDLE
        signals = BodySignals()
    }

    private fun firstPending(s: BodySignals): EvacuationStep = when {
        !s.hazardOn -> EvacuationStep.ASK_HAZARD
        !s.trunkOpen -> EvacuationStep.ASK_TRUNK
        !s.driverDoorOpen -> EvacuationStep.ASK_EXIT
        else -> EvacuationStep.OUTSIDE
    }

    private fun move(to: EvacuationStep): EvacuationTransition {
        val from = step
        step = to
        return EvacuationTransition(from, to, SpeechBuilder.evacuation(to))
    }

    companion object {
        /** 한국도로공사 콜센터 긴급견인(V2 3-4 원문). */
        const val EX_CALL_CENTER = "1588-2504"
    }
}
