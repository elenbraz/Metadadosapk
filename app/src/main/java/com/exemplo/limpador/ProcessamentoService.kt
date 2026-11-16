package com.exemplo.limpador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@OptIn(UnstableApi::class)
class ProcessamentoService : Service() {

    companion object {
        const val ACAO_CANCELAR = "com.exemplo.limpador.CANCELAR"
        const val ACAO_PROGRESSO = "com.exemplo.limpador.PROGRESSO"
        const val ACAO_CONCLUIDO = "com.exemplo.limpador.CONCLUIDO"
        const val EXTRA_PROGRESSO = "progresso"
        const val EXTRA_CAMINHO_TEMP = "caminhoTemp"
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

            val ultimosProcessados = mutableListOf<String>()

            uris.forEachIndexed { index, u ->
                val total = uris.size
                val msg = "Processando vídeo ${index + 1} de $total"
                nm.notify(ID_NOTIF, builder.setContentText(msg).setProgress(100, 0, false).build())

                val uri = Uri.parse(u)
                val tempFile = File(cacheDir, "processed_video_${System.currentTimeMillis()}.mp4")
                val latch = CountDownLatch(1)

                val transformer = Transformer.Builder(this)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                            salvarNoGeral(tempFile)
                            ultimosProcessados.add(tempFile.absolutePath)
                            enviarBroadcastConcluido(tempFile.absolutePath)
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

                val handler = Handler(Looper.getMainLooper())
                val progressHolder = ProgressHolder()

                val runnableProgresso = object : Runnable {
                    override fun run() {
                        try {
                            val state = transformer.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val pct = progressHolder.progress
                                enviarBroadcastProgresso(pct)
                                nm.notify(ID_NOTIF, builder.setContentText("$msg ($pct%)").setProgress(100, pct, false).build())
                            }
                        } catch (_: Exception) {}
                        if (latch.count > 0) {
                            handler.postDelayed(this, 300)
                        }
                    }
                }

                handler.post(object : Runnable {
                    override fun run() {
                        try {
                            transformer.start(editedItem, tempFile.absolutePath)
                            handler.post(runnableProgresso)
                        } catch (e: Exception) {
                            latch.countDown()
                        }
                    }
                })

                latch.await()
            }

            nm.notify(ID_NOTIF, builder.setContentText("Processamento concluído").setProgress(0, 0, false).build())
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun enviarBroadcastProgresso(progresso: Int) {
        val intent = Intent(ACAO_PROGRESSO).apply {
            putExtra(EXTRA_PROGRESSO, progresso)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun enviarBroadcastConcluido(caminhoTemp: String) {
        val intent = Intent(ACAO_CONCLUIDO).apply {
            putExtra(EXTRA_CAMINHO_TEMP, caminhoTemp)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun salvarNoGeral(arquivo: File) {
        try {
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
            }
        } catch (_: Exception) {}
    }

    private fun criarCanal() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Processamento de vídeos", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
