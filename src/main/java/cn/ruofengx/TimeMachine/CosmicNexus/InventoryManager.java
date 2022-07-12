// 多世界物品处理中心
package cn.ruofengx.TimeMachine.CosmicNexus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import dev.dewy.nbt.Nbt;
import dev.dewy.nbt.tags.collection.CompoundTag;

public class InventoryManager {
    // 玩家TP和物品TP的接口，物品TP还在制作中
    private JavaPlugin plugin;
    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    private String dbTable;
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final Nbt NBT_PARSER = new Nbt();
    private final int dataVersion = this.getDataVersion();

    public InventoryManager(JavaPlugin p, ConfigurationSection mysqlSection) throws RuntimeException {
        this.plugin = p; // MySQL 8.0 以上版本 - JDBC 驱动名及数据库 URL
        this.dbUrl = mysqlSection.getString("URL");
        this.dbUsername = mysqlSection.getString("user");
        this.dbPassword = mysqlSection.getString("password");
        this.dbTable = "InventoryManager";

        java.sql.Connection conn = this.createConnection();

        // 创建物品表
        String sql = "CREATE TABLE IF NOT EXISTS " + dbTable
                + " (\n"
                + "  `id` INT AUTO_INCREMENT NOT NULL,\n"
                + "  `player` TEXT NOT NULL,\n"
                + "  `item` VARBINARY(8192) NOT NULL,\n"
                + "  `world` TEXT NOT NULL,\n"
                + "  PRIMARY KEY (`id`)\n"
                + " ) ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4;";

        try {
            // 执行 SQL 查询
            java.sql.Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
            stmt.close(); // 释放资源

        } catch (SQLException e) {
            e.printStackTrace();
            this.plugin.getServer().getLogger().warning(e.getMessage());
            throw new RuntimeException("数据库连接出错，请检查配置文件和数据库状态");
        } catch (Exception e) {
            this.plugin.getServer().getLogger().warning("数据库初始化失败，相关功能不可用");
        }
        this.closeConnection(conn);

    }

    // @Deprecated // 物品复制bug，上层调用已关闭，不会修复
    // public void whatsMyInv(Player p) {
    // // 测试用例

    // this.plugin.getLogger().info("[CosmicNexus] 玩家" + p.getName() + "的背包内容如下：");

    // PlayerInventory inv = p.getInventory(); // 遍历玩家全部物品
    // for (int i = 0; i < inv.getSize(); i++) {
    // ItemStack item = inv.getItem(i);
    // if (item != null) {
    // this.plugin.getLogger()
    // .info("[CosmicNexus]" + i + ". " + item.getType().name() + " x " +
    // item.getAmount());
    // byte[] nbt = item.serializeAsBytes(); // 序列化为NBT字节串
    // this.plugin.getLogger().info("序列化结果" + nbt.toString());

    // this.plugin.getLogger().info("将NBT字节串反序列化为物品并给予玩家");
    // ItemStack dummyItem = ItemStack.deserializeBytes(nbt);

    // // 超出的部分扔到玩家的脚底下的坐标
    // Map<Integer, ItemStack> restItems = p.getInventory().addItem(dummyItem);
    // Location loc = p.getLocation();
    // for (ItemStack itemStack : restItems.values()) {
    // p.getWorld().dropItem(loc, itemStack);
    // }

    // }
    // }

    // }

    public void uploadInventory(Player p) {
        // 上传玩家的背包到数据库
        PlayerInventory inv = p.getInventory();

        int successCount = 0;
        int errorCount = 0;

        Connection conn = this.createConnection();

        for (int i = 0; i < inv.getSize(); i++) {

            ItemStack item = inv.getItem(i);
            if (item != null) {
                try {
                    byte[] nbt = item.serializeAsBytes();
                    String sql = "INSERT INTO InventoryManager (player, item, world) VALUES (?, ?, ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, p.getName());
                    ps.setBytes(2, nbt);
                    ps.setString(3, p.getWorld().getName());
                    int affectedRows = ps.executeUpdate(); // TODO:可以异步操作
                    ps.close(); // 释放预处理

                    if (affectedRows > 0) {
                        successCount++;
                        // 清空这个物品
                        inv.setItem(i, null); // 无法异步的API操作
                    } else {
                        errorCount++;
                    }

                } catch (Exception e) {
                    errorCount++;

                }
            }
        }

        this.closeConnection(conn);

        p.sendMessage(successCount + "个物品上传完成 | " + errorCount + "个物品上传失败");
    }

