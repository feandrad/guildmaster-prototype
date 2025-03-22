# Guild Master Debug Tools

This folder contains scripts to help debug and test the Guild Master application.

## Available Scripts

### `start_debug_session.sh`

This script starts a complete debug session with:
- A server instance in one terminal window
- Two client instances in separate terminal windows

```bash
./start_debug_session.sh
```

### `start_server.sh`

Starts only the server component in a new terminal window.

```bash
./start_server.sh
```

### `start_client.sh`

Starts a client instance in a new terminal window. You can optionally specify server address and ports.

```bash
# Start with default settings (localhost:9999,9998)
./start_client.sh

# Specify server address and ports
./start_client.sh <server_ip> <tcp_port> <udp_port>
```

Example:
```bash
./start_client.sh 192.168.1.100 9999 9998
```

## Tips for Debugging

1. Start the server first, then launch clients
2. To test network issues, try running clients from different machines
3. Each window can be closed independently when you're done testing
4. Watch the server terminal for connection logs and errors

## Requirements

- The scripts work on macOS, Linux, and Windows
- Each OS detects the appropriate terminal application automatically 