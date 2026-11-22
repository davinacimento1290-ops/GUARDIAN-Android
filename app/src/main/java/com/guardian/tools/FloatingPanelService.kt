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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private val values = linkedMapOf("Moedas (demo)" to 1250, "Nível (demo)" to 12, "Energia (demo)" to 80)
    private val enabledServices = linkedMapOf("Editor de arquivos" to true, "Explorador de APK" to true, "Perfil de valores demo" to true)
    private var selectedValue = "Moedas (demo)"
    private var currentTab = "Ferramentas"

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeView(panel)
        removeView(bubble)
        panel = null
        bubble = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "GUARDIAN flutuante", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("GUARDIAN está ativo")
            .setContentText("Toque no ícone flutuante para abrir as ferramentas.")
            .setOngoing(true)
        if (pending != null) builder.setContentIntent(pending)
        return builder.build()
    }

    private fun overlayParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
    }

    private fun showBubble() {
        if (bubble != null) return
        val button = TextView(this).apply {
            text = "G"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(22, 133, 196)); setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE) }
            elevation = 12f
            setOnClickListener { togglePanel() }
        }
        val size = (52 * resources.displayMetrics.density).toInt()
        val params = overlayParams(size, size, (12 * resources.displayMetrics.density).toInt(), (180 * resources.displayMetrics.density).toInt()).apply {
            width = size
            height = size
        }
        button.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var dragged = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = params.x; startY = params.y; dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) dragged = true
                        params.x = startX + dx; params.y = startY + dy
                        windowManager.updateViewLayout(button, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) togglePanel()
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(button, params)
        bubble = button
    }

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun hidePanel() {
        removeView(panel)
        panel = null
    }

    private fun removeView(view: View?) {
        if (view != null) try { windowManager.removeView(view) } catch (_: Exception) { }
    }

    private fun showPanel() {
        if (panel != null) return
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(Color.rgb(12, 22, 39))
            elevation = 16f
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("GUARDIAN • PAINEL", 16f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("×") { hidePanel() })
        root.addView(header)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Ferramentas", "Valores", "Serviços", "Config.").forEach { tab ->
            tabs.addView(actionButton(tab) { currentTab = tab; renderPanelBody(root) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "body"; setPadding(0, (8 * density).toInt(), 0, 0) }
        root.addView(body)
        panel = root
        panelParams = overlayParams((340 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            (70 * density).toInt(), (130 * density).toInt()).apply {
            width = minOf((340 * density).toInt(), resources.displayMetrics.widthPixels - (24 * density).toInt())
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        windowManager.addView(root, panelParams)
        renderPanelBody(root)
    }

    private fun renderPanelBody(root: LinearLayout) {
        val body = root.findViewWithTag<LinearLayout>("body") ?: return
        body.removeAllViews()
        when (currentTab) {
            "Ferramentas" -> {
                body.addView(label("Acesso rápido", 14f, Color.rgb(90, 205, 255)))
                body.addView(actionButton("Abrir Editor PRO") { openMain(2) })
                body.addView(actionButton("Explorar APK") { openMain(1) })
                body.addView(actionButton("Lista de aplicativos") { openMain(0) })
                body.addView(label("O painel fica visível sobre outros apps enquanto estiver ativo.", 12f, Color.LTGRAY))
            }
            "Valores" -> {
                body.addView(label("PERFIL LOCAL DE DEMONSTRAÇÃO", 12f, Color.rgb(255, 205, 90)))
                body.addView(label("Estes valores são exemplos guardados pelo GUARDIAN. Não são lidos nem escritos dentro de jogos.", 12f, Color.LTGRAY))
                values.forEach { (name, value) ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(actionButton(name) { selectedValue = name; currentTab = "Valores"; renderPanelBody(root) }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(label(value.toString(), 15f, Color.WHITE))
                    body.addView(row)
                }
                body.addView(label("Selecionado: $selectedValue", 13f, Color.rgb(90, 205, 255)))
                val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                controls.addView(actionButton("−") { values[selectedValue] = (values[selectedValue] ?: 0) - 1; renderPanelBody(root) }, LinearLayout.LayoutParams(0, -2, 1f))
                controls.addView(actionButton("+") { values[selectedValue] = (values[selectedValue] ?: 0) + 1; renderPanelBody(root) }, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(controls)
                body.addView(actionButton("Restaurar exemplos") { values["Moedas (demo)"] = 1250; values["Nível (demo)"] = 12; values["Energia (demo)"] = 80; renderPanelBody(root) })
            }
            "Serviços" -> {
                body.addView(label("Serviços locais do GUARDIAN", 14f, Color.rgb(90, 205, 255)))
                enabledServices.forEach { (name, enabled) ->
                    body.addView(actionButton((if (enabled) "✓  " else "○  ") + name) {
                        enabledServices[name] = !(enabledServices[name] ?: false)
                        renderPanelBody(root)
                    })
                }
                body.addView(label("Os seletores ativam/desativam atalhos internos; não iniciam modificações em outros aplicativos.", 12f, Color.LTGRAY))
            }
            else -> {
                body.addView(label("Sobreposição autorizada pelo Android", 13f, Color.WHITE))
                body.addView(actionButton("Ocultar painel (manter ícone)") { hidePanel() })
                body.addView(actionButton("Encerrar serviço flutuante") { stopSelf() })
                body.addView(label("Arraste o círculo G para mover. Toque nele para abrir/fechar este painel.", 12f, Color.LTGRAY))
            }
        }
    }

    private fun openMain(destination: Int) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra("guardian_tab", destination)
        startActivity(intent)
        hidePanel()
    }

    private fun label(text: String, size: Float, color: Int): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(6, 8, 6, 8)
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 11f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(31, 65, 94)))
        setOnClickListener { action() }
    }

    companion object {
        const val ACTION_STOP = "com.guardian.tools.STOP_FLOATING"
        private const val CHANNEL_ID = "guardian_overlay"
        private const val NOTIFICATION_ID = 7412
    }
}
