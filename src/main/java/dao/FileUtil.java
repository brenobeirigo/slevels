package dao;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FileUtil {

    public static final String DISTANCE_FILES_PATH = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\SP\\";

    public static Map getMapFrom(String jsonFilePath) throws IOException {
        // Reading input settings
        Path filePath = Paths.get(jsonFilePath);
        String inputSettings = new String(Files.readAllBytes(filePath));
        Gson gson = new Gson();
        return gson.fromJson(inputSettings, Map.class);
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

    /**
     * Get shortest path (list of network ids) between two nodes from file.
     *
     * @param fromNode
     * @param toNode
     * @return list of network ids, or empty list if there is no path
     */
    public ArrayList<Short> getShortestPathBetweenODfromFile(int fromNode, int toNode) {

        File folder = new File(DISTANCE_FILES_PATH + String.format("%04d", fromNode));

        ArrayList<Short> sp = new ArrayList<>();

        String fileName = String.format("%04d_%04d.sp", fromNode, toNode);

        try (BufferedReader br = new BufferedReader(new FileReader(folder + "\\" + fileName))) {

            // Read all intermediate nodes
            List<Short> result = br.lines().map(Short::parseShort).collect(Collectors.toList());

            sp.addAll(result);

        } catch (IOException e) {
            return sp;
        }

        //System.out.println(sp);
        return sp;
    }
}
