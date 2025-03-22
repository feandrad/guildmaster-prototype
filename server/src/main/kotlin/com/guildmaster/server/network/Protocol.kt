package com.guildmaster.server.network

import com.guildmaster.server.player.Player
import com.guildmaster.server.session.PlayerSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Protocol definitions and message handling for client-server communication.
 */
object Protocol {
    // JSON serializer
    val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // Message types
    const val MSG_CONNECT = "CONNECT"
    const val MSG_CONFIG = "CONFIG"
    const val MSG_POS = "POSITION"
    const val MSG_CHAT = "CHAT"
    const val MSG_PLAYERS = "PLAYERS"
    const val MSG_MAP = "MAP"
    const val MSG_UDP_REG = "UDP_REG"
    const val MSG_PONG = "PONG"
    
    // Command types
    const val CMD_CONNECT = "CONNECT"
    const val CMD_CONFIG = "CONFIG"
    const val CMD_ACTION = "ACTION"
    const val CMD_MAP = "MAP"
    const val CMD_CHAT = "CHAT"
    const val CMD_POS = "POS"
    const val CMD_PING = "PING"
    const val CMD_UDP_REGISTER = "UDP_REGISTER"
    
    // Message classes
    @Serializable
    data class ConnectMessage(val name: String, val color: String)
    
    @Serializable
    data class ConfigMessage(
        val id: String, 
        val udpPort: Int,
        val color: String? = null,
        val mapId: String? = null
    )
    
    @Serializable
    data class PositionMessage(
        val playerId: String? = null,
        val x: Float,
        val y: Float,
        val mapId: String? = null
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
    data class UdpRegisterMessage(val id: String)
    
    @Serializable
    data class PlayerInfo(
        val id: String, 
        val name: String, 
        val color: String,
        val x: Float = 0f,
        val y: Float = 0f,
        val mapId: String = "default"
    ) {
        companion object {
            fun fromPlayer(player: Player): PlayerInfo {
                return PlayerInfo(
                    id = player.id,
                    name = player.name,
                    color = player.color,
                    x = player.x,
                    y = player.y,
                    mapId = player.currentMapId
                )
            }
        }
    }
    
    // Utility functions for encoding/decoding messages
    
    fun encodeConnectMessage(message: ConnectMessage): String {
        return "$CMD_CONNECT ${json.encodeToString(ConnectMessage.serializer(), message)}"
    }
    
    fun encodeConfigMessage(message: ConfigMessage): String {
        return "$MSG_CONFIG ${json.encodeToString(ConfigMessage.serializer(), message)}"
    }
    
    fun encodePositionMessage(message: PositionMessage): String {
        return "$MSG_POS ${json.encodeToString(PositionMessage.serializer(), message)}"
    }
    
    fun encodeActionMessage(message: ActionMessage): String {
        return "$CMD_ACTION ${json.encodeToString(ActionMessage.serializer(), message)}"
    }
    
    fun encodeChatMessage(message: ChatMessage): String {
        return "$MSG_CHAT ${json.encodeToString(ChatMessage.serializer(), message)}"
    }
    
    fun encodeMapChangeMessage(message: MapChangeMessage): String {
        return "$CMD_MAP ${json.encodeToString(MapChangeMessage.serializer(), message)}"
    }
    
    // Function to create player list message
    fun createPlayersListMessage(players: List<PlayerSession>): String {
        val playerInfos = players.map { PlayerInfo.fromPlayer(it.player) }
        return "$MSG_PLAYERS ${json.encodeToString(playerInfos)}"
    }
} 