package cn.ruofengx.TimeMachine;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

// Learn here -> https://bukkit.fandom.com/wiki/Plugin_Tutorial_(Eclipse)

public final class TimeMachine extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
       getLogger().info("This is my first timemachine, it still do nothing yet.");
       Bukkit.getPluginManager().registerEvents(this, this); //Register the events
    }

    @EventHandler
    // This event is called when a player joins the server.
    public static void onPlayerJoin(PlayerJoinEvent event) {
        
       event.getPlayer().sendMessage("Welcome");
    
    }


    
}
