package com.guildmaster.server

import mu.KotlinLogging
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

private const val DEFAULT_TCP_PORT = 9999
private const val DEFAULT_UDP_PORT = 9998
private const val SHUTDOWN_CHECK_INTERVAL = 1000L

/**
 * Main entry point for the Guild Master server application.
 */
fun main(args: Array<String>) {
    logger.info { "Starting Guild Master server..." }
    
    val tcpPort = System.getProperty("tcpPort")?.toIntOrNull() ?: DEFAULT_TCP_PORT
    val udpPort = System.getProperty("udpPort")?.toIntOrNull() ?: DEFAULT_UDP_PORT
    
    logger.info { "Using TCP port: $tcpPort" }
    logger.info { "Using UDP port: $udpPort" }
    
    val server = GameServer(tcpPort, udpPort)
    server.start()
    
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.info { "Shutting down server..." }
        server.stop()
    })
    
    logger.info { "Server started successfully" }
    logger.info { "Press Ctrl+C to stop the server" }
    
    try {
        while (true) {
            Thread.sleep(SHUTDOWN_CHECK_INTERVAL)
        }
    } catch (e: InterruptedException) {
        // Normal shutdown
    } finally {
        server.stop()
    }
} 