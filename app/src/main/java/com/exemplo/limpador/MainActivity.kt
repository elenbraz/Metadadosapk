package com.exemplo.limpador

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    // Paleta de Cores - Premium Dark Theme / CapCut Style
    private val fundo = Color.parseColor("#090A0F")
    private val superficie = Color.parseColor("#131B2E")
    private val superficie2 = Color.parseColor("#1C2744")
    private val linhaCor = Color.parseColor("#2A3B5E")
    private val texto = Color.parseColor("#FFFFFF")
    private val sub = Color.parseColor("#94A3B8")
    private val ciano = Color.parseColor("#22D3EE")
    private val violeta = Color.parseColor("#7C3AED")
    private val azul = Color.parseColor("#2563EB")
    private val verde = Color.parseColor("#10B981")

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var tela = ""
    private val pilha = ArrayList<String>()
    private var videos: List<Uri> = emptyList()
    private var fNitidez = true

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    private var barraProgresso: ProgressBar? = null
    private var txtStatusProgresso: TextView? = null
    private var btnBaixar: Button? = null
    private var ultimoArquivoProcessado: File? = null

    private lateinit var conteudo: LinearLayout
    private lateinit var barraTopo: LinearLayout
    private lateinit var barraTitulo: TextView
    private lateinit var barraVoltar: TextView

    private val prefs by lazy { getSharedPreferences("clippure", MODE_PRIVATE) }

    private val permissaoNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val escolher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            for (u in uris) {
                try {
                    contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
            }
            videos = uris
            if (tela == "configurar") mostrar("configurar") else irPara("configurar")
        }
    }

    private val salvarDocumento = registerForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uriTarget ->
        uriTarget?.let { target ->
            ultimoArquivoProcessado?.let { origem ->
                try {
                    contentResolver.openOutputStream(target)?.use { out ->
                        origem.inputStream().use { input -> input.copyTo(out) }
                    }
                    aviso("Vídeo guardado com sucesso!")
                } catch (e: Exception) {
                    aviso("Erro ao guardar o vídeo: ${e.message}")
                }
            } ?: aviso("Nenhum vídeo processado disponível para guardar")
        }
    }

    private val receiverProgresso = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ProcessamentoService.ACAO_PROGRESSO -> {
                    val progresso = intent.getIntExtra(ProcessamentoService.EXTRA_PROGRESSO, 0)
                    barraProgresso?.isIndeterminate = false
                    barraProgresso?.progress = progresso
                    txtStatusProgresso?.text = "A limpar e otimizar: $progresso%"
                }
                ProcessamentoService.ACAO_CONCLUIDO -> {
                    val caminho = intent.getStringExtra(ProcessamentoService.EXTRA_CAMINHO_TEMP)
                    if (caminho != null) {
                        ultimoArquivoProcessado = File(caminho)
                        carregarVideoNoPlayer(Uri.fromFile(ultimoArquivoProcessado))
                    }
                    barraProgresso?.progress = 100
                    txtStatusProgresso?.text = "Processamento Concluído! Pronto para baixar."
                    txtStatusProgresso?.setTextColor(verde)
                    btnBaixar?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun aviso(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun fonte(peso: String) = Typeface.create(peso, Typeface.NORMAL)

    private fun rect(cor: Int, raio: Int, borda: Int? = null) = GradientDrawable().apply {
        setColor(cor)
        cornerRadius = dp(raio).toFloat()
        if (borda != null) setStroke(dp(1), borda)
    }

    private fun <T : View> LinearLayout.adicionar(
        v: T, topo: Int = 0, baixo: Int = 0, peso: Float = 0f, largura: Int = MATCH
    ): T {
        v.layoutParams = LinearLayout.LayoutParams(largura, WRAP, peso).apply {
            setMargins(0, dp(topo), 0, dp(baixo))
        }
        addView(v)
        return v
    }

    private fun txt(t: String, tam: Float, cor: Int, peso: String = "sans-serif"): TextView =
        TextView(this).apply {
            text = t
            textSize = tam
            setTextColor(cor)
            typeface = fonte(peso)
        }

    private fun secao(t: String): TextView =
        txt(t.uppercase(), 12f, ciano, "sans-serif-medium").apply {
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(10))
        }

    private fun cartao(borda: Int = linhaCor): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rect(superficie, 20, borda)
    }

    private fun btnPrimario(t: String, acao: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 16f
        typeface = fonte("sans-serif-medium")
        stateListAnimator = null
        minHeight = dp(56)
        setTextColor(Color.WHITE)
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(violeta, azul))
            .apply { cornerRadius = dp(16).toFloat() }
        setOnClickListener { acao() }
    }

    private fun btnSucesso(t: String, acao: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 16f
        typeface = fonte("sans-serif-medium")
        stateListAnimator = null
        minHeight = dp(56)
        setTextColor(Color.WHITE)
        background = rect(verde, 16)
        setOnClickListener { acao() }
    }

    private fun campo(
        dica: String, valor: String, multi: Boolean = false, tipo: Int = 0, aoMudar: (String) -> Unit
    ): EditText =
        EditText(this).apply {
            hint = dica
            setText(valor)
            setTextColor(texto)
            setHintTextColor(sub)
            textSize = 15f
            inputType = if (tipo != 0) tipo
            else if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else InputType.TYPE_CLASS_TEXT
            if (multi) maxLines = 3
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rect(superficie2, 14, linhaCor)
            doAfterTextChanged { aoMudar(it.toString()) }
        }

    private fun opcao(t: String, desc: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(12))

        val textos = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        textos.addView(txt(t, 16f, texto, "sans-serif-medium"))
        textos.addView(txt(desc, 13f, sub).apply { setPadding(0, dp(2), 0, 0) })

        val sw = Switch(this@MainActivity).apply {
            isChecked = ligado
            thumbTintList = ColorStateList.valueOf(if (ligado) ciano else sub)
            trackTintList = ColorStateList.valueOf(if (ligado) ciano else linhaCor)
            setOnCheckedChangeListener { _, checked ->
                thumbTintList = ColorStateList.valueOf(if (checked) ciano else sub)
                trackTintList = ColorStateList.valueOf(if (checked) ciano else linhaCor)
                aoMudar(checked)
            }
        }

        addView(textos)
        addView(sw)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = fundo
        window.navigationBarColor = fundo

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissaoNotif.launch(Manifest.permission.POST_NOTIFICATIONS)

        val filter = IntentFilter().apply {
            addAction(ProcessamentoService.ACAO_PROGRESSO)
            addAction(ProcessamentoService.ACAO_CONCLUIDO)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiverProgresso, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiverProgresso, filter)
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(fundo)
        }
        barraTopo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(32), dp(16), dp(8))
        }
        barraVoltar = TextView(this).apply {
            text = "←"
            textSize = 28f
            setTextColor(texto)
            setPadding(dp(12), dp(4), dp(16), dp(4))
            setOnClickListener { voltar() }
        }
        barraTitulo = txt("", 20f, texto, "sans-serif-medium")
        barraTopo.addView(barraVoltar)
        barraTopo.addView(barraTitulo)
        raiz.addView(barraTopo)

        conteudo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(32))
        }
        raiz.addView(
            ScrollView(this).apply { isFillViewport = true; addView(conteudo) },
            LinearLayout.LayoutParams(MATCH, 0, 1f)
        )
        setContentView(raiz)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { voltar() }
        })

        if (!prefs.getBoolean("boasvindas", false)) mostrar("boasvindas") else entrarNoApp()
    }

    override fun onDestroy() {
        super.onDestroy()
        liberarPlayer()
        try {
            unregisterReceiver(receiverProgresso)
        } catch (_: Exception) {}
    }

    private fun inicializarPlayer(): ExoPlayer {
        val p = ExoPlayer.Builder(this).build()
        player = p
        return p
    }

    private fun carregarVideoNoPlayer(uri: Uri) {
        val p = player ?: inicializarPlayer()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = false
    }

    private fun liberarPlayer() {
        player?.release()
        player = null
    }

    private fun irPara(t: String) {
        if (tela.isNotEmpty() && tela != t) pilha.add(tela)
        mostrar(t)
    }

    private fun voltar() {
        liberarPlayer()
        if (pilha.isNotEmpty()) mostrar(pilha.removeAt(pilha.size - 1)) else finish()
    }

    private fun mostrar(t: String) {
        tela = t
        conteudo.removeAllViews()
        val semBarra = t == "inicio" || t == "boasvindas"
        barraTopo.visibility = if (semBarra) View.GONE else View.VISIBLE
        conteudo.setPadding(dp(20), dp(if (semBarra) 48 else 12), dp(20), dp(32))
        barraTitulo.text = when (t) {
            "configurar" -> "Editor ClipPure"
            "meus" -> "Meus vídeos"
            "marca" -> "Minha marca"
            "sobre" -> "Sobre"
            else -> ""
        }
        when (t) {
            "boasvindas" -> telaBoasVindas()
            "inicio" -> telaInicio()
            "configurar" -> telaConfigurar()
            "meus" -> telaMeus()
            "marca" -> telaMarca()
            "sobre" -> telaSobre()
        }
    }

    private fun telaBoasVindas() {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        conteudo.adicionar(c)
        c.adicionar(txt(getString(R.string.app_name), 36f, texto, "sans-serif-black"), topo = 24)
        c.adicionar(
            txt("Vídeos limpos, nítidos e prontos para postar", 16f, sub).apply { gravity = Gravity.CENTER },
            topo = 8, baixo = 36
        )

        var nome = prefs.getString("nome", "") ?: ""
        val form = cartao()
        form.adicionar(secao("Vamos começar"))
        form.adicionar(campo("Seu nome ou marca", nome) { nome = it })
        c.adicionar(form, baixo = 24)

        c.adicionar(btnPrimario("Começar Editor") {
            prefs.edit().putString("nome", nome.trim())
                .putBoolean("boasvindas", true).apply()
            entrarNoApp()
        })
    }

    private fun telaInicio() {
        val c = conteudo
        val nome = prefs.getString("nome", "") ?: ""

        c.adicionar(txt(if (nome.isNotBlank()) "Olá, $nome" else "Olá", 28f, texto, "sans-serif-medium"), topo = 12)
        c.adicionar(txt("O que vamos criar e limpar hoje?", 16f, sub), baixo = 28)

        val cardInicio = cartao()
        cardInicio.adicionar(secao("Novo Projeto"))
        cardInicio.adicionar(txt("Selecione um ou mais vídeos para remover metadados de rastreio e aplicar melhorias profissionais.", 14f, sub), baixo = 16)
        cardInicio.adicionar(btnPrimario("＋  Selecionar Vídeos") { escolher.launch(arrayOf("video/*")) })
        c.adicionar(cardInicio, baixo = 20)
    }

    private fun telaConfigurar() {
        val c = conteudo

        // 1. ÁREA DE PRÉ-VISUALIZAÇÃO (PREVIEW) NO TOPO - CAPCUT STYLE
        val cartaoPreview = cartao()
        cartaoPreview.adicionar(secao("Pré-visualização do Vídeo"))

        val pv = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(240))
            useController = true
            background = rect(Color.BLACK, 16)
        }
        playerView = pv
        val exoPlayer = inicializarPlayer()
        pv.player = exoPlayer

        if (videos.isNotEmpty()) {
            carregarVideoNoPlayer(videos[0])
        }

        cartaoPreview.addView(pv)
        c.adicionar(cartaoPreview, baixo = 16)

        // 2. ÁREA DE FEEDBACK E PROGRESSO
        val cartaoProgresso = cartao()
        cartaoProgresso.adicionar(secao("Progresso do Processamento"))

        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(14))
            progressTintList = ColorStateList.valueOf(ciano)
            progressBackgroundTintList = ColorStateList.valueOf(linhaCor)
            max = 100
            progress = 0
        }
        barraProgresso = pb

        val txtStatus = txt("Pronto para iniciar a limpeza e otimização...", 14f, sub).apply {
            setPadding(0, dp(12), 0, 0)
        }
        txtStatusProgresso = txtStatus

        cartaoProgresso.addView(pb)
        cartaoProgresso.addView(txtStatus)
        c.adicionar(cartaoProgresso, baixo = 16)

        // 3. OPÇÕES E CONTROLES DO EDITOR (SCROLLABLE)
        val opcoes = cartao()
        opcoes.adicionar(secao("Configurações Avançadas"))
        opcoes.adicionar(opcao("Nitidez Extra", "Aplica shader para realçar contornos e detalhes visuais", fNitidez) { fNitidez = it })
        opcoes.adicionar(opcao("Limpar Metadados", "Remove GPS, modelo da câmera e rastreadores EXIF", true) { })
        c.adicionar(opcoes, baixo = 20)

        // 4. BOTÕES DE AÇÃO: PROCESSAR E BAIXAR VÍDEO
        c.adicionar(btnPrimario("⚡ PROCESSAR VÍDEO") {
            if (videos.isEmpty()) {
                aviso("Selecione pelo menos um vídeo")
                return@btnPrimario
            }
            pb.isIndeterminate = true
            txtStatus.text = "Processando em segundo plano..."
            txtStatus.setTextColor(texto)
            btnBaixar?.visibility = View.GONE

            val intent = Intent(this, ProcessamentoService::class.java).apply {
                putStringArrayListExtra("videos", ArrayList(videos.map { it.toString() }))
                putExtra("nitidez", fNitidez)
            }
            startService(intent)
            aviso("Processamento iniciado!")
        }, baixo = 12)

        // BOTÃO DE BAIXAR VÍDEO COM STORAGE ACCESS FRAMEWORK
        val btnB = btnSucesso("⬇ BAIXAR VÍDEO FINAL") {
            val nomeSugerido = "ClipPure_${System.currentTimeMillis()}.mp4"
            salvarDocumento.launch(nomeSugerido)
        }.apply {
            visibility = View.GONE
        }
        btnBaixar = btnB
        c.adicionar(btnB, baixo = 20)
    }

    private fun telaMeus() {
        val c = conteudo
        val v = cartao()
        v.adicionar(secao("Histórico"))
        v.adicionar(txt("Nenhum vídeo processado recentemente nesta sessão.", 15f, sub))
        c.adicionar(v)
    }

    private fun telaMarca() {
        val c = conteudo
        val cp = cartao()
        cp.adicionar(secao("Perfil de Marca"))
        cp.adicionar(campo("Seu nome ou marca", prefs.getString("nome", "") ?: "") {
            prefs.edit().putString("nome", it.trim()).apply()
        })
        c.adicionar(cp)
    }

    private fun telaSobre() {
        val c = conteudo
        val cc = cartao()
        cc.adicionar(secao("Sobre o ClipPure"))
        cc.adicionar(txt("ClipPure - Editor e Limpador Profissional de Metadados de Vídeo.\nVersão 1.0 (Dark Premium Edition)", 15f, texto))
        c.adicionar(cc)
    }

    private fun entrarNoApp() {
        pilha.clear()
        if (videos.isNotEmpty()) {
            pilha.add("inicio")
            mostrar("configurar")
        } else {
            mostrar("inicio")
        }
    }
}
