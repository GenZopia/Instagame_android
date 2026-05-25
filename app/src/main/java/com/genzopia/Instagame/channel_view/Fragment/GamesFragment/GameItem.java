// GameItem.java
package com.genzopia.Instagame.channel_view.Fragment.GamesFragment;

public class GameItem {
    private String gameId;
    private String title;
    private String shortDescription;
    private String longDescription;
    private String thumbnailUrl;
    private String gameLink;
    private boolean expanded = false;

    public GameItem(String gameId,
                    String title,
                    String shortDescription,
                    String longDescription,
                    String thumbnailUrl,
                    String gameLink) {
        this.gameId           = gameId;
        this.title            = title;
        this.shortDescription = shortDescription;
        this.longDescription  = longDescription;
        this.thumbnailUrl     = thumbnailUrl;
        this.gameLink         = gameLink;
    }

    public String getGameId()            { return gameId; }
    public String getTitle()             { return title; }
    public String getShortDescription()  { return shortDescription; }
    public String getLongDescription()   { return longDescription; }
    public String getThumbnailUrl()      { return thumbnailUrl; }
    public String getGameLink()          { return gameLink; }
    public boolean isExpanded()          { return expanded; }
    public void   setExpanded(boolean e) { expanded = e; }
}
