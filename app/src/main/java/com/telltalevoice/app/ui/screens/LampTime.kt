package com.telltalevoice.app.ui.screens

import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 경고 기록(EngineHub.warningLogText, WarningLog.serialize 형식)에서 경고등마다 마지막 켜짐 기록을 읽어
 * 손님용 한 줄로 만든다. 기록 시각은 SystemClock.elapsedRealtime 기준이라 지금 벽시계 시각으로 바꾼다.
 * 예: "오전 9:36부터 켜져 있음", "오전 9:36에 켜졌다가 9:53에 꺼짐", 오늘이 아니면 앞에 "11월 10일".
 */
object LampTime {
    private data class Entry(val lampId: String, val onAt: Long, val offAt: Long?)

    private fun parse(text: String): List<Entry> = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val c = line.split("\t")
        val on = c.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        Entry(c[0], on, c.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toLongOrNull())
    }.toList()

    fun lines(logText: String, zone: ZoneId = ZoneId.systemDefault()): Map<String, String> {
        val wallNow = System.currentTimeMillis()
        val bootNow = SystemClock.elapsedRealtime()
        fun wall(ms: Long) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallNow - (bootNow - ms)), zone)
        val today = LocalDate.now(zone)
        return parse(logText).groupBy { it.lampId }.mapValues { (_, list) ->
            val e = list.maxBy { it.onAt }
            val on = wall(e.onAt)
            val day = if (on.toLocalDate() == today) "" else "${on.monthValue}월 ${on.dayOfMonth}일 "
            val off = e.offAt?.let { wall(it) }
            if (off == null) "$day${clock(on, true)} 켜짐"
            else if (off.hour == on.hour && off.minute == on.minute) "$day${clock(on, true)} 켜짐, 꺼짐"
            else "$day${clock(on, true)} 켜짐, ${clock(off, off.hour / 12 != on.hour / 12)} 꺼짐"
        }
    }

    /** 기록 화면용 줄. 켜짐 기록 하나마다 한 줄, 최근 순. */
    fun rows(logText: String, zone: ZoneId = ZoneId.systemDefault()): List<LogView> {
        val wallNow = System.currentTimeMillis()
        val bootNow = SystemClock.elapsedRealtime()
        fun wall(ms: Long) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallNow - (bootNow - ms)), zone)
        val today = LocalDate.now(zone)
        return parse(logText).sortedByDescending { it.onAt }.map { e ->
            val lamp = com.telltalevoice.core.LampCatalog.byId(e.lampId)
            val on = wall(e.onAt)
            val day = if (on.toLocalDate() == today) "" else "${on.monthValue}월 ${on.dayOfMonth}일 "
            val off = e.offAt?.let { wall(it) }
            val line = if (off == null) "$day${clock(on, true)} 켜짐"
                else if (off.hour == on.hour && off.minute == on.minute) "$day${clock(on, true)} 켜짐, 꺼짐"
                else "$day${clock(on, true)} 켜짐, ${clock(off, off.hour / 12 != on.hour / 12)} 꺼짐"
            LogView(e.lampId, lamp?.nameKo ?: e.lampId, lamp?.tier, line, off == null, wallNow - (bootNow - e.onAt))
        }
    }

    /** 지금 시각 한 줄(예: "오후 2:33"). */
    fun nowClock(zone: ZoneId = ZoneId.systemDefault()): String = clock(ZonedDateTime.now(zone), true)

    /** 벽시계 밀리초 한 줄(예: "오후 2:33", 오늘이 아니면 "11월 10일 오후 2:33"). */
    fun wallClock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
        val day = if (t.toLocalDate() == LocalDate.now(zone)) "" else "${t.monthValue}월 ${t.dayOfMonth}일 "
        return day + clock(t, true)
    }

    /** 12시간 표기. withNoon 이 false 면 오전과 오후를 앞 시각과 같다고 보고 뺀다. */
    private fun clock(t: ZonedDateTime, withNoon: Boolean): String {
        val h = t.hour % 12
        val hh = if (h == 0) 12 else h
        val mm = t.minute.toString().padStart(2, '0')
        val noon = if (t.hour < 12) "오전" else "오후"
        return if (withNoon) "$noon $hh:$mm" else "$hh:$mm"
    }
}
