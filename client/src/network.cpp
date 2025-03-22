#include "network.h"
#include <iostream>
#include <sstream>
#include <algorithm>
#include <cstring>
#include <chrono>
#include <iomanip>
#include <nlohmann/json.hpp>
#include <thread>

// Platform-specific socket includes
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    typedef SOCKET socket_t;
    #pragma comment(lib, "ws2_32.lib")
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <netdb.h>
    #include <unistd.h>
    #include <fcntl.h>
    typedef int socket_t;
#endif

// For debug logs
#define DEBUG_LOG(msg) std::cout << "[NetworkClient] " << msg << std::endl

// Constructor
NetworkClient::NetworkClient() : 
    tcpSocket(INVALID_SOCKET),
    udpSocket(INVALID_SOCKET),
    status(ConnectionStatus::DISCONNECTED),
    statusMessage("Not connected"),
    tcpConnectPending(false),
    udpRegistered(false)
{
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
    // Already connected or connecting
    if (status == ConnectionStatus::CONNECTED || status == ConnectionStatus::CONNECTING) {
        return false;
    }
    
    // Update status
    status = ConnectionStatus::CONNECTING;
    statusMessage = "Connecting to server...";
    connectStartTime = std::chrono::steady_clock::now();
    
    // Create TCP socket
    tcpSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (tcpSocket == INVALID_SOCKET) {
        status = ConnectionStatus::CONNECTION_FAILED;
        statusMessage = "Failed to create TCP socket";
        return false;
    }
    
    // Create UDP socket
    udpSocket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (udpSocket == INVALID_SOCKET) {
        closesocket(tcpSocket);
        tcpSocket = INVALID_SOCKET;
        status = ConnectionStatus::CONNECTION_FAILED;
        statusMessage = "Failed to create UDP socket";
        return false;
    }
    
    // Set server addresses
    memset(&serverTcpAddr, 0, sizeof(serverTcpAddr));
    serverTcpAddr.sin_family = AF_INET;
    serverTcpAddr.sin_port = htons(tcpPort);
    
    memset(&serverUdpAddr, 0, sizeof(serverUdpAddr));
    serverUdpAddr.sin_family = AF_INET;
    serverUdpAddr.sin_port = htons(udpPort);
    
    // Resolve server address
    if (inet_pton(AF_INET, serverAddress.c_str(), &serverTcpAddr.sin_addr) <= 0) {
        struct hostent* he = gethostbyname(serverAddress.c_str());
        if (!he) {
            closesocket(tcpSocket);
            closesocket(udpSocket);
            tcpSocket = INVALID_SOCKET;
            udpSocket = INVALID_SOCKET;
            status = ConnectionStatus::CONNECTION_FAILED;
            statusMessage = "Failed to resolve server address";
            return false;
        }
        memcpy(&serverTcpAddr.sin_addr, he->h_addr_list[0], he->h_length);
        memcpy(&serverUdpAddr.sin_addr, he->h_addr_list[0], he->h_length);
    } else {
        serverUdpAddr.sin_addr = serverTcpAddr.sin_addr;
    }
    
    // Set non-blocking mode for TCP socket
    if (!setSocketNonBlocking(tcpSocket)) {
        closesocket(tcpSocket);
        closesocket(udpSocket);
        tcpSocket = INVALID_SOCKET;
        udpSocket = INVALID_SOCKET;
        status = ConnectionStatus::CONNECTION_FAILED;
        statusMessage = "Failed to set TCP socket to non-blocking mode";
        return false;
    }
    
    // Set non-blocking mode for UDP socket
    if (!setSocketNonBlocking(udpSocket)) {
        closesocket(tcpSocket);
        closesocket(udpSocket);
        tcpSocket = INVALID_SOCKET;
        udpSocket = INVALID_SOCKET;
        status = ConnectionStatus::CONNECTION_FAILED;
        statusMessage = "Failed to set UDP socket to non-blocking mode";
        return false;
    }
    
    // Initiate connection
    if (::connect(tcpSocket, (struct sockaddr*)&serverTcpAddr, sizeof(serverTcpAddr)) == SOCKET_ERROR) {
#ifdef _WIN32
        if (WSAGetLastError() != WSAEWOULDBLOCK) {
            closesocket(tcpSocket);
            closesocket(udpSocket);
            tcpSocket = INVALID_SOCKET;
            udpSocket = INVALID_SOCKET;
            status = ConnectionStatus::CONNECTION_FAILED;
            statusMessage = "Failed to connect to server";
            return false;
        }
#else
        if (errno != EINPROGRESS) {
            closesocket(tcpSocket);
            closesocket(udpSocket);
            tcpSocket = INVALID_SOCKET;
            udpSocket = INVALID_SOCKET;
            status = ConnectionStatus::CONNECTION_FAILED;
            statusMessage = "Failed to connect to server";
            return false;
        }
#endif
    }
    
    tcpConnectPending = true;
    statusMessage = "Waiting for connection...";
    return true;
}

