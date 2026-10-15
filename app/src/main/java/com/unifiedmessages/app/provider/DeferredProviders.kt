package com.unifiedmessages.app.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Future transports are intentionally inert until their real connectors are implemented. */
sealed class DeferredProvider(final override val id: String) : MessageProvider {
    final override val capabilities = Capabilities()
    final override val events: Flow<MessageEvent> = emptyFlow()
}

class OpenBubblesProvider : DeferredProvider("imessage")
class RcsProvider : DeferredProvider("rcs")
class OpenPigeonProvider : DeferredProvider("openpigeon")
