package com.guildmaster.server.world.player

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
    @Contextual var position: Vector2f = randomSpawnPosition(),
    var mapId: String = ""
) {
    companion object {
        private const val SPAWN_CENTER_X = 400f
        private const val SPAWN_CENTER_Y = 300f
        private const val SPAWN_VARIANCE = 50f

        private fun randomSpawnPosition(): Vector2f {
            val x = SPAWN_CENTER_X + Random.nextFloat() * SPAWN_VARIANCE * 2 - SPAWN_VARIANCE
            val y = SPAWN_CENTER_Y + Random.nextFloat() * SPAWN_VARIANCE * 2 - SPAWN_VARIANCE
            return Vector2f(x, y)
        }
    }
} 