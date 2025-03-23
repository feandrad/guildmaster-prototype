package com.guildmaster.server.session

import com.guildmaster.server.Logger
import com.guildmaster.server.player.Player
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class SessionService {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private val tcpToSession = ConcurrentHashMap<InetSocketAddress, String>()
    private val udpToSession = ConcurrentHashMap<InetSocketAddress, String>()
    
    fun createSession(player: Player): Response<PlayerSession> {
        return try {
            val session = PlayerSession(player)
            sessions[player.id] = session
            Response.Success(session)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to create session for player ${player.id}" }
            Response.Error("Failed to create session")
        }
    }
    
    fun getSession(id: String): Response<PlayerSession> {
        return sessions[id]?.let { Response.Success(it) }
            ?: Response.Error("Session not found")
    }
    
    fun getSessionByTcpAddress(address: InetSocketAddress): Response<PlayerSession> {
        return tcpToSession[address]?.let { getSession(it) }
            ?: Response.Error("No session found for TCP address")
    }
    
    fun getSessionByUdpAddress(address: InetSocketAddress): Response<PlayerSession> {
        return udpToSession[address]?.let { getSession(it) }
            ?: Response.Error("No session found for UDP address")
    }
    
    fun getSessionsInMap(mapId: String): Response<List<PlayerSession>> {
        return try {
            val mapSessions = sessions.values.filter { it.player.mapId == mapId }
            Response.Success(mapSessions)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to get sessions in map $mapId" }
            Response.Error("Failed to get sessions in map")
        }
    }
    
    fun updateTcpAddress(id: String, address: InetSocketAddress): Response<Unit> {
        return try {
            tcpToSession[address] = id
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to update TCP address for session $id" }
            Response.Error("Failed to update TCP address")
        }
    }
    
    fun updateUdpAddress(id: String, address: InetSocketAddress): Response<Unit> {
        return try {
            udpToSession[address] = id
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to update UDP address for session $id" }
            Response.Error("Failed to update UDP address")
        }
    }
    
    fun updateMap(id: String, mapId: String): Response<Unit> {
        return try {
            sessions[id]?.player?.mapId = mapId
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to update map for session $id" }
            Response.Error("Failed to update map")
        }
    }
    
    fun removeSession(id: String): Response<Unit> {
        return try {
            val session = sessions[id]
            if (session != null) {
                session.tcpAddress?.let { tcpToSession.remove(it) }
                session.udpAddress?.let { udpToSession.remove(it) }
                sessions.remove(id)
            }
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to remove session $id" }
            Response.Error("Failed to remove session")
        }
    }
    
    fun removeSessionByTcpAddress(address: InetSocketAddress): Response<Unit> {
        return try {
            tcpToSession[address]?.let { removeSession(it) }
                ?: Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to remove session by TCP address" }
            Response.Error("Failed to remove session")
        }
    }
    
    fun removeSessionByUdpAddress(address: InetSocketAddress): Response<Unit> {
        return try {
            udpToSession[address]?.let { removeSession(it) }
                ?: Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to remove session by UDP address" }
            Response.Error("Failed to remove session")
        }
    }
    
    fun getAllSessions(): Response<List<PlayerSession>> {
        return try {
            Response.Success(sessions.values.toList())
        } catch (e: Exception) {
            Logger.error(e) { "Failed to get all sessions" }
            Response.Error("Failed to get all sessions")
        }
    }
} 