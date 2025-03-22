#pragma once

#include <raylib.h>
#include <vector>
#include <string>
#include <memory>
#include <unordered_map>
#include "network.h"
#include "ui_manager.h"
#include "player_manager.h"
#include "color_utils.h"

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
    void processPositionUpdate(const std::string& playerId, float x, float y);
    
    // Chat handling
    void processChatInput();
    
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
    
    // Synchronization
    float syncTimer = 0.0f;
    float syncInterval = 0.05f; // 50ms = 20 times per second
    float correctionTimer = 0.0f;
    float correctionInterval = 0.01f; // 10ms = 100 times per second
    
    // UI
    std::unique_ptr<UIManager> uiManager;
    bool nameInputActive = false;
    
    // Color selection
    int selectedColorIndex = 0;
    Rectangle colorButtons[ColorUtils::NUM_COLORS];
    
    // Chat
    std::vector<std::string> chatMessages;
    char chatInput[128] = { 0 };
    int chatInputLength = 0;
    bool chatInputActive = false;
    
    // Player management
    std::unique_ptr<PlayerManager> playerManager;
}; 