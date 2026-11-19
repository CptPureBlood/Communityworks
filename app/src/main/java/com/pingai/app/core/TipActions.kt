package com.pingai.app.core

import android.content.Context
import com.pingai.app.data.Prefs
import com.pingai.app.data.MissedQ
import com.pingai.app.data.SavedTip
import com.pingai.app.data.Topic
import com.pingai.app.widget.WidgetUpdater
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Widget, bildirim ve uygulamanın ortak kullandığı kullanıcı eylemleri. */
object TipActions {

    private val lock = Mutex()

    /** Cevabı kaydeder. Döner: true doğru, false yanlış, null (zaten cevaplanmış / geçersiz). */
    suspend fun answer(app: Context, topicId: String?, index: Int): Boolean? = lock.withLock {
        val prefs = Prefs(app)
        val topic = findTopic(prefs, topicId) ?: return null
        if (!topic.hasQuiz || topic.answeredIdx >= 0 || index !in topic.options.indices) return null
        val correct = index == topic.correctIdx
        prefs.updateTopic(topic.id) { it.copy(answeredIdx = index) }
        prefs.recordAnswer(correct)
        if (!correct) {
            prefs.addMissed(
                MissedQ(topic.id, topic.name, topic.title, topic.question, topic.options, topic.correctIdx, topic.shortInfo)
            )
        }
        Haptics.answer(app, correct)
        correct
    }

    suspend fun toggleSave(app: Context, topicId: String?): Boolean {
        val prefs = Prefs(app)
        val t = findTopic(prefs, topicId) ?: return false
        if (!t.hasTip) return false
        val tip = SavedTip(
            id = t.tipId,
            topic = t.name,
            question = t.question,
            answer = t.options.getOrNull(t.correctIdx).orEmpty(),
            info = t.shortInfo,
            savedAt = System.currentTimeMillis()
        )
        return prefs.toggleSaved(tip)
    }

    /** like=true: beğen, false: beğenme. Aynı düğmeye tekrar basmak geri alır. */
    suspend fun feedback(app: Context, topicId: String?, like: Boolean) {
        val prefs = Prefs(app)
        val t = findTopic(prefs, topicId) ?: return
        val target = if (like) 1 else -1
        val newValue = if (t.feedback == target) 0 else target
        prefs.updateTopic(t.id) { cur ->
            val title = cur.title
            var liked = cur.liked - title
            var avoid = cur.avoid - title
            if (title.isNotBlank()) {
                if (newValue == 1) liked = (listOf(title) + liked).take(3)
                if (newValue == -1) avoid = (listOf(title) + avoid).take(6)
            }
            cur.copy(feedback = newValue, liked = liked, avoid = avoid)
        }
    }

    fun shareText(t: Topic): String =
        "📚 ${t.name}\n\n${t.shortInfo}\n\n— Ping AI"

    private suspend fun findTopic(prefs: Prefs, id: String?): Topic? {
        val all = prefs.getTopicsOnce()
        return all.firstOrNull { it.id == id } ?: prefs.activeTopic()
    }

    suspend fun refreshWidget(app: Context) = WidgetUpdater.updateAll(app)
}
