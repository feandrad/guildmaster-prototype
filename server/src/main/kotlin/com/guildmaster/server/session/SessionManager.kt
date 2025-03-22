package com.guildmaster.server.session

import mu.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.util.UUID
import com.guildmaster.server.player.Player

private const val INACTIVITY_TIMEOUT_MS = 30_000L
private const val INACTIVITY_CHECK_INTERVAL_S = 10L
private const val SCHEDULER_SHUTDOWN_TIMEOUT_S = 5L

/**
 * Thread-safe manager for player sessions with automatic cleanup of inactive sessions.
 * Handles session lifecycle, network address mappings, and map-based organization.
 */
class SessionManager {
    private val logger = KotlinLogging.logger {}
    
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private val tcpAddressToSessionId = ConcurrentHashMap<InetSocketAddress, String>()
    private val udpAddressToSessionId = ConcurrentHashMap<InetSocketAddress, String>()
    private val mapToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    
    private val lock = ReentrantLock()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "session-cleanup-thread").apply { isDaemon = true }
    }
    
    init {
        startInactivityMonitor()
    }
    
    fun createSession(player: Player, tcpAddress: InetSocketAddress): Response<PlayerSession> {
        return try {
            val session = PlayerSession(player, tcpAddress)
            sessions[player.id] = session
            tcpAddressToSessionId[tcpAddress] = player.id
            Response.Success(session)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create session for player ${player.id}" }
            Response.Error("Failed to create session: ${e.message}")
        }
    }
    
    fun getSessionById(playerId: String): Response<PlayerSession> {
        return sessions[playerId]?.let { Response.Success(it) }
            ?: Response.Error("Session not found for player ID: $playerId")
    }
    
    fun getSessionByTcpAddress(address: InetSocketAddress): Response<PlayerSession> {
        return tcpAddressToSessionId[address]?.let { playerId ->
            sessions[playerId]?.let { Response.Success(it) }
                ?: Response.Error("Session not found for TCP address: $address")
        } ?: Response.Error("No session found for TCP address: $address")
    }
    
    fun getSessionByUdpAddress(address: InetSocketAddress): Response<PlayerSession> {
        return udpAddressToSessionId[address]?.let { playerId ->
            sessions[playerId]?.let { Response.Success(it) }
                ?: Response.Error("Session not found for UDP address: $address")
        } ?: Response.Error("No session found for UDP address: $address")
    }
    
    fun updateUdpAddress(playerId: String, udpAddress: InetSocketAddress): Response<Unit> {
        return try {
            val session = sessions[playerId] ?: return Response.Error("Session not found for player ID: $playerId")
            session.udpAddress = udpAddress
            udpAddressToSessionId[udpAddress] = playerId
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update UDP address for player $playerId" }
            Response.Error("Failed to update UDP address: ${e.message}")
        }
    }
    
    fun getSessionsInMap(mapId: String): Response<List<PlayerSession>> {
        if (mapId.isBlank()) {
            return Response.Error("Map ID cannot be blank")
        }
        
        return try {
            Response.Success(
                mapToSessions[mapId]?.mapNotNull { sessions[it] } ?: emptyList()
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get sessions in map $mapId" }
            Response.Error("Failed to get sessions in map: ${e.message}")
        }
    }
    
    fun getAllPlayers(): Response<List<Player>> {
        return try {
            Response.Success(sessions.values.map { it.player })
        } catch (e: Exception) {
            logger.error(e) { "Failed to get all players" }
            Response.Error("Failed to get all players: ${e.message}")
        }
    }
    
    fun removeSession(playerId: String): Response<Unit> {
        return try {
            val session = sessions[playerId] ?: return Response.Error("Session not found for player ID: $playerId")
            session.tcpAddress?.let { tcpAddressToSessionId.remove(it) }
            session.udpAddress?.let { udpAddressToSessionId.remove(it) }
            sessions.remove(playerId)
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove session for player $playerId" }
            Response.Error("Failed to remove session: ${e.message}")
        }
    }
    
    fun updateMap(targetSessionId: String, newMapId: String): Response<Unit> {
        val session = sessions[targetSessionId] ?: return Response.Error("Session not found")
        
        return try {
            lock.withLock {
                removeSessionFromMap(targetSessionId, session.player.currentMapId)
                session.player.currentMapId = newMapId
                addSessionToMap(targetSessionId, newMapId)
            }
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update map for session: $targetSessionId" }
            Response.Error("Failed to update map: ${e.message}")
        }
    }
    
    fun getAllSessions(): Response<List<PlayerSession>> =
        Response.Success(sessions.values.toList())
    
    fun getConnectedPlayers(): Response<List<Player>> =
        Response.Success(sessions.values.map { it.player })
    
    fun getPlayersList(): Response<List<Pair<String, String>>> =
        Response.Success(sessions.values.map { it.player.id to it.player.name })
    
    fun associateTcpAddress(tcpAddress: InetSocketAddress, targetSessionId: String): Response<Unit> {
        if (!sessions.containsKey(targetSessionId)) {
            return Response.Error("Session not found")
        }

        return try {
            lock.withLock {
                tcpAddressToSessionId[tcpAddress] = targetSessionId
            }
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to associate TCP address for session: $targetSessionId" }
            Response.Error("Failed to associate TCP address: ${e.message}")
        }
    }
    
    private fun generatePlayerId(): String = UUID.randomUUID().toString()
    
    private fun addSessionToMap(targetSessionId: String, targetMapId: String) {
        mapToSessions.computeIfAbsent(targetMapId) { mutableSetOf() }.add(targetSessionId)
    }
    
    private fun removeSessionFromMap(targetSessionId: String, sourceMapId: String) {
        mapToSessions[sourceMapId]?.remove(targetSessionId)
        if (mapToSessions[sourceMapId]?.isEmpty() == true) {
            mapToSessions.remove(sourceMapId)
        }
    }
    
    private fun cleanupSessionResources(session: PlayerSession) {
        removeSessionFromMap(session.player.id, session.player.currentMapId)
        session.tcpAddress?.let { tcpAddressToSessionId.remove(it) }
        session.udpAddress?.let { udpAddressToSessionId.remove(it) }
    }
    
    private fun startInactivityMonitor() {
        scheduler.scheduleAtFixedRate(
            ::removeInactiveSessions,
            INACTIVITY_CHECK_INTERVAL_S,
            INACTIVITY_CHECK_INTERVAL_S,
            TimeUnit.SECONDS
        )
    }
    
    private fun removeInactiveSessions() {
        sessions.values.forEach { session ->
            if (session.isInactive(INACTIVITY_TIMEOUT_MS)) {
                removeSession(session.player.id)
            }
        }
    }
    
    fun shutdown() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(SCHEDULER_SHUTDOWN_TIMEOUT_S, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
} 