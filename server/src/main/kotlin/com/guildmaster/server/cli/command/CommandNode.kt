package com.guildmaster.server.cli.command

import com.guildmaster.server.session.Response
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Interface for all command nodes in the command tree.
 */
interface CommandNode<T> {
    val name: String
    val description: String
    fun execute(args: List<String>): Response<T>
    fun getUsage(): String
}

/**
 * Base class for command nodes that can have subcommands but don't execute anything themselves.
 */
abstract class SubcommandNode<T>(
    override val name: String,
    override val description: String,
    private val parent: CommandNode<*>? = null
) : CommandNode<T> {
    override fun execute(args: List<String>): Response<T> = Response.Success(Unit as T)
    
    override fun getUsage(): String = ""
}

/**
 * Base class for leaf command nodes that execute actual commands.
 */
abstract class LeafCommandNode<T>(
    override val name: String,
    override val description: String
) : CommandNode<T> {
    override fun execute(args: List<String>): Response<T> = Response.Success(Unit as T)
    
    override fun getUsage(): String = ""
}

/**
 * Root dispatcher for all commands.
 */
class CommandDispatcher {
    private val root = object : SubcommandNode<Unit>("root", "Root command node") {}
    
    /**
     * Register a command path with its node.
     */
    fun registerCommand(path: List<String>, node: CommandNode<Unit>) {
        var current = root
        var parent: SubcommandNode<*>? = null
        
        // Create or traverse the path
        for (i in 0 until path.size - 1) {
            val name = path[i].lowercase()
            val child = current.children[name] ?: SubcommandNode(name, "Command namespace")
            if (child !is SubcommandNode<*>) {
                logger.error { "Cannot add subcommand to leaf node: ${child.getPath()}" }
                return
            }
            parent = child
            current = child
        }
        
        // Add the final node
        val finalName = path.last().lowercase()
        if (current.children.containsKey(finalName)) {
            logger.warn { "Overwriting existing command: ${path.joinToString(" ")}" }
        }
        current.children[finalName] = node
        
        // Add aliases
        node.aliases.forEach { alias ->
            if (current.children.containsKey(alias.lowercase())) {
                logger.warn { "Overwriting existing alias: $alias" }
            }
            current.children[alias.lowercase()] = node
        }
    }
    
    /**
     * Dispatch a command string to the appropriate command node.
     */
    fun dispatchCommand(input: String, source: CommandSource): Boolean {
        val tokens = input.trim().split(" ")
        if (tokens.isEmpty()) return false
        
        val commandPath = if (tokens[0].startsWith("/")) {
            tokens[0].substring(1) + tokens.drop(1)
        } else {
            tokens
        }
        
        var current = root
        var pathIndex = 0
        
        while (pathIndex < commandPath.size) {
            val name = commandPath[pathIndex].lowercase()
            val child = current.children[name] ?: run {
                logger.warn { "Unknown command: ${commandPath.take(pathIndex + 1).joinToString(" ")}" }
                return false
            }
            current = child
            pathIndex++
        }
        
        val context = CommandContext(
            source = source,
            arguments = commandPath.drop(pathIndex),
            rawInput = input
        )
        
        return current.execute(context.arguments)
    }
    
    /**
     * Get help text for all registered commands.
     */
    fun getHelpText(): String = buildHelpText(root, 0)
    
    private fun buildHelpText(node: CommandNode<Unit>, depth: Int): String {
        val indent = "  ".repeat(depth)
        val sb = StringBuilder()
        
        // Add this node's description if it's not the root
        if (node.name != "root") {
            sb.append("$indent${node.name}: ${node.description}\n")
        }
        
        // Add child nodes
        node.children.values.distinct().forEach { child ->
            sb.append(buildHelpText(child, depth + 1))
        }
        
        return sb.toString()
    }
} 