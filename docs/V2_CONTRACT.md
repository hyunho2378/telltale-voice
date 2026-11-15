# V2 화면 데이터 계약 (core 작업자 작성, 2026. 11. 14., 7차 갱신)

화면이 읽는 값은 이 문서에 적힌 이름뿐이다. StateFlow 는 모두 `com.telltalevoice.app.EngineHub` 안에 있으며 주 스레드에서 값이 바뀐다. 데이터 클래스는 `com.telltalevoice.core` 패키지에 둔다. 화면 작업자가 V2Data.kt 에서 제안한 모양을 거의 그대로 따랐고, 달라진 곳은 항목마다 "제안과 다른 점"으로 따로 적었다.

## 1. 판정 근거

`EngineHub.basis: StateFlow<DecisionBasis?>`
켜진 경고등 가운데 가장 급한 경고등을 어떻게 판정했는지 담는다. 경고등이 모두 꺼져도 마지막 판정은 남아 있다가, 시동을 다시 걸면 null 로 돌아간다. 주행 화면과 대피 화면, 정차 후 화면은 모두 이 값 하나를 함께 읽는다.
정차 후 화면에서 경고등마다 판정을 따로 보이려면 `UiState.ParkedDetail.items[i].basis: DecisionBasis?` 를 읽는다.

```kotlin
data class DecisionBasis(
    val lampId: String,          // "LOW_TIRE_PRESSURE"
    val tier: Tier,              // 긴급도. 저압 타이어는 시내와 고속도로 모두 REST_AREA
    val speedKmh: Int?,          // 120, 모르면 null
    val road: RoadKind,          // HIGHWAY, GENERAL, UNKNOWN
    val refuge: Refuge?,         // 규칙이 고른 장소, 없으면 null
    val rule: DecisionRule,      // TIRE_HIGHWAY 등 규칙 이름
    val spokenText: String,      // 실제로 말한 경고 문장
    val atMs: Long,              // 엔진 시각
) {
    val refugeName: String?      // "횡성휴게소"
    val refugeMeters: Int?       // 12000
    val sentence: String         // 화면에 보일 한 문장(rule.sentence)
    val verdictLabel: String     // 판정 카드 라벨(rule.label). 도로에 따라 바뀜. "휴게소까지 서행", "서비스센터까지 서행"
}
data class Refuge(val kind: RefugeKind, val name: String, val meters: Int)
enum class RefugeKind { DROWSY_SHELTER, REST_AREA, REPAIR_SHOP }
enum class RoadKind { HIGHWAY, GENERAL, UNKNOWN }
```

규칙 이름과 한 문장(`DecisionRule.sentence`)

| rule | 쓰는 때 | 장소 | verdictLabel | sentence |
|---|---|---|---|---|
| STOP_NOW_HIGHWAY | 즉시 정차 경고, 고속도로 | 졸음쉼터와 휴게소 중 가까운 곳 | 즉시 정차 | 고속도로 주행 중이고 설명서가 정차를 지시하는 경고등이라 즉시 정차와 대피를 안내합니다. |
| STOP_NOW_ROAD | 즉시 정차 경고, 일반도로나 모름 | 없음 | 즉시 정차 | 설명서가 정차를 지시하는 경고등이라 도로 옆 정차를 안내합니다. |
| TIRE_HIGHWAY | 저압 타이어, 고속도로 | 앞쪽 휴게소(경정비 가능 먼저) | 휴게소까지 서행 | 고속도로 주행 중이라 속도를 낮춰 앞쪽 휴게소까지 가도록 안내합니다. |
| TIRE_GENERAL | 저압 타이어, 일반도로 | 가까운 서비스센터(블루핸즈 먼저) | 서비스센터까지 서행 | 일반도로 주행 중이라 속도를 낮춰 가까운 서비스센터까지 가도록 안내합니다. |
| CHARGING_CHECK | 충전 경고등(5차 갱신에서 STOP_NOW 를 REST_AREA 로 옮김) | 졸음쉼터와 휴게소 중 가까운 곳 | 안전한 곳에서 점검 | 설명서가 배터리 충전 상태 확인과 점검을 지시하는 경고등이라 안전한 곳에서 점검을 안내합니다. |
| REST_AREA_DEFAULT | 그 밖의 휴게소까지 경고 | 앞쪽 휴게소 | 휴게소까지 주행 가능 | 설명서가 정차를 지시하지 않는 경고등이라 앞쪽 휴게소까지 주행을 안내합니다. |
| RED_CHECK | 정차 지시 없이 점검만 적은 빨간 경고등(LPG 과충전) | 졸음쉼터와 휴게소 중 가까운 곳 | 안전한 곳에서 점검 | 설명서가 정차 지시 없이 점검만 적은 빨간 경고등이라 안전한 곳에서 점검을 안내합니다. |
| AFTER_ARRIVAL_DEFAULT | 도착 후 점검 경고 | 가까운 서비스센터 | 도착 후 점검 | 설명서가 점검만 적은 경고등이라 도착 후 점검을 안내합니다. |

