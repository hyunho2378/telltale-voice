# Gleo 음성 명령 문장 (2026. 11. 15.)

App Schema(app/src/main/assets/schema.json)에 올린 function 세 개를 운전자가 Gleo에게 어떻게 말할지 정리한 문서다.

## 확인한 범위

| 항목 | 상태 |
|---|---|
| App Schema 등록 | Pleos Connect 차량 에뮬레이터 release_connect_v2.1.0에서 확인함. Gleo(ai.umos.ambientai)가 설치 때 schema.json을 읽어 function 3개로 변환함(2026. 11. 14. 실측, docs/PLEOS_VERIFY.md 3번) |
| 수신부 응답 | adb broadcast로 세 function 모두 APP_ACTION_RESPONSE 회신을 확인함 |
| Gleo가 아래 문장을 듣고 우리 function을 부르는 동작 | 확인하지 못함. 에뮬레이터에 음성을 넣을 길이 없고 Gleo의 글 입력 경로를 찾지 못했다. 아래 문장은 공식 문서 App Schema Writing Guide의 작성 규칙과 function 설명을 보고 적은 예상 문장이며, 실제 인식 여부는 확인하지 못함 |

## 말할 문장

| 말할 문장 | 불릴 function | 앱이 하는 답 |
|---|---|---|
| 지금 켜진 경고등 알려 줘 | TELL_ACTIVE_WARNING_LAMPS | 켜진 경고등 가운데 가장 급한 경고 문장을 말한다. 없으면 "현재 켜진 경고등이 없습니다." |
| 계기판에 뭐 켜졌어 | TELL_ACTIVE_WARNING_LAMPS | 같음 |
| 이 경고등 무슨 뜻이야 | TELL_ACTIVE_WARNING_LAMPS | 같음. 설명서 원인 문장은 화면에서 이어 본다 |
| 지금 차 상태 괜찮아 | TELL_ACTIVE_WARNING_LAMPS | 같음 |
| 경고등 떴는데 지금 세워야 해 | TELL_ACTIVE_WARNING_LAMPS | 가장 급한 경고의 갈래 문장(즉시 정차, 휴게소까지, 도착 후 점검)을 말한다 |
| 방금 안내 다시 말해 줘 | REPEAT_LAST_GUIDANCE | 마지막으로 말한 안내를 한 번 더 말한다 |
| 다시 들려줘 | REPEAT_LAST_GUIDANCE | 같음 |
| 뭐라고 했어 | REPEAT_LAST_GUIDANCE | 같음 |
| 못 들었어 한 번 더 | REPEAT_LAST_GUIDANCE | 같음. 마지막 안내가 없으면 말하지 않는다 |
| 가까운 서비스센터 알려 줘 | FIND_NEARBY_REPAIR_SHOP | 가까운 정비소 이름과 거리를 말한다(이름에 블루핸즈가 들어간 곳 먼저) |
| 근처 블루핸즈 찾아 줘 | FIND_NEARBY_REPAIR_SHOP | 같음 |
| 여기서 제일 가까운 공업사 어디야 | FIND_NEARBY_REPAIR_SHOP | 같음 |
| 차 점검받을 데 알려 줘 | FIND_NEARBY_REPAIR_SHOP | 같음. 정비소를 모르면 말하지 않는다 |

## 문장을 이렇게 적은 까닭

- 공식 문서는 function 이름을 대문자와 밑줄로, 설명은 영어 한 문장에서 두 문장으로 쓰라고 한다. 문장 자체를 스키마에 적는 칸은 없고 Gleo가 설명을 읽어 맥락으로 고른다.
- 그래서 세 function의 설명을 서로 겹치지 않게 썼다. 켜진 경고등 알리기, 마지막 안내 되풀이, 가까운 정비소 찾기로 나뉜다.
- 공식 문서는 설명에 구체적 예시를 넣지 말라고 해서 위 문장은 스키마에 넣지 않고 이 문서에만 둔다.
- 운전 중 손 조작을 줄이는 길이 Gleo 호출이라 세 function 모두 화면 없이 도는 service(sendBroadcast) 방식이다.

## 출처

- Pleos Connect 개발자 가이드, App Schema Writing Guide(앱 개발과 연동 가이드, Gleo Interface 구현 가이드)
- 차량 에뮬레이터 실측 기록은 docs/PLEOS_VERIFY.md 3번
