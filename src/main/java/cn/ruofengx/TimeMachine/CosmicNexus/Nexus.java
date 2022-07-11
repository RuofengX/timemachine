package cn.ruofengx.TimeMachine.CosmicNexus;

import java.util.Arrays;
import java.util.HashSet;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Nexus {
    private JavaPlugin plugin;
    private BungeeCordMsgManager gateway;
    private InventoryManager repository;

    public Nexus(JavaPlugin p, ConfigurationSection section) {
        ConfigurationSection mysqlSection = section.getConfigurationSection("mysql");

        this.plugin = p;
        this.gateway = new BungeeCordMsgManager(p);
        this.repository = new InventoryManager(p, mysqlSection);
    }

    public void warp(Player player, String targetServer) {
        // 异步
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {

            player.sendMessage("§a正在验证目标是否在线§e" + targetServer + "§a...");
            this.gateway.refreshServers(player);

            if (this.gateway.getServerList() == null) {
                player.sendMessage("§cBungeeCord通信出现问题，稍后再试吧~");
                return;
            }
            boolean isOnline = new HashSet<>(Arrays.asList(this.gateway.getServerList())).contains(targetServer);
            if (!isOnline) {
                player.sendMessage("§c目标服务器不在线，请稍后再试");
                return;
            }

            player.sendMessage("§a正在传送到服务器§e" + targetServer + "§a...");
            BungeeCordMsgManager.movePlayerServer(this.plugin, player, targetServer);
        });
    }

    // @Deprecated // 物品复制bug，上层调用已关闭，不会修复
    // public void whatsMyInv(Player p){
    // this.repository.whatsMyInv(p);
    // }

    public void uploadInv(Player p) {
        this.repository.uploadInventory(p);
    }

    public void downloadInv(Player p) {
        this.repository.downloadInventory(p);
    }

    public void onDisableCallback() {
        // 提供给上层插件在disable时调用
        this.gateway.unregister();
    }

}