경고등별 기본 판정 규칙(경고등 정보 라벨용): `DecisionRules.defaultRule(lampId: String): DecisionRule?` 도로와 위치를 모를 때 decide 가 고르는 규칙과 같다(STOP_NOW 는 STOP_NOW_ROAD, 충전은 CHARGING_CHECK, LPG 과충전은 RED_CHECK, 그 밖 REST_AREA 는 REST_AREA_DEFAULT, 나머지 AFTER_ARRIVAL_DEFAULT). lampId 는 LampCatalog id(경고등 정보 줄의 lampId), 짝이 없으면 null. 라벨 글은 `defaultRule(id)?.label`.

판정 근거 칸 세 개 예시: 시속 칸은 `speedKmh`, 도로 칸은 `road`, 장소 칸은 `refuge.kind`와 `refugeName`, `refugeMeters`(거리 글은 `SpeechBuilder.formatDistance(meters)` 로 "2.1킬로미터").

저압 타이어 시내와 고속도로는 긴급도(REST_AREA)와 음성 경고 문장이 같다. 다른 곳은 장소(서비스센터와 휴게소), verdictLabel, sentence, 근거 줄(시속, 도로)이다. 고속도로는 길 안내에 휴게소가 있으면 "다음 휴게소까지 12킬로미터입니다. 경유지로 넣으시겠습니까?"를 한 번 더 말한다.

제안과 다른 점: refugeName 과 refugeMeters 는 refuge 안에 있고 같은 이름의 읽기 속성도 둔다. rule 은 String 대신 enum `DecisionRule`(이름 글은 `rule.name`).

## 2. 미리 듣기 장면

`Scenarios.previews: List<Scenario>` 다섯 장면, 순서 그대로
1. `engine_oil` 엔진 오일 압력 경고
2. `low_tire_city` 저압 타이어 경고(시내): 시속 30km, 일반도로. 말하는 문장 "타이어 공기압이 낮습니다. 속도를 낮추고 급격한 조향을 피하십시오." 라벨 "서비스센터까지 서행"
3. `low_tire_highway` 저압 타이어 경고(고속도로): 시속 120km, 고속도로. 같은 경고 문장 다음 "다음 휴게소까지 12킬로미터입니다. 경유지로 넣으시겠습니까?" 라벨 "휴게소까지 서행"
4. `engine_arrival` 엔진 경고, 도착 후 점검
5. `brake_highway` 브레이크 경고, 고속도로

장면 시속(미리 듣기 입력값): engine_oil 50(시내), low_tire_city 30, low_tire_highway 120, engine_arrival 70(국도), brake_highway 110(고속도로). 모든 장면은 정차 단계에서 시속 0을 넣어 정차 후 화면이 열린다.
`Scenarios.basisFor(id: String): DecisionBasis?` 장면을 끝까지 돌린 첫 경고의 판정 근거(한 번 계산해 둠). 미리 듣기에는 공공데이터 위치가 없어 시내 장면의 refuge 는 null 이고, 고속도로 장면은 길 안내의 휴게소(이름 "휴게소", 12000미터)다.
`Scenarios.all` 네 개와 기존 `low_tire` 는 그대로 남아 있다. `Scenarios.byId` 는 두 목록을 모두 찾는다.

## 3. 출발 전 점검

`EngineHub.preTrip: StateFlow<List<PreTripItem>>` 늘 다섯 줄, 순서는 타이어 공기압, 엔진 오일, 브레이크액, 와셔액, 연료.

```kotlin
data class PreTripItem(
    val kind: PreTripKind,       // TIRE, ENGINE_OIL, BRAKE_FLUID, WASHER, FUEL (kind.label 은 "타이어 공기압" 등 화면 이름)
    val status: PreTripStatus,   // OK, CHECK, UNKNOWN
    val valueText: String?,      // "정상", "낮음", "매우 낮음", "높음", "부족", "점검", "64%", 신호가 없으면 null
    val lampId: String?,         // 같은 내용의 설명서 경고등 id(LOW_TIRE_PRESSURE, BRAKE, LOW_FUEL), 없으면 null
)
```
말하기는 엔진이 한다(시동 ON 이고 기어 P 일 때 한 운전에 한 번, 설명서 문장이 있는 항목만). 화면은 표시만 한다.
브레이크액과 저압 타이어는 이번 운전에서 같은 경고 안내가 이미 나갔으면 점검 문장을 말하지 않는다(줄에 들어가 있던 점검 문장도 지움). 연료는 그대로 말한다.
타이어 줄의 바퀴 공기압 비교에 쓰는 `Tire.getLowTirePressure` 값(저압 기준으로 봄)은 문서 본문이 비어 있어 이름으로만 판단했다. 2026. 11. 14. 에뮬레이터에서 바퀴 네 곳 모두 137.0 을 돌려주는 것을 확인했다(docs/PLEOS_VERIFY.md 1번). 경고등 TPMS 판단은 그대로 먼저 쓴다.

