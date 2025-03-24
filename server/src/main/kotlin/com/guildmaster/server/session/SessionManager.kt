package com.guildmaster.server.session

import com.guildmaster.server.Logger
import com.guildmaster.server.world.player.Player
import org.joml.Vector2f
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val INACTIVITY_TIMEOUT_MS = 30_000L
private const val INACTIVITY_CHECK_INTERVAL_S = 10L
private const val SCHEDULER_SHUTDOWN_TIMEOUT_S = 5L

class SessionManager {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private val tcpAddressToSessionId = ConcurrentHashMap<InetSocketAddress, String>()
    private val udpAddressToSessionId = ConcurrentHashMap<InetSocketAddress, String>()
    private val mapToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val tokenToSessionId = ConcurrentHashMap<String, String>()
    private val lock = ReentrantLock()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "session-cleanup-thread").apply { isDaemon = true }
    }

    init {
        startInactivityMonitor()
    }

    fun createSession(user: String, color: String, tcpAddress: InetSocketAddress): Response<PlayerSession> {
        return try {
            val playerId = UUID.randomUUID().toString()
            val player = Player(
                id = playerId,
                name = user,
                color = color,
                position = Vector2f(0f, 0f),
                mapId = "default"
            )
            val session = PlayerSession(player, tcpAddress)
            sessions[player.id] = session
            tcpAddressToSessionId[tcpAddress] = player.id
            tokenToSessionId[session.token] = player.id
            addSessionToMap(player.id, player.mapId)
            Response.Success(session)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to create session for user $user" }
            Response.Error("Failed to create session: ${e.message}")
        }
    }

    fun getSessionByPlayerId(playerId: String): Response<PlayerSession> {
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
            val session = sessions[playerId]
                ?: return Response.Error("Session not found for player ID: $playerId")
            session.udpAddress = udpAddress
            udpAddressToSessionId[udpAddress] = playerId
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to update UDP address for player $playerId" }
            Response.Error("Failed to update UDP address: ${e.message}")
        }
    }

    fun updatePosition(playerId: String, newPos: Vector2f): Response<Unit> {
        return try {
            val session = sessions[playerId]
                ?: return Response.Error("Session not found for player ID: $playerId")
            session.player.position.set(newPos)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to update position for player $playerId" }
            Response.Error("Failed to update position: ${e.message}")
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
            Logger.error(e) { "Failed to get sessions in map $mapId" }
            Response.Error("Failed to get sessions in map: ${e.message}")
        }
    }

    fun getAllPlayers(): Response<List<Player>> {
        return try {
            Response.Success(sessions.values.map { it.player })
        } catch (e: Exception) {
            Logger.error(e) { "Failed to get all players" }
            Response.Error("Failed to get all players: ${e.message}")
        }
    }

    fun removeSession(playerId: String): Response<Unit> {
        return try {
            val session = sessions[playerId]
                ?: return Response.Error("Session not found for player ID: $playerId")
            session.tcpAddress?.let { tcpAddressToSessionId.remove(it) }
            session.udpAddress?.let { udpAddressToSessionId.remove(it) }
            cleanupSessionResources(session)
            sessions.remove(playerId)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to remove session for player $playerId" }
            Response.Error("Failed to remove session: ${e.message}")
        }
    }

    fun updateMap(targetSessionId: String, newMapId: String): Response<Unit> {
        val session = sessions[targetSessionId]
            ?: return Response.Error("Session not found for player ID: $targetSessionId")
        return try {
            lock.withLock {
                removeSessionFromMap(targetSessionId, session.player.mapId)
                session.player.mapId = newMapId
                addSessionToMap(targetSessionId, newMapId)
            }
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to update map for session: $targetSessionId" }
            Response.Error("Failed to update map: ${e.message}")
        }
    }

    fun getAllSessions(): Response<List<PlayerSession>> =
        Response.Success(sessions.values.toList())

    fun getConnectedPlayers(): Response<List<Player>> =
        Response.Success(sessions.values.map { it.player })

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
            Logger.error(e) { "Failed to associate TCP address for session: $targetSessionId" }
            Response.Error("Failed to associate TCP address: ${e.message}")
        }
    }

    fun getSessionByToken(token: String): Response<PlayerSession> {
        return tokenToSessionId[token]?.let { playerId ->
            sessions[playerId]?.let { Response.Success(it) }
                ?: Response.Error("Session not found for token")
        } ?: Response.Error("Invalid token")
    }

    fun validateToken(token: String): Boolean {
        return tokenToSessionId.containsKey(token)
    }

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
        removeSessionFromMap(session.player.id, session.player.mapId)
        session.tcpAddress?.let { tcpAddressToSessionId.remove(it) }
        session.udpAddress?.let { udpAddressToSessionId.remove(it) }
        tokenToSessionId.remove(session.token)
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
