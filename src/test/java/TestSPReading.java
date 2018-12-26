import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestSPReading {

    public static final String shortestPathDistancesFile = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\SP\\";

    public static final int NETWORK_NODES = 4469;

    public static void main23(String[] a) throws FileNotFoundException {

        long runTime = System.nanoTime();


        int[][][] shortestPaths = new int[NETWORK_NODES][NETWORK_NODES][100];
        ArrayList<ArrayList<ArrayList<Short>>> sps = new ArrayList<>();

        for (int fromNode = 0; fromNode < NETWORK_NODES; fromNode++) {
            System.out.println("Reading folder " + fromNode);

            File folder = new File(shortestPathDistancesFile + String.format("%04d", fromNode));

            for (int toNode = 0; toNode < NETWORK_NODES; toNode++) {

                String fileName = String.format("%04d_%04d.sp", fromNode, toNode);

                try (BufferedReader br = new BufferedReader(new FileReader(folder + "\\" + fileName))) {
                    shortestPaths[fromNode][toNode] = br.lines()
                            .mapToInt(Integer::parseInt)
                            .toArray();

                    //shortestPaths[fromNode][toNode] = Arrays.array;
                    //System.out.println(Arrays.toString(shortestPaths[fromNode][toNode]));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println(fileName);


            }

        }

        System.out.println("Execution " + (System.nanoTime() - runTime) / 1000000);


    }

    public static void main(String[] a) {

        ArrayList<ArrayList<ArrayList<Short>>> sps = new ArrayList<>();


        for (short fromNode = 0; fromNode < NETWORK_NODES; fromNode++) {

            long runTime = System.nanoTime();

            sps.add(fromNode, new ArrayList<>());

            for (short toNode = 0; toNode < NETWORK_NODES; toNode++) {
                ArrayList<Short> sp = getShortestPathFromTo(fromNode, toNode);
                sps.get(fromNode).add(toNode, sp);
            }

            System.out.println(String.format("Reading folder %s (%.2f s)", fromNode, (double) (System.nanoTime() - runTime) / 1000000000));
        }
    }

    public static void updateShortestPathMatrix(ArrayList<ArrayList<ArrayList<Short>>> sps, int fromNode) {

        // File was read before
        if (sps.contains(fromNode)) return;

        // Adding first dimension
        sps.add(fromNode, new ArrayList<>());

        File folder = new File(shortestPathDistancesFile + String.format("%04d", fromNode));

        // Loop files of to Nodes
        for (int toNode = 0; toNode < NETWORK_NODES; toNode++) {

            //Adding second dimension
            sps.get(fromNode).add(toNode, new ArrayList<>());

            String fileName = String.format("%04d_%04d.sp", fromNode, toNode);

            try (BufferedReader br = new BufferedReader(new FileReader(folder + "\\" + fileName))) {

                // Read all intermediate nodes
                List<Short> result = br.lines().map(Short::parseShort).collect(Collectors.toList());

                sps.get(fromNode).get(toNode).addAll(result);
            } catch (IOException e) {
                //e.printStackTrace();
                continue;
            }
            //System.out.println(fileName);

        }
    }

    public static ArrayList<ArrayList<Short>> getShortestPathsFrom(int fromNode) {

        ArrayList<ArrayList<Short>> sps = new ArrayList<>();

        File folder = new File(shortestPathDistancesFile + String.format("%04d", fromNode));

        // Loop files of to Nodes
        for (int toNode = 0; toNode < NETWORK_NODES; toNode++) {

            //Adding second dimension
            sps.add(toNode, new ArrayList<>());

            String fileName = String.format("%04d_%04d.sp", fromNode, toNode);

            try (BufferedReader br = new BufferedReader(new FileReader(folder + "\\" + fileName))) {

                // Read all intermediate nodes
                List<Short> result = br.lines().map(Short::parseShort).collect(Collectors.toList());

                sps.get(toNode).addAll(result);

            } catch (IOException e) {
                continue;
            }

        }
        return sps;
    }

    /**
     * Get shortest path (list of network ids) between two nodes.
     *
     * @param fromNode
     * @param toNode
     * @return list of network ids, or empty list if there is no path
     */
    public static ArrayList<Short> getShortestPathFromTo(short fromNode, short toNode) {


        File folder = new File(shortestPathDistancesFile + String.format("%04d", fromNode));

        ArrayList<Short> sp = new ArrayList<>();

        String fileName = String.format("%04d_%04d.sp", fromNode, toNode);

        try (BufferedReader br = new BufferedReader(new FileReader(folder + "\\" + fileName))) {

            // Read all intermediate nodes
            List<Short> result = br.lines().map(Short::parseShort).collect(Collectors.toList());

            sp.addAll(result);

        } catch (IOException e) {
            return null;
        }

        return sp;
    }

    public static ArrayList<ArrayList<ArrayList<Short>>> initShortestPathsMatrix() {

        ArrayList<ArrayList<ArrayList<Short>>> sps = new ArrayList<>();

        // Loop folders of from nodes
        for (int fromNode = 0; fromNode < NETWORK_NODES; fromNode++) {

            long runTime = System.nanoTime();

            updateShortestPathMatrix(sps, fromNode);

            System.out.println(String.format("Reading folder %s (%.2f s)", fromNode, (double) (System.nanoTime() - runTime) / 1000000000));
        }

        return sps;
    }

    public int[] fileToArray(String fileName) throws IOException {
        List<String> list = Files.readAllLines(Paths.get(fileName));
        int[] res = new int[list.size()];
        int pos = 0;
        for (String line : list) {
            res[pos++] = Integer.parseInt(line);
        }
        return res;
    }
}

