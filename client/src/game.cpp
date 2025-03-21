#include "game.h"
#include <iostream>
#include <algorithm>
#include <cstring>

// Constructor
Game::Game() : isRunning(false), network(nullptr) {}

// Destructor
Game::~Game() {
    close();
}

// Initialize game
void Game::init(int width, int height, const std::string& title) {
    screenWidth = width;
    screenHeight = height;
    
    // Initialize Raylib
    InitWindow(screenWidth, screenHeight, title.c_str());
    SetTargetFPS(60);
    
    // Initialize UI elements
    nameInputBox = { 
        static_cast<float>(screenWidth/2 - 150), 
        static_cast<float>(screenHeight/2 - 25), 
        300.0f, 
        50.0f 
    };
    
    chatInputBox = { 
        10.0f, 
        static_cast<float>(screenHeight - 40), 
        static_cast<float>(screenWidth - 20), 
        30.0f 
    };
    
    // Initialize local player defaults
    localPlayer.x = static_cast<float>(screenWidth / 2);
    localPlayer.y = static_cast<float>(screenHeight / 2);
    localPlayer.radius = 20.0f;
    localPlayer.speed = 200.0f;
    localPlayer.color = RED;
    localPlayer.isActive = true;
    
    // Initialize network
    network = std::make_unique<NetworkClient>();
    if (!network->initialize()) {
        std::cerr << "Failed to initialize network" << std::endl;
        return;
    }
    
    isRunning = true;
}

// Main game loop
void Game::run() {
    while (isRunning && !WindowShouldClose()) {
        update();
        render();
    }
}

// Close and clean up
void Game::close() {
    if (network) {
        network->disconnect();
    }
    
    CloseWindow();
    isRunning = false;
}

// Update game state
void Game::update() {
    // Process network updates
    if (network) {
        network->update();
    }
    
    // Get delta time
    float deltaTime = GetFrameTime();
    
    // Handle input based on current state
    handleInput();
    
    // Update local player if in playing state
    if (state == GameState::PLAYING) {
        updateLocalPlayer(deltaTime);
        
        // Update sync timer
        syncTimer += deltaTime;
        if (syncTimer >= syncInterval) {
            syncTimer = 0.0f;
            sendPlayerUpdate();
        }
    }
}

// Render the game
void Game::render() {
    BeginDrawing();
    ClearBackground(RAYWHITE);
    
    // Draw based on state
    switch (state) {
        case GameState::INPUT_NAME:
            drawNameInputScreen();
            break;
            
        case GameState::CONNECTING:
            drawConnectingScreen();
            break;
            
        case GameState::PLAYING:
            drawGameScreen();
            break;
            
        case GameState::DISCONNECTED:
            drawDisconnectedScreen();
            break;
    }
    
    EndDrawing();
}

// Handle user input
void Game::handleInput() {
    switch (state) {
        case GameState::INPUT_NAME:
            // Handle name input
            if (CheckCollisionPointRec(GetMousePosition(), nameInputBox)) {
                nameInputActive = true;
                SetMouseCursor(MOUSE_CURSOR_IBEAM);
            } else if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON)) {
                nameInputActive = false;
                SetMouseCursor(MOUSE_CURSOR_DEFAULT);
            }
            
            if (nameInputActive) {
                // Get char pressed
                int key = GetCharPressed();
                
                // Check if more characters can be added
                if (key > 0 && nameLength < 30) {
                    nameInput[nameLength] = (char)key;
                    nameInput[++nameLength] = '\0';
                }
                
                // Delete character
                if (IsKeyPressed(KEY_BACKSPACE) && nameLength > 0) {
                    nameLength--;
                    nameInput[nameLength] = '\0';
                }
                
                // Submit name
                if (IsKeyPressed(KEY_ENTER) && nameLength > 0) {
                    // Connect to server
                    if (network->connect(serverAddress, tcpPort, udpPort)) {
                        state = GameState::CONNECTING;
                        network->sendConnectRequest(nameInput);
                    } else {
                        state = GameState::DISCONNECTED;
                    }
                }
            }
            break;
            
        case GameState::PLAYING:
            // Process player movement input in updateLocalPlayer
            
            // Handle chat input
            if (IsKeyPressed(KEY_T) && !chatInputActive) {
                chatInputActive = true;
                chatInputLength = 0;
                chatInput[0] = '\0';
            } else if (chatInputActive) {
                // Get char pressed
                int key = GetCharPressed();
                
                // Check if more characters can be added
                if (key > 0 && chatInputLength < 126) {
                    chatInput[chatInputLength] = (char)key;
                    chatInput[++chatInputLength] = '\0';
                }
                
                // Delete character
                if (IsKeyPressed(KEY_BACKSPACE) && chatInputLength > 0) {
                    chatInputLength--;
                    chatInput[chatInputLength] = '\0';
                }
                
                // Send chat message
                if (IsKeyPressed(KEY_ENTER)) {
                    if (chatInputLength > 0) {
                        network->sendChatMessage(chatInput);
                    }
                    chatInputActive = false;
                }
                
                // Cancel chat input
                if (IsKeyPressed(KEY_ESCAPE)) {
                    chatInputActive = false;
                }
            }
            break;
            
        default:
            break;
    }
}

