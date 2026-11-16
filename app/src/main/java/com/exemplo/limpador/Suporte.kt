package com.exemplo.limpador

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Suporte {
    private var instalado = false

    private fun arquivo(ctx: Context) = File(ctx.applicationContext.filesDir, "erros.log")

    private fun agora() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

    fun instalar(ctx: Context) {
        if (instalado) return
        instalado = true
        val app = ctx.applicationContext
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { registrar(app, "Erro fatal na thread ${t.name}", e) } catch (_: Throwable) { }
            anterior?.uncaughtException(t, e)
        }
    }

    fun registrar(ctx: Context, titulo: String, e: Throwable?) {
        try {
            val f = arquivo(ctx)
            val trecho = "[${agora()}] $titulo\n" + (if (e != null) Log.getStackTraceString(e) else "") + "\n---\n"
            f.appendText(trecho)
            if (f.length() > 100_000) f.writeText(f.readText().takeLast(60_000))
        } catch (_: Throwable) { }
    }

    fun limpar(ctx: Context) {
        try { arquivo(ctx).delete() } catch (_: Throwable) { }
    }

    private fun versao(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    fun relatorio(ctx: Context): String {
        val erros = try {
            val f = arquivo(ctx)
            if (f.exists()) f.readText().takeLast(6000) else ""
        } catch (e: Exception) { "" }
        val conta = if (Auth.logado(ctx)) Auth.email(ctx) else "sem login"
        return buildString {
            append("Relatório do app\n")
            append("Data: ${agora()}\n")
            append("Versão do app: ${versao(ctx)}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Conta: $conta\n\n")
            append("--- Último processamento ---\n")
            append(Fila.texto.takeLast(3000).ifBlank { "(nada registrado)" })
            append("\n\n--- Erros registrados ---\n")
            append(erros.ifBlank { "(nenhum erro registrado)" })
        }
    }
}
