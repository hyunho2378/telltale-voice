package com.telltalevoice.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gleo Interface 수신부. assets/schema.json 의 actionCalls 3개(component "service")를 sendBroadcast 로 받는다.
 * 근거: Pleos app-integration-guide(App Schema, Gleo Action and Callback, Examples).
 * - 받는 쪽: intent.action 이 function 이름, 매개변수는 ACTION_PARAM(JSON 글). 세 function 모두 매개변수 없음
 * - 답하는 쪽: "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE" broadcast 에 packageName 과 response(JSON) 를 넣는다
 * - 우리 앱이 음성으로 직접 답하므로 mode 는 "silent". 우리 음성 출력이 아직 없으면 "voice" 로 두어 Gleo 가 message 를 읽게 한다
 * - Gleo 는 7초 뒤 Action 을 취소하므로 바로 답한다
 */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Gleo action $action param=${intent.getStringExtra(EXTRA_PARAM)}")
        EngineHub.note("Gleo 요청: $action")
        // 앱 화면을 열지 않은 상태에서도 신호와 음성을 붙이려고 서비스를 깨운다(이미 돌고 있으면 그대로)
        runCatching { GuardService.start(context) }.onFailure { EngineHub.note("Gleo: 서비스 시작 실패 ${it.message}") }
        val voice = EngineHub.speakerReady
        val reply = when (action) {
            ACTIVE_LAMPS -> {
                val u = EngineHub.gleoActiveLamps()
                Reply("success", if (EngineHub.activeLampCount() == 0) "No warning lamp is on now." else "The most urgent warning lamp was announced.",
                    JSONArray().put(JSONObject().put("guidance", u.text).put("activeLampCount", EngineHub.activeLampCount())))
            }
            REPEAT_LAST -> {
                val text = EngineHub.gleoRepeatLast()
                if (text == null) Reply("success", "There is no previous guidance to repeat.", null)
                else Reply("success", "The last guidance will be repeated.", JSONArray().put(JSONObject().put("guidance", text)))
            }
            REPAIR_SHOP -> {
                val u = EngineHub.gleoRepairShop()
                if (u == null) Reply("fail", "The current location or repair shop data is not available yet.", null)
                else Reply("success", "The nearest repair shop was announced.", JSONArray().put(JSONObject().put("guidance", u.text)))
            }
            else -> return
        }
        respond(context, action, reply, if (voice) "silent" else "voice")
    }

    private data class Reply(val status: String, val message: String, val data: JSONArray?)

    private fun respond(context: Context, functionName: String, r: Reply, mode: String) {
        runCatching {
            val json = JSONObject()
                .put("functionName", functionName)
                .put("status", r.status)
                .put("message", r.message)
                .put("mode", mode)
            r.data?.let { json.put("data", it.toString()) }
            context.sendBroadcast(Intent(RESPONSE_ACTION).apply {
                putExtra("packageName", context.packageName)
                putExtra("response", json.toString())
            })
            Log.i(TAG, "Gleo 답 $json")
        }.onFailure { Log.e(TAG, "Gleo 답 실패", it) }
    }

    companion object {
        private const val TAG = "GleoAction"
        const val EXTRA_PARAM = "ACTION_PARAM"
        const val RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        const val ACTIVE_LAMPS = "TELL_ACTIVE_WARNING_LAMPS"
        const val REPEAT_LAST = "REPEAT_LAST_GUIDANCE"
        const val REPAIR_SHOP = "FIND_NEARBY_REPAIR_SHOP"
    }
}
