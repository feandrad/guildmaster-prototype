package com.guildmaster.server.cli

import com.guildmaster.server.GameServer
import com.guildmaster.server.Logger
import com.guildmaster.server.cli.commands.ChatCommand
import com.guildmaster.server.cli.commands.ExitCommand
import com.guildmaster.server.cli.commands.HelpCommand
import com.guildmaster.server.cli.commands.PlayersCommand
import com.guildmaster.server.network.Broadcaster
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager

class CommandHandler(
    private val server: GameServer,
    private val sessionManager : SessionManager,
    private val broadcaster : Broadcaster,
) {
    private val dispatcher = CommandDispatcher()

    private var isRunning: Boolean = false

    init {
        registerCommands()
    }

    private fun registerCommands() {
        dispatcher.registerCommand(PlayersCommand(sessionManager))
        dispatcher.registerCommand(HelpCommand(this))
        dispatcher.registerCommand(ExitCommand(server))
        dispatcher.registerCommand(ChatCommand(broadcaster))
    }

    fun getRegisteredCommands(): Collection<CommandNode<*>> = dispatcher.getRegisteredCommands()

    fun start() {
        isRunning = true
        Thread {
            try {
                Logger.info { "Command handler started" }
                while (isRunning) {
                    print("> ")
                    val input = readlnOrNull() ?: break
                    if (input.isBlank()) continue

                    try {
                        when (val result = dispatcher.dispatch(CommandContext.from(input, CommandSource.Terminal))) {
                            is Response.Success -> {}
                            is Response.Error -> println("Error: ${result.message}")
                        }
                    } catch (e: Exception) {
                        Logger.error(e) { "Error executing command: $input" }
                        println("Error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.error(e) { "Command handler error" }
            } finally {
                isRunning = false
                Logger.info { "Command handler stopped" }
            }
        }.start()
    }

    fun stop() {
        isRunning = false
    }

    fun executeCommand(context: CommandContext) = dispatcher.dispatch(context)
} 