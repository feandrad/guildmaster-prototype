package com.guildmaster.server.cli

import com.guildmaster.server.session.PlayerSession

data class CommandContext(
    val source: CommandSource,
    val arguments: List<String>,
    val rawInput: String
) {
    companion object {
        fun from(input: String, source: CommandSource): CommandContext = CommandContext(
            source, emptyList(), input
        )
    }
}

sealed class CommandSource {
    data object Terminal : CommandSource()
    data class Session(val client: PlayerSession) : CommandSource()
} 