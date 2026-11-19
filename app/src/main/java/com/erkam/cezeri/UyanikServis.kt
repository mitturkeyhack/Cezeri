package com.erkam.cezeri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ekran kapalıyken de "Hey Cezerî"yi dinleyen ön plan servisi.
 * Cezerî bir işle meşgulken (konuşurken, dinlerken, onay beklerken) mikrofonu bırakır, iş bitince geri döner.
 */
class UyanikServis : Service() {

    private lateinit var app: CezeriApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dongu: Job? = null
    private var kilit: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as CezeriApp
        kanalOlustur()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DURDUR) {
            app.uyanma.acik = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, BILDIRIM_ID, bildirim(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            // Arka planda yeniden başlatılmaya çalışıldıysa Android mikrofona izin vermez; uygulama açılınca tekrar başlar
            stopSelf()
            return START_NOT_STICKY
        }
        if (kilit == null) {
            kilit = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cezeri:uyanik")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (dongu == null) dongu = scope.launch { izle() }
        return START_STICKY
    }

    private suspend fun izle() {
        var bosSure = 0L
        while (scope.isActive) {
            // Ses kimliği kaydı sürerken mikrofon kayıt ekranında; dokunma
            if (app.uyanma.duraklatildi) { bosSure = 0L; delay(300); continue }
            val d = app.asistan.durum.value
            val mesgul = d.dinliyor || d.konusuyor || d.bekliyor || d.dusunuyor || aramada()
            if (mesgul) {
                bosSure = 0L
                if (app.uyanma.dinliyor.value) app.uyanma.durdur()
            } else {
                bosSure += 300
                if (!app.uyanma.dinliyor.value && bosSure >= 1200 && app.uyanma.modellerVar()) {
                    app.uyanma.baslat { uyandi() }
                    bosSure = -3000 // başlatma birkaç saniye sürebilir
                }
            }
            delay(300)
        }
    }

    private fun aramada(): Boolean {
        val m = getSystemService(AudioManager::class.java).mode
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE
    }

    private fun uyandi() {
        try {
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(40, 120))
        } catch (_: Exception) {}
        if (!app.onPlanda && Settings.canDrawOverlays(this)) {
            // Ekranı açıp Cezerî'yi göster (kilit ekranının üstünde de)
            val i = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_ASSIST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { startActivity(i); return } catch (_: Exception) {}
        }
        app.asistan.cagrildi()
    }

    private fun kanalOlustur() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Hey Cezerî", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Cezerî uyanma kelimesini dinlerken görünür"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun bildirim(): Notification {
        val ac = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dur = PendingIntent.getService(
            this, 1, Intent(this, UyanikServis::class.java).setAction(DURDUR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("Cezerî seni dinliyor")
            .setContentText("“Hey Cezerî” demen yeterli")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(ac)
            .addAction(0, "Durdur", dur)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        app.uyanma.durdur()
        scope.cancel()
        try { kilit?.release() } catch (_: Exception) {}
        kilit = null
        super.onDestroy()
    }

    companion object {
        const val KANAL = "uyanik"
        const val BILDIRIM_ID = 7
        const val DURDUR = "com.erkam.cezeri.DURDUR"

        fun baslat(ctx: Context) {
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, UyanikServis::class.java)) } catch (_: Exception) {}
        }

        fun durdur(ctx: Context) { ctx.stopService(Intent(ctx, UyanikServis::class.java)) }
    }
}
