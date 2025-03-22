#include "game.h"
#include <iostream>
#include <algorithm>
#include <cstring>
#include <sstream>
#include <iomanip>

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
    
    // Initialize color buttons
    float colorButtonSize = 30.0f;
    float colorButtonSpacing = 10.0f;
    float totalWidth = NUM_COLORS * colorButtonSize + (NUM_COLORS - 1) * colorButtonSpacing;
    float startX = (screenWidth - totalWidth) / 2;
    float colorButtonY = nameInputBox.y + nameInputBox.height + 30.0f;
    
    for (int i = 0; i < NUM_COLORS; i++) {
        colorButtons[i] = {
            startX + i * (colorButtonSize + colorButtonSpacing),
            colorButtonY,
            colorButtonSize,
            colorButtonSize
        };
    }
    
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
    localPlayer.color = availableColors[selectedColorIndex];
    localPlayer.isActive = true;
    
    // Initialize network
    network = std::make_unique<NetworkClient>();
    if (!network->initialize()) {
        std::cerr << "Failed to initialize network" << std::endl;
        return;
    }
    
    // Set callback for player list updates
    network->setPlayerListCallback([this](const std::vector<PlayerInfo>& playerList) {
        this->updatePlayers(playerList);
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
    // Process network updates
    if (network) {
        network->update();
        
        // Check if we were previously connected but are now disconnected
        if (state == GameState::PLAYING && network->getStatus() == ConnectionStatus::DISCONNECTED) {
            std::cout << "Server connection lost: " << network->getStatusMessage() << std::endl;
            state = GameState::DISCONNECTED;
        }
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
        
        // Process player position updates
        updatePlayerInfo();
    }
}

// Update player information from network messages
void Game::updatePlayerInfo() {
    // Update player list from network
    auto& playerInfos = network->getPlayers();
    
    // Check for players that are no longer in the list and mark them inactive
    std::vector<std::string> activeIds;
    for (const auto& info : playerInfos) {
        activeIds.push_back(info.id);
    }
    
    // Mark players not in the current list as inactive
    for (auto& [id, player] : players) {
        player.isActive = std::find(activeIds.begin(), activeIds.end(), id) != activeIds.end();
    }
    
    // Clean up inactive players after some time (future enhancement)
}

// Process position update from server for a specific player
void Game::processPositionUpdate(const std::string& playerId, float x, float y) {
    // Skip if it's our own player
    if (playerId == network->getPlayerId()) return;
    
    // Find player
    auto it = players.find(playerId);
    if (it != players.end()) {
        // Update position
        it->second.x = x;
        it->second.y = y;
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
                    // Set local player color based on selection
                    localPlayer.color = availableColors[selectedColorIndex];
                    
                    // Convert color to string format for server
                    std::string colorString = ColorToString(localPlayer.color);
                    
                    // Attempt to connect to server
                    if (network->connect(serverAddress, tcpPort, udpPort)) {
                        // Connection initiated, will be checked in update()
                        state = GameState::CONNECTING;
                        
                        // The actual connect request will be sent once the connection is established
                        // This is handled in the NetworkClient::update method
                        network->sendConnectRequest(nameInput, colorString);
                    } else {
                        state = GameState::DISCONNECTED;
                    }
                }
            }
            
            // Handle color selection
            handleColorSelection();
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

// Handle color selection
void Game::handleColorSelection() {
    // Check if any color button was clicked
    for (int i = 0; i < NUM_COLORS; i++) {
        if (CheckCollisionPointRec(GetMousePosition(), colorButtons[i])) {
            if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON)) {
                selectedColorIndex = i;
                localPlayer.color = availableColors[selectedColorIndex];
            }
        }
    }
}

// Draw color selector
void Game::drawColorSelector() {
    DrawText("Choose Your Color:", static_cast<int>(colorButtons[0].x), 
             static_cast<int>(colorButtons[0].y - 30), 20, BLACK);
    
    for (int i = 0; i < NUM_COLORS; i++) {
        // Draw color button
        DrawRectangleRec(colorButtons[i], availableColors[i]);
        
        // Draw selection indicator
        if (i == selectedColorIndex) {
            DrawRectangleLinesEx(colorButtons[i], 3, BLACK);
        } else {
            DrawRectangleLinesEx(colorButtons[i], 1, DARKGRAY);
        }
    }
}

// Get Color from index
Color Game::getColorFromIndex(int index) {
    if (index >= 0 && index < NUM_COLORS) {
        return availableColors[index];
    }
    return RED; // Default
}

