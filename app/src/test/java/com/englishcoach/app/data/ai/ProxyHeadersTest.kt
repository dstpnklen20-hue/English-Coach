package com.englishcoach.app.data.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Что SDK реально отправляет посреднику: адрес, Authorization: Bearer и отсутствие x-api-key.
 * Локальный сервер вместо настоящего — ключ здесь пробный, наружу ничего не уходит.
 */
class ProxyHeadersTest {
    @Test
    fun bearerKeyGoesToProxyPathWithoutApiKeyHeader() {
        val seen = mutableMapOf<String, String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seen["path"] = ex.requestURI.path
            seen["authorization"] = ex.requestHeaders.getFirst("Authorization")
            seen["x-api-key"] = ex.requestHeaders.getFirst("x-api-key")
            val body = """{"error":{"message":"Invalid API key.","type":"authentication_error"}}""".toByteArray()
            ex.sendResponseHeaders(401, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = AnthropicOkHttpClient.builder()
                .baseUrl("http://127.0.0.1:${server.address.port}")
                .authToken("test-token-0123456789")
                .maxRetries(0)
                .build()
            runCatching {
                client.messages().create(
                    MessageCreateParams.builder().model("claude-sonnet-5").maxTokens(16L).addUserMessage("ping").build(),
                )
            }
        } finally {
            server.stop(0)
        }
        assertEquals("/v1/messages", seen["path"])
        assertEquals("Bearer test-token-0123456789", seen["authorization"])
        assertNull(seen["x-api-key"])
    }
}