// Disconnect from server
void NetworkClient::disconnect() {
    if (tcpSocket != INVALID_SOCKET) {
        closesocket(tcpSocket);
        tcpSocket = INVALID_SOCKET;
    }
    
    if (udpSocket != INVALID_SOCKET) {
        closesocket(udpSocket);
        udpSocket = INVALID_SOCKET;
    }
    
    status = ConnectionStatus::DISCONNECTED;
    statusMessage = "Disconnected from server";
    tcpConnectPending = false;
    udpRegistered = false;
    playerId = "";
    playerColor = "";
    
#ifdef _WIN32
    WSACleanup();
#endif
}

// Set socket to non-blocking mode
bool NetworkClient::setSocketNonBlocking(socket_t socket) {
#ifdef _WIN32
    u_long mode = 1;
    return (ioctlsocket(socket, FIONBIO, &mode) == 0);
#else
    int flags = fcntl(socket, F_GETFL, 0);
    return (fcntl(socket, F_SETFL, flags | O_NONBLOCK) != -1);
#endif
}

// Check TCP connection status
bool NetworkClient::checkTcpConnectionStatus() {
    if (!tcpConnectPending) {
        return true;
    }
    
    std::cout << "Checking TCP connection status..." << std::endl;
    
    // Check if connection completed
    fd_set writefds;
    fd_set errorfds;
    struct timeval tv;
    
    FD_ZERO(&writefds);
    FD_ZERO(&errorfds);
    FD_SET(tcpSocket, &writefds);
    FD_SET(tcpSocket, &errorfds);
    
    tv.tv_sec = 0;
    tv.tv_usec = 0;
    
    int ret = select(tcpSocket + 1, NULL, &writefds, &errorfds, &tv);
    
    if (ret > 0) {
        if (FD_ISSET(tcpSocket, &errorfds)) {
            // Connection failed
            tcpConnectPending = false;
            status = ConnectionStatus::CONNECTION_FAILED;
            statusMessage = "Connection to server failed";
            std::cout << "Connection failed: socket error set" << std::endl;
            return false;
        }
        
        if (FD_ISSET(tcpSocket, &writefds)) {
            // Connection succeeded
            tcpConnectPending = false;
            status = ConnectionStatus::CONNECTED;
            statusMessage = "Connected to server";
            lastMessageTime = std::chrono::steady_clock::now();
            lastPingTime = std::chrono::steady_clock::now();
            std::cout << "Connection established successfully" << std::endl;
            return true;
        }
    }
    
    // Check for connection timeout
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - connectStartTime).count();
    
    if (elapsed > 10) {
        tcpConnectPending = false;
        status = ConnectionStatus::CONNECTION_FAILED;
        statusMessage = "Connection to server timed out";
        std::cout << "Connection timed out after " << elapsed << " seconds" << std::endl;
        return false;
    }
    
    std::cout << "Connection still pending after " << elapsed << " seconds" << std::endl;
    return true;
}