// Update local player position
void Game::updateLocalPlayer(float deltaTime) {
    if (chatInputActive) return; // Don't move when chatting
    
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
    localPlayer.x = std::max(localPlayer.radius, std::min(localPlayer.x, (float)screenWidth - localPlayer.radius));
    localPlayer.y = std::max(localPlayer.radius, std::min(localPlayer.y, (float)screenHeight - localPlayer.radius));
}

// Send player update to server
void Game::sendPlayerUpdate() {
    if (network && network->getStatus() == ConnectionStatus::CONNECTED) {
        network->sendPositionUpdate(localPlayer.x, localPlayer.y);
    }
}

// Update player info from network data
void Game::updatePlayers(const std::vector<PlayerInfo>& playerInfos) {
    // Add or update players from info
    for (const auto& info : playerInfos) {
        // Skip if this is local player
        if (info.id == network->getPlayerId()) continue;
        
        // Check if player exists
        if (players.find(info.id) == players.end()) {
            // New player
            Player newPlayer;
            newPlayer.id = info.id;
            newPlayer.name = info.name;
            newPlayer.x = info.x;
            newPlayer.y = info.y;
            newPlayer.mapId = info.mapId;
            
            // Set color based on player info
            if (info.color == "#FF5252") newPlayer.color = RED;
            else if (info.color == "#4CAF50") newPlayer.color = GREEN;
            else if (info.color == "#2196F3") newPlayer.color = BLUE;
            else if (info.color == "#FFC107") newPlayer.color = YELLOW;
            else if (info.color == "#9C27B0") newPlayer.color = PURPLE;
            else newPlayer.color = ORANGE;
            
            players[info.id] = newPlayer;
        } else {
            // Update existing player
            players[info.id].name = info.name;
            players[info.id].x = info.x;
            players[info.id].y = info.y;
            players[info.id].mapId = info.mapId;
        }
    }
}

// Draw name input screen
void Game::drawNameInputScreen() {
    DrawText("Guild Master", screenWidth/2 - MeasureText("Guild Master", 40)/2, 100, 40, DARKGRAY);
    DrawText("Enter your name:", screenWidth/2 - MeasureText("Enter your name:", 20)/2, nameInputBox.y - 40, 20, DARKGRAY);
    
    // Draw name input box
    DrawRectangleRec(nameInputBox, LIGHTGRAY);
    DrawRectangleLinesEx(nameInputBox, 2, nameInputActive ? BLUE : DARKGRAY);
    DrawText(nameInput, nameInputBox.x + 10, nameInputBox.y + 15, 20, DARKGRAY);
    
    // Draw cursor when active
    if (nameInputActive) {
        DrawText("_", nameInputBox.x + 10 + MeasureText(nameInput, 20), nameInputBox.y + 15, 20, DARKGRAY);
    }
    
    // Draw instructions
    DrawText("Press ENTER to connect", screenWidth/2 - MeasureText("Press ENTER to connect", 20)/2, nameInputBox.y + 70, 20, DARKGRAY);
}

