# 경고등 음성 안내

계기판에 경고등이 켜지면 지금 멈춰야 하는지, 휴게소까지 가도 되는지, 도착 후 점검하면 되는지를 현대차 설명서 문장으로 짧게 말해 주는 Pleos Connect 앱이다.

현대자동차그룹 차량용 앱 공모전(Pleos Connect) 출품작이며, 참가자 코드 이름은 telltale-voice다.

## 하는 일

### 주행 중 음성 안내
1. 경고등은 급한 정도에 따라 즉시 정차(STOP_NOW)와 휴게소까지(REST_AREA), 도착 후 점검(AFTER_ARRIVAL)으로 나뉜다.
2. 음성 문장은 2027 그랜저(GN7) 웹 취급설명서(Pleos Connect 차종)의 원문 낱말만 골라 짧게 줄였다. 즉시 정차 경고는 행동을 4어절 안에서 말한다. 경고등마다 어떤 문장을 말하는지는 docs/VOICE_LINES_2027.md에, 설명서 원문과 맞춰 본 기록은 docs/MANUAL_AUDIT_2027.md에 있다.
3. 4어절 지시문은 두 어절 다음에 한 번 쉬어 읽는다("비상 경고등을, 켜고 정차하십시오"). TextToSpeech SDK로는 쉬는 길이를 정할 수 없어서, 음성 엔진에 넘기는 글에만 쉼표를 넣고 화면 글에는 넣지 않는다.
4. 같은 경고등이라도 달리는 도로와 속도에 따라 안내가 달라진다. 저압 타이어가 고속도로에서 켜지면 "휴게소까지 서행", 일반도로에서 켜지면 "서비스센터까지 서행"으로 안내한다. 어떤 속도와 도로, 대피 장소를 보고 정했는지도 함께 기록해 둔다.
5. 즉시 정차 경고는 알림음에 이어 바로 말하며, 경고가 꺼지거나 차가 설 때까지 60초마다 다시 말한다. 다른 경고는 운전자가 회전하려 하거나 브레이크를 밟고 있으면 10초까지 기다렸다가 말하고, 한 번 주행하는 동안 세 번을 넘기지 않는다. 후진할 때는 소리를 내지 않고 화면에만 띄운다.
6. 안내 음성은 내비게이션 안내와 같은 용도로 재생되므로, 말하는 동안에는 음악 같은 다른 소리가 작아진다.
7. 고속도로에서 차를 세우면 비상등을 켜고 트렁크를 연 다음 가드레일 밖으로 피하고 견인을 요청하도록 차례대로 안내한다.
8. 앱 화면을 닫아 두어도 foreground 서비스가 경고등 신호를 받아 안내한다.
9. Gleo에게 "지금 켜진 경고등 알려 줘"나 "방금 안내 다시 말해 줘", "가까운 서비스센터 알려 줘"라고 물으면 이 앱이 답하도록 App Schema(app/src/main/assets/schema.json)와 수신부를 넣었다.

