package com.guildmaster.server

import com.guildmaster.server.cli.CommandHandler
import com.guildmaster.server.cli.command.TcpCommand
import com.guildmaster.server.config.SystemIdentity
import com.guildmaster.server.network.Protocol
import com.guildmaster.server.player.Player
import com.guildmaster.server.session.PlayerSession
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val BUFFER_SIZE = 1024
private const val CONFIG_DELAY_MS = 100L

/**
 * Main server class that handles all network communication and game state.
 */
class GameServer(
    private val tcpPort: Int,
    private val udpPort: Int
) {
    private val logger = KotlinLogging.logger {}
    
    val sessionManager = SessionManager()
    private val systemIdentity = SystemIdentity()
    
    private var isRunning = false
    private lateinit var tcpServerChannel: ServerSocketChannel
    private lateinit var udpChannel: DatagramChannel
    val clients = mutableListOf<TcpClientHandler>()
    
    private val tcpExecutor = Executors.newCachedThreadPool()
    private val udpExecutor = Executors.newSingleThreadExecutor()
    
    private lateinit var commandHandler: CommandHandler
    private lateinit var tcpCommand: TcpCommand
    
    /**
     * Start the server.
     */
    fun start() {
        if (isRunning) return
        
        try {
            systemIdentity.loadOrGenerateConfig()
            initializeNetworkChannels()
            isRunning = true
            logger.info { "Server started on TCP port $tcpPort and UDP port $udpPort" }
            
            commandHandler = CommandHandler(this)
            tcpCommand = TcpCommand(this)
            commandHandler.start()
            
            startUdpListener()
            startTcpListener()
        } catch (e: Exception) {
            logger.error(e) { "Failed to start server" }
            stop()
            throw e
        }
    }
    
    private fun initializeNetworkChannels() {
        tcpServerChannel = ServerSocketChannel.open().apply {
            configureBlocking(true)
            socket().bind(InetSocketAddress(tcpPort))
        }
        
        udpChannel = DatagramChannel.open().apply {
            configureBlocking(true)
            socket().bind(InetSocketAddress(udpPort))
        }
    }
    
    private fun startTcpListener() {
        thread(name = "tcp-listener") {
            try {
                logger.info { "TCP listener started, waiting for connections..." }
                
                while (isRunning) {
                    val clientChannel = tcpServerChannel.accept()
                    handleNewTcpConnection(clientChannel)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    logger.error(e) { "Error in TCP listener" }
                }
            }
        }
    }
    
    private fun handleNewTcpConnection(clientChannel: SocketChannel) {
        try {
            clientChannel.configureBlocking(true)
            val clientAddress = clientChannel.remoteAddress as InetSocketAddress
            
            logger.info { "New TCP connection from $clientAddress" }
            
            val clientHandler = TcpClientHandler(clientChannel, clientAddress, this)
            clients.add(clientHandler)
            tcpExecutor.execute(clientHandler)
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing new TCP connection" }
            try { clientChannel.close() } catch (ignored: Exception) {}
        }
    }
    
    private fun startUdpListener() {
        udpExecutor.execute {
            try {
                logger.info { "UDP listener started, waiting for packets..." }
                
                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                
                while (isRunning) {
                    buffer.clear()
                    val sender = udpChannel.receive(buffer) as? InetSocketAddress ?: continue
                    
                    buffer.flip()
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    
                    handleUdpPacket(sender, bytes)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    logger.error(e) { "Error in UDP listener" }
                }
            }
        }
    }

    private fun handleUdpPacket(sender: InetSocketAddress, data: ByteArray) {
        try {
            val message = String(data).trim()
            
            logger.debug { "UDP packet received from $sender: $message" }
            
            when {
                message.startsWith(Protocol.CMD_POS) -> handlePositionUpdate(sender, message)
                message.startsWith(Protocol.CMD_ACTION) -> handleActionPacket(sender, message)
                message.startsWith(Protocol.CMD_PING) -> sendUdpPacket(sender, "${Protocol.MSG_PONG}\n")
                message.startsWith(Protocol.CMD_UDP_REGISTER) -> handleUdpRegistration(sender, message)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP packet from $sender" }
        }
    }
    
    private fun handleUdpRegistration(sender: InetSocketAddress, message: String) {
        try {
            val jsonPart = message.substringAfter(" ", "")
            if (jsonPart.isBlank()) {
                logger.warn { "Invalid UDP registration message format from $sender" }
                return
            }
            
            val regData = Protocol.json.decodeFromString<Protocol.UdpRegisterMessage>(jsonPart)
            val playerId = regData.id
            
            when (val sessionResult = sessionManager.getSessionById(playerId)) {
                is Response.Error -> {
                    logger.warn { "Session not found for player ID $playerId during UDP registration: ${sessionResult.message}" }
                    return
                }
                is Response.Success -> {
                    val session = sessionResult.data
                    when (val updateResult = sessionManager.updateUdpAddress(session.player.id, sender)) {
                        is Response.Error -> {
                            logger.error { "Failed to update UDP address: ${updateResult.message}" }
                            return
                        }
                        is Response.Success -> {
                            logger.info { "UDP address $sender registered for player ${session.player.name} (${session.player.id})" }
                            sendUdpPacket(sender, "UDP_REGISTERED\n")
                            
                            sendInitialPosition(sender, session)
                            notifyTcpClient(session)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP registration from $sender" }
        }
    }
    
    private fun sendInitialPosition(sender: InetSocketAddress, session: PlayerSession) {
        val positionMessage = Protocol.PositionMessage(
            playerId = session.player.id,
            x = session.player.position.x,
            y = session.player.position.y,
            mapId = session.player.currentMapId
        )
        val posMsg = Protocol.encodePositionMessage(positionMessage)
        sendUdpPacket(sender, "$posMsg\n")
        logger.debug { "Sent initial position after UDP registration: (${session.player.position.x}, ${session.player.position.y})" }
    }
    
    private fun notifyTcpClient(session: PlayerSession) {
        session.tcpAddress?.let { tcpAddr ->
            when (val tcpSessionResult = sessionManager.getSessionByTcpAddress(tcpAddr)) {
                is Response.Error -> logger.warn { "TCP session not found: ${tcpSessionResult.message}" }
                is Response.Success -> {
                    val tcpSession = tcpSessionResult.data
                    val tcpClient = clients.find { it.sessionId == tcpSession.player.id }
                    tcpClient?.let { client ->
                        when (val mapSessionsResult = sessionManager.getSessionsInMap(session.player.currentMapId)) {
                            is Response.Error -> logger.warn { "Failed to get map sessions: ${mapSessionsResult.message}" }
                            is Response.Success -> {
                                val playersMessage = Protocol.createPlayersListMessage(mapSessionsResult.data)
                                client.sendMessage(playersMessage)
                                logger.debug { "Sent updated player list to ${session.player.name} after UDP registration" }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun handlePositionUpdate(sender: InetSocketAddress, positionMessage: String) {
        try {
            val jsonPart = positionMessage.substringAfter(" ", "")
            if (jsonPart.isBlank()) return
            
            val positionData = Protocol.json.decodeFromString<Protocol.PositionMessage>(jsonPart)
            
            when (val sessionResult = sessionManager.getSessionByUdpAddress(sender)) {
                is Response.Error -> {
                    logger.warn { "Session not found for UDP address $sender: ${sessionResult.message}" }
                    return
                }
                is Response.Success -> {
                    val session = sessionResult.data
                    
                    if (positionData.playerId != session.player.id) {
                        logger.warn { "Invalid player ID in position update from $sender" }
                        return
                    }
                    
                    session.updatePosition(positionData.x, positionData.y)
                    
                    when (val mapSessionsResult = sessionManager.getSessionsInMap(session.player.currentMapId)) {
                        is Response.Error -> {
                            logger.warn { "Failed to get map sessions: ${mapSessionsResult.message}" }
                            return
                        }
                        is Response.Success -> {
                            val playersMessage = Protocol.createPlayersListMessage(mapSessionsResult.data)
                            broadcastToMap(session.player.currentMapId, playersMessage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing position update from $sender" }
        }
    }
    
    private fun handleActionPacket(sender: InetSocketAddress, actionMessage: String) {
        try {
            val jsonPart = actionMessage.substringAfter(" ", "")
            if (jsonPart.isBlank()) return
            
            val actionData = Protocol.json.decodeFromString<Protocol.ActionMessage>(jsonPart)
            
            when (val sessionResult = sessionManager.getSessionByUdpAddress(sender)) {
                is Response.Error -> {
                    logger.warn { "Session not found for UDP address $sender: ${sessionResult.message}" }
                    return
                }
                is Response.Success -> {
                    val session = sessionResult.data
                    
                    if (actionData.playerId != session.player.id) {
                        logger.warn { "Invalid player ID in action packet from $sender" }
                        return
                    }
                    
                    when (val mapSessionsResult = sessionManager.getSessionsInMap(session.player.currentMapId)) {
                        is Response.Error -> {
                            logger.warn { "Failed to get map sessions: ${mapSessionsResult.message}" }
                            return
                        }
                        is Response.Success -> {
                            val actionMessage = Protocol.encodeActionMessage(actionData)
                            broadcastToMap(session.player.currentMapId, actionMessage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing action packet from $sender" }
        }
    }
    
    private fun broadcastToMap(mapId: String, message: String) {
        when (val mapSessionsResult = sessionManager.getSessionsInMap(mapId)) {
            is Response.Error -> {
                logger.warn { "Failed to get map sessions: ${mapSessionsResult.message}" }
                return
            }
            is Response.Success -> {
                mapSessionsResult.data.forEach { session ->
                    session.udpAddress?.let { sendUdpPacket(it, "$message\n") }
                }
            }
        }
    }
    
    fun sendUdpPacket(target: InetSocketAddress, message: String) {
        try {
            val buffer = ByteBuffer.wrap(message.toByteArray())
            udpChannel.send(buffer, target)
        } catch (e: Exception) {
            logger.error(e) { "Error sending UDP packet to $target" }
        }
    }
    
    fun handleTcpCommand(client: TcpClientHandler, command: String) {
        logger.debug { "Processing TCP command: $command from ${client.clientAddress}" }
        
        try {
            val context = CommandContext(
                source = CommandSource.Session(client),
                arguments = listOf(command),
                rawInput = command
            )
            
            if (!tcpCommand.execute(context)) {
                logger.warn { "Failed to execute TCP command: $command" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing command: $command" }
            client.sendMessage("ERROR Internal server error")
        }
    }
    
    private fun notifyMapChange(mapId: String) {
        when (val playersResult = sessionManager.getSessionsInMap(mapId)) {
            is Response.Error -> {
                logger.error { "Failed to get players in map: ${playersResult.message}" }
                return
            }
            is Response.Success -> {
                val mapMessage = Protocol.encodeMapChangeMessage(
                    Protocol.MapChangeMessage(
                        mapId = mapId,
                        playerIds = playersResult.data.map { it.player.id }
                    )
                )
                
                playersResult.data.forEach { player ->
                    val playerClient = clients.find { it.clientAddress == player.tcpAddress }
                    playerClient?.sendMessage(mapMessage)
                }
            }
        }
    }
    
    fun broadcastPlayersList() {
        when (val playersResult = sessionManager.getAllPlayers()) {
            is Response.Error -> {
                logger.error { "Failed to get players list: ${playersResult.message}" }
                return
            }
            is Response.Success -> {
                val playersMessage = Protocol.createPlayersListMessage(playersResult.data.map { it.id })
                clients.forEach { it.sendMessage(playersMessage) }
            }
        }
    }
    
    fun handleClientDisconnect(client: TcpClientHandler) {
        clients.remove(client)
        
        when (val sessionResult = sessionManager.getSessionByTcpAddress(client.clientAddress)) {
            is Response.Error -> {
                logger.info { "Client disconnected: ${client.clientAddress}" }
            }
            is Response.Success -> {
                val session = sessionResult.data
                val mapId = session.player.currentMapId
                when (val removeResult = sessionManager.removeSession(session.player.id)) {
                    is Response.Error -> {
                        logger.error { "Failed to remove session: ${removeResult.message}" }
                    }
                    is Response.Success -> {
                        broadcastPlayersList()
                        notifyMapChange(mapId)
                        logger.info { "Client disconnected: ${session.player.name} (${client.clientAddress})" }
                    }
                }
            }
        }
    }
    
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        logger.info { "Stopping server..." }
        
        try {
            tcpServerChannel.close()
            udpChannel.close()
            tcpExecutor.shutdown()
            udpExecutor.shutdown()
            
            if (!tcpExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                tcpExecutor.shutdownNow()
            }
            if (!udpExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                udpExecutor.shutdownNow()
            }
            
            clients.forEach { it.stop() }
            clients.clear()
            
            logger.info { "Server stopped" }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping server" }
        }
    }
    
    fun getConnectedPlayers(): Response<List<Player>> {
        return sessionManager.getAllPlayers()
    }
    
    fun broadcastSystemMessage(message: String) {
        val chatMessage = Protocol.encodeChatMessage(
            Protocol.ChatMessage(
                playerId = "system",
                playerName = "System",
                text = message
            )
        )
        
        clients.forEach { it.sendMessage(chatMessage) }
    }
}

class TcpClientHandler(
    private val clientChannel: SocketChannel,
    val clientAddress: InetSocketAddress,
    private val server: GameServer
) : Runnable {
    private val logger = KotlinLogging.logger {}
    private val buffer = ByteBuffer.allocate(BUFFER_SIZE)
    private val incomingData = StringBuilder()
    
    @Volatile
    private var isRunning = true
    
    // Session ID associated with this client
    var sessionId: String? = null
    
    override fun run() {
        try {
            while (isRunning && clientChannel.isOpen) {
                buffer.clear()
                val bytesRead = clientChannel.read(buffer)
                
                if (bytesRead == -1) {
                    // Connection closed by client
                    break
                }
                
                if (bytesRead > 0) {
                    buffer.flip()
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    
                    processIncomingData(String(data))
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing data from client $clientAddress" }
        } finally {
            close()
            server.handleClientDisconnect(this)
        }
    }
    
    private fun processIncomingData(data: String) {
        incomingData.append(data)
        
        var newlinePos: Int
        while (incomingData.indexOf('\n').also { newlinePos = it } != -1) {
            val line = incomingData.substring(0, newlinePos).trim()
            incomingData.delete(0, newlinePos + 1)
            
            if (line.isNotEmpty()) {
                server.handleTcpCommand(this, line)
            }
        }
    }
    
    fun sendMessage(message: String) {
        try {
            if (!clientChannel.isOpen) return
            
            val data = if (message.endsWith('\n')) message else "$message\n"
            val buffer = ByteBuffer.wrap(data.toByteArray())
            
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending message to $clientAddress" }
            close()
        }
    }
    
    /**
     * Close the connection to the client.
     */
    fun close() {
        isRunning = false
        try { clientChannel.close() } catch (ignored: Exception) {}
    }
} 