#!/bin/bash

# Change to the project's root directory where this script is located
cd "$(dirname "$0")/.."

# Optional parameters
SERVER_ADDR=${1:-"127.0.0.1"}
TCP_PORT=${2:-9999}
UDP_PORT=${3:-9998}

# Check if the terminal command exists
if command -v osascript &> /dev/null; then
    # macOS
    osascript -e 'tell application "Terminal" to do script "cd \"'$(pwd)'\" && cd client && ./run_client.sh -s '$SERVER_ADDR' -t '$TCP_PORT' -u '$UDP_PORT'; echo \"Press Enter to close\"; read"'
    echo "Client started in a new terminal window"
elif command -v gnome-terminal &> /dev/null; then
    # Linux with GNOME
    gnome-terminal -- bash -c "cd \"$(pwd)\" && cd client && ./run_client.sh -s '$SERVER_ADDR' -t '$TCP_PORT' -u '$UDP_PORT'; echo \"Press Enter to close\"; read"
    echo "Client started in a new terminal window"
elif command -v xterm &> /dev/null; then
    # Linux with X
    xterm -e "cd \"$(pwd)\" && cd client && ./run_client.sh -s '$SERVER_ADDR' -t '$TCP_PORT' -u '$UDP_PORT'; echo \"Press Enter to close\"; read" &
    echo "Client started in a new terminal window"
elif command -v start &> /dev/null; then
    # Windows
    start cmd /k "cd /d \"$(pwd)\" && cd client && run_client.bat -s '$SERVER_ADDR' -t '$TCP_PORT' -u '$UDP_PORT'"
    echo "Client started in a new terminal window"
else
    echo "Unable to open a new terminal window. Starting client in this window."
    cd client && ./run_client.sh -s "$SERVER_ADDR" -t "$TCP_PORT" -u "$UDP_PORT"
fi 