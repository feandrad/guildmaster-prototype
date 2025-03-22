package com.guildmaster.server.cli.command

import com.guildmaster.server.GameServer
import com.guildmaster.server.session.Response
import kotlin.system.exitProcess

class ExitCommand(private val server: GameServer) : LeafCommandNode<Unit>(
    name = "exit",
    description = "Stop the server and exit"
) {
    override fun execute(args: List<String>): Response<Unit> {
        println("Stopping server...")
        server.stop()
        exitProcess(0)
        return Response.Success(Unit)
    }

    override fun getUsage(): String = "exit - Stop the server and exit"
} 