// Draw name input screen
void Game::drawNameInputScreen() {
    // Title
    DrawText("Guild Master", screenWidth/2 - MeasureText("Guild Master", 40)/2, 100, 40, BLACK);
    
    // Name input field
    DrawRectangleRec(nameInputBox, LIGHTGRAY);
    DrawRectangleLinesEx(nameInputBox, 2, nameInputActive ? DARKBLUE : GRAY);
    
    // Name input text
    DrawText(nameInput, static_cast<int>(nameInputBox.x + 10), 
             static_cast<int>(nameInputBox.y + nameInputBox.height/2 - 10), 20, BLACK);
    
    // Placeholder text if empty
    if (nameLength == 0) {
        DrawText("Enter your name...", static_cast<int>(nameInputBox.x + 10), 
                 static_cast<int>(nameInputBox.y + nameInputBox.height/2 - 10), 20, GRAY);
    }
    
    // Input cursor when active
    if (nameInputActive) {
        DrawText("_", static_cast<int>(nameInputBox.x + 10 + MeasureText(nameInput, 20)),
                 static_cast<int>(nameInputBox.y + nameInputBox.height/2 - 10), 20, DARKBLUE);
    }
    
    // Draw color selection UI
    drawColorSelector();
    
    // Preview player with selected color
    DrawCircle(screenWidth/2, 
               static_cast<int>(colorButtons[0].y + 100), 
               30, localPlayer.color);
    
    // Instructions
    DrawText("Press ENTER to connect", screenWidth/2 - MeasureText("Press ENTER to connect", 20)/2, 
             static_cast<int>(colorButtons[0].y + 150), 20, DARKGRAY);
}

// Draw connecting screen
void Game::drawConnectingScreen() {
    // Update connection status
    std::string statusMsg = "Connecting...";
    if (network) {
        statusMsg = network->getStatusMessage();
        
        // Auto transition when connected
        if (network->getStatus() == ConnectionStatus::CONNECTED) {
            state = GameState::PLAYING;
            std::cout << "Connection established, transitioning to PLAYING state" << std::endl;
        } else if (network->getStatus() == ConnectionStatus::CONNECTION_FAILED) {
            state = GameState::DISCONNECTED;
            std::cout << "Connection failed: " << statusMsg << std::endl;
        }
    }
    
    // Draw status
    DrawText("Guild Master", screenWidth/2 - MeasureText("Guild Master", 40)/2, 100, 40, BLACK);
    DrawText(statusMsg.c_str(), screenWidth/2 - MeasureText(statusMsg.c_str(), 20)/2, screenHeight/2, 20, DARKGRAY);
    
    // Draw spinner or animation to indicate ongoing connection
    static float rotation = 0.0f;
    rotation += 5.0f * GetFrameTime();
    
    // Draw a rotating circle as a loading indicator
    DrawCircleSector(Vector2{static_cast<float>(screenWidth/2), static_cast<float>(screenHeight/2 + 50)}, 20, rotation, rotation + 270, 0, DARKBLUE);
    
    // Add some visual feedback about the connection process
    DrawText("Establishing connection to server...", 
             screenWidth/2 - MeasureText("Establishing connection to server...", 16)/2, 
             screenHeight/2 + 80, 16, DARKGRAY);
}

// Draw game screen
void Game::drawGameScreen() {
    // Draw all other players
    for (const auto& [id, player] : players) {
        if (!player.isActive) continue;
        
        DrawCircle(static_cast<int>(player.x), static_cast<int>(player.y), 
                   player.radius, player.color);
        
        // Draw player name
        DrawText(player.name.c_str(), 
                 static_cast<int>(player.x - MeasureText(player.name.c_str(), 16)/2), 
                 static_cast<int>(player.y - player.radius - 20), 
                 16, BLACK);
    }
    
    // Draw local player
    DrawCircle(static_cast<int>(localPlayer.x), static_cast<int>(localPlayer.y), 
               localPlayer.radius, localPlayer.color);
    
    // Draw local player name
    DrawText(nameInput, 
             static_cast<int>(localPlayer.x - MeasureText(nameInput, 16)/2), 
             static_cast<int>(localPlayer.y - localPlayer.radius - 20), 
             16, BLACK);
    
    // Draw player count
    std::string playerCountText = "Players: " + std::to_string(players.size() + 1); // +1 for local player
    DrawText(playerCountText.c_str(), 10, 10, 20, DARKGRAY);
    
    // Draw chat input box if active
    if (chatInputActive) {
        DrawRectangleRec(chatInputBox, Fade(LIGHTGRAY, 0.7f));
        DrawRectangleLinesEx(chatInputBox, 1, DARKGRAY);
        DrawText(chatInput, static_cast<int>(chatInputBox.x + 5), 
                 static_cast<int>(chatInputBox.y + 5), 18, BLACK);
    } else {
        // Draw hint
        DrawText("Press T to chat", 10, static_cast<int>(screenHeight - 20), 16, GRAY);
    }
    
    // Create a combined list of all chat messages
    std::vector<std::string> allChatMessages;
    
    // Add local chat messages
    for (const auto& msg : chatMessages) {
        allChatMessages.push_back(msg);
    }
    
    // Add network chat messages from other players
    if (network) {
        const auto& networkChatMsgs = network->getChatMessages();
        for (const auto& msg : networkChatMsgs) {
            allChatMessages.push_back(msg);
        }
    }
    
    // Sort messages by time (since we don't have timestamps, this is approximate based on position in the vector)
    // In this case, we'll assume that more recent messages are at the end of each vector
    
    // Limit total number of messages displayed
    int maxMessagesToShow = MAX_CHAT_MESSAGES;
    if (allChatMessages.size() > maxMessagesToShow) {
        allChatMessages.erase(allChatMessages.begin(), allChatMessages.begin() + (allChatMessages.size() - maxMessagesToShow));
    }
    
    // Draw chat messages
    int msgY = static_cast<int>(screenHeight - 60);
    for (int i = allChatMessages.size() - 1; i >= 0 && msgY > 0; i--) {
        DrawText(allChatMessages[i].c_str(), 10, msgY, 16, DARKGRAY);
        msgY -= 20;
    }
}

