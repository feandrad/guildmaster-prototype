package com.guildmaster.server.network

import com.guildmaster.server.Logger
import com.guildmaster.server.session.SessionManager
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TcpService(
    private val sessionManager: SessionManager,
    private val port: Int,
) {
    private var isRunning = false
    private lateinit var channel: ServerSocketChannel
    private val executor = Executors.newCachedThreadPool()

    // Thread-safe if multiple threads add/remove
    private val clients = CopyOnWriteArrayList<TcpClientHandler>()

    fun start() {
        if (isRunning) return

        try {
            channel = ServerSocketChannel.open().apply {
                configureBlocking(true)
                socket().bind(InetSocketAddress(port))
            }
            isRunning = true
            Logger.info { "TCP service started on port $port" }

            startListener()
        } catch (e: Exception) {
            Logger.error(e) { "Failed to start TCP service" }
            stop()
            throw e
        }
    }

    private fun startListener() {
        executor.submit {
            try {
                while (isRunning) {
                    val clientChannel = channel.accept() ?: continue
                    handleNewConnection(clientChannel)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Logger.error(e) { "TCP listener error" }
                }
            }
        }
    }

    private fun handleNewConnection(clientChannel: SocketChannel) {
        val clientAddress = clientChannel.remoteAddress as InetSocketAddress

        // Pass references that TcpClientHandler needs
        val client = TcpClientHandler(
            sessionManager = sessionManager,
            tcpService = this,
            channel = clientChannel,
            clientAddress = clientAddress
        )
        clients.add(client)

        Logger.info { "New TCP client connected: $clientAddress" }
        executor.submit(client)
    }

    fun sendMessage(target: InetSocketAddress, message: String) {
        try {
            clients.find { it.clientAddress == target }?.sendMessage(message)
        } catch (e: Exception) {
            Logger.error(e) { "Error sending message to $target" }
        }
    }

    fun removeClient(client: TcpClientHandler) {
        clients.remove(client)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        Logger.info { "Stopping TCP service..." }
        try {
            channel.close()
            clients.forEach { it.close() }
            clients.clear()

            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }

            Logger.info { "TCP service stopped" }
        } catch (e: Exception) {
            Logger.error(e) { "Error stopping TCP service" }
        }
    }
}
