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

import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import kotlin.concurrent.thread

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import java.io.File
import java.util.concurrent.CountDownLatch

@OptIn(androidx.media3.common.util.UnstableApi::class)
class ProcessamentoService : Service() {

    companion object {
        const val ACAO_CANCELAR = "com.exemplo.limpador.CANCELAR"
        private const val CANAL = "processamento"
        private const val ID_NOTIF = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoStrings = intent?.getStringArrayListExtra("videos")
        val nitidezAtiva = intent?.getBooleanExtra("nitidez", false) ?: false

        criarCanal()
        val notif = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Preparando...")
            .build()
        ServiceCompat.startForeground(
            this, ID_NOTIF, notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (videoStrings != null) {
            processarVideos(videoStrings, nitidezAtiva)
        }

        return START_NOT_STICKY
    }

    private fun processarVideos(uris: List<String>, nitidezAtiva: Boolean) {
        thread {
            val nm = getSystemService(NotificationManager::class.java)
            val builder = NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("ClipPure")

            uris.forEachIndexed { index, u ->
                val total = uris.size
                val msg = "Processando vídeo ${index + 1} de $total"
                nm.notify(ID_NOTIF, builder.setContentText(msg).setProgress(total, index, false).build())

                val uri = Uri.parse(u)
                val tempFile = File(cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
                val latch = CountDownLatch(1)

                val transformer = Transformer.Builder(this)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            salvarNoGeral(tempFile)
                            latch.countDown()
                        }
                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                            latch.countDown()
                        }
                    }).build()

                val mediaItem = MediaItem.fromUri(uri)
                val editedItem = EditedMediaItem.Builder(mediaItem).apply {
                    if (nitidezAtiva) {
                        setEffects(Effects(emptyList(), listOf(NitidezEffect(0.5f))))
                    }
                }.build()

                runOnUiThread {
                    try {
                        transformer.start(editedItem, tempFile.absolutePath)
                    } catch (e: Exception) {
                        latch.countDown()
                    }
                }
                latch.await()
            }
            nm.notify(ID_NOTIF, builder.setContentText("Processamento concluído").setProgress(0, 0, false).build())
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun salvarNoGeral(arquivo: File) {
        val valores = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ClipPure_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClipPure")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                arquivo.inputStream().use { input -> input.copyTo(out) }
            }
            arquivo.delete()
        }
    }

    private fun runOnUiThread(acao: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(acao)
    }

    private fun criarCanal() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Processamento de vídeos", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
