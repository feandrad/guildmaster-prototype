# Guild Master

Guild Master is a multiplayer game featuring a client-server architecture with real-time communication between players across a network.

## Project Structure

### Server (Kotlin)
The server is implemented in Kotlin using non-blocking I/O for handling multiple client connections efficiently.

```
server/
├── src/main/kotlin/com/guildmaster/server/
│   ├── Application.kt        # Main entry point
│   ├── GameServer.kt         # Core server implementation
│   ├── Session.kt            # Player session management
│   ├── SessionManager.kt     # Session tracking and lifecycle
│   ├── Protocol.kt           # Communication protocol definitions
│   └── MessageHandler.kt     # Message processing logic
├── build.gradle.kts          # Gradle build configuration
└── ...
```

### Client (C++ with Raylib)
The client is implemented in C++ using Raylib for graphics rendering.

```
client/
├── src/
│   ├── main.cpp              # Main entry point
│   ├── game.cpp              # Game implementation
│   └── network.cpp           # Network client implementation
├── include/
│   ├── game.h                # Game definitions
│   └── network.h             # Network client definitions
├── CMakeLists.txt            # Build configuration
└── ...
```

## Communication Protocol

Guild Master uses a hybrid protocol approach:

### TCP (Reliable Communication)
- Connection handshake
- Player registration and authentication
- Chat messages
- Map changes
- Important game state updates

### UDP (Fast, Real-time Communication)
- Position updates
- Player actions
- Non-critical, frequent updates

### Message Format
Messages follow a text-based protocol with JSON payloads:

```
COMMAND_TYPE json_payload
```

Example:
```
CONNECT {"name":"Player1"}
POS {"x":100.5,"y":200.3}
CHAT {"playerId":"123","text":"Hello everyone!"}
```

## Key Features

- **Multi-player Support**: Connect multiple players across a network
- **Real-time Communication**: Fast position updates and actions
- **Map System**: Players can move between different maps
- **Chat System**: In-game communication between players
- **Session Management**: Player tracking and state persistence
- **Colorful Avatars**: Players are assigned unique colors for identification

## Building and Running

### Server

#### Prerequisites
- JDK 11+
- Gradle

#### Build and Run
Using the provided script:
```bash
./run_server.sh
```

The server script accepts optional parameters:
```bash
./run_server.sh -t <tcp_port> -u <udp_port>
```

Default ports:
- TCP: 9999
- UDP: 9998

### Client

#### Prerequisites
- C++ compiler
- CMake
- Raylib

#### Build and Run
Using the provided script:
```bash
./run_client.sh
```

The client script accepts optional parameters:
```bash
./run_client.sh -s <server_address> -t <tcp_port> -u <udp_port>
```

Default settings:
- Server: 127.0.0.1
- TCP Port: 9999
- UDP Port: 9998

Manual build and run:
```bash
cd client
mkdir build && cd build
cmake ..
make
./guildmaster_client
```

## Game Controls

- **WASD/Arrow Keys**: Move your character
- **T**: Open chat input
- **Enter**: Send chat message
- **Escape**: Cancel chat input

## Architecture

### Server Architecture

The server consists of several key components:

1. **GameServer**: Handles TCP and UDP connections, message routing
2. **SessionManager**: Manages player sessions, maps, and position tracking
3. **Protocol**: Defines message formats and serialization
4. **MessageHandler**: Processes incoming messages and generates responses

### Client Architecture

The client implements:

1. **Network Layer**: Manages TCP and UDP sockets for communication with both reliable and real-time messages
2. **Rendering Engine**: Uses Raylib for graphics display
3. **Input Handling**: Captures user input for movement and actions
4. **Game Logic**: Processes game state and updates

## Development

### Adding New Features

1. Define new message types in the Protocol
2. Implement handlers on both server and client
3. Add UI elements as needed
4. Test with multiple clients

### Protocol Extension

To add new message types:
1. Add constants to Protocol.kt
2. Create data classes for serialization
3. Implement handlers in MessageHandler
4. Add client-side support

## License

This project is licensed under the MIT License - see the LICENSE file for details. 