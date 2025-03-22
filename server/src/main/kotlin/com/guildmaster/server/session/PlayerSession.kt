package com.guildmaster.server.session

import com.guildmaster.server.player.Player
import kotlinx.serialization.Serializable
import java.net.InetSocketAddress
import java.util.*

/**
 * Represents a connected player session on the server.
 * Stores all relevant information about the player during the session.
 */
@Serializable
data class PlayerSession(
    // Player information
    val player: Player,
    
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
    constructor(id: String, name: String, color: String) : this(
        player = Player(id = id, name = name, color = color)
    )
    
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
        player.updatePosition(newX, newY)
        updateUdpActivity()
    }
    
    /**
     * Convert the session to a simplified representation for sending to the client
     */
    fun toClientView(): Map<String, Any> {
        return player.toClientView()
    }
    
    /**
     * Check if the session has been inactive for too long
     */
    fun isInactive(timeoutMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastTcpActivity > timeoutMs) && (now - lastUdpActivity > timeoutMs)
    }
} 