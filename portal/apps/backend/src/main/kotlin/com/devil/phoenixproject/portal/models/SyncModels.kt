package com.devil.phoenixproject.portal.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncPushRequest(
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String? = null,
    val changes: List<SyncChange>
)

@Serializable
data class SyncChange(
    val table: String,
    val operation: String, // "upsert" or "delete"
    val rowId: String,
    val baseVersion: Int? = null,
    val payload: JsonElement
)

@Serializable
data class SyncPushResponse(
    val accepted: List<String>,
    val conflicts: List<SyncConflict>,
    val newCursor: String,
    val serverTime: Long
)

@Serializable
data class SyncConflict(
    val rowId: String,
    val table: String,
    val clientVersion: Int,
    val serverVersion: Int,
    val serverData: JsonElement
)

@Serializable
data class SyncPullRequest(
    val deviceId: String,
    val cursor: String? = null,
    val tables: List<String>? = null
)

@Serializable
data class SyncPullResponse(
    val changes: List<SyncChange>,
    val newCursor: String,
    val hasMore: Boolean
)
