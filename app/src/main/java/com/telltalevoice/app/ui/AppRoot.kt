package com.telltalevoice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.app.Activity
import androidx.compose.foundation.layout.size
import com.telltalevoice.app.BuildConfig
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.telltalevoice.app.EngineHub
import com.telltalevoice.app.ui.pleos.Pleos
import com.telltalevoice.app.ui.pleos.PleosTheme
import com.telltalevoice.app.ui.pleos.PleosToast
import com.telltalevoice.app.ui.pleos.formFactorFor
import com.telltalevoice.app.ui.pleos.generated.PleosFormFactor
import com.telltalevoice.app.ui.pleos.generated.pleosDimensOf
import com.telltalevoice.app.ui.screens.LampTime
import com.telltalevoice.app.ui.screens.V2Data
import com.telltalevoice.app.ui.sys.HomePage
import com.telltalevoice.app.ui.sys.ActiveLamp
import com.telltalevoice.app.ui.sys.DriverCtx
import com.telltalevoice.app.ui.sys.LocalDriver
import com.telltalevoice.app.ui.sys.SysSettings
import com.telltalevoice.app.ui.sys.MutedLamp
import com.telltalevoice.app.ui.sys.SysVoiceTest
import com.telltalevoice.app.ui.sys.SysAlert
import com.telltalevoice.app.ui.sys.SysCatalog
import com.telltalevoice.app.ui.sys.SysEvac
import com.telltalevoice.app.ui.sys.SysHome
import com.telltalevoice.app.ui.sys.SysLog
import com.telltalevoice.app.ui.sys.SysParked
import com.telltalevoice.app.ui.sys.SysPreview
import com.telltalevoice.app.ui.screens.SceneView
import com.telltalevoice.app.ui.screens.BasisView
import com.telltalevoice.core.LampCatalog
import androidx.compose.runtime.remember
import com.telltalevoice.core.ButtonAction
import com.telltalevoice.core.Scenarios
import com.telltalevoice.core.UiState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.telltalevoice.app.ui.screens.PreviewHistory
import com.telltalevoice.app.ui.screens.Shops

/** 창 모양. 가로 창은 두 칸, 세로 창은 한 칸으로 놓는다. */
/** compact: 창 폭이 키트 최소 세로 앱 창(window/apps/portrait_width, connect-l 628)보다 좁음(휴대폰 등). */
data class WindowShape(val landscape: Boolean, val compact: Boolean = false)

/**
 * 화면 상태 갈래
 * - 신호 대기(UiState.Idle, 미리 듣기 없음): HomeScreen. 앱 소개, 지금 상태, 경고 안내 미리 듣기 목록.
 * - 미리 듣기 대기와 끝(UiState.Idle, 미리 듣기 고름): PreviewWaitScreen. 위쪽 닫기 하나.
 * - 주행 중 경고(Driving)와 고속도로 대피(Evacuation): AlertScreen. 글 3줄 이하, 버튼 2개 이하, 스크롤과 움직임 없음.
 * - 정차 뒤(ParkedDetail): ParkedScreen. 설명서 문장과 기록, 가까운 서비스센터. 차가 서 있어서 스크롤 허용.
 */
