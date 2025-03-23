package com.guildmaster.server.config

import com.guildmaster.server.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*


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

    val id: String
        get() = config.id

    val name: String
        get() = config.name
    
    /**
     * Load or generate the system identity configuration.
     * @throws IllegalStateException if the configuration is invalid
     */
    fun loadOrGenerateConfig() {
        try {
            if (configFile.exists()) {
                config = Json.decodeFromString(configFile.readText())
                Logger.info { "Loaded system identity: $config" }
            } else {
                config = SystemConfig()
                saveConfig()
                Logger.info { "Generated new system identity: $config" }
            }
        } catch (e: Exception) {
            Logger.error(e) { "Failed to load system identity" }
            config = SystemConfig()
            saveConfig()
        }
    }
    
    private fun saveConfig() {
        try {
            configFile.writeText(Json.encodeToString(config))
        } catch (e: Exception) {
            Logger.error(e) { "Failed to save system identity" }
        }
    }
} 