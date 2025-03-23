package com.guildmaster.server.network

import com.guildmaster.server.player.Player
import com.guildmaster.server.serialization.Vector2fSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.contextual
import org.joml.Vector2f

/**
 * Protocol definitions and message handling for client-server communication.
 */
object Protocol {

    // JSON serializer
    val json = Json {
        prettyPrint = true
        serializersModule = kotlinx.serialization.modules.SerializersModule {
            contextual(Vector2fSerializer)
        }
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
    const val MSG_LOGIN_SUCCESS = "MSG_LOGIN_SUCCESS"

    // Command types
    const val CMD_CONNECT = "CONNECT"
    const val CMD_CONFIG = "CONFIG"
    const val CMD_ACTION = "ACTION"
    const val CMD_MAP = "MAP"
    const val CMD_CHAT = "CHAT"
    const val CMD_POS = "POS"
    const val CMD_LOGIN = "LOGIN"
    const val CMD_PING = "PING"
    const val CMD_UDP_REGISTER = "UDP_REG"

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
        @Contextual val position: Vector2f,
        val mapId: String
    )

    @Serializable
    data class ActionMessage(
        val action: String,
        val data: Map<String, String> = emptyMap()
    )

    @Serializable
    data class ChatMessage(val playerId: String, val playerName: String, val text: String)

    @Serializable
    data class MapChangeMessage(val mapId: String, val playerIds: List<String>)

    @Serializable
    data class LoginMessage(val player: Player)

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
            fun fromPlayer(player: Player): PlayerInfo = PlayerInfo(
                id = player.id,
                name = player.name,
                color = player.color,
                x = player.position.x,
                y = player.position.y,
                mapId = player.mapId
            )
        }
    }

    // Utility functions for encoding/decoding messages

    fun encodeConnectMessage(message: ConnectMessage): String =
        "$CMD_CONNECT ${json.encodeToString(ConnectMessage.serializer(), message)}"

    fun encodeConfigMessage(message: ConfigMessage): String =
        "$MSG_CONFIG ${json.encodeToString(ConfigMessage.serializer(), message)}"

    fun encodePositionMessage(message: PositionMessage): String =
        "$MSG_POS ${json.encodeToString(PositionMessage.serializer(), message)}"

    fun encodeActionMessage(message: ActionMessage): String =
        "$CMD_ACTION ${json.encodeToString(ActionMessage.serializer(), message)}"

    fun encodeChatMessage(message: ChatMessage): String =
        "$MSG_CHAT ${json.encodeToString(ChatMessage.serializer(), message)}"

    fun encodeMapChangeMessage(message: MapChangeMessage): String =
        "$CMD_MAP ${json.encodeToString(MapChangeMessage.serializer(), message)}"

    // Function to create player list message
    fun createPlayersListMessage(players: List<Player>): String {
        return buildString {
            append("PLAYERS ")
            append(json.encodeToString(players))
            append("\n")
        }
    }

    fun createPositionUpdateMessage(playerId: String, position: Vector2f, mapId: String): String {
        return buildString {
            append("POS_UPDATE ")
            append(json.encodeToString(PositionMessage(playerId, position, mapId)))
            append("\n")
        }
    }

    fun createActionMessage(playerId: String, action: String, data: Map<String, String> = emptyMap()): String {
        return buildString {
            append("ACTION ")
            append(json.encodeToString(ActionMessage(action, data)))
            append("\n")
        }
    }

    fun createSystemMessage(message: String): String {
        return buildString {
            append("SYSTEM ")
            append(message)
            append("\n")
        }
    }
} 