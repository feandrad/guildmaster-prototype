package com.guildmaster.server.cli.commands

import com.guildmaster.server.GameServer
import com.guildmaster.server.Logger
import com.guildmaster.server.cli.CommandContext
import com.guildmaster.server.cli.CommandNode
import com.guildmaster.server.session.Response
import kotlin.system.exitProcess

class ExitCommand(private val server: GameServer) : CommandNode<Nothing>("exit") {
    override fun execute(context: CommandContext): Response<Nothing> = try {
        println("Stopping server...")
        server.stop()
        exitProcess(0)
    } catch (e: Exception) {
        Logger.error(e) { "Error stopping server" }
        Response.Error("Error stopping server")
    }


    override fun getUsage(): String = "exit - Stop the server and exit"
} 