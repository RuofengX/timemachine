package cn.ruofengx.TimeMachine;

import java.io.File;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

// Learn here -> https://bukkit.fandom.com/wiki/Plugin_Tutorial_(Eclipse)

public final class TimeMachine extends JavaPlugin implements Listener {

   private BukkitTask saveUploaderTask;

   @Override
   public void onEnable() {
      this.saveDefaultConfig();
      if (getConfig().getBoolean("SaveUploader.isEnabled")) {
         File pwd = new File(".");
         getLogger().info("current work folder");
         getLogger().info(pwd.getAbsolutePath());
         for (File f : pwd.listFiles()) {
            getLogger().info(f.getAbsolutePath());
         }
      }

      // startSaveUploader();
   }

   @Override
   public void onDisable() {
      // Plugin shutdown logic
      stopSaveUploader();
   }

   @Override
   public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label,
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
               } else if (args[1].equalsIgnoreCase("off")) {
                  stopSaveUploader();
               } else {
                  sender.sendMessage("§c请输入参数：on/off");
               }
            }
         }
      }

      return true;
   }

   private void startSaveUploader() {
      ConfigurationSection section = getConfig().getConfigurationSection("SaveUploader");
      if (this.saveUploaderTask == null) {
         this.saveUploaderTask = new SaveUploader(section).runTaskTimer(this, 200,
               section.getInt("interval-second") * 20);
      } else {
         this.saveUploaderTask.cancel();
         this.startSaveUploader();
      }
      ;
   }

   private void stopSaveUploader() {
      if (this.saveUploaderTask != null) {
         if (!this.saveUploaderTask.isCancelled()) {
            this.saveUploaderTask.cancel();
         }
      }
      this.saveUploaderTask = null;
   }

}
