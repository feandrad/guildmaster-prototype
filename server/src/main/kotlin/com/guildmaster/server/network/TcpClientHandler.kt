package com.guildmaster.server.network

import com.guildmaster.server.Logger
import com.guildmaster.server.cli.CommandContext
import com.guildmaster.server.cli.CommandSource
import com.guildmaster.server.session.PlayerSession
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager
import kotlinx.serialization.SerializationException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel


class TcpClientHandler(
    private val sessionManager: SessionManager,
    private val tcpService: TcpService,
    private val channel: SocketChannel,
    val clientAddress: InetSocketAddress,
) : Runnable {
    private var isRunning = true
    private var session: PlayerSession? = null
    
    override fun run() {
        try {
            val buffer = ByteBuffer.allocate(1024)
            while (isRunning) {
                val bytesRead = channel.read(buffer)
                if (bytesRead == -1) {
                    handleDisconnect()
                    break
                }
                
                buffer.flip()
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                buffer.clear()
                
                processIncomingData(String(data))
            }
        } catch (e: Exception) {
            if (isRunning) {
                Logger.error(e) { "Error reading from client $clientAddress" }
            }
        } finally {
            handleDisconnect()
        }
    }
    
    private fun processIncomingData(data: String) {
        data.split("\n").forEach { line ->
            if (line.isBlank()) return@forEach
            
            try {
                when {
                    line.startsWith(Protocol.CMD_CONNECT) -> handleLogin(line)
                    line.startsWith(Protocol.CMD_PING) -> heartbeat(line)
                    else -> handleCommand(line)
                }
            } catch (e: Exception) {
                Logger.error(e) { "Error processing line: $line" }
                sendMessage("ERROR Internal server error")
            }
        }
    }

    private fun handleLogin(message: String) {
        try {
            val jsonPart = message.substring(Protocol.CMD_CONNECT.length).trim()
            val data = Protocol.json.decodeFromString<Protocol.ConnectMessage>(jsonPart)

            when (val result = sessionManager.createSession(data.user, data.color, clientAddress)) {
                is Response.Success -> sendMessage(Protocol.connectMessage(result.data))
                is Response.Error -> sendMessage("ERROR ${result.message}")
            }
        } catch (e: SerializationException) {
            Logger.warn(e) { "Invalid login JSON received: $message" }
            sendMessage("ERROR Invalid login JSON received")
        } catch (e: Exception) {
            Logger.error(e) { "Error handling login" }
            sendMessage("ERROR Failed to process login")
        }
    }
    
    private fun heartbeat(line: String) {

    }
    
    private fun handleCommand(command: String) {
        val context = CommandContext(
            source = CommandSource.Session(session ?: return),
            arguments = command.split(" "),
            rawInput = command
        )
//        commandHandler.executeCommand(context)
    }
    
    fun sendMessage(message: String) {
        try {
            channel.write(ByteBuffer.wrap(message.toByteArray()))
        } catch (e: Exception) {
            Logger.error(e) { "Error sending message to client $clientAddress" }
        }
    }
    
    private fun handleDisconnect() {
        isRunning = false
        session?.let { session ->
            sessionManager.removeSession(session.player.id)
        }
        tcpService.removeClient(this)
        close()
        Logger.info { "Client disconnected: $clientAddress" }
    }
    
    fun close() {
        try {
            channel.close()
        } catch (e: Exception) {
            Logger.error(e) { "Error closing client channel $clientAddress" }
        }
    }
}