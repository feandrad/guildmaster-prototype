#include "network.h"
#include <iostream>
#include <sstream>
#include <algorithm>
#include <cstring>
#include <chrono>
#include <iomanip>
#include <random>
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
                {"user", name},
                {"color", playerColor.empty() ? "#FF0000" : playerColor}
            };
            
            std::string requestStr = request.dump();
            DEBUG_LOG("Connection established, sending connect request: CONNECT " << requestStr);
            
            // Send the connect request and wait for response
            if (!sendTcpMessage("CONNECT " + requestStr)) {
                DEBUG_LOG("Failed to send connect request");
                disconnect();
                status = ConnectionStatus::DISCONNECTED;
                statusMessage = "Failed to send connect request";
                return;
            }
            
            DEBUG_LOG("Connect request sent, waiting for response...");
        }
    }
    
    // Process messages if connected
    if (status == ConnectionStatus::CONNECTED) {
        checkTcpMessages();
        checkUdpMessages();
        
        // Check for connection timeout
        auto now = std::chrono::steady_clock::now();
        auto timeSinceLastMessage = std::chrono::duration_cast<std::chrono::seconds>(
            now - lastMessageTime).count();
        
        int timeoutSeconds = isConnected ? POST_CONNECTION_TIMEOUT_SECONDS : CONNECTION_TIMEOUT_SECONDS;
        if (timeSinceLastMessage >= timeoutSeconds) {
            DEBUG_LOG("Connection timed out after " << timeoutSeconds << " seconds");
            DEBUG_LOG("Last message time: " << std::chrono::system_clock::to_time_t(
                std::chrono::system_clock::now() - std::chrono::seconds(timeSinceLastMessage)));
            DEBUG_LOG("Current time: " << std::chrono::system_clock::to_time_t(
                std::chrono::system_clock::now()));
            disconnect();
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
    
    DEBUG_LOG("Sent TCP message (" << result << " bytes): '" << message << "'");
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
        
        // Check if the received data is blank or just whitespace
        bool isBlank = true;
        for (int i = 0; i < bytesReceived; i++) {
            if (buffer[i] != ' ' && buffer[i] != '\t' && buffer[i] != '\n' && buffer[i] != '\r') {
                isBlank = false;
                break;
            }
        }
        
        if (isBlank) {
            DEBUG_LOG("Received blank data (" << bytesReceived << " bytes)");
            return;
        }
        
        DEBUG_LOG("Raw TCP data received (" << bytesReceived << " bytes): '" << buffer << "'");
        
        // Add to buffer
        tcpBuffer += buffer;
        DEBUG_LOG("Current TCP buffer: '" << tcpBuffer << "'");
        
        // Process complete messages
        size_t pos;
        while ((pos = tcpBuffer.find('\n')) != std::string::npos) {
            std::string message = tcpBuffer.substr(0, pos);
            tcpBuffer.erase(0, pos + 1);
            
            // Process message
            if (!message.empty()) {
                DEBUG_LOG("Processing complete message: '" << message << "'");
                DEBUG_LOG("Message length: " << message.length());
                DEBUG_LOG("First 8 chars: '" << message.substr(0, 8) << "'");
                processServerMessage(message);
            }
        }
    }
    else if (bytesReceived == 0) {
        // Connection closed by server
        DEBUG_LOG("Server closed the connection");
        disconnect();
        status = ConnectionStatus::DISCONNECTED;
        statusMessage = "Server closed the connection";
    }
    else {
        // Check for socket errors
#ifdef _WIN32
        int error = WSAGetLastError();
        if (error != WSAEWOULDBLOCK) {
            DEBUG_LOG("TCP receive error: " << error);
            disconnect();
            status = ConnectionStatus::DISCONNECTED;
            statusMessage = "Connection to server lost";
        }
#else
        if (errno != EAGAIN && errno != EWOULDBLOCK) {
            DEBUG_LOG("TCP receive error: " << errno);
            disconnect();
            status = ConnectionStatus::DISCONNECTED;
            statusMessage = "Connection to server lost";
        }
#endif
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
    // Debug log the received message
    DEBUG_LOG("Processing message: '" << message << "'");
    DEBUG_LOG("Message length: " << message.length());
    DEBUG_LOG("First 8 chars: '" << message.substr(0, 8) << "'");
    
    // Check for CONNECT response
    if (message.substr(0, 8) == "CONNECT ") {
        std::string jsonStr = message.substr(8); // Skip "CONNECT " prefix
        try {
            DEBUG_LOG("Found CONNECT response, JSON part: '" << jsonStr << "'");
            
            nlohmann::json response = nlohmann::json::parse(jsonStr);
            
            if (response.contains("player") && response.contains("token")) {
                const auto& playerData = response["player"];
                playerId = playerData["id"];
                playerColor = playerData["color"];
                
                DEBUG_LOG("Received player data - ID: " << playerId << ", Color: " << playerColor);
                
                // Update player position if provided
                if (playerData.contains("position")) {
                    const auto& pos = playerData["position"];
                    float x = pos["x"];
                    float y = pos["y"];
                    
                    DEBUG_LOG("Received initial position: (" << x << ", " << y << ")");
                    
                    // Update player position through callback if set
                    if (positionCallback) {
                        positionCallback(playerId, x, y);
                    }
                } else {
                    DEBUG_LOG("No position data in CONNECT response");
                }
                
                // Store the token for future use
                token = response["token"];
                
                DEBUG_LOG("Successfully processed CONNECT response - ID: " << playerId << ", Token: " << token);
                return;
            } else {
                DEBUG_LOG("CONNECT response missing required fields. Response: " << response.dump());
            }
        } catch (const nlohmann::json::exception& e) {
            DEBUG_LOG("JSON parsing error in CONNECT response: " << std::string(e.what()));
            DEBUG_LOG("Raw JSON string: '" << jsonStr << "'");
        }
    } else {
        DEBUG_LOG("Message is not a CONNECT response. First 8 chars: '" << message.substr(0, 8) << "'");
    }
    
    // Handle other message types
    try {
        // Parse the message as JSON if it's a complex message
        nlohmann::json jsonMessage = nlohmann::json::parse(message);
        
        // Handle different types of messages based on their structure
        if (jsonMessage.contains("type")) {
            std::string messageType = jsonMessage["type"];
            
            // Handle player list updates
            if (messageType == "PLAYER_LIST") {
                players.clear();
                for (const auto& playerData : jsonMessage["players"]) {
                    PlayerInfo player;
                    player.id = playerData["id"];
                    player.name = playerData["name"];
                    player.color = playerData["color"];
                    player.x = playerData["position"]["x"];
                    player.y = playerData["position"]["y"];
                    player.mapId = playerData["mapId"];
                    players.push_back(player);
                }
                
                // Invoke player list callback if set
                if (playerListCallback) {
                    playerListCallback(players);
                }
            }
            // Add more message type handlers as needed
        }
    } catch (const nlohmann::json::exception& e) {
        // Handle JSON parsing errors
        DEBUG_LOG("JSON parsing error: " << std::string(e.what()));
    } catch (const std::exception& e) {
        // Handle other potential exceptions
        DEBUG_LOG("Message processing error: " << std::string(e.what()));
    }
}

// Send connect request
bool NetworkClient::sendConnectRequest(const std::string& playerName, const std::string& colorHex) {
    // Prepare the connect message in the expected JSON format
    nlohmann::json connectJson = {
        {"user", playerName},
        {"color", colorHex}
    };
    
    // Convert JSON to string and prepend the CONNECT command
    std::string connectMessage = "CONNECT " + connectJson.dump();
    
    // Store player details for later use
    this->pendingConnectName = playerName;
    this->playerColor = colorHex;
    
    // Send the connect message via TCP
    return sendTcpMessage(connectMessage);
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
        {"position", {
            {"x", std::stof(xStr.str())},
            {"y", std::stof(yStr.str())}
        }}
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

// Generate a unique player ID (UUID)
std::string NetworkClient::generateUniqueId() {
    // Use random device and Mersenne Twister engine for better randomness
    std::random_device rd;
    std::mt19937 gen(rd());
    
    // Create a uniform distribution for hexadecimal digits
    std::uniform_int_distribution<> dis(0, 15);
    
    // UUID format: 8-4-4-4-12 hexadecimal characters
    std::stringstream uuid;
    uuid << std::hex << std::setfill('0');
    
    // Generate each section of the UUID
    for (int i = 0; i < 32; ++i) {
        // Add hyphens at the standard positions
        if (i == 8 || i == 12 || i == 16 || i == 20) {
            uuid << '-';
        }
        
        // Generate a random hexadecimal digit
        uuid << dis(gen);
    }
    
    return uuid.str();
} 