package com.guildmaster.server

import kotlinx.serialization.Serializable
import java.awt.Color
import java.net.InetSocketAddress
import java.util.*
import kotlin.random.Random

/**
 * Represents a connected player session on the server.
 * Stores all relevant information about the player during the session.
 */
@Serializable
data class PlayerSession(
    // Unique player identifier
    val id: String = UUID.randomUUID().toString(),
    
    // Basic information
    var name: String,
    
    // Player appearance
    var color: String = randomColor(),
    
    // Position in the world - default to spawn area center
    var x: Float = randomSpawnX(),
    var y: Float = randomSpawnY(),
    
    // Current map
    var currentMapId: String = "default",
    
    // Connection metadata (not serialized)
    @kotlinx.serialization.Transient
    var tcpAddress: InetSocketAddress? = null,
    
    @kotlinx.serialization.Transient
    var udpAddress: InetSocketAddress? = null,
    
    // Session status and timestamps
    @kotlinx.serialization.Transient
    var isActive: Boolean = true,
    
    @kotlinx.serialization.Transient
    var lastTcpActivity: Long = System.currentTimeMillis(),
    
    @kotlinx.serialization.Transient
    var lastUdpActivity: Long = System.currentTimeMillis()
) {
    // Constructor for simplified session creation
    constructor(id: String, name: String) : this(
        id = id,
        name = name,
        color = randomColor(),
        x = randomSpawnX(),
        y = randomSpawnY()
    )
    
    companion object {
        // Predefined player colors
        private val PLAYER_COLORS = listOf(
            "#FF5252", "#FF4081", "#E040FB", "#7C4DFF", 
            "#536DFE", "#448AFF", "#40C4FF", "#18FFFF",
            "#64FFDA", "#69F0AE", "#B2FF59", "#EEFF41",
            "#FFFF00", "#FFD740", "#FFAB40", "#FF6E40"
        )
        
        // Spawn area parameters
        private const val SPAWN_CENTER_X = 400f
        private const val SPAWN_CENTER_Y = 300f
        private const val SPAWN_VARIANCE = 50f
        
        // Generate a random color for the player
        private fun randomColor(): String {
            return PLAYER_COLORS[Random.nextInt(PLAYER_COLORS.size)]
        }
        
        // Generate a random X coordinate for the spawn area
        private fun randomSpawnX(): Float {
            return SPAWN_CENTER_X + Random.nextFloat() * SPAWN_VARIANCE * 2 - SPAWN_VARIANCE
        }
        
        // Generate a random Y coordinate for the spawn area
        private fun randomSpawnY(): Float {
            return SPAWN_CENTER_Y + Random.nextFloat() * SPAWN_VARIANCE * 2 - SPAWN_VARIANCE
        }
    }
    
    /**
     * Update the timestamp of the last TCP activity
     */
    fun updateTcpActivity() {
        lastTcpActivity = System.currentTimeMillis()
    }
    
    /**
     * Update the timestamp of the last UDP activity
     */
    fun updateUdpActivity() {
        lastUdpActivity = System.currentTimeMillis()
    }
    
    /**
     * Update the player's position
     */
    fun updatePosition(newX: Float, newY: Float) {
        x = newX
        y = newY
        updateUdpActivity()
    }
    
    /**
     * Convert the session to a simplified representation for sending to the client
     */
    fun toClientView(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "color" to color,
            "x" to x,
            "y" to y,
            "map" to currentMapId
        )
    }
    
    /**
     * Check if the session has been inactive for too long
     */
    fun isInactive(timeoutMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastTcpActivity > timeoutMs) && (now - lastUdpActivity > timeoutMs)
    }
} 