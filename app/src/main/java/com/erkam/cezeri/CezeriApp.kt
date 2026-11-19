package com.erkam.cezeri

import android.app.Application
import com.erkam.cezeri.core.Akil
import com.erkam.cezeri.core.Eylem
import com.erkam.cezeri.core.Hafiza
import com.erkam.cezeri.core.Konum
import com.erkam.cezeri.core.ModelIndirici
import com.erkam.cezeri.core.Rehber
import com.erkam.cezeri.core.Ses
import com.erkam.cezeri.core.Uyanma

class CezeriApp : Application() {
    lateinit var ses: Ses; private set
    lateinit var akil: Akil; private set
    lateinit var model: ModelIndirici; private set
    lateinit var modelDerin: ModelIndirici; private set
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
        model = ModelIndirici.hizli(this)
        modelDerin = ModelIndirici.derin(this)
        rehber = Rehber(this)
        eylem = Eylem(this)
        hafiza = Hafiza(this)
        konum = Konum(this)
        model.devamEt()
        modelDerin.devamEt()
        // Cezerî'nin kendi sesi: ana model hazırsa ve henüz inmediyse kendiliğinden indir (kaldığı yerden devam eder).
        if (ses.motor.value == Ses.MOTOR_CEZERI && !ses.cezeri.tamamMi() && model.hazirMi()) ses.sesIndir()
        uyanma = Uyanma(this)
        asistan = Asistan(this)
        com.erkam.cezeri.asistan.VakitHatirlatici.planla(this)
    }
}
