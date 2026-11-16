package com.exemplo.limpador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class ProcessamentoService : Service() {

    companion object {
        const val ACAO_CANCELAR = "com.exemplo.limpador.CANCELAR"
        private const val CANAL = "processamento"
        private const val ID_NOTIF = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        criarCanal()
        val notif = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Processando...")
            .build()
        ServiceCompat.startForeground(
            this, ID_NOTIF, notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        return START_NOT_STICKY
    }

    private fun criarCanal() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Processamento de vídeos", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
