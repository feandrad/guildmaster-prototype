#pragma once

#include <raylib.h>
#include <string>

class ColorUtils {
public:
    static const int NUM_COLORS = 8;
    static const Color availableColors[NUM_COLORS];
    
    // Color utility functions
    static std::string ColorToString(Color color);
    static Color parseColorString(const std::string& colorStr);
    static bool ColorEquals(Color a, Color b);
    static Color getColorFromIndex(int index);
}; 