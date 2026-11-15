package com.telltalevoice.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.telltalevoice.app.signal.SignalAdapter
import com.telltalevoice.app.signal.StandardLocationAdapter
import com.telltalevoice.app.signal.fake.AndroidTtsSpeaker
import com.telltalevoice.app.signal.pleos.CarPowertrainAdapter
import com.telltalevoice.app.signal.pleos.PleosNaviAdapter
import com.telltalevoice.app.signal.pleos.PleosTtsSpeaker
import com.telltalevoice.app.signal.pleos.PleosVehicleAdapter
import com.telltalevoice.core.publicdata.CsvPublicDataSource

/**
 * 신호를 받는 foreground service. 앱 화면이 밖에 있어도 경고등 신호를 받고 말한다(W1 실측 4번).
 * 실제 어댑터를 모두 붙이고, 붙지 않는 어댑터는 화면 상태 줄에 이유를 남긴다. 시연 모드는 같은 엔진에 시나리오를 넣는다.
 */
class GuardService : Service() {
    private val adapters = mutableListOf<SignalAdapter>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        val hub = EngineHub
        hub.attach(this)
        // 공공데이터(assets/publicdata, 출처는 같은 폴더 README.md). 정비업체 파일이 커서 뒤에서 읽는다.
        Thread {
            runCatching {
                val src = CsvPublicDataSource(readAsset("rest_areas.csv"), readAsset("repair_shops.csv"), readAsset("drowsy_shelters.csv"))
                hub.loadPublicData(src)
            }.onFailure { hub.note("공공데이터 읽기 실패: ${it.message}") }
        }.start()
        val pleosTts = PleosTtsSpeaker(this) { busy -> hub.onAudioBusy(busy) }
        pleosTts.start { ok ->
            if (ok) { hub.speaker = pleosTts; hub.note("음성: ${pleosTts.name}") }
            else {
                val fallback = AndroidTtsSpeaker(this)
                fallback.start { ok2 -> hub.note(if (ok2) "음성: ${fallback.name}" else "음성 엔진 없음") }
                hub.speaker = fallback
            }
        }
        val navi = PleosNaviAdapter(this)
        hub.navi = navi
        // 휴대폰 에뮬레이터처럼 android.car 나 Pleos 클래스가 없는 기기에서는 어댑터를 만드는 순간 클래스 로드 오류가 난다. 어댑터마다 따로 감싸 나머지 기능은 계속 돌린다.
        val makers: List<Pair<String, () -> SignalAdapter>> = listOf(
            "Pleos Vehicle" to { PleosVehicleAdapter(this) },
            "CarPropertyManager" to { CarPowertrainAdapter(this) },
        )
        makers.forEach { (label, make) ->
            runCatching { make() }
                .onSuccess { adapters += it }
                .onFailure { hub.note("$label: 이 기기에서는 쓸 수 없음") }
        }
        adapters += navi
        adapters += StandardLocationAdapter(this)
        adapters.forEach { a -> runCatching { a.start(hub) }.onFailure { hub.note("${a.name}: ${it.message}") } }
        hub.startTicking()
    }

    private fun readAsset(name: String): String =
        assets.open("publicdata/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        adapters.forEach { runCatching { it.stop() } }
        adapters.clear()
        EngineHub.stopTicking()
        EngineHub.speaker?.release()
        EngineHub.speaker = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "telltale"
        fun start(context: Context) {
            context.startForegroundService(Intent(context, GuardService::class.java))
        }
    }
}
