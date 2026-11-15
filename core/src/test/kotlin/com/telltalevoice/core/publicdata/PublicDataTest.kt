package com.telltalevoice.core.publicdata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** 시험용 지점은 실제 시설이 아니다. 이름은 "시험"으로 시작하고 좌표는 계산하기 쉬운 값이다. */
class PublicDataTest {
    private val origin = LatLon(36.0, 127.0)
    private fun stop(name: String, lat: Double, lon: Double, kind: RestStop.Kind = RestStop.Kind.SERVICE_AREA) =
        RestStop(name, LatLon(lat, lon), kind)

    @Test
    fun `긴급견인 번호 고정`() {
        assertEquals("1588-2504", EmergencyContacts.EX_TOW.phone)
        assertEquals(listOf(EmergencyContacts.EX_TOW), EmergencyContacts.all)
    }

    @Test
    fun `위경도 범위 밖은 거절`() {
        assertThrows(IllegalArgumentException::class.java) { LatLon(91.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { LatLon(0.0, 181.0) }
    }

    @ParameterizedTest(name = "위도 {0}도 차이 -> {1}m")
    @CsvSource("0.0, 0", "0.01, 1112", "0.1, 11120", "1.0, 111195")
    fun `남북 거리`(dLat: Double, meters: Int) {
        assertEquals(meters, Geo.metersRounded(Geo.distanceMeters(origin, LatLon(36.0 + dLat, 127.0))))
    }

    @Test
    fun `거리는 대칭`() {
        val b = LatLon(37.5, 126.9)
        assertEquals(Geo.distanceMeters(origin, b), Geo.distanceMeters(b, origin), 1e-6)
    }

    @ParameterizedTest(name = "방위 {0},{1} -> {2}")
    @CsvSource("0.1, 0.0, 0", "0.0, 0.1, 90", "-0.1, 0.0, 180", "0.0, -0.1, 270")
    fun `방위각`(dLat: Double, dLon: Double, deg: Int) {
        val b = Geo.bearingDeg(origin, LatLon(36.0 + dLat, 127.0 + dLon))
        assertTrue(Geo.angleDiff(b, deg.toDouble()) < 0.1, "$b")
    }

    @ParameterizedTest(name = "각도차 {0} {1} -> {2}")
    @CsvSource("0, 0, 0", "10, 350, 20", "350, 10, 20", "0, 180, 180", "90, 270, 180", "45, 90, 45")
    fun `각도 차이`(a: Double, b: Double, d: Double) = assertEquals(d, Geo.angleDiff(a, b), 1e-9)

    @Test
    fun `가장 가까운 곳`() {
        val items = listOf(stop("시험A", 36.2, 127.0), stop("시험B", 36.05, 127.0), stop("시험C", 35.9, 127.0))
        assertEquals(listOf("시험B", "시험C"), Geo.nearest(origin, items, 2).map { it.place.name })
        assertEquals(emptyList<String>(), Geo.nearest(origin, items, 1, maxMeters = 1_000.0).map { it.place.name })
        assertEquals(emptyList<String>(), Geo.nearest(origin, emptyList<RestStop>(), 3).map { it.place.name })
    }

    @Test
    fun `진행 방향 앞쪽만`() {
        val items = listOf(stop("시험북", 36.1, 127.0), stop("시험남", 35.95, 127.0), stop("시험동", 36.0, 127.1))
        assertEquals(listOf("시험북"), Geo.aheadOnly(origin, 0.0, items).map { it.place.name })
        assertEquals(listOf("시험남"), Geo.aheadOnly(origin, 180.0, items).map { it.place.name })
        assertEquals(listOf("시험남", "시험동", "시험북"), Geo.aheadOnly(origin, 0.0, items, halfAngleDeg = 180.0).map { it.place.name })
    }

    @Test
    fun `지나친 휴게소는 가까워도 뺌`() {
        val items = listOf(stop("시험뒤", 35.99, 127.0), stop("시험앞", 36.2, 127.0))
        assertEquals("시험앞", Geo.nextRestStop(origin, 0.0, items)?.place?.name)
    }

    @Test
    fun `다음 졸음쉼터만`() {
        val items = listOf(stop("시험휴게", 36.05, 127.0), stop("시험쉼터", 36.1, 127.0, RestStop.Kind.DROWSY_SHELTER))
        assertEquals("시험쉼터", Geo.nextRestStop(origin, 0.0, items, RestStop.Kind.DROWSY_SHELTER)?.place?.name)
        assertNull(Geo.nextRestStop(origin, 180.0, items))
    }

    @Test
    fun `CSV 따옴표와 쉼표와 주석`() {
        val rows = Csv.parse("\uFEFF# 주석\na,b\n\"x,1\",\"말 \"\"인용\"\"\"\r\n\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x,1", "말 \"인용\"")), rows)
    }

