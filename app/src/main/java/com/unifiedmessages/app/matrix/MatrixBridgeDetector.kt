package com.unifiedmessages.app.matrix

import com.unifiedmessages.app.provider.NetworkType
import java.util.Locale
import org.json.JSONObject

data class VerifiedBridge(
    val stateEventId: String,
    val bridgeBotId: String,
    val protocolId: String,
    val remoteChannelId: String,
    val networkType: NetworkType,
)

/** Room state is untrusted until both its sender and advertised bot match the configured bot. */
object MatrixBridgeDetector {
    val stateEventTypes = listOf("m.bridge", "uk.half-shot.bridge")

    fun detect(
        eventType: String,
        stateEventId: String,
        sender: String,
        contentJson: String,
        configuredBotId: String?,
    ): VerifiedBridge? {
        val bot = configuredBotId?.trim()?.takeIf { it.startsWith("@") && ':' in it } ?: return null
        if (eventType !in stateEventTypes || sender != bot || stateEventId.isBlank()) return null
        val content = try { JSONObject(contentJson) } catch (_: Exception) { return null }
        if (content.optString("bridgebot") != bot) return null
        val protocolId = content.optJSONObject("protocol")?.optString("id")?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val channelId = content.optJSONObject("channel")?.optString("id")?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val network = when (protocolId.lowercase(Locale.ROOT).replace('-', '_')) {
            "whatsapp", "wa" -> NetworkType.WHATSAPP
            "telegram", "tg" -> NetworkType.TELEGRAM
            "gmessages", "googlemessages", "google_messages" -> NetworkType.GOOGLE_MESSAGES
            else -> NetworkType.MATRIX
        }
        return VerifiedBridge(stateEventId, bot, protocolId, channelId, network)
    }
}
