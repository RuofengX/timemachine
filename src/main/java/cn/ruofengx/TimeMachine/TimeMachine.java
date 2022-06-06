package cn.ruofengx.TimeMachine;

import java.io.File;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

// Learn here -> https://bukkit.fandom.com/wiki/Plugin_Tutorial_(Eclipse)

public final class TimeMachine extends JavaPlugin implements Listener {

   private BukkitTask saveUploaderTask;

   @Override
   public void onEnable() {
      this.saveDefaultConfig();
      this.saveResource("data.yml", false);
      // if (getConfig().getBoolean("SaveUploader.isEnabled")) {
      // File pwd = new File(".");
      // getLogger().info("current work folder");
      // getLogger().info(pwd.getAbsolutePath());
      // for (File f : pwd.listFiles()) {
      // getLogger().info(f.getAbsolutePath());
      // }
      // }

      // startSaveUploader();
   }

   @Override
   public void onDisable() {
      // Plugin shutdown logic
      stopSaveUploader();
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label,
         String[] args) {
      if (command.getName().equalsIgnoreCase("timemachine")) {
         if (args.length == 0) {
            return false;
         } else if (args[0].equalsIgnoreCase("autoupload")) {
            if (args.length == 1) {
               sender.sendMessage("§c请输入参数：on/off");
               return true;
            } else {
               if (args[1].equalsIgnoreCase("on")) {
                  startSaveUploader();
                  return true;
               } else if (args[1].equalsIgnoreCase("off")) {
                  stopSaveUploader();
                  return true;
               } else {
                  sender.sendMessage("§c请输入参数：on/off");
               }
            }
         }
      }

      return false;
   }

   private void startSaveUploader() {
      // 如果已经在运行则直接跳过
      if (this.saveUploaderTask != null) {
         // 已经在运行，立刻返回
         this.getLogger().info("§cSaveUploader is already running!");
         return;

      }

      
      long delayTicks;
      long periodTicks;

      this.getLogger().info("§aStarting SaveUploader...");

      // 获取基本配置
      ConfigurationSection section = getConfig().getConfigurationSection("SaveUploader");
      periodTicks = section.getInt("interval-second") * 20;

      // 在正确的时间点上传

      //获取上次运行时间
      FileConfiguration data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));

      long lastUploadTimestamp = data.getLong("last-upload-timestamp-millis", 0);
      long now = System.currentTimeMillis();

      int sinceLastUpdate = (int) ((now - lastUploadTimestamp) / 1000); // 距离上一次上传的的时间，秒，可以是负数

      if (sinceLastUpdate < 0) {

         // 时间错乱，推迟到上一次标记的时间的下一个周期
         this.getLogger().warning("§时间似乎错乱了，任务将会推迟");
         delayTicks = Math.abs(sinceLastUpdate) * 20 + periodTicks;

      } else {
         
         if (sinceLastUpdate <= section.getInt("interval-second")) {
            // 小于间隔时间，需要推迟
            this.getLogger().warning("§c上次上传时间距离现在小于间隔时间，任务规划推迟");
            delayTicks = (section.getInt("interval-second") - sinceLastUpdate) * 20;
         } else {
            // 已经超过间隔时间，不需要推迟
            delayTicks = 0;
         }

      }

      // 记录当前的时间
      data.set("last-upload-timestamp-millis", System.currentTimeMillis());

      // 规划任务
      this.saveUploaderTask = new SaveUploader(section).runTaskTimerAsynchronously(this, delayTicks, periodTicks);

   }

   private void stopSaveUploader() {
      if (this.saveUploaderTask != null) {
         this.saveUploaderTask.cancel();
         this.saveUploaderTask = null;
      }
   }

}
