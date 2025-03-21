# Guild Master Protocol

This document defines the communication protocol between the Guild Master client and server.

## Data Structures

### Game State

```json
{
  "id": "string",
  "players": [Player],
  "gameTime": long
}
```

### Player

```json
{
  "id": "string",
  "name": "string",
  "x": float,
  "y": float,
  "health": int
}
```

## API Endpoints

### List Games

- **URL**: `/api/game`
- **Method**: `GET`
- **Response**: Array of Game State objects

### Create Game

- **URL**: `/api/game`
- **Method**: `POST`
- **Body**: Game State object
- **Response**: Created Game State object

### Get Game

- **URL**: `/api/game/{id}`
- **Method**: `GET`
- **URL Params**: `id` - Game ID
- **Response**: Game State object

### Update Player

- **URL**: `/api/game/{gameId}/player/{playerId}`
- **Method**: `PUT`
- **URL Params**: 
  - `gameId` - Game ID
  - `playerId` - Player ID
- **Body**: Player object
- **Response**: Updated Game State object

## Error Responses

All API errors are returned with appropriate HTTP status codes and a string message.

Common errors:
- 400 Bad Request: Missing or invalid parameters
- 404 Not Found: Resource not found
- 500 Internal Server Error: Server-side error

## Synchronization Logic

1. Clients join a game by updating their player state.
2. Clients periodically (every 100ms) update their position.
3. Clients periodically fetch the complete game state to update other players' positions.

## Security Considerations

This is a simple demo implementation with no security measures. In a production environment, consider:
- Authentication
- Input validation
- Rate limiting
- WebSocket for more efficient real-time updates 