#include "player_manager.h"
#include <iostream>
#include <cmath>
#include "color_utils.h"

#define DEBUG_LOG(msg) std::cout << "[PlayerManager] " << msg << std::endl

PlayerManager::PlayerManager(int width, int height) : 
    screenWidth(width),
    screenHeight(height) {
    
    // Initialize local player
    localPlayer.x = screenWidth / 2;
    localPlayer.y = screenHeight / 2;
    localPlayer.initialPositionReceived = false;
    localPlayer.radius = 20.0f;
    localPlayer.speed = 200.0f;
    localPlayer.isActive = true;
}

// Update local player position
void PlayerManager::updateLocalPlayer(float deltaTime, bool chatInputActive) {
    if (chatInputActive) return; // Don't move when chatting
    if (!localPlayer.initialPositionReceived) return; // Don't move until initial position is received
    
    float speed = localPlayer.speed * deltaTime;
    
    // Movement keys
    if (IsKeyDown(KEY_W) || IsKeyDown(KEY_UP)) {
        localPlayer.y -= speed;
    }
    if (IsKeyDown(KEY_S) || IsKeyDown(KEY_DOWN)) {
        localPlayer.y += speed;
    }
    if (IsKeyDown(KEY_A) || IsKeyDown(KEY_LEFT)) {
        localPlayer.x -= speed;
    }
    if (IsKeyDown(KEY_D) || IsKeyDown(KEY_RIGHT)) {
        localPlayer.x += speed;
    }
    
    // Keep player within screen bounds
    if (localPlayer.x < localPlayer.radius) localPlayer.x = localPlayer.radius;
    if (localPlayer.y < localPlayer.radius) localPlayer.y = localPlayer.radius;
    if (localPlayer.x > screenWidth - localPlayer.radius) localPlayer.x = screenWidth - localPlayer.radius;
    if (localPlayer.y > screenHeight - localPlayer.radius) localPlayer.y = screenHeight - localPlayer.radius;
}

// Update player list based on server data
void PlayerManager::updatePlayers(const std::vector<PlayerInfo>& playerInfos, const std::string& localPlayerId) {
    DEBUG_LOG("Updating player list with " << playerInfos.size() << " players");
    
    bool foundLocalPlayer = false;
    
    // Update existing players and add new ones
    for (const auto& playerInfo : playerInfos) {
        DEBUG_LOG("Processing player: " << playerInfo.id << " (" << playerInfo.name << ")");
        
        // Check if this is the local player
        if (playerInfo.id == localPlayerId) {
            foundLocalPlayer = true;
            
            // If this is the first time we're seeing the local player or if server sent a position update
            if (!localPlayer.initialPositionReceived || playerInfo.x != 0.0f || playerInfo.y != 0.0f) {
                // Use the server-provided position
                localPlayer.x = playerInfo.x;
                localPlayer.y = playerInfo.y;
                localPlayer.serverX = playerInfo.x;
                localPlayer.serverY = playerInfo.y;
                localPlayer.initialPositionReceived = true;
                
                DEBUG_LOG("Using server-provided position: (" << playerInfo.x << "," << playerInfo.y << ")");
            } else {
                DEBUG_LOG("Found local player: " << playerInfo.name << " at position (" 
                          << localPlayer.x << "," << localPlayer.y << ")");
            }
        }
        // Otherwise, it's another player
        else {
            bool isNewPlayer = (players.find(playerInfo.id) == players.end());
            
            // Get reference to player (creates if not exists)
            Player& player = players[playerInfo.id];
            player.id = playerInfo.id;
            player.name = playerInfo.name;
            player.color = ColorUtils::parseColorString(playerInfo.color);
            player.mapId = playerInfo.mapId;
            player.isActive = true;
            
            // Always use the server's position for other players
            player.x = playerInfo.x;
            player.y = playerInfo.y;
            player.serverX = playerInfo.x;
            player.serverY = playerInfo.y;
            
            DEBUG_LOG("Updated position for player " << playerInfo.name << ": (" 
                      << playerInfo.x << "," << playerInfo.y << ")");
        }
    }
    
    // Prune disconnected players
    auto it = players.begin();
    while (it != players.end()) {
        bool stillActive = false;
        for (const auto& playerInfo : playerInfos) {
            if (playerInfo.id == it->first) {
                stillActive = true;
                break;
            }
        }
        
        if (!stillActive) {
            DEBUG_LOG("Removing disconnected player: " << it->second.name);
            it = players.erase(it);
        } else {
            ++it;
        }
    }
    
    // If we didn't find the local player in the list, something is wrong
    if (!foundLocalPlayer && !localPlayerId.empty()) {
        DEBUG_LOG("Warning: Local player ID " << localPlayerId << " not found in player list!");
    }
}

// Process position update from server for a specific player
void PlayerManager::processPositionUpdate(const std::string& playerId, float x, float y, const std::string& localPlayerId) {
    std::cout << "Received position update for player: " << playerId << " at position (" << x << ", " << y << ")" << std::endl;
    
    // Check if it's our own player
    if (playerId == localPlayerId) {
        // If this is the first position update received from the server
        if (!localPlayer.initialPositionReceived) {
            // Initialize player at the server's position
            localPlayer.x = x;
            localPlayer.y = y;
            localPlayer.serverX = x;
            localPlayer.serverY = y;
            localPlayer.initialPositionReceived = true;
            std::cout << "Initial position received from server: (" << x << ", " << y << ")" << std::endl;
        } else {
            // Just update the server position for correction
            localPlayer.serverX = x;
            localPlayer.serverY = y;
        }
        return;
    }
    
    // Find player
    auto it = players.find(playerId);
    if (it != players.end()) {
        // For other players, directly update their position
        it->second.x = x;
        it->second.y = y;
    }
}

// Correct player position based on server position
void PlayerManager::correctPlayerPosition() {
    // Temporarily commented out because the function is not working as expected
    /*
    // Skip correction if initial position hasn't been received yet
    if (!localPlayer.initialPositionReceived) return;
    
    // Calculate the distance between local prediction and server position
    float dx = localPlayer.serverX - localPlayer.x;
    float dy = localPlayer.serverY - localPlayer.y;
    float distance = std::sqrt(dx * dx + dy * dy);
    
    // Only correct if the player has a valid server position (has received updates)
    if (distance > 0) {
        float lerpFactor = 0.0f;
        
        // Determine correction amount based on error
        if (distance <= 5.0f) {
            // Small error: light Lerp
            lerpFactor = 0.1f;
        } else if (distance <= 15.0f) {
            // Medium error: more aggressive Lerp
            lerpFactor = 0.4f;
        } else {
            // Large error: snap immediately
            lerpFactor = 1.0f;
            
            // Log for debugging
            std::cout << "Position snapped from (" << localPlayer.x << "," << localPlayer.y 
                      << ") to (" << localPlayer.serverX << "," << localPlayer.serverY 
                      << ") - Error: " << distance << "px" << std::endl;
        }
        
        // Apply the correction
        if (lerpFactor > 0.0f) {
            localPlayer.x += dx * lerpFactor;
            localPlayer.y += dy * lerpFactor;
        }
    }
    */
} 