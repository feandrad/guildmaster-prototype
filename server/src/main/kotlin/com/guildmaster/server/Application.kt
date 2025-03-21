package com.guildmaster.server

import mu.KotlinLogging
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

/**
 * Main entry point for the Guild Master server application.
 */
fun main(args: Array<String>) {
    logger.info { "Starting Guild Master server..." }
    
    // Parse command line arguments
    var tcpPort = 9999
    var udpPort = 9998
    
    // Check for system properties passed via command line
    System.getProperty("tcpPort")?.toIntOrNull()?.let { tcpPort = it }
    System.getProperty("udpPort")?.toIntOrNull()?.let { udpPort = it }
    
    logger.info { "Using TCP port: $tcpPort" }
    logger.info { "Using UDP port: $udpPort" }
    
    // Create and start the server
    val server = GameServer(tcpPort, udpPort)
    server.start()
    
    // Register shutdown hook to stop the server when the program terminates
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.info { "Shutting down server..." }
        server.stop()
    })
    
    logger.info { "Server started successfully" }
    logger.info { "Press Ctrl+C to stop the server" }
    
    // Keep the program running
    try {
        while (true) {
            Thread.sleep(1000)
        }
    } catch (e: InterruptedException) {
        // Interrupted, shutting down
    } finally {
        server.stop()
    }
} 