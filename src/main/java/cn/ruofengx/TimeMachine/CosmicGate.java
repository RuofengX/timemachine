package cn.ruofengx.TimeMachine;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CosmicGate {
    
        // Move a player to a different server in the bungee network
        @SuppressWarnings("UnstableApiUsage")
        public void movePlayerServer(JavaPlugin plugin, Player p, String targetServer) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(targetServer);
                p.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
            });
        }
}
