package com.guildmaster.server

import com.guildmaster.server.network.Protocol
import mu.KotlinLogging
import java.net.InetSocketAddress
import kotlinx.serialization.json.Json

/**
 * Example of a message handler that could process messages.
 * This class is not currently used as message handling is done directly in GameServer.
 */
class MessageHandlerExample {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Log a TCP message example.
     */
    fun logTcpMessage(address: InetSocketAddress, message: String) {
        try {
            logger.debug { "TCP message from $address: $message" }
            
            // Parse the message based on protocol prefixes
            when {
                message.startsWith(Protocol.CMD_CONNECT) -> {
                    val playerName = message.substringAfter(" ").trim()
                    logger.info { "Connect request from $address with name: $playerName" }
                }
                message.startsWith(Protocol.CMD_CHAT) -> {
                    logger.debug { "Chat message from $address" }
                }
                message.startsWith(Protocol.CMD_MAP) -> {
                    logger.debug { "Map change from $address" }
                }
                message.startsWith(Protocol.CMD_CONFIG) -> {
                    logger.debug { "Config update from $address" }
                }
                else -> {
                    logger.warn { "Unknown TCP message format from $address: $message" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing TCP message from $address" }
        }
    }
    
    /**
     * Log a UDP message example.
     */
    fun logUdpMessage(address: InetSocketAddress, message: String) {
        try {
            logger.debug { "UDP message from $address: $message" }
            
            // Parse the message based on protocol prefixes
            when {
                message.startsWith(Protocol.CMD_POS) -> {
                    logger.debug { "Position update from $address" }
                }
                message.startsWith(Protocol.CMD_ACTION) -> {
                    logger.debug { "Action from $address" }
                }
                message.startsWith(Protocol.CMD_PING) -> {
                    logger.debug { "Ping from $address" }
                }
                else -> {
                    logger.warn { "Unknown UDP message format from $address: $message" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP message from $address" }
        }
    }
} 