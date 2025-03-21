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

# Run the server with the specified ports
./gradlew run --args="-DtcpPort=$TCP_PORT -DudpPort=$UDP_PORT" 