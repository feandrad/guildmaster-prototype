#include "color_utils.h"
#include <iostream>
#include <sstream>
#include <iomanip>

// Initialize static array
const Color ColorUtils::availableColors[ColorUtils::NUM_COLORS] = {
    RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE, PINK, SKYBLUE
};

// Convert Color to string
std::string ColorUtils::ColorToString(Color color) {
    std::stringstream ss;
    ss << "#";
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(color.r);
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(color.g);
    ss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(color.b);
    return ss.str();
}

// Parse color string in #RRGGBB format
Color ColorUtils::parseColorString(const std::string& colorStr) {
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
bool ColorUtils::ColorEquals(Color a, Color b) {
    return a.r == b.r && a.g == b.g && a.b == b.b && a.a == b.a;
}

// Get Color from index
Color ColorUtils::getColorFromIndex(int index) {
    if (index >= 0 && index < NUM_COLORS) {
        return availableColors[index];
    }
    return RED; // Default
} 