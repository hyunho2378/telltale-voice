package com.telltalevoice.core.publicdata

/**
 * CSV 글로 공공데이터를 읽는다. 파일 세 개(2026. 11. 11. 공공데이터포털에서 받아 UTF-8로 바꾼 파일, 02. 개발/publicdata/README.md):
 * - rest_areas.csv: 한국도로공사, 전국휴게소정보표준데이터
 * - drowsy_shelters.csv: 전국졸음쉼터표준데이터
 * - repair_shops.csv: 전국자동차정비업체표준데이터(영업 중 업체만)
 * 칸 이름은 여러 후보(aliases)를 두고 처음 맞는 칸을 쓴다.
 * 칸 이름 후보는 실제 파일을 받은 뒤 맞춰야 한다(지금 값은 표준데이터 칸 이름 추정).
 * 좌표가 비었거나 숫자가 아닌 줄은 버리고 skipped 에 센다.
 */
class CsvPublicDataSource(
    private val restAreaCsv: String,
    private val repairShopCsv: String,
    private val drowsyShelterCsv: String = "",
    private val aliases: Aliases = Aliases(),
) : PublicDataSource {

    data class Aliases(
        val name: List<String> = listOf("휴게소명", "시설명", "졸음쉼터명", "자동차정비업체명", "업체명", "name"),
        val lat: List<String> = listOf("위도", "lat", "latitude"),
        val lon: List<String> = listOf("경도", "lon", "lng", "longitude"),
        val route: List<String> = listOf("도로노선명", "노선명", "route"),
        val direction: List<String> = listOf("도로노선방향", "방향", "direction"),
        val kind: List<String> = listOf("휴게소종류", "시설구분", "kind"),
        val lightRepair: List<String> = listOf("경정비가능여부"),
        val restPhone: List<String> = listOf("휴게소전화번호", "관리기관전화번호", "전화번호"),
        val shopType: List<String> = listOf("자동차정비업체종류", "업체종류"),
        val roadAddress: List<String> = listOf("소재지도로명주소", "도로명주소"),
        val phone: List<String> = listOf("전화번호", "phone"),
        val status: List<String> = listOf("운영상태", "영업상태"),
    )

    var skippedRestStops = 0
        private set
    var skippedRepairShops = 0
        private set

    private fun Map<String, String>.pick(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { k -> this[k]?.takeIf { it.isNotBlank() } }

    private fun Map<String, String>.position(): LatLon? {
        val lat = pick(aliases.lat)?.toDoubleOrNull() ?: return null
        val lon = pick(aliases.lon)?.toDoubleOrNull() ?: return null
        return runCatching { LatLon(lat, lon) }.getOrNull()
    }

    override fun restStops(): List<RestStop> {
        skippedRestStops = 0
        return parseRest(restAreaCsv, RestStop.Kind.SERVICE_AREA) + parseRest(drowsyShelterCsv, RestStop.Kind.DROWSY_SHELTER)
    }

    private fun parseRest(csv: String, defaultKind: RestStop.Kind): List<RestStop> =
        Csv.parseWithHeader(csv).mapNotNull { m ->
            val name = m.pick(aliases.name)
            val pos = m.position()
            if (name == null || pos == null) { skippedRestStops++; return@mapNotNull null }
            val kindText = m.pick(aliases.kind) ?: ""
            val kind = if (kindText.contains("졸음") || m.containsKey("졸음쉼터명")) RestStop.Kind.DROWSY_SHELTER else defaultKind
            RestStop(
                name, pos, kind, m.pick(aliases.route), m.pick(aliases.direction),
                lightRepair = m.pick(aliases.lightRepair) == "Y", phone = m.pick(aliases.restPhone),
            )
        }

    override fun repairShops(): List<RepairShop> {
        skippedRepairShops = 0
        return Csv.parseWithHeader(repairShopCsv).mapNotNull { m ->
            val name = m.pick(aliases.name)
            val pos = m.position()
            if (name == null || pos == null) { skippedRepairShops++; return@mapNotNull null }
            val status = m.pick(aliases.status)
            RepairShop(
                name, pos, m.pick(aliases.shopType), m.pick(aliases.roadAddress), m.pick(aliases.phone),
                operating = status == null || !(status.contains("폐업") || status.contains("휴업")),
            )
        }
    }
}
