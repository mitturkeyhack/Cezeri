package com.erkam.cezeri.core

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date

/** Telefonda iş yapan eylemler. Hepsi sonucu Türkçe bir cümleyle bildirir. */
class Eylem(private val ctx: Context) {

    private fun izin(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun ara(numara: String): Boolean {
        return try {
            val uri = Uri.parse("tel:" + Uri.encode(numara))
            val i = if (izin(Manifest.permission.CALL_PHONE)) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }

    fun sms(numara: String, metin: String): Boolean {
        if (!izin(Manifest.permission.SEND_SMS)) return false
        return try {
            val sm = ctx.getSystemService(SmsManager::class.java)
            val parts = sm.divideMessage(metin)
            if (parts.size > 1) sm.sendMultipartTextMessage(numara, null, parts, null, null)
            else sm.sendTextMessage(numara, null, metin, null, null)
            true
        } catch (_: Exception) { false }
    }

    /** Aşama 1: WhatsApp sohbetini mesaj yazılmış hâlde açar. Tam otomatik gönderim Aşama 3'te. */
    fun whatsapp(numara: String, metin: String): Boolean {
        return try {
            val d = uluslararasi(numara)
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d?text=" + Uri.encode(metin)))
                .setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i); true
        } catch (_: Exception) { false }
    }

    fun whatsappVar(): Boolean = try {
        ctx.packageManager.getPackageInfo("com.whatsapp", 0); true
    } catch (_: Exception) { false }

    private fun uluslararasi(numara: String): String {
        var d = numara.filter { it.isDigit() }
        if (numara.trim().startsWith("+")) return d
        if (d.startsWith("00")) d = d.drop(2)
        else if (d.startsWith("0")) d = "90" + d.drop(1)
        else if (d.length == 10 && d.startsWith("5")) d = "90$d"
        return d
    }

    fun fener(ac: Boolean): Boolean = try {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id != null) { cm.setTorchMode(id, ac); true } else false
    } catch (_: Exception) { false }

    fun alarm(saat: Int, dakika: Int): Boolean = try {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, saat)
            .putExtra(AlarmClock.EXTRA_MINUTES, dakika)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Cezerî")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i); true
    } catch (_: Exception) { false }

    fun sesAyarla(yukselt: Boolean) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val yon = if (yukselt) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(2) {
            am.adjustStreamVolume(AudioManager.STREAM_RING, yon, 0)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, yon, if (it == 1) AudioManager.FLAG_SHOW_UI else 0)
        }
    }

    /** true: yapıldı, false: Rahatsız Etme izni gerekiyor (ayarlar açıldı). */
    fun sessiz(): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
            return false
        }
        ctx.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
        return true
    }

    fun titresim() {
        try { ctx.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_VIBRATE } catch (_: Exception) {}
    }

    fun pil(): Int = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun saatCumlesi(): String = "Saat " + SimpleDateFormat("HH:mm", Turkce.TR).format(Date()) + "."
    fun tarihCumlesi(): String = "Bugün " + SimpleDateFormat("d MMMM yyyy, EEEE", Turkce.TR).format(Date()) + "."
}
