# Guild Master Server

The server-side component of the Guild Master multiplayer game, built with Kotlin and Ktor.

## Features

- RESTful API for game state management
- JSON serialization with kotlinx.serialization
- In-memory game state storage
- Player position synchronization
- Simple game lobby system

## Prerequisites

- Kotlin 2.1.20 or higher
- JDK 17 or higher
- Gradle (or use the included Gradle wrapper)

## Project Structure

```
server/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/
│   │   │       └── guildmaster/
│   │   │           └── server/
│   │   │               ├── Application.kt
│   │   │               └── GameRoutes.kt
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
└── build.gradle.kts
```

## Setup

To set up the server:

```bash
# Create the Gradle wrapper (if not already present)
gradle wrapper

# Build the project
./gradlew build
```

## Running the Server

```bash
./gradlew run
```

The server will start on `http://localhost:8080`.

## API Endpoints

### List All Games

```
GET /api/game
```

Returns a list of all active games.

### Create a New Game

```
POST /api/game
```

Request body:
```json
{
  "id": "game_1",
  "players": [],
  "gameTime": 0
}
```

### Get a Specific Game

```
GET /api/game/{id}
```

Returns the game state for the specified game ID.

### Update Player Position

```
PUT /api/game/{gameId}/player/{playerId}
```

Request body:
```json
{
  "id": "player_1",
  "name": "Player",
  "x": 100.0,
  "y": 200.0,
  "health": 100
}
```

## Dependencies

- Ktor 2.3.8 (Server, Serialization)
- kotlinx.serialization 1.6.3
- Logback 1.5.3

## Development

To add new dependencies, modify the `build.gradle.kts` file. 