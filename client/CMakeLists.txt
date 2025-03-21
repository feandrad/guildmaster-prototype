cmake_minimum_required(VERSION 3.10)
project(GuildMasterClient VERSION 0.1.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find raylib package
find_package(raylib REQUIRED)

# Include directories
include_directories(include)

# Define source files
set(SOURCES
    src/main.cpp
    src/network.cpp
    src/game.cpp
)

# Add executable
add_executable(guildmaster_client ${SOURCES})

# Include directories
target_include_directories(guildmaster_client PRIVATE include)

# Link raylib
target_link_libraries(guildmaster_client raylib)

# On macOS, also link required frameworks
if(APPLE)
    target_link_libraries(guildmaster_client "-framework IOKit")
    target_link_libraries(guildmaster_client "-framework Cocoa")
    target_link_libraries(guildmaster_client "-framework OpenGL")
endif()

# Link socket libraries on Windows
if(WIN32)
    target_link_libraries(guildmaster_client wsock32 ws2_32)
endif() 