### 화면
화면은 차가 시속 5km 이하로 서 있고 기어가 P이거나 주차 브레이크가 걸려 있을 때 볼 수 있다(Pleos 개발 문서 주행 규제 흐름).
차속 신호는 2.1초쯤마다 들어오기 때문에, 기어 P나 주차 브레이크 신호가 오면 앞서 받은 차속은 버리고 차가 멈춘 상태로 본다. 그래서 기어를 P에 넣는 순간 주행 중 화면이 정차 후 안내로 바뀐다. 다시 시속 5km를 넘거나 기어를 D에 넣으면 주행 중 화면으로 돌아간다.
1. 차량 상태 홈: 지금 켜진 경고등과 경고 음성 안내가 켜져 있는지를 맨 위에 보인다. 그 아래에서 타이어 공기압과 엔진 오일, 브레이크액, 와셔액, 연료를 출발 전에 점검하고, 서비스센터 찾기와 경고 이력으로 넘어갈 수 있다.
2. 출발 전 점검 상세: 항목을 누르면 지금 상태와 함께 2027 설명서의 원인과 조치 방법을 보인다. 값을 아직 받지 못했으면 "확인 중", 차량이 그 값을 내주지 않으면 "확인 불가"로 보인다(core PreTripSignal).
3. 정차 후 안내: 첫 화면에는 판정과 경고등 이름, 바로 할 행동, 자세히 보기만 둔다. 바로 할 행동은 즉시 정차 경고면 견인 요청, 그 밖이면 서비스센터 찾기다. 자세히 보기를 누르면 보험사 긴급출동과 다시 듣기, 켜진 시각, 설명서의 원인과 조치 방법이 펼쳐진다.
4. 경고 이력: 지난 경고를 하나 누르면 언제 켜지고 꺼졌는지, 그때 어떤 문장으로 안내했고 어느 도로를 달리고 있었는지, 조치 방법은 무엇인지 보인다.
5. 경고등 정보: 앱은 계기판 경고등 95종의 신호를 받는다. 이 가운데 설명서에서 공식 이름과 계기판 색을 확인한 31종을 빨강과 노랑, 초록과 파랑 색으로 나눠 보여 준다.
6. 설정: 경고 음성 안내를 끌 수 있지만, 꺼 두어도 즉시 정차 경고는 계속 안내한다. 운전자가 안내를 일시 정지한 경고등은 여기에 한 줄씩 나오며, 해제하면 다시 안내한다. 마지막 안내를 다시 듣거나 음성 미리 듣기로 넘어갈 수도 있다.
7. 음성 미리 듣기: 차 신호가 없어도 실제 주행처럼 안내를 들어 볼 수 있다. 장면은 엔진 오일 압력과 시내의 저압 타이어, 고속도로의 저압 타이어, 엔진 점검, 고속도로의 브레이크액이다.

## APK 파일 위치

제출용 APK는 이 저장소의 `apk/루시드드림_경고등음성안내_release.apk` 하나다.

| 항목 | 값 |
|---|---|
| 경로 | apk/루시드드림_경고등음성안내_release.apk |
| 크기 | 42,984,128바이트 |
| SHA-256 | bb4714afde8344f5d032b922c1189548d05db5c68c4d5e732a4fb5bbea6a5a7c |
| 빌드 종류 | release(`./gradlew :app:assembleRelease`) |
| 서명 | 참가자 release 키, APK Signature Scheme v2, RSA 2048 |
| 서명자 | CN=telltale-voice |
| 서명 인증서 SHA-256 | ba2ecec573006e3acdd164fd9312342cd9560f43522ee87b4b0cf21263c4c8da |
| 서명 검증 | `apksigner verify --print-certs` 통과 |

내려받은 파일이 같은지 확인하는 명령이다.

```
shasum -a 256 apk/루시드드림_경고등음성안내_release.apk
```

디버그 APK는 디버그 키로 서명되어 심사 대상이 아니므로 저장소에 넣지 않는다(.gitignore).

## 실행 방법

1. APK를 설치한다.

```
adb install apk/루시드드림_경고등음성안내_release.apk
```

2. 앱 목록에서 "경고등 음성 안내"를 연다. 처음 열면 위치와 알림 권한을 묻는다.
3. Pleos Connect 차량 에뮬레이터에서는 Virtual Console의 Telltales(Safety)에서 "Engine Oil Pressure"나 "TPMS"를 켜고, Gear Selection을 D로 바꾸면 주행 중 음성 안내를, P로 바꾸면 정차 후 안내를 볼 수 있다.
4. 차 신호가 없는 기기에서는 설정의 음성 미리 듣기에서 장면을 골라 들어 본다.

## 빌드

JDK 17이 필요하다. 안드로이드 SDK 경로는 local.properties의 sdk.dir로 잡는다.

