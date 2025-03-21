package com.guildmaster.server

import mu.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    
    init {
        // Start task to clean up inactive sessions
        scheduler.scheduleAtFixedRate(this::cleanupInactiveSessions, 10, 10, TimeUnit.SECONDS)
    }
    
    /**
     * Create a new session for a player.
     */
    fun createSession(playerName: String, tcpAddress: InetSocketAddress): PlayerSession {
        val session = PlayerSession(name = playerName, tcpAddress = tcpAddress)
        
        sessions[session.id] = session
        tcpAddressToSessionId[tcpAddress] = session.id
        
        // Add the session to the default map
        addSessionToMap(session.id, session.currentMapId)
        
        logger.info { "New session created: ${session.id} for player: ${session.name}" }
        return session
    }
    
    /**
     * Update the UDP address for an existing session.
     */
    fun updateUdpAddress(sessionId: String, udpAddress: InetSocketAddress) {
        sessions[sessionId]?.let { session ->
            session.udpAddress = udpAddress
            udpAddressToSessionId[udpAddress] = sessionId
            logger.debug { "UDP address updated for session ${session.id}: $udpAddress" }
        }
    }
    
    /**
     * Update a player's position.
     */
    fun updatePosition(sessionId: String, x: Float, y: Float) {
        sessions[sessionId]?.let { session ->
            session.updatePosition(x, y)
            logger.debug { "Position updated for ${session.name}: ($x, $y)" }
        }
    }
    
    /**
     * Update a player's map.
     */
    fun updateMap(sessionId: String, mapId: String) {
        sessions[sessionId]?.let { session ->
            // Remove from current map
            removeSessionFromMap(sessionId, session.currentMapId)
            
            // Update the map
            session.currentMapId = mapId
            
            // Add to new map
            addSessionToMap(sessionId, mapId)
            
            logger.info { "Player ${session.name} changed to map: $mapId" }
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
    fun getSession(sessionId: String): PlayerSession? {
        return sessions[sessionId]
    }
    
    /**
     * Get a session by TCP address.
     */
    fun getSessionByTcpAddress(address: InetSocketAddress): PlayerSession? {
        val sessionId = tcpAddressToSessionId[address] ?: return null
        return sessions[sessionId]
    }
    
    /**
     * Get a session by UDP address.
     */
    fun getSessionByUdpAddress(address: InetSocketAddress): PlayerSession? {
        val sessionId = udpAddressToSessionId[address] ?: return null
        return sessions[sessionId]
    }
    
    /**
     * Get all active sessions.
     */
    fun getAllSessions(): List<PlayerSession> {
        return sessions.values.toList()
    }
    
    /**
     * Get all sessions on a specific map.
     */
    fun getSessionsInMap(mapId: String): List<PlayerSession> {
        val sessionIds = mapToSessions[mapId] ?: return emptyList()
        return sessionIds.mapNotNull { sessions[it] }
    }
    
    /**
     * Remove a session.
     */
    fun removeSession(sessionId: String) {
        sessions[sessionId]?.let { session ->
            // Remove from maps
            removeSessionFromMap(sessionId, session.currentMapId)
            
            // Remove from address mappings
            session.tcpAddress?.let { tcpAddressToSessionId.remove(it) }
            session.udpAddress?.let { udpAddressToSessionId.remove(it) }
            
            // Remove from sessions list
            sessions.remove(sessionId)
            
            logger.info { "Session removed: ${session.id} (${session.name})" }
        }
    }
    
    /**
     * Clean up inactive sessions.
     */
    private fun cleanupInactiveSessions() {
        val inactiveSessions = sessions.values.filter { it.isInactive(sessionTimeoutMs) }
        
        if (inactiveSessions.isNotEmpty()) {
            logger.info { "Removing ${inactiveSessions.size} inactive sessions" }
            
            inactiveSessions.forEach { session ->
                removeSession(session.id)
            }
        }
    }
    
    /**
     * Format the player list in the current system format (just names).
     */
    fun getPlayersList(): List<Pair<String, String>> {
        return sessions.values.map { it.id to it.name }
    }
    
    /**
     * Shut down the session manager.
     */
    fun shutdown() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
        
        sessions.clear()
        tcpAddressToSessionId.clear()
        udpAddressToSessionId.clear()
        mapToSessions.clear()
    }
} 