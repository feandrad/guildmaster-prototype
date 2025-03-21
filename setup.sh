#!/bin/bash

# Install server dependencies
echo "Installing server dependencies..."
brew install kotlin gradle

# Install client dependencies
echo "Installing client dependencies..."
brew install raylib cmake curl nlohmann-json

# Setup wrapper for Gradle (server)
echo "Setting up Gradle wrapper for server..."
cd server
gradle wrapper
cd ..

# Create build directory for client
echo "Setting up build directory for client..."
cd client
mkdir -p build
cd build
cmake ..
cd ../..

echo "Setup complete! You can now build the project."
echo "To run the server: cd server && ./gradlew run"
echo "To build the client: cd client/build && make"
echo "To run the client: cd client/build && ./guildmaster_client" 