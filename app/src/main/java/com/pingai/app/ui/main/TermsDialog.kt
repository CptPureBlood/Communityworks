package com.pingai.app.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingai.app.R
import com.pingai.app.ui.theme.WinColors

/** Kullanım koşulları ve gizlilik politikası. Başlık satırları "# " ile başlar, paragraflar boş satırla ayrılır. */
@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WinColors.Card,
        title = { Text(stringResource(R.string.terms_title), color = WinColors.Text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.terms_close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                stringResource(R.string.terms_body).split("\n\n").forEach { para ->
                    if (para.startsWith("# ")) {
                        Spacer(Modifier.height(6.dp))
                        Text(para.removePrefix("# "), color = WinColors.Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    } else {
                        Text(para, color = WinColors.Text, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    )
}
