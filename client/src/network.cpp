#include "network.h"
#include <iostream>
#include <sstream>
#include <cstring>
#include <chrono>

// Constructor
NetworkClient::NetworkClient() {
    // Initialize members
    status = ConnectionStatus::DISCONNECTED;
    statusMessage = "Not connected";
    tcpConnectPending = false;
    udpRegistered = false;
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
    
    // Connect TCP socket (non-blocking)
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
        
        // Connection is in progress
        tcpConnectPending = true;
        connectStartTime = std::chrono::steady_clock::now();
        return true;
    }
    
    // Connection succeeded immediately (rare with non-blocking sockets)
    tcpConnectPending = false;
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
    tcpConnectPending = false;
}

// Check if TCP connection is established
bool NetworkClient::checkTcpConnectionStatus() {
    if (!tcpConnectPending || !ISVALIDSOCKET(tcpSocket)) {
        return false;
    }
    
    // Check connection status with select
    fd_set writeSet;
    fd_set errorSet;
    struct timeval timeout = {0, 0}; // Non-blocking
    
    FD_ZERO(&writeSet);
    FD_ZERO(&errorSet);
    FD_SET(tcpSocket, &writeSet);
    FD_SET(tcpSocket, &errorSet);
    
    int selectResult = select(tcpSocket + 1, NULL, &writeSet, &errorSet, &timeout);
    
    if (selectResult > 0) {
        if (FD_ISSET(tcpSocket, &errorSet)) {
            // Connection failed
            int error = 0;
            socklen_t len = sizeof(error);
            getsockopt(tcpSocket, SOL_SOCKET, SO_ERROR, (char*)&error, &len);
            
            statusMessage = "Connection failed: " + std::to_string(error);
            status = ConnectionStatus::CONNECTION_FAILED;
            tcpConnectPending = false;
            return false;
        }
        
        if (FD_ISSET(tcpSocket, &writeSet)) {
            // Connection successful
            status = ConnectionStatus::CONNECTED;
            statusMessage = "Connected to server";
            tcpConnectPending = false;
            return true;
        }
    }
    else if (selectResult < 0) {
        // Select error
        statusMessage = "Select error: " + std::to_string(GETSOCKETERRNO());
        status = ConnectionStatus::CONNECTION_FAILED;
        tcpConnectPending = false;
        return false;
    }
    
    // Check timeout (5 seconds)
    auto currentTime = std::chrono::steady_clock::now();
    auto elapsedTime = std::chrono::duration_cast<std::chrono::seconds>(currentTime - connectStartTime).count();
    
    if (elapsedTime > 5) {
        statusMessage = "Connection timeout";
        status = ConnectionStatus::CONNECTION_FAILED;
        tcpConnectPending = false;
        return false;
    }
    
    // Still connecting
    return false;
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
bool NetworkClient::sendConnectRequest(const std::string& playerName, const std::string& color) {
    if (status != ConnectionStatus::CONNECTED) {
        pendingConnectName = playerName;
        playerColor = color;
        return false;
    }
    
    std::stringstream ss;
    ss << "CONNECT {\"name\":\"" << playerName << "\",\"color\":\"" << color << "\"}";
    pendingConnectName = "";
    return sendTcpMessage(ss.str());
}

// Send config update (like color change)
bool NetworkClient::sendConfigUpdate(const std::string& color) {
    if (status != ConnectionStatus::CONNECTED || playerId.empty()) {
        return false;
    }
    
    std::stringstream ss;
    ss << "CONFIG {\"playerId\":\"" << playerId << "\",\"color\":\"" << color << "\",\"mapId\":\"\"}";
    return sendTcpMessage(ss.str());
}

// Send position update
bool NetworkClient::sendPositionUpdate(float x, float y) {
    if (status != ConnectionStatus::CONNECTED || playerId.empty() || !udpRegistered) {
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
    
    // Update last message time for timeout detection
    lastMessageTime = std::chrono::steady_clock::now();
    
    std::cout << "Received: " << message << std::endl;
    
    // Check message type
    if (message == "PONG") {
        // Server responded to our ping
        return;
    }
    else if (message.find("CONFIG ") == 0) {
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
                    
                    // Send UDP registration message to associate UDP address with player
                    std::string regMessage = "UDP_REGISTER {\"playerId\":\"" + playerId + "\"}";
                    sendUdpMessage(regMessage);
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
        // Player list update - now in JSON format
        std::string playerListJson = message.substr(8); // Remove "PLAYERS " prefix
        std::vector<PlayerInfo> updatedPlayers;
        
        // Simple JSON array parsing
        // In a real implementation, use a proper JSON parser
        size_t arrayStart = playerListJson.find('[');
        size_t arrayEnd = playerListJson.find(']');
        
        if (arrayStart != std::string::npos && arrayEnd != std::string::npos && arrayStart < arrayEnd) {
            std::string arrayContent = playerListJson.substr(arrayStart + 1, arrayEnd - arrayStart - 1);
            
            // Split objects by "},{" pattern
            size_t pos = 0;
            size_t objStart = 0;
            
            while (objStart < arrayContent.length()) {
                // Find object end
                size_t objEnd = arrayContent.find("},{", objStart);
                if (objEnd == std::string::npos) {
                    objEnd = arrayContent.length();
                } else {
                    objEnd += 1; // Include closing brace
                }
                
                // Extract object string
                std::string objStr = arrayContent.substr(objStart, objEnd - objStart);
                
                // Parse player info fields
                PlayerInfo info;
                
                size_t idPos = objStr.find("\"id\"");
                size_t namePos = objStr.find("\"name\"");
                size_t colorPos = objStr.find("\"color\"");
                
                if (idPos != std::string::npos) {
                    size_t valStart = objStr.find('\"', idPos + 5) + 1;
                    size_t valEnd = objStr.find('\"', valStart);
                    if (valStart != std::string::npos && valEnd != std::string::npos) {
                        info.id = objStr.substr(valStart, valEnd - valStart);
                    }
                }
                
                if (namePos != std::string::npos) {
                    size_t valStart = objStr.find('\"', namePos + 7) + 1;
                    size_t valEnd = objStr.find('\"', valStart);
                    if (valStart != std::string::npos && valEnd != std::string::npos) {
                        info.name = objStr.substr(valStart, valEnd - valStart);
                    }
                }
                
                if (colorPos != std::string::npos) {
                    size_t valStart = objStr.find('\"', colorPos + 8) + 1;
                    size_t valEnd = objStr.find('\"', valStart);
                    if (valStart != std::string::npos && valEnd != std::string::npos) {
                        info.color = objStr.substr(valStart, valEnd - valStart);
                    }
                }
                
                // Add to player list if it has an ID and is not our own player
                if (!info.id.empty() && info.id != playerId) {
                    updatedPlayers.push_back(info);
                }
                
                // Move to next object
                if (objEnd < arrayContent.length()) {
                    objStart = objEnd + 1; // Skip comma
                } else {
                    break;
                }
            }
        }
        
        players = updatedPlayers;
        
        // Call callback if set
        if (playerListCallback) {
            playerListCallback(players);
        }
    }
    else if (message.find("UDP_REGISTERED") == 0) {
        // UDP registration confirmed
        udpRegistered = true;
        std::cout << "UDP registration confirmed" << std::endl;
    }
    else if (message.find("POS ") == 0) {
        // Position update - for UDP messages
        // Extract player ID, x, y
        std::string posJson = message.substr(4); // Remove "POS " prefix
        
        // Simple JSON parsing - extract player ID and position
        size_t idPos = posJson.find("\"playerId\"");
        size_t xPos = posJson.find("\"x\"");
        size_t yPos = posJson.find("\"y\"");
        
        if (idPos != std::string::npos && xPos != std::string::npos && yPos != std::string::npos) {
            // Extract player ID
            size_t idStart = posJson.find('\"', idPos + 11) + 1;
            size_t idEnd = posJson.find('\"', idStart);
            std::string playerId = "";
            
            if (idStart != std::string::npos && idEnd != std::string::npos) {
                playerId = posJson.substr(idStart, idEnd - idStart);
            }
            
            // Extract X position
            float x = 0.0f;
            size_t xStart = xPos + 4; // Skip "x":
            size_t xEnd = posJson.find(',', xStart);
            if (xStart != std::string::npos && xEnd != std::string::npos) {
                try {
                    x = std::stof(posJson.substr(xStart, xEnd - xStart));
                } catch (...) {
                    // Handle parsing error
                }
            }
            
            // Extract Y position
            float y = 0.0f;
            size_t yStart = yPos + 4; // Skip "y":
            size_t yEnd = posJson.find('}', yStart);
            if (yStart != std::string::npos && yEnd != std::string::npos) {
                try {
                    y = std::stof(posJson.substr(yStart, yEnd - yStart));
                } catch (...) {
                    // Handle parsing error
                }
            }
            
            // Update player position in the player list
            for (auto& player : players) {
                if (player.id == playerId) {
                    player.x = x;
                    player.y = y;
                    break;
                }
            }
            
            // Call callback if set
            if (playerListCallback) {
                playerListCallback(players);
            }
        }
    }
    else if (message.find("CHAT ") == 0) {
        // Chat message from another player
        std::string chatJson = message.substr(5); // Remove "CHAT " prefix
        
        std::cout << "Processing chat message: " << chatJson << std::endl;
        
        // Parse the JSON to extract player id, player name, and message text
        size_t playerIdPos = chatJson.find("\"playerId\"");
        size_t playerNamePos = chatJson.find("\"playerName\"");
        size_t textPos = chatJson.find("\"text\"");
        
        std::cout << "Found positions - playerId: " << (playerIdPos != std::string::npos ? "yes" : "no")
                 << ", playerName: " << (playerNamePos != std::string::npos ? "yes" : "no")
                 << ", text: " << (textPos != std::string::npos ? "yes" : "no") << std::endl;
        
        if (playerIdPos != std::string::npos && playerNamePos != std::string::npos && textPos != std::string::npos) {
            // Extract player name (we display this)
            std::string playerName = "";
            size_t nameStart = chatJson.find('\"', playerNamePos + 13) + 1;
            size_t nameEnd = chatJson.find('\"', nameStart);
            if (nameStart != std::string::npos && nameEnd != std::string::npos) {
                playerName = chatJson.substr(nameStart, nameEnd - nameStart);
                std::cout << "Extracted playerName: " << playerName << std::endl;
            }
            
            // Extract message text
            std::string text = "";
            size_t textStart = chatJson.find('\"', textPos + 7) + 1;
            size_t textEnd = chatJson.find('\"', textStart);
            if (textStart != std::string::npos && textEnd != std::string::npos) {
                text = chatJson.substr(textStart, textEnd - textStart);
                std::cout << "Extracted text: " << text << std::endl;
            }
            
            // Extract player ID (to check if it's our own message)
            std::string msgPlayerId = "";
            size_t idStart = chatJson.find('\"', playerIdPos + 11) + 1;
            size_t idEnd = chatJson.find('\"', idStart);
            if (idStart != std::string::npos && idEnd != std::string::npos) {
                msgPlayerId = chatJson.substr(idStart, idEnd - idStart);
                std::cout << "Extracted playerId: " << msgPlayerId << ", local playerId: " << playerId << std::endl;
            }
            
            // Create a formatted chat message
            if (!playerName.empty() && !text.empty()) {
                std::string chatMessage = playerName + ": " + text;
                
                std::cout << "Created chat message: " << chatMessage << std::endl;
                std::cout << "Comparison result: " << (msgPlayerId != playerId ? "Different IDs, adding to messages" : "Same ID, skipping") << std::endl;
                
                // Always add messages to the chat list, even our own messages
                // The client-side will combine local and network messages
                chatMessages.push_back(chatMessage);
                std::cout << "Chat message added, total messages: " << chatMessages.size() << std::endl;
                
                // Keep chat history to a reasonable size
                if (chatMessages.size() > 20) { // Keep last 20 messages
                    chatMessages.erase(chatMessages.begin());
                }
            }
        } else {
            std::cout << "Failed to parse chat message JSON: " << chatJson << std::endl;
        }
    }
}

// Update network state
void NetworkClient::update() {
    // If connection is pending, check if it's established
    if (tcpConnectPending) {
        if (checkTcpConnectionStatus() && !pendingConnectName.empty()) {
            // Connection established, send pending connect request
            std::cout << "TCP connection established, sending connect request" << std::endl;
            sendConnectRequest(pendingConnectName, playerColor);
        }
    }
    
    // Check connection status first
    if (status == ConnectionStatus::CONNECTED) {
        // Update connection timeout (only after established)
        auto currentTime = std::chrono::steady_clock::now();
        auto elapsedSinceLastMessage = std::chrono::duration_cast<std::chrono::seconds>(
            currentTime - lastMessageTime).count();
        
        // If no message for more than connectionTimeout seconds, consider disconnected
        if (elapsedSinceLastMessage > connectionTimeout) {
            status = ConnectionStatus::DISCONNECTED;
            statusMessage = "Connection timeout - server not responding";
            std::cout << "Connection timeout detected: " << elapsedSinceLastMessage << " seconds" << std::endl;
            return;
        }
        
        // Check for messages
        checkTcpMessages();
        checkUdpMessages();
        
        // Send ping message occasionally to keep the connection alive
        auto elapsedSincePing = std::chrono::duration_cast<std::chrono::seconds>(
            currentTime - lastPingTime).count();
            
        if (elapsedSincePing > pingInterval) {
            sendTcpMessage("PING");
            lastPingTime = currentTime;
        }
    }
    // Check for pending connections too
    else if (status == ConnectionStatus::CONNECTING) {
        // Occasionally check TCP connection status during connecting phase
        checkTcpMessages(); // May update connection status if server sends data
        
        // Check if connection is taking too long
        auto currentTime = std::chrono::steady_clock::now();
        auto elapsedSinceConnect = std::chrono::duration_cast<std::chrono::seconds>(
            currentTime - connectStartTime).count();
            
        if (elapsedSinceConnect > connectionTimeout) {
            status = ConnectionStatus::CONNECTION_FAILED;
            statusMessage = "Connection timeout - could not establish connection";
            std::cout << "Initial connection timeout: " << elapsedSinceConnect << " seconds" << std::endl;
        }
    }
} 