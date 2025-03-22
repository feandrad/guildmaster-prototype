package com.guildmaster.server.session

import com.guildmaster.server.player.Player
import java.net.InetSocketAddress
import org.joml.Vector2f

/**
 * Represents a connected player session on the server.
 * Stores all relevant information about the player during the session.
 */
data class PlayerSession(
    // Player information
    val player: Player,
    
    // Connection metadata (not serialized)
    var tcpAddress: InetSocketAddress? = null,
    
    var udpAddress: InetSocketAddress? = null,
    
    // Session status and timestamps
    private var lastTcpActivity: Long = System.currentTimeMillis(),
    
    private var lastUdpActivity: Long = System.currentTimeMillis()
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
    fun updatePosition(x: Float, y: Float) {
        player.position = Vector2f(x, y)
        updateUdpActivity()
    }
    
    /**
     * Convert the session to a simplified representation for sending to the client
     */
    fun toClientView(): Map<String, Any> = player.toClientView()
    
    /**
     * Check if the session has been inactive for too long
     */
    fun isInactive(timeoutMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastTcpActivity > timeoutMs) && (now - lastUdpActivity > timeoutMs)
    }
} 