    @Test
    fun `CSV 칸 안 줄바꿈`() = assertEquals(listOf(listOf("가\n나", "다")), Csv.parse("\"가\n나\",다"))

    @Test
    fun `CSV 닫히지 않은 따옴표는 오류`() {
        assertThrows(IllegalArgumentException::class.java) { Csv.parse("\"abc") }
    }

    @Test
    fun `CSV 머리 지도`() {
        assertEquals(listOf(mapOf("이름" to "시험", "값" to "")), Csv.parseWithHeader("이름,값\n시험"))
        assertEquals(emptyList<Map<String, String>>(), Csv.parseWithHeader(""))
    }

    // 실제 파일(2026. 11. 11. 공공데이터포털, UTF-8 변환본)의 머리 행과 앞쪽 줄을 그대로 옮김
    private val restHeader = "휴게소명,도로종류,도로노선명,도로노선방향,위도,경도,휴게소종류,경정비가능여부,주유소유무,전기차충전소유무,화장실유무,휴게소전화번호,데이터기준일자"
    private val restRows = listOf(
        "횡성(인천),고속국도,영동선,상행,37.464983,128.135449,일반휴게소,N,Y,Y,Y,033-342-4200,2026-09-17",
        "횡성(강릉),고속국도,영동선,하행,37.462755,128.133969,일반휴게소,N,Y,Y,Y,033-342-4231,2026-09-17",
        "황전(완주),고속국도,순천완주선,상행,35.153306,127.454683,일반휴게소,N,Y,Y,Y,061-782-1154,2026-09-17",
        "행담도,고속국도,서해안선,양방향,36.944819,126.807525,일반휴게소,Y,Y,Y,Y,041-358-0700,2026-09-17",
    )
    private val shelterHeader = "졸음쉼터명,도로종류,도로노선명,도로노선방향,위도,경도,화장실유무,관리기관명,관리기관전화번호,데이터기준일자"
    private val shelterRows = listOf(
        "태인,고속국도,호남선,순천기점 + 천안종점,35.66350643,126.9322504,Y,한국도로공사 전북특별자치도본부,063-714-6000,2026-02-25",
        "전주,고속국도,호남선,순천기점 + 천안종점,35.86947805,127.0516682,Y,한국도로공사 전북특별자치도본부,063-714-6000,2026-02-25",
    )
    private val shopHeader = "자동차정비업체명,자동차정비업체종류,소재지도로명주소,위도,경도,운영시작시각,운영종료시각,전화번호,데이터기준일자"
    private val shopRows = listOf(
        "연암카클리닉,03,충청남도 아산시 음봉면 연암율금로 399,36.87435033,127.06587709,,,,2026-09-23",
        "고향카센타,03,충청남도 아산시 시민로 195(용화동),36.76736997,126.99455813,,,,2026-09-23",
        "현대자동차블루핸즈괴산점,3,충청북도 괴산군 문광면 읍내로 35-17,36.8005054,127.7725379,09:00,18:00,043-832-3000,2026-10-13",
    )
    private fun csv(h: String, rows: List<String>) = (listOf(h) + rows).joinToString("\n")

    @Test
    fun `휴게소 파일 머리 행으로 읽기`() {
        val out = CsvPublicDataSource(csv(restHeader, restRows), "").restStops()
        assertEquals(listOf("횡성(인천)", "횡성(강릉)", "황전(완주)", "행담도"), out.map { it.name })
        assertEquals(LatLon(37.464983, 128.135449), out[0].position)
        assertEquals("영동선", out[0].routeName)
        assertEquals("상행", out[0].direction)
        assertEquals("033-342-4200", out[0].phone)
        assertEquals(listOf(false, false, false, true), out.map { it.lightRepair })
        assertTrue(out.all { it.kind == RestStop.Kind.SERVICE_AREA })
    }

    @Test
    fun `졸음쉼터 파일 머리 행으로 읽기`() {
        val out = CsvPublicDataSource("", "", csv(shelterHeader, shelterRows)).restStops()
        assertEquals(listOf("태인", "전주"), out.map { it.name })
        assertTrue(out.all { it.kind == RestStop.Kind.DROWSY_SHELTER })
        assertEquals("063-714-6000", out[0].phone)
        assertEquals(false, out[0].lightRepair)
    }

    @Test
    fun `휴게소와 졸음쉼터 합치기와 잘못된 줄 버리기`() {
        val src = CsvPublicDataSource(csv(restHeader, restRows + "빈좌표,고속국도,영동선,상행,,,일반휴게소,N,Y,Y,Y,,2026-09-17"), "",
            csv(shelterHeader, shelterRows + "글자좌표,고속국도,호남선,상행,abc,127,Y,,,2026-02-25"))
        assertEquals(6, src.restStops().size)
        assertEquals(2, src.skippedRestStops)
    }

