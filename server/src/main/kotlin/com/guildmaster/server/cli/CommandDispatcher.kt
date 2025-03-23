package com.guildmaster.server.cli

import com.guildmaster.server.Logger
import com.guildmaster.server.session.Response

class CommandDispatcher {
    private val commands = mutableMapOf<String, CommandNode<*>>()

    fun registerCommand(node: CommandNode<*>) {
        val name = node.name.lowercase()
        if (commands.containsKey(name)) {
            Logger.warn { "Overwriting existing command: ${node.name}" }
        }
        commands[name] = node
    }

    fun dispatch(context: CommandContext): Response<*> {
        val input = context.rawInput

        if (input.isBlank()) return Response.Error("Empty command")

        val tokens = input.trim().split("\\s+".toRegex())
        if (tokens.isEmpty()) return Response.Error("Empty command")

        val firstToken = tokens[0]
        val commandName = if (firstToken.startsWith("/")) {
            firstToken.substring(1).lowercase()
        } else {
            firstToken.lowercase()
        }

        val command = commands[commandName]
            ?: return Response.Error("Unknown command: $commandName")

        val args = tokens.drop(1)
        return command.execute(
            CommandContext(
                source = context.source,
                arguments = args,
                rawInput = input
            )
        )
    }

    fun getRegisteredCommands(): Collection<CommandNode<*>> = commands.values
}
