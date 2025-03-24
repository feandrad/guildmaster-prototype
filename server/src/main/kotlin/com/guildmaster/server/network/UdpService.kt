package com.guildmaster.server.network

import com.guildmaster.server.Logger
import com.guildmaster.server.session.Response
import com.guildmaster.server.session.SessionManager
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class UdpService(
    private val sessionManager: SessionManager,
    private val broadcaster: Broadcaster,
    private val port: Int
) {
    private var isRunning = false
    private lateinit var channel: DatagramChannel
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (isRunning) return

        try {
            initializeChannel()
            isRunning = true
            Logger.info { "UDP service started on port $port" }

            startListener()
        } catch (e: Exception) {
            Logger.error(e) { "Failed to start UDP service" }
            stop()
        }
    }

    private fun initializeChannel() {
        channel = DatagramChannel.open().apply {
            configureBlocking(true)
            socket().bind(InetSocketAddress(port))
        }
    }

    private fun startListener() {
        executor.submit {
            try {
                val buffer = ByteArray(1024)
                while (isRunning) {
                    val sender = channel.receive(ByteBuffer.wrap(buffer)) as InetSocketAddress
                    handlePacket(sender, buffer)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Logger.error(e) { "UDP listener error" }
                }
            }
        }
    }

    private fun handlePacket(sender: InetSocketAddress, data: ByteArray) {
        try {
            val message = String(data).trim()

            when {
                message.startsWith(Protocol.CMD_POS) -> handlePositionUpdate(sender, message)
//                message.startsWith(Protocol.CMD_ACTION) -> handleActionPacket(sender, message)
                message.startsWith(Protocol.CMD_PING) -> sendPacket(sender, "${Protocol.MSG_PONG}\n")
                message.startsWith(Protocol.CMD_UDP_REGISTER) -> handleUdpRegistration(sender, message)
            }
        } catch (e: Exception) {
            Logger.error(e) { "Error processing UDP packet from $sender" }
        }
    }

    private fun handlePositionUpdate(sender: InetSocketAddress, message: String) {
        try {
            val data = Protocol.json.decodeFromString<Protocol.PositionMessage>(
                message.substring(Protocol.CMD_POS.length).trim()
            )

            when (val sessionResult = sessionManager.getSessionByToken(data.token)) {
                is Response.Success -> {
                    val session = sessionResult.data
                    sessionManager.updateMap(session.player.id, data.mapId)
                    broadcaster.broadcastPositionUpdate(session.player.id, data.position, data.mapId, session.token)
                }

                is Response.Error -> {
                    Logger.warn { "Invalid token for position update from $sender: ${sessionResult.message}" }
                }
            }
        } catch (e: Exception) {
            Logger.error(e) { "Error handling position update from $sender" }
        }
    }

    private fun handleActionPacket(sender: InetSocketAddress, message: String) {
        try {
            val data = Protocol.json.decodeFromString<Protocol.ActionMessage>(
                message.substring(Protocol.CMD_ACTION.length).trim()
            )

            when (val sessionResult = sessionManager.getSessionByToken(data.token)) {
                is Response.Success -> {
                    val session = sessionResult.data
                    broadcaster.broadcastAction(session.player.id, data.action, data.data, session.token)
                }

                is Response.Error -> {
                    Logger.warn { "Invalid token for action from $sender: ${sessionResult.message}" }
                }
            }
        } catch (e: Exception) {
            Logger.error(e) { "Error handling action packet from $sender" }
        }
    }

    private fun handleUdpRegistration(sender: InetSocketAddress, message: String) {
        try {
            val data = Protocol.json.decodeFromString<Protocol.UdpRegisterMessage>(
                message.substring(Protocol.CMD_UDP_REGISTER.length).trim()
            )

            when (val sessionResult = sessionManager.getSessionByToken(data.token)) {
                is Response.Success -> {
                    val session = sessionResult.data
                    when (val result = sessionManager.updateUdpAddress(session.player.id, sender)) {
                        is Response.Success -> {
                            Logger.info { "UDP address registered for session ${session.player.id}" }
                            sendPacket(sender, "${Protocol.MSG_UDP_REG}\n")
                        }

                        is Response.Error -> {
                            Logger.warn { "Failed to register UDP address: ${result.message}" }
                        }
                    }
                }

                is Response.Error -> {
                    Logger.warn { "Invalid token for UDP registration from $sender: ${sessionResult.message}" }
                }
            }
        } catch (e: Exception) {
            Logger.error(e) { "Error handling UDP registration from $sender" }
        }
    }

    fun sendPacket(target: InetSocketAddress, message: String) {
        try {
            channel.send(
                ByteBuffer.wrap(message.toByteArray()),
                target
            )
        } catch (e: Exception) {
            Logger.error(e) { "Error sending UDP packet to $target" }
        }
    }

    fun stop() {
        if (!isRunning) return

        isRunning = false
        Logger.info { "Stopping UDP service..." }

        try {
            channel.close()
            executor.shutdown()

            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }

            Logger.info { "UDP service stopped" }
        } catch (e: Exception) {
            Logger.error(e) { "Error stopping UDP service" }
        }
    }
} 