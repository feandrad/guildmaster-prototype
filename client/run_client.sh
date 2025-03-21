#!/bin/bash

# Default server settings
SERVER="127.0.0.1"
TCP_PORT=9999
UDP_PORT=9998

# Parse command-line arguments
while getopts "s:t:u:h" opt; do
  case $opt in
    s) SERVER="$OPTARG" ;;
    t) TCP_PORT="$OPTARG" ;;
    u) UDP_PORT="$OPTARG" ;;
    h) 
       echo "Guild Master Client"
       echo "Usage: $0 [options]"
       echo "Options:"
       echo "  -s <address>  Server address (default: 127.0.0.1)"
       echo "  -t <port>     TCP port (default: 9999)"
       echo "  -u <port>     UDP port (default: 9998)"
       echo "  -h            Show this help message"
       exit 0
       ;;
    \?) echo "Invalid option -$OPTARG" >&2; exit 1 ;;
  esac
done

# Check if build directory exists, if not create it
if [ ! -d "build" ]; then
  mkdir -p build
fi

# Navigate to build directory
cd build

# Run CMake if CMakeCache.txt doesn't exist
if [ ! -f "CMakeCache.txt" ]; then
  echo "Configuring with CMake..."
  cmake ..
fi

# Compile the project
echo "Compiling client..."
make -j4

# Check if compilation was successful
if [ $? -ne 0 ]; then
  echo "Compilation failed!"
  exit 1
fi

# Run the client with server settings
echo "Starting Guild Master client..."
echo "Connecting to server: $SERVER (TCP: $TCP_PORT, UDP: $UDP_PORT)"
./guildmaster_client --server "$SERVER" --tcp-port "$TCP_PORT" --udp-port "$UDP_PORT" 