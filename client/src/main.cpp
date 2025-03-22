#include "game.h"
#include "player_manager.h"
#include <iostream>
#include <string>
#include <getopt.h>

void printUsage(const char* programName) {
    std::cout << "Usage: " << programName << " [options]" << std::endl;
    std::cout << "Options:" << std::endl;
    std::cout << "  -s, --server <address>   Server address (default: 127.0.0.1)" << std::endl;
    std::cout << "  -t, --tcp-port <port>    TCP port (default: 9999)" << std::endl;
    std::cout << "  -u, --udp-port <port>    UDP port (default: 9998)" << std::endl;
    std::cout << "  -h, --help               Show this help" << std::endl;
}

int main(int argc, char* argv[]) {
    // Default values
    std::string serverAddress = "127.0.0.1";
    int tcpPort = 9999;
    int udpPort = 9998;
    
    // Parse command-line arguments
    static struct option long_options[] = {
        {"server", required_argument, 0, 's'},
        {"tcp-port", required_argument, 0, 't'},
        {"udp-port", required_argument, 0, 'u'},
        {"help", no_argument, 0, 'h'},
        {0, 0, 0, 0}
    };
    
    int opt;
    int option_index = 0;
    
    while ((opt = getopt_long(argc, argv, "s:t:u:h", long_options, &option_index)) != -1) {
        switch (opt) {
            case 's':
                serverAddress = optarg;
                break;
            case 't':
                tcpPort = std::stoi(optarg);
                break;
            case 'u':
                udpPort = std::stoi(optarg);
                break;
            case 'h':
                printUsage(argv[0]);
                return 0;
            default:
                printUsage(argv[0]);
                return 1;
        }
    }
    
    // Create and initialize game
    Game game;
    game.setServerConfig(serverAddress, tcpPort, udpPort);
    game.init(800, 600, "Guild Master");
    
    // Run game loop
    game.run();
    
    return 0;
} 