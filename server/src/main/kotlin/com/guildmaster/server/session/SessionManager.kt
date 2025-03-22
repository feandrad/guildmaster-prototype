package com.guildmaster.server.session

import mu.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.util.UUID

/**
 * Manages all connected player sessions on the server.
 * Responsible for creating, updating, and removing sessions.
 */
class SessionManager {
    private val logger = KotlinLogging.logger {}
    
    // Stores all active sessions indexed by ID
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    
    // Mapping of TCP addresses to session IDs
    private val tcpAddressToSessionId = ConcurrentHashMap<InetSocketAddress, String>()
    
    // Mapping of UDP addresses to session IDs
    private val udpAddressToSessionId = ConcurrentHashMap<InetSocketAddress, String>()
    
    // Mapping of sessions by map
    private val mapToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    
    // Scheduler for background tasks
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    
    // Timeout for inactivity (30 seconds)
    private val sessionTimeoutMs = 30_000L
    
    private val lock = ReentrantLock()
    
    init {
        // Start task to clean up inactive sessions
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 10, 10, TimeUnit.SECONDS)
    }
    
    /**
     * Creates a new player session.
     */
    fun createSession(playerName: String, playerColor: String): PlayerSession {
        val id = generatePlayerId()
        val session = PlayerSession(id, playerName, playerColor)
        
        // Store the session
        sessions[id] = session
        
        // Add the session to the default map
        addSessionToMap(session.player.id, session.player.currentMapId)
        
        logger.info { "New session created: $id for player: $playerName with color: $playerColor" }
        
        return session
    }
    
    /**
     * Generate a unique player ID
     */
    private fun generatePlayerId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Update the UDP address for an existing session.
     */
    fun updateUdpAddress(sessionId: String, udpAddress: InetSocketAddress) {
        sessions[sessionId]?.let { session ->
            session.udpAddress = udpAddress
            udpAddressToSessionId[udpAddress] = sessionId
            logger.debug { "UDP address updated for session ${session.player.id}: $udpAddress" }
        }
    }
    
    /**
     * Update a player's position.
     */
    fun updatePosition(sessionId: String, x: Float, y: Float) {
        sessions[sessionId]?.let { session ->
            session.updatePosition(x, y)
            logger.debug { "Position updated for ${session.player.name}: ($x, $y)" }
        }
    }
    
    /**
     * Update a player's map.
     */
    fun updateMap(sessionId: String, mapId: String) {
        sessions[sessionId]?.let { session ->
            // Remove from current map
            removeSessionFromMap(sessionId, session.player.currentMapId)
            
            // Update the map
            session.player.currentMapId = mapId
            
            // Add to new map
            addSessionToMap(sessionId, mapId)
            
            logger.info { "Player ${session.player.name} changed to map: $mapId" }
        }
    }
    
    /**
     * Add a session to a map.
     */
    private fun addSessionToMap(sessionId: String, mapId: String) {
        mapToSessions.computeIfAbsent(mapId) { mutableSetOf() }.add(sessionId)
    }
    
    /**
     * Remove a session from a map.
     */
    private fun removeSessionFromMap(sessionId: String, mapId: String) {
        mapToSessions[mapId]?.remove(sessionId)
        
        // Remove the map set if it's empty
        if (mapToSessions[mapId]?.isEmpty() == true) {
            mapToSessions.remove(mapId)
        }
    }
    
    /**
     * Get a session by ID.
     */
    fun getSessionById(sessionId: String): PlayerSession? {
        return sessions[sessionId]
    }
    
    /**
     * Get a session by TCP address
     */
    fun getSessionByTcpAddress(address: InetSocketAddress): PlayerSession? {
        val sessionId = tcpAddressToSessionId[address] ?: return null
        return sessions[sessionId]
    }
    
    /**
     * Get a session by UDP address
     */
    fun getSessionByUdpAddress(address: InetSocketAddress): PlayerSession? {
        val sessionId = udpAddressToSessionId[address] ?: return null
        return sessions[sessionId]
    }
    
    /**
     * Get all sessions in a map.
     */
    fun getSessionsInMap(mapId: String): List<PlayerSession> {
        return mapToSessions[mapId]?.mapNotNull { sessions[it] } ?: emptyList()
    }
    
    /**
     * Get all active sessions.
     */
    fun getAllSessions(): List<PlayerSession> {
        return sessions.values.toList()
    }
    
    /**
     * Remove a session.
     */
    fun removeSession(sessionId: String) {
        sessions[sessionId]?.let { session ->
            // Remove from map
            removeSessionFromMap(sessionId, session.player.currentMapId)
            
            // Remove TCP address mapping
            session.tcpAddress?.let { tcpAddressToSessionId.remove(it) }
            
            // Remove UDP address mapping
            session.udpAddress?.let { udpAddressToSessionId.remove(it) }
            
            // Remove session
            sessions.remove(sessionId)
            
            logger.info { "Session removed: $sessionId (${session.player.name})" }
        }
    }
    
    /**
     * Clean up inactive sessions.
     */
    private fun cleanupInactiveSessions() {
        val inactiveSessions = sessions.values.filter { it.isInactive(sessionTimeoutMs) }
        inactiveSessions.forEach { session ->
            logger.info { "Removing inactive session: ${session.player.id} (${session.player.name})" }
            removeSession(session.player.id)
        }
    }
    
    /**
     * Format the player list in the current system format (just names).
     */
    fun getPlayersList(): List<Pair<String, String>> {
        return sessions.values.map { it.player.id to it.player.name }
    }
    
    /**
     * Shutdown the session manager.
     */
    fun shutdown() {
        scheduler.shutdown()
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            logger.error { "Error shutting down session manager: ${e.message}" }
        }
        
        sessions.clear()
        tcpAddressToSessionId.clear()
        udpAddressToSessionId.clear()
        mapToSessions.clear()
    }
    
    /**
     * Associate a TCP address with a session ID.
     */
    fun associateTcpAddress(address: InetSocketAddress, sessionId: String) {
        lock.withLock {
            tcpAddressToSessionId[address] = sessionId
        }
    }
} 