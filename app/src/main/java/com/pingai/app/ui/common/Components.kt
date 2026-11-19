package com.pingai.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingai.app.R
import com.pingai.app.ui.theme.WinColors

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = when {
            selected -> WinColors.Accent
            enabled -> WinColors.Text
            else -> WinColors.TextDim.copy(alpha = 0.5f)
        },
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .clip(shape)
            .background(if (selected) WinColors.Accent.copy(alpha = 0.16f) else WinColors.Card)
            .border(BorderStroke(1.dp, if (selected) WinColors.Accent else WinColors.Stroke), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WinColors.Card)
            .border(BorderStroke(1.dp, WinColors.Stroke), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WinColors.Text)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            number.toString(),
            color = WinColors.Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(WinColors.Accent.copy(alpha = 0.16f))
                .padding(top = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(text, color = WinColors.Text, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
    }
}

/** Kategori çipleri + serbest metin + "istediğin kadar ayrıntılı yaz" ipucu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPicker(value: String, onValueChange: (String) -> Unit) {
    val cats = listOf(
        "📜" to R.string.cat_history,
        "⚖️" to R.string.cat_law,
        "✨" to R.string.cat_fun_facts,
        "🔬" to R.string.cat_science,
        "🌍" to R.string.cat_geography,
        "💻" to R.string.cat_technology,
        "🩺" to R.string.cat_health,
        "🗣️" to R.string.cat_languages,
        "⚓" to R.string.cat_maritime,
        "💼" to R.string.cat_business,
        "🚀" to R.string.cat_space,
        "🧠" to R.string.cat_psychology
    )
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            cats.forEach { (emoji, res) ->
                val name = stringResource(res)
                Chip(label = "$emoji $name", selected = value.trim() == name) { onValueChange(name) }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.topic_field_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.topic_hint_specific),
            color = WinColors.TextDim,
            fontSize = 13.sp,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
