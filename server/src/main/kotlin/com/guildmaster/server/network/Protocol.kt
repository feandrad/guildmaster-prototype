package com.guildmaster.server.network

import com.guildmaster.server.network.Protocol.json
import com.guildmaster.server.serialization.Vector2fSerializer
import com.guildmaster.server.session.PlayerSession
import com.guildmaster.server.world.player.Player
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
    const val MSG_POS = "POSITION"
    const val MSG_PONG = "PONG"
    const val MSG_CHAT = "CHAT"
    const val MSG_PLAYERS = "PLAYERS"
    const val MSG_UDP_REG = "UDP_REG"

    // Command types
    const val CMD_CONNECT = "CONNECT"
    const val CMD_POS = "POSITION"
    const val CMD_PING = "PING"
    const val CMD_CHAT = "CHAT"
    const val CMD_UDP_REGISTER = "UDP_REG"
    const val CMD_ACTION = "ACTION"

    @Serializable
    data class ConnectMessage(
        val user: String,
        val color: String,
    )

    @Serializable
    data class PlayerSessionMessage(
        val player: Player,
        val token: String,
        val udpPort: Int?,
    )

    @Serializable
    data class PositionMessage(
        val playerId: String? = null,
        @Contextual val position: Vector2f,
        val mapId: String,
        val token: String
    )

    fun connectMessage(session: PlayerSession): String =
        MSG_CONNECT.toMessage(PlayerSessionMessage(session.player, session.token, session.udpAddress?.port))

    fun positionUpdateMessage(playerId: String, position: Vector2f, mapId: String, token: String) =
        CMD_POS.toMessage(PositionMessage(playerId, position, mapId, token))

    fun playersListMessage(players: List<Player>): String = MSG_PLAYERS.toMessage(players)

    fun pongMessage() = MSG_PONG.toMessage()
}

fun String.toMessage(): String = buildString {
    append(this@toMessage)
    append("\n")
}

inline fun <reified T> String.toMessage(arg: T): String = buildString {
    append(this@toMessage)
    append(" ")
    append(json.encodeToString(arg))
    append("\n")
}