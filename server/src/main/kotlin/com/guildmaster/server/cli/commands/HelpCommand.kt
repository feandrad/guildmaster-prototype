package com.guildmaster.server.cli.commands

import com.guildmaster.server.cli.CommandContext
import com.guildmaster.server.cli.CommandHandler
import com.guildmaster.server.cli.CommandNode
import com.guildmaster.server.session.Response

class HelpCommand(
    private val commandHandler: CommandHandler
) : CommandNode<Unit>("help") {
    override fun getUsage(): String = "$name - Show available commands"

    override fun execute(context: CommandContext): Response<Unit> {
        println("\nAvailable Commands:")
        println("------------------")
        commandHandler.getRegisteredCommands().forEach { command ->
            println(command.getUsage())
        }
        println("------------------")
        return Response.Success(Unit)
    }
}