// Draw disconnected screen
void Game::drawDisconnectedScreen() {
    ClearBackground(RAYWHITE);
    
    // Title - Red to catch attention
    const char* title = "DISCONNECTED FROM SERVER";
    int titleWidth = MeasureText(title, 30);
    DrawText(title, screenWidth / 2 - titleWidth / 2, 100, 30, RED);
    
    // Get disconnection reason
    std::string reason = "Unknown error";
    if (network) {
        reason = network->getStatusMessage();
    }
    
    // Show reason
    const char* reasonTitle = "Reason:";
    int reasonTitleWidth = MeasureText(reasonTitle, 20);
    DrawText(reasonTitle, screenWidth / 2 - reasonTitleWidth / 2, 160, 20, BLACK);
    
    int reasonWidth = MeasureText(reason.c_str(), 18);
    DrawText(reason.c_str(), screenWidth / 2 - reasonWidth / 2, 190, 18, DARKGRAY);
    
    // Instruction
    const char* instruction = "Press ENTER to return to menu";
    int instructionWidth = MeasureText(instruction, 20);
    DrawText(instruction, screenWidth / 2 - instructionWidth / 2, 300, 20, DARKBLUE);
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
    if (network && network->isConnected()) {
        network->sendPositionUpdate(localPlayer.x, localPlayer.y);
    }
}

// Update players from player info
void Game::updatePlayers(const std::vector<PlayerInfo>& playerInfos) {
    for (const auto& info : playerInfos) {
        // Check if it's the local player to update local color
        if (info.id == network->getPlayerId()) {
            // Update local player color from server if needed
            Color serverColor = parseColorString(info.color);
            if (!ColorEquals(localPlayer.color, serverColor)) {
                localPlayer.color = serverColor;
            }
            continue;
        }
        
        // Find or create player
        if (players.find(info.id) == players.end()) {
            Player newPlayer;
            newPlayer.id = info.id;
            newPlayer.name = info.name;
            newPlayer.x = info.x;
            newPlayer.y = info.y;
            
            // Convert color from string to Color
            newPlayer.color = parseColorString(info.color);
            
            players[info.id] = newPlayer;
        } else {
            // Update existing player
            players[info.id].x = info.x;
            players[info.id].y = info.y;
            players[info.id].name = info.name;
            
            // Update color if changed
            Color newColor = parseColorString(info.color);
            if (!ColorEquals(players[info.id].color, newColor)) {
                players[info.id].color = newColor;
            }
        }
    }
}

// Parse color string in #RRGGBB format
Color Game::parseColorString(const std::string& colorStr) {
    Color result = RED; // Default color
    
    if (colorStr.length() == 7 && colorStr[0] == '#') {
        try {
            int r = std::stoi(colorStr.substr(1, 2), nullptr, 16);
            int g = std::stoi(colorStr.substr(3, 2), nullptr, 16);
            int b = std::stoi(colorStr.substr(5, 2), nullptr, 16);
            
            result = (Color){ (unsigned char)r, (unsigned char)g, (unsigned char)b, 255 };
        } catch (const std::exception& e) {
            // If parsing fails, use default color
            std::cerr << "Failed to parse color: " << colorStr << std::endl;
        }
    }
    
    return result;
}

// Check if two colors are equal
bool Game::ColorEquals(Color a, Color b) {
    return a.r == b.r && a.g == b.g && a.b == b.b && a.a == b.a;
}

// Convert Color to string
std::string Game::ColorToString(Color color) {
    std::stringstream ss;
    ss << "#";
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(color.r);
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(color.g);
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(color.b);
    return ss.str();
} 