package com.eren.yapayzekachat.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.eren.yapayzekachat.network.GeminiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ChatRepository(context: Context) {
    private val dao = AppDatabase.get(context).chatDao()
    private val appContext = context.applicationContext
    private val attachmentsDir = File(appContext.filesDir, "attachments").apply { mkdirs() }

    fun observeConversations(): Flow<List<ConversationEntity>> = dao.observeConversations()
    fun observeMessages(id: String): Flow<List<MessageEntity>> = dao.observeMessages(id)

    suspend fun createConversation(title: String = "Yeni sohbet"): ConversationEntity = withContext(Dispatchers.IO) {
        val conversation = ConversationEntity(title = title)
        dao.upsertConversation(conversation)
        conversation
    }

    suspend fun ensureConversation(id: String?, title: String = "Yeni sohbet"): ConversationEntity {
        if (id != null) {
            dao.getConversation(id)?.let { return it }
        }
        return createConversation(title)
    }

    suspend fun insertUserMessage(
        conversationId: String,
        text: String,
        uris: List<Uri>
    ): MessageEntity = withContext(Dispatchers.IO) {
        val attachments = uris.mapNotNull { copyAttachment(it) }
        val entity = MessageEntity(
            conversationId = conversationId,
            role = "user",
            text = text,
            attachmentsJson = encodeAttachments(attachments)
        )
        dao.insertMessage(entity)
        dao.updateConversation(
            id = conversationId,
            title = titleFromFirstMessage(text).ifBlank { "Dosyalı sohbet" }
        )
        entity
    }

    suspend fun insertAssistantMessage(conversationId: String, text: String): MessageEntity = withContext(Dispatchers.IO) {
        val entity = MessageEntity(
            conversationId = conversationId,
            role = "assistant",
            text = text
        )
        dao.insertMessage(entity)
        dao.updateConversation(conversationId, dao.getConversation(conversationId)?.title ?: "Sohbet")
        entity
    }

    suspend fun getRequestMessages(conversationId: String): List<GeminiApi.RequestMessage> = withContext(Dispatchers.IO) {
        dao.getMessages(conversationId).map { message ->
            GeminiApi.RequestMessage(
                role = message.role,
                text = message.text,
                attachments = decodeAttachments(message.attachmentsJson).mapNotNull { saved ->
                    val file = File(saved.path)
                    if (!file.exists()) return@mapNotNull null
                    GeminiApi.AttachmentPayload(
                        name = saved.name,
                        mimeType = saved.mimeType,
                        bytes = file.readBytes()
                    )
                }
            )
        }
    }

    suspend fun getLastUserMessage(conversationId: String): MessageEntity? = withContext(Dispatchers.IO) {
        dao.getMessages(conversationId).lastOrNull { it.role == "user" }
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        dao.getMessages(id).flatMap { decodeAttachments(it.attachmentsJson) }.forEach { File(it.path).delete() }
        dao.deleteMessages(id)
        dao.deleteConversation(id)
    }

    fun titleFromFirstMessage(text: String): String = text.trim().replace("\\s+".toRegex(), " ").take(42)

    private fun copyAttachment(uri: Uri): SavedAttachment? {
        return try {
            val name = getDisplayName(uri) ?: "dosya_${UUID.randomUUID()}"
            val mime = appContext.contentResolver.getType(uri) ?: "application/octet-stream"
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(attachmentsDir, "${UUID.randomUUID()}_$safeName")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            SavedAttachment(name, mime, target.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private data class SavedAttachment(val name: String, val mimeType: String, val path: String)

    private fun encodeAttachments(items: List<SavedAttachment>): String {
        val json = JSONArray()
        items.forEach {
            json.put(JSONObject().put("name", it.name).put("mimeType", it.mimeType).put("path", it.path))
        }
        return json.toString()
    }

    private fun decodeAttachments(value: String): List<SavedAttachment> {
        return try {
            val json = JSONArray(value)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    add(
                        SavedAttachment(
                            item.optString("name"),
                            item.optString("mimeType", "application/octet-stream"),
                            item.optString("path")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
