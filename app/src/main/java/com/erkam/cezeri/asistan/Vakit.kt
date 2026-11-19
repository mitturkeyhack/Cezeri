package com.erkam.cezeri.asistan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.erkam.cezeri.CezeriApp
import com.erkam.cezeri.MainActivity
import com.erkam.cezeri.R
import com.erkam.cezeri.core.Namaz
import java.util.Calendar

/** Vakit hatırlatıcı ayarları ve zamanlaması. */
object VakitHatirlatici {
    private const val KANAL = "vakit"
    const val EYLEM = "com.erkam.cezeri.VAKIT"

    private fun p(ctx: Context) = ctx.getSharedPreferences("namaz", Context.MODE_PRIVATE)
    fun acik(ctx: Context) = p(ctx).getBoolean("hatirlat", false)
    fun once(ctx: Context) = p(ctx).getInt("once", 10)
    fun sesli(ctx: Context) = p(ctx).getBoolean("sesli", true)

    fun ayarla(ctx: Context, acik: Boolean? = null, once: Int? = null, sesli: Boolean? = null) {
        p(ctx).edit().apply {
            acik?.let { putBoolean("hatirlat", it) }
            once?.let { putInt("once", it) }
            sesli?.let { putBoolean("sesli", it) }
        }.apply()
        planla(ctx)
    }

    fun tamZamanliIzin(ctx: Context): Boolean = ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun niyet(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, VakitAlici::class.java).setAction(EYLEM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Bir sonraki hatırlatmayı kurar (güneş doğuşu hariç beş vakit). */
    fun planla(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(niyet(ctx))
        if (!acik(ctx)) return
        val konum = ctx.getSharedPreferences("konum", Context.MODE_PRIVATE)
        if (!konum.contains("enlem")) return
        val enlem = konum.getFloat("enlem", 0f).toDouble(); val boylam = konum.getFloat("boylam", 0f).toDouble()
        val simdi = System.currentTimeMillis()
        val onceMs = once(ctx) * 60_000L
        for (gunEk in 0..1) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, gunEk) }
            val gun = Namaz.bugun(enlem, boylam, cal)
            for (v in Namaz.Vakit.entries) {
                if (v == Namaz.Vakit.GUNES) continue
                val dk = gun.dakikalar[v] ?: continue
                val t = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    add(Calendar.MINUTE, dk)
                }.timeInMillis
                val tetik = t - onceMs
                if (tetik > simdi + 5_000) {
                    p(ctx).edit().putString("sonraki", v.anahtar).putLong("sonraki_zaman", t).apply()
                    val pi = niyet(ctx)
                    if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tetik, pi)
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tetik, pi)
                    return
                }
            }
        }
    }

    fun bildir(ctx: Context) {
        val pr = p(ctx)
        val v = Namaz.Vakit.bul(pr.getString("sonraki", "") ?: "") ?: return
        val zaman = pr.getLong("sonraki_zaman", 0L)
        val kalan = ((zaman - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
        val saat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("tr")).format(java.util.Date(zaman))
        val metin = if (kalan <= 0) "${v.ad} vakti girdi." else "${v.ad} vaktine $kalan dakika var. Ezan $saat'te."
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Vakit hatırlatıcı", NotificationManager.IMPORTANCE_HIGH))
        val ac = PendingIntent.getActivity(ctx, 8, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            41,
            NotificationCompat.Builder(ctx, KANAL).setSmallIcon(R.drawable.ic_bildirim)
                .setContentTitle("Cezerî").setContentText(metin).setContentIntent(ac).setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        )
        if (sesli(ctx)) (ctx.applicationContext as? CezeriApp)?.asistan?.vakitHatirlat(metin)
    }
}

class VakitAlici : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            VakitHatirlatici.EYLEM -> { VakitHatirlatici.bildir(ctx); VakitHatirlatici.planla(ctx) }
            else -> VakitHatirlatici.planla(ctx)
        }
    }
}