// Update network state
void NetworkClient::update() {
    // Check connection status
    if (tcpConnectPending) {
        if (!checkTcpConnectionStatus()) {
            return;
        }
        
        // If we just connected and have a pending connect name, send the connect request immediately
        if (status == ConnectionStatus::CONNECTED && !pendingConnectName.empty()) {
            std::string name = pendingConnectName;
            pendingConnectName = ""; // Clear it to avoid sending multiple times
            
            // Create connect message with the pending name using JSON
            nlohmann::json request = {
                {"name", name},
                {"color", playerColor.empty() ? "#FF0000" : playerColor}
            };
            
            std::string requestStr = request.dump();
            DEBUG_LOG("Connection established, sending delayed connect request: CONNECT " << requestStr);
            sendTcpMessage("CONNECT " + requestStr);
        }
    }
    
    // Process messages if connected
    if (status == ConnectionStatus::CONNECTED) {
        checkTcpMessages();
        checkUdpMessages();
        
        // Check for connection timeout
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - lastMessageTime).count();
        
        if (elapsed > connectionTimeout) {
            DEBUG_LOG("Connection timed out after " << elapsed << " seconds");
            disconnect();
            status = ConnectionStatus::DISCONNECTED;
            statusMessage = "Connection to server timed out";
            return;
        }
        
        // Send ping if needed
        auto pingElapsed = std::chrono::duration_cast<std::chrono::seconds>(now - lastPingTime).count();
        
        if (pingElapsed > pingInterval) {
            sendTcpMessage("PING");
            lastPingTime = now;
        }
    }
}

