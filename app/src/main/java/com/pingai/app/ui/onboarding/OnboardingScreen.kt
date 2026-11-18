package com.pingai.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.pingai.app.R
import com.pingai.app.data.AiProvider
import com.pingai.app.data.Prefs
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.data.Topic
import com.pingai.app.widget.PingWidget
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private enum class Step { WELCOME, PROVIDER, KEY, TOPIC, LANGUAGE }

val CONTENT_LANGUAGES = listOf(
    "tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español"
)

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val keyStore = remember { SecureKeyStore(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.WELCOME) }
    var provider by remember { mutableStateOf(AiProvider.GEMINI) }
    var apiKey by remember { mutableStateOf("") }
    var topicText by remember { mutableStateOf("") }
    var contentLanguage by remember {
        mutableStateOf(Locale.getDefault().language.takeIf { l -> CONTENT_LANGUAGES.any { it.first == l } } ?: "en")
    }

    fun s(id: Int) = context.getString(id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        when (step) {
            Step.WELCOME -> {
                Title(s(R.string.onboarding_welcome_title))
                Text(s(R.string.onboarding_welcome_body))
                Spacer(Modifier.height(32.dp))
                NextButton(s(R.string.onboarding_next), true) { step = Step.PROVIDER }
            }

            Step.PROVIDER -> {
                Title(s(R.string.onboarding_provider_title))
                listOf(
                    AiProvider.GEMINI to R.string.onboarding_provider_gemini,
                    AiProvider.OPENAI to R.string.onboarding_provider_openai,
                    AiProvider.CLAUDE to R.string.onboarding_provider_claude
                ).forEach { (p, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = provider == p, onClick = { provider = p })
                        Text(s(label))
                    }
                }
                Spacer(Modifier.height(24.dp))
                NextButton(s(R.string.onboarding_next), true) { step = Step.KEY }
            }

            Step.KEY -> {
                Title(s(R.string.onboarding_key_title))
                Text(s(R.string.onboarding_key_instructions))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(s(R.string.onboarding_open_studio)) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(s(R.string.onboarding_key_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                NextButton(s(R.string.onboarding_next), apiKey.isNotBlank()) {
                    keyStore.setKey(provider, apiKey)
                    step = Step.TOPIC
                }
            }

            Step.TOPIC -> {
                Title(s(R.string.onboarding_topic_title))
                OutlinedTextField(
                    value = topicText,
                    onValueChange = { topicText = it },
                    label = { Text(s(R.string.onboarding_topic_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                NextButton(s(R.string.onboarding_next), topicText.isNotBlank()) { step = Step.LANGUAGE }
            }

            Step.LANGUAGE -> {
                Title(s(R.string.onboarding_language_title))
                CONTENT_LANGUAGES.forEach { (code, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = contentLanguage == code, onClick = { contentLanguage = code })
                        Text(label)
                    }
                }
                Spacer(Modifier.height(24.dp))
                NextButton(s(R.string.onboarding_finish), true) {
                    scope.launch {
                        val topic = Topic(id = UUID.randomUUID().toString(), name = topicText.trim())
                        prefs.setProvider(provider)
                        prefs.setContentLanguage(contentLanguage)
                        prefs.upsertTopic(topic)
                        prefs.setActiveTopicId(topic.id)
                        prefs.setRefreshIntervalMinutes(720)
                        prefs.setNotificationsEnabled(true)
                        WorkScheduler.runNow(context)
                        PingWidget().updateAll(context)
                        prefs.setOnboardingDone(true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun NextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
