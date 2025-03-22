package com.guildmaster.server.cli.command

import com.guildmaster.server.GameServer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val MAX_MESSAGE_LENGTH = 256
private const val ELLIPSIS = "…"

/**
 * Command node for handling chat messages.
 */
class ChatCommand(
    private val gameServer: GameServer,
    parent: CommandNode? = null
) : LeafCommandNode(
    name = "chat",
    description = "Send a chat message to all players",
    aliases = listOf("say"),
    parent = parent
) {
    override fun execute(context: CommandContext): Boolean {
        val message = context.arguments.joinToString(" ").trim()
        
        if (message.isBlank()) {
            println("Message is empty")
            return false
        }
        
        val finalMessage = truncateMessage(message)
        gameServer.broadcastSystemMessage(finalMessage)
        logger.info { "Broadcast system message: $finalMessage" }
        return true
    }
    
    override fun getUsage(): String = "chat/say <message> - Send a message to all players"
    
    private fun truncateMessage(message: String): String {
        return if (message.length > MAX_MESSAGE_LENGTH) {
            message.substring(0, MAX_MESSAGE_LENGTH - 1) + ELLIPSIS
        } else {
            message
        }
    }
} 