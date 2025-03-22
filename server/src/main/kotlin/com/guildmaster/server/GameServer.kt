package com.guildmaster.server

import com.guildmaster.server.network.Protocol
import com.guildmaster.server.session.SessionManager
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Main game server, responsible for managing TCP and UDP connections.
 */
class GameServer(
    private val tcpPort: Int = 9999,
    private val udpPort: Int = 9998
) {
    private val logger = KotlinLogging.logger {}
    
    // Session manager
    private val sessionManager = SessionManager()
    
    // List of connected TCP clients
    private val clients = CopyOnWriteArrayList<TcpClientHandler>()
    
    // Network channels
    private var tcpServerChannel: ServerSocketChannel? = null
    private var udpChannel: DatagramChannel? = null
    
    // Thread pools
    private val tcpExecutor = Executors.newCachedThreadPool()
    private val udpExecutor = Executors.newSingleThreadExecutor()
    
    // Control flags
    @Volatile
    private var isRunning = false
    
    /**
     * Start the server.
     */
    fun start() {
        if (isRunning) {
            logger.warn { "Server is already running." }
            return
        }
        
        try {
            // Initialize TCP server
            tcpServerChannel = ServerSocketChannel.open().apply {
                socket().bind(InetSocketAddress(tcpPort))
                configureBlocking(true)
            }
            
            // Initialize UDP server
            udpChannel = DatagramChannel.open().apply {
                socket().bind(InetSocketAddress(udpPort))
                configureBlocking(true)
            }
            
            isRunning = true
            logger.info { "Server started - TCP on port $tcpPort, UDP on port $udpPort" }
            
            // Start processing TCP connections
            startTcpListener()
            
            // Start processing UDP packets
            startUdpListener()
            
        } catch (e: Exception) {
            logger.error(e) { "Error starting server" }
            stop()
        }
    }
    
    /**
     * Start the TCP listener to accept new connections.
     */
    private fun startTcpListener() {
        thread(name = "tcp-listener") {
            try {
                logger.info { "TCP listener started, waiting for connections..." }
                
                while (isRunning) {
                    val clientChannel = tcpServerChannel?.accept() ?: continue
                    handleNewTcpConnection(clientChannel)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    logger.error(e) { "Error in TCP listener" }
                }
            }
        }
    }
    
    /**
     * Process a new TCP connection.
     */
    private fun handleNewTcpConnection(clientChannel: SocketChannel) {
        try {
            clientChannel.configureBlocking(true)
            val clientAddress = clientChannel.remoteAddress as InetSocketAddress
            
            logger.info { "New TCP connection from $clientAddress" }
            
            val clientHandler = TcpClientHandler(clientChannel, clientAddress, this)
            clients.add(clientHandler)
            
            // Process the client in a separate thread
            tcpExecutor.execute(clientHandler)
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing new TCP connection" }
            try { clientChannel.close() } catch (ignored: Exception) {}
        }
    }
    
    /**
     * Start the UDP listener to receive packets.
     */
    private fun startUdpListener() {
        udpExecutor.execute {
            try {
                logger.info { "UDP listener started, waiting for packets..." }
                
                val buffer = ByteBuffer.allocate(1024)
                
                while (isRunning) {
                    buffer.clear()
                    val sender = udpChannel?.receive(buffer) as? InetSocketAddress ?: continue
                    
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
    
    /**
     * Process a received UDP packet.
     */
    private fun handleUdpPacket(sender: InetSocketAddress, data: ByteArray) {
        try {
            val message = String(data).trim()
            
            logger.debug { "UDP packet received from $sender: $message" }
            
            if (message.startsWith(Protocol.CMD_POS)) {
                handlePositionUpdate(sender, message)
            } else if (message.startsWith(Protocol.CMD_ACTION)) {
                handleActionPacket(sender, message)
            } else if (message.startsWith(Protocol.CMD_PING)) {
                sendUdpPacket(sender, "${Protocol.MSG_PONG}\n")
            } else if (message.startsWith(Protocol.CMD_UDP_REGISTER)) {
                handleUdpRegistration(sender, message)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP packet from $sender" }
        }
    }
    
    /**
     * Handle UDP registration to associate UDP address with TCP session
     */
    private fun handleUdpRegistration(sender: InetSocketAddress, message: String) {
        try {
            // Get the JSON part of the message
            val jsonPart = message.substringAfter(" ", "")
            if (jsonPart.isBlank()) {
                logger.warn { "Invalid UDP registration message format from $sender" }
                return
            }
            
            // Parse the registration message
            val regData = Protocol.json.decodeFromString<Protocol.UdpRegisterMessage>(jsonPart)
            val playerId = regData.id
            
            // Find the session by player ID
            val session = sessionManager.getSessionById(playerId)
            if (session == null) {
                logger.warn { "Session not found for player ID $playerId during UDP registration" }
                return
            }
            
            // Associate the UDP address with the session
            sessionManager.updateUdpAddress(session.player.id, sender)
            logger.info { "UDP address $sender registered for player ${session.player.name} (${session.player.id})" }
            
            // Acknowledge registration
            sendUdpPacket(sender, "UDP_REGISTERED\n")
            
            // After UDP registration, make sure we send a new position update to the client
            // This ensures they have their assigned position
            val positionMessage = Protocol.PositionMessage(
                playerId = session.player.id,
                x = session.player.x,
                y = session.player.y,
                mapId = session.player.currentMapId
            )
            
            val posMsg = Protocol.encodePositionMessage(positionMessage)
            sendUdpPacket(sender, "$posMsg\n")
            logger.debug { "Sent initial position after UDP registration: (${session.player.x}, ${session.player.y})" }
            
            // Also send a full player list update via TCP
            sessionManager.getSessionByTcpAddress(session.tcpAddress!!)?.let { tcpSession ->
                val tcpClient = clients.find { it.sessionId == tcpSession.player.id }
                if (tcpClient != null) {
                    val allPlayers = sessionManager.getSessionsInMap(session.player.currentMapId)
                    val playersMessage = Protocol.createPlayersListMessage(allPlayers)
                    tcpClient.sendMessage(playersMessage)
                    logger.debug { "Sent updated player list to ${session.player.name} after UDP registration" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP registration from $sender" }
        }
    }
    
    /**
     * Process a position update via UDP.
     */
    private fun handlePositionUpdate(sender: InetSocketAddress, positionMessage: String) {
        try {
            val session = sessionManager.getSessionByUdpAddress(sender)
            if (session == null) {
                logger.warn { "Session not found for UDP address $sender" }
                return
            }
            
            // Get the JSON part of the message
            val jsonPart = positionMessage.substringAfter(" ", "")
            if (jsonPart.isBlank()) return
            
            // Deserialize the position message
            val posData = Protocol.json.decodeFromString<Protocol.PositionMessage>(jsonPart)
            
            // Update the player's position
            sessionManager.updatePosition(session.player.id, posData.x, posData.y)
            
            // Propagate the update to other players on the same map
            val playersInMap = sessionManager.getSessionsInMap(session.player.currentMapId)
            
            for (otherPlayer in playersInMap) {
                if (otherPlayer.player.id != session.player.id && otherPlayer.udpAddress != null) {
                    sendUdpPacket(otherPlayer.udpAddress!!, positionMessage + "\n")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP position update" }
        }
    }
    
    /**
     * Process a player action via UDP.
     */
    private fun handleActionPacket(sender: InetSocketAddress, actionMessage: String) {
        try {
            val session = sessionManager.getSessionByUdpAddress(sender)
            if (session == null) {
                logger.warn { "Session not found for UDP address $sender" }
                return
            }
            
            // Propagate the action to other players on the same map
            val playersInMap = sessionManager.getSessionsInMap(session.player.currentMapId)
            
            for (otherPlayer in playersInMap) {
                if (otherPlayer.player.id != session.player.id && otherPlayer.udpAddress != null) {
                    sendUdpPacket(otherPlayer.udpAddress!!, actionMessage + "\n")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP action" }
        }
    }
    
    /**
     * Send a UDP packet to a client.
     */
    fun sendUdpPacket(target: InetSocketAddress, message: String) {
        try {
            val buffer = ByteBuffer.wrap(message.toByteArray())
            udpChannel?.send(buffer, target)
        } catch (e: Exception) {
            logger.error(e) { "Error sending UDP packet to $target" }
        }
    }
    
    /**
     * Process TCP commands from clients
     */
    fun handleTcpCommand(client: TcpClientHandler, command: String) {
        logger.debug { "Processing TCP command: $command from ${client.clientAddress}" }
        
        try {
            when {
                command.startsWith(Protocol.CMD_CONNECT) -> {
                    handleConnectCommand(client, command)
                }
                command.startsWith(Protocol.CMD_CONFIG) -> {
                    handleConfigCommand(client, command)
                }
                command.startsWith(Protocol.CMD_MAP) -> {
                    handleMapCommand(client, command)
                }
                command.startsWith(Protocol.CMD_CHAT) -> {
                    handleChatCommand(client, command)
                }
                command.startsWith(Protocol.CMD_PING) -> {
                    handlePingCommand(client, command)
                }
                else -> {
                    logger.warn { "Unknown TCP command: $command" }
                    client.sendMessage("ERROR Unknown command")
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing command: $command" }
            client.sendMessage("ERROR Internal server error")
        }
    }
    
    /**
     * Process the CONNECT command.
     */
    private fun handleConnectCommand(client: TcpClientHandler, command: String) {
        try {
            // Get the JSON part of the message
            val jsonPart = command.substringAfter(" ", "")
            if (jsonPart.isBlank()) {
                logger.warn { "Invalid connect command format from ${client.clientAddress}" }
                client.sendMessage("ERROR Invalid connect command format")
                return
            }
            
            // Parse the connect message
            val connectMsg = Protocol.json.decodeFromString<Protocol.ConnectMessage>(jsonPart)
            val playerName = connectMsg.name
            val playerColor = connectMsg.color
            
            // Log connection request
            logger.debug { "Connect request from ${client.clientAddress} with name: $playerName, color: $playerColor" }
            
            // Make sure the player name isn't empty
            if (playerName.isBlank()) {
                logger.warn { "Invalid player name from ${client.clientAddress}" }
                client.sendMessage("ERROR Invalid player name")
                return
            }
            
            // Create a new session for this player
            val session = sessionManager.createSession(playerName, playerColor)
            
            // Associate the TCP client with this session
            client.sessionId = session.player.id
            
            // Associate the TCP address with the session
            session.tcpAddress = client.clientAddress
            sessionManager.associateTcpAddress(client.clientAddress, session.player.id)
            
            // Log successful connection
            logger.info { "Player connected: $playerName (${session.player.id}) with color $playerColor" }
            
            // Send configuration message back to client
            val configMessage = Protocol.ConfigMessage(
                id = session.player.id,
                udpPort = udpPort,
                color = session.player.color,
                mapId = session.player.currentMapId
            )
            val configMsg = Protocol.encodeConfigMessage(configMessage)
            logger.debug { "Sending config message to client: $configMsg" }
            client.sendMessage(configMsg)
            
            // Notify all clients of player list update (with a small delay to prevent overwhelming connections)
            GlobalScope.launch {
                delay(100) // Small delay to allow client processing
                broadcastPlayersList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing connect command from ${client.clientAddress}" }
            client.sendMessage("ERROR Internal server error during connection")
        }
    }
    
    /**
     * Process the CONFIG command.
     */
    private fun handleConfigCommand(client: TcpClientHandler, command: String) {
        try {
            // Get the session associated with this client
            val session = sessionManager.getSessionByTcpAddress(client.clientAddress)
            if (session == null) {
                client.sendMessage("ERROR Session not found")
                return
            }
            
            // Process UDP configuration (to map UDP address to player)
            val jsonPart = command.substringAfter(" ", "")
            if (jsonPart.isBlank()) return
            
            val configData = Protocol.json.decodeFromString<Protocol.ConfigMessage>(jsonPart)
            
            // Verify session ID is valid
            if (configData.id != session.player.id) {
                client.sendMessage("ERROR Invalid player ID")
                return
            }
            
            // Update player configuration
            if (configData.color?.isNotBlank() == true) {
                session.player.color = configData.color
            }
            
            if (configData.mapId?.isNotBlank() == true && configData.mapId != session.player.currentMapId) {
                sessionManager.updateMap(session.player.id, configData.mapId)
            }
            
            client.sendMessage("CONFIG_OK")
            logger.debug { "Configuration updated for ${session.player.name}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing CONFIG command" }
            client.sendMessage("ERROR Failed to configure")
        }
    }
    
    /**
     * Process the MAP command.
     */
    private fun handleMapCommand(client: TcpClientHandler, command: String) {
        try {
            // Get the session associated with this client
            val session = sessionManager.getSessionByTcpAddress(client.clientAddress)
            if (session == null) {
                client.sendMessage("ERROR Session not found")
                return
            }
            
            // Process map change
            val jsonPart = command.substringAfter(" ", "")
            if (jsonPart.isBlank()) return
            
            val mapData = Protocol.json.decodeFromString<Protocol.MapChangeMessage>(jsonPart)
            
            // Update player's map
            val oldMapId = session.player.currentMapId
            sessionManager.updateMap(session.player.id, mapData.mapId)
            
            // Notify players on affected maps
            notifyMapChange(oldMapId)
            notifyMapChange(mapData.mapId)
            
            logger.info { "Player ${session.player.name} changed from map $oldMapId to ${mapData.mapId}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing MAP command" }
            client.sendMessage("ERROR Failed to change map")
        }
    }
    
    /**
     * Process the CHAT command.
     */
    private fun handleChatCommand(client: TcpClientHandler, command: String) {
        try {
            // Get the session associated with this client
            val session = sessionManager.getSessionByTcpAddress(client.clientAddress)
            if (session == null) {
                client.sendMessage("ERROR Session not found")
                return
            }
            
            // Process chat message
            val jsonPart = command.substringAfter(" ", "")
            if (jsonPart.isBlank()) return
            
            val chatData = Protocol.json.decodeFromString<Protocol.ChatMessage>(jsonPart)
            
            // Verify session ID is valid
            if (chatData.playerId != session.player.id) {
                client.sendMessage("ERROR Invalid player ID")
                return
            }
            
            // Propagate the message to all players on the same map
            val playersInMap = sessionManager.getSessionsInMap(session.player.currentMapId)
            
            val chatMessage = Protocol.encodeChatMessage(
                Protocol.ChatMessage(
                    playerId = session.player.id,
                    playerName = session.player.name,
                    text = chatData.text
                )
            )
            
            for (player in playersInMap) {
                val playerClient = clients.find { it.clientAddress == player.tcpAddress }
                playerClient?.sendMessage(chatMessage)
            }
            
            logger.debug { "Chat message from ${session.player.name}: ${chatData.text}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing CHAT command" }
            client.sendMessage("ERROR Failed to send message")
        }
    }
    
    /**
     * Process the PING command.
     */
    private fun handlePingCommand(client: TcpClientHandler, command: String) {
        try {
            // Send PONG response - used for connection keepalive
            client.sendMessage(Protocol.MSG_PONG)
        } catch (e: Exception) {
            logger.error(e) { "Error processing PING command" }
            client.sendMessage("ERROR Failed to respond to PING")
        }
    }
    
    /**
     * Notify players on a map about changes.
     */
    private fun notifyMapChange(mapId: String) {
        val playersInMap = sessionManager.getSessionsInMap(mapId)
        
        // Create message with list of player IDs on the map
        val mapMessage = Protocol.encodeMapChangeMessage(
            Protocol.MapChangeMessage(
                mapId = mapId,
                playerIds = playersInMap.map { it.player.id }
            )
        )
        
        // Send to all players on the map
        for (player in playersInMap) {
            val playerClient = clients.find { it.clientAddress == player.tcpAddress }
            playerClient?.sendMessage(mapMessage)
        }
    }
    
    /**
     * Send the player list to all connected clients.
     */
    fun broadcastPlayersList() {
        val players = sessionManager.getAllSessions()
        val playersMessage = Protocol.createPlayersListMessage(players)
        
        for (client in clients) {
            client.sendMessage(playersMessage)
        }
    }
    
    /**
     * Called when a TCP client disconnects.
     */
    fun handleClientDisconnect(client: TcpClientHandler) {
        clients.remove(client)
        
        val session = sessionManager.getSessionByTcpAddress(client.clientAddress)
        if (session != null) {
            val mapId = session.player.currentMapId
            sessionManager.removeSession(session.player.id)
            
            // Notify remaining players
            broadcastPlayersList()
            notifyMapChange(mapId)
            
            logger.info { "Client disconnected: ${session.player.name} (${client.clientAddress})" }
        } else {
            logger.info { "Client disconnected: ${client.clientAddress}" }
        }
    }
    
    /**
     * Stop the server.
     */
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        logger.info { "Shutting down server..." }
        
        // Close all TCP connections
        clients.forEach { it.close() }
        clients.clear()
        
        // Close network channels
        try { tcpServerChannel?.close() } catch (ignored: Exception) {}
        try { udpChannel?.close() } catch (ignored: Exception) {}
        
        // Shutdown executors
        tcpExecutor.shutdown()
        udpExecutor.shutdown()
        
        // Shutdown session manager
        sessionManager.shutdown()
        
        logger.info { "Server shutdown complete" }
    }
}

/**
 * Handler for TCP clients.
 */
class TcpClientHandler(
    private val clientChannel: SocketChannel,
    val clientAddress: InetSocketAddress,
    private val server: GameServer
) : Runnable {
    private val logger = KotlinLogging.logger {}
    private val buffer = ByteBuffer.allocate(1024)
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
    
    /**
     * Process received data, splitting by lines.
     */
    private fun processIncomingData(data: String) {
        incomingData.append(data)
        
        // Process complete lines
        var newlinePos: Int
        while (incomingData.indexOf('\n').also { newlinePos = it } != -1) {
            val line = incomingData.substring(0, newlinePos).trim()
            incomingData.delete(0, newlinePos + 1)
            
            if (line.isNotEmpty()) {
                server.handleTcpCommand(this, line)
            }
        }
    }
    
    /**
     * Send a message to the client.
     */
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