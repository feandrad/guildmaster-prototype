package com.guildmaster.server.cli.commands

import com.guildmaster.server.cli.CommandContext
import com.guildmaster.server.cli.CommandNode
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager

class PlayersCommand(private val sessionManager: SessionManager) : CommandNode<Unit>("players") {
    override fun execute(context: CommandContext): Response<Unit> {
        when (val result = sessionManager.getConnectedPlayers()) {
            is Response.Success -> {
                val players = result.data
                if (players.isEmpty()) {
                    println("No players connected")
                } else {
                    println("Connected players:")
                    players.forEach { player ->
                        println("- ${player.name} (${player.id}) at (${player.position.x}, ${player.position.y}) in map ${player.mapId}")
                    }
                }
                return Response.Success(Unit)
            }
            is Response.Error -> return Response.Error(result.message)
        }
    }

    override fun getUsage(): String = "$name - List connected players"

} 