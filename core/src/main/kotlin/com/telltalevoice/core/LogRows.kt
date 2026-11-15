package com.telltalevoice.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 기록 목록 한 줄(최근 순). 시각은 벽시계 밀리초(WarningLog.wallOffsetMs 를 더한 값).
 * tier 는 점등 당시 판정한 갈래(판정 기록이 없으면 분류표 갈래).
 * timeText 는 화면 LampTime 규칙과 같은 글: "오전 9:36 켜짐", "오전 9:36 켜짐, 9:53 꺼짐", 오늘이 아니면 앞에 "11월 10일 ".
 */
data class LogRow(
    val lampId: String,
    val nameKo: String,
    val onAtWallMs: Long,
    val offAtWallMs: Long?,
    val tier: Tier,
    val timeText: String,
    val roadLabel: String?,
    /** 그 점등 때 실제로 말한 경고 문장. 말하지 않았으면 null */
    val spokenText: String? = null,
    /** 켜진 시각 "오전 9:36", 오늘이 아니면 "11월 10일 오전 9:36" */
    val onTimeText: String = "",
    /** 꺼진 시각 "오전 9:53", 꺼지지 않았으면 null */
    val offTimeText: String? = null,
)

object LampTimeText {
    fun describe(onWallMs: Long, offWallMs: Long?, nowWallMs: Long, zone: ZoneId): String {
        fun t(ms: Long) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
        val on = t(onWallMs)
        val today = t(nowWallMs).toLocalDate()
        val day = if (on.toLocalDate() == today) "" else "${on.monthValue}월 ${on.dayOfMonth}일 "
        val off = offWallMs?.let(::t)
        return when {
            off == null -> "$day${clock(on, true)} 켜짐"
            off.hour == on.hour && off.minute == on.minute -> "$day${clock(on, true)} 켜짐, 꺼짐"
            else -> "$day${clock(on, true)} 켜짐, ${clock(off, off.hour / 12 != on.hour / 12)} 꺼짐"
        }
    }

    /** 켜진 시각 글. 오늘이 아니면 앞에 "11월 10일 " */
    fun onTime(onWallMs: Long, nowWallMs: Long, zone: ZoneId): String {
        val on = ZonedDateTime.ofInstant(Instant.ofEpochMilli(onWallMs), zone)
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowWallMs), zone).toLocalDate()
        val day = if (on.toLocalDate() == today) "" else "${on.monthValue}월 ${on.dayOfMonth}일 "
        return day + clock(on, true)
    }

    /** 꺼진 시각 글(오전과 오후를 늘 붙임) */
    fun offTime(offWallMs: Long, zone: ZoneId): String =
        clock(ZonedDateTime.ofInstant(Instant.ofEpochMilli(offWallMs), zone), true)

    /** 12시간 표기. withNoon 이 false 면 오전과 오후를 앞 시각과 같다고 보고 뺀다. */
    fun clock(t: ZonedDateTime, withNoon: Boolean): String {
        val h = t.hour % 12
        val hh = if (h == 0) 12 else h
        val mm = t.minute.toString().padStart(2, '0')
        val noon = if (t.hour < 12) "오전" else "오후"
        return if (withNoon) "$noon $hh:$mm" else "$hh:$mm"
    }
}
