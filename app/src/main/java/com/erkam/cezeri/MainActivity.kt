package com.erkam.cezeri

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erkam.cezeri.ui.Uygulama
import com.erkam.cezeri.ui.Yazi

class MainActivity : ComponentActivity() {

    private val vm: Asistan by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Yazi.yukle(this)
        setContent {
            val d by vm.durum.collectAsStateWithLifecycle()
            Uygulama(vm, d)
        }
        if (savedInstanceState == null) cagriMi(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        cagriMi(intent)
    }

    private fun cagriMi(i: Intent?) {
        val a = i?.action ?: return
        if (a == Intent.ACTION_ASSIST || a == Intent.ACTION_VOICE_COMMAND) vm.cagrildi()
    }

    override fun onStop() {
        super.onStop()
        vm.dinlemeyiBirak()
    }
}
