#pragma once

#include <raylib.h>
#include <vector>
#include <string>
#include <memory>
#include <unordered_map>
#include "network.h"

// Player representation in the game
struct Player {
    std::string id;
    std::string name;
    float x = 400.0f;
    float y = 300.0f;
    Color color = RED;
    std::string mapId = "default";
    
    // Movement
    float speed = 200.0f;
    
    // Visual
    float radius = 20.0f;
    
    // State
    bool isLocalPlayer = false;
    bool isActive = true;
};

// Game state enumeration
enum class GameState {
    INPUT_NAME,
    CONNECTING,
    PLAYING,
    DISCONNECTED
};

// Main game class
class Game {
public:
    Game();
    ~Game();
    
    // Core functions
    void init(int width, int height, const std::string& title);
    void run();
    void close();
    
    // Server configuration
    void setServerConfig(const std::string& address, int tcp, int udp) {
        serverAddress = address;
        tcpPort = tcp;
        udpPort = udp;
    }
    
private:
    // Game loop functions
    void update();
    void render();
    void handleInput();
    
    // Game states
    void drawNameInputScreen();
    void drawConnectingScreen();
    void drawGameScreen();
    void drawDisconnectedScreen();
    
    // Network update
    void updatePlayerInfo();
    void sendPlayerUpdate();
    
    // Player management
    void updateLocalPlayer(float deltaTime);
    void updatePlayers(const std::vector<PlayerInfo>& playerInfos);
    void processPlayerInput();
    
    // Variables
    GameState state = GameState::INPUT_NAME;
    bool isRunning = false;
    int screenWidth = 800;
    int screenHeight = 600;
    
    // Network
    std::unique_ptr<NetworkClient> network;
    
    // Game data
    std::string serverAddress = "127.0.0.1";
    int tcpPort = 9999;
    int udpPort = 9998;
    char nameInput[32] = { 0 };
    int nameLength = 0;
    
    // Player data
    Player localPlayer;
    std::unordered_map<std::string, Player> players;
    
    // Synchronization
    float syncTimer = 0.0f;
    float syncInterval = 0.05f; // 50ms
    
    // UI
    Rectangle nameInputBox = { 0 };
    bool nameInputActive = false;
    
    // Chat
    std::vector<std::string> chatMessages;
    char chatInput[128] = { 0 };
    int chatInputLength = 0;
    bool chatInputActive = false;
    Rectangle chatInputBox = { 0 };
}; 