#include "ui_manager.h"
#include <iostream>
#include <cmath>
#include "player_manager.h"
#include "color_utils.h"

UIManager::UIManager(int width, int height) : 
    screenWidth(width), 
    screenHeight(height) {
    initUI();
}

void UIManager::initUI() {
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
}

Rectangle* UIManager::getColorButtons(Rectangle* buttons, int numColors) {
    float colorButtonSize = 30.0f;
    float colorButtonSpacing = 10.0f;
    float totalWidth = numColors * colorButtonSize + (numColors - 1) * colorButtonSpacing;
    float startX = (screenWidth - totalWidth) / 2;
    float colorButtonY = nameInputBox.y + nameInputBox.height + 30.0f;
    
    for (int i = 0; i < numColors; i++) {
        buttons[i] = {
            startX + i * (colorButtonSize + colorButtonSpacing),
            colorButtonY,
            colorButtonSize,
            colorButtonSize
        };
    }
    
    return buttons;
}

void UIManager::drawColorSelector(const Rectangle colorButtons[], const Color availableColors[], int selectedColorIndex) {
    DrawText("Choose Your Color:", static_cast<int>(colorButtons[0].x), 
             static_cast<int>(colorButtons[0].y - 30), 20, BLACK);
    
    for (int i = 0; i < 8; i++) {  // Using NUM_COLORS constant value
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

void UIManager::drawNameInputScreen(const char* nameInput, int nameLength, bool nameInputActive, 
                                    int selectedColorIndex, const Color availableColors[], 
                                    const Rectangle colorButtons[], const Player& localPlayer) {
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
    drawColorSelector(colorButtons, availableColors, selectedColorIndex);
    
    // Preview player with selected color
    DrawCircle(screenWidth/2, 
               static_cast<int>(colorButtons[0].y + 100), 
               30, localPlayer.color);
    
    // Instructions
    DrawText("Press ENTER to connect", screenWidth/2 - MeasureText("Press ENTER to connect", 20)/2, 
             static_cast<int>(colorButtons[0].y + 150), 20, DARKGRAY);
}

void UIManager::drawConnectingScreen(const std::string& statusMsg) {
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

void UIManager::drawGameScreen(const Player& localPlayer, const std::unordered_map<std::string, Player>& players, 
                              const char* nameInput, const std::vector<std::string>& chatMessages, 
                              const std::vector<std::string>& networkChatMsgs,
                              bool chatInputActive, const char* chatInput, const Rectangle& chatInputBox) {
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
    
    // Draw local player only if initial position was received
    if (localPlayer.initialPositionReceived) {
        DrawCircle(static_cast<int>(localPlayer.x), static_cast<int>(localPlayer.y), 
                  localPlayer.radius, localPlayer.color);
        
        // Draw local player name
        DrawText(nameInput, 
                static_cast<int>(localPlayer.x - MeasureText(nameInput, 16)/2), 
                static_cast<int>(localPlayer.y - localPlayer.radius - 20), 
                16, BLACK);
    } else {
        // Draw waiting message if the position hasn't been received yet
        const char* waitMessage = "Waiting for server...";
        DrawText(waitMessage, 
                screenWidth/2 - MeasureText(waitMessage, 24)/2, 
                screenHeight/2, 
                24, DARKGRAY);
    }
    
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
    for (const auto& msg : networkChatMsgs) {
        allChatMessages.push_back(msg);
    }
    
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

void UIManager::drawDisconnectedScreen(const std::string& reason) {
    ClearBackground(RAYWHITE);
    
    // Title - Red to catch attention
    const char* title = "DISCONNECTED FROM SERVER";
    int titleWidth = MeasureText(title, 30);
    DrawText(title, screenWidth / 2 - titleWidth / 2, 100, 30, RED);
    
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