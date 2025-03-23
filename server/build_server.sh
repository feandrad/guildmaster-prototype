#!/bin/bash

TCP_PORT=9999
UDP_PORT=9998

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

echo "Building and running Guild Master server..."
echo "TCP Port: $TCP_PORT"
echo "UDP Port: $UDP_PORT"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "Running Gradle clean + installDist..."
./gradlew clean installDist || {
  echo "Gradle build failed!"
  exit 1
}

echo "Starting the server..."
build/install/guildmaster-server/bin/guildmaster-server -DtcpPort=$TCP_PORT -DudpPort=$UDP_PORT
