package com.englishcoach.app.data.ai

import android.content.Context
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.Usage
import com.englishcoach.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.YearMonth

class AiException(message: String) : Exception(message)

/** Модель Claude, её цена за миллион токенов (вход / выход, $) и подпись в настройках. */
data class AiModel(val id: String, val title: String, val inPrice: Double, val outPrice: Double)

/**
 * Запросы к Claude API с ключом пользователя. Ключ хранится зашифрованно (SecretStore)
 * и уходит только на api.anthropic.com. Ведётся учёт потраченных токенов по месяцам.
 */
class AiRepository(context: Context, private val settings: SettingsRepository) {
    private val secrets = SecretStore(context)
    private val usagePrefs = context.getSharedPreferences("ai_usage", Context.MODE_PRIVATE)
    private val _hasKey = MutableStateFlow(secrets.get(KEY_NAME) != null)
    val hasKey: StateFlow<Boolean> = _hasKey
    @Volatile private var client: AnthropicClient? = null

    fun keyHint(): String? = secrets.get(KEY_NAME)?.takeLast(4)

    fun saveKey(key: String) {
        secrets.put(KEY_NAME, key.trim())
        client = null
        _hasKey.value = true
    }

    fun clearKey() {
        secrets.remove(KEY_NAME)
        client = null
        _hasKey.value = false
    }

    /**
     * Один запрос: системная инструкция + история + JSON-схема ответа.
     * Возвращает сообщение целиком — его надо класть в историю как есть (toParam), не редактируя.
     */
    suspend fun request(system: String, history: List<MessageParam>, schema: Map<String, Any>): Message = withContext(Dispatchers.IO) {
        val model = modelById(settings.current().aiModel)
        val params = MessageCreateParams.builder()
            .model(model.id)
            .maxTokens(16000L)
            .system(system)
            .apply { history.forEach { addMessage(it) } }
            .outputConfig(
                OutputConfig.builder()
                    // Для разговорного чата и проверки текста низкого усилия достаточно — и дешевле
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(schemaOf(schema)).build())
                    .build(),
            )
            .apply {
                // Если Sonnet откажется отвечать, сервер сам передаст запрос подходящей модели
                if (model.id == SONNET.id) {
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()

        val msg = try {
            client().messages().create(params)
        } catch (e: UnauthorizedException) {
            throw AiException("Ключ API не подходит. Проверьте его в настройках.")
        } catch (e: PermissionDeniedException) {
            throw AiException("У этого ключа нет доступа к модели ${model.title}.")
        } catch (e: NotFoundException) {
            throw AiException("Модель ${model.id} не найдена.")
        } catch (e: RateLimitException) {
            throw AiException("Слишком много запросов. Подождите минуту и попробуйте снова.")
        } catch (e: AnthropicServiceException) {
            throw AiException("Сервис ответил ошибкой (${e.statusCode()}). Если дело в оплате — проверьте баланс в консоли Anthropic.")
        } catch (e: AnthropicIoException) {
            throw AiException("Нет связи с сервером. Проверьте интернет.")
        }
        addUsage(model, msg.usage())
        if (msg.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw AiException("Модель отказалась отвечать на это сообщение. Попробуйте сформулировать иначе.")
        }
        msg
    }

    /** Стоимость запросов за текущий месяц, $ (по ценам из таблицы моделей). */
    fun monthCostUsd(): Double = MODELS.sumOf { m ->
        val month = YearMonth.now()
        usagePrefs.getLong("in_${month}_${m.id}", 0) / 1e6 * m.inPrice +
            usagePrefs.getLong("out_${month}_${m.id}", 0) / 1e6 * m.outPrice
    }

    private fun addUsage(model: AiModel, u: Usage) {
        val month = YearMonth.now()
        val inKey = "in_${month}_${model.id}"
        val outKey = "out_${month}_${model.id}"
        usagePrefs.edit()
            .putLong(inKey, usagePrefs.getLong(inKey, 0) + u.inputTokens())
            .putLong(outKey, usagePrefs.getLong(outKey, 0) + u.outputTokens())
            .apply()
    }

    private fun client(): AnthropicClient = client ?: run {
        val key = secrets.get(KEY_NAME) ?: throw AiException("Добавьте ключ API в настройках.")
        AnthropicOkHttpClient.builder().apiKey(key).build().also { client = it }
    }

    private fun schemaOf(schema: Map<String, Any>): JsonOutputFormat.Schema =
        JsonOutputFormat.Schema.builder().apply {
            schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
        }.build()

    companion object {
        private const val KEY_NAME = "anthropic_api_key"

        val SONNET = AiModel("claude-sonnet-5-5", "Claude Sonnet 5.5", 2.0, 10.0)
        val HAIKU = AiModel("claude-haiku-5-5", "Claude Haiku 5.5", 0.10, 0.50)
        val MODELS = listOf(SONNET, HAIKU)

        fun modelById(id: String) = MODELS.firstOrNull { it.id == id } ?: SONNET

        /** Текст ответа (JSON по схеме) из сообщения. */
        fun textOf(msg: Message): String = msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")

        fun userMessage(text: String): MessageParam =
            MessageParam.builder().role(MessageParam.Role.USER).content(text).build()

        // Помощники для JSON-схем ответа: все поля обязательны, лишних нет — так требует structured output
        fun obj(vararg props: Pair<String, Any>): Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to props.toMap(),
            "required" to props.map { it.first },
            "additionalProperties" to false,
        )

        fun str() = mapOf("type" to "string")
        fun int() = mapOf("type" to "integer")
        fun arr(items: Any) = mapOf("type" to "array", "items" to items)
    }
}
