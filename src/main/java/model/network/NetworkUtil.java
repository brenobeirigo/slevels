package model.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.InstanceConfig;
import dao.Dao;
import dao.Logging;
import model.node.NodeNetwork;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.awt.geom.Point2D;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

;
public class NetworkUtil {

    // TODO read from json
    public static final int[] ZONE_IDS = new int[]{105, 116, 152, 264, 305, 354, 372, 388, 592, 612, 806, 828, 845, 869, 885, 932, 986, 1005, 1008, 1044, 1085, 1189, 1219, 1237, 1242, 1269, 1422, 1564, 1587, 1641, 1789, 1941, 2056, 2246, 2249, 2335, 2343, 2405, 2424, 2462, 2500, 2608, 2731, 2740, 2944, 2957, 2992, 3018, 3153, 3176, 3218, 3221, 3248, 3251, 3387, 3511, 3746, 3788, 3838, 3848, 3953, 3978, 4059, 4097, 4357, 4362, 4419};

    public static List<Zone> getZones(String filePath){
        Logging.logger.info("# Reading zone data from '{}'...", filePath);
        List<Zone> zones = new ArrayList<>();
        try {
            CSVParser records = CSVParser.parse(new FileReader(filePath), CSVFormat.RFC4180.withFirstRecordAsHeader());
            for (CSVRecord record : records) {
                Zone z = new Zone(Integer.parseInt(record.get("node_id")), record.get("zone"));
                zones.add(z);
            }

            Logging.logger.info("# Read {} zones.", zones.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return zones;
    }
    /***
     * Read .csv distance matrix. Assumes KxK accessibility.
     * @param filePath
     * @return Distance matrix
     */
    public static short[][] getDistanceMatrixFrom(String filePath) {

        short[][] dist_matrix = new short[Dao.numberOfNodes][Dao.numberOfNodes];

        try {
            Logging.logger.info("# Reading distance matrix from '{}'...", filePath);
            Reader in = new FileReader(filePath);

            int row = 0;

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);

            for (CSVRecord record : records) {

                int col = 0;

                for (String r : record) {

                    dist_matrix[row][col] = (short) (Double.parseDouble(r) * Dao.SPEED_FACTOR);
                    if (dist_matrix[row][col] == 0 && row != col) {
                        dist_matrix[row][col] = 1;
                        //System.out.printf("%s-%s (%s) %s%n", row, col, r,dist_matrix[row][col] );
                    }

                    // TODO Reachable within 300s
                    int maxPickupDelay = 300;
                    if (dist_matrix[row][col] < maxPickupDelay) { //Config.getInstance().qosDic.get("B").pkDelay
                    }

                    col++;
                }
                row++;
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return dist_matrix;
    }


    /**
     * Build map of "maxNumberClosestZones" associated to each zone.
     * Not all zones considered in order to reduce computation effort during rebalance.
     *
     * @param maxNumberClosestZones How many closest zones considered
     * @param distMatrix            KxK distance matrix
     * @param zoneIds               Network ids identified as zone centroids
     * @return Map of zone id to list of the closest zones
     */
    public static Map<Integer, List<Integer>> getClosestZoneMap(int maxNumberClosestZones, List<List<Double>> distMatrix, List<Integer> zoneIds) {

        Map<Integer, List<Pair<Integer, Double>>> zoneToZonesAndDistancesMap = getZoneToZonesAndDistanceMap(distMatrix, zoneIds);


        Map<Integer, List<Integer>> closestZoneIds = new HashMap<>();

        // Cap number of zones
        zoneToZonesAndDistancesMap.forEach((integer, pairs) -> {
            List<Integer> targets = pairs.stream().limit(maxNumberClosestZones).map(Pair::getFirst).toList();
            closestZoneIds.put(integer, targets);
        });
        return closestZoneIds;
    }

    /**
     * Find the distances between each zone and the other zones.
     *
     * @param distMatrix KxK distance matrix
     * @param zoneIds    Network ids identified as zone centroids
     * @return Map of zone ids associated to (zone id, distance) pairs (sorted by distances)
     */
    public static Map<Integer, List<Pair<Integer, Double>>> getZoneToZonesAndDistanceMap(List<List<Double>> distMatrix, List<Integer> zoneIds) {

        Map<Integer, List<Pair<Integer, Double>>> zoneIdClosestZonesMap = new HashMap<>();

        for (int idZoneOrigin = 0; idZoneOrigin < distMatrix.size(); idZoneOrigin++) {
            zoneIdClosestZonesMap.put(idZoneOrigin, new ArrayList<>());
            for (int idZoneDestination : zoneIds) {
                if (idZoneOrigin != idZoneDestination) {
                    double distanceToZone = distMatrix.get(idZoneOrigin).get(idZoneDestination);
                    zoneIdClosestZonesMap.get(idZoneOrigin).add(new Pair<>(idZoneDestination, distanceToZone));
                }
            }
        }
        // For each zone, sort according to distance
        zoneIdClosestZonesMap.forEach((integer, pairs) -> pairs.sort((o1, o2) -> o1.getSecond().compareTo(o2.getSecond())));

        return zoneIdClosestZonesMap;
    }

    /***
     * Read .csv adjacency matrix. Assumes KxK accessibility.
     * @param filePath Where the matrix is saved
     * @return Sparse adjacency matrix
     */
    public static List<List<Integer>> getAdjacencyMatrix(String filePath) {
        List<List<Integer>> adjacencyMatrix = new ArrayList<>();

        try {
            Logging.logger.info("# Reading adjacency data from \"{}\"...", filePath);
            Reader in = new FileReader(filePath);

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {
                List<Integer> cols = new ArrayList<>();
                for (String r : record) {
                    int v = Integer.parseInt(r);
                    cols.add(v);
                }
                adjacencyMatrix.add(cols);
            }
            Logging.logger.info("# Read adjacency data ({} nodes).", adjacencyMatrix.size());

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return adjacencyMatrix;
    }


    public static List<List<Double>> getDistanceMatrixMeters(String filePath) {
        List<List<Double>> distMatrix = new ArrayList<>();

        try {
            Logging.logger.info("# Reading distance matrix from \"{}\"...", filePath);
            Reader in = new FileReader(filePath);

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);

            for (CSVRecord record : records) {
                List<Double> cols = new ArrayList<>();
                for (String r : record) {
                    cols.add(Double.parseDouble(r));
                }
                distMatrix.add(cols);
            }

            Logging.logger.info("# Read distance matrix ({} nodes).", distMatrix.size());

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return distMatrix;
    }

    /**
     * Weighted directed graph is used in conjunction with shortest path method to determine where each vehicle is
     * at each time.
     *
     * @param distMatrixSec
     * @return
     */
    public static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getWeightedGraphFromAdjacencyMatrix(
            List<List<Integer>> adjacencyMatrix, short[][] distMatrixSec) {

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        for (int i = 0; i < distMatrixSec.length; i++) {
            graph.addVertex(i);
        }

        for (int i = 0; i < distMatrixSec.length; i++) {
            for (int j = 0; j < distMatrixSec.length; j++) {

                if (adjacencyMatrix.get(i).get(j) != 0) {
                    DefaultWeightedEdge edge = graph.addEdge(i, j);
                    graph.setEdgeWeight(edge, distMatrixSec[i][j]);
                }
            }
        }

        return graph;
    }

    /**
     * Weighted directed graph is used in conjunction with  the shortest path method to determine where each vehicle is
     * at each time.
     *
     * @param adjacencyMatrix
     * @param distMatrixMeters
     * @return
     */
    public static SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getWeightedGraphFromAdjacencyMatrix(
            List<List<Integer>> adjacencyMatrix, List<List<Double>> distMatrixMeters) {

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Logging.logger.info("Creating weighted graph with distance edges (meters)...");
        for (int i = 0; i < distMatrixMeters.size(); i++) {
            graph.addVertex(i);
        }

        for (int i = 0; i < distMatrixMeters.size(); i++) {
            for (int j = 0; j < distMatrixMeters.size(); j++) {

                if (adjacencyMatrix.get(i).get(j) != 0) {
                    DefaultWeightedEdge edge = graph.addEdge(i, j);
                    graph.setEdgeWeight(edge, distMatrixMeters.get(i).get(j));
                }
            }
        }
        Logging.logger.info("Network: # Edges: {}, #Nodes: {}", graph.edgeSet().size(), graph.vertexSet().size());

        return graph;
    }

    public static Map<Integer, Map<String, List<Integer>>> getReachabilityMap(Set<Integer> nodeIds) {

        Map<Integer, Map<String, List<Integer>>> canReachClass = new HashMap<>();

        for (int i : nodeIds) {

            canReachClass.put(i, new HashMap<>());

            for (Map.Entry<String, Integer> e : InstanceConfig.getInstance().getMaxTimeHiringList().entrySet()) {
                int maxTime = e.getValue();
                List<Integer> canReachNode = Dao.getInstance().getServer().getAllCanReachNode(i, maxTime);
                //canReachList.get(i).put(maxTime, canReachNode);
                canReachClass.get(i).put(e.getKey(), canReachNode);
                //Logging.logger.info(i + " - " + canReachList.get(i).get(maxTime).size());
            }
        }

        return canReachClass;
    }

    /**
     * Parse json of containing list of nodes. E.g., {"nodes"=[{"id"=1, "x": 45.56, "y":75.43}]}
     * <p>
     * json list can be pulled from REST server using address localhost:5000/nodes
     *
     * @param json
     * @return
     */
    public static Map<Integer, NodeNetwork> getNodeDictionaryFromJsonString(String json) {

        JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
        Map<Integer, NodeNetwork> nodes = new HashMap<>();
        JsonArray arr = jsonObject.getAsJsonArray("nodes");
        for (int i = 0; i < arr.size(); i++) {
            int id = arr.get(i).getAsJsonObject().get("id").getAsShort();
            double x = arr.get(i).getAsJsonObject().get("x").getAsDouble();
            double y = arr.get(i).getAsJsonObject().get("y").getAsDouble();

            //TODO center node is centroid
            // Closest center that can reach node at each step (e.g., 30, 60, 90, ..., 600)
            //JsonObject centers = arr.get(i).getAsJsonObject().get("step_center").getAsJsonObject();
            Map<Integer, Integer> centerNodeId = new HashMap<>();
//            for (Map.Entry<String, JsonElement> entry : centers.entrySet()) {
//                centerNodeId.put(Integer.valueOf(entry.getKey()), Integer.valueOf(entry.getKey()));
//            }
            Point2D point = new Point2D.Double(x, y);
            NodeNetwork node = new NodeNetwork(id, point, centerNodeId);

            nodes.put(id, node);
        }
        Logging.logger.info("# {} nodes read.", nodes.size());
        return nodes;
    }
}
