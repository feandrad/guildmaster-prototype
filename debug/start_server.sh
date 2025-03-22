#!/bin/bash

# Change to the project's root directory where this script is located
cd "$(dirname "$0")/.."

# Check if the terminal command exists
if command -v osascript &> /dev/null; then
    # macOS
    osascript -e 'tell application "Terminal" to do script "cd \"'$(pwd)'\" && cd server && ./run_server.sh; echo \"Press Enter to close\"; read"'
    echo "Server started in a new terminal window"
elif command -v gnome-terminal &> /dev/null; then
    # Linux with GNOME
    gnome-terminal -- bash -c "cd \"$(pwd)\" && cd server && ./run_server.sh; echo \"Press Enter to close\"; read"
    echo "Server started in a new terminal window"
elif command -v xterm &> /dev/null; then
    # Linux with X
    xterm -e "cd \"$(pwd)\" && cd server && ./run_server.sh; echo \"Press Enter to close\"; read" &
    echo "Server started in a new terminal window"
elif command -v start &> /dev/null; then
    # Windows
    start cmd /k "cd /d \"$(pwd)\" && cd server && run_server.bat"
    echo "Server started in a new terminal window"
else
    echo "Unable to open a new terminal window. Starting server in this window."
    cd server && ./run_server.sh
fi 