#include "network.h"
#include <iostream>
#include <sstream>
#include <cstring>

// Constructor
NetworkClient::NetworkClient() {
    // Initialize members
    status = ConnectionStatus::DISCONNECTED;
    statusMessage = "Not connected";
}

// Destructor
NetworkClient::~NetworkClient() {
    disconnect();
}

// Initialize networking
bool NetworkClient::initialize() {
#ifdef _WIN32
    WSADATA wsa_data;
    if (WSAStartup(MAKEWORD(2, 2), &wsa_data) != 0) {
        statusMessage = "Failed to initialize Winsock";
        return false;
    }
#endif
    return true;
}

// Connect to server
bool NetworkClient::connect(const std::string& serverAddress, int tcpPort, int udpPort) {
    // Return if already connecting or connected
    if (status == ConnectionStatus::CONNECTING || status == ConnectionStatus::CONNECTED) {
        return true;
    }
    
    status = ConnectionStatus::CONNECTING;
    statusMessage = "Connecting to server...";
    
    // Create TCP socket
    tcpSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (!ISVALIDSOCKET(tcpSocket)) {
        statusMessage = "Failed to create TCP socket";
        status = ConnectionStatus::CONNECTION_FAILED;
        return false;
    }
    
    // Create UDP socket
    udpSocket = socket(AF_INET, SOCK_DGRAM, 0);
    if (!ISVALIDSOCKET(udpSocket)) {
        statusMessage = "Failed to create UDP socket";
        CLOSESOCKET(tcpSocket);
        tcpSocket = -1;
        status = ConnectionStatus::CONNECTION_FAILED;
        return false;
    }
    
    // Make sockets non-blocking
    if (!setSocketNonBlocking(tcpSocket) || !setSocketNonBlocking(udpSocket)) {
        statusMessage = "Failed to set non-blocking mode";
        CLOSESOCKET(tcpSocket);
        CLOSESOCKET(udpSocket);
        tcpSocket = -1;
        udpSocket = -1;
        status = ConnectionStatus::CONNECTION_FAILED;
        return false;
    }
    
    // Set up TCP server address
    memset(&serverTcpAddr, 0, sizeof(serverTcpAddr));
    serverTcpAddr.sin_family = AF_INET;
    serverTcpAddr.sin_port = htons(tcpPort);
    
    // Set up UDP server address
    memset(&serverUdpAddr, 0, sizeof(serverUdpAddr));
    serverUdpAddr.sin_family = AF_INET;
    serverUdpAddr.sin_port = htons(udpPort);
    
    // Convert IP address
    if (inet_pton(AF_INET, serverAddress.c_str(), &serverTcpAddr.sin_addr) <= 0 ||
        inet_pton(AF_INET, serverAddress.c_str(), &serverUdpAddr.sin_addr) <= 0) {
        statusMessage = "Invalid server address";
        CLOSESOCKET(tcpSocket);
        CLOSESOCKET(udpSocket);
        tcpSocket = -1;
        udpSocket = -1;
        status = ConnectionStatus::CONNECTION_FAILED;
        return false;
    }
    
    // Connect TCP socket
    if (::connect(tcpSocket, (struct sockaddr*)&serverTcpAddr, sizeof(serverTcpAddr)) == SOCKET_ERROR) {
        if (GETSOCKETERRNO() != EINPROGRESS && GETSOCKETERRNO() != EWOULDBLOCK
#ifdef _WIN32
            && GETSOCKETERRNO() != WSAEWOULDBLOCK
#endif
        ) {
            statusMessage = "Failed to connect to server: " + std::to_string(GETSOCKETERRNO());
            CLOSESOCKET(tcpSocket);
            CLOSESOCKET(udpSocket);
            tcpSocket = -1;
            udpSocket = -1;
            status = ConnectionStatus::CONNECTION_FAILED;
            return false;
        }
    }
    
    // Connection initiated successfully
    status = ConnectionStatus::CONNECTED;
    statusMessage = "Connected to server";
    return true;
}