## 4. 경고등 정보

정적 값이라 StateFlow 가 아니다. 감시는 Pleos 상수 95종 그대로 하고, 경고등 정보 목록에는 현대차 설명서에서 공식 이름과 계기판 색을 찾은 상수만 싣는다.
- 출처: 현대자동차, 2027 그랜저(GN7) 웹 취급설명서(Pleos Connect 차종), 경고등 및 표시등 확인하기, 37개 항목
- 주소: https://ownersmanual.hyundai.com/full_webhelp/GN7/2027/ko_KR/cluster-check_warn_indic_lights-check_warn_and_ind_lights.html
- `EngineHub.catalog`(= `Catalog95.all`) 경고등 정보 31줄(SDK 순서), `EngineHub.catalogByColor`(= `Catalog95.grouped`, 빨강 8, 노랑 14, 초록과 파랑 9)
- `Catalog95.monitored` 감시 95종, `Catalog95.hiddenIds` 숨긴 64종(설명서에서 이름이나 색을 찾지 못함. 도로 결빙 경고등은 이름은 있으나 색이 없어 숨김)
- `Catalog95.byColor(group)`, `Catalog95.byId(id)`, Pleos 상수 짝이 없는 설명서 경고등 세 개(브레이크 경고등, EBD 경고등, 4륜구동 경고등)는 `Catalog95.manualOnly`

```kotlin
data class CatalogEntry(
    val id: String,              // Pleos 상수 이름 "TPMS"
    val nameKo: String,          // 설명서 이름 "저압 타이어 경고등(TPMS 경고등)"
    val nameSource: String,      // 설명서 쪽 주소(위 주소)
    val manualNo: Int,           // 설명서 항목 번호 13
    val colorGroup: ColorGroup,  // RED(설명서 빨간색), YELLOW(설명서 주황색), GREEN_BLUE(초록색과 파란색)
    val colors: List<ManualColor>, // 표와 본문에 적힌 색 모두. AUTO_HOLD 는 [ORANGE, GREEN, WHITE]
    val tier: Tier?,             // 엔진 판정 갈래(분류표 짝이 없으면 null)
    val manualMeaning: String?,  // ManualGN7.meaning(항목). 정차 후 화면 lamp.manualMeaning 과 같은 함수
    val actions: List<String>,   // ManualGN7.actions(항목). 정차 후 화면 lamp.manualActions 와 같은 함수
    val manualLines: List<String>, // 항목 문장 전부(원문 그대로, 아이콘 글자만 뺌)
    val manualSource: String,    // "현대자동차, 2027 그랜저(GN7) 웹 취급설명서, 경고등 및 표시등 확인하기"
    val iconKind: String,        // 아래 목록
    val lampId: String?,         // 짝지은 LampCatalog id
)
enum class ManualColor { RED, ORANGE, GREEN, BLUE, WHITE, GRAY }
```
iconKind 값: SEAT_BELT, AIRBAG, STEERING, BATTERY, FUEL, ENGINE_OIL, TEMPERATURE, BRAKE, ABS, TIRE, PARKING_BRAKE, GENERIC_WARNING, AUTO_HOLD, ENGINE, LAMP, ESC, DRIVE_ASSIST, DOOR, TURN_SIGNAL, DRIVE_MODE(manualOnly 4륜구동).
같은 설명서 항목을 두 상수가 쓰는 곳: 방향지시등 왼쪽과 오른쪽(비상 경고등/방향지시등), 차로 변경 보조 왼쪽과 오른쪽(고속도로 차로변경 보조 표시등). 이름이 같으니 화면에서 하나로 보여도 된다.
4차 갱신: 정차 후 화면(UiState.ParkedDetail.items[i].lamp.manualMeaning, manualActions)도 2027 그랜저 설명서 원문으로 바뀌어 경고등 정보와 같은 문장이다. 냉각수 수온과 저압 타이어는 같은 설명서의 엔진 과열 쪽과 TPMS 쪽 원문을 조치 방법에 더했다.
1차 계약과 달라진 점: nameSource 가 enum 에서 주소 글로 바뀜, NameSource enum 삭제, manualNo와 colors, manualLines 추가, manualSource 가 null 아님, 목록이 95에서 31로 줄고 숨긴 목록 추가.

## 5. 다시 듣기

