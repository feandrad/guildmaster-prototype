#!/bin/bash

# Default port settings
TCP_PORT=9999
UDP_PORT=9998

# Process command line arguments
while getopts "t:u:" opt; do
  case $opt in
    t) TCP_PORT=$OPTARG ;;
    u) UDP_PORT=$OPTARG ;;
    *) 
      echo "Usage: $0 [-t tcp_port] [-u udp_port]"
      exit 1
      ;;
  esac
done

echo "Starting Guild Master server..."
echo "TCP Port: $TCP_PORT"
echo "UDP Port: $UDP_PORT"

# Navigate to the server directory (in case script is run from elsewhere)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Install the distribution if needed
if [ ! -d "build/install/guildmaster-server" ]; then
    echo "Installing the application..."
    ./gradlew installDist
fi

# Run the application using the generated start script
echo "Running the server..."
build/install/guildmaster-server/bin/guildmaster-server -DtcpPort=$TCP_PORT -DudpPort=$UDP_PORT 