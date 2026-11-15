package com.telltalevoice.core

/**
 * 경고등 세 갈래. 현대차 사용설명서 행동 문장 기준(V2 묶음 6).
 * - STOP_NOW: 매뉴얼이 주행 중 정차를 지시한 경고등
 * - REST_AREA: 속도를 낮추거나 고속을 피하라고 하고 정차는 지시하지 않은 경고등
 * - AFTER_ARRIVAL: "점검을 받으십시오"만 적고 정차나 속도 조건이 없는 경고등
 */
enum class Tier { STOP_NOW, REST_AREA, AFTER_ARRIVAL }

/** Pleos TelltaleState 이름과 같다. */
enum class LampState { OFF, ON, BLINK }

/** Pleos TelltaleColor 이름과 같다. */
enum class LampColor { WHITE, GREEN, BLUE, YELLOW, RED, GRAY, UNKNOWN }

/**
 * 경고등이 어떤 차 신호로 켜졌다고 보는지.
 * - Telltale: Pleos TelltaleType 상수 이름과 켜졌다고 볼 상태와 색
 * - BrakeFluidLow: Pleos Safety.getBrakeFluidLevelState 가 LOW 또는 CRITICALLY_LOW
 * - None: Pleos 상수 짝이 없음(V2 2-2 "짝 없음")
 */
sealed interface LampSignal {
    data class Telltale(
        val types: List<String>,
        val states: Set<LampState> = setOf(LampState.ON, LampState.BLINK),
        val colors: Set<LampColor>? = null,
    ) : LampSignal

    data object BrakeFluidLow : LampSignal
    data object None : LampSignal
}

/**
 * 매뉴얼 경고등 한 개.
 * manualMeaning 과 manualActions 는 V2 1-3 표의 원문 문장을 그대로 옮겼다(바꾸지 않는다).
 * keywords 는 음성 문장의 핵심 낱말(4개 이하), subject 와 action 으로 음성 문장을 만든다.
 */
data class WarningLamp(
    val id: String,
    val manualNo: Int?,
    val nameKo: String,
    val tier: Tier,
    val signal: LampSignal,
    val manualMeaning: String,
    val manualActions: List<String>,
    val manualSource: String,
    val subject: String,
    val predicate: String,
    val action: String,
    val keywords: List<String>,
    val pairNote: String,
)
