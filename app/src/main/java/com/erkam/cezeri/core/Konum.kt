package com.erkam.cezeri.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Namaz vakti ve kıble için konum. GPS internetsiz çalışır; son bilinen konum saklanır. */
class Konum(private val ctx: Context) {

    private val p = ctx.getSharedPreferences("konum", Context.MODE_PRIVATE)
    private val lm = ctx.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _konum = MutableStateFlow(kayitli())
    val konum: StateFlow<Pair<Double, Double>?> = _konum

    fun izinVar(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun kayitli(): Pair<Double, Double>? {
        if (!p.contains("enlem")) return null
        return p.getFloat("enlem", 0f).toDouble() to p.getFloat("boylam", 0f).toDouble()
    }

    private fun kaydet(l: Location) {
        p.edit().putFloat("enlem", l.latitude.toFloat()).putFloat("boylam", l.longitude.toFloat()).putLong("zaman", l.time).apply()
        _konum.value = l.latitude to l.longitude
    }

    /** Son bilinen konumu hemen kullanır, ardından GPS'ten taze konum ister. */
    @Suppress("MissingPermission")
    fun guncelle(bitince: ((Pair<Double, Double>?) -> Unit)? = null) {
        if (!izinVar()) { bitince?.invoke(kayitli()); return }
        var verildi = false
        fun ver() { if (!verildi) { verildi = true; bitince?.invoke(kayitli()) } }
        try {
            val son = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            if (son != null) { kaydet(son); ver() }
            val saglayici = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
                .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (saglayici == null) { ver(); return }
            val iptal = CancellationSignal()
            main.postDelayed({ iptal.cancel(); ver() }, 25_000)
            lm.getCurrentLocation(saglayici, iptal, ctx.mainExecutor) { l -> if (l != null) kaydet(l); ver() }
        } catch (_: Exception) {
            ver()
        }
    }
}
