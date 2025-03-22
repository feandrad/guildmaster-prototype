package com.guildmaster.server.cli.command

import com.guildmaster.server.TcpClientHandler

data class CommandContext(
    val source: CommandSource,
    val arguments: List<String>,
    val rawInput: String
)

sealed class CommandSource {
    object Terminal : CommandSource()
    object System : CommandSource()
    data class Session(val client: TcpClientHandler) : CommandSource()
} 