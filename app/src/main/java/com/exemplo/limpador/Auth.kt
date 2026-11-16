package com.exemplo.limpador

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import kotlin.concurrent.thread

object Auth {
    private const val BASE = "https://identitytoolkit.googleapis.com/v1/accounts"

    private class Resposta(val codigo: Int, val corpo: String)

    private fun p(ctx: Context) = ctx.getSharedPreferences("clippure_auth", Context.MODE_PRIVATE)

    fun ativo() = Config.FIREBASE_API_KEY.isNotBlank()
    fun exigeLogin() = ativo() && Config.EXIGIR_LOGIN
    fun logado(ctx: Context) = ativo() && (p(ctx).getString("refresh", "") ?: "").isNotEmpty()
    fun email(ctx: Context) = p(ctx).getString("email", "") ?: ""
    fun ultimoEmail(ctx: Context) = p(ctx).getString("ultimo_email", "") ?: ""

    fun ehAdmin(ctx: Context): Boolean {
        if (!logado(ctx)) return false
        val meu = email(ctx).trim().lowercase()
        return Config.ADMIN_EMAILS.split(",").map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }.contains(meu)
    }

    fun sair(ctx: Context) {
        p(ctx).edit().remove("refresh").remove("email").remove("uid").apply()
    }

    fun gerarSenha(): String {
        val letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..10).map { letras[r.nextInt(letras.length)] }.joinToString("")
    }

    private fun chamar(url: String, corpo: String, tipo: String = "application/json"): Resposta {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.doOutput = true
            c.setRequestProperty("Content-Type", tipo)
            c.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            val codigo = c.responseCode
            val fluxo = if (codigo >= 400) c.errorStream else c.inputStream
            val texto = fluxo?.bufferedReader()?.use { it.readText() } ?: ""
            return Resposta(codigo, texto)
        } finally {
            c.disconnect()
        }
    }

    private fun codigoErro(corpo: String): String = try {
        JSONObject(corpo).getJSONObject("error").getString("message").substringBefore(" :").trim()
    } catch (e: Exception) { "" }

    private fun traduzir(corpo: String): String {
        if (corpo.contains("API key not valid") || corpo.contains("API_KEY_INVALID")) {
            return "A chave do Firebase está inválida. Confira a configuração do app."
        }
        return when (val codigo = codigoErro(corpo)) {
            "EMAIL_NOT_FOUND" -> "Não encontramos esse e-mail."
            "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> "E-mail ou senha incorretos."
            "USER_DISABLED" -> "Este acesso foi desativado. Fale com o suporte."
            "EMAIL_EXISTS" -> "Já existe uma conta com esse e-mail."
            "WEAK_PASSWORD" -> "Senha fraca: use pelo menos 6 caracteres."
            "INVALID_EMAIL" -> "E-mail inválido."
            "MISSING_PASSWORD" -> "Digite a senha."
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Muitas tentativas. Aguarde alguns minutos."
            "OPERATION_NOT_ALLOWED" -> "O login por e-mail e senha não está ativado no Firebase."
            "" -> "Não foi possível concluir. Tente de novo."
            else -> "Erro: $codigo"
        }
    }

    private fun acessar(
        ctx: Context, metodo: String, email: String, senha: String,
        salvar: Boolean, fim: (Boolean, String) -> Unit
    ) {
        if (!ativo()) { fim(false, "O login ainda não foi configurado."); return }
        val app = ctx.applicationContext
        thread {
            try {
                val corpo = JSONObject()
                    .put("email", email)
                    .put("password", senha)
                    .put("returnSecureToken", true)
                    .toString()
                val r = chamar("$BASE:$metodo?key=${Config.FIREBASE_API_KEY}", corpo)
                if (r.codigo in 200..299) {
                    if (salvar) {
                        val j = JSONObject(r.corpo)
                        p(app).edit()
                            .putString("refresh", j.getString("refreshToken"))
                            .putString("email", j.optString("email", email))
                            .putString("uid", j.optString("localId", ""))
                            .putString("ultimo_email", email)
                            .apply()
                    }
                    fim(true, "ok")
                } else {
                    fim(false, traduzir(r.corpo))
                }
            } catch (e: Exception) {
                fim(false, "Sem conexão com a internet. Tente de novo.")
            }
        }
    }

    fun entrar(ctx: Context, email: String, senha: String, fim: (Boolean, String) -> Unit) =
        acessar(ctx, "signInWithPassword", email, senha, true, fim)

    fun cadastrar(ctx: Context, email: String, senha: String, fim: (Boolean, String) -> Unit) =
        acessar(ctx, "signUp", email, senha, true, fim)

    fun criarAcesso(ctx: Context, email: String, senha: String, fim: (Boolean, String) -> Unit) =
        acessar(ctx, "signUp", email, senha, false, fim)

    fun esqueciSenha(ctx: Context, email: String, fim: (Boolean, String) -> Unit) {
        if (!ativo()) { fim(false, "O login ainda não foi configurado."); return }
        thread {
            try {
                val corpo = JSONObject().put("requestType", "PASSWORD_RESET").put("email", email).toString()
                val r = chamar("$BASE:sendOobCode?key=${Config.FIREBASE_API_KEY}", corpo)
                if (r.codigo in 200..299) fim(true, "ok") else fim(false, traduzir(r.corpo))
            } catch (e: Exception) {
                fim(false, "Sem conexão com a internet. Tente de novo.")
            }
        }
    }

    fun validarSessao(ctx: Context, fim: (Boolean) -> Unit) {
        if (!logado(ctx)) { fim(true); return }
        val app = ctx.applicationContext
        thread {
            try {
                val refresh = p(app).getString("refresh", "") ?: ""
                val corpo = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8")
                val r = chamar(
                    "https://securetoken.googleapis.com/v1/token?key=${Config.FIREBASE_API_KEY}",
                    corpo, "application/x-www-form-urlencoded"
                )
                if (r.codigo in 200..299) {
                    val novo = JSONObject(r.corpo).optString("refresh_token", "")
                    if (novo.isNotEmpty()) p(app).edit().putString("refresh", novo).apply()
                    fim(true)
                } else {
                    val codigo = codigoErro(r.corpo)
                    if (codigo in listOf("TOKEN_EXPIRED", "USER_DISABLED", "USER_NOT_FOUND", "INVALID_REFRESH_TOKEN")) {
                        sair(app)
                        fim(false)
                    } else {
                        fim(true)
                    }
                }
            } catch (e: Exception) {
                fim(true)
            }
        }
    }
}
