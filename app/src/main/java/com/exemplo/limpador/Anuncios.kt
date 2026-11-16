package com.exemplo.limpador

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.random.Random

object Anuncios {
    const val ADMOB_TESTE_BANNER = "ca-app-pub-3940256099942544/6300978111"

    val EXEMPLO_JSON = """{
  "anuncios": [
    {
      "id": "promo1",
      "titulo": "Seu título aqui",
      "texto": "Texto curto do anúncio.",
      "botao": "Saiba mais",
      "url": "https://seusite.com/oferta",
      "imagem": "https://seusite.com/banner.jpg",
      "locais": ["inicio", "resultado", "meus"],
      "peso": 1
    }
  ]
}"""

    class Item(
        val id: String,
        val titulo: String,
        val texto: String,
        val botao: String,
        val url: String,
        val imagem: String,
        val locais: List<String>,
        val peso: Int
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("clippure_ads", Context.MODE_PRIVATE)

    fun internoLigado(ctx: Context) = p(ctx).getBoolean("interno", Config.ADS_INTERNOS_ATIVO)
    fun definirInterno(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("interno", v).apply() }
    fun url(ctx: Context) = p(ctx).getString("url", Config.ADS_INTERNOS_URL) ?: Config.ADS_INTERNOS_URL
    fun definirUrl(ctx: Context, v: String) { p(ctx).edit().putString("url", v).apply() }
    fun googleLigado(ctx: Context) = p(ctx).getBoolean("google", Config.ADMOB_ATIVO)
    fun definirGoogle(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("google", v).apply() }
    fun googleUnit(ctx: Context) =
        p(ctx).getString("google_unit", null) ?: Config.ADMOB_BANNER_ID.ifBlank { ADMOB_TESTE_BANNER }
    fun definirGoogleUnit(ctx: Context, v: String) { p(ctx).edit().putString("google_unit", v).apply() }

    fun demo() = Item(
        "exemplo", "Seu anúncio aqui",
        "Exemplo de anúncio da sua rede interna. Edite o arquivo JSON para divulgar seus produtos.",
        "Saiba mais", "https://example.com", "", listOf("inicio", "resultado", "meus"), 1
    )

    fun interpretar(json: String): List<Item> {
        val arr = JSONObject(json).getJSONArray("anuncios")
        val lista = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val link = o.optString("url", "")
            if (!(link.startsWith("https://") || link.startsWith("http://"))) continue
            val locais = mutableListOf<String>()
            val la = o.optJSONArray("locais")
            if (la != null) for (j in 0 until la.length()) locais.add(la.getString(j))
            if (locais.isEmpty()) locais.addAll(listOf("inicio", "resultado", "meus"))
            lista.add(
                Item(
                    o.optString("id", "a$i"),
                    o.optString("titulo", ""),
                    o.optString("texto", ""),
                    o.optString("botao", "Saiba mais"),
                    link,
                    o.optString("imagem", ""),
                    locais,
                    o.optInt("peso", 1).coerceAtLeast(1)
                )
            )
        }
        return lista
    }

    private fun doCache(ctx: Context): List<Item> = try {
        val j = p(ctx).getString("cache", null)
        if (j == null) emptyList() else interpretar(j)
    } catch (e: Exception) { emptyList() }

    fun atualizar(ctx: Context, fim: (Boolean, String) -> Unit) {
        val u = url(ctx).trim()
        if (!u.startsWith("https://")) { fim(false, "Informe uma URL que comece com https://"); return }
        val app = ctx.applicationContext
        thread {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val corpo = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val itens = interpretar(corpo)
                p(app).edit().putString("cache", corpo).putLong("cache_t", System.currentTimeMillis()).apply()
                fim(true, "Atualizado: ${itens.size} anúncio(s) válido(s)")
            } catch (e: Exception) {
                fim(false, "Falha ao atualizar: ${e.message}")
            }
        }
    }

    fun atualizarSeVelho(ctx: Context) {
        if (!internoLigado(ctx) || url(ctx).isBlank()) return
        val t = p(ctx).getLong("cache_t", 0L)
        if (System.currentTimeMillis() - t > 6 * 3600 * 1000L) atualizar(ctx) { _, _ -> }
    }

    fun escolher(ctx: Context, local: String): Item? {
        val lista = doCache(ctx).filter { local in it.locais }
        if (lista.isEmpty()) return null
        var sorteio = Random.nextInt(lista.sumOf { it.peso })
        for (a in lista) {
            sorteio -= a.peso
            if (sorteio < 0) return a
        }
        return lista.last()
    }

    fun baixarImagem(u: String): Bitmap? {
        if (!u.startsWith("https://")) return null
        return try {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val b = BitmapFactory.decodeStream(c.inputStream)
            c.disconnect()
            b
        } catch (e: Exception) { null }
    }

    private fun contar(ctx: Context, tipo: String, id: String) {
        val sp = p(ctx)
        val ids = (sp.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(id)
        val k = "${tipo}_$id"
        sp.edit().putStringSet("ids", ids).putInt(k, sp.getInt(k, 0) + 1).apply()
    }

    fun contarExibicao(ctx: Context, id: String) = contar(ctx, "imp", id)
    fun contarClique(ctx: Context, id: String) = contar(ctx, "cli", id)

    fun estatisticas(ctx: Context): List<Triple<String, Int, Int>> {
        val sp = p(ctx)
        val ids = sp.getStringSet("ids", emptySet()) ?: emptySet()
        return ids.sorted().map { Triple(it, sp.getInt("imp_$it", 0), sp.getInt("cli_$it", 0)) }
    }

    fun zerar(ctx: Context) {
        val sp = p(ctx)
        val ed = sp.edit()
        for (id in sp.getStringSet("ids", emptySet()) ?: emptySet()) {
            ed.remove("imp_$id").remove("cli_$id")
        }
        ed.remove("ids").apply()
    }
}
