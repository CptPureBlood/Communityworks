package com.pingai.app.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pingai.app.core.TipActions
import com.pingai.app.data.Prefs
import kotlinx.coroutines.launch

/** Widget'tan paylaşım: şeffaf aktivite, paylaşım penceresini açıp kapanır. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val topic = Prefs(applicationContext).activeTopic()
            if (topic != null && topic.hasTip) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, TipActions.shareText(topic))
                }
                startActivity(Intent.createChooser(send, null))
            }
            finish()
        }
    }
}