// Send TCP message
bool NetworkClient::sendTcpMessage(const std::string& message) {
    if (tcpSocket == INVALID_SOCKET || status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    // End message with newline
    std::string fullMessage = message + "\n";
    
    // Send message
    int result = send(tcpSocket, fullMessage.c_str(), static_cast<int>(fullMessage.length()), 0);
    
    if (result == SOCKET_ERROR) {
        DEBUG_LOG("Failed to send TCP message: " << message);
        return false;
    }
    
    DEBUG_LOG("Sent TCP: " << message);
    return true;
}

// Send UDP message
bool NetworkClient::sendUdpMessage(const std::string& message) {
    if (udpSocket == INVALID_SOCKET || status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    // Send message
    int result = sendto(udpSocket, message.c_str(), static_cast<int>(message.length()), 0,
                       (struct sockaddr*)&serverUdpAddr, sizeof(serverUdpAddr));
    
    if (result == SOCKET_ERROR) {
        DEBUG_LOG("Failed to send UDP message: " << message);
        return false;
    }
    
    return true;
}

// Check for TCP messages
void NetworkClient::checkTcpMessages() {
    if (tcpSocket == INVALID_SOCKET) {
        return;
    }
    
    // Receive data
    char buffer[1024];
    int bytesReceived = recv(tcpSocket, buffer, sizeof(buffer) - 1, 0);
    
    if (bytesReceived > 0) {
        // Update last message time
        lastMessageTime = std::chrono::steady_clock::now();
        
        // Process received data
        buffer[bytesReceived] = '\0';
        
        // Add to buffer
        tcpBuffer += buffer;
        
        // Process complete messages
        size_t pos;
        while ((pos = tcpBuffer.find('\n')) != std::string::npos) {
            std::string message = tcpBuffer.substr(0, pos);
            tcpBuffer.erase(0, pos + 1);
            
            // Process message
            if (!message.empty()) {
                processServerMessage(message);
            }
        }
    }
#ifdef _WIN32
    else if (bytesReceived == SOCKET_ERROR && WSAGetLastError() != WSAEWOULDBLOCK) {
        DEBUG_LOG("TCP receive error: " << WSAGetLastError());
        disconnect();
        status = ConnectionStatus::DISCONNECTED;
        statusMessage = "Connection to server lost";
    }
#else
    else if (bytesReceived == SOCKET_ERROR && errno != EAGAIN && errno != EWOULDBLOCK) {
        DEBUG_LOG("TCP receive error: " << errno);
        disconnect();
        status = ConnectionStatus::DISCONNECTED;
        statusMessage = "Connection to server lost";
    }
#endif
    else if (bytesReceived == 0) {
        // Connection closed by server
        DEBUG_LOG("Server closed the connection");
        disconnect();
        status = ConnectionStatus::DISCONNECTED;
        statusMessage = "Server closed the connection";
    }
}

// Check for UDP messages
void NetworkClient::checkUdpMessages() {
    if (udpSocket == INVALID_SOCKET) {
        return;
    }
    
    // Receive data
    char buffer[1024];
    struct sockaddr_in senderAddr;
    socklen_t addrLen = sizeof(senderAddr);
    
    int bytesReceived = recvfrom(udpSocket, buffer, sizeof(buffer) - 1, 0,
                                (struct sockaddr*)&senderAddr, &addrLen);
    
    if (bytesReceived > 0) {
        // Update last message time
        lastMessageTime = std::chrono::steady_clock::now();
        
        // Process received data
        buffer[bytesReceived] = '\0';
        std::string message(buffer);
        
        // Process message if from server
        if (senderAddr.sin_addr.s_addr == serverUdpAddr.sin_addr.s_addr && 
            senderAddr.sin_port == serverUdpAddr.sin_port) {
            processServerMessage(message);
        }
    }
}

// Process message from server
void NetworkClient::processServerMessage(const std::string& message) {
    DEBUG_LOG("Received: " << message);
    
    // Check if the message starts with a command prefix
    size_t spacePos = message.find_first_of(' ');
    if (spacePos != std::string::npos) {
        std::string command = message.substr(0, spacePos);
        std::string payload = message.substr(spacePos + 1);
        
        DEBUG_LOG("Processing command: " << command << " with payload: " << payload);
        
        try {
            // Parse payload as JSON
            nlohmann::json data = nlohmann::json::parse(payload);
            
            if (command == "CONFIG") {
                // Configuration message
                if (data.contains("playerId") && data.contains("color")) {
                    playerId = data["playerId"];
                    playerColor = data["color"];
                    DEBUG_LOG("Received CONFIG message. Player ID: " << playerId << ", Color: " << playerColor);
                    
                    // Register UDP address
                    DEBUG_LOG("Sending UDP registration");
                    sendUdpRegistration();
                    
                    // Send a position update to get a position assigned
                    DEBUG_LOG("Sending initial position request");
                    sendPositionUpdate(0, 0); // Use 0,0 to let server assign position
                }
            } 
            else if (command == "PLAYERS") {
                // Player list update
                if (data.is_array()) {
                    std::vector<PlayerInfo> newPlayers;
                    
                    DEBUG_LOG("Processing PLAYERS update with " << data.size() << " players");
                    
                    for (const auto& player : data) {
                        if (player.contains("id") && player.contains("name") && player.contains("color")) {
                            PlayerInfo info;
                            info.id = player["id"];
                            info.name = player["name"];
                            info.color = player["color"];
                            
                            if (player.contains("x") && player.contains("y")) {
                                info.x = player["x"];
                                info.y = player["y"];
                                DEBUG_LOG("Player " << info.name << " (" << info.id << ") at position (" 
                                         << info.x << "," << info.y << ")");
                            }
                            
                            if (player.contains("mapId")) {
                                info.mapId = player["mapId"];
                            } else {
                                info.mapId = "default";
                            }
                            
                            newPlayers.push_back(info);
                        }
                    }
                    
                    players = newPlayers;
                    
                    // Call the registered callback if available
                    if (playerListCallback) {
                        DEBUG_LOG("Calling player list callback with " << players.size() << " players");
                        playerListCallback(players);
                    }
                    
                    // Check all players and call position callback if we have some with positions
                    for (const auto& player : players) {
                        if (positionCallback) {
                            DEBUG_LOG("Calling position callback for player " << player.id);
                            positionCallback(player.id, player.x, player.y);
                        }
                    }
                    
                    DEBUG_LOG("Updated player list: " << players.size() << " players");
                }
            } 
            else if (command == "POSITION") {
                // Position update
                if (data.contains("id") && data.contains("x") && data.contains("y")) {
                    std::string id = data["id"];
                    float x = data["x"];
                    float y = data["y"];
                    
                    DEBUG_LOG("Position update for player " << id << ": (" << x << ", " << y << ")");
                    
                    // Update player position in the list
                    for (auto& player : players) {
                        if (player.id == id) {
                            player.x = x;
                            player.y = y;
                            break;
                        }
                    }
                    
                    // Call the position callback if available
                    if (positionCallback) {
                        DEBUG_LOG("Calling position callback for " << id);
                        positionCallback(id, x, y);
                    }
                }
            }
            else if (command == "CHAT") {
                // Chat message
                if (data.contains("sender") && data.contains("message")) {
                    std::string sender = data["sender"];
                    std::string chatMsg = data["message"];
                    std::string fullMessage = sender + ": " + chatMsg;
                    chatMessages.push_back(fullMessage);
                    DEBUG_LOG("Chat message: " << fullMessage);
                }
            }
            else if (command == "PONG") {
                // Pong response, no action needed
                DEBUG_LOG("Received PONG from server");
            }
            else if (command == "ERROR") {
                // Error message
                if (data.contains("message")) {
                    std::string errorMsg = data["message"];
                    DEBUG_LOG("Server error: " << errorMsg);
                    statusMessage = "Server error: " + errorMsg;
                }
            }
            else if (command == "UDP_REGISTERED") {
                // UDP registration confirmation
                DEBUG_LOG("UDP registration confirmed by server");
                udpRegistered = true;
            }
            else if (command == "GAME_STATE") {
                // Full game state update - similar to PLAYERS but might have additional fields
                DEBUG_LOG("Received GAME_STATE update");
                if (data.contains("players") && data["players"].is_array()) {
                    std::vector<PlayerInfo> newPlayers;
                    
                    for (const auto& player : data["players"]) {
                        if (player.contains("id") && player.contains("name")) {
                            PlayerInfo info;
                            info.id = player["id"];
                            info.name = player["name"];
                            
                            if (player.contains("color")) {
                                info.color = player["color"];
                            } else {
                                info.color = "#FF0000"; // Default red
                            }
                            
                            if (player.contains("x") && player.contains("y")) {
                                info.x = player["x"];
                                info.y = player["y"];
                            }
                            
                            if (player.contains("mapId")) {
                                info.mapId = player["mapId"];
                            } else {
                                info.mapId = "default";
                            }
                            
                            newPlayers.push_back(info);
                        }
                    }
                    
                    players = newPlayers;
                    
                    // Call the registered callback if available
                    if (playerListCallback) {
                        playerListCallback(players);
                    }
                    
                    // Call position callback for each player to ensure UI is updated
                    for (const auto& player : players) {
                        if (positionCallback) {
                            positionCallback(player.id, player.x, player.y);
                        }
                    }
                }
            }
            return; // Successfully processed command with JSON payload
        } catch (const std::exception& e) {
            DEBUG_LOG("Failed to parse payload as JSON: " << e.what());
            
            // Handle special case for PONG without payload
            if (command == "PONG") {
                DEBUG_LOG("Received PONG from server (legacy)");
                return;
            }
        }
    }
    
    // If we get here, try to process as legacy format without command prefix
    try {
        // Parse message as JSON
        nlohmann::json data = nlohmann::json::parse(message);
        
        // Process different message types
        if (data.contains("type")) {
            std::string type = data["type"];
            
            if (type == "CONFIG") {
                // Configuration message
                if (data.contains("playerId") && data.contains("color")) {
                    playerId = data["playerId"];
                    playerColor = data["color"];
                    DEBUG_LOG("Received CONFIG message (JSON). Player ID: " << playerId << ", Color: " << playerColor);
                    
                    // Register UDP address
                    DEBUG_LOG("Sending UDP_REGISTER");
                    sendUdpRegistration();
                    
                    // Send a position update to get a position assigned
                    DEBUG_LOG("Sending initial position request");
                    sendPositionUpdate(0, 0); // Use 0,0 to let server assign position
                }
            } 
            else if (type == "CHAT") {
                // Chat message
                if (data.contains("sender") && data.contains("message")) {
                    std::string sender = data["sender"];
                    std::string chatMsg = data["message"];
                    std::string fullMessage = sender + ": " + chatMsg;
                    chatMessages.push_back(fullMessage);
                    DEBUG_LOG("Chat message: " << fullMessage);
                }
            } 
            else if (type == "PLAYERS") {
                // Player list update
                if (data.contains("players") && data["players"].is_array()) {
                    std::vector<PlayerInfo> newPlayers;
                    
                    DEBUG_LOG("Processing PLAYERS update with " << data["players"].size() << " players");
                    
                    for (const auto& player : data["players"]) {
                        if (player.contains("id") && player.contains("name") && player.contains("color")) {
                            PlayerInfo info;
                            info.id = player["id"];
                            info.name = player["name"];
                            info.color = player["color"];
                            
                            if (player.contains("x") && player.contains("y")) {
                                info.x = player["x"];
                                info.y = player["y"];
                                DEBUG_LOG("Player " << info.name << " (" << info.id << ") at position (" 
                                         << info.x << "," << info.y << ")");
                            }
                            
                            if (player.contains("mapId")) {
                                info.mapId = player["mapId"];
                            } else {
                                info.mapId = "default";
                            }
                            
                            newPlayers.push_back(info);
                        }
                    }
                    
                    players = newPlayers;
                    
                    // Call the registered callback if available
                    if (playerListCallback) {
                        DEBUG_LOG("Calling player list callback with " << players.size() << " players");
                        playerListCallback(players);
                    }
                    
                    // Check all players and call position callback if we have some with positions
                    for (const auto& player : players) {
                        if (positionCallback) {
                            DEBUG_LOG("Calling position callback for player " << player.id);
                            positionCallback(player.id, player.x, player.y);
                        }
                    }
                    
                    DEBUG_LOG("Updated player list: " << players.size() << " players");
                }
            } 
            else if (type == "POSITION") {
                // Position update
                if (data.contains("id") && data.contains("x") && data.contains("y")) {
                    std::string id = data["id"];
                    float x = data["x"];
                    float y = data["y"];
                    
                    DEBUG_LOG("Position update for player " << id << ": (" << x << ", " << y << ")");
                    
                    // Update player position in the list
                    for (auto& player : players) {
                        if (player.id == id) {
                            player.x = x;
                            player.y = y;
                            break;
                        }
                    }
                    
                    // Call the position callback if available
                    if (positionCallback) {
                        DEBUG_LOG("Calling position callback for " << id);
                        positionCallback(id, x, y);
                    }
                }
            }
            else if (type == "PONG") {
                // Pong response, no action needed
                DEBUG_LOG("Received PONG from server");
            }
        }
    } catch (const std::exception& e) {
        // Legacy string-based message parsing for backward compatibility
        if (message.substr(0, 7) == "CONFIG:") {
            std::istringstream iss(message.substr(7));
            std::string id, color;
            
            if (std::getline(iss, id, ':') && std::getline(iss, color)) {
                playerId = id;
                playerColor = color;
                DEBUG_LOG("Received CONFIG message (legacy). Player ID: " << playerId << ", Color: " << playerColor);
                
                // Register UDP address
                DEBUG_LOG("Sending UDP registration");
                sendUdpRegistration();
                
                // Request initial position
                DEBUG_LOG("Sending initial position request");
                sendPositionUpdate(0, 0);
            }
        } 
        else if (message.substr(0, 5) == "CHAT:") {
            std::string chatMessage = message.substr(5);
            chatMessages.push_back(chatMessage);
            DEBUG_LOG("Chat message (legacy): " << chatMessage);
        } 
        else if (message.substr(0, 8) == "PLAYERS:") {
            std::istringstream iss(message.substr(8));
            std::string playerData;
            std::vector<PlayerInfo> newPlayers;
            
            DEBUG_LOG("Processing legacy PLAYERS update");
            
            while (std::getline(iss, playerData, '|')) {
                std::istringstream playerIss(playerData);
                std::string id, name, color, xStr, yStr;
                
                if (std::getline(playerIss, id, ':') && 
                    std::getline(playerIss, name, ':') && 
                    std::getline(playerIss, color, ':') &&
                    std::getline(playerIss, xStr, ':') &&
                    std::getline(playerIss, yStr)) {
                    
                    PlayerInfo info;
                    info.id = id;
                    info.name = name;
                    info.color = color;
                    info.x = std::stof(xStr);
                    info.y = std::stof(yStr);
                    info.mapId = "default";
                    
                    DEBUG_LOG("Legacy player data: " << id << ", " << name << " at (" << info.x << "," << info.y << ")");
                    
                    newPlayers.push_back(info);
                }
            }
            
            players = newPlayers;
            
            // Call the registered callback if available
            if (playerListCallback) {
                DEBUG_LOG("Calling player list callback (legacy) with " << players.size() << " players");
                playerListCallback(players);
            }
            
            // Call position callback for each player
            for (const auto& player : players) {
                if (positionCallback) {
                    DEBUG_LOG("Calling position callback (legacy) for player " << player.id);
                    positionCallback(player.id, player.x, player.y);
                }
            }
            
            DEBUG_LOG("Updated player list (legacy): " << players.size() << " players");
        } 
        else if (message.substr(0, 9) == "POSITION:") {
            std::istringstream iss(message.substr(9));
            std::string id, xStr, yStr;
            
            if (std::getline(iss, id, ':') && 
                std::getline(iss, xStr, ':') && 
                std::getline(iss, yStr)) {
                
                float x = std::stof(xStr);
                float y = std::stof(yStr);
                
                DEBUG_LOG("Position update for player " << id << " (legacy): (" << x << ", " << y << ")");
                
                // Update player position in the list
                for (auto& player : players) {
                    if (player.id == id) {
                        player.x = x;
                        player.y = y;
                        break;
                    }
                }
                
                // Call the position callback if available
                if (positionCallback) {
                    DEBUG_LOG("Calling position callback (legacy) for " << id);
                    positionCallback(id, x, y);
                }
            }
        }
        else if (message == "PONG") {
            // Pong response, no action needed
            DEBUG_LOG("Received PONG from server (legacy)");
        }
        else if (message.substr(0, 14) == "UDP_REGISTERED") {
            DEBUG_LOG("UDP registration confirmed by server (legacy)");
            udpRegistered = true;
        }
        else {
            DEBUG_LOG("Failed to parse message: " << e.what());
            DEBUG_LOG("Original message: " << message);
        }
    }
}

// Send connect request
bool NetworkClient::sendConnectRequest(const std::string& playerName, const std::string& colorHex) {
    if (status != ConnectionStatus::CONNECTED) {
        std::cout << "Cannot send connect request: not connected (status: " 
                  << static_cast<int>(status) << ")" << std::endl;
        return false;
    }
    
    // Send connect request using JSON format that the server expects
    nlohmann::json request = {
        {"name", playerName},
        {"color", colorHex}
    };
    
    std::string requestStr = request.dump();
    pendingConnectName = playerName;
    
    std::cout << "Sending connect request: " << requestStr << std::endl;
    bool result = sendTcpMessage("CONNECT " + requestStr);
    std::cout << "Connect request sent: " << (result ? "success" : "failed") << std::endl;
    return result;
}

// Send position update
bool NetworkClient::sendPositionUpdate(float x, float y) {
    if (status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    // Format position with fixed precision to avoid floating point issues
    std::stringstream xStr, yStr;
    xStr << std::fixed << std::setprecision(2) << x;
    yStr << std::fixed << std::setprecision(2) << y;
    
    // Send position update using JSON format
    nlohmann::json update = {
        {"id", playerId},
        {"x", std::stof(xStr.str())},
        {"y", std::stof(yStr.str())}
    };
    
    std::string updateStr = update.dump();
    DEBUG_LOG("Sending position update: " << updateStr);
    
    // Try UDP first, but fall back to TCP if UDP not registered
    if (udpRegistered) {
        return sendUdpMessage("POSITION " + updateStr);
    } else {
        return sendTcpMessage("POSITION " + updateStr);
    }
}

// Send chat message
bool NetworkClient::sendChatMessage(const std::string& message) {
    if (status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    // Send chat message using JSON format
    nlohmann::json chatMsg = {
        {"message", message}
    };
    
    std::string chatStr = chatMsg.dump();
    DEBUG_LOG("Sending chat message: " << chatStr);
    
    return sendTcpMessage("CHAT " + chatStr);
}

// Send map change
bool NetworkClient::sendMapChange(const std::string& mapId) {
    if (status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    // Send map change request using JSON format
    nlohmann::json mapChange = {
        {"map_id", mapId}
    };
    
    std::string mapChangeStr = mapChange.dump();
    DEBUG_LOG("Sending map change: " << mapChangeStr);
    
    return sendTcpMessage("MAP_CHANGE " + mapChangeStr);
}

// Send UDP registration
bool NetworkClient::sendUdpRegistration() {
    if (status != ConnectionStatus::CONNECTED) {
        return false;
    }
    
    // Create a simple UDP packet with the player ID for the server to register
    nlohmann::json regMsg = {
        {"id", playerId}
    };
    
    std::string regStr = regMsg.dump();
    DEBUG_LOG("Sending direct UDP packet for registration: " << regStr);
    
    // Send via UDP socket directly to register the address with the server
    // Using the exact command prefix that the server expects (UDP_REGISTER)
    bool result = sendUdpMessage("UDP_REGISTER " + regStr);
    
    if (result) {
        // Try sending a few times to ensure registration goes through
        // Sometimes the first packet can be lost
        for (int i = 0; i < 3; i++) {
            sendUdpMessage("UDP_REGISTER " + regStr);
            // Small delay between attempts
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
        
        DEBUG_LOG("UDP registration sent (multiple attempts)");
        
        // Mark as registered, though server will confirm this
        udpRegistered = true;
    } else {
        DEBUG_LOG("Failed to send UDP registration");
    }
    
    return result;
} 