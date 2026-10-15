package com.unifiedmessages.app.provider

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSendRouterTest {
    private val route = MessageRoute("thread-1", "sms:local", "sms", NetworkType.SMS)

    @Test fun validSendUsesThreadProviderAndRejectsBlankText() = runBlocking {
        val provider = FakeProvider()
        val router = router(provider)
        router.send(route.threadId, "  Hello  ", emptyList())
        assertEquals(listOf("Hello"), provider.sent)

        val failure = runCatching { router.send(route.threadId, " \n ", emptyList()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(1, provider.sent.size)
    }

    @Test fun concurrentDoubleTapCannotDispatchTwice() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = FakeProvider().apply {
            onSend = { entered.complete(Unit); release.await() }
        }
        val router = router(provider)
        val first = async { router.send(route.threadId, "Hello", emptyList()) }
        entered.await()
        val secondFailure = runCatching { router.send(route.threadId, "Hello", emptyList()) }.exceptionOrNull()
        assertTrue(secondFailure is IllegalStateException)
        release.complete(Unit)
        first.await()
        assertEquals(1, provider.sent.size)
    }

    @Test fun failedDispatchReleasesGuardAndFailedMessageCanBeRetried() = runBlocking {
        val provider = FakeProvider().apply { onSend = { error("Carrier rejected send") } }
        val router = router(provider)
        val failure = runCatching { router.send(route.threadId, "Hello", emptyList()) }.exceptionOrNull()
        assertEquals("Carrier rejected send", failure?.message)
        provider.onSend = {}
        router.retry("failed-message")
        assertEquals(listOf("failed-message"), provider.retried)
        router.send(route.threadId, "Try again", emptyList())
        assertEquals(listOf("Hello", "Try again"), provider.sent)
    }

    @Test fun unavailableAndUnknownRoutesHaveExplicitReasons() {
        val provider = FakeProvider().apply { issue = "Allow SMS permissions to send." }
        val router = router(provider)
        assertEquals("Allow SMS permissions to send.",
            router.availability(route, listOf("+15551234567"), "US").disabledReason)
        provider.issue = null
        assertEquals("This sender can't receive replies.",
            router.availability(route, listOf("ALERT"), "US").disabledReason)
        val unknown = route.copy(providerId = "imessage", accountId = "imessage:local",
            networkType = NetworkType.IMESSAGE)
        assertEquals("Messaging is not connected for this conversation.",
            router.availability(unknown, emptyList(), "US").disabledReason)
    }

    @Test fun matrixRouteUsesItsAccountWithoutPhoneNumberChecks() = runBlocking {
        val matrix = route.copy(accountId = "matrix:@alice:matrix.org", providerId = "matrix",
            networkType = NetworkType.MATRIX, remoteThreadId = "!room:matrix.org")
        val provider = FakeProvider("matrix")
        val router = ProviderSendRouter.forTesting(object : SendRouteLookup {
            override suspend fun forThread(threadId: String) = matrix.takeIf { it.threadId == threadId }
            override suspend fun forMessage(messageId: String): MessageRoute? = null
        }, listOf(provider))
        assertTrue(router.availability(matrix, listOf("@bob:matrix.org"), "US").canSend)
        router.send(matrix.threadId, "Hi", emptyList())
        assertEquals(listOf("Hi"), provider.sent)
    }

    private fun router(provider: FakeProvider) = ProviderSendRouter.forTesting(
        object : SendRouteLookup {
            override suspend fun forThread(threadId: String): MessageRoute? =
                route.takeIf { it.threadId == threadId }
            override suspend fun forMessage(messageId: String): MessageRoute? =
                route.takeIf { messageId == "failed-message" }
        },
        listOf(provider),
    )

    private class FakeProvider(override val id: String = "sms") : SendCommandProvider {
        var issue: String? = null
        var onSend: suspend () -> Unit = {}
        val sent = mutableListOf<String>()
        val retried = mutableListOf<String>()

        override fun connectionIssue(): String? = issue
        override suspend fun send(threadId: String, body: String, attachments: List<Uri>) {
            sent += body
            onSend()
        }
        override suspend fun retry(messageId: String) { retried += messageId }
    }
}
