package com.guildmaster.server

import com.guildmaster.server.cli.CommandHandler
import com.guildmaster.server.network.Broadcaster
import com.guildmaster.server.network.TcpService
import com.guildmaster.server.network.UdpService
import com.guildmaster.server.session.SessionManager
import com.guildmaster.server.world.GameService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameServer(
    private val tcpPort: Int,
    private val udpPort: Int,
) {
    private val sessionManager = SessionManager()
    private val tcpService = TcpService(sessionManager, tcpPort)
    private val broadcaster = Broadcaster(sessionManager, tcpService)
    private val udpService = UdpService(sessionManager, broadcaster, udpPort)
    private val commandHandler = CommandHandler(this, sessionManager, broadcaster)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val gameService = GameService(this)

    fun start() {
        try {
            tcpService.start()
            udpService.start()
            commandHandler.start()
            Logger.info { "Server started on TCP port $tcpPort and UDP port $udpPort" }
        } catch (e: Exception) {
            Logger.error(e) { "Failed to start server" }
            stop()
        }
    }

    fun stop() {
        Logger.info { "Stopping server..." }
        commandHandler.stop()
        tcpService.stop()
        udpService.stop()
        executor.shutdown()

        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        
        Logger.info { "Server stopped" }
    }
}
