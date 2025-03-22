package com.guildmaster.server.cli.command

import com.guildmaster.server.GameServer
import com.guildmaster.server.player.Player
import com.guildmaster.server.session.Response
import java.net.InetSocketAddress
import java.util.UUID

class ConnectCommand(private val server: GameServer) : LeafCommandNode<Unit>(
    name = "connect",
    description = "Connect a new player to the server"
) {
    override fun execute(args: List<String>): Response<Unit> {
        if (args.size < 2) {
            println(getUsage())
            return Response.Error("Invalid arguments")
        }

        val name = args[0]
        val address = args[1]

        try {
            val player = Player(
                id = UUID.randomUUID().toString(),
                name = name,
                color = "blue"
            )

            val socketAddress = InetSocketAddress(address, 8080)
            val result = server.sessionManager.createSession(player, socketAddress)

            when (result) {
                is Response.Success -> {
                    println("Player $name connected successfully")
                    return Response.Success(Unit)
                }
                is Response.Error -> {
                    return Response.Error("Failed to connect player: ${result.message}")
                }
            }
        } catch (e: Exception) {
            return Response.Error("Error connecting player: ${e.message}")
        }
    }

    override fun getUsage(): String = "connect <name> <address> - Connect a new player to the server"
} 