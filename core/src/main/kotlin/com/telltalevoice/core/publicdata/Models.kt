package com.telltalevoice.core.publicdata

/** 위경도(WGS84, 도 단위). */
data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0) { "위도 범위 밖: $lat" }
        require(lon in -180.0..180.0) { "경도 범위 밖: $lon" }
    }
}

interface Place {
    val name: String
    val position: LatLon
}

/** 고속도로 휴게시설. 휴게소와 졸음쉼터를 함께 담는다. */
data class RestStop(
    override val name: String,
    override val position: LatLon,
    val kind: Kind,
    val routeName: String? = null,
    val direction: String? = null,
    /** 전국휴게소정보표준데이터 "경정비가능여부" Y */
    val lightRepair: Boolean = false,
    val phone: String? = null,
) : Place {
    enum class Kind { SERVICE_AREA, DROWSY_SHELTER }
}

/** 전국자동차정비업체표준데이터 한 줄(영업 중 업체만 받은 파일). */
data class RepairShop(
    override val name: String,
    override val position: LatLon,
    val shopType: String? = null,
    val roadAddress: String? = null,
    val phone: String? = null,
    val operating: Boolean = true,
) : Place {
    /** 업체명으로 블루핸즈 여부를 본다. 표준데이터에는 블루핸즈 칸이 없다. */
    val bluehandsByName: Boolean get() = name.contains("블루핸즈")
}

/** 긴급 연락처. 번호는 원문 출처와 함께 둔다. */
data class EmergencyContact(val label: String, val phone: String, val source: String)

object EmergencyContacts {
    /** 출처: 건설이코노미뉴스, 한국도로공사 보도자료 기사(2023. 5. 9.) "한국도로공사 콜센터(☎1588-2504)로 신청하면 된다." */
    val EX_TOW = EmergencyContact(
        label = "한국도로공사 긴급견인",
        phone = "1588-2504",
        source = "한국도로공사 보도자료 기사(건설이코노미뉴스, 2023. 5. 9.)",
    )
    val all = listOf(EX_TOW)
}

/**
 * 공공데이터를 주는 곳. 실제 호출 주소(공공데이터포털 API 또는 파일 내려받기)는 정해지지 않아
 * 앱은 이 인터페이스만 쓴다. 지금 구현은 CSV 글을 받는 CsvPublicDataSource 하나다.
 */
interface PublicDataSource {
    fun restStops(): List<RestStop>
    fun repairShops(): List<RepairShop>
    fun emergencyContacts(): List<EmergencyContact> = EmergencyContacts.all
}
