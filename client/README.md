# Guild Master Client

The client-side component of the Guild Master multiplayer game, built with C++ and Raylib.

## Features

- 2D graphics with Raylib
- Keyboard-controlled player movement
- Real-time multiplayer interaction
- HTTP-based communication with server
- JSON data handling with nlohmann/json

## Prerequisites

- C++17 compatible compiler
- CMake 3.10 or higher
- Raylib
- libcurl
- nlohmann/json

## Project Structure

```
client/
├── include/
│   ├── game.h
│   └── network.h
├── src/
│   ├── main.cpp
│   ├── game.cpp
│   └── network.cpp
└── CMakeLists.txt
```

## Setup

### Installing Dependencies

On macOS:
```bash
brew install raylib cmake curl nlohmann-json
```

On Linux:
```bash
sudo apt install cmake libcurl4-openssl-dev
sudo apt install libasound2-dev libx11-dev libxrandr-dev libxi-dev libgl1-mesa-dev libglu1-mesa-dev libxinerama-dev libxcursor-dev libxext-dev
git clone https://github.com/raysan5/raylib.git
cd raylib && mkdir build && cd build
cmake -DBUILD_SHARED_LIBS=ON ..
make && sudo make install
sudo apt install nlohmann-json3-dev
```

### Building the Client

```bash
mkdir -p build
cd build
cmake ..
make
```

## Running the Client

From the build directory:
```bash
./guildmaster_client
```

Make sure the server is running before starting the client.

## Controls

- **WASD** or **Arrow Keys**: Move character
- **ESC**: Exit game

## Network Communication

The client communicates with the server via HTTP:
- Joins or creates a game on startup
- Periodically sends player position updates
- Fetches the latest game state to display other players

## Configuration

The server URL is set to `http://localhost:8080` by default. To change it, modify the server URL in `game.cpp`:

```cpp
network = std::make_unique<NetworkClient>("http://your-server-url:port");
```

## Dependencies

- Raylib: Graphics and input handling
- libcurl: HTTP communication
- nlohmann/json: JSON parsing

## Troubleshooting

### Connection Issues
- Ensure the server is running
- Check firewall settings
- Verify the server URL is correct

### Build Errors
- Make sure all dependencies are installed
- Check CMake version
- Ensure compiler supports C++17 