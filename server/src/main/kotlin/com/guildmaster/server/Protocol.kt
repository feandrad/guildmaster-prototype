package com.guildmaster.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Protocol definition for client-server communication.
 */
object Protocol {
    // Setup JSON serializer
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }
    
    // Client commands
    const val CMD_CONNECT = "CONNECT"
    const val CMD_CONFIG = "CONFIG"
    const val CMD_MAP = "MAP"
    const val CMD_CHAT = "CHAT"
    const val CMD_UDP_REGISTER = "UDP_REGISTER"
    const val CMD_POS = "POS"
    const val CMD_PING = "PING"
    const val CMD_ACTION = "ACTION"
    
    // Server messages
    const val MSG_CONFIG = "CONFIG"
    const val MSG_PLAYERS = "PLAYERS"
    const val MSG_CHAT = "CHAT"
    const val MSG_UDP_REGISTERED = "UDP_REGISTERED"
    const val MSG_POS = "POS"
    const val MSG_PONG = "PONG"
    const val MSG_ERROR = "ERROR"
    
    // Timeouts
    const val CONNECTION_TIMEOUT_MS = 30000 // 30 seconds
    
    // Constants for message types
    const val MSG_CONNECT = "CONNECT"
    const val MSG_MAP = "MAP"
    const val MSG_ACTION = "ACTION"
    const val MSG_UDP_REGISTER = "UDP_REGISTER"
    
    // Serializable message classes
    @Serializable
    data class ConnectMessage(val name: String, val color: String = "#FF0000")
    
    @Serializable
    data class ConfigMessage(val playerId: String, val color: String, val mapId: String)
    
    @Serializable
    data class PositionMessage(
        val playerId: String, 
        val x: Float, 
        val y: Float,
        val mapId: String = "default"
    )
    
    @Serializable
    data class ActionMessage(
        val playerId: String, 
        val actionType: String,
        val targetId: String = "",
        val x: Float = 0f,
        val y: Float = 0f
    )
    
    @Serializable
    data class ChatMessage(val playerId: String, val playerName: String, val text: String)
    
    @Serializable
    data class MapChangeMessage(val mapId: String, val playerIds: List<String>)
    
    @Serializable
    data class UdpRegisterMessage(val playerId: String)
    
    @Serializable
    data class PlayerInfo(val id: String, val name: String, val color: String)
    
    // Utility functions for encoding/decoding messages
    
    fun encodeConnectMessage(message: ConnectMessage): String {
        return "$CMD_CONNECT ${json.encodeToString(message)}"
    }
    
    fun encodeConfigMessage(message: ConfigMessage): String {
        return "$MSG_CONFIG ${json.encodeToString(message)}"
    }
    
    fun encodePositionMessage(message: PositionMessage): String {
        return "$MSG_POS ${json.encodeToString(message)}"
    }
    
    fun encodeActionMessage(message: ActionMessage): String {
        return "$CMD_ACTION ${json.encodeToString(message)}"
    }
    
    fun encodeChatMessage(message: ChatMessage): String {
        return "$MSG_CHAT ${json.encodeToString(message)}"
    }
    
    fun encodeMapChangeMessage(message: MapChangeMessage): String {
        return "$CMD_MAP ${json.encodeToString(message)}"
    }
    
    // Function to create player list message
    fun createPlayersListMessage(players: List<PlayerSession>): String {
        val playerInfos = players.map { PlayerInfo(it.id, it.name, it.color) }
        return "$MSG_PLAYERS ${json.encodeToString(playerInfos)}"
    }
} 