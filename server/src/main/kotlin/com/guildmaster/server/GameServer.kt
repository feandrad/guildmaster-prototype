package com.guildmaster.server

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
    private val tcpClients = CopyOnWriteArrayList<TcpClientHandler>()
    
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
            tcpClients.add(clientHandler)
            
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
            
            if (message.startsWith(Protocol.MSG_POS)) {
                handlePositionUpdate(sender, message)
            } else if (message.startsWith(Protocol.MSG_ACTION)) {
                handleActionPacket(sender, message)
            } else if (message.startsWith(Protocol.MSG_PING)) {
                sendUdpPacket(sender, "${Protocol.MSG_PONG}\n")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing UDP packet from $sender" }
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
            sessionManager.updatePosition(session.id, posData.x, posData.y)
            
            // Propagate the update to other players on the same map
            val playersInMap = sessionManager.getSessionsInMap(session.currentMapId)
            
            for (otherPlayer in playersInMap) {
                if (otherPlayer.id != session.id && otherPlayer.udpAddress != null) {
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
            val playersInMap = sessionManager.getSessionsInMap(session.currentMapId)
            
            for (otherPlayer in playersInMap) {
                if (otherPlayer.id != session.id && otherPlayer.udpAddress != null) {
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
     * Process a received TCP command.
     */
    fun handleTcpCommand(client: TcpClientHandler, command: String) {
        logger.debug { "TCP command from ${client.clientAddress}: $command" }
        
        try {
            when {
                command.startsWith(Protocol.MSG_CONNECT) -> {
                    handleConnectCommand(client, command)
                }
                command.startsWith(Protocol.MSG_CONFIG) -> {
                    handleConfigCommand(client, command)
                }
                command.startsWith(Protocol.MSG_MAP) -> {
                    handleMapChangeCommand(client, command)
                }
                command.startsWith(Protocol.MSG_CHAT) -> {
                    handleChatCommand(client, command)
                }
                else -> {
                    logger.warn { "Unknown TCP command: $command" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing TCP command: $command" }
        }
    }
    
    /**
     * Process the CONNECT command.
     */
    private fun handleConnectCommand(client: TcpClientHandler, command: String) {
        try {
            // Original format: "CONNECT name"
            val playerName = command.substringAfter(" ").trim()
            
            if (playerName.isBlank()) {
                client.sendMessage("ERROR Invalid player name")
                return
            }
            
            // Create the session
            val session = sessionManager.createSession(playerName, client.clientAddress)
            
            // Notify the client about its configuration
            val configMsg = Protocol.encodeConfigMessage(
                Protocol.ConfigMessage(
                    playerId = session.id,
                    color = session.color,
                    mapId = session.currentMapId
                )
            )
            client.sendMessage(configMsg)
            
            // Send updated player list to everyone
            broadcastPlayersList()
            
            logger.info { "Player connected: ${session.name} (${session.id})" }
        } catch (e: Exception) {
            logger.error(e) { "Error processing CONNECT command" }
            client.sendMessage("ERROR Failed to connect")
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
            if (configData.playerId != session.id) {
                client.sendMessage("ERROR Invalid player ID")
                return
            }
            
            // Update player configuration
            if (configData.color.isNotBlank()) {
                session.color = configData.color
            }
            
            if (configData.mapId.isNotBlank() && configData.mapId != session.currentMapId) {
                sessionManager.updateMap(session.id, configData.mapId)
            }
            
            client.sendMessage("CONFIG_OK")
            logger.debug { "Configuration updated for ${session.name}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing CONFIG command" }
            client.sendMessage("ERROR Failed to configure")
        }
    }
    
    /**
     * Process the MAP command.
     */
    private fun handleMapChangeCommand(client: TcpClientHandler, command: String) {
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
            val oldMapId = session.currentMapId
            sessionManager.updateMap(session.id, mapData.mapId)
            
            // Notify players on affected maps
            notifyMapChange(oldMapId)
            notifyMapChange(mapData.mapId)
            
            logger.info { "Player ${session.name} changed from map $oldMapId to ${mapData.mapId}" }
            
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
            if (chatData.playerId != session.id) {
                client.sendMessage("ERROR Invalid player ID")
                return
            }
            
            // Propagate the message to all players on the same map
            val playersInMap = sessionManager.getSessionsInMap(session.currentMapId)
            
            val chatMessage = Protocol.encodeChatMessage(
                Protocol.ChatMessage(
                    playerId = session.id,
                    playerName = session.name,
                    text = chatData.text
                )
            )
            
            for (player in playersInMap) {
                val playerClient = tcpClients.find { it.clientAddress == player.tcpAddress }
                playerClient?.sendMessage(chatMessage)
            }
            
            logger.debug { "Chat message from ${session.name}: ${chatData.text}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing CHAT command" }
            client.sendMessage("ERROR Failed to send message")
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
                playerIds = playersInMap.map { it.id }
            )
        )
        
        // Send to all players on the map
        for (player in playersInMap) {
            val playerClient = tcpClients.find { it.clientAddress == player.tcpAddress }
            playerClient?.sendMessage(mapMessage)
        }
    }
    
    /**
     * Send the player list to all connected clients.
     */
    fun broadcastPlayersList() {
        val players = sessionManager.getPlayersList()
        val playersMessage = Protocol.createPlayersListMessage(players)
        
        for (client in tcpClients) {
            client.sendMessage(playersMessage)
        }
    }
    
    /**
     * Called when a TCP client disconnects.
     */
    fun handleClientDisconnect(client: TcpClientHandler) {
        tcpClients.remove(client)
        
        val session = sessionManager.getSessionByTcpAddress(client.clientAddress)
        if (session != null) {
            val mapId = session.currentMapId
            sessionManager.removeSession(session.id)
            
            // Notify remaining players
            broadcastPlayersList()
            notifyMapChange(mapId)
            
            logger.info { "Client disconnected: ${session.name} (${client.clientAddress})" }
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
        tcpClients.forEach { it.close() }
        tcpClients.clear()
        
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