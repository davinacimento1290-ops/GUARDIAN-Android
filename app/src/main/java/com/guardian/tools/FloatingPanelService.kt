package com.guardian.tools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var currentTab = "Ferramentas"
    private val values = linkedMapOf("Diamantes (demo)" to 250, "Moedas (demo)" to 1250, "Vidas (demo)" to 5, "Energia (demo)" to 80, "XP (demo)" to 340, "Nível (demo)" to 12)
    private val originalValues = values.toMap()
    private var searchQuery = ""
    private var appFilter = ""
    private var selectedPackage = ""
    private var lastSearchResults: List<Pair<String, Int>> = emptyList()
    private var editInput = ""
    private val enabledServices = linkedMapOf(
        "Editor de arquivos" to true,
        "Explorador de APK" to true,
        "Perfil de valores demo" to true
    )
    private var selectedValue = "Moedas (demo)"

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permissão de sobreposição não autorizada.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            if (bubble == null) showBubble()
            START_STICKY
        } catch (error: Exception) {
            removeView(panel)
            removeView(bubble)
            panel = null
            bubble = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Toast.makeText(
                this,
                "GUARDIAN não conseguiu criar a sobreposição: ${error.message ?: "erro do Android"}",
                Toast.LENGTH_LONG
            ).show()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeView(panel)
        removeView(bubble)
        panel = null
        bubble = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "GUARDIAN flutuante",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Indica quando o painel flutuante está ativo." }
            )
        }
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            android.app.PendingIntent.getActivity(
                this,
                0,
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("GUARDIAN está ativo")
            .setContentText("Toque no ícone G para abrir as ferramentas.")
            .setOngoing(true)
        if (pending != null) builder.setContentIntent(pending)
        return builder.build()
    }

    private fun overlayParams(width: Int, height: Int, x: Int, y: Int, focusable: Boolean = false): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun showBubble() {
        if (bubble != null) return

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val size = (52 * density).toInt()
        val params = overlayParams(
            size,
            size,
            (12 * density).toInt(),
            min((180 * density).toInt(), max(0, screenHeight - size))
        )

        val button = TextView(this).apply {
            text = "G"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.rgb(20, 165, 220), Color.rgb(91, 74, 210))
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke((2 * density).toInt().coerceAtLeast(1), Color.WHITE)
            }
            elevation = 12f
        }

        button.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var dragged = false
            private val touchSlop = ViewConfiguration.get(this@FloatingPanelService).scaledTouchSlop

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragged = true
                        if (dragged) {
                            params.x = (startX + dx).coerceIn(0, max(0, screenWidth - size))
                            params.y = (startY + dy).coerceIn(0, max(0, screenHeight - size))
                            try {
                                windowManager.updateViewLayout(view, params)
                            } catch (_: Exception) {
                                stopSelf()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) togglePanel()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return true
            }
        })

        windowManager.addView(button, params)
        bubble = button
    }

    private fun togglePanel() {
        try {
            if (panel == null) showPanel() else hidePanel()
        } catch (error: Exception) {
            hidePanel()
            Toast.makeText(
                this,
                "Não foi possível abrir o painel: ${error.message ?: "erro do Android"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun hidePanel() {
        removeView(panel)
        panel = null
    }

    private fun removeView(view: View?) {
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // A janela pode já ter sido removida pelo sistema.
            }
        }
    }

    private fun showPanel() {
        if (panel != null) return

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val margin = (12 * density).toInt()
        val panelWidth = min((340 * density).toInt(), max(1, screenWidth - margin * 2))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, (10 * density).toInt(), margin, (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 22, 39))
                cornerRadius = 16 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.rgb(55, 91, 125))
            }
            elevation = 16f
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("GUARDIAN • PAINEL", 16f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("×") { hidePanel() })
        root.addView(header)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Ferramentas", "Jogos", "Buscar", "Valores", "Editar", "Serviços", "Config.").forEach { tab ->
            tabs.addView(
                actionButton(tab) {
                    currentTab = tab
                    renderPanelBody(root)
                },
                LinearLayout.LayoutParams(-2, -2)
            )
        }
        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        root.addView(tabScroll)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = BODY_TAG
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(body)
        }
        val bodyHeight = min((screenHeight * 0.48f).toInt(), (330 * density).toInt())
        root.addView(scroll, LinearLayout.LayoutParams(-1, bodyHeight))

        val x = min((12 * density).toInt(), max(0, screenWidth - panelWidth))
        val y = min((110 * density).toInt(), max(0, screenHeight - (220 * density).toInt()))
        val params = overlayParams(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT, x, y, focusable = true)

        windowManager.addView(root, params)
        panel = root
        renderPanelBody(root)
    }

    private fun renderPanelBody(root: LinearLayout) {
        val body = root.findViewWithTag<LinearLayout>(BODY_TAG) ?: return
        body.removeAllViews()

        when (currentTab) {
            "Ferramentas" -> {
                body.addView(label("Acesso rápido", 14f, Color.rgb(90, 205, 255)))
                body.addView(actionButton("Abrir Editor PRO") { openMain(2) })
                body.addView(actionButton("Explorar APK") { openMain(1) })
                body.addView(actionButton("Lista de aplicativos") { openMain(0) })
                body.addView(
                    label(
                        "O painel flutuante permanece ativo enquanto o Android permitir a sobreposição.",
                        12f,
                        Color.LTGRAY
                    )
                )
            }
            "Jogos" -> {
                body.addView(label("APLICATIVOS • ABRIR SEPARADAMENTE", 13f, Color.rgb(90, 205, 255)))
                body.addView(label("Escolha um app instalado para abrir. A bolinha G continua ativa sobre a tela quando o Android permitir.", 12f, Color.LTGRAY))
                val filterField = EditText(this).apply {
                    setSingleLine(true)
                    hint = "Filtrar por nome ou pacote (com...)"
                    setText(appFilter)
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                }
                body.addView(filterField)
                body.addView(actionButton("Filtrar lista") {
                    appFilter = filterField.text?.toString()?.trim().orEmpty()
                    renderPanelBody(root)
                })
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = packageManager.queryIntentActivities(launcherIntent, 0)
                    .map { info -> info.activityInfo.packageName to info.loadLabel(packageManager).toString() }
                    .distinctBy { it.first }
                    .filter { (packageName, name) -> appFilter.isBlank() || name.contains(appFilter, ignoreCase = true) || packageName.contains(appFilter, ignoreCase = true) }
                    .sortedBy { it.second.lowercase() }
                body.addView(label("${apps.size} aplicativo(s) encontrado(s)", 12f, Color.LTGRAY))
                body.addView(label(if (selectedPackage.isBlank()) "Nenhum jogo selecionado" else "Selecionado: $selectedPackage", 12f, Color.rgb(90, 205, 255)))
                apps.take(35).forEach { (packageName, appName) ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val appInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    appInfo.addView(label(appName, 13f, Color.WHITE))
                    appInfo.addView(label(packageName, 9f, Color.LTGRAY))
                    row.addView(appInfo, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(actionButton(if (selectedPackage == packageName) "Selecionado" else "Selecionar") {
                        selectedPackage = packageName
                        Toast.makeText(this@FloatingPanelService, "App selecionado: $packageName", Toast.LENGTH_SHORT).show()
                        renderPanelBody(root)
                    })
                    row.addView(actionButton("Abrir") {
                        val launch = packageManager.getLaunchIntentForPackage(packageName)
                        if (launch == null) {
                            Toast.makeText(this@FloatingPanelService, "Este app não oferece uma tela de inicialização.", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                hidePanel()
                            } catch (error: Exception) {
                                Toast.makeText(this@FloatingPanelService, "Não foi possível abrir: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                    body.addView(row)
                }
                if (apps.size > 35) body.addView(label("Exibindo os primeiros 35 resultados. O filtro pesquisa nome e pacote.", 11f, Color.LTGRAY))
                body.addView(label("A seleção identifica o aplicativo instalado; não concede acesso ao processo ou à memória privada dele.", 11f, Color.LTGRAY))
            }
            "Buscar" -> {
                body.addView(label("PESQUISA • DADOS DE TESTE", 13f, Color.rgb(90, 205, 255)))
                body.addView(label("Pesquisa somente os valores de demonstração mantidos pelo GUARDIAN. Não acessa a memória de outros jogos.", 12f, Color.LTGRAY))
                val queryField = EditText(this).apply {
                    setSingleLine(true)
                    hint = "Nome ou valor (ex.: moedas, 1250)"
                    setText(searchQuery)
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                }
                body.addView(queryField)
                body.addView(actionButton("Pesquisar valores") {
                    searchQuery = queryField.text?.toString()?.trim().orEmpty()
                    lastSearchResults = values.filter { (name, value) ->
                        searchQuery.isNotBlank() &&
                            (name.contains(searchQuery, ignoreCase = true) || value.toString().contains(searchQuery))
                    }.map { it.key to it.value }
                    renderPanelBody(root)
                })
                body.addView(label("${lastSearchResults.size} resultado(s)", 12f, Color.LTGRAY))
                lastSearchResults.forEach { (name, value) ->
                    body.addView(actionButton("$name  =  $value") {
                        selectedValue = name
                        editInput = value.toString()
                        currentTab = "Editar"
                        renderPanelBody(root)
                    })
                }
            }
            "Valores" -> {
                body.addView(label("PERFIL LOCAL DE DEMONSTRAÇÃO", 12f, Color.rgb(255, 205, 90)))
                body.addView(
                    label(
                        "Estes valores pertencem ao GUARDIAN. Não são lidos nem escritos dentro de jogos.",
                        12f,
                        Color.LTGRAY
                    )
                )
                values.forEach { (name, value) ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(
                        actionButton(if (selectedValue == name) "● $name" else name) {
                            selectedValue = name
                            renderPanelBody(root)
                        },
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )
                    row.addView(label(value.toString(), 15f, Color.WHITE))
                    body.addView(row)
                }
                body.addView(label("Selecionado: $selectedValue", 13f, Color.rgb(90, 205, 255)))
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                controls.addView(
                    actionButton("−") {
                        values[selectedValue] = (values[selectedValue] ?: 0) - 1
                        renderPanelBody(root)
                    },
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                controls.addView(
                    actionButton("+") {
                        values[selectedValue] = (values[selectedValue] ?: 0) + 1
                        renderPanelBody(root)
                    },
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
                body.addView(controls)
                body.addView(
                    actionButton("Restaurar exemplos") {
                        originalValues.forEach { (name, value) -> values[name] = value }
                        renderPanelBody(root)
                    }
                )
            }
            "Editar" -> {
                body.addView(label("EDITOR DE VALORES TEMPORÁRIOS", 13f, Color.rgb(90, 205, 255)))
                body.addView(label("Selecionado: $selectedValue", 13f, Color.WHITE))
                val valueField = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                    setSingleLine(true)
                    hint = "Novo valor inteiro"
                    setText(if (editInput.isNotEmpty()) editInput else (values[selectedValue] ?: 0).toString())
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                }
                body.addView(valueField)
                body.addView(actionButton("Aplicar no perfil de teste") {
                    val parsed = valueField.text?.toString()?.toIntOrNull()
                    if (parsed == null) {
                        Toast.makeText(this@FloatingPanelService, "Digite um número inteiro válido.", Toast.LENGTH_SHORT).show()
                    } else {
                        values[selectedValue] = parsed
                        editInput = parsed.toString()
                        Toast.makeText(this@FloatingPanelService, "Valor de demonstração atualizado.", Toast.LENGTH_SHORT).show()
                        currentTab = "Valores"
                        renderPanelBody(root)
                    }
                })
                body.addView(actionButton("Restaurar valores originais") {
                    originalValues.forEach { (name, value) -> values[name] = value }
                    editInput = ""
                    lastSearchResults = emptyList()
                    Toast.makeText(this@FloatingPanelService, "Valores de demonstração restaurados.", Toast.LENGTH_SHORT).show()
                    currentTab = "Valores"
                    renderPanelBody(root)
                })
            }
            "Serviços" -> {
                body.addView(label("Serviços locais do GUARDIAN", 14f, Color.rgb(90, 205, 255)))
                enabledServices.forEach { (name, enabled) ->
                    body.addView(
                        actionButton((if (enabled) "✓  " else "○  ") + name) {
                            enabledServices[name] = !(enabledServices[name] ?: false)
                            renderPanelBody(root)
                        }
                    )
                }
                body.addView(
                    label(
                        "Estes controles ativam atalhos internos; não modificam outros aplicativos.",
                        12f,
                        Color.LTGRAY
                    )
                )
            }
            else -> {
                body.addView(label("Sobreposição autorizada pelo Android", 13f, Color.WHITE))
                body.addView(actionButton("Ocultar painel (manter ícone)") { hidePanel() })
                body.addView(actionButton("Encerrar serviço flutuante") { stopSelf() })
                body.addView(
                    label(
                        "Arraste o círculo G para mover. Toque nele para abrir ou fechar o painel.",
                        12f,
                        Color.LTGRAY
                    )
                )
            }
        }
    }

    private fun openMain(destination: Int) {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        intent.putExtra("guardian_tab", destination)
        startActivity(intent)
        hidePanel()
    }

    private fun label(text: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(6, 8, 6, 8)
        }

    private fun actionButton(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.rgb(31, 65, 94))
            )
            setOnClickListener { action() }
        }

    companion object {
        const val ACTION_STOP = "com.guardian.tools.STOP_FLOATING"
        private const val CHANNEL_ID = "guardian_overlay"
        private const val NOTIFICATION_ID = 7412
        private const val BODY_TAG = "guardian_panel_body"
    }
}
