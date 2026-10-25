package com.mvp.core.featureflags.data.remote

import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureStage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lecture du JSON distant (ADR 0021) : `{ "schemaVersion": 1, "flags": { "catalog": true } }`.
 * La réponse est refusée en entier si elle est trop grande, illisible ou d'une autre version de format ; ensuite,
 * flag par flag, une valeur non booléenne, une clé inconnue ou une feature encore en développement est ignorée.
 */
internal object RemoteFlagsParser {

    const val MAX_BYTES = 16 * 1024
    const val SCHEMA_VERSION = 1

    fun parse(bytes: ByteArray): Map<String, Boolean> {
        if (bytes.size > MAX_BYTES) throw SerializationException("Réponse des flags trop grande")
        val root = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
            ?: throw SerializationException("Réponse des flags inattendue")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        if (version != SCHEMA_VERSION) throw SerializationException("Version de format des flags non prise en charge")
        val flags = root["flags"] as? JsonObject ?: throw SerializationException("Réponse des flags inattendue")
        return flags.mapNotNull { (key, value) ->
            val flag = FeatureFlag.fromKey(key)?.takeIf { it.stage == FeatureStage.Released } ?: return@mapNotNull null
            val primitive = value as? JsonPrimitive ?: return@mapNotNull null
            val enabled = primitive.takeUnless { it.isString }?.booleanOrNull ?: return@mapNotNull null
            flag.key to enabled
        }.toMap()
    }
}