    public void downloadInventory(Player p) {
        // 下载玩家的物品到玩家的背包

        PlayerInventory inv = p.getInventory();

        int successCount = 0;
        int errorCount = 0;

        Connection conn = this.createConnection();
        ResultSet rs = null;
        try {
            String sql = "SELECT * FROM InventoryManager WHERE player = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, p.getName());
            rs = ps.executeQuery();
            while (rs.next()) { // ResultSet的一开始的指针是在第一行之前，需要先next()才能获取到第一行；此外，next()返回false表示没有下一行了
                int id = rs.getInt("id");
                byte[] nbt = rs.getBytes("item");
                ItemStack item = null;
                try {
                    int nbtVersion = this.getNBTVersion(nbt);
                    if (nbtVersion <= 0) {
                        throw new IOException("获取物品版本信息错误");
                    }
                    if (nbtVersion > this.dataVersion) {
                        p.sendMessage("检测到版本降级，可能导致具象化物品失败（版本不兼容）");

                        if (Material.matchMaterial(this.getNBTId(nbt)) == null) {
                            // 当前版本找不到这个物品
                            throw new IllegalArgumentException("版本不兼容");
                        } else {
                            // 有这个物品，尝试强制下载
                            nbt = this.changeNBTVersion(nbt, this.dataVersion);
                        }
                    }
                    item = ItemStack.deserializeBytes(nbt); // 失败会抛出 IllegalArgumentException

                } catch (IllegalArgumentException e) {
                    p.sendMessage("具象化物品失败（版本不兼容），跳过");
                    errorCount++;
                    // e.printStackTrace();
                    continue;
                } catch (IOException e) {
                    p.sendMessage("具象化物品失败（获取物品版本信息错误），跳过");
                    errorCount++;
                    // e.printStackTrace();
                    continue;
                }
                Map<Integer, ItemStack> restItems = inv.addItem(item); // 多余的物品就会放在restItems中
                Location loc = p.getLocation();
                for (ItemStack itemStack : restItems.values()) {
                    p.getWorld().dropItem(loc, itemStack);
                }

                // 从数据库清空这一个物品，之前有异常则不会清除
                PreparedStatement ps2 = conn.prepareStatement("DELETE FROM InventoryManager WHERE id = ?");
                ps2.setInt(1, id);
                ps2.executeUpdate();
                ps2.close(); // 关闭预处理
                successCount++;
            }

        } catch (SQLException e) {
            p.sendMessage("数据库操作时出现异常");
            this.plugin.getLogger().warning(e.getMessage());
        } catch (Exception e) {
            p.sendMessage("发生错误,中止全部操作");
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                this.plugin.getLogger().warning("数据库操作时出现异常");
                this.plugin.getLogger().warning(e.getMessage());

            }

        }
        this.closeConnection(conn);
        p.sendMessage(successCount + "个物品下载完成 | " + errorCount + "个物品下载失败");
    }

    private java.sql.Connection createConnection() {
        // 新建数据库连接
        java.sql.Connection conn = null;
        try {
            Class.forName(JDBC_DRIVER);
            conn = java.sql.DriverManager.getConnection(this.dbUrl, this.dbUsername,
                    this.dbPassword);
        } catch (SQLException e) {
            this.plugin.getLogger().warning("新建数据库连接出错，请检查配置文件和数据库状态");
            this.plugin.getLogger().warning(e.getMessage());

        } catch (ClassNotFoundException e) {
            this.plugin.getServer().getLogger().info("JDBC驱动未找到，请联系开发者");
            e.printStackTrace();
        }

        return conn;

    }

    private void closeConnection(java.sql.Connection conn) {
        // 关闭数据库连接并处理异常， conn可以为空
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            this.plugin.getLogger().warning("关闭数据库连接失败");
            this.plugin.getLogger().warning(e.getMessage());
        }

    }

    private int getDataVersion() {
        // 尝试获取当前服务器DataVersion，如果获取过程中发生异常，则视作最低版本0，以求最大兼容
        return this.getNBTVersion(new ItemStack(Material.EGG).serializeAsBytes());
    }

    private CompoundTag getNBTCompound(byte[] nbt) {
        // https://github.com/BitBuf/nbt
        // 从压缩过后的nbt字节串中读取CompoundTag格式的NBT信息
        GZIPInputStream gStream = null;
        ByteArrayOutputStream out = null;

        try {
            gStream = new GZIPInputStream(new ByteArrayInputStream(nbt));
            out = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gStream.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            CompoundTag tag = NBT_PARSER.fromByteArray(out.toByteArray());
            return tag;

        } catch (IOException e) {
            // e.printStackTrace();
            return null;

        } finally {
            try {
                if (gStream != null) {
                    gStream.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                this.plugin.getLogger().info("关闭流时发生错误");
                this.plugin.getLogger().info(e.getMessage());
            }
        }

    }

    private int getNBTVersion(byte[] nbt) {
        // 从压缩过后的nbt字节串中读取DataVersion信息，如果读取失败则返回0

        CompoundTag tag = this.getNBTCompound(nbt);
        int versionInfo = tag.getInt("DataVersion").getValue();
        return versionInfo;

    }

    private String getNBTId(byte[] nbt) {
        // 从压缩过后的nbt字节串中读取DataVersion信息，如果读取失败则返回0
        CompoundTag tag = this.getNBTCompound(nbt);
        String id = tag.getString("id").getValue();
        return id;
    }

    private byte[] gzipByte(byte[] raw) {

        GZIPOutputStream gOutputStream = null;
        ByteArrayOutputStream out = null;

        try {
            out = new ByteArrayOutputStream(raw.length);
            gOutputStream = new GZIPOutputStream(out);
            gOutputStream.write(raw);

            return out.toByteArray();

        } catch (IOException e){
            return null;
        } finally {
            try {
                if (out!= null) {
                    out.close();
                }
                if (gOutputStream != null) {
                    gOutputStream.close();
                }

            } catch (IOException e) {
                this.plugin.getLogger().info("关闭流时发生错误");
                this.plugin.getLogger().info(e.getMessage());
            }
        }

    }
    private byte[] changeNBTVersion(byte[] nbt, int version) {
        // 读取
        CompoundTag tag = this.getNBTCompound(nbt);

        // 修改
        tag.putInt("DataVersion", version);

        // 输出
        try {
            return this.gzipByte(NBT_PARSER.toByteArray(tag));
        } catch (IOException e){
            return null;
        }
    }

}