// Disconnect from server
void NetworkClient::disconnect() {
    // Close sockets
    if (ISVALIDSOCKET(tcpSocket)) {
        CLOSESOCKET(tcpSocket);
        tcpSocket = -1;
    }
    
    if (ISVALIDSOCKET(udpSocket)) {
        CLOSESOCKET(udpSocket);
        udpSocket = -1;
    }
    
    // Clean up Windows sockets
#ifdef _WIN32
    WSACleanup();
#endif
    
    // Reset state
    status = ConnectionStatus::DISCONNECTED;
    statusMessage = "Disconnected";
    players.clear();
    playerId = "";
    playerColor = "";
    tcpBuffer.clear();
}

// Set socket to non-blocking mode
bool NetworkClient::setSocketNonBlocking(socket_t socket) {
    if (!ISVALIDSOCKET(socket)) return false;
    
#ifdef _WIN32
    u_long mode = 1;
    return (ioctlsocket(socket, FIONBIO, &mode) == 0);
#else
    int flags = fcntl(socket, F_GETFL, 0);
    if (flags == -1) return false;
    return (fcntl(socket, F_SETFL, flags | O_NONBLOCK) == 0);
#endif
}

// Send connect request
bool NetworkClient::sendConnectRequest(const std::string& playerName) {
    if (status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    std::string connectMessage = "CONNECT " + playerName;
    return sendTcpMessage(connectMessage);
}

// Send position update
bool NetworkClient::sendPositionUpdate(float x, float y) {
    if (status != ConnectionStatus::CONNECTED || playerId.empty()) {
        return false;
    }
    
    std::stringstream ss;
    ss << "POS {\"playerId\":\"" << playerId << "\",\"x\":" << x << ",\"y\":" << y << "}";
    return sendUdpMessage(ss.str());
}

// Send chat message
bool NetworkClient::sendChatMessage(const std::string& message) {
    if (status != ConnectionStatus::CONNECTED || playerId.empty()) {
        return false;
    }
    
    std::stringstream ss;
    ss << "CHAT {\"playerId\":\"" << playerId << "\",\"text\":\"" << message << "\"}";
    return sendTcpMessage(ss.str());
}

// Send map change
bool NetworkClient::sendMapChange(const std::string& mapId) {
    if (status != ConnectionStatus::CONNECTED || playerId.empty()) {
        return false;
    }
    
    std::stringstream ss;
    ss << "MAP {\"mapId\":\"" << mapId << "\"}";
    return sendTcpMessage(ss.str());
}

// Send TCP message
bool NetworkClient::sendTcpMessage(const std::string& message) {
    if (!ISVALIDSOCKET(tcpSocket) || status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    std::string fullMessage = message + "\n";
    ssize_t bytesSent = send(tcpSocket, fullMessage.c_str(), fullMessage.length(), 0);
    
    if (bytesSent == SOCKET_ERROR) {
        statusMessage = "Failed to send TCP message: " + std::to_string(GETSOCKETERRNO());
        return false;
    }
    
    return true;
}

// Send UDP message
bool NetworkClient::sendUdpMessage(const std::string& message) {
    if (!ISVALIDSOCKET(udpSocket) || status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    std::string fullMessage = message + "\n";
    ssize_t bytesSent = sendto(udpSocket, fullMessage.c_str(), fullMessage.length(), 0,
                              (struct sockaddr*)&serverUdpAddr, sizeof(serverUdpAddr));
    
    if (bytesSent == SOCKET_ERROR) {
        statusMessage = "Failed to send UDP message: " + std::to_string(GETSOCKETERRNO());
        return false;
    }
    
    return true;
}

// Check for and process TCP messages
void NetworkClient::checkTcpMessages() {
    if (!ISVALIDSOCKET(tcpSocket) || status != ConnectionStatus::CONNECTED) {
        return;
    }
    
    char buffer[4096];
    ssize_t bytesRead = recv(tcpSocket, buffer, sizeof(buffer) - 1, 0);
    
    if (bytesRead > 0) {
        // Add received data to buffer
        buffer[bytesRead] = '\0';
        tcpBuffer += buffer;
        
        // Process complete messages
        size_t pos;
        while ((pos = tcpBuffer.find('\n')) != std::string::npos) {
            std::string message = tcpBuffer.substr(0, pos);
            tcpBuffer.erase(0, pos + 1);
            
            // Process the message
            processServerMessage(message);
        }
    }
    else if (bytesRead == 0) {
        // Connection closed by server
        status = ConnectionStatus::DISCONNECTED;
        statusMessage = "Server closed the connection";
    }
    else {
        // No data or would block
        if (GETSOCKETERRNO() != EWOULDBLOCK && GETSOCKETERRNO() != EAGAIN
#ifdef _WIN32
            && GETSOCKETERRNO() != WSAEWOULDBLOCK
#endif
        ) {
            status = ConnectionStatus::DISCONNECTED;
            statusMessage = "TCP connection error: " + std::to_string(GETSOCKETERRNO());
        }
    }
}

// Check for and process UDP messages
void NetworkClient::checkUdpMessages() {
    if (!ISVALIDSOCKET(udpSocket) || status != ConnectionStatus::CONNECTED) {
        return;
    }
    
    char buffer[4096];
    struct sockaddr_in senderAddr;
    socklen_t senderAddrLen = sizeof(senderAddr);
    
    ssize_t bytesRead = recvfrom(udpSocket, buffer, sizeof(buffer) - 1, 0,
                                (struct sockaddr*)&senderAddr, &senderAddrLen);
    
    if (bytesRead > 0) {
        buffer[bytesRead] = '\0';
        
        // Split by newlines and process each message
        std::string data(buffer);
        size_t pos = 0;
        size_t prev = 0;
        
        while ((pos = data.find('\n', prev)) != std::string::npos) {
            std::string message = data.substr(prev, pos - prev);
            processServerMessage(message);
            prev = pos + 1;
        }
        
        // Process any remaining data
        if (prev < data.length()) {
            processServerMessage(data.substr(prev));
        }
    }
    else if (bytesRead < 0) {
        // Error or would block
        if (GETSOCKETERRNO() != EWOULDBLOCK && GETSOCKETERRNO() != EAGAIN
#ifdef _WIN32
            && GETSOCKETERRNO() != WSAEWOULDBLOCK
#endif
        ) {
            // Real error
            statusMessage = "UDP error: " + std::to_string(GETSOCKETERRNO());
        }
    }
}

// Process messages from the server
void NetworkClient::processServerMessage(const std::string& message) {
    if (message.empty()) return;
    
    std::cout << "Received: " << message << std::endl;
    
    // Check message type
    if (message.find("CONFIG ") == 0) {
        // Config message - extract player ID and color
        size_t jsonStart = message.find('{');
        if (jsonStart != std::string::npos) {
            // Simple parsing to extract the player ID and color
            // In a real implementation, use a proper JSON parser
            size_t idPos = message.find("\"playerId\"");
            size_t colorPos = message.find("\"color\"");
            
            if (idPos != std::string::npos) {
                size_t idStart = message.find('\"', idPos + 11) + 1;
                size_t idEnd = message.find('\"', idStart);
                if (idStart != std::string::npos && idEnd != std::string::npos) {
                    playerId = message.substr(idStart, idEnd - idStart);
                }
            }
            
            if (colorPos != std::string::npos) {
                size_t colorStart = message.find('\"', colorPos + 8) + 1;
                size_t colorEnd = message.find('\"', colorStart);
                if (colorStart != std::string::npos && colorEnd != std::string::npos) {
                    playerColor = message.substr(colorStart, colorEnd - colorStart);
                }
            }
        }
    }
    else if (message.find("PLAYERS ") == 0) {
        // Player list update
        std::string playerList = message.substr(8); // Remove "PLAYERS " prefix
        std::vector<PlayerInfo> updatedPlayers;
        
        // Split by commas
        std::stringstream ss(playerList);
        std::string playerName;
        int index = 0;
        
        while (std::getline(ss, playerName, ',')) {
            if (!playerName.empty()) {
                PlayerInfo info;
                info.id = "player_" + std::to_string(index++);
                info.name = playerName;
                updatedPlayers.push_back(info);
            }
        }
        
        players = updatedPlayers;
        
        // Call callback if set
        if (playerListCallback) {
            playerListCallback(players);
        }
    }
    else if (message.find("POS ") == 0) {
        // Position update - for UDP messages
        // Extract player ID, x, y
    }
}

// Update network state
void NetworkClient::update() {
    // Check connection status first
    if (status == ConnectionStatus::CONNECTED) {
        // Check for messages
        checkTcpMessages();
        checkUdpMessages();
    }
} 