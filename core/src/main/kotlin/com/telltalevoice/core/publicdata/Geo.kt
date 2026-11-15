package com.telltalevoice.core.publicdata

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Ranked<T : Place>(val place: T, val distanceMeters: Double, val bearingDeg: Double)

/** 위경도 거리와 방향. 지구 반지름은 평균값 6,371,008.8m(IUGG). */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8
    /** 설계 값: 진행 방향 양옆 이 각도 안을 "앞쪽"으로 본다. */
    const val DEFAULT_AHEAD_HALF_ANGLE = 60.0

    private fun rad(d: Double) = Math.toRadians(d)

    /** 하버사인 거리(미터). */
    fun distanceMeters(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** a 에서 b 로 가는 첫 방위각(도, 북쪽 0, 시계 방향, 0 이상 360 미만). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 두 방위각 차이(0 이상 180 이하). */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs((a - b) % 360.0)
        return if (d > 180.0) 360.0 - d else d
    }

    fun <T : Place> rank(from: LatLon, items: List<T>): List<Ranked<T>> =
        items.map { Ranked(it, distanceMeters(from, it.position), bearingDeg(from, it.position)) }
            .sortedBy { it.distanceMeters }

    /** 가장 가까운 곳 k 개. */
    fun <T : Place> nearest(from: LatLon, items: List<T>, k: Int = 1, maxMeters: Double = Double.MAX_VALUE): List<Ranked<T>> {
        require(k >= 0)
        return rank(from, items).filter { it.distanceMeters <= maxMeters }.take(k)
    }

    /** 진행 방향(headingDeg) 앞쪽에 있는 곳만 가까운 순으로. 이미 지나친 곳은 뺀다. */
    fun <T : Place> aheadOnly(
        from: LatLon, headingDeg: Double, items: List<T>,
        halfAngleDeg: Double = DEFAULT_AHEAD_HALF_ANGLE, maxMeters: Double = Double.MAX_VALUE,
    ): List<Ranked<T>> {
        require(halfAngleDeg in 0.0..180.0)
        return rank(from, items).filter { angleDiff(it.bearingDeg, headingDeg) <= halfAngleDeg && it.distanceMeters <= maxMeters }
    }

    /** 다음 휴게시설(휴게소와 졸음쉼터 중 앞쪽 가장 가까운 곳). */
    fun nextRestStop(from: LatLon, headingDeg: Double, stops: List<RestStop>, kind: RestStop.Kind? = null): Ranked<RestStop>? =
        aheadOnly(from, headingDeg, stops.filter { kind == null || it.kind == kind }).firstOrNull()

    /**
     * 저압 타이어 경고 때 들를 휴게소. 진행 방향 앞쪽 휴게소 중 경정비가능여부 Y 를 먼저 고르고,
     * maxMeters 안에 없으면 앞쪽 가장 가까운 휴게소를 고른다. 졸음쉼터는 정비가 없어 뺀다.
     */
    fun restAreaForTire(from: LatLon, headingDeg: Double, stops: List<RestStop>, maxMeters: Double = Double.MAX_VALUE): Ranked<RestStop>? {
        val ahead = aheadOnly(from, headingDeg, stops.filter { it.kind == RestStop.Kind.SERVICE_AREA }, maxMeters = maxMeters)
        return ahead.firstOrNull { it.place.lightRepair } ?: ahead.firstOrNull()
    }

    /** 가까운 영업 중 정비업체. 블루핸즈 이름이 있는 곳을 먼저 원하면 bluehandsFirst. */
    fun nearestShops(from: LatLon, shops: List<RepairShop>, k: Int = 3, bluehandsFirst: Boolean = true): List<Ranked<RepairShop>> {
        val ranked = rank(from, shops.filter { it.operating })
        val ordered = if (bluehandsFirst) ranked.sortedWith(compareBy({ !it.place.bluehandsByName }, { it.distanceMeters })) else ranked
        return ordered.take(k)
    }

    fun metersRounded(m: Double): Int = m.roundToInt()
}

/** 판정 근거에 쓸 가까운 장소 세 종류를 고른다. 저압 타이어면 앞쪽 경정비 가능 휴게소를 먼저 고른다. */
object NearbyFinder {
    fun find(
        from: com.telltalevoice.core.publicdata.LatLon,
        headingDeg: Double,
        stops: List<RestStop>,
        shops: List<RepairShop>,
        tire: Boolean = false,
    ): com.telltalevoice.core.NearbyPlaces {
        fun ref(kind: com.telltalevoice.core.RefugeKind, r: Ranked<out Place>?) =
            r?.let { com.telltalevoice.core.Refuge(kind, it.place.name, Geo.metersRounded(it.distanceMeters)) }
        val rest = if (tire) Geo.restAreaForTire(from, headingDeg, stops) else Geo.nextRestStop(from, headingDeg, stops, RestStop.Kind.SERVICE_AREA)
        return com.telltalevoice.core.NearbyPlaces(
            shelter = ref(com.telltalevoice.core.RefugeKind.DROWSY_SHELTER, Geo.nextRestStop(from, headingDeg, stops, RestStop.Kind.DROWSY_SHELTER)),
            restArea = ref(com.telltalevoice.core.RefugeKind.REST_AREA, rest),
            repairShop = ref(com.telltalevoice.core.RefugeKind.REPAIR_SHOP, Geo.nearestShops(from, shops, k = 1).firstOrNull()),
        )
    }
}
