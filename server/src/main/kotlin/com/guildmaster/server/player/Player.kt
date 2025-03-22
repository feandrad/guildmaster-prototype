package com.guildmaster.server.player

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Represents a player in the game with all their specific information.
 */
@Serializable
data class Player(
    // Unique player identifier
    val id: String,
    
    // Basic information
    var name: String,
    
    // Player appearance
    var color: String,
    
    // Position in the world - default to spawn area center
    var x: Float = randomSpawnX(),
    var y: Float = randomSpawnY(),
    
    // Current map
    var currentMapId: String = "default"
) {
    companion object {
        // Spawn area parameters
        private const val SPAWN_CENTER_X = 400f
        private const val SPAWN_CENTER_Y = 300f
        private const val SPAWN_VARIANCE = 50f
        
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
     * Update the player's position
     */
    fun updatePosition(newX: Float, newY: Float) {
        x = newX
        y = newY
    }
    
    /**
     * Convert the player to a simplified representation for sending to the client
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
} 