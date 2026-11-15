package com.telltalevoice.app.signal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * 안드로이드 표준 위치(GPS 또는 네트워크)로 현재 위치를 넣는다. Pleos NaviHelper 가 위치를 주지 않는 기기와
 * 위치 권한이 있는 일반 안드로이드에서 가까운 정비소 안내에 쓴다. 권한이 없으면 아무것도 하지 않는다.
 */
class StandardLocationAdapter(private val context: Context) : SignalAdapter, LocationListener {
    override val name = "표준 위치"
    private var sink: SignalSink? = null
    private var manager: LocationManager? = null

    override fun start(sink: SignalSink) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        this.sink = sink
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager = lm
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!lm.isProviderEnabled(provider)) continue
            lm.getLastKnownLocation(provider)?.let(::push)
            lm.requestLocationUpdates(provider, 5_000L, 20f, this, Looper.getMainLooper())
        }
    }

    override fun stop() {
        manager?.removeUpdates(this)
        manager = null
        sink = null
    }

    override fun onLocationChanged(location: Location) = push(location)

    private fun push(l: Location) {
        sink?.onLocation(l.latitude, l.longitude, if (l.hasBearing()) l.bearing.toDouble() else 0.0)
    }
}
