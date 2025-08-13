package com.penumbraos.bridge_settings

import com.penumbraos.bridge_settings.json.toJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

object AnyAsJsonElementSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonElement = value.toJsonElement()
        encoder.encodeSerializableValue(JsonElement.serializer(), jsonElement)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonElement = decoder.decodeSerializableValue(JsonElement.serializer())
        return when {
            jsonElement.jsonPrimitive.isString -> jsonElement.jsonPrimitive.content
            else -> jsonElement.jsonPrimitive.content
        }
    }
}

object NumberAsJsonElementSerializer : KSerializer<Number> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Number")

    override fun serialize(encoder: Encoder, value: Number) {
        val jsonElement = value.toJsonElement()
        encoder.encodeSerializableValue(JsonElement.serializer(), jsonElement)
    }

    override fun deserialize(decoder: Decoder): Number {
        val jsonElement = decoder.decodeSerializableValue(JsonElement.serializer())
        val primitive = jsonElement.jsonPrimitive
        return when {
            primitive.intOrNull != null -> primitive.int
            primitive.longOrNull != null -> primitive.long
            primitive.floatOrNull != null -> primitive.float
            primitive.doubleOrNull != null -> primitive.double
            else -> primitive.content.toDoubleOrNull() ?: 0.0
        }
    }
}

@Serializable
data class LocalActionDefinition(
    val key: String,
    val displayText: String,
    val parameters: List<ActionParameter> = emptyList(),
    val description: String? = null
)

@Serializable
data class ActionParameter(
    val name: String,
    val type: SettingType,
    val required: Boolean = true,
    @Serializable(with = AnyAsJsonElementSerializer::class)
    val defaultValue: Any? = null,
    val description: String? = null
)

@Serializable
data class SettingDefinition(
    val key: String,
    val type: SettingType,
    @Serializable(with = AnyAsJsonElementSerializer::class)
    val defaultValue: Any,
    val validation: SettingValidation? = null
)

@Serializable
enum class SettingType {
    BOOLEAN, INTEGER, STRING, FLOAT, ACTION
}

@Serializable
data class SettingValidation(
    @Serializable(with = NumberAsJsonElementSerializer::class)
    val min: Number? = null,
    @Serializable(with = NumberAsJsonElementSerializer::class)
    val max: Number? = null,
    val allowedValues: List<@Serializable(with = AnyAsJsonElementSerializer::class) Any>? = null,
    val regex: String? = null
)

@Serializable
data class AppSettingsCategory(
    val appId: String,
    val category: String,
    val definitions: Map<String, SettingDefinition>,
    val values: MutableMap<String, @Serializable(with = AnyAsJsonElementSerializer::class) Any> = mutableMapOf()
)

@Serializable
data class PersistedSettings(
    val systemSettings: Map<String, String> = emptyMap(),
    val appSettings: Map<String, Map<String, Map<String, JsonElement>>> = emptyMap()
)

@Serializable
data class ExecutingAction(
    val providerId: String,
    val actionName: String,
    val params: Map<String, @Serializable(with = AnyAsJsonElementSerializer::class) Any>,
    val startTime: Long
)
