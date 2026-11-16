package com.exemplo.limpador

import android.net.Uri
import android.os.Handler
import android.os.Looper

object Fila {
    private val main = Handler(Looper.getMainLooper())

    var texto: String = ""
        private set
    var progresso: Int = 0
        private set
    var ativo: Boolean = false
        private set
    var saidas: List<Uri> = emptyList()
        private set
    var ouvinte: (() -> Unit)? = null

    private fun avisar() { ouvinte?.invoke() }

    fun iniciar() {
        texto = ""; progresso = 0; ativo = true; saidas = emptyList()
        avisar()
    }

    fun log(msg: String) {
        main.post { texto += msg + "\n"; avisar() }
    }

    fun definirProgresso(p: Int) {
        main.post { progresso = p; avisar() }
    }

    fun adicionarSaida(uri: Uri) {
        main.post { saidas = saidas + uri; avisar() }
    }

    fun limparTexto() {
        main.post { if (!ativo) { texto = ""; progresso = 0; avisar() } }
    }

    fun fim() {
        main.post { ativo = false; avisar() }
    }
}
