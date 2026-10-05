package io.github.ssebanom.ling.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.ssebanom.ling.domain.ChatMessage
import io.github.ssebanom.ling.domain.Role
import io.github.ssebanom.ling.domain.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Conversation(
    val id: Long,
    val title: String,
    val thinking: Boolean,
    val systemPrompt: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 메시지별 성능 기록(UI 표시용) */
data class MessageStats(
    val prefillTokens: Int = 0,
    val reusedTokens: Int = 0,
    val prefillMs: Double = 0.0,
    val decodeTokens: Int = 0,
    val decodeMs: Double = 0.0,
    val ttftMs: Double = 0.0,
    val restored: Boolean = false,
) {
    val decodeTps: Double get() = if (decodeMs > 0) decodeTokens * 1000.0 / decodeMs else 0.0
}

data class StoredMessage(val id: Long, val message: ChatMessage, val stats: MessageStats?)

/** SQLite 대화 저장소. rawTokens 는 BLOB(int32 LE)로 저장해 재시작 후에도 캐시 정합성 유지 */
class ConversationStore(context: Context) : SQLiteOpenHelper(context, "ling.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE conversations(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, thinking INTEGER NOT NULL,
                system_prompt TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT, conv_id INTEGER NOT NULL, idx INTEGER NOT NULL,
                role TEXT NOT NULL, content TEXT NOT NULL, reasoning TEXT, tool_calls TEXT,
                raw_tokens BLOB, thinking_at_gen INTEGER, stats TEXT,
                FOREIGN KEY(conv_id) REFERENCES conversations(id) ON DELETE CASCADE)""",
        )
        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conv_id, idx)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    suspend fun listConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM conversations ORDER BY updated_at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toConversation()) }
        }
    }

    suspend fun getConversation(id: Long): Conversation? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM conversations WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toConversation() else null
        }
    }

    suspend fun createConversation(title: String, thinking: Boolean, systemPrompt: String): Conversation =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val id = writableDatabase.insertOrThrow("conversations", null, ContentValues().apply {
                put("title", title); put("thinking", if (thinking) 1 else 0); put("system_prompt", systemPrompt)
                put("created_at", now); put("updated_at", now)
            })
            Conversation(id, title, thinking, systemPrompt, now, now)
        }

    suspend fun updateConversation(c: Conversation) = withContext(Dispatchers.IO) {
        writableDatabase.update("conversations", ContentValues().apply {
            put("title", c.title); put("thinking", if (c.thinking) 1 else 0); put("system_prompt", c.systemPrompt)
            put("updated_at", System.currentTimeMillis())
        }, "id=?", arrayOf(c.id.toString()))
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("conversations", "id=?", arrayOf(id.toString()))
    }

    suspend fun messages(convId: Long): List<StoredMessage> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT * FROM messages WHERE conv_id=? ORDER BY idx", arrayOf(convId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val role = Role.valueOf(c.getString(c.getColumnIndexOrThrow("role")))
                    val rawIdx = c.getColumnIndexOrThrow("raw_tokens")
                    val tagIdx = c.getColumnIndexOrThrow("thinking_at_gen")
                    val msg = ChatMessage(
                        role = role,
                        content = c.getString(c.getColumnIndexOrThrow("content")),
                        reasoning = c.getString(c.getColumnIndexOrThrow("reasoning")),
                        toolCalls = decodeToolCalls(c.getString(c.getColumnIndexOrThrow("tool_calls"))),
                        rawTokens = if (c.isNull(rawIdx)) null else decodeTokens(c.getBlob(rawIdx)),
                        thinkingAtGeneration = if (c.isNull(tagIdx)) null else c.getInt(tagIdx) != 0,
                    )
                    val statsJson = c.getString(c.getColumnIndexOrThrow("stats"))
                    add(StoredMessage(c.getLong(c.getColumnIndexOrThrow("id")), msg, statsJson?.let(::decodeStats)))
                }
            }
        }
    }

    /** 대화 메시지를 통째로 교체(편집/재생성 시 뒤쪽 삭제 포함) */
    suspend fun replaceMessages(convId: Long, msgs: List<Pair<ChatMessage, MessageStats?>>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", "conv_id=?", arrayOf(convId.toString()))
            msgs.forEachIndexed { i, (m, s) ->
                db.insertOrThrow("messages", null, ContentValues().apply {
                    put("conv_id", convId); put("idx", i); put("role", m.role.name); put("content", m.content)
                    put("reasoning", m.reasoning)
                    put("tool_calls", if (m.toolCalls.isEmpty()) null else encodeToolCalls(m.toolCalls))
                    put("raw_tokens", m.rawTokens?.let(::encodeTokens))
                    m.thinkingAtGeneration?.let { put("thinking_at_gen", if (it) 1 else 0) }
                    put("stats", s?.let(::encodeStats))
                })
            }
            db.update("conversations", ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
                "id=?", arrayOf(convId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun android.database.Cursor.toConversation() = Conversation(
        getLong(getColumnIndexOrThrow("id")),
        getString(getColumnIndexOrThrow("title")),
        getInt(getColumnIndexOrThrow("thinking")) != 0,
        getString(getColumnIndexOrThrow("system_prompt")),
        getLong(getColumnIndexOrThrow("created_at")),
        getLong(getColumnIndexOrThrow("updated_at")),
    )

    companion object {
        fun encodeTokens(t: IntArray): ByteArray =
            ByteBuffer.allocate(t.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asIntBuffer().put(t) }.array()

        fun decodeTokens(b: ByteArray): IntArray {
            val ib = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            return IntArray(ib.remaining()).also { ib.get(it) }
        }

        fun encodeToolCalls(calls: List<ToolCall>): String = JSONArray().apply {
            for (c in calls) put(JSONObject().apply {
                put("name", c.name)
                put("args", JSONArray().apply {
                    for ((k, v) in c.arguments) put(JSONArray().put(k).put(v?.toString() ?: JSONObject.NULL))
                })
            })
        }.toString()

        fun decodeToolCalls(s: String?): List<ToolCall> {
            if (s.isNullOrEmpty()) return emptyList()
            val a = JSONArray(s)
            return (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val args = LinkedHashMap<String, Any?>()
                val aa = o.getJSONArray("args")
                for (j in 0 until aa.length()) {
                    val kv = aa.getJSONArray(j)
                    args[kv.getString(0)] = if (kv.isNull(1)) null else kv.getString(1)
                }
                ToolCall(o.getString("name"), args)
            }
        }

        fun encodeStats(s: MessageStats): String = JSONObject().apply {
            put("pt", s.prefillTokens); put("rt", s.reusedTokens); put("pms", s.prefillMs)
            put("dt", s.decodeTokens); put("dms", s.decodeMs); put("ttft", s.ttftMs); put("rs", s.restored)
        }.toString()

        fun decodeStats(s: String): MessageStats = JSONObject(s).let {
            MessageStats(it.optInt("pt"), it.optInt("rt"), it.optDouble("pms"), it.optInt("dt"), it.optDouble("dms"),
                it.optDouble("ttft"), it.optBoolean("rs"))
        }
    }
}
