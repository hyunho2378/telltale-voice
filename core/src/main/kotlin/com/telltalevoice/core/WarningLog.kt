package com.telltalevoice.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 점등 당시 경로. NaviHelper 현재 위치와 목적지, 다음 회전 설명에서 만든다. */
data class RouteSnapshot(
    val roadLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val destinationName: String? = null,
    val onHighway: Boolean = false,
)

data class LogEntry(
    val lampId: String,
    val onAtMs: Long,
    val onRoute: RouteSnapshot,
    val offAtMs: Long? = null,
    val offRoute: RouteSnapshot? = null,
    /** 켜질 때 판정한 갈래(엔진이 채움). 저장 글 6번째 칸. */
    val tier: Tier? = null,
    /** 이 점등 때 실제로 말한 경고 문장(엔진이 채움). 저장 글 7번째 칸. */
    val spokenText: String? = null,
) {
    val active: Boolean get() = offAtMs == null
    fun durationMs(nowMs: Long): Long = (offAtMs ?: nowMs) - onAtMs
}

/**
 * 경고 기록. 켜진 시각과 꺼진 시각, 점등 당시 경로를 차 안에 남긴다.
 * 운전자는 정비소에서 이 기록을 보여 준다("켜졌다 꺼졌다"를 설명하지 못하는 불편, V4).
 */
class WarningLog(private val zone: ZoneId = ZoneId.of("Asia/Seoul")) {
    private val entries = mutableListOf<LogEntry>()

    fun all(): List<LogEntry> = entries.toList()
    fun active(): List<LogEntry> = entries.filter { it.active }
    fun forLamp(lampId: String): List<LogEntry> = entries.filter { it.lampId == lampId }

    /** 켜짐 기록. 같은 경고가 이미 켜져 있으면 새로 쓰지 않는다. */
    fun onLampOn(lampId: String, atMs: Long, route: RouteSnapshot, tier: Tier? = null): Boolean {
        if (entries.any { it.lampId == lampId && it.active }) return false
        entries += LogEntry(lampId, atMs, route, tier = tier)
        return true
    }

    /** 켜져 있는 기록에 실제로 말한 경고 문장을 처음 한 번 남긴다. */
    fun markSpoken(lampId: String, text: String) {
        val i = entries.indexOfLast { it.lampId == lampId && it.active }
        if (i >= 0 && entries[i].spokenText == null) entries[i] = entries[i].copy(spokenText = text)
    }

    /** 꺼짐 기록. 켜진 기록이 없으면 false. */
    fun onLampOff(lampId: String, atMs: Long, route: RouteSnapshot): Boolean {
        val i = entries.indexOfLast { it.lampId == lampId && it.active }
        if (i < 0) return false
        require(atMs >= entries[i].onAtMs) { "꺼진 시각이 켜진 시각보다 앞섬" }
        entries[i] = entries[i].copy(offAtMs = atMs, offRoute = route)
        return true
    }

    /** 같은 경고가 켜졌다 꺼진 횟수(켜짐 기준). */
    fun onCount(lampId: String): Int = forLamp(lampId).size

    fun clear() = entries.clear()

    /** 엔진 시각(부팅 후 경과 시간)에 더해 실제 날짜와 시각으로 보이게 하는 값. 단위 시험은 0 그대로 쓴다. */
    var wallOffsetMs: Long = 0

    private val timeFmt = DateTimeFormatter.ofPattern("M. d. HH:mm").withZone(zone)

    /**
     * 최근 순 기록 목록. nowMs 는 엔진 시각, tierOf 는 점등 당시 판정한 갈래를 돌려준다(없으면 분류표 갈래).
     * 분류표에 없는 id 는 뺀다.
     */
    fun rows(nowMs: Long, tierOf: (LogEntry) -> Tier? = { it.tier }): List<LogRow> =
        entries.sortedByDescending { it.onAtMs }.mapNotNull { e ->
            val lamp = LampCatalog.byId(e.lampId) ?: return@mapNotNull null
            val onWall = e.onAtMs + wallOffsetMs
            val offWall = e.offAtMs?.let { it + wallOffsetMs }
            LogRow(
                lampId = e.lampId, nameKo = lamp.nameKo, onAtWallMs = onWall, offAtWallMs = offWall,
                tier = tierOf(e) ?: lamp.tier,
                timeText = LampTimeText.describe(onWall, offWall, nowMs + wallOffsetMs, zone),
                roadLabel = e.onRoute.roadLabel,
                spokenText = e.spokenText,
                onTimeText = LampTimeText.onTime(onWall, nowMs + wallOffsetMs, zone),
                offTimeText = offWall?.let { LampTimeText.offTime(it, zone) },
            )
        }

    /** 화면에 보일 한 줄. 예: "11. 12. 14:03 켜짐, 14:20 꺼짐(17분), 경부고속도로" */
    fun describe(entry: LogEntry): String {
        val on = timeFmt.format(Instant.ofEpochMilli(entry.onAtMs + wallOffsetMs))
        val place = entry.onRoute.roadLabel?.let { ", $it" } ?: ""
        val off = entry.offAtMs?.let {
            val hm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(it + wallOffsetMs))
            val min = (it - entry.onAtMs) / 60_000
            ", $hm 꺼짐(${min}분)"
        } ?: ", 아직 켜져 있음"
        return "$on 켜짐$off$place"
    }

    /** 한 줄 한 기록 글로 내보낸다(앱 저장용). 칸 구분은 탭. */
    fun serialize(): String = entries.joinToString("\n") { e ->
        listOf(
            e.lampId, e.onAtMs.toString(), e.offAtMs?.toString() ?: "",
            enc(e.onRoute), e.offRoute?.let(::enc) ?: "", e.tier?.name ?: "",
            e.spokenText?.replace("\t", " ")?.replace("\n", " ") ?: "",
        ).joinToString("\t")
    }

    fun restore(text: String) {
        entries.clear()
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val c = line.split("\t")
            // 6번째 칸(판정 갈래)은 v2 에서 더함. 그 전 기록(5칸)도 읽는다.
            // 7번째 칸(말한 문장)은 v2 4차에서 더함
            require(c.size in 5..7) { "기록 칸 수가 5에서 7이 아님: ${c.size}" }
            entries += LogEntry(
                lampId = c[0], onAtMs = c[1].toLong(), offAtMs = c[2].ifEmpty { null }?.toLong(),
                onRoute = dec(c[3]), offRoute = c[4].ifEmpty { null }?.let(::dec),
                tier = c.getOrNull(5)?.ifEmpty { null }?.let { Tier.valueOf(it) },
                spokenText = c.getOrNull(6)?.ifEmpty { null },
            )
        }
    }

    private fun enc(r: RouteSnapshot): String = listOf(
        r.roadLabel ?: "", r.latitude?.toString() ?: "", r.longitude?.toString() ?: "",
        r.destinationName ?: "", if (r.onHighway) "1" else "0",
    ).joinToString("|") { it.replace("|", "/").replace("\t", " ").replace("\n", " ") }

    private fun dec(s: String): RouteSnapshot {
        val p = s.split("|")
        require(p.size == 5) { "경로 칸 수가 5가 아님" }
        return RouteSnapshot(
            roadLabel = p[0].ifEmpty { null }, latitude = p[1].ifEmpty { null }?.toDouble(),
            longitude = p[2].ifEmpty { null }?.toDouble(), destinationName = p[3].ifEmpty { null },
            onHighway = p[4] == "1",
        )
    }
}
