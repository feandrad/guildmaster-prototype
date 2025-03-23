package com.guildmaster.server

import mu.KotlinLogging
import kotlin.concurrent.thread

 val Logger = KotlinLogging.logger {}

private const val DEFAULT_TCP_PORT = 9999
private const val DEFAULT_UDP_PORT = 9998
private const val SHUTDOWN_CHECK_INTERVAL = 1000L

/**
 * Main entry point for the Guild Master server application.
 */
fun main(args: Array<String>) {
    Logger.info { "Starting Guild Master server..." }
    
    val tcpPort = System.getProperty("tcpPort")?.toIntOrNull() ?: DEFAULT_TCP_PORT
    val udpPort = System.getProperty("udpPort")?.toIntOrNull() ?: DEFAULT_UDP_PORT
    
    Logger.info { "Using TCP port: $tcpPort" }
    Logger.info { "Using UDP port: $udpPort" }
    
    val server = GameServer(tcpPort, udpPort)
    server.start()
    
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        Logger.info { "Shutting down server..." }
        server.stop()
    })
    
    Logger.info { "Server started successfully" }
    Logger.info { "Press Ctrl+C to stop the server" }
    
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