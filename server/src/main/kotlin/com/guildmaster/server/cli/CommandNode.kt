package com.guildmaster.server.cli

import com.guildmaster.server.session.Response

abstract class CommandNode<T>(
    val name: String,
    val aliases: List<String> = emptyList(),
){
    abstract fun execute(context: CommandContext): Response<T>
    abstract fun getUsage(): String
}