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
    #include <fcntl.h>
    #include <errno.h>
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
    bool sendConnectRequest(const std::string& playerName);
    bool sendPositionUpdate(float x, float y);
    bool sendChatMessage(const std::string& message);
    bool sendMapChange(const std::string& mapId);
    
    // Status and info
    ConnectionStatus getStatus() const { return status; }
    const std::string& getStatusMessage() const { return statusMessage; }
    const std::string& getPlayerId() const { return playerId; }
    const std::vector<PlayerInfo>& getPlayers() const { return players; }
    
    // Update and process
    void update();
    
    // Set callback for player list updates
    void setPlayerListCallback(std::function<void(const std::vector<PlayerInfo>&)> callback) {
        playerListCallback = callback;
    }

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
    
    // Processing
    std::string tcpBuffer;
    
    // Callbacks
    std::function<void(const std::vector<PlayerInfo>&)> playerListCallback;
    
    // Helper methods
    bool sendTcpMessage(const std::string& message);
    bool sendUdpMessage(const std::string& message);
    void processServerMessage(const std::string& message);
    void checkTcpMessages();
    void checkUdpMessages();
    bool setSocketNonBlocking(socket_t socket);
}; 