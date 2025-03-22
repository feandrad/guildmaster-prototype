package com.guildmaster.server.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Serializable
data class SystemConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "System"
)

/**
 * Manages the system identity configuration for system-generated messages.
 */
class SystemIdentity {
    private val configFile = File("system.json")
    private var config: SystemConfig = SystemConfig()
    
    /**
     * Load or generate the system identity configuration.
     * @throws IllegalStateException if the configuration is invalid
     */
    fun loadOrGenerateConfig() {
        try {
            if (configFile.exists()) {
                config = Json.decodeFromString(configFile.readText())
                logger.info { "Loaded system identity: $config" }
            } else {
                config = SystemConfig()
                saveConfig()
                logger.info { "Generated new system identity: $config" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load system identity" }
            config = SystemConfig()
            saveConfig()
        }
    }
    
    private fun saveConfig() {
        try {
            configFile.writeText(Json.encodeToString(config))
        } catch (e: Exception) {
            logger.error(e) { "Failed to save system identity" }
        }
    }
    
    /**
     * Get the system player ID.
     */
    fun getId(): String = config.id
    
    /**
     * Get the system name.
     */
    fun getName(): String = config.name
} 