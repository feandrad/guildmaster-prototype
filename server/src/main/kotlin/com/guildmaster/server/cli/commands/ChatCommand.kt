package com.guildmaster.server.cli.commands

import com.guildmaster.server.Logger
import com.guildmaster.server.broadcast.Broadcaster
import com.guildmaster.server.cli.CommandContext
import com.guildmaster.server.cli.CommandNode
import com.guildmaster.server.cli.CommandSource
import com.guildmaster.server.session.Response

class ChatCommand(
    private val broadcaster: Broadcaster,
) : CommandNode<Any?>("chat", listOf("say")) {

    override fun execute(context: CommandContext): Response<String> {
        return try {
            val message = context.arguments.drop(1).joinToString(" ")
            if (message.isBlank()) {
                return Response.Error("Message cannot be empty")
            }

            val truncatedMessage = if (message.length > MAX_MESSAGE_LENGTH) {
                message.take(MAX_MESSAGE_LENGTH) + ELLIPSIS
            } else {
                message
            }

            return when (context.source) {
                is CommandSource.Session -> {
                    broadcaster.broadcastToMap(context.source.client.player.mapId, truncatedMessage)
                    Response.Success(truncatedMessage)
                }

                CommandSource.Terminal -> {
                    val finalMsg = "[System] $truncatedMessage"
                    broadcaster.broadcastToAll(finalMsg)
                    Response.Success(finalMsg)
                }
            }

        } catch (e: Exception) {
            Logger.error(e) { "Error executing chat command" }
            Response.Error("Failed to send chat message")
        }
    }

    override fun getUsage(): String {
        return "$name <message> - Sends a text message to online clients"
    }


    companion object {
        private const val MAX_MESSAGE_LENGTH = 256
        private const val ELLIPSIS = "…"
    }
}