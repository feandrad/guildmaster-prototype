package com.guildmaster.server.gameplay

import com.guildmaster.server.GameServer
import com.guildmaster.server.Logger
import com.guildmaster.server.session.Response
import java.util.concurrent.ConcurrentHashMap



class GameService(private val server: GameServer) {
    private val maps = ConcurrentHashMap<String, GameMap>()
    
    fun createMap(id: String): Response<GameMap> {
        return try {
            val map = GameMap(id)
            maps[id] = map
            Response.Success(map)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to create map $id" }
            Response.Error("Failed to create map")
        }
    }
    
    fun getMap(id: String): Response<GameMap> {
        return maps[id]?.let { Response.Success(it) }
            ?: Response.Error("Map not found")
    }
    
    fun removeMap(id: String): Response<Unit> {
        return try {
            maps.remove(id)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to remove map $id" }
            Response.Error("Failed to remove map")
        }
    }
    
    fun getAllMaps(): Response<List<GameMap>> {
        return try {
            Response.Success(maps.values.toList())
        } catch (e: Exception) {
            Logger.error(e) { "Failed to get all maps" }
            Response.Error("Failed to get all maps")
        }
    }
    
    fun handlePlayerJoin(playerId: String, mapId: String): Response<Unit> {
        return try {
            val map = maps[mapId] ?: return Response.Error("Map not found")
            map.addPlayer(playerId)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to handle player join: $playerId in map $mapId" }
            Response.Error("Failed to handle player join")
        }
    }
    
    fun handlePlayerLeave(playerId: String, mapId: String): Response<Unit> {
        return try {
            val map = maps[mapId] ?: return Response.Error("Map not found")
            map.removePlayer(playerId)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to handle player leave: $playerId from map $mapId" }
            Response.Error("Failed to handle player leave")
        }
    }
    
    fun handlePlayerMove(playerId: String, mapId: String, x: Float, y: Float): Response<Unit> {
        return try {
            val map = maps[mapId] ?: return Response.Error("Map not found")
            map.updatePlayerPosition(playerId, x, y)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to handle player move: $playerId in map $mapId" }
            Response.Error("Failed to handle player move")
        }
    }
    
    fun handlePlayerAction(playerId: String, mapId: String, action: String): Response<Unit> {
        return try {
            val map = maps[mapId] ?: return Response.Error("Map not found")
            map.handlePlayerAction(playerId, action)
            Response.Success(Unit)
        } catch (e: Exception) {
            Logger.error(e) { "Failed to handle player action: $playerId in map $mapId" }
            Response.Error("Failed to handle player action")
        }
    }
} 