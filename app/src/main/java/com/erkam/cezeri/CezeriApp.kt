package com.erkam.cezeri

import android.app.Application
import com.erkam.cezeri.core.Akil
import com.erkam.cezeri.core.Eylem
import com.erkam.cezeri.core.Hafiza
import com.erkam.cezeri.core.Konum
import com.erkam.cezeri.core.Rehber
import com.erkam.cezeri.core.Ses
import com.erkam.cezeri.core.Uyanma

class CezeriApp : Application() {
    lateinit var ses: Ses; private set
    lateinit var akil: Akil; private set
    lateinit var rehber: Rehber; private set
    lateinit var eylem: Eylem; private set
    lateinit var hafiza: Hafiza; private set
    lateinit var asistan: Asistan; private set
    lateinit var uyanma: Uyanma; private set
    lateinit var konum: Konum; private set
    @Volatile var onPlanda: Boolean = false

    override fun onCreate() {
        super.onCreate()
        ses = Ses(this)
        akil = Akil(this)
        rehber = Rehber(this)
        eylem = Eylem(this)
        hafiza = Hafiza(this)
        konum = Konum(this)
        // Eski sürümden kalan telefon içi akıl dosyalarını (Gemma, ~2,7–3,7 GB) sil: artık akıl EVREN'de
        Thread {
            runCatching { java.io.File(getExternalFilesDir(null), "models").deleteRecursively() }
            // Eski telefon içi ses modeli (Supertonic, ~300 MB) artık kullanılmıyor
            runCatching { java.io.File(filesDir, "supertonic").deleteRecursively() }
        }.start()
        uyanma = Uyanma(this)
        asistan = Asistan(this)
        com.erkam.cezeri.asistan.VakitHatirlatici.planla(this)
    }
}
