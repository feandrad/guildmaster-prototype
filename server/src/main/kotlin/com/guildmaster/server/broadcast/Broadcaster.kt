package com.guildmaster.server.broadcast

import com.guildmaster.server.Logger
import com.guildmaster.server.network.Protocol
import com.guildmaster.server.network.TcpService
import com.guildmaster.server.player.Player
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager
import org.joml.Vector2f


class Broadcaster(
    private val sessionManager: SessionManager,
    private val tcpService: TcpService
) {
    fun broadcastToAll(message: String) {
        sessionManager.getAllSessions().let { result ->
            when (result) {
                is Response.Success -> {
                    result.data.forEach { session ->
                        session.tcpAddress?.let { address ->
                            tcpService.sendMessage(address, message)
                        }
                    }
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast to all: ${result.message}" }
                }
            }
        }
    }

    fun broadcastToMap(mapId: String, message: String) {
        sessionManager.getSessionsInMap(mapId).let { result ->
            when (result) {
                is Response.Success -> {
                    result.data.forEach { session ->
                        session.tcpAddress?.let { address ->
                            tcpService.sendMessage(address, message)
                        }
                    }
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast to map $mapId: ${result.message}" }
                }
            }
        }
    }

    fun broadcastPositionUpdate(playerId: String, position: Vector2f, mapId: String) {
        sessionManager.getSessionByPlayerId(playerId).let { result ->
            when (result) {
                is Response.Success -> {
                    val session = result.data
                    val message = Protocol.createPositionUpdateMessage(playerId, position, mapId)
                    broadcastToPlayer(session.player, message)
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast position update: ${result.message}" }
                }
            }
        }
    }

    fun broadcastAction(playerId: String, action: String, data: Map<String, String> = emptyMap()) {
        sessionManager.getSessionByPlayerId(playerId).let { result ->
            when (result) {
                is Response.Success -> {
                    val session = result.data
                    val message = Protocol.createActionMessage(playerId, action, data)
                    broadcastToPlayer(session.player, message)
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast action: ${result.message}" }
                }
            }
        }
    }

    fun broadcastToPlayer(player: Player, message: String) {
        sessionManager.getSessionByPlayerId(player.id).let { result ->
            when (result) {
                is Response.Success -> {
                    val session = result.data
                    session.tcpAddress?.let { address ->
                        tcpService.sendMessage(address, message)
                    } ?: Logger.warn { "Cannot broadcast to player ${player.id}: No TCP address" }
                }
                is Response.Error -> {
                    Logger.warn { "Failed to broadcast to player ${player.id}: ${result.message}" }
                }
            }
        }
    }

    fun broadcastSystemMessage(message: String) {
        broadcastToAll(Protocol.createSystemMessage(message))
    }

    fun broadcastPlayersList(mapId: String) {
        sessionManager.getSessionsInMap(mapId).let { result ->
            when (result) {
                is Response.Success -> {
                    val players = result.data.map { it.player }
                    val message = Protocol.createPlayersListMessage(players)
                    broadcastToMap(mapId, message)
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast players list: ${result.message}" }
                }
            }
        }
    }
} 