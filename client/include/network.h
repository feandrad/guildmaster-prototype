#pragma once

#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <condition_variable>
#include <queue>
#include <functional>
#include <memory>
#include <chrono>
#include <nlohmann/json_fwd.hpp>

// Platform-specific socket definitions
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "Ws2_32.lib")
    typedef SOCKET socket_t;
    #define INVALID_SOCKET INVALID_SOCKET
    #define SOCKET_ERROR SOCKET_ERROR
    #define closesocket closesocket
#else
    #include <sys/types.h>
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <netdb.h>
    #include <unistd.h>
    #include <errno.h>
    #include <fcntl.h>
    typedef int socket_t;
    #define INVALID_SOCKET -1
    #define SOCKET_ERROR -1
    #define closesocket close
#endif

// Forward declarations
struct Player;

// Connection status enum
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CONNECTION_FAILED
};

// Player info structure
struct PlayerInfo {
    std::string id;
    std::string name;
    std::string color;
    float x = 0.0f;
    float y = 0.0f;
    std::string mapId = "default";
};

// Callback function types
using PlayerListCallback = std::function<void(const std::vector<PlayerInfo>&)>;
using PositionCallback = std::function<void(const std::string&, float, float)>;

// Network client class for handling client-server communication
class NetworkClient {
public:
    NetworkClient();
    ~NetworkClient();
    
    // Initialize the network subsystem
    bool initialize();
    
    // Connect to server
    bool connect(const std::string& serverAddress, int tcpPort, int udpPort);
    
    // Disconnect from server
    void disconnect();
    
    // Update network state (should be called every frame)
    void update();
    
    // Send messages to server
    bool sendConnectRequest(const std::string& playerName, const std::string& colorHex);
    bool sendPositionUpdate(float x, float y);
    bool sendChatMessage(const std::string& message);
    bool sendMapChange(const std::string& mapId);
    bool sendUdpRegistration();
    
    // Register callbacks
    void setPlayerListCallback(PlayerListCallback callback) {
        playerListCallback = callback;
    }
    
    void setPositionCallback(PositionCallback callback) {
        positionCallback = callback;
    }
    
    // Getters
    ConnectionStatus getStatus() const { return status; }
    std::string getStatusMessage() const { return statusMessage; }
    std::string getPlayerId() const { return playerId; }
    const std::vector<PlayerInfo>& getPlayers() const { return players; }
    const std::vector<std::string>& getChatMessages() const { return chatMessages; }
    bool isConnected() const { return status == ConnectionStatus::CONNECTED; }
    
    // Public members for connection state
    std::string pendingConnectName;
    std::string playerColor;
    
private:
    // Socket management
    socket_t tcpSocket = -1;
    socket_t udpSocket = -1;
    struct sockaddr_in serverTcpAddr;
    struct sockaddr_in serverUdpAddr;
    
    // Connection status
    ConnectionStatus status = ConnectionStatus::DISCONNECTED;
    std::string statusMessage;
    
    // Player data
    std::string playerId;
    std::vector<PlayerInfo> players;
    std::vector<std::string> chatMessages;
    
    // Processing
    std::string tcpBuffer;
    
    // Callbacks
    PlayerListCallback playerListCallback;
    PositionCallback positionCallback;
    
    // Helper methods
    bool sendTcpMessage(const std::string& message);
    bool sendUdpMessage(const std::string& message);
    void processServerMessage(const std::string& message);
    void checkTcpMessages();
    void checkUdpMessages();
    bool setSocketNonBlocking(socket_t socket);
    bool checkTcpConnectionStatus();
    
    // Connection state
    bool tcpConnectPending = false;
    std::chrono::steady_clock::time_point connectStartTime;
    bool udpRegistered;
    
    // Connection timeout handling
    std::chrono::steady_clock::time_point lastMessageTime = std::chrono::steady_clock::now();
    std::chrono::steady_clock::time_point lastPingTime = std::chrono::steady_clock::now();
    const int connectionTimeout = 15; // seconds before considering disconnected (increased from 10)
    const int pingInterval = 3; // seconds between pings (reduced from 5)
}; 