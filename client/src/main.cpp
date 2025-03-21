#include "game.h"
#include <iostream>
#include <string>

int main(int argc, char* argv[]) {
    // Default server settings
    std::string serverAddress = "127.0.0.1";
    int tcpPort = 9999;
    int udpPort = 9998;
    
    // Parse command line arguments
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        
        if (arg == "--server" || arg == "-s") {
            if (i + 1 < argc) {
                serverAddress = argv[++i];
            }
        } else if (arg == "--tcp-port" || arg == "-t") {
            if (i + 1 < argc) {
                tcpPort = std::stoi(argv[++i]);
            }
        } else if (arg == "--udp-port" || arg == "-u") {
            if (i + 1 < argc) {
                udpPort = std::stoi(argv[++i]);
            }
        } else if (arg == "--help" || arg == "-h") {
            std::cout << "Guild Master Client" << std::endl;
            std::cout << "Usage: " << argv[0] << " [options]" << std::endl;
            std::cout << "Options:" << std::endl;
            std::cout << "  -s, --server <address>    Server address (default: 127.0.0.1)" << std::endl;
            std::cout << "  -t, --tcp-port <port>     TCP port (default: 9999)" << std::endl;
            std::cout << "  -u, --udp-port <port>     UDP port (default: 9998)" << std::endl;
            std::cout << "  -h, --help                Show this help message" << std::endl;
            return 0;
        }
    }
    
    // Create game instance
    Game game;
    
    // Initialize game window
    game.init(800, 600, "Guild Master");
    
    // Set server configuration
    game.setServerConfig(serverAddress, tcpPort, udpPort);
    
    // Run the game
    game.run();
    
    return 0;
} 