`EngineHub.repeatLast()` 마지막 안내를 다시 말한다. 즉시 정차 경고와 대피 문장은 바로(횟수 상한 넘음), 나머지는 정차 중이면 바로, 달리는 중이면 정차 후에 한 번. 마지막 안내가 없으면 아무것도 하지 않는다.
`EngineHub.repeatWaiting: StateFlow<Boolean>` 달리는 중에 눌러 정차 후 말하려고 기다리는 중이면 true. 화면은 이 값을 표시하지 않는다(설명 문장을 화면에 두지 않는 규칙).
기존 `EngineHub.repeat()`(가장 급한 켜진 경고 다시 말하기)은 그대로 있다. MainActivity 의 REPEAT 버튼(ButtonAction.REPEAT)은 2차 갱신에서 `repeatLast()` 를 부르도록 바뀌었다.

## 6. 기록 목록

`EngineHub.logRows: StateFlow<List<LogRow>>` 최근 순(켜진 시각 늦은 것 먼저).

```kotlin
data class LogRow(
    val lampId: String,          // "ENGINE"
    val nameKo: String,          // "엔진 경고등"
    val onAtWallMs: Long,        // 벽시계 밀리초
    val offAtWallMs: Long?,      // 아직 켜져 있으면 null
    val tier: Tier,              // 켜질 때 판정한 갈래
    val timeText: String,        // "오전 9:36부터 켜져 있음", "오전 9:36에 켜졌다가 9:53에 꺼짐", 오늘이 아니면 "11월 10일 " 이 앞에 붙음
    val roadLabel: String?,      // 켜질 때 도로 이름, 없으면 null
)
```
timeText 는 ui/screens/LampTime.kt 와 같은 규칙을 core `LampTimeText.describe` 로 옮긴 글이다. 화면은 LampTime 대신 이 값을 써도 같은 글이 나온다.
제안과 다른 점: timeText 와 roadLabel 을 더함.

## 7. Gleo 와 차 속도 (화면 변경 없음)

- `assets/schema.json` actionCalls 3개: TELL_ACTIVE_WARNING_LAMPS, REPEAT_LAST_GUIDANCE, FIND_NEARBY_REPAIR_SHOP. 수신부 `GleoActionReceiver`
- 차 속도는 CarPropertyManager PERF_VEHICLE_SPEED 로 받아 엔진에 넣는다. 화면 허용 규칙은 `TelltaleVoiceEngine.parkedForScreen()` 그대로
- 새 권한 `android.car.permission.CAR_SPEED` 는 실행 중 승인이 필요해 MainActivity 권한 요청 목록에 더함

음성 문장 이전과 이후 전체 목록은 VOICE_LINES_2027.md(5차 갱신).

## 8. 7차 갱신(2026. 11. 14.): 기록 상세, 설정, 출발 전 점검 상세

- `LogRow` 에 필드 셋 추가
  - `spokenText: String?` 그 점등 때 실제로 말한 경고 문장(처음 말한 문장). 말하지 않았으면(그만, 안내 끔, 상한) null
  - `onTimeText: String` "오전 9:36", 오늘이 아니면 "11월 10일 오전 9:36"
  - `offTimeText: String?` "오전 9:53"(오전과 오후를 늘 붙임), 꺼지지 않았으면 null
- 안내 음성 켜기와 끄기: `EngineHub.voiceEnabled: StateFlow<Boolean>`(기본 true, SharedPreferences "telltale_settings" 에 저장), `EngineHub.setVoiceEnabled(on: Boolean)`
  - 끄면 급하지 않은 경고(휴게소까지, 도착 후 점검), 휴게소 거리, 출발 전 점검, 도착 안내는 말하지 않는다
  - **꺼도 즉시 정차 경고와 고속도로 대피 문장은 말한다.** 운전자가 직접 누른 다시 듣기와 Gleo 질문의 답도 말한다
  - 화면 상태(경고 카드, 정차 후 화면, 기록)는 그대로
- 이번 운전 그만 말하기: `EngineHub.mutedLampIds: StateFlow<Set<String>>`(LampCatalog id), `EngineHub.mute(lampId)`, `EngineHub.unmute(lampId)`, `EngineHub.unmuteAll()`. 새 운전(시동)에서 비워진다
- 출발 전 점검 상세: `PreTripKind.manualMeaning: String?`, `PreTripKind.manualActions: List<String>`, `PreTripKind.manualUrl: String`(모두 2027 그랜저 설명서 원문)
  - TIRE: 경고등 및 표시등 (13) 저압 타이어의 원인과 조치 방법
  - ENGINE_OIL: 엔진 오일 점검하기 쪽(원인 1문장, 조치 방법 3문장)
  - BRAKE_FLUID: 브레이크액 점검하기 쪽(원인 1문장, 조치 방법 3문장)
  - WASHER: 워셔액 보충하기 쪽(원인 없음 null, 조치 방법 3문장)
  - FUEL: 경고등 및 표시등 (12) 연료 부족의 원인과 조치 방법
