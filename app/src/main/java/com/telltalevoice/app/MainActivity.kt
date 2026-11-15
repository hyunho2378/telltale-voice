package com.telltalevoice.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.telltalevoice.app.ui.AppRoot
import com.telltalevoice.core.ButtonAction
import com.telltalevoice.core.publicdata.EmergencyContacts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            // 차 속도(PERF_VEHICLE_SPEED). 정차 뒤 화면 허용(시속 5km 이하)과 판정 근거에 쓴다
            add("android.car.permission.CAR_SPEED")
        }
        requestPermissions(wanted.toTypedArray(), 1)
        runCatching { GuardService.start(this) }.onFailure {
            EngineHub.note("서비스 시작 실패: ${it.message}")
            EngineHub.startTicking()
        }
        setContent { AppRoot(onAction = ::onAction) }
    }

    private fun onAction(action: ButtonAction): String? = when (action) {
        ButtonAction.CALL_EX_TOW -> { dial(EmergencyContacts.EX_TOW.phone); null }
        // 보험사 번호는 운전자마다 달라 전화 앱만 연다
        ButtonAction.CALL_INSURANCE -> { dial(null); null }
        ButtonAction.ADD_REST_AREA -> EngineHub.addRestAreaWaypoint()
        ButtonAction.REPEAT -> { EngineHub.repeatLast(); null }
    }

    private fun dial(number: String?) {
        val uri = Uri.parse(if (number == null) "tel:" else "tel:${number.replace("-", "")}")
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, uri)) }
            .onFailure { EngineHub.note("전화 앱 없음") }
    }
}
