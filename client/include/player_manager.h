#pragma once

#include <raylib.h>
#include <string>
#include <unordered_map>
#include <vector>
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
    bool initialPositionReceived = false;
    
    // Server position for correction
    float serverX = 400.0f;
    float serverY = 300.0f;
};

class PlayerManager {
public:
    PlayerManager(int screenWidth, int screenHeight);
    
    // Player management
    void updateLocalPlayer(float deltaTime, bool chatInputActive);
    void updatePlayers(const std::vector<PlayerInfo>& playerInfos, const std::string& localPlayerId);
    void processPositionUpdate(const std::string& playerId, float x, float y, const std::string& localPlayerId);
    void correctPlayerPosition();
    
    // Getters
    Player& getLocalPlayer() { return localPlayer; }
    std::unordered_map<std::string, Player>& getPlayers() { return players; }
    
    // Boundary checking
    void setScreenBounds(int width, int height) {
        screenWidth = width;
        screenHeight = height;
    }
    
private:
    Player localPlayer;
    std::unordered_map<std::string, Player> players;
    int screenWidth;
    int screenHeight;
}; 