@Composable
fun AppRoot(onAction: (ButtonAction) -> String?) {
    val ui by EngineHub.ui.collectAsState()
    val demo by EngineHub.demo.collectAsState()
    val speaking by EngineHub.speaking.collectAsState()
    val shops by EngineHub.nearbyShops.collectAsState()
    // 손님이 고른 미리 듣기 id. 시간표가 끝나도 닫기를 누를 때까지 남긴다.
    var session by rememberSaveable { mutableStateOf<String?>(null) }
    var toast by rememberSaveable { mutableStateOf<String?>(null) }
    // 다시 듣기는 마지막 안내를 다시 말한다(core repeatLast). 나머지 버튼은 MainActivity 가 받는다.
    val act: (ButtonAction) -> Unit = { if (it == ButtonAction.REPEAT) EngineHub.repeatLast() else toast = onAction(it) }
    val liveBasis by EngineHub.basis.collectAsState()
    val preTrip by EngineHub.preTrip.collectAsState()
    val logRows by EngineHub.logRows.collectAsState()
    val voiceOn by EngineHub.voiceEnabled.collectAsState()
    val muted by EngineHub.mutedLampIds.collectAsState()
    val context = LocalContext.current
    val scenario = session?.let { Scenarios.byId(it) }
    val endSession: (() -> Unit)? = scenario?.let { { EngineHub.stopDemo(); session = null } }
    val spoken by EngineHub.spoken.collectAsState()
    var spokenAtStart by remember { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf(PAGE_HOME) }
    var parkedDismissed by rememberSaveable { mutableStateOf<String?>(null) }
    // 미리 듣기 기록(실제 경고 기록과 나눔). 장면을 틀 때마다 기기에 남긴다.
    var plays by remember { mutableStateOf(PreviewHistory.all(context)) }
    // 경유지 질문에 답했는지. 장면이나 경고가 바뀌면 다시 묻는다.
    var waypointAnswered by remember { mutableStateOf(false) }
    val playScene: (SceneView) -> Unit = { picked ->
        Scenarios.byId(picked.scenarioId)?.let { sc ->
            EngineHub.stopDemo(); spokenAtStart = EngineHub.spoken.value; session = sc.id; waypointAnswered = false
            plays = PreviewHistory.record(context, sc.id)
            EngineHub.playDemo(sc)
        }
    }
    val shopList = remember(shops) { Shops.parse(shops) }
    val onWaypoint: (Boolean) -> Unit = { yes -> waypointAnswered = true; if (yes) act(ButtonAction.ADD_REST_AREA) }
    // 마지막 확인 시각. 1분마다 다시 읽는다.
    var clock by remember { mutableStateOf(LampTime.nowClock()) }
    LaunchedEffect(Unit) { while (true) { clock = LampTime.nowClock(); delay(CLOCK_MS) } }

    LaunchedEffect(toast) { if (toast != null) { delay(TOAST_MS); toast = null } }

    // 디버그 빌드 전용: 시작 인텐트 문자열 pleos_ff(s, l, w, wf)로 폼팩터 모드를 강제하고, 앱 창을 그 모드의 키트 앱 창 크기
    // (window/apps/landscape_width 또는 portrait_width, application_height)로 줄여 그린다. pleos_portrait=1 이면 세로 창. 릴리스에서는 읽지 않는다.
    val forced = if (BuildConfig.DEBUG) (context as? Activity)?.intent?.getStringExtra("pleos_ff") else null
    // 디버그 빌드 전용: pleos_view=alert 면 미리 듣기 중 주행 경고를 미리 듣기 화면 대신 경고 팝업으로 그린다(팝업 화면 확인용)
    val debugPopup = BuildConfig.DEBUG && (context as? Activity)?.intent?.getStringExtra("pleos_view") == "alert"
    val forcedPortrait = BuildConfig.DEBUG && (context as? Activity)?.intent?.getStringExtra("pleos_portrait") == "1"
    // 디버그 폼팩터 강제(l, w, wf, 세로) 때는 창 바깥을 어두운 회색으로 칠해 캡처에서 창 경계를 잴 수 있게 한다
    val framed = forced != null && !(forced == "s" && !forcedPortrait)
    BoxWithConstraints(Modifier.fillMaxSize().background(if (framed) androidx.compose.ui.graphics.Color(0xFF303030) else androidx.compose.ui.graphics.Color.White).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        val mode = when (forced) {
            "s" -> PleosFormFactor.ConnectS; "l" -> PleosFormFactor.ConnectL; "w" -> PleosFormFactor.ConnectW; "wf" -> PleosFormFactor.ConnectWaterfall
            else -> formFactorFor(maxHeight)
        }
        val kit = pleosDimensOf(mode)
        val frame = if (forced == null || (forced == "s" && !forcedPortrait)) Modifier.fillMaxSize()
            else Modifier.size(if (forcedPortrait) kit.windowAppsPortraitWidth else kit.windowAppsLandscapeWidth, kit.windowAppsApplicationHeight)
        BoxWithConstraints(frame) {
            val shape = WindowShape(landscape = maxWidth > maxHeight, compact = maxWidth < pleosDimensOf(PleosFormFactor.ConnectL).windowAppsPortraitWidth)
            // waterfall 창(1480 x 802)은 창 높이로 고르는 앱 규칙(formFactorFor)에 따라 connect-w 값으로 그린다(창 크기만 waterfall)
            val themeMode = if (mode == PleosFormFactor.ConnectWaterfall) formFactorFor(kit.windowAppsApplicationHeight) else mode
            PleosTheme(themeMode) {
            Box(Modifier.fillMaxSize().background(Pleos.colors.backgroundSecondary)) {
                    // 홈에서 여는 쪽: 경고등 정보과 기록. 경고 상태가 오면 그 화면이 먼저다.
                    val sceneView = session?.let { V2Data.scene(it) }
                    // 판정 근거 고르기: 정차 뒤 항목의 근거, 엔진의 지금 근거, 장면의 근거, 갈래만 쓴 근거 순
                    val parkedItems = (ui as? UiState.ParkedDetail)?.items.orEmpty()
                    val basisFor: (String) -> BasisView? = { lampId ->
                        parkedItems.firstOrNull { it.lamp.id == lampId }?.basis?.let(V2Data::view)
                            ?: liveBasis?.takeIf { it.lampId == lampId }?.let(V2Data::view)
                            ?: sceneView?.takeIf { it.lampId == lampId }?.let { V2Data.basisForScene(it.scenarioId) }
                            ?: LampCatalog.byId(lampId)?.let { V2Data.basisForTier(it.tier) }
                    }
                    val logs = V2Data.log(logRows)
                    val onPage: (HomePage) -> Unit = { p -> page = when (p) { HomePage.Home -> PAGE_HOME; HomePage.Catalog -> PAGE_CATALOG; HomePage.Log -> PAGE_LOG; HomePage.Settings -> PAGE_SETTINGS; HomePage.VoiceTest -> PAGE_VOICE_TEST } }
                    // 서비스센터와 서비스센터 전화. MainActivity 액션에 서비스센터 전화가 없어 전화 앱을 직접 연다(번호만 채움, 걸기는 운전자가 누름)
                    val dial: (String) -> Unit = { phone -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() }))) } }
                    val realLogs = logs.filterNot { PreviewHistory.isPreview(it.onAtMs, plays) }
                    val active = realLogs.filter { it.stillOn }.distinctBy { it.lampId }.map { ActiveLamp(it.lampId, it.nameKo, it.tier) }
                    val sessionSpoken = spoken?.takeIf { it != spokenAtStart }
                    val showPopup = sceneView == null || debugPopup
                    // 경고가 없는 정차(항목 없음)에는 정차 뒤 화면을 띄우지 않고 홈을 둔다
                    // 운전자가 닫은 정차 뒤 화면은 같은 경고 묶음이 이어지는 동안 다시 띄우지 않는다(새 경고가 켜지면 다시 뜸)
                    val parkedKey = (ui as? UiState.ParkedDetail)?.items?.joinToString { it.lamp.id + it.stillOn }
                    val shown = ui.let { if (it is UiState.ParkedDetail && (it.items.isEmpty() || parkedKey == parkedDismissed)) UiState.Idle else it }
                    androidx.compose.runtime.CompositionLocalProvider(LocalDriver provides DriverCtx(shopList, realLogs, clock, active.map { it.lampId }.toSet(), dial, onPage)) {
                    when (val s = shown) {
                        is UiState.Driving -> {
                            val ask = ButtonAction.ADD_REST_AREA in s.buttons && !waypointAnswered
                            if (showPopup || endSession == null) {
                                SysAlert(s, speaking, basisFor(s.lampId), shopList, ask, onWaypoint, endSession, act)
                            } else {
                                SysPreview(sceneView!!, V2Data.scenes, sessionSpoken, if (speaking || sessionSpoken != null) 1 else 0, running = demo != null, speaking = speaking,
                                    shops = shopList, askWaypoint = ask && sessionSpoken?.contains("경유지") == true,
                                    onClose = endSession, onPick = playScene, onStop = { EngineHub.stopDemo() }, onWaypoint = onWaypoint, onAction = act)
                            }
                        }
                        is UiState.Evacuation -> SysEvac(s, speaking, liveBasis?.let(V2Data::view) ?: sceneView?.let { V2Data.basisForScene(it.scenarioId) }, shopList, endSession, act)
                        is UiState.ParkedDetail -> SysParked(s, shops, basisFor, sceneView?.situation, endSession ?: { parkedDismissed = parkedKey }, onDial = dial, onAction = act)
                        UiState.Idle -> when {
                            sceneView != null && endSession != null -> SysPreview(sceneView, V2Data.scenes, sessionSpoken, if (demo != null) 0 else 2,
                                running = demo != null, speaking = speaking, shops = shopList, askWaypoint = false,
                                onClose = endSession, onPick = playScene, onStop = { EngineHub.stopDemo() }, onWaypoint = onWaypoint, onAction = act)
                            page == PAGE_CATALOG -> SysCatalog(V2Data.catalog, onPage)
                            page == PAGE_LOG -> SysLog(logs, plays, clock, onPage)
                            page == PAGE_SETTINGS -> SysSettings(voiceOn, { EngineHub.setVoiceEnabled(it) },
                                muted.mapNotNull { id -> LampCatalog.byId(id)?.let { MutedLamp(id, it.nameKo) } },
                                { id -> if (id == null) EngineHub.unmuteAll() else EngineHub.unmute(id) }, act, onPage)
                            page == PAGE_VOICE_TEST -> SysVoiceTest(V2Data.scenes, plays, playScene, onPage)
                            else -> SysHome(V2Data.preTrip(preTrip), active, realLogs.firstOrNull(), clock, shopList, voiceOn, onPage, dial)
                        }
                    }
                    }
                    toast?.let { PleosToast(it, Modifier.align(Alignment.BottomCenter)) }
                }
            }
        }
    }
}

/** 알림 글 표시 시간. 키트에 시간 값이 없어 앱이 4초로 정함. */
private const val TOAST_MS = 4_000L
/** 마지막 확인 시각 다시 읽는 간격(1분). */
private const val CLOCK_MS = 60_000L
private const val PAGE_HOME = 0
private const val PAGE_CATALOG = 1
private const val PAGE_LOG = 2
private const val PAGE_SETTINGS = 3
private const val PAGE_VOICE_TEST = 4
