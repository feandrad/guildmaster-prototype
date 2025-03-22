package com.guildmaster.server.cli.command

import com.guildmaster.server.GameServer
import com.guildmaster.server.session.Response

class PlayersCommand(private val server: GameServer) : LeafCommandNode<Unit>(
    name = "players",
    description = "List connected players"
) {
    override fun execute(args: List<String>): Response<Unit> {
        when (val result = server.getConnectedPlayers()) {
            is Response.Success -> {
                val players = result.data
                if (players.isEmpty()) {
                    println("No players connected")
                } else {
                    println("Connected players:")
                    players.forEach { player ->
                        println("- ${player.name} (${player.id}) at (${player.position.x}, ${player.position.y}) in map ${player.currentMapId}")
                    }
                }
                return Response.Success(Unit)
            }
            is Response.Error -> return Response.Error(result.message)
        }
    }

    override fun getUsage(): String = "players - List connected players"
} 