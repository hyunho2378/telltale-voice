# Pleos 차량 에뮬레이터 실측 (2026. 11. 14. 21:46~21:57)

- 기기: Pleos Connect 차량 에뮬레이터 crp_avd_arm64, release_connect_v2.1.0(user/test-keys), 화면 2560 x 1440, adb -s emulator-5554
- 앱: debug 빌드(이 커밋 코드), 8번만 release 서명 APK
- 신호 입력: Virtual Console(http://127.0.0.1:8088/) 의 HTTP 쓰기 주소(/vehicle/gear, /vehicle/controls, /vehicle/telltales/simulator). 콘솔은 브라우저 세션 신호(/browser/session)가 있어야 쓰기를 받는다(없으면 503 emulator_inactive)
- 화면 캡처: Aside 세션 artifacts/pleos_verify/ 01~09 png

## 항목별 결과

| 번호 | 항목 | 상태 |
|---|---|---|
| 1 | 새 어댑터 신호 | 차속, 기어, 저압 기준은 Pleos에서 확인함. 와셔액, 연료 비율, 엔진 오일 수준, 바퀴 공기압은 이 이미지가 속성을 내주지 않아 확인 불가(크래시 없이 확인 중으로 남음). 브레이크액과 주차 브레이크는 콘솔에 쓰기 항목이 없어 값 변화 확인 불가. 시동 속성이 없어 출발 전 점검이 멈추던 문제는 고침 |
| 2 | 정차 뒤 화면 허용 규칙 | Pleos에서 확인함(기어 D 시속 0, 기어 P 시속 0, 기어 P 시속 10). 주차 브레이크 켜짐은 콘솔에 쓰기 항목이 없어 확인 불가 |
| 3 | Gleo Interface | App Schema 등록은 Pleos에서 확인함(function 3개 변환). Gleo가 우리 수신부를 실제로 부르는 일은 음성 입력이 필요해 확인 불가 |
| 4 | 음성 실제 재생 | Pleos에서 확인함(알림음, 음성 시작, UpdateTtsDone 순서와 시각). 기어 D 시속 40 주행 중에도 음성이 나옴 |
| 5 | 오디오 포커스 | 고침. 고치기 전에는 포커스 요청 없이 USAGE_MEDIA 로 재생됨. 고친 뒤 GAIN_TRANSIENT_MAY_DUCK(C 조건)과 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE 를 Pleos에서 확인함 |
| 6 | 주행 중 앱 화면 가림 | Pleos에서 확인함. distractionOptimized 선언이 있어도 기어 D(시속 40과 시속 0 모두)에서 "주행 중에는 사용할 수 없습니다"가 앱 영역을 덮음. 이때도 음성은 끝까지 나옴 |
| 7 | 백그라운드 서비스 | Pleos에서 확인함. 설정 앱이 앞에 있을 때 엔진 오일 압력 켜짐을 받아 알림음과 음성을 냄 |
| 8 | release 서명 APK | Pleos에서 확인함(엔진 오일 압력 음성과 정차 뒤 화면). 그 뒤 uninstall 하고 debug 다시 설치 |

## 1. 새 어댑터 신호

Pleos에서 실제로 확인함
- 차속 PERF_VEHICLE_SPEED: 콘솔 speed 30, 40, 10, 0 km/h 를 m/s 로 받음. 권한 CAR_SPEED 는 이 이미지에서 설치 때 granted=true 로 붙어 있고 pm revoke 뒤에도 값이 들어와 승인 창이 뜨지 않음(승인 창 흐름은 이 이미지에서 볼 수 없음)
```
21:48:45.159 I CarPowertrain: PERF_VEHICLE_SPEED = 8.333334      (30 km/h)
21:48:55.464 I CarPowertrain: PERF_VEHICLE_SPEED = 11.111112     (40 km/h)
21:50:34.478 I CarPowertrain: PERF_VEHICLE_SPEED = 2.777778      (10 km/h)
```
- 기어 GEAR_SELECTION: 콘솔 Park 와 Drive 가 4 와 8 로 들어옴(콘솔 표시 이름은 VENDOR_GEAR_SELECTION 이지만 표준 속성도 함께 바뀜)
```
21:48:50.822 I CarPowertrain: GEAR_SELECTION = 4
21:48:52.745 I CarPowertrain: GEAR_SELECTION = 8
```
- 저압 기준 Tire.getLowTirePressure: 바퀴 네 곳 모두 CRITICALLY_LOW_TIRE_PRESSURE 속성 값 137.0 을 돌려줌. 이름 그대로 저압 기준 값이 맞음(단위는 문서에 없고 콘솔 타이어 공기압 항목 단위가 kPa)
```
21:46:44.177 I [Pleos][SDK]b: getProperty_onResult eventPropKey CRITICALLY_LOW_TIRE_PRESSURE eventAreaId 2 eventValue 137.0
21:46:44.180 I [Pleos][SDK]b: getProperty_onResult eventPropKey CRITICALLY_LOW_TIRE_PRESSURE eventAreaId 4 eventValue 137.0
```
- 주차 브레이크 PARKING_BRAKE_ON: 등록되고 처음 값 false 를 받음
```
21:48:45.159 I CarPowertrain: PARKING_BRAKE_ON = false
```

확인 불가와 이유
- 와셔액(0x2120071e), 연료 비율(0x21400be5), 엔진 오일 수준(ENGINE_OIL_LEVEL), 바퀴 공기압(TIRE_PRESSURE): 이 이미지의 차량 속성 목록에 없음. 어댑터는 오류만 남기고 크래시 없이 출발 전 점검 줄을 확인 중으로 둠
```
21:46:44.233 I EngineHub: Pleos Vehicle: 와셔액 읽기: java.lang.IllegalArgumentException: property ID is not in carPropertyConfig list, and so it is not supported: 0x2120071e
21:46:44.234 I EngineHub: Pleos Vehicle: 엔진 오일 읽기: ... not supported: ENGINE_OIL_LEVEL
21:46:44.234 I EngineHub: Pleos Vehicle: 연료 비율 읽기: ... not supported: 0x21400be5
21:46:44.237 I EngineHub: Pleos Vehicle: 타이어 공기압 RIGHT_FRONT: ... not supported: TIRE_PRESSURE
```
- 브레이크액(BRAKE_FLUID_LEVEL): 등록 오류는 없으나 Virtual Console 항목 63개에 브레이크액 쓰기가 없어 값 변화를 볼 수 없음
- 주차 브레이크 켜짐: 콘솔에 PARKING_BRAKE_ON 쓰기가 없음(경고등 목록의 Parking Brake 는 경고등 표시만 바꿈)
- 콘솔의 타이어 공기압 항목은 숨김(visible false)이고 이 이미지가 TIRE_PRESSURE 를 내주지 않음

고침
- 시동 IGNITION_STATE 가 이 이미지에 없어 시동 신호가 한 번도 오지 않았고, 출발 전 점검 말하기(시동 ON 과 기어 P 조건)가 영영 돌지 않았음. CarPowertrainAdapter 가 속성이 없으면 시동 켜짐으로 보도록 고침
```
(고치기 전) 21:46:43.423 E CarPropertyManager: registerCallback:  propertyId is not in carPropertyConfig list:  IGNITION_STATE
(고친 뒤)   21:48:44.329 I EngineHub: CarPropertyManager: 속성 IGNITION_STATE 없음
            21:48:44.329 I EngineHub: 시동 켜짐
```
- 차 신호 값이 로그에 하나도 남지 않아 실측이 불가능했음. CarPowertrainAdapter 에 속성 값 로그(태그 CarPowertrain, 차속은 2초에 한 줄)를 더함

## 2. 정차 뒤 화면 허용 규칙

엔진 오일 압력 경고등을 켠 상태에서 확인함(캡처 03, 04, 05)
- 기어 D, 시속 0: 플랫폼이 앱 영역을 "주행 중에는 사용할 수 없습니다"로 덮음(앱 규칙도 D 이고 주차 브레이크 해제라 정차 뒤 화면을 열지 않음)
- 기어 P, 시속 0: 정차 뒤 화면(바로 정차, 긴급견인 전화, 자세히 보기)이 열림
- 기어 P, 시속 10: 정차 뒤 화면이 닫히고 주행 경고 카드(음성 안내 대기, 다시 듣기)로 바뀜. 플랫폼은 P 라 가리지 않음
```
21:50:29.826 I CarPowertrain: GEAR_SELECTION = 4
21:50:32.477 I CarPowertrain: PERF_VEHICLE_SPEED = 0.0
21:50:34.478 I CarPowertrain: PERF_VEHICLE_SPEED = 2.777778
```
확인 불가: 기어 D 와 주차 브레이크 켜짐(콘솔에 주차 브레이크 쓰기 없음). 오토홀드 신호는 Vehicle SDK 함수 목록에 없음

## 3. Gleo Interface

Pleos에서 실제로 확인함: Gleo(ai.umos.ambientai)가 설치 때 assets/schema.json 을 읽어 function 3개를 등록함
```
21:50:09.937 I [GAA]: [Schema] InstalledPackages User App Packages : [com.telltalevoice.app]
21:50:09.938 I [GAA]: [Schema] Loaded schema.json from com.telltalevoice.app:
21:50:09.938 I [GAA]:         "name": "TELL_ACTIVE_WARNING_LAMPS",
21:50:09.944 I ami-sdk : ToolRepository | setAppCapability | start | schemaCount=4 appVersion=4.2.0.15 schemaAppBlackList=[]
21:50:09.945 I ami-sdk : ToolRepository | setAppCapability | converted | package=com.telltalevoice.app functionCount=3
```
- 같은 목록에 시스템 앱 3개(vehiclecontrol 2개, 내비 8개, 미디어 8개)와 함께 21개 function 으로 올라감. 검토 승인 없이도 에뮬레이터에서는 바로 등록됨
- 수신부 응답은 11. 11. 폰 에뮬레이터에서 adb broadcast 로 확인(세 function 모두 APP_ACTION_RESPONSE 회신)

확인 불가와 이유: Gleo가 우리 function 을 실제로 부르려면 Gleo에 음성 질문을 넣어야 함. 이 샌드박스는 에뮬레이터 마이크에 소리를 넣을 수 없고, Gleo의 글 입력 경로를 찾지 못함. 필요한 설정은 따로 없음(스키마 등록, 매니페스트 action filter, exported receiver 모두 갖춤). 사용자가 에뮬레이터에서 "지금 켜진 경고등 알려 줘"라고 말해 logcat 의 GleoAction 태그를 보면 확인됨

## 4. 음성 실제 재생

Pleos에서 실제로 확인함(기어 D 시속 40 주행 중, 앱 화면은 플랫폼이 가린 상태)
```
21:52:36.662 I PleosTts: 포커스 요청 MAY_DUCK 결과=1
21:52:36.662 I PleosTts: 알림음 시작
21:52:36.927 I PleosTts: 알림음 끝
21:52:36.927 I PleosTts: 음성 요청: 엔진 오일 압력 경고등이 켜졌습니다. 도로 옆으로 정차하십시오.
21:52:36.932 I [GAAS]a : updateTts cmd(UpdateTtsStart) id(com.telltalevoice.app:ondevice)
21:52:41.273 I [GAAS]a : updateTts cmd(UpdateTtsDone) id(com.telltalevoice.app:ondevice)
21:52:41.274 I PleosTts: 음성 끝
```
- 알림음 265ms 뒤 음성 요청, 5ms 뒤 재생 시작, 4.3초 동안 말하고 끝남. 화자 seoyoon, 한국어
- 앱을 처음 켠 뒤 첫 음성은 음성 모델 읽기에 843ms 가 더 걸림(21:49:10.741 model load elapsed: 843ms)
- 휴게소까지 경고(저압 타이어)는 브레이크 페달을 밟는 중이면 설계대로 최대 10초 미룬 뒤 말함

## 5. 오디오 포커스

고치기 전(21:49, 21:51): 우리 앱은 포커스를 요청하지 않았고, Pleos 음성 서비스가 usage=1(USAGE_MEDIA)로 재생함. 포커스 스택이 비어 있어 음악과 같은 묶음으로 섞임
```
21:51:28.158 I [GAAS]AudioTrackManagerImpl: getAudioDeviceInfo zoneId=-1 usage=1 contentType=1
Audio Focus stack entries: (없음)
```
문서 대조: Pleos 오디오 포커스 개발 가이드는 동시 재생(C)을 원하면 들어오는 요청이 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 을 쓰라고 하고, 오디오 컨텍스트 표에서 ASSISTANCE_NAVIGATION_GUIDANCE 는 NAVIGATION 컨텍스트임

고친 뒤(21:52): PleosTtsSpeaker 가 TextToSpeech.setAudioAttributes(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, CONTENT_TYPE_SPEECH)를 설정하고, 말하는 동안 GAIN_TRANSIENT_MAY_DUCK 을 요청하고 끝나면 반환함
```
focus owners: pack: com.telltalevoice.app -- gain: GAIN_TRANSIENT_MAY_DUCK -- attr: usage=USAGE_ASSISTANCE_NAVIGATION_GUIDANCE content=CONTENT_TYPE_SPEECH
21:52:36.938 I [GAAS]AudioTrackManagerImpl: getAudioDeviceInfo zoneId=-1 usage=12 contentType=1
```
확인 불가: 내비 안내나 음악이 실제로 재생 중일 때 duck 되는 소리(이 샌드박스는 에뮬레이터 소리를 들을 수 없음)

## 6. 주행 중 앱 화면 가림

Pleos에서 실제로 확인함(캡처 02, 03)
- 기어 D 에서는 시속 40 과 시속 0 모두 앱 영역을 "주행 중에는 사용할 수 없습니다"가 덮음. 매니페스트의 distractionOptimized 선언으로는 풀리지 않음
- 이때도 경고 음성(알림음 포함)은 끝까지 나옴(4번 로그). 왼쪽 계기 영역에는 엔진 오일 압력 경고등이 보임
- 기어 P 로 바꾸면 덮개가 사라지고 정차 뒤 화면이 보임

문서에서 다시 찾은 내용
- 지역별 주행 규제 정책: "앱에서 Distraction Optimization(DO)를 선언하지 않았을 때, 주행 상황에 따라 앱 사용 가능 여부가 달라집니다."
- 주행 규제 개발 가이드: 앱은 CarUxRestrictions(isRequiresDistractionOptimization)로 제한 상태를 확인하고 제한 대상 기능을 줄여야 함
- 앱 카테고리 문서: 카테고리(Maps/Navigation, Mobility Services 등)와 주행 상태에 따라 주행 중 기능 범위가 달라짐
- distractionOptimized 선언 외에 서드파티 앱이 주행 중 화면을 열 수 있는 우회 방법은 문서에서 찾지 못함. 덮개가 계속 나오는 이유로 가능한 것은 앱 카테고리 승인(마켓 심사)이나 플랫폼 허용 목록이지만 문서에 명시는 없음

## 7. 백그라운드 서비스

Pleos에서 실제로 확인함(캡처 07): 설정 앱(com.android.car.settings)이 앱 영역 앞에 있을 때 엔진 오일 압력 켜짐을 받아 말함. GuardService 는 foreground(specialUse)로 떠 있음
```
topResumedActivity=ActivityRecord{... com.android.car.settings/.Settings_Launcher_Homepage}
ServiceRecord{... com.telltalevoice.app/.GuardService} isForeground=true
21:54:38.826 I PleosTts: 포커스 요청 MAY_DUCK 결과=1
21:54:38.826 I PleosTts: 알림음 시작
21:54:39.093 I PleosTts: 음성 요청: 엔진 오일 압력 경고등이 켜졌습니다. 도로 옆으로 정차하십시오.
21:54:43.398 I PleosTts: 음성 끝
```

## 8. release 서명 APK

Pleos에서 실제로 확인함(캡처 09): apk/루시드드림_경고등음성안내_release.apk(2026. 11. 13. 빌드)를 설치해 기어 D 시속 40 에서 엔진 오일 압력을 켜고 음성을 들은 뒤 기어 P 시속 0 에서 정차 뒤 화면(뜻, 해야 할 일 2줄, 가까운 정비소)을 확인함. 크래시 0건. 확인 뒤 uninstall 하고 debug 를 다시 설치함
```
21:56:00.257 D [GAAS][SDK]OnDeviceStreamTts: doSpeak(com.telltalevoice.app) text=엔진 오일 압력 경고등이 켜졌습니다. 도로 옆으로 차를...
21:56:05.575 I [GAAS]a : updateTts cmd(UpdateTtsDone) id(com.telltalevoice.app:ondevice)
```
- 이 release 는 11. 13. 빌드라 11. 14. 고친 내용(바로 정차 행동 문장 단축, 시동 대체, 오디오 포커스)이 들어 있지 않음. 화면 작업이 커밋된 뒤 release 를 다시 빌드해 제출 파일을 바꿔야 함
