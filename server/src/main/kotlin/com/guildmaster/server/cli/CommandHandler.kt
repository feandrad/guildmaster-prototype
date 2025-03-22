package com.guildmaster.server.cli

import com.guildmaster.server.GameServer
import com.guildmaster.server.session.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.Scanner
import java.util.NoSuchElementException
import kotlin.system.exitProcess

class CommandHandler(private val gameServer: GameServer) {
    private val logger = KotlinLogging.logger {}
    private val scanner = Scanner(System.`in`)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        scope.launch {
            try {
                while (true) {
                    try {
                        print("> ")
                        val input = scanner.nextLine().trim()
                        if (input.isNotEmpty()) {
                            handleCommand(input)
                        }
                    } catch (e: NoSuchElementException) {
                        // This happens when Ctrl+C is pressed
                        logger.debug { "Command input stream closed" }
                        break
                    } catch (e: IllegalStateException) {
                        // This happens when the scanner is closed
                        logger.debug { "Command scanner closed" }
                        break
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in command handler" }
            }
        }
    }

    private fun handleCommand(input: String) {
        val parts = input.split(" ")
        val command = parts[0].lowercase()

        when (command) {
            "players" -> listPlayers()
            "help" -> showHelp()
            "exit", "quit" -> {
                println("Shutting down server...")
                gameServer.stop()
                exitProcess(0)
            }
            else -> println("Unknown command. Type 'help' for available commands.")
        }
    }

    private fun listPlayers() {
        when (val result = gameServer.getConnectedPlayers()) {
            is Response.Error -> {
                logger.error { "Failed to get player list: ${result.message}" }
                println("Error: Failed to get player list")
            }
            is Response.Success -> {
                val players = result.data
                if (players.isEmpty()) {
                    println("No players connected.")
                    return
                }
                
                println("\nConnected players:")
                println("------------------")
                for ((index, player) in players.withIndex()) {
                    println("${index + 1}. ${player.name} (${player.color})")
                }
                println("------------------\n")
            }
        }
    }

    private fun showHelp() {
        println("""
            
            Available commands:
            ------------------
            players : List all connected players
            help    : Show this help message
            exit    : Shutdown the server
            ------------------
            
        """.trimIndent())
    }
} 