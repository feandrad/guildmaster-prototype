package com.guildmaster.server.cli.command

import com.guildmaster.server.GameServer
import com.guildmaster.server.player.Player
import com.guildmaster.server.session.Response
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.util.UUID

private val logger = KotlinLogging.logger {}

class TcpCommand(private val server: GameServer) : LeafCommandNode(
    name = "tcp",
    description = "Manage TCP connections"
) {
    override fun execute(args: List<String>) {
        if (args.isEmpty()) {
            println(getUsage())
            return
        }

        when (args[0]) {
            "list" -> listConnections()
            "disconnect" -> handleDisconnect(args.drop(1))
            else -> println("Unknown subcommand: ${args[0]}")
        }
    }

    private fun listConnections() {
        val clients = server.clients
        if (clients.isEmpty()) {
            println("No active TCP connections")
            return
        }

        println("Active TCP connections:")
        clients.forEach { client ->
            println("- ${client.sessionId} from ${client.address}")
        }
    }

    private fun handleDisconnect(args: List<String>) {
        if (args.isEmpty()) {
            println("Usage: tcp disconnect <session_id>")
            return
        }

        val sessionId = args[0]
        val client = server.clients.find { it.sessionId == sessionId }

        if (client == null) {
            println("No active connection found for session ID: $sessionId")
            return
        }

        try {
            client.stop()
            println("Disconnected client: $sessionId")
        } catch (e: Exception) {
            logger.error(e) { "Error disconnecting client: $sessionId" }
            println("Error disconnecting client: ${e.message}")
        }
    }

    override fun getUsage(): String = """
        TCP Commands:
        tcp list - List active TCP connections
        tcp disconnect <session_id> - Disconnect a TCP client
    """.trimIndent()
} 