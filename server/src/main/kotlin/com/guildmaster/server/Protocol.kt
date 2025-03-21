package com.guildmaster.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Defines the Guild Master communication protocol.
 *
 * TCP Protocol: Used for:
 * - Initial handshake (CONNECT)
 * - Player list updates (PLAYERS)
 * - Session configuration (CONFIG)
 * - Map changes (MAP)
 * - Chat messages (CHAT)
 *
 * UDP Protocol: Used for:
 * - Position updates (POS)
 * - Quick actions (ACTION)
 * - Heartbeat (PING/PONG)
 */
object Protocol {
    
    // JSON format for serialization/deserialization
    val json = Json { 
        prettyPrint = false 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Constants for message types
    const val MSG_CONNECT = "CONNECT"
    const val MSG_PLAYERS = "PLAYERS"
    const val MSG_CONFIG = "CONFIG"
    const val MSG_MAP = "MAP"
    const val MSG_CHAT = "CHAT"
    const val MSG_POS = "POS"
    const val MSG_ACTION = "ACTION"
    const val MSG_PING = "PING"
    const val MSG_PONG = "PONG"
    
    // Serializable message classes
    
    @Serializable
    data class ConnectMessage(val playerId: String, val playerName: String)
    
    @Serializable
    data class ConfigMessage(val playerId: String, val color: String, val mapId: String)
    
    @Serializable
    data class PositionMessage(
        val playerId: String, 
        val x: Float, 
        val y: Float, 
        val timestamp: Long = System.currentTimeMillis()
    )
    
    @Serializable
    data class ActionMessage(
        val playerId: String, 
        val actionType: String, 
        val x: Float? = null, 
        val y: Float? = null
    )
    
    @Serializable
    data class ChatMessage(val playerId: String, val playerName: String, val text: String)
    
    @Serializable
    data class MapChangeMessage(val mapId: String, val playerIds: List<String>)
    
    // Utility functions for encoding/decoding messages
    
    fun encodeConnectMessage(message: ConnectMessage): String {
        return "$MSG_CONNECT ${json.encodeToString(message)}"
    }
    
    fun encodeConfigMessage(message: ConfigMessage): String {
        return "$MSG_CONFIG ${json.encodeToString(message)}"
    }
    
    fun encodePositionMessage(message: PositionMessage): String {
        return "$MSG_POS ${json.encodeToString(message)}"
    }
    
    fun encodeActionMessage(message: ActionMessage): String {
        return "$MSG_ACTION ${json.encodeToString(message)}"
    }
    
    fun encodeChatMessage(message: ChatMessage): String {
        return "$MSG_CHAT ${json.encodeToString(message)}"
    }
    
    fun encodeMapChangeMessage(message: MapChangeMessage): String {
        return "$MSG_MAP ${json.encodeToString(message)}"
    }
    
    // Function to create player list message (specific to current format)
    fun createPlayersListMessage(players: List<Pair<String, String>>): String {
        val playerNames = players.map { it.second }.joinToString(",")
        return "$MSG_PLAYERS $playerNames"
    }
} 