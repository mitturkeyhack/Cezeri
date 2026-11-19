package com.erkam.cezeri.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class Kisi(
    val contactId: Long,
    val ad: String,
    val numara: String,
    val etiket: String,
    val sonGorusme: Long,
) {
    /** Ekranda gösterilecek kısaltılmış numara: 0532 ••• •• 12 */
    val gizliNumara: String
        get() {
            if (contactId == -1L) return ad
            val d = numara.filter { it.isDigit() }
            if (d.length < 6) return numara
            val tail = d.takeLast(2)
            val head = if (d.startsWith("90") && d.length >= 12) "0" + d.substring(2, 5) else d.take(4)
            return "$head ••• •• $tail"
        }
    val anahtar: String get() = "$contactId|${numara.filter { it.isDigit() }}"
}

data class Eslesme(val kisi: Kisi, val puan: Double)

class Rehber(private val ctx: Context) {

    @Volatile private var cache: List<Kisi>? = null
    @Volatile private var cacheTime = 0L

    fun izinVar(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hepsi(): List<Kisi> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheTime < 60_000) return it }
        if (!izinVar()) return emptyList()
        val list = ArrayList<Kisi>()
        val seen = HashSet<String>()
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED,
        )
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null, null
            )?.use { c ->
                val iId = c.getColumnIndex(proj[0]); val iName = c.getColumnIndex(proj[1])
                val iNum = c.getColumnIndex(proj[2]); val iType = c.getColumnIndex(proj[3])
                val iLabel = c.getColumnIndex(proj[4]); val iLast = c.getColumnIndex(proj[5])
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val name = c.getString(iName) ?: continue
                    val num = c.getString(iNum) ?: continue
                    val digits = num.filter { it.isDigit() }.takeLast(10)
                    if (!seen.add("$id|$digits")) continue
                    val type = c.getInt(iType)
                    val label = when (type) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobil"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Ev"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "İş"
                        else -> c.getString(iLabel) ?: "Telefon"
                    }
                    val last = if (iLast >= 0) c.getLong(iLast) else 0L
                    list.add(Kisi(id, name, num, label, last))
                }
            }
        } catch (_: Exception) {
        }
        cache = list; cacheTime = now
        return list
    }

    /** Söylenen isme en uygun kişileri puanlayarak döndürür (kişi başına tek satır değil; her numara ayrı). */
    fun bul(varyantlar: List<String>): List<Eslesme> =
        KisiEsle.sirala(hepsi(), { it.ad }, varyantlar).map { Eslesme(it.oge, it.puan) }
            .sortedWith(
                compareByDescending<Eslesme> { it.puan }
                    .thenByDescending { if (it.kisi.etiket == "Mobil") 1 else 0 }
                    .thenByDescending { it.kisi.sonGorusme }
            )

    /** Kişi başına en iyi numara, puana göre. */
    fun kisiler(varyantlar: List<String>): List<Eslesme> =
        bul(varyantlar).groupBy { it.kisi.contactId }.map { it.value.first() }.sortedByDescending { it.puan }

    fun idIle(anahtar: String): Kisi? = hepsi().firstOrNull { it.anahtar == anahtar }
}
