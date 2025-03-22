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
    
    private val inactivityTimeoutMillis = 30_000L
    private val inactivityCheckIntervalSeconds = 10L
    private val schedulerShutdownTimeoutSeconds = 5L
    
    init {
        startInactivityMonitor()
    }
    
    fun createSession(displayName: String, displayColor: String): Response<PlayerSession> {
        if (displayName.isBlank()) {
            return Response.Error("Player name cannot be blank")
        }
        
        if (displayColor.isBlank()) {
            return Response.Error("Player color cannot be blank")
        }
        
        return try {
            val id = generatePlayerId()
            val session = PlayerSession(id, displayName, displayColor)
            
            lock.withLock {
                sessions[id] = session
                addSessionToMap(session.player.id, session.player.currentMapId)
            }
            
            logger.info { "New session created: $id for player: $displayName with color: $displayColor" }
            Response.Success(session)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create session for player: $displayName" }
            Response.Error("Failed to create session", e)
        }
    }
    
    fun removeSession(targetSessionId: String): Response<Boolean> {
        val session = sessions[targetSessionId] ?: return Response.Error("Session not found")
        
        return try {
            lock.withLock {
                cleanupSessionResources(session)
                sessions.remove(targetSessionId)
            }
            
            logger.info { "Session removed: $targetSessionId (${session.player.name})" }
            Response.Success(true)
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove session: $targetSessionId" }
            Response.Error("Failed to remove session", e)
        }
    }
    
    fun updateUdpAddress(targetSessionId: String, newUdpAddress: InetSocketAddress): Response<Unit> {
        val session = sessions[targetSessionId] ?: return Response.Error("Session not found")
        
        return try {
            lock.withLock {
                session.udpAddress?.let { oldAddress ->
                    udpAddressToSessionId.remove(oldAddress)
                }
                
                session.udpAddress = newUdpAddress
                udpAddressToSessionId[newUdpAddress] = targetSessionId
            }
            
            logger.debug { "UDP address updated for session ${session.player.id}: $newUdpAddress" }
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update UDP address for session: $targetSessionId" }
            Response.Error("Failed to update UDP address", e)
        }
    }
    
    fun updatePosition(targetSessionId: String, newXPosition: Float, newYPosition: Float): Response<Unit> {
        val session = sessions[targetSessionId] ?: return Response.Error("Session not found")
        
        return try {
            session.updatePosition(newXPosition, newYPosition)
            logger.debug { "Position updated for ${session.player.name}: ($newXPosition, $newYPosition)" }
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update position for session: $targetSessionId" }
            Response.Error("Failed to update position", e)
        }
    }
    
    fun updateMap(targetSessionId: String, destinationMapId: String): Response<Unit> {
        if (destinationMapId.isBlank()) {
            return Response.Error("Map ID cannot be blank")
        }
        
        val session = sessions[targetSessionId] ?: return Response.Error("Session not found")
        
        return try {
            lock.withLock {
                val oldMapId = session.player.currentMapId
                
                removeSessionFromMap(targetSessionId, oldMapId)
                session.player.currentMapId = destinationMapId
                addSessionToMap(targetSessionId, destinationMapId)
                
                logger.info { "Player ${session.player.name} changed from map $oldMapId to $destinationMapId" }
            }
            Response.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update map for session: $targetSessionId" }
            Response.Error("Failed to update map", e)
        }
    }
    
    fun getSessionById(targetSessionId: String): Response<PlayerSession> =
        sessions[targetSessionId]?.let { Response.Success(it) }
            ?: Response.Error("Session not found")
    
    fun getSessionByTcpAddress(tcpAddress: InetSocketAddress): Response<PlayerSession> =
        tcpAddressToSessionId[tcpAddress]?.let { sessionId ->
            sessions[sessionId]?.let { Response.Success(it) }
        } ?: Response.Error("Session not found for TCP address: $tcpAddress")
    
    fun getSessionByUdpAddress(udpAddress: InetSocketAddress): Response<PlayerSession> =
        udpAddressToSessionId[udpAddress]?.let { sessionId ->
            sessions[sessionId]?.let { Response.Success(it) }
        } ?: Response.Error("Session not found for UDP address: $udpAddress")
    
    fun getSessionsInMap(targetMapId: String): Response<List<PlayerSession>> {
        if (targetMapId.isBlank()) {
            return Response.Error("Map ID cannot be blank")
        }
        
        return Response.Success(
            mapToSessions[targetMapId]?.mapNotNull { sessions[it] } ?: emptyList()
        )
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
            Response.Error("Failed to associate TCP address", e)
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
            inactivityCheckIntervalSeconds,
            inactivityCheckIntervalSeconds,
            TimeUnit.SECONDS
        )
    }
    
    private fun removeInactiveSessions() {
        when (val sessionsResult = getAllSessions()) {
            is Response.Error -> {
                logger.error { "Failed to get sessions for cleanup: ${sessionsResult.message}" }
                return
            }
            is Response.Success -> {
                val inactiveSessions = sessionsResult.data.filter { it.isInactive(inactivityTimeoutMillis) }
                inactiveSessions.forEach { session ->
                    logger.info { "Removing inactive session: ${session.player.id} (${session.player.name})" }
                    when (val result = removeSession(session.player.id)) {
                        is Response.Error -> logger.error { "Failed to remove inactive session: ${result.message}" }
                        is Response.Success -> {} // Successfully removed
                    }
                }
            }
        }
    }
    
    fun shutdown() {
        stopScheduler()
        clearResources()
    }
    
    private fun stopScheduler() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(schedulerShutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn { "Session manager scheduler did not terminate in time" }
            }
        } catch (e: InterruptedException) {
            logger.error(e) { "Error shutting down session manager" }
            Thread.currentThread().interrupt()
        }
    }
    
    private fun clearResources() {
        lock.withLock {
            sessions.clear()
            tcpAddressToSessionId.clear()
            udpAddressToSessionId.clear()
            mapToSessions.clear()
        }
    }
} 