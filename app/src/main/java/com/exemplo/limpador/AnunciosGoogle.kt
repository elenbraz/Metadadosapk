package com.exemplo.limpador

import android.content.Context
import android.view.View
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object AnunciosGoogle {
    private var iniciado = false

    fun iniciar(ctx: Context) {
        if (!iniciado) {
            MobileAds.initialize(ctx.applicationContext) { }
            iniciado = true
        }
    }

    fun banner(ctx: Context, unit: String): View {
        iniciar(ctx)
        val v = AdView(ctx)
        v.setAdSize(AdSize.BANNER)
        v.adUnitId = unit
        v.loadAd(AdRequest.Builder().build())
        return v
    }
}
