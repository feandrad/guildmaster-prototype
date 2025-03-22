# Guild Master

A multiplayer online game using a hybrid TCP/UDP protocol with a Kotlin server and C++ client using raylib.

## Project Structure

- `client/`: Client application written in C++ using raylib
- `server/`: Server application written in Kotlin
- `shared/`: Shared protocol documentation

## Server (Kotlin)

The server is responsible for:
- Managing player sessions
- Processing player actions
- Synchronizing game state between clients
- Handling player connections/disconnections

### Server Architecture

- Uses a hybrid TCP/UDP protocol
- TCP for reliable communication (connections, important messages)
- UDP for real-time updates (position changes)
- Maintains a session manager to track connected players
- Implements automatic cleanup of inactive sessions

### Running the Server

```bash
cd server
./run_server.sh
```

You can customize the TCP and UDP ports:
```bash
cd server
./run_server.sh -t 9999 -u 9998
```

## Client (C++)

The client is responsible for:
- Connecting to the server
- Rendering the game world
- Processing player input
- Displaying other players

### Client Architecture

- Uses raylib for rendering
- Implements the hybrid TCP/UDP protocol
- Manages connection state and player data
- Renders the local player and other connected players

### Building and Running the Client

```bash
cd client
./run_client.sh
```

You can customize the server address and ports:
```bash
cd client
./run_client.sh -s 127.0.0.1 -t 9999 -u 9998
```

Or build manually:
```bash
cd client
mkdir -p build
cd build
cmake ..
make
./guildmaster_client
```

## Communication Protocol

The client and server communicate using a text-based protocol with JSON payloads:

- `CONNECT`: Initial connection and player setup
- `CONFIG`: Player configuration updates
- `PLAYERS`: List of active players
- `POS`: Player position updates
- `CHAT`: In-game chat messages
- `MAP`: Map change requests

For detailed protocol information, see `shared/protocol.md`.

## Game Features

- Real-time multiplayer interaction
- Player customization (name, color)
- Chat functionality
- Smooth movement with client-side prediction

## Development

### Prerequisites

#### Server
- JDK 11+
- Kotlin
- Gradle

#### Client
- C++ compiler
- CMake
- raylib

### Setting Up Development Environment

#### macOS
```bash
# Server dependencies
brew install kotlin gradle

# Client dependencies
brew install raylib cmake
```

#### Windows
- Install JDK, Kotlin, and Gradle for the server
- Install CMake, a C++ compiler (like Visual Studio), and raylib for the client

## License

This project is licensed under the MIT License - see the LICENSE file for details. 