package com.guildmaster.server.player

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f
import kotlin.random.Random

/**
 * Represents a player in the game with all their specific information.
 */
@Serializable
data class Player(
    val id: String,
    val name: String,
    var color: String,
    @Contextual
    var position: Vector2f = Vector2f(0f, 0f),
    var mapId: String = "default"
) {
    companion object {
        // Spawn area parameters
        private const val SPAWN_CENTER_X = 400f
        private const val SPAWN_CENTER_Y = 300f
        private const val SPAWN_VARIANCE = 50f
        
        // Generate a random spawn position
        private fun randomSpawnPosition(): Vector2f {
            val x = SPAWN_CENTER_X + Random.nextFloat() * SPAWN_VARIANCE * 2 - SPAWN_VARIANCE
            val y = SPAWN_CENTER_Y + Random.nextFloat() * SPAWN_VARIANCE * 2 - SPAWN_VARIANCE
            return Vector2f(x, y)
        }
    }
    
    /**
     * Update the player's position
     */
    fun updatePosition(newX: Float, newY: Float) {
        position.set(newX, newY)
    }
    
    /**
     * Convert the player to a simplified representation for sending to the client
     */
    fun toClientView(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "color" to color,
        "x" to position.x,
        "y" to position.y,
        "map" to mapId
    )
} 