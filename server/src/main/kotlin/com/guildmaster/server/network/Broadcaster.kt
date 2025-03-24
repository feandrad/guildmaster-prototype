package com.guildmaster.server.network

import com.guildmaster.server.Logger
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager
import com.guildmaster.server.world.player.Player
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

    fun broadcastPositionUpdate(playerId: String, position: Vector2f, mapId: String, token: String) {
        sessionManager.getSessionByPlayerId(playerId).let { result ->
            when (result) {
                is Response.Success -> {
                    val session = result.data
                    val message = Protocol.positionUpdateMessage(playerId, position, mapId, token)
                    broadcastToPlayer(session.player, message)
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast position update: ${result.message}" }
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

    fun broadcastPlayersList(mapId: String) {
        sessionManager.getSessionsInMap(mapId).let { result ->
            when (result) {
                is Response.Success -> {
                    val players = result.data.map { it.player }
                    val message = Protocol.playersListMessage(players)
                    broadcastToMap(mapId, message)
                }

                is Response.Error -> {
                    Logger.warn { "Failed to broadcast players list: ${result.message}" }
                }
            }
        }
    }
} 