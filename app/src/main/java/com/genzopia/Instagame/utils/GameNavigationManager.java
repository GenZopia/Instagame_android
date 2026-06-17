package com.genzopia.Instagame.utils;

/**
 * Singleton to handle game navigation from deep links.
 * Stores a pending game ID that HomeFragmentCompose can pick up and display.
 */
public class GameNavigationManager {
    private static GameNavigationManager instance;
    private String pendingGameId = null;

    private GameNavigationManager() {}

    public static synchronized GameNavigationManager getInstance() {
        if (instance == null) {
            instance = new GameNavigationManager();
        }
        return instance;
    }

    public void setPendingGameId(String gameId) {
        this.pendingGameId = gameId;
    }

    public String getPendingGameId() {
        return pendingGameId;
    }

    public String consumePendingGameId() {
        String gameId = pendingGameId;
        pendingGameId = null;
        return gameId;
    }

    public void clearPendingGameId() {
        pendingGameId = null;
    }
}
