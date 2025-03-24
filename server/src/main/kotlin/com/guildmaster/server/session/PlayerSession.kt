package com.guildmaster.server.session

import com.guildmaster.server.world.player.Player
import org.joml.Vector2f
import java.net.InetSocketAddress

/**
 * Represents a connected player session on the server.
 * Stores all relevant information about the player during the session.
 */
data class PlayerSession(
    val player: Player,
    var tcpAddress: InetSocketAddress? = null,
    var udpAddress: InetSocketAddress? = null,
    private var lastTcpActivity: Long = System.currentTimeMillis(),
    private var lastUdpActivity: Long = System.currentTimeMillis(),    
    val token: String = java.util.UUID.randomUUID().toString()
) {
    constructor(id: String, name: String, color: String) : this(
        player = Player(id = id, name = name, color = color)
    )
    
    fun updateTcpActivity() {
        lastTcpActivity = System.currentTimeMillis()
    }
    
    fun updateUdpActivity() {
        lastUdpActivity = System.currentTimeMillis()
    }
    
    fun updatePosition(x: Float, y: Float) {
        player.position = Vector2f(x, y)
        updateUdpActivity()
    }

    fun isInactive(timeoutMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastTcpActivity > timeoutMs) && (now - lastUdpActivity > timeoutMs)
    }
} 