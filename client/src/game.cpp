#include "game.h"
#include <iostream>
#include <algorithm>
#include <cstring>
#include <sstream>
#include <iomanip>
#include <cmath>
#include <random>

// For debug logs
#define DEBUG_LOG(msg) std::cout << "[GameClient] " << msg << std::endl

// Constructor
Game::Game() : 
    state(GameState::INPUT_NAME),
    isRunning(false),
    screenWidth(800),
    screenHeight(600),
    network(nullptr),
    nameInputActive(false),
    nameLength(0),
    selectedColorIndex(0),
    chatInputActive(false),
    chatInputLength(0),
    syncTimer(0.0f),
    syncInterval(0.05f), // 50ms
    correctionTimer(0.0f),
    correctionInterval(0.01f) // 10ms
{
    // Other initialization happens in init()
}

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
    
    // Initialize UI manager
    uiManager = std::make_unique<UIManager>(screenWidth, screenHeight);
    
    // Get color buttons
    uiManager->getColorButtons(colorButtons, ColorUtils::NUM_COLORS);
    
    // Initialize player manager
    playerManager = std::make_unique<PlayerManager>(screenWidth, screenHeight);
    
    // Set default color
    playerManager->getLocalPlayer().color = ColorUtils::getColorFromIndex(selectedColorIndex);
    
    // Initialize network
    network = std::make_unique<NetworkClient>();
    if (!network->initialize()) {
        std::cerr << "Failed to initialize network" << std::endl;
        return;
    }
    
    // Set callback for player list updates
    network->setPlayerListCallback([this](const std::vector<PlayerInfo>& playerList) {
        playerManager->updatePlayers(playerList, network->getPlayerId());
    });
    
    network->setPositionCallback([this](const std::string& playerId, float x, float y) {
        playerManager->processPositionUpdate(playerId, x, y, network->getPlayerId());
    });
    
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
    // Handle user input
    handleInput();
    
    // Update network state
    if (network) {
        network->update();
        
        // Check connection status
        if (state == GameState::CONNECTING) {
            if (network->getStatus() == ConnectionStatus::CONNECTED) {
                DEBUG_LOG("Connection established, transitioning to PLAYING state");
                state = GameState::PLAYING;
            } else if (network->getStatus() == ConnectionStatus::CONNECTION_FAILED ||
                       network->getStatus() == ConnectionStatus::DISCONNECTED) {
                DEBUG_LOG("Connection failed or disconnected");
                state = GameState::DISCONNECTED;
            }
        } else if (state == GameState::PLAYING) {
            if (network->getStatus() != ConnectionStatus::CONNECTED) {
                DEBUG_LOG("Connection lost, transitioning to DISCONNECTED state");
                state = GameState::DISCONNECTED;
            }
        }
    }
    
    // State-specific updates
    if (state == GameState::PLAYING) {
        Player& localPlayer = playerManager->getLocalPlayer();
        
        // Only allow actual gameplay if we've received our initial position
        if (localPlayer.initialPositionReceived) {
            // Get delta time
            float deltaTime = GetFrameTime();
            
            // Update local player movement
            playerManager->updateLocalPlayer(deltaTime, chatInputActive);
            
            // Sync with server at regular intervals
            syncTimer += deltaTime;
            if (syncTimer >= syncInterval) {
                sendPlayerUpdate();
                syncTimer = 0;
            }
            
            // Position correction is temporarily disabled
            /*
            // Apply position corrections at regular intervals
            correctionTimer += deltaTime;
            if (correctionTimer >= correctionInterval) {
                playerManager->correctPlayerPosition();
                correctionTimer = 0;
            }
            */
        } else {
            DEBUG_LOG("Waiting for initial position from server...");
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
            uiManager->drawNameInputScreen(nameInput, nameLength, nameInputActive, 
                                          selectedColorIndex, ColorUtils::availableColors,
                                          colorButtons, playerManager->getLocalPlayer());
            break;
            
        case GameState::CONNECTING:
            uiManager->drawConnectingScreen(network ? network->getStatusMessage() : "Connecting...");
            break;
            
        case GameState::PLAYING:
            uiManager->drawGameScreen(playerManager->getLocalPlayer(), playerManager->getPlayers(),
                                     nameInput, chatMessages, network->getChatMessages(),
                                     chatInputActive, chatInput, uiManager->getChatInputBox());
            break;
            
        case GameState::DISCONNECTED:
            uiManager->drawDisconnectedScreen(network ? network->getStatusMessage() : "Unknown error");
            break;
    }
    
    EndDrawing();
}

// Handle user input
void Game::handleInput() {
    switch (state) {
        case GameState::INPUT_NAME:
            // Handle name input
            if (CheckCollisionPointRec(GetMousePosition(), uiManager->getNameInputBox())) {
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
                    // Set local player color based on selection
                    playerManager->getLocalPlayer().color = ColorUtils::getColorFromIndex(selectedColorIndex);
                    
                    // Convert color to string format for server
                    std::string colorString = ColorUtils::ColorToString(playerManager->getLocalPlayer().color);
                    
                    // Set the name in the network client before connecting
                    if (network) {
                        network->pendingConnectName = nameInput;
                        network->playerColor = colorString;
                    }
                    
                    // Attempt to connect to server
                    if (network && network->connect(serverAddress, tcpPort, udpPort)) {
                        // Connection initiated, will be checked in update()
                        state = GameState::CONNECTING;
                        std::cout << "Connection initiated, moving to CONNECTING state" << std::endl;
                    } else {
                        state = GameState::DISCONNECTED;
                    }
                }
            }
            
            // Handle color selection
            for (int i = 0; i < ColorUtils::NUM_COLORS; i++) {
                if (CheckCollisionPointRec(GetMousePosition(), colorButtons[i])) {
                    if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON)) {
                        selectedColorIndex = i;
                        playerManager->getLocalPlayer().color = ColorUtils::getColorFromIndex(selectedColorIndex);
                    }
                }
            }
            break;
            
        case GameState::PLAYING:
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
                        // Send to server
                        network->sendChatMessage(chatInput);
                        
                        // Add local copy to chat history
                        std::string senderPrefix = std::string(nameInput) + " (me): ";
                        std::string chatMsg = senderPrefix + chatInput;
                        
                        std::cout << "Adding local chat message: " << chatMsg << std::endl;
                        chatMessages.push_back(chatMsg);
                        
                        // Limit chat history size
                        if (chatMessages.size() > MAX_CHAT_MESSAGES) {
                            chatMessages.erase(chatMessages.begin());
                        }
                    }
                    chatInputActive = false;
                }
                
                // Cancel chat input
                if (IsKeyPressed(KEY_ESCAPE)) {
                    chatInputActive = false;
                }
            }
            break;
            
        case GameState::DISCONNECTED:
            // Allow retry on disconnected screen
            if (IsKeyPressed(KEY_ENTER)) {
                state = GameState::INPUT_NAME;
            }
            break;
            
        default:
            break;
    }
}

// Send player update to server
void Game::sendPlayerUpdate() {
    if (network && network->isConnected() && playerManager->getLocalPlayer().initialPositionReceived) {
        network->sendPositionUpdate(playerManager->getLocalPlayer().x, playerManager->getLocalPlayer().y);
    }
} 