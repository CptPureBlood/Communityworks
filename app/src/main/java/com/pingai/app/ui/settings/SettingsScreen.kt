package com.pingai.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingai.app.R
import com.pingai.app.data.*
import com.pingai.app.ui.onboarding.CONTENT_LANGUAGES
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.launch
import java.util.UUID

private const val MAX_TOPICS_FREE = 1
private const val MAX_TOPICS_PRO = 3
// Basit yerel Pro açma kodu (gerçek ödeme sistemi değil). İstersen değiştir.
private const val PRO_UNLOCK_CODE = "PINGAI-PRO"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val keyStore = remember { SecureKeyStore(context) }
    val scope = rememberCoroutineScope()
    fun s(id: Int) = context.getString(id)

    val provider by prefs.provider.collectAsState(initial = AiProvider.GEMINI)
    val topics by prefs.topics.collectAsState(initial = emptyList())
    val activeTopicId by prefs.activeTopicId.collectAsState(initial = null)
    val intervalMin by prefs.refreshIntervalMinutes.collectAsState(initial = RefreshInterval.H12.minutes)
    val notificationsEnabled by prefs.notificationsEnabled.collectAsState(initial = true)
    val isPro by prefs.isPro.collectAsState(initial = false)
    val contentLanguage by prefs.contentLanguage.collectAsState(initial = "system")

    var newTopic by remember { mutableStateOf("") }
    var proCode by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    LaunchedEffect(provider) { apiKeyInput = keyStore.getKey(provider) ?: "" }

    val maxTopics = if (isPro) MAX_TOPICS_PRO else MAX_TOPICS_FREE
    val activeTopic = topics.firstOrNull { it.id == activeTopicId } ?: topics.firstOrNull()

    val intervalOptions = listOf(
        RefreshInterval.H12 to R.string.interval_12h,
        RefreshInterval.H6 to R.string.interval_6h,
        RefreshInterval.H4 to R.string.interval_4h,
        RefreshInterval.H2 to R.string.interval_2h,
        RefreshInterval.H1 to R.string.interval_1h,
        RefreshInterval.M30 to R.string.interval_30m,
        RefreshInterval.M15 to R.string.interval_15m,
        RefreshInterval.M10 to R.string.interval_10m
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Ping AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                s(R.string.settings_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Durum: son bilgi veya hata
        SectionCard(title = activeTopic?.name) {
            val info = activeTopic?.shortInfo.orEmpty().ifBlank { "…" }
            Text(
                info,
                style = MaterialTheme.typography.bodyLarge,
                color = if (info.startsWith("⚠")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            if (activeTopic != null) {
                Button(
                    onClick = { WorkScheduler.runNow(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s(R.string.settings_manual_refresh)) }
            }
        }

        // Sağlayıcı ve API anahtarı
        SectionCard(title = s(R.string.settings_provider)) {
            Picker(
                valueText = provider.displayLabel,
                options = AiProvider.entries.map { p ->
                    Triple(p, p.displayLabel + if (!isPro && p != AiProvider.GEMINI) " 🔒" else "",
                        isPro || p == AiProvider.GEMINI)
                },
                onPick = { p -> scope.launch { prefs.setProvider(p) } }
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { keyStore.setKey(provider, apiKeyInput) },
                modifier = Modifier.align(Alignment.End)
            ) { Text("OK") }
        }

        // Konular
        SectionCard(title = "${s(R.string.settings_topics)} (${topics.size}/$maxTopics)") {
            topics.forEach { topic ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = topic.id == activeTopicId,
                        onClick = { scope.launch { prefs.setActiveTopicId(topic.id) } }
                    )
                    Text(topic.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(
                        enabled = topics.size > 1,
                        onClick = {
                            scope.launch {
                                prefs.removeTopic(topic.id)
                                if (topic.id == activeTopicId) {
                                    prefs.getTopicsOnce().firstOrNull()?.let { prefs.setActiveTopicId(it.id) }
                                }
                            }
                        }
                    ) { Icon(Icons.Default.Delete, contentDescription = null) }
                }
            }
            if (topics.size < maxTopics) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTopic,
                        onValueChange = { newTopic = it },
                        label = { Text(s(R.string.settings_add_topic)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (newTopic.isNotBlank()) {
                            scope.launch {
                                val t = Topic(id = UUID.randomUUID().toString(), name = newTopic.trim())
                                prefs.upsertTopic(t)
                                if (activeTopicId == null) prefs.setActiveTopicId(t.id)
                                newTopic = ""
                                WorkScheduler.runNow(context)
                            }
                        }
                    }) { Text("+") }
                }
            }
        }

        // Yenilenme sıklığı
        SectionCard(title = s(R.string.settings_refresh_interval)) {
            val current = intervalOptions.firstOrNull { it.first.minutes == intervalMin } ?: intervalOptions.first()
            Picker(
                valueText = s(current.second),
                options = intervalOptions.map { (interval, label) ->
                    Triple(interval, s(label) + if (!isPro && interval.isPro) " 🔒" else "", isPro || !interval.isPro)
                },
                onPick = { interval ->
                    scope.launch {
                        prefs.setRefreshIntervalMinutes(interval.minutes)
                        WorkScheduler.scheduleNext(context, interval.minutes)
                    }
                }
            )
        }

        // Bildirim ve içerik dili
        SectionCard(title = null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s(R.string.settings_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { scope.launch { prefs.setNotificationsEnabled(it) } }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                s(R.string.settings_content_language),
                style = MaterialTheme.typography.titleMedium
            )
            val langLabel = CONTENT_LANGUAGES.firstOrNull { it.first == contentLanguage }?.second
                ?: CONTENT_LANGUAGES.first().second
            Picker(
                valueText = langLabel,
                options = CONTENT_LANGUAGES.map { Triple(it.first, it.second, true) },
                onPick = { code -> scope.launch { prefs.setContentLanguage(code) } }
            )
        }

        // Pro
        SectionCard(title = if (isPro) null else s(R.string.settings_pro)) {
            if (isPro) {
                Text(
                    "✓ " + s(R.string.settings_pro_active),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(s(R.string.pro_upgrade_body), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = proCode,
                        onValueChange = { proCode = it },
                        label = { Text(s(R.string.settings_pro_code_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (proCode.trim() == PRO_UNLOCK_CODE) scope.launch { prefs.setPro(true) }
                    }) { Text("OK") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

/** Açılır liste: (değer, görünen metin, seçilebilir mi) */
@Composable
private fun <T> Picker(
    valueText: String,
    options: List<Triple<T, String, Boolean>>,
    onPick: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(valueText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text, enabled) ->
                DropdownMenuItem(
                    text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
                    enabled = enabled,
                    onClick = {
                        open = false
                        onPick(key)
                    }
                )
            }
        }
    }
}
