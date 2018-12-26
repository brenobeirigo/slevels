package simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtil {

    public static final String DISTANCE_FILES_PATH = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\SP\\";

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
