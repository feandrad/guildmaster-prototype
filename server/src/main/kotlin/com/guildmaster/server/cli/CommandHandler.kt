package com.guildmaster.server.cli

import com.guildmaster.server.GameServer
import com.guildmaster.server.cli.command.*
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class CommandHandler(private val server: GameServer) {
    private val commands = ConcurrentHashMap<String, CommandNode>()
    private var isRunning = false

    init {
        registerCommands()
    }

    private fun registerCommands() {
        registerCommand(PlayersCommand(server))
        registerCommand(HelpCommand(this))
        registerCommand(ExitCommand(server))
        registerCommand(TcpCommand(server))
    }

    private fun registerCommand(command: CommandNode) {
        commands[command.name] = command
    }

    fun getRegisteredCommands(): Collection<CommandNode> = commands.values

    fun start() {
        if (isRunning) return
        isRunning = true

        Thread {
            try {
                logger.info { "Command handler started" }
                while (isRunning) {
                    print("> ")
                    val input = readlnOrNull() ?: break
                    if (input.isBlank()) continue

                    try {
                        val args = input.split(" ")
                        val commandName = args[0]
                        val command = commands[commandName]

                        if (command == null) {
                            println("Unknown command: $commandName")
                            continue
                        }

                        command.execute(args.drop(1))
                    } catch (e: Exception) {
                        logger.error(e) { "Error executing command: $input" }
                        println("Error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Command handler error" }
            } finally {
                isRunning = false
                logger.info { "Command handler stopped" }
            }
        }.start()
    }

    fun stop() {
        isRunning = false
    }
} 