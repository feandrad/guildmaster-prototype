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

// Platform-specific socket definitions
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "Ws2_32.lib")
    #define ISVALIDSOCKET(s) ((s) != INVALID_SOCKET)
    #define CLOSESOCKET(s) closesocket(s)
    #define GETSOCKETERRNO() (WSAGetLastError())
    typedef SOCKET socket_t;
#else
    #include <sys/types.h>
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <netdb.h>
    #include <unistd.h>
    #include <errno.h>
    #include <fcntl.h>
    #define ISVALIDSOCKET(s) ((s) >= 0)
    #define CLOSESOCKET(s) close(s)
    #define GETSOCKETERRNO() (errno)
    #define SOCKET_ERROR (-1)
    typedef int socket_t;
#endif

// Forward declarations
struct Player;

// Connection status
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CONNECTION_FAILED
};

struct PlayerInfo {
    std::string id;
    std::string name;
    std::string color;
    float x = 0.0f;
    float y = 0.0f;
    std::string mapId = "default";
};

// Network client for Guild Master
class NetworkClient {
public:
    NetworkClient();
    ~NetworkClient();
    
    // Initialization and connection
    bool initialize();
    bool connect(const std::string& serverAddress, int tcpPort, int udpPort);
    void disconnect();
    
    // Send messages
    bool sendConnectRequest(const std::string& name, const std::string& color = "RED");
    bool sendPositionUpdate(float x, float y);
    bool sendChatMessage(const std::string& message);
    bool sendMapChange(const std::string& mapId);
    bool sendConfigUpdate(const std::string& color);
    
    // Status and info
    ConnectionStatus getStatus() const { return status; }
    const std::string& getStatusMessage() const { return statusMessage; }
    const std::string& getPlayerId() const { return playerId; }
    const std::string& getPlayerColor() const { return playerColor; }
    const std::vector<PlayerInfo>& getPlayers() const { return players; }
    const std::vector<std::string>& getChatMessages() const { return chatMessages; }
    bool isConnected() const { return status == ConnectionStatus::CONNECTED; }
    bool isUdpRegistered() const { return udpRegistered; }
    
    // Update and process
    void update();
    
    // Set callback for player list updates
    using PlayerListCallback = std::function<void(const std::vector<PlayerInfo>&)>;
    void setPlayerListCallback(PlayerListCallback callback) { playerListCallback = callback; }

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
    std::string playerColor;
    std::vector<PlayerInfo> players;
    std::vector<std::string> chatMessages;
    
    // Processing
    std::string tcpBuffer;
    
    // Callbacks
    PlayerListCallback playerListCallback;
    
    // Helper methods
    bool sendTcpMessage(const std::string& message);
    bool sendUdpMessage(const std::string& message);
    void processServerMessage(const std::string& message);
    void checkTcpMessages();
    void checkUdpMessages();
    bool setSocketNonBlocking(socket_t socket);
    bool checkTcpConnectionStatus();
    
    // New member variables
    bool tcpConnectPending = false;
    std::chrono::steady_clock::time_point connectStartTime;
    std::string pendingConnectName;
    bool udpRegistered;
    
    // Connection timeout handling
    std::chrono::steady_clock::time_point lastMessageTime = std::chrono::steady_clock::now();
    std::chrono::steady_clock::time_point lastPingTime = std::chrono::steady_clock::now();
    const int connectionTimeout = 15; // seconds before considering disconnected (increased from 10)
    const int pingInterval = 3; // seconds between pings (reduced from 5)
}; 