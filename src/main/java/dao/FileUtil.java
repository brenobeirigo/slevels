package dao;

import com.google.gson.Gson;
import config.ConfigInstance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtil {

    public static String readJson(String jsonFilePath) {

        Path filePath = Paths.get(jsonFilePath);

        return readFile(filePath);
    }


    private static String readFile(Path filePath) {
        // Reading input settings
        String inputSettings = null;
        try {
            inputSettings = new String(Files.readAllBytes(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //String jsonString = gson.fromJson(inputSettings, String.class);
        return inputSettings;
    }

    public static ConfigInstance getMapFrom(String jsonFilePath) throws IOException {
        // Reading input settings
        Path filePath = Paths.get(jsonFilePath);
        String inputSettings = new String(Files.readAllBytes(filePath));
        Gson gson = new Gson();
        return gson.fromJson(inputSettings, ConfigInstance.class);
    }

    public static void createDir(String dir) {
        /*
         * Use createDirectories method of Files class
         * to create a directory along with all the
         * non-existent parent directories
         */

        //Path of the directory
        Path dirsPath = Paths.get(dir);

        try {

            Files.createDirectories(dirsPath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
