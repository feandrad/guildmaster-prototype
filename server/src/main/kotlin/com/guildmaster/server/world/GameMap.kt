package com.guildmaster.server.world

import com.guildmaster.server.Logger
import java.util.concurrent.ConcurrentHashMap


class GameMap(val id: String) {
    private val players = ConcurrentHashMap<String, PlayerState>()
    
    fun addPlayer(playerId: String) {
        players[playerId] = PlayerState()
        Logger.info { "Player $playerId joined map $id" }
    }
    
    fun removePlayer(playerId: String) {
        players.remove(playerId)
        Logger.info { "Player $playerId left map $id" }
    }
    
    fun updatePlayerPosition(playerId: String, x: Float, y: Float) {
        players[playerId]?.let { state ->
            state.x = x
            state.y = y
        }
    }
    
    fun handlePlayerAction(playerId: String, action: String) {
        players[playerId]?.let { state ->
            state.lastAction = action
        }
    }
    
    fun getPlayerState(playerId: String): PlayerState? {
        return players[playerId]
    }
    
    fun getPlayerIds(): List<String> {
        return players.keys.toList()
    }
    
    fun getPlayerCount(): Int {
        return players.size
    }
}

data class PlayerState(
    var x: Float = 0f,
    var y: Float = 0f,
    var lastAction: String = ""
) 