package com.guardian.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { editorText = it.readText() }
            openedName = uri.lastPathSegment ?: "Arquivo selecionado"
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
                        Text("GUARDIAN", style = MaterialTheme.typography.titleLarge)
                        Text("ANDROID TOOLKIT • SEM ROOT", style = MaterialTheme.typography.labelSmall)
                    } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = tab) {
                    listOf("Apps", "APK", "Editor", "Sobre").forEachIndexed { index, label ->
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
                            Text("Editor de arquivos", style = MaterialTheme.typography.titleLarge)
                            Text("Abra configurações ou arquivos de texto que você tem autorização para editar. Salve uma cópia para manter o original.")
                            OutlinedButton(onClick = { configPicker.launch(arrayOf("text/*", "application/json", "application/xml", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir arquivo") }
                            Text(openedName, style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(
                                value = editorText, onValueChange = { editorText = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                                label = { Text("Conteúdo") }, minLines = 8
                            )
                            Button(onClick = { savePicker.launch("guardian-backup.txt") }, enabled = editorText.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Salvar cópia / backup") }
                        }
                        else -> {
                            Text("Sobre o GUARDIAN", style = MaterialTheme.typography.titleLarge)
                            Text("Versão 1.0.0 • Android 8 ou superior • Sem root.")
                            Text("O sandbox do Android impede que apps comuns leiam livremente a memória e os diretórios privados de outros jogos. Para testar valores, use arquivos exportados ou uma interface de depuração criada pelo próprio jogo.")
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Abrir configuração de sobreposição") }
                            Text("O atalho abre as configurações do Android. O painel flutuante ainda não está implementado nesta versão.", style = MaterialTheme.typography.bodySmall)
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