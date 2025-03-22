#!/bin/bash

# Change to the project's root directory where this script is located
cd "$(dirname "$0")"

# Make all scripts executable
chmod +x start_server.sh
chmod +x start_client.sh

echo "Starting Guild Master debug session..."

# Start the server in a new terminal window
./start_server.sh

# Wait a moment for the server to initialize
sleep 2

# Start two client instances in separate terminal windows
./start_client.sh
./start_client.sh

echo "Debug session started successfully!"
echo "- Server is running in a separate terminal window"
echo "- Two client instances are running in separate terminal windows"
echo ""
echo "To connect with additional clients, run: ./start_client.sh"
echo "To customize client connection: ./start_client.sh <server_ip> <tcp_port> <udp_port>"
echo ""
echo "Note: Close the terminal windows manually when you're done testing." 