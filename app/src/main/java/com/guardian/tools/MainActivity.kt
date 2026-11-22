package com.guardian.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.zip.ZipInputStream

private data class InstalledApp(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuardianScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardianScreen() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Pronto. Escolha uma ferramenta.") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var editorText by remember { mutableStateOf("") }
    var openedName by remember { mutableStateOf("Nenhum arquivo aberto") }
    var apkEntries by remember { mutableStateOf<List<String>>(emptyList()) }

    // Ferramentas de análise e edição de arquivos autorizados.
    var searchQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("Tudo") }
    val editHistory = remember { mutableStateListOf<String>() }

    fun rememberBeforeChange() {
        if (editHistory.lastOrNull() != editorText) {
            editHistory.add(editorText)
            if (editHistory.size > 30) editHistory.removeAt(0)
        }
    }

    val visibleLines = remember(editorText, searchQuery, filterMode) {
        editorText.lines().mapIndexed { index, line -> (index + 1) to line }
            .filter { (_, line) ->
                val queryMatches = searchQuery.isBlank() || line.contains(searchQuery, ignoreCase = true)
                val typeMatches = when (filterMode) {
                    "Números" -> Regex("[-+]?\\d+(\\.\\d+)?").containsMatchIn(line)
                    "Hex" -> Regex("(?i)(0x)?[0-9a-f]{2,}").containsMatchIn(line)
                    else -> true
                }
                queryMatches && typeMatches
            }
    }

    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { editorText = it.readText() }
            openedName = uri.lastPathSegment ?: "Arquivo selecionado"
            searchQuery = ""
            replacement = ""
            editHistory.clear()
            status = "Arquivo carregado. O original não será sobrescrito."
        } catch (e: Exception) { status = "Erro ao ler arquivo: ${e.message}" }
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val entries = mutableListOf<String>()
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Arquivo indisponível" }
                ZipInputStream(input).use { zip ->
                    var item = zip.nextEntry
                    while (item != null && entries.size < 250) {
                        if (!item.isDirectory) entries.add(item.name)
                        zip.closeEntry()
                        item = zip.nextEntry
                    }
                }
            }
            apkEntries = entries
            status = "Análise concluída: ${entries.size} itens listados. APK não foi alterado."
        } catch (e: Exception) { status = "Não foi possível analisar: ${e.message}" }
    }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(editorText) }
            status = "Cópia salva. O arquivo original foi preservado."
        } catch (e: Exception) { status = "Erro ao salvar: ${e.message}" }
    }

    fun refreshApps() {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = context.packageManager.queryIntentActivities(launchIntent, 0)
            .map { InstalledApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        status = "${apps.size} aplicativos com ícone de inicialização encontrados."
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF42C8FF),
        secondary = androidx.compose.ui.graphics.Color(0xFFB28CFF),
        background = androidx.compose.ui.graphics.Color(0xFF080D18),
        surface = androidx.compose.ui.graphics.Color(0xFF121D30)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("GUARDIAN PRO MAX", style = MaterialTheme.typography.titleLarge)
                        Text("ANDROID TOOLKIT • SEM ROOT", style = MaterialTheme.typography.labelSmall)
                    } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = tab) {
                    listOf("Apps", "APK", "Editor PRO", "Sobre").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (tab) {
                        0 -> {
                            Text("Aplicativos instalados", style = MaterialTheme.typography.titleLarge)
                            Text("Lista apps que o Android apresenta como iniciáveis. Não acessa dados privados ou memória de outros apps.")
                            Button(onClick = { refreshApps() }, modifier = Modifier.fillMaxWidth()) { Text("Atualizar lista") }
                            apps.forEach { app ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        1 -> {
                            Text("Explorador de APK", style = MaterialTheme.typography.titleLarge)
                            Text("Escolha um APK. O GUARDIAN lista até 250 arquivos internos do pacote, sem instalar nem modificar o app.")
                            Button(onClick = { apkPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Selecionar APK") }
                            apkEntries.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall); HorizontalDivider() }
                        }
                        2 -> {
                            Text("Editor PRO de arquivos", style = MaterialTheme.typography.titleLarge)
                            Text("Analise arquivos TXT, JSON, XML e INI que você tem autorização para editar. As alterações são feitas em uma cópia.")
                            OutlinedButton(onClick = { configPicker.launch(arrayOf("text/*", "application/json", "application/xml", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir arquivo") }
                            Text(openedName, style = MaterialTheme.typography.labelMedium)

                            OutlinedTextField(
                                value = searchQuery, onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Buscar texto ou valor") },
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Tudo", "Números", "Hex").forEach { mode ->
                                    FilterChip(
                                        selected = filterMode == mode,
                                        onClick = { filterMode = mode },
                                        label = { Text(mode) }
                                    )
                                }
                            }
                            Text("${visibleLines.size} linha(s) encontrada(s)", style = MaterialTheme.typography.labelLarge)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                                    if (visibleLines.isEmpty()) {
                                        Text("Nenhum resultado. Tente outro termo ou filtro.")
                                    } else {
                                        visibleLines.take(100).forEach { (lineNumber, line) ->
                                            Text("$lineNumber  |  $line", style = MaterialTheme.typography.bodySmall)
                                            HorizontalDivider()
                                        }
                                        if (visibleLines.size > 100) Text("Exibindo as primeiras 100 linhas. O arquivo completo continua preservado.")
                                    }
                                }
                            }

                            Text("Substituição controlada", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = replacement, onValueChange = { replacement = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Novo valor") },
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank() && editorText.contains(searchQuery, ignoreCase = true)) {
                                        rememberBeforeChange()
                                        val regex = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
                                        val count = regex.findAll(editorText).count()
                                        editorText = regex.replace(editorText) { replacement }
                                        status = "$count ocorrência(s) substituída(s). Use Desfazer se necessário."
                                        searchQuery = ""
                                    } else status = "Digite um termo existente para substituir."
                                },
                                enabled = searchQuery.isNotBlank() && editorText.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Substituir todas as ocorrências") }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        val previous = editHistory.removeLastOrNull()
                                        if (previous != null) {
                                            editorText = previous
                                            status = "Última alteração desfeita."
                                        } else status = "Não há alterações para desfazer."
                                    },
                                    enabled = editHistory.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Desfazer") }
                                OutlinedButton(
                                    onClick = { savePicker.launch("guardian-pro-backup.txt") },
                                    enabled = editorText.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Salvar cópia") }
                            }
                            Text("Histórico local: ${editHistory.size}/30 etapa(s). O arquivo original não é sobrescrito.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = editorText, onValueChange = { editorText = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                                label = { Text("Conteúdo completo do arquivo") },
                                minLines = 8
                            )
                            Text("Dica: busque uma chave como velocidade=10 e substitua apenas o valor, conferindo a prévia antes de salvar.", style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {
                            Text("Sobre o GUARDIAN PRO MAX", style = MaterialTheme.typography.titleLarge)
                            Text("Versão 1.1.0 • Android 8 ou superior • Sem root.")
                            Text("Inclui explorador de APK, lista de apps, busca por linhas, filtros numéricos/hexadecimais, substituição controlada, desfazer e salvamento de cópia.")
                            Text("O sandbox do Android impede que apps comuns leiam livremente a memória e os diretórios privados de outros jogos. Para testar valores em tempo real, use uma API de depuração criada pelo próprio jogo.")
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                        status = "Autorize 'Sobrepor a outros apps', volte ao GUARDIAN e toque em Iniciar painel."
                                    } else {
                                        try {
                                            context.startForegroundService(Intent(context, FloatingPanelService::class.java))
                                            status = "Sobreposição solicitada. O ícone G deve aparecer na tela."
                                        } catch (e: Exception) {
                                            status = "Não foi possível iniciar o painel: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Ativar sobreposição GUARDIAN") }
                            OutlinedButton(
                                onClick = {
                                    context.stopService(Intent(context, FloatingPanelService::class.java))
                                    status = "Comando para encerrar o painel enviado."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Encerrar painel flutuante") }
                            Text("Autorize a sobreposição do GUARDIAN, volte ao app e toque novamente em Ativar sobreposição. O círculo G abre o painel e pode ser arrastado."), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                    Text("STATUS", style = MaterialTheme.typography.labelLarge)
                    Text(status, color = MaterialTheme.colorScheme.secondary)
                    Text("GUARDIAN não altera processos nem contorna proteções de jogos de terceiros.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
