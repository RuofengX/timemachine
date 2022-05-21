package cn.ruofengx.TimeMachine;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.UploadResult;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.cos.transfer.Upload;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

// 定时将游戏存档上传至COS存储
public class SaveUploader extends BukkitRunnable {
    private String API_ID;
    private String API_KEY;
    private String REGION;
    private String BUCKET_NAME;
    private String folderPath;
    private int interval;
    private String PREFIX;

    public SaveUploader(ConfigurationSection section) {
        this.API_ID = section.getString("tencent-cos.secret-id");
        this.API_KEY = section.getString("tencent-cos.secret-key");
        this.REGION = section.getString("tencent-cos.region");
        this.BUCKET_NAME = section.getString("tencent-cos.bucket");
        this.folderPath = section.getString("world-folder");
        this.interval = section.getInt("interval-second");
        this.PREFIX = section.getString("backup-prefix");
    }

    public int getInterval() {
        return interval;
    }

    @Override
    public void run() {
        // What you want to schedule goes here
        this.SaveAndUpload();
    }

    private void SaveAndUpload() { // 全同步方法
        try {
            String targetFilePath = zipFolder(this.folderPath);
            uploadZipFile(targetFilePath);

        } catch (IOException e) {
            System.out.println(e.getMessage());

        }

    }

    private String zipFolder(String folderPath) throws IOException {
        // 将folderPath文件夹的所有内容打包压缩成为一个zip文件，保存在folderPath/../中

        // FIXME: 文件夹1G容量没有报错；但是20G大小的文件夹打包会导致主线程阻塞，游戏环境被看门狗关闭
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String separator = File.separator;
        File outputFile = new File(folderPath + separator + ".." + separator + sdf.format(new Date()) + ".zip");

        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                System.out.println("Error when creating zip file");
                System.out.println(e.getMessage());
            }

        }
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(outputFile));

        File inputFolder = new File(folderPath);

        zip(zipOutputStream, inputFolder, outputFile.getName());

        try {
            zipOutputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputFile.getAbsolutePath();
    }

    private void zip(ZipOutputStream zout, File target, String name) {
        // 递归压缩target文件(文件夹)到zout输出流中
        try {
            if (target.isDirectory()) {
                for (File file : target.listFiles()) {
                    zip(zout, file, name + "/" + file.getName());
                }
            } else {
                zout.putNextEntry(new ZipEntry(name));
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(target));
                int len;
                byte[] buffer = new byte[1024];
                while ((len = bis.read(buffer)) != -1) {
                    zout.write(buffer, 0, len);
                }
                bis.close();
                zout.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadZipFile(String zipFilePath) {

        COSManager cos = new COSManager(this.API_ID, this.API_KEY, this.BUCKET_NAME, this.REGION, this.PREFIX);
        File localFile = new File(zipFilePath);
        cos.Upload(localFile); // 同步方法
        localFile.delete(); // 删除本地文件

    }

}

final class COSManager {

    // COS文档https://cloud.tencent.com/document/product/436/10199
    private String API_ID;
    private String API_KEY;
    private String BUCKET_NAME;
    private String REGION_NAME;
    private String BACKUP_PREFIX;
    private TransferManager transferManager;

    COSManager(String id, String key, String bucketName, String regionName, String prefix) {
        this.API_ID = id;
        this.API_KEY = key;
        this.BUCKET_NAME = bucketName;
        this.REGION_NAME = regionName;
        this.BACKUP_PREFIX = prefix;
    }

    public void Upload(File localFile) {
        // 使用高级接口必须先保证本进程存在一个 TransferManager 实例
        this.createTransferManager();

        // 存储桶的命名格式为 BucketName-APPID，此处填写的存储桶名称必须为此格式
        String bucketName = this.BUCKET_NAME;
        // 对象键(Key)是对象在存储桶中的唯一标识。
        String key;
        if (this.BACKUP_PREFIX == "") {
            key = localFile.getName();
        } else {
            key = this.BACKUP_PREFIX + "-" + localFile.getName();
        }

        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, localFile);

        try {
            // 高级接口会返回一个异步结果Upload
            // 可同步地调用 waitForUploadResult 方法等待上传完成，成功返回UploadResult, 失败抛出异常
            Upload upload = this.transferManager.upload(putObjectRequest);
            UploadResult uploadResult = upload.waitForUploadResult();
            System.out.println("Upload Success!");
            System.out.println(uploadResult.getETag());
        } catch (CosServiceException e) {
            e.printStackTrace();
        } catch (CosClientException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 确定本进程不再使用 transferManager 实例之后，关闭之
        // 详细代码参见本页：高级接口 -> 关闭 TransferManager
        shutdownTransferManager(transferManager);
    }

    private COSClient createCOSClient(String regionName) {
        // 1 初始化用户身份信息（secretId, secretKey）。
        // SECRETID和SECRETKEY请登录访问管理控制台 https://console.cloud.tencent.com/cam/capi
        // 进行查看和管理
        COSCredentials cred = new BasicCOSCredentials(this.API_ID, this.API_KEY);
        // 2 设置 bucket 的地域, COS 地域的简称请参照
        // https://cloud.tencent.com/document/product/436/6224
        // clientConfig 中包含了设置 region, https(默认 http), 超时, 代理等 set 方法, 使用可参见源码或者常见问题
        // Java SDK 部分。
        Region region = new Region(regionName);
        ClientConfig clientConfig = new ClientConfig(region);
        // 这里建议设置使用 https 协议
        // 从 5.6.54 版本开始，默认使用了 https
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 3 生成 cos 客户端。
        COSClient cosClient = new COSClient(cred, clientConfig);
        return cosClient;
    }

    // 创建 TransferManager 实例，这个实例用来后续调用高级接口
    private void createTransferManager() {
        // 创建一个 COSClient 实例，这是访问 COS 服务的基础实例。
        COSClient cosClient = createCOSClient(this.REGION_NAME);

        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        ExecutorService threadPool = Executors.newFixedThreadPool(32);

        // 传入一个 threadpool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        this.transferManager = new TransferManager(cosClient, threadPool);

        // 设置高级接口的配置项
        // 分块上传阈值和分块大小分别为 5MB 和 1MB
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(5 * 1024 * 1024);
        transferManagerConfiguration.setMinimumUploadPartSize(1 * 1024 * 1024);
        this.transferManager.setConfiguration(transferManagerConfiguration);

    }

    private void shutdownTransferManager(TransferManager transferManager) {
        // 指定参数为 true, 则同时会关闭 transferManager 内部的 COSClient 实例。
        // 指定参数为 false, 则不会关闭 transferManager 内部的 COSClient 实例。
        this.transferManager.shutdownNow(true);
    }
}
