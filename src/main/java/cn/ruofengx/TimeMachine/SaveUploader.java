package cn.ruofengx.TimeMachine;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// 定时将游戏存档上传至COS存储
public class SaveUploader {

    private String API_TOKEN;
    private String API_KEY;

    public SaveUploader(String configFileName) {
        getAPITokenFromFile(configFileName);
    }

    public SaveUploader() {
        this("TENCENT_KEY");
    }

    private void getAPITokenFromFile(String fileName) {
        try {
            File file = new File(fileName);
            if (!file.exists()) {
                throw new FileNotFoundException("API file not found");
            }
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("API_TOKEN")) {
                    API_TOKEN = line.split("=")[1];
                } else if (line.startsWith("API_KEY")) {
                    API_KEY = line.split("=")[1];
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void zipFolder(String folderPath) throws IOException {
        // 将folderPath文件夹的所有内容打包压缩成为一个zip文件，保存在folderPath/../中

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
    }

    private void zip(ZipOutputStream zout, File target, String name) {
        // 递归压缩target文件到zout输出流中
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

}