// Draw connecting screen
void Game::drawConnectingScreen() {
    DrawText("Connecting to server...", screenWidth/2 - MeasureText("Connecting to server...", 30)/2, screenHeight/2 - 15, 30, DARKGRAY);
    
    // Check connection status
    if (network) {
        if (network->getStatus() == ConnectionStatus::CONNECTED) {
            if (!network->getPlayerId().empty()) {
                // Successfully connected and registered
                localPlayer.id = network->getPlayerId();
                localPlayer.name = nameInput;
                localPlayer.isLocalPlayer = true;
                
                state = GameState::PLAYING;
            }
        } else if (network->getStatus() == ConnectionStatus::CONNECTION_FAILED) {
            state = GameState::DISCONNECTED;
        }
        
        // Display status message
        DrawText(network->getStatusMessage().c_str(), 
                 screenWidth/2 - MeasureText(network->getStatusMessage().c_str(), 20)/2, 
                 screenHeight/2 + 30, 20, DARKGRAY);
    }
}

// Draw game screen
void Game::drawGameScreen() {
    // Draw background
    DrawRectangle(0, 0, screenWidth, screenHeight, RAYWHITE);
    
    // Draw players
    for (const auto& [id, player] : players) {
        if (player.isActive) {
            DrawCircle(player.x, player.y, player.radius, player.color);
            DrawText(player.name.c_str(), player.x - MeasureText(player.name.c_str(), 20)/2, player.y - 40, 20, BLACK);
        }
    }
    
    // Draw local player
    DrawCircle(localPlayer.x, localPlayer.y, localPlayer.radius, RED);
    DrawText(localPlayer.name.c_str(), localPlayer.x - MeasureText(localPlayer.name.c_str(), 20)/2, localPlayer.y - 40, 20, BLACK);
    
    // Draw chat messages
    for (size_t i = 0; i < chatMessages.size() && i < 5; i++) {
        DrawText(chatMessages[chatMessages.size() - 1 - i].c_str(), 10, screenHeight - 80 - (i * 25), 18, DARKGRAY);
    }
    
    // Draw chat input
    if (chatInputActive) {
        DrawRectangleRec(chatInputBox, LIGHTGRAY);
        DrawRectangleLinesEx(chatInputBox, 2, BLUE);
        DrawText(chatInput, chatInputBox.x + 10, chatInputBox.y + 5, 20, DARKGRAY);
        
        // Draw cursor
        DrawText("_", chatInputBox.x + 10 + MeasureText(chatInput, 20), chatInputBox.y + 5, 20, DARKGRAY);
    } else {
        DrawText("Press T to chat", 10, screenHeight - 25, 20, DARKGRAY);
    }
    
    // Draw player info
    std::string playerCountText = "Players online: " + std::to_string(players.size() + 1); // +1 for local player
    DrawText(playerCountText.c_str(), screenWidth - MeasureText(playerCountText.c_str(), 20) - 10, 10, 20, DARKGRAY);
    
    // Draw network status
    if (network) {
        DrawText(network->getStatusMessage().c_str(), 10, 10, 20, DARKGRAY);
    }
}

// Draw disconnected screen
void Game::drawDisconnectedScreen() {
    DrawText("Disconnected from server", screenWidth/2 - MeasureText("Disconnected from server", 30)/2, screenHeight/2 - 15, 30, RED);
    
    if (network) {
        DrawText(network->getStatusMessage().c_str(), 
                 screenWidth/2 - MeasureText(network->getStatusMessage().c_str(), 20)/2, 
                 screenHeight/2 + 30, 20, DARKGRAY);
    }
    
    DrawText("Press ENTER to retry", screenWidth/2 - MeasureText("Press ENTER to retry", 20)/2, screenHeight/2 + 80, 20, DARKGRAY);
    
    if (IsKeyPressed(KEY_ENTER)) {
        state = GameState::INPUT_NAME;
    }
} 