    @Test
    fun `정비업체 파일 머리 행으로 읽기`() {
        val shops = CsvPublicDataSource("", csv(shopHeader, shopRows)).repairShops()
        assertEquals(3, shops.size)
        assertEquals("03", shops[0].shopType)
        assertEquals("충청남도 아산시 음봉면 연암율금로 399", shops[0].roadAddress)
        assertEquals(null, shops[0].phone)
        assertEquals("043-832-3000", shops[2].phone)
        assertEquals(listOf(false, false, true), shops.map { it.bluehandsByName })
        assertTrue(shops.all { it.operating })
    }

    @Test
    fun `도착 뒤 정비소는 블루핸즈 먼저`() {
        val shops = CsvPublicDataSource("", csv(shopHeader, shopRows)).repairShops()
        // 아산 연암카클리닉 바로 옆에서 찾아도 괴산 블루핸즈가 먼저
        val from = LatLon(36.8743, 127.0658)
        assertEquals(listOf("현대자동차블루핸즈괴산점", "연암카클리닉", "고향카센타"), Geo.nearestShops(from, shops).map { it.place.name })
    }

    @Test
    fun `저압 타이어는 앞쪽 경정비 휴게소 먼저`() {
        val stops = listOf(
            stop("시험가까운", 36.05, 127.0),
            RestStop("시험경정비", LatLon(36.2, 127.0), RestStop.Kind.SERVICE_AREA, lightRepair = true),
            RestStop("시험뒤경정비", LatLon(35.9, 127.0), RestStop.Kind.SERVICE_AREA, lightRepair = true),
            stop("시험쉼터", 36.01, 127.0, RestStop.Kind.DROWSY_SHELTER),
        )
        assertEquals("시험경정비", Geo.restAreaForTire(origin, 0.0, stops)?.place?.name)
        assertEquals("시험가까운", Geo.restAreaForTire(origin, 0.0, stops, maxMeters = 10_000.0)?.place?.name)
        assertNull(Geo.restAreaForTire(origin, 0.0, stops.filter { it.kind == RestStop.Kind.DROWSY_SHELTER }))
    }

    @Test
    fun `정비업체 CSV 폐업 표시`() {
        val csv = "자동차정비업체명,자동차정비업체종류,소재지도로명주소,위도,경도,운영상태,전화번호\n시험 블루핸즈,종합,시험로 1,36.01,127.0,영업,000\n시험정비,소형,시험로 2,36.001,127.0,폐업,\n"
        val shops = CsvPublicDataSource("", csv).repairShops()
        assertEquals(2, shops.size)
        assertEquals(false, shops[1].operating)
        assertEquals(listOf("시험 블루핸즈"), Geo.nearestShops(origin, shops).map { it.place.name })
    }

    @Test
    fun `블루핸즈 먼저 그다음 거리`() {
        val shops = listOf(
            RepairShop("시험정비1", LatLon(36.001, 127.0)),
            RepairShop("시험 블루핸즈 2", LatLon(36.05, 127.0)),
            RepairShop("시험정비3", LatLon(36.002, 127.0)),
        )
        assertEquals(listOf("시험 블루핸즈 2", "시험정비1", "시험정비3"), Geo.nearestShops(origin, shops).map { it.place.name })
        assertEquals(listOf("시험정비1", "시험정비3"), Geo.nearestShops(origin, shops, 2, bluehandsFirst = false).map { it.place.name })
    }

    private fun assetFile(name: String): java.io.File? =
        listOf("../app/src/main/assets/publicdata/$name", "app/src/main/assets/publicdata/$name").map { java.io.File(it) }.firstOrNull { it.exists() }

    @Test
    fun `앱에 넣은 실제 파일 세 개 전체 읽기`() {
        val rest = assetFile("rest_areas.csv"); val shelter = assetFile("drowsy_shelters.csv"); val shop = assetFile("repair_shops.csv")
        org.junit.jupiter.api.Assumptions.assumeTrue(rest != null && shelter != null && shop != null, "앱 assets 파일 없음")
        val src = CsvPublicDataSource(rest!!.readText(), shop!!.readText(), shelter!!.readText())
        val stops = src.restStops()
        assertEquals(215, stops.count { it.kind == RestStop.Kind.SERVICE_AREA })
        assertEquals(253, stops.count { it.kind == RestStop.Kind.DROWSY_SHELTER })
        assertEquals(15, stops.count { it.lightRepair })
        assertEquals(0, src.skippedRestStops)
        val shops = src.repairShops()
        assertEquals(34_313, shops.size + src.skippedRepairShops)
        assertEquals(72, shops.count { it.bluehandsByName })
    }
}
