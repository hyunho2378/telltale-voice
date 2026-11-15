package com.telltalevoice.app.signal.pleos

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.telltalevoice.app.signal.NaviSignals
import com.telltalevoice.app.signal.SignalSink
import com.telltalevoice.core.RouteContext
import com.telltalevoice.core.TurnItem

/**
 * Pleos NaviHelper 3.0.5 어댑터. 다음 회전 목록(onTBTInfo), 현재 위치, 도착, 경유지 넣기.
 * getTBTInfo 와 getCurrentLocationInfo 는 요청이고 결과는 리스너로 온다. POLL_MS 마다 다시 요청한다.
 */
class PleosNaviAdapter(context: Context) : NaviSignals, NaviHelperEventListener {
    override val name = "Pleos NaviHelper"
    private val app = context.applicationContext
    private var helper: NaviHelper? = null
    private var sink: SignalSink? = null
    private var route = RouteContext()
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            helper?.let { h ->
                runCatching { h.getTBTInfo(); h.getCurrentLocationInfo() }
                    .onFailure { sink?.onAdapterError(name, "요청 실패: ${it.message}") }
                handler.postDelayed(this, POLL_MS)
            }
        }
    }

    override fun start(sink: SignalSink) {
        this.sink = sink
        try {
            val h = NaviHelper(app)
            h.initialize()
            h.addListener(this)
            helper = h
            handler.post(poll)
        } catch (e: Throwable) {
            sink.onAdapterError(name, "초기화 실패: ${e.message}")
        }
    }

    override fun stop() {
        handler.removeCallbacks(poll)
        runCatching { helper?.removeListener(this) }
        runCatching { helper?.release() }
        helper = null
    }

    override fun addWaypoint(lat: Double, lon: Double, name: String): Boolean = runCatching {
        helper!!.addWaypoint(
            RequestWaypointInfo(latitude = lat, longitude = lon, poiName = name, waypointIndex = WaypointIndex.FIRST,
                poiId = "", poiSubId = "0", address = "")
        )
    }.isSuccess

    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
        route = route.copy(turns = tbtInfoList.map { TurnItem(it.type.name, it.distance, it.description) })
        sink?.onRoute(route)
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        route = route.copy(guiding = status == NavigationStatus.ROUTE_DRIVE)
        sink?.onRoute(route)
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
        sink?.onLocation(currentLocationInfo.latitude, currentLocationInfo.longitude, currentLocationInfo.heading)
        route = route.copy(roadLabel = currentLocationInfo.address)
    }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        route = route.copy(destinationName = destinationArrivedInfo.name)
        sink?.onArrived()
    }

    companion object { const val POLL_MS = 5_000L }
}
