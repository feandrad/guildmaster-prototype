#pragma once

#include <raylib.h>
#include <vector>
#include <string>
#include <unordered_map>

// Constants
const int MAX_CHAT_MESSAGES = 50;  // Maximum number of chat messages to display

// Forward declarations
struct Player;

class UIManager {
public:
    UIManager(int screenWidth, int screenHeight);
    
    // UI initialization
    void initUI();
    
    // Screen rendering
    void drawNameInputScreen(const char* nameInput, int nameLength, bool nameInputActive, int selectedColorIndex, const Color availableColors[], const Rectangle colorButtons[], const Player& localPlayer);
    void drawConnectingScreen(const std::string& statusMsg);
    void drawGameScreen(const Player& localPlayer, const std::unordered_map<std::string, Player>& players, 
                       const char* nameInput, const std::vector<std::string>& chatMessages, 
                       const std::vector<std::string>& networkChatMsgs,
                       bool chatInputActive, const char* chatInput, const Rectangle& chatInputBox);
    void drawDisconnectedScreen(const std::string& reason);
    
    // UI elements
    void drawColorSelector(const Rectangle colorButtons[], const Color availableColors[], int selectedColorIndex);
    
    // Getters for UI elements
    Rectangle getNameInputBox() const { return nameInputBox; }
    Rectangle getChatInputBox() const { return chatInputBox; }
    Rectangle* getColorButtons(Rectangle* buttons, int numColors);
    
private:
    int screenWidth;
    int screenHeight;
    
    // UI elements
    Rectangle nameInputBox;
    Rectangle chatInputBox;
}; 