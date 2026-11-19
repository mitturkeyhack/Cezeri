package com.erkam.cezeri.asistan

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.erkam.cezeri.CezeriApp

/** Gelen arama, meşgul olma ve kapanma bildirimlerini Cezerî'ye iletir. */
class AramaAlici : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val app = context.applicationContext as? CezeriApp ?: return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val numara = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                // Android aynı çalma için yayını iki kez gönderir (numaralı ve numarasız); numaralıyı bekle
                if (numara == null && context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) return
                app.asistan.gelenArama(numara)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> app.asistan.aramaBitti()
        }
    }
}

/** Telefon çalarken sesli komutla açma / reddetme */
object AramaKontrol {
    private var sessizeAlindi = false

    fun ziliKis(ctx: Context) {
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            sessizeAlindi = true
        } catch (_: Exception) {}
    }

    fun ziliAc(ctx: Context) {
        if (!sessizeAlindi) return
        try {
            ctx.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: Exception) {}
        sessizeAlindi = false
    }

    @SuppressLint("MissingPermission")
    fun ac(ctx: Context): Boolean = try {
        @Suppress("DEPRECATION")
        ctx.getSystemService(TelecomManager::class.java).acceptRingingCall(); true
    } catch (_: Exception) { false }

    @SuppressLint("MissingPermission")
    fun reddet(ctx: Context): Boolean = try {
        @Suppress("DEPRECATION")
        ctx.getSystemService(TelecomManager::class.java).endCall()
    } catch (_: Exception) { false }

    fun izinVar(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
}

/** WhatsApp'ta Cezerî'nin hazırladığı mesajın "gönder" tuşuna yalnızca Cezerî istediğinde basar. */
class WhatsappErisim : AccessibilityService() {

    private val ana = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!WhatsappGonderim.bekliyor()) return
        val kok = rootInActiveWindow ?: return
        val dugme = gonderDugmesi(kok) ?: return
        if (dugme.isEnabled && dugme.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            WhatsappGonderim.tamam(true)
            // Sohbetten çık ve WhatsApp'tan geri dön
            ana.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 700)
            ana.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1300)
        }
    }

    private fun gonderDugmesi(kok: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
            kok.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        for (ad in listOf("Gönder", "Send")) {
            kok.findAccessibilityNodeInfosByText(ad)?.firstOrNull { it.isClickable }?.let { return it }
        }
        return null
    }

    override fun onInterrupt() {}

    companion object {
        fun acikMi(ctx: Context): Boolean {
            val acik = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val ben = ComponentName(ctx, WhatsappErisim::class.java).flattenToString()
            return acik.split(':').any { it.equals(ben, ignoreCase = true) }
        }
    }
}

object WhatsappGonderim {
    @Volatile private var zaman = 0L
    @Volatile private var sonuc: ((Boolean) -> Unit)? = null

    fun bekle(cb: (Boolean) -> Unit) { zaman = System.currentTimeMillis(); sonuc = cb }
    fun bekliyor(): Boolean = zaman != 0L && System.currentTimeMillis() - zaman < 15_000
    fun tamam(ok: Boolean) {
        val cb = sonuc
        zaman = 0L; sonuc = null
        cb?.let { Handler(Looper.getMainLooper()).post { it(ok) } }
    }
}
