//BungeeCord通信模块
package cn.ruofengx.TimeMachine.CosmicNexus;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

class BungeeCordMsgManager implements PluginMessageListener {
    /**
     * 使用BungeeCord插件通信
     * 
     * 初始化的同时必须使用register方法
     * 析构的同时必须使用unregister方法
     * 
     * 用于玩家跨服TP和获取服务器在线状态的API
     * 
     */

    private String serverName;
    private String[] serverList;
    private JavaPlugin plugin;

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String[] getServerList() {
        return serverList;
    }

    public void setServerList(String[] serverList) {
        for (String onlineServerName : serverList) {
            this.plugin.getLogger().info(onlineServerName);
        }
        this.serverList = serverList;
    }

    BungeeCordMsgManager(JavaPlugin p) {
        register(p);
    }

    public void register(JavaPlugin p) {
        // 注册BungeeCord插件通信消息监听器
        if (this.plugin == null) {
            this.plugin = p;
            p.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            p.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
        } else {
            this.plugin.getLogger().warning("[CosmicGate] CosmicGate has been registered!");
        }
    }

    public void unregister() {
        // 在disable时必须调用make sure to unregister the registered channels in case of a
        // reload
        if (this.plugin != null) {
            this.plugin.getLogger().warning("[CosmicGate] Unregister CosmicGate...");
            this.plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
            this.plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
            this.plugin = null;
        } else {

        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // 回调函数，接收到消息后调用
        if (!channel.equals("BungeeCord")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        switch (subchannel) {
            case "GetServer":
                this.setServerName(in.readUTF());
                break;
            case "GetServers":
                this.serverList = in.readUTF().split(", ");
                break;

            default:
                break;
        }

    }

    public static void movePlayerServer(JavaPlugin plugin, Player p, String targetServer) {
        // 对应Connect子频道
        Bukkit.getScheduler().runTask(plugin, () -> {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            p.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        });
    }

    public boolean isServerExist(String serverName) {

        if (serverList == null) {
            return false;
        }
        for (String server : this.serverList) {
            if (server.equals(serverName)) {
                return true;
            }
        }
        return false;
    }

    public void refreshServers(Player p) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServers");

        // If you don't care about the player
        // Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        // Else, specify them
        // Player player = Bukkit.getPlayerExact("Example");
        this.serverList = null;
        p.sendPluginMessage(this.plugin, "BungeeCord", out.toByteArray());
    }

}
