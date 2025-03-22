package com.guildmaster.server.cli.command

import com.guildmaster.server.cli.CommandHandler
import com.guildmaster.server.session.Response

class HelpCommand(private val commandHandler: CommandHandler) : LeafCommandNode<Unit>(
    name = "help",
    description = "Show available commands"
) {
    override fun execute(args: List<String>): Response<Unit> {
        println("Available commands:")
        commandHandler.getRegisteredCommands().forEach { command ->
            println(command.getUsage())
        }
        return Response.Success(Unit)
    }

    override fun getUsage(): String = "help - Show available commands"
} 