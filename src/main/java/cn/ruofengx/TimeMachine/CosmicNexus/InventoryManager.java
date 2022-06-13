// 多世界物品处理中心
package cn.ruofengx.TimeMachine.CosmicNexus;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public class InventoryManager {
    // 玩家TP和物品TP的接口，物品TP还在制作中
    private JavaPlugin plugin;

    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private java.sql.Connection conn = null;

    public InventoryManager(JavaPlugin p, ConfigurationSection mysqlSection) throws RuntimeException {
        this.plugin = p;// MySQL 8.0 以上版本 - JDBC 驱动名及数据库 URL

        String dbUrl = mysqlSection.getString("URL");
        String dbUsername = mysqlSection.getString("user");
        String dbPassword = mysqlSection.getString("password");
        String dbTable = "InventoryManager";

        try {
            Class.forName(JDBC_DRIVER);
            this.conn = java.sql.DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        } catch (SQLException e) {
            throw new RuntimeException("[CosmicNexus] MySQL连接出错，请检查配置文件和数据库状态");

        } catch (ClassNotFoundException e) {
            this.plugin.getServer().getLogger().info("[CosmicNexus] MySQL驱动未找到，请联系开发者");
            e.printStackTrace();
        }

        // 创建物品表
        String sql = "CREATE TABLE IF NOT EXISTS " + dbTable + " (\n"
                + "  `id` INT AUTO_INCREMENT NOT NULL,\n"
                + "  `player` TEXT NOT NULL,\n"
                + "  `item` VARBINARY(8192) NOT NULL,\n"
                + "  `world` TEXT NOT NULL,\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;";

        try {
            // 执行 SQL 查询
            java.sql.Statement stmt = this.conn.createStatement();
            stmt.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("[CosmicNexus] MySQL连接出错，请检查配置文件和数据库状态");
        } catch (Exception e) {
            this.plugin.getServer().getLogger().warning("[CosmicNexus] MySQL初始化失败，相关功能不可用");
        }

    }

    @Deprecated // 物品复制bug，上层调用已关闭，不会修复
    public void whatsMyInv(Player p) {
        // 测试用例

        this.plugin.getLogger().info("[CosmicNexus] 玩家" + p.getName() + "的背包内容如下：");

        PlayerInventory inv = p.getInventory(); // 遍历玩家全部物品
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) {
                this.plugin.getLogger()
                        .info("[CosmicNexus]" + i + ". " + item.getType().name() + " x " + item.getAmount());
                byte[] nbt = item.serializeAsBytes(); // 序列化为NBT字节串
                this.plugin.getLogger().info("序列化结果" + nbt.toString());

                this.plugin.getLogger().info("将NBT字节串反序列化为物品并给予玩家");
                ItemStack dummyItem = ItemStack.deserializeBytes(nbt);

                // 超出的部分扔到玩家的脚底下的坐标
                Map<Integer, ItemStack> restItems = p.getInventory().addItem(dummyItem);
                Location loc = p.getLocation();
                for (ItemStack itemStack : restItems.values()) {
                    p.getWorld().dropItem(loc, itemStack);
                }

            }
        }

    }

    public void uploadInventory(Player p) {
        // 上传玩家的背包到数据库
        PlayerInventory inv = p.getInventory();

        int successCount = 0;
        int errorCount = 0;
        for (int i = 0; i < inv.getSize(); i++) {

            ItemStack item = inv.getItem(i);
            if (item != null) {
                try {
                    byte[] nbt = item.serializeAsBytes();
                    String sql = "INSERT INTO InventoryManager (player, item, world) VALUES (?, ?, ?)";
                    PreparedStatement ps = this.conn.prepareStatement(sql);
                    ps.setString(1, p.getName());
                    ps.setBytes(2, nbt);
                    ps.setString(3, p.getWorld().getName());
                    int affectedRows = ps.executeUpdate();

                    if (affectedRows > 0) {
                        successCount++;
                        // 清空这个物品
                        inv.setItem(i, null);
                    } else {
                        errorCount++;
                    }

                } catch (Exception e) {
                    errorCount++;
                } finally {
                    this.conn = null;
                }
            }
        }
        p.sendMessage(successCount + "个物品上传完成 | " + errorCount + "个物品上传失败");
    }

    public void downloadInventory(Player p) {
        // 下载玩家的背包到玩家的背包

        int successCount = 0;
        int errorCount = 0;

        PlayerInventory inv = p.getInventory();
        String sql = "SELECT * FROM InventoryManager WHERE player = ?";
        try {
            PreparedStatement ps = this.conn.prepareStatement(sql);
            ps.setString(1, p.getName());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { // ResultSet的一开始的指针是在第一行之前，需要先next()才能获取到第一行；此外，next()返回false表示没有下一行了
                int id = rs.getInt("id");
                byte[] nbt = rs.getBytes("item");
                ItemStack item = ItemStack.deserializeBytes(nbt);

                // 超出的部分扔到玩家的脚底下的坐标
                Map<Integer, ItemStack> restItems = inv.addItem(item);
                Location loc = p.getLocation();
                for (ItemStack itemStack : restItems.values()) {
                    p.getWorld().dropItem(loc, itemStack);
                }

                // 从数据库清空这个物品
                PreparedStatement ps2 = this.conn.prepareStatement("DELETE FROM InventoryManager WHERE id = ?");
                ps2.setInt(1, id);
                ps2.executeUpdate();
                successCount++;
            }
        } catch (SQLException e) {
            p.sendMessage("数据库操作时出错");
            e.printStackTrace();
            errorCount++;
        } catch (Exception e) {
            p.sendMessage("未知错误");
            e.printStackTrace();
            errorCount++;
        } finally {

            this.conn = null;
        }
        p.sendMessage(successCount + "个物品下载完成 | " + errorCount + "个物品下载失败");
    }
}