```
./gradlew :core:test
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

release 서명 키는 저장소 밖 ../keystore/keystore.properties 에서 읽는다(storeFile, storePassword, keyAlias, keyPassword). 이 파일이 없으면 release APK는 서명 없이 만들어진다. 키 파일과 비밀번호는 저장소에 넣지 않는다.

## 개발에 사용한 Pleos Connect 에뮬레이터와 화면 규격

| 항목 | 값 |
|---|---|
| 에뮬레이터 이미지 | Pleos Connect v2.1.0 APIs(arm64-v8a, Android Automotive) |
| 빌드 이름 | release_connect_v2.1.0(user/test-keys) |
| Android API | 34 |
| 기기 정의 | 42dot Pleos Connect v2.1.0(AVD 이름 pleos_connect, adb 이름 crp_avd_arm64) |
| 화면 해상도 | 2560 x 1440px |
| 화면 밀도 | 160dpi(1dp = 1px이므로 dp 기준도 2560 x 1440) |
| 방향 | 가로 고정 |
| 앱 창 실측 | 1682 x 1210dp(홈 화면 두 칸 기준, docs/PLEOS_EMPTY_AREA.md 측정 39장) |

앱 화면은 Pleos 디자인 시스템 폼팩터 네 모드의 값을 모두 넣어 두고, 앱 창 높이로 모드를 고른다(app/src/main/java/com/telltalevoice/app/ui/pleos/PleosTheme.kt의 formFactorFor). 기준값은 Pleos 디자인 시스템 원격 변수 묶음 form_factor의 window/apps 값이다.

| 폼팩터 | 앱 창 가로 x 높이(dp) | 주행 중 화면(dp) |
|---|---|---|
| Connect-S | 1700 x 1184 | 840 x 1280 |
| Connect-L | 1274 x 855 | 628 x 939 |
| Connect-W | 1640 x 810 | 1040 x 850 |
| Connect-Waterfall | 1480 x 802 | 전용 화면이라 창 크기로 고르지 않음 |

에뮬레이터 화면(2560 x 1440)에서 앱 창 높이는 1184dp 이상이므로 Connect-S 모드로 동작한다. 글자 단계와 치수, 그림자는 폼팩터마다 다른 값을 쓴다(generated/PleosDimens.kt, PleosType.kt, PleosShadows.kt).

## 테스트 환경 및 필요한 설정

필요한 설정이다.

1. Android Studio에서 Device Manager로 Automotive 폼 팩터, 시스템 이미지 Pleos Connect v2.1.0 APIs(API 34)로 AVD를 만든다.
2. 빌드에는 JDK 17과 안드로이드 SDK가 필요하다. SDK 경로는 local.properties의 sdk.dir로 잡는다.
3. Pleos SDK 의존성은 Maven 저장소 https://nexus-playground.pleos.ai/repository/maven-releases/ 에서 받는다(settings.gradle.kts).
4. 앱을 처음 열면 위치와 알림 권한을 묻는다. 에뮬레이터에서는 `adb install -r -g` 로 권한을 미리 줄 수 있다.
5. 차량 신호는 에뮬레이터의 Virtual Console에서 바꾼다. 경고등은 TELLTALES의 Safety 도메인, 주행 상태는 Gear Selection과 차 속도다.
6. 차량 신호가 없는 기기에서는 설정의 음성 미리 듣기로 안내를 들어 본다.

실제로 확인한 환경만 적는다. 자세한 기록은 docs/PLEOS_VERIFY.md에 있다.

| 환경 | 확인한 내용 |
|---|---|
| Pleos Connect 차량 에뮬레이터 release_connect_v2.1.0, 2560 x 1440(2026. 11. 11.) | Virtual Console 신호 5종(TELLTALES Safety 도메인 "Engine Oil Pressure" 켜기와 끄기, "TPMS" 켜기와 끄기, Gear Selection D와 P 변경)만으로 홈에서 경고 음성, 정차 후 화면 전환, 꺼짐 기록("켜졌다가 꺼짐") 확인 |
| 같은 에뮬레이터(2026. 11. 14.) | 차 속도와 기어 신호 수신, 정차 후 화면 허용 규칙(기어 P 시속 0에서 열림, 시속 10에서 닫힘), 알림음 뒤 음성 재생과 재생 완료 신호, 주행 중 음성, 오디오 포커스, 다른 앱이 앞에 있을 때 음성, Gleo의 App Schema 등록(기능 3개), release 서명 APK 실행 |
| 안드로이드 폰 에뮬레이터(sdk_gphone64_arm64, Android 17, API 37, 2026. 11. 13.) | release APK 설치와 실행, 홈 화면, 음성 미리 듣기(엔진 오일 압력), 디버그 전용 폼팩터 스위치가 release에서 동작하지 않음 |
| core 단위 시험(JDK 17) | 593건 모두 통과(./gradlew :core:test) |

Pleos 에뮬레이터에서 확인하지 못한 항목
- 와셔액, 연료 비율, 엔진 오일 수준, 바퀴 공기압: 이 에뮬레이터 이미지에 해당 차량 속성이 없어 값을 받지 못한다. 앱은 오류 없이 출발 전 점검 칸에 "확인 불가"를 보인다.
- 브레이크액 수준과 주차 브레이크 체결: Virtual Console에 값을 바꾸는 항목이 없다.
- Gleo가 우리 앱 기능을 실제로 부르는 동작: Gleo에 음성 질문을 넣지 못했다. 수신부의 응답은 adb broadcast로 확인했다.
- 주행 중(기어 D)에는 플랫폼이 앱 화면을 "주행 중에는 사용할 수 없습니다"로 가리고, 경고 음성과 알림음만 나온다.

## 구조

| 경로 | 내용 |
|---|---|
| core | 순수 Kotlin(JVM) 판단 엔진. 안드로이드와 Pleos SDK를 모른다. 단위 시험 593건 |
| core/.../publicdata | CSV 파서, 위경도 거리 계산, 진행 방향 앞쪽 고르기 |
| app | 안드로이드 앱(minSdk 28, Compose, Pleos 디자인 시스템 가로 화면) |
| app/.../signal | 차 신호와 음성 출력 어댑터 인터페이스 |
| app/.../signal/pleos | Pleos Vehicle 2.3.2(경고등, 브레이크액, 엔진 오일, 와셔액, 연료 비율, 타이어 공기압, 문, 브레이크 페달), NaviHelper, TextToSpeech, CarPropertyManager(기어, 시동, 주차 브레이크, 차 속도) 호출부 |
| app/src/main/assets | App Schema(schema.json)와 공공데이터 CSV 3개 |
| docs | 화면과 엔진 계약, 화면 문구 기준, 음성 문장 목록, 설명서 원문 대조 기록, Pleos 실측 기록, Gleo 음성 명령 문장 |
| apk | 제출용 release APK |

Pleos Maven 저장소는 https://nexus-playground.pleos.ai/repository/maven-releases/ 이다.

## 공공데이터 출처

원본은 공공데이터포털이며 2026. 11. 11.에 내려받아 UTF-8로 바꾸고 필요한 열만 남겼다.

| 파일 | 원본 | 행 수 |
|---|---|---|
| rest_areas.csv | 한국도로공사 전국휴게소정보표준데이터 | 215 |
| drowsy_shelters.csv | 전국졸음쉼터표준데이터 | 253 |
| repair_shops.csv | 전국자동차정비업체표준데이터(영업 중) | 34,313 |

## 설명서 출처

- 현대자동차, 2027 그랜저(GN7) 웹 취급설명서, 경고등 및 표시등 확인하기, 타이어 공기압 경보 시스템(TPMS), 엔진 과열(2026. 11. 11. 열람), 엔진 오일 점검하기, 브레이크액 점검하기, 워셔액 보충하기(2026. 11. 14. 열람)

## 라이선스

MIT(LICENSE 파일). 공공데이터와 현대차 설명서 문장의 권리는 각 원 저작권자에게 있다.
