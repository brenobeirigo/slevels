package dao;


import com.google.gson.Gson;
import config.Config;
import config.InstanceConfig;
import config.Qos;
import helper.HelperIO;
import helper.Runtime;
import model.User;
import model.node.Node;
import model.node.NodeNetwork;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.AStarShortestPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simulation.Simulation;

import java.awt.geom.Point2D;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static util.pdcombinatorics.PDPermutations.loadPrecalculatedPermutationsPUDO;


public class Dao {

    public static final int[] ZONE_IDS = new int[]{105, 116, 152, 264, 305, 354, 372, 388, 592, 612, 806, 828, 845, 869, 885, 932, 986, 1005, 1008, 1044, 1085, 1189, 1219, 1237, 1242, 1269, 1422, 1564, 1587, 1641, 1789, 1941, 2056, 2246, 2249, 2335, 2343, 2405, 2424, 2462, 2500, 2608, 2731, 2740, 2944, 2957, 2992, 3018, 3153, 3176, 3218, 3221, 3248, 3251, 3387, 3511, 3746, 3788, 3838, 3848, 3953, 3978, 4059, 4097, 4357, 4362, 4419};
    public static final Set<Integer> ZONE_ID_SET = new HashSet<>(Arrays.stream(ZONE_IDS).boxed().toList());
    public static final  double SPEED_FACTOR = 0.3;

    public static Map<Date, Map<String, Map<Integer, Map<Integer, List<String>>>>> requestDataMap = new HashMap<>();

    // Speed of vehicles m/s
    public static final double SPEED = 30;
    public static final String PICKUP_DATETIME = "pickup_datetime";
    public static final String USER_ID = "id";
    public static final String PASSENGER_COUNT = "passenger_count";
    public static final String PICKUP_NODE_ID = "pickup_node_id";
    public static final String DROPOFF_NODE_ID = "dropoff_node_id";
    public static final String PICKUP_LATITUDE = "pickup_latitude";
    public static final String PICKUP_LONGITUDE = "pickup_longitude";
    public static final String DROPOFF_LATITUDE = "dropoff_latitude";
    public static final String DROPOFF_LONGITUDE = "dropoff_longitude";
    // Vehicle can only execute new plan after a solution has been generated and broadcasted
    // Hence the earliest departure time from a pickup point IS NOT the time request was placed but:
    // Earliest departure time =
    //  Latest time window of request batch
    //  + solution generation delay (SGD)
    //  + solution broadcasting delay (SBD)
    // Hence, assuming SGD=5s and SBD=5, if users can handle a 600-second pickup delay and entail a 60-second service time:
    // Consider user A has made a request at t=15s in batch 0-30s, then:
    // A) Earliest time = 15s
    // A) Latest pickup = 600 - 15 = 585s
    // A) Earliest departure = 30 + 5 + 5 + 60 = 100s
    //

    // Consider user B has made a request at t=20s in batch 0-30s, then:
    // B) Earliest time = 20s
    // B) Latest pickup = 600 - 20 = 580s
    // B) Earliest departure = 30 + 5 + 5 + 60 = 45s

    // If:
    // A ------- 500s -------- B, then a vehicle cannot pool A and B.
    // (A,B) = 100 + 500 = 600 (earliest arrival at B) > 580 (latest pickup)
    private static final int AVG_DELAY_GENERATE_SOLUTION = 0;
    private static final int AVG_DELAY_BROADCAST_SOLUTION = 0;
    private static final String SHORTEST_PATH_DIJKSTRA = "shortest_path_dijkstra";
    private static final String SHORTEST_PATH_ASTAR = "shortest_path_astar";
    public static int numberOfNodes;
    private static Dao ourInstance = new Dao();
    public final long SEED = 0;
    public Map<Integer, List<Integer>> closestZones;
    public Random rand;
    protected ShortestPathAlgorithm<Integer, DefaultWeightedEdge> shortestPath;
    protected ArrayList<ArrayList<List<Integer>>> shortestPathsNodeIds;
    protected ArrayList<ArrayList<List<Integer>>> shortestPathDistances;
    private String pathDistanceMatrix;
    private String pathPrecalculatedPermutations;
    private String pathDurationsMatrix;
    private String pathRequestList;
    private String pathadjacencyMatrix;
    private String pathNetworkNodeInfo;
    // Geographical data
    private Map<Integer, NodeNetwork> nodeNetworkInfo; // Map of node ids and respective coordinates
    private ServerUtil server;

    public Map<Integer, Set<Integer>> getMapReachableNetworkIdsWithinTimeLimit() {
        return mapReachableNetworkIdsWithinTimeLimit;
    }

    public void setMapReachableNetworkIdsWithinTimeLimit(Map<Integer, Set<Integer>> mapReachableNetworkIdsWithinTimeLimit) {
        this.mapReachableNetworkIdsWithinTimeLimit = mapReachableNetworkIdsWithinTimeLimit;
    }

    private Map<Integer, Set<Integer>> mapReachableNetworkIdsWithinTimeLimit;
    private short[][] distMatrix;
    private double[][] distMatrixMeters;
    private int[][] adjacencyMatrix;
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> networkGraph;
    private Map<Integer, Map<String, List<Integer>>> canReachClass;
    private int iUserNextRound;
    private User userBuff;
    private List<User> allUsers;
    private Iterable<CSVRecord> records;
    private int earliestTimeRequestBatch = 0;
    private Runtime runTimes;

    public Runtime getRunTimes() {
        return runTimes;
    }

    public Dao() {

        Logging.logger.debug("Start DAO");
        try {

            runTimes = new Runtime();

            // Log user data
            allUsers = new ArrayList<>();
            userBuff = null;

            // Set seed to guarantee reproducibility (set later)
            rand = new Random(SEED);

            iUserNextRound = 0;

            String serverUrl = InstanceConfig.getInstance().getServerUrl();
            server = new ServerUtil(serverUrl);
            // Get paths from configuration file ///////////////////////////////////////////////////////////////////////
            pathDistanceMatrix = InstanceConfig.getInstance().getDistancesPath().toString();
            pathDurationsMatrix = InstanceConfig.getInstance().getDurationsPath().toString();
            pathadjacencyMatrix = InstanceConfig.getInstance().getAdjacencyMatrixPath().toString();
            pathNetworkNodeInfo = InstanceConfig.getInstance().getNetworkNodeInfoPath().toString();
            pathRequestList = InstanceConfig.getInstance().getRequestsPath().toString();
            pathPrecalculatedPermutations = InstanceConfig.getInstance().getPrecalculatedPermutationsPath().toString();

            // Reading map data ////////////////////////////////////////////////////////////////////////////////////////

            Logging.logger.info("# Reading nodeset data from '{}'...", pathNetworkNodeInfo);
            nodeNetworkInfo = ParseJsonUtil.getNodeDictionaryFromJsonString(HelperIO.readFileFromPath(pathNetworkNodeInfo));
            numberOfNodes = nodeNetworkInfo.size();

            //distMatrix = getDistanceMatrixFrom(pathDistanceMatrix);
            distMatrix = getDistanceMatrixFrom(pathDurationsMatrix, false);
            this.closestZones = this.closestZones(4);
//            distMatrixMeters = getDistanceMatrixMeters(pathDistanceMatrix);

            Logging.logger.info("# Reading precalculated PUDO data from '{}'...", pathPrecalculatedPermutations);
            loadPrecalculatedPermutationsPUDO(pathPrecalculatedPermutations);


            adjacencyMatrix = getAdjacencyMatrix(pathadjacencyMatrix);

            // networkGraph = getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrixMeters);
            networkGraph = getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrix);

            if (Config.getInstance().getSpAlgorithm().equals(SHORTEST_PATH_DIJKSTRA)) {
                this.shortestPath = new DijkstraShortestPath(networkGraph);
            } else if (Config.getInstance().getSpAlgorithm().equals(SHORTEST_PATH_ASTAR)) {
                this.shortestPath = new AStarShortestPath<Integer, DefaultWeightedEdge>(
                        networkGraph,
                        new AStarAdmissibleHeuristic<Integer>() {
                            @Override
                            public double getCostEstimate(Integer i, Integer j) {
                                return distMatrix[i][j];
                            }
                        });
            }

            // Shortest path info (node ids, node arrivals)
            shortestPathsNodeIds = new ArrayList<>();
            shortestPathDistances = new ArrayList<>();

            for (int i = 0; i < numberOfNodes; i++) {
                shortestPathsNodeIds.add(i, new ArrayList<>());
                shortestPathDistances.add(i, new ArrayList<>());

                for (int j = 0; j < numberOfNodes; j++) {
                    shortestPathsNodeIds.get(i).add(j, null);
                    shortestPathDistances.get(i).add(j, null);
                }
            }

            Logging.logger.info("# Reading all records from '{}'...", pathRequestList);
            records = CSVParser.parse(new FileReader(pathRequestList), CSVFormat.RFC4180.withFirstRecordAsHeader());


            // TODO Read nodes from server
            // Logging.logger.info("Pulling shortest path info...");
            //
            // Pull map of nodes from server. Format: {id=Point(x,y)}
            // Logging.logger.info("Pulling nodes from server...");
            // nodeNetworkInfo = ParseJsonUtil.getNodeDictionaryFromJsonString(ServerUtil.getNodeList());
            /*Logging.logger.info("Pulling reachability map for max trip times:"
                    + InstanceConfig.getInstance().getMaxTimeHiringList());
            canReachClass = getReachabilityMap(nodeNetworkInfo.keySet());
            Logging.logger.info(canReachClass);
            //Logging.logger.info();
            ServerUtil.getAllCanReachNode(row, 150);

            */

            /*
            //Try to read all shortest paths in advance
            Instant before = Instant.now();
            IntStream.range(0, shortestPathDistances.size()).parallel().forEach(o ->
                    IntStream.range(0, shortestPathDistances.get(o).size()).parallel().forEach(d ->
                            shortestPathDistances.get(o).set(d, ServerUtil.getShortestPathBetween(o, d))));
            Instant after = Instant.now();
            Duration duration = Duration.between(before, after);
            Logging.logger.info("DURATION: " + duration.toMillis());*/

            /*
            // Save all shortest paths before execution
            for (int i = 0; i < distMatrixMeters.length; i++) {
                Logging.logger.info("Processing " + i);
                for (int j = 0; j < distMatrixMeters.length; j++) {
                    Logging.logger.info(i + "==" + j);
                    getShortestPathBetween(i, j);
                }
            }*/


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Get the array of trip durations between every pair of nodes in the shortest path between "from" and "to"
     *
     * @param from
     * @param to
     * @return
     */
    public static List<Integer> getArrayTravelDurationsBetweenInSeconds(Node from, Node to) {

        List<Integer> listNodes = Dao.getInstance().getServer().getShortestPathBetween(from.getNetworkId(), to.getNetworkId());

        List<Integer> durations = new ArrayList<>();

        for (int i = 0; i < listNodes.size() - 1; i++) {
            int o = listNodes.get(i);
            int d = listNodes.get(i + 1);
            durations.add(Dao.getInstance().getDistSec(o, d));
        }
        return durations;
    }

    public static Dao getInstance() {
        return ourInstance;
    }

    /**
     * Weighted directed graph is used in conjunction with shortest path method to determine where each vehicle is
     * at each time.
     *
     * @param adjacencyMatrix
     * @param distMatrixMeters
     * @return
     */
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getWeightedGraphFromAdjacencyMatrix(
            int[][] adjacencyMatrix, double[][] distMatrixMeters) {

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        for (int i = 0; i < distMatrixMeters.length; i++) {
            graph.addVertex(i);
        }

        for (int i = 0; i < distMatrixMeters.length; i++) {
            for (int j = 0; j < distMatrixMeters.length; j++) {

                if (adjacencyMatrix[i][j] != 0) {
                    DefaultWeightedEdge edge = graph.addEdge(i, j);
                    graph.setEdgeWeight(edge, distMatrixMeters[i][j]);
                }
            }
        }

        return graph;
    }

    /**
     * Weighted directed graph is used in conjunction with shortest path method to determine where each vehicle is
     * at each time.
     *
     * @param distMatrixSec
     * @return
     */
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getWeightedGraphFromAdjacencyMatrix(
            int[][] adjacencyMatrix, short[][] distMatrixSec) {

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        for (int i = 0; i < distMatrixSec.length; i++) {
            graph.addVertex(i);
        }

        for (int i = 0; i < distMatrixSec.length; i++) {
            for (int j = 0; j < distMatrixSec.length; j++) {

                if (adjacencyMatrix[i][j] != 0) {
                    DefaultWeightedEdge edge = graph.addEdge(i, j);
                    graph.setEdgeWeight(edge, distMatrixSec[i][j]);
                }
            }
        }

        return graph;
    }

    public List<String> getLonLatList(List<Integer> shortestPathB) {
        return shortestPathB.stream().map(
                v -> String.format(
                        "[%f, %f]",
                        nodeNetworkInfo.get(v).getPoint().getX(),
                        nodeNetworkInfo.get(v).getPoint().getY())
        ).collect(Collectors.toList());
    }

    private Map<Integer, Map<String, List<Integer>>> getReachabilityMap(Set<Integer> nodeIds) {

        Map<Integer, Map<String, List<Integer>>> canReachClass = new HashMap<>();

        for (int i : nodeIds) {

            canReachClass.put(i, new HashMap<>());

            for (Entry<String, Integer> e : InstanceConfig.getInstance().getMaxTimeHiringList().entrySet()) {
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
     * Access coordinate of point.
     *
     * @param id Node id
     * @return Point2D coordinate
     */
    public Point2D getLocation(int id) {
        return nodeNetworkInfo.get((int) id).getPoint();
    }

    /**
     * Get GeoJson linestring given list of coordinates.
     *
     * @param line List of Point2D coordinates
     * @return GeoJson object
     */
    public String getLinestring(List<Point2D> line) {

        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {},\n" +
                "      \"geometry\":{\n" +
                "  \"type\": \"LineString\",\n" +
                "  \"coordinates\": [\n");

        b.append(String.join(",", line.stream()
                .map(p -> String.format("[%f,%f]",
                        p.getX(),
                        p.getY()))
                .collect(Collectors.toList())));

        b.append("]}}");

        return b.toString();
    }


    /**
     * Get GeoJson of all points that compose a linestring (sequence of Point2D).
     *
     * @param linestring (list of points Point2D)
     * @return A GeoJson object containing all points
     */
    public String allPoints(List<Point2D> linestring) {
        StringBuilder b = new StringBuilder();
        b.append("{\n" +
                "  \"type\": \"FeatureCollection\",\n" +
                "  \"features\": [");
        b.append(String.join(",", linestring.stream().map(p -> Node.getGeoJson(p)).collect(Collectors.toList())));
        b.append("]}");
        return b.toString();
    }

    /**
     * Get shortest path (node id list) between origin o and destination d (including)
     *
     * @param o Shortest path origin
     * @param d Shortest path destination
     * @return List of node ids
     */
    public List<Integer> getShortestPathBetween(int o, int d) {

            List<Integer> sp = shortestPath.getPath(o, d).getVertexList();
//        if (shortestPathsNodeIds.get(o).get(d) == null) {
//            //Instant a = Instant.now();
////            Instant b = Instant.now();
////            List<Integer> sp2 = aStarShortestPath.getPath(o, d).getVertexList();
////            Instant c = Instant.now();
////            Duration d1 = Duration.between(a, b);
////            Duration d2 = Duration.between(b, c);
//
////            Logging.logger.info(sp + "-" + d1.toMillis());
////            Logging.logger.info(sp2 + "-" + d2.toMinutes());
//            //Logging.logger.info("{}", String.format("%d -> %d\n s%\n %s \n\n", o, d, String.valueOf(sp), String.valueOf(sp2)));
//            shortestPathsNodeIds.get(o).set(d, sp);
        //}
        return sp;
    }

    /**
     * Reset trip records reading file.
     */
    public void resetRecords() {

        // Logging.logger.info("Resetting trip records...");

//        try {

        // Timers are cleaned for next simulation
        runTimes = new Runtime();

        // Reset system current for next test set
        earliestTimeRequestBatch = 0;

        // Read the requests from the beginning
        //records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new FileReader(pathRequestList));

        // Start random again
        rand = new Random(SEED);

        // Buffer that saves previous is reset
        userBuff = null;

//
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    public Map<Integer, Map<String, List<Integer>>> getCanReachList() {
        return canReachClass;
    }

    private double[][] getDistanceMatrixMeters(String filePath) {
        double[][] dist_matrix = new double[numberOfNodes][numberOfNodes];

        try {
            Logging.logger.info("{}", String.format("# Reading distance matrix from \"%s\"...", filePath));
            Reader in = new FileReader(filePath);
            int row = 0;
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {
                int col = 0;
                for (String r : record) {
                    dist_matrix[row][col] = Float.parseFloat(r);
                    col++;
                }
                row++;
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return dist_matrix;
    }

    public Set<User> getListTripsClassed(Date earliestTime, int timeSpanSec, int maxPassengerCount, int maxNumber) {

        List<User> trips = getListTripsClassed(earliestTime, timeSpanSec, maxPassengerCount);

        Collections.shuffle(trips);

        if (trips.size() > maxNumber) {
            return trips.stream().limit(maxNumber).collect(Collectors.toSet());
        }

        return new HashSet<>(trips);
    }


    public Set<User> getListTripsClassedShuffled(Date earliestTime, int timeSpanSec, int maxPassengerCount, int maxNumber, Random rand) {

        List<User> trips = getUsers(earliestTimeRequestBatch, earliestTime, timeSpanSec, maxPassengerCount);

        Collections.shuffle(trips, rand);

        return getTripSubset(maxNumber, trips);

    }

    private List<User> getUsers(int earliestTimeRequestBatch, Date earliestTime, int timeSpanSec, int maxPassengerCount) {
        List<User> trips = new ArrayList<>();
        if (requestDataMap.containsKey(earliestTime)) {
            if (requestDataMap.get(earliestTime).containsKey(this.pathRequestList)) {
                if (requestDataMap.get(earliestTime).get(this.pathRequestList).containsKey(earliestTimeRequestBatch)) {
                    if (requestDataMap.get(earliestTime).get(this.pathRequestList).get(earliestTimeRequestBatch).containsKey(maxPassengerCount)) {
                        trips = requestDataMap.get(earliestTime).get(this.pathRequestList).get(earliestTimeRequestBatch).get(maxPassengerCount).stream().map(userStr -> {
                            Gson u = new Gson();
                            User n = u.fromJson(userStr, User.class);
                            return n;
                        }).collect(Collectors.toList());
                    }
                }
            }
        } else {
            trips = getListTripsClassed(earliestTime, timeSpanSec, maxPassengerCount);
            Map<String, Map<Integer, Map<Integer, List<String>>>> timeSpanPercentage1 = new HashMap<>();
            Map<Integer, Map<Integer, List<String>>> timeSpanPercentage = new HashMap<>();
            Map<Integer, List<String>> percentageUsers = new HashMap<>();
            percentageUsers.put(maxPassengerCount, trips.stream().map(user -> {
                Gson a = new Gson();
                String s = a.toJson(user);
                return s;
            }).collect(Collectors.toList()));
            timeSpanPercentage.put(earliestTimeRequestBatch, percentageUsers);
            timeSpanPercentage1.put(this.pathRequestList, timeSpanPercentage);
            requestDataMap.put(earliestTime, timeSpanPercentage1);
        }
        return trips;
    }

    public Set<User> getListTripsClassedShuffled(Date earliestTime, int timeSpanSec, int maxPassengerCount, double percentage, Random rand) {

        List<User> trips = getListTripsClassed(earliestTime, timeSpanSec, maxPassengerCount);
//
//        List<User> trips = getUsers(earliestTimeRequestBatch, earliestTime, timeSpanSec, maxPassengerCount);
//        trips.forEach(user -> User.mapOfUsers.put(user.getId(), user));

        Collections.shuffle(trips, rand);

        return getTripSubset(percentage, trips);

    }

    private Set<User> getTripSubset(int maxSize, List<User> trips) {
        if (trips.size() > maxSize) {
            return trips.stream().limit(trips.size()).collect(Collectors.toSet());
        }
        return new HashSet<>(trips);
    }

    private Set<User> getTripSubset(double percentage, List<User> trips) {
        if (trips.size() > percentage * trips.size()) {
            int maxSize = (int) (percentage * trips.size());
            return trips.stream().limit(maxSize).collect(Collectors.toSet());
        }
        return new HashSet<>(trips);
    }


    public Set<User> getListTrips(Date earliestDatetime, int timeSpanSec, int maxPassengerCount, int maxNumber) {
        Set<User> trips = getListTrips(earliestDatetime, timeSpanSec, maxPassengerCount);
        if (trips.size() > maxNumber) {
            trips = trips.stream().limit(maxNumber).collect(Collectors.toSet());
        }
        return trips;
    }

    /**
     * Read a request batch of timeSpanSec seconds from file whose number of passengers is <= maxPassengerCount
     *
     * @param timeSpanSec       Time window of batch of requests
     * @param maxPassengerCount Keep records with PASSENGER_COUNT lower
     * @return
     */
    public Set<User> getListTrips(Date earliestDatetime, int timeSpanSec, int maxPassengerCount) {

        Set<User> listUser = new HashSet<>();

        for (CSVRecord record : records) {

            if (Integer.parseInt(record.get(PASSENGER_COUNT)) > maxPassengerCount) {
                continue;
            }

            User user = new User(
                    record.get(PICKUP_DATETIME),
                    earliestDatetime,
                    Integer.parseInt(record.get(PASSENGER_COUNT)),
                    Integer.parseInt(record.get(PICKUP_NODE_ID)),
                    Integer.parseInt(record.get(DROPOFF_NODE_ID)),
                    Double.parseDouble(record.get(PICKUP_LATITUDE)),
                    Double.parseDouble(record.get(PICKUP_LONGITUDE)),
                    Double.parseDouble(record.get(DROPOFF_LATITUDE)),
                    Double.parseDouble(record.get(DROPOFF_LONGITUDE)));

            if (user.getReqTime() >= earliestTimeRequestBatch + timeSpanSec) {
                earliestTimeRequestBatch = earliestTimeRequestBatch + timeSpanSec;
                userBuff = user;
                break;
            }

            listUser.add(user);
        }

        return listUser;
    }

    /***
     * Read .csv distance matrix. Assumes KxK acessibility.
     * @param filePath
     * @param useSpeed If True, distance data is converted to seconds, otherwise distance data is already in seconds
     * @return double[][]
     */
    private short[][] getDistanceMatrixFrom(String filePath, boolean useSpeed) {
        distMatrixMeters = new double[numberOfNodes][numberOfNodes];
        mapReachableNetworkIdsWithinTimeLimit = new HashMap<>();

        short[][] dist_matrix = new short[numberOfNodes][numberOfNodes];

        try {
            Logging.logger.info("# Reading distance matrix from '{}'...", filePath);
            Reader in = new FileReader(filePath);

            int row = 0;

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {

                mapReachableNetworkIdsWithinTimeLimit.put(row, new HashSet<>());

                int col = 0;

                for (String r : record) {
                    if (useSpeed) {
                        double meters = Double.valueOf(r);
                        short sec = (short) (3.6 * meters / SPEED + 0.5);
                        dist_matrix[row][col] = sec;
                        distMatrixMeters[row][col] = meters;
                    } else {
                        dist_matrix[row][col] = (short) (Double.parseDouble(r) * SPEED_FACTOR);
                        if (dist_matrix[row][col]==0 && row!= col){
                            dist_matrix[row][col] = 1;
                            //System.out.printf("%s-%s (%s) %s%n", row, col, r,dist_matrix[row][col] );
                        }
                    }
                    // TODO Reachable within 300s
                    int maxPickupDelay = 300;
                    if (dist_matrix[row][col] < maxPickupDelay) { //Config.getInstance().qosDic.get("B").pkDelay
                        mapReachableNetworkIdsWithinTimeLimit.get(row).add(col);
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


    /***
     * Read .csv distance matrix. Assumes KxK accessibility.
     * @param file_path
     * @return double[][]
     */
    private int[][] getAdjacencyMatrix(String file_path) {
        int[][] adjacencyMatrix = new int[numberOfNodes][numberOfNodes];

        try {
            Logging.logger.info("{}", String.format("# Reading adjacency data from \"%s\"...", file_path));
            Reader in = new FileReader(file_path);

            int row = 0;

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {
                int col = 0;

                for (String r : record) {
                    int v = Integer.valueOf(r);
                    adjacencyMatrix[row][col] = v;
                    col++;
                }
                row++;
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return adjacencyMatrix;
    }

    public static Map<Integer, CSVRecord> recordsFiltered = new HashMap<>();

    /**
     * Get list of users, attributing classes A, B, and C to them (according to shares defined in config)
     *
     * @param timeSpanSec       Time span of pooling
     * @param maxPassengerCount Maximum passenger count (<= max. vehicle capacity)
     * @return
     */
    public List<User> getListTripsClassed(Date earliestTime, int timeSpanSec, int maxPassengerCount) {
        Logging.logger.info("Getting users...");

        // Start list of users with buffer from last iteration (users read, but not in time span)
        List<User> listUser = new ArrayList<>();

        int latestTimeRequestBatch = earliestTimeRequestBatch + timeSpanSec;
        assert latestTimeRequestBatch == Simulation.rightTW : String.format("%s - %s", latestTimeRequestBatch, Simulation.rightTW);
        if (userBuff != null) {
            if (userBuff.getReqTime() < latestTimeRequestBatch) {
                listUser.add(userBuff);
            } else {
                earliestTimeRequestBatch = latestTimeRequestBatch;
                return listUser;
            }
        }

        // Continue reading records
        for (CSVRecord record : records) {

            // Filter requests before earliest configured time
            if (getPickupDateTime(record).before(earliestTime)) {
                continue;
            }
            // Skip passenger record with high passenger count
            if (Integer.parseInt(record.get(PASSENGER_COUNT)) > maxPassengerCount) {
                continue;
            }

            // Create user using record
            User user = new User(earliestTime, record);

            // Stop reading if request is out of time span
            if (user.getReqTime() >= latestTimeRequestBatch) {
                earliestTimeRequestBatch = latestTimeRequestBatch;
                // Save user wrongfully read to next iteration
                userBuff = user;
                break;
            }

            // Add user to list
            listUser.add(user);
        }

        LinkedList<Entry<String, Qos>> qosClasses = new LinkedList<>(Config.getInstance().qosDic.entrySet());

        for (User user : listUser) {
            user.updatePerformanceClass(getRandomClassRoulleteWheel(qosClasses));
        }
        Logging.logger.info("Read {}", listUser.size());

        return listUser;
    }

    private Date getPickupDateTime(CSVRecord record) {
        try {
            return Config.formatter_date_time.parse(record.get(PICKUP_DATETIME));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Date();
    }

    Map<String, Set<String>> getCountUserClass(List<User> users) {
        return users.stream().collect(Collectors.groupingBy(User::getPerformanceClass, Collectors.mapping(User::toString, toSet())));
    }

    private String getRandomClassRoulleteWheel(LinkedList<Entry<String, Qos>> qosClasses) {

        int share = 0;
        int randValue = rand.nextInt(100) + 1;

        for (Entry<String, Qos> qos : qosClasses) {
            share += qos.getValue().share * 100;
            if (randValue <= share) {
                return qos.getKey();
            }
        }

        return qosClasses.getLast().getKey();
    }

    /**
     * Get list of users, attributing classes A, B, and C to them (according to shares defined in config)
     *
     * @param timeSpanSec       Time span of pooling
     * @param maxPassengerCount Maximum passenger count (<= max. vehicle capacity)
     * @return
     */
    public List<User> getListTripsClassed2(int timeSpanSec, int maxPassengerCount) {

        // Start list of users with buffer from last iteration
        // (users read ahead time span to be placed in next iteration)
        Set<User> listUser = new HashSet<>();
        // Logging.logger.info("%d",timeSpanSec);
        // Continue reading records
//            if (userBuff.get(userBuff.size()-1).getReqTime() < currentTime + timeSpanSec){
//            return new ArrayList<>();
//        }
        for (int i = iUserNextRound; i < allUsers.size(); i++) {

            User user = allUsers.get(i);
            // Skip passenger record with high passenger count
            if (user.getNumPassengers() > maxPassengerCount) {
                continue;
            }

            //TODO - User with invalid ids! (clean in python)
            if (user.getDistFromTo() < 0) {
                continue;
            }
            // Stop reading if request is out of time span
            if (user.getReqTime() >= earliestTimeRequestBatch + timeSpanSec) {
                earliestTimeRequestBatch = earliestTimeRequestBatch + timeSpanSec;
                iUserNextRound = i;
                break;
            }

            // Add user to list
            listUser.add(user);
        }
        //Logging.logger.info("RECORD:");
        //Logging.logger.info(record);
        Logging.logger.info("USER BUF: {}", userBuff);
        Logging.logger.info("LIST USER:");
        listUser.forEach(user -> Logging.logger.info(user.getPickupDatetime()));

        // Number of requests for each class according to their share

        List<User> classed = new ArrayList<>(listUser);

        // List of class tags

        List<String> classTagList = new ArrayList<>(Config.getInstance().qosDic.keySet());

        Map<String, Integer> usersPerClass = new HashMap<>();

        // Filling the number of users per class
        for (Entry<String, Qos> e : Config.getInstance().qosDic.entrySet()) {
            String qsClass = e.getKey();
            int qtUsers = (int) (e.getValue().share * listUser.size());
            usersPerClass.put(qsClass, qtUsers);
        }

        // Fill the number of users per class (in case proportions failed)
        while (usersPerClass.values().stream().mapToInt(Integer::intValue).sum() < listUser.size()) {
            //Logging.logger.info("TAG LIST:" + classTagList + "- qosDic: " + Config.getInstance().qosDic);
            int randClass = Dao.getInstance().rand.nextInt(classTagList.size());
            usersPerClass.put(classTagList.get(randClass), usersPerClass.get(classTagList.get(randClass)) + 1);
        }

        // Assigning classes
        for (int j = 0; j < classed.size(); j++) {

            String qosClass = null;

            // Repeats until a valid qos class is found
            while (qosClass == null) {

                int randClass = Dao.getInstance().rand.nextInt(classTagList.size());

                String sqClass = classTagList.get(randClass);

                if (usersPerClass.get(sqClass) > 0) {
                    qosClass = sqClass;
                    usersPerClass.put(classTagList.get(randClass), usersPerClass.get(classTagList.get(randClass)) - 1);
                }
            }
            classed.get(j).updatePerformanceClass(qosClass);

        }

        return classed;
    }

    /**
     * Get distance in meters
     *
     * @param from node id
     * @param to   node id
     * @return distance in meters
     */
    public double getDist(int from, int to) {
        return distMatrix[from][to];
    }

    /**
     * Get distance in seconds (considering 30 m/s)
     *
     * @param from node id
     * @param to   node id
     * @return distance in seconds
     */
    public int getDistSecOld(int from, int to) {
        return (int) (3.6 * distMatrix[from][to] / SPEED);
    }

    public double getLon(int networkId) {
        return Dao.getInstance().getLocation(networkId).getX();
    }

    public double getLat(int networkId) {
        return Dao.getInstance().getLocation(networkId).getY();
    }

    /**
     * Get distance in km
     *
     * @param from node id
     * @param to   node id
     * @return distance in km
     */
    public double getDistKm(int from, int to) {
        return distMatrixMeters[from][to];
    }

    /**
     * Get distance in km
     *
     * @param from node
     * @param to   node
     * @return distance in km
     */
    public double getDistKm(Node from, Node to) {
        return getDistKm(from.getNetworkId(), to.getNetworkId());
    }

    /**
     * Get distance in seconds (considering 30 m/s)
     *
     * @param from node id
     * @param to   node id
     * @return distance in seconds
     */
    public int getDistSec(int from, int to) {
                /*if (distMatrixDerivedFromSP[from][to] > 0) {
            return distMatrixDerivedFromSP[from][to];
        }

        List<Integer> shortestPath = dijkstraShortestPath.getPath(from, to).getVertexList();
        int distPath = IntStream
                .range(0, shortestPath.size() - 1)
                .reduce(0, (sum, e) -> sum + distMatrix[shortestPath.get(e)][shortestPath.get(e + 1)]);

        distMatrixDerivedFromSP[from][to] = (short) distPath;
        return distMatrixDerivedFromSP[from][to];*/
        return distMatrix[from][to];
    }

    /**
     * Get distance in seconds (considering 30 m/s)
     *
     * @param from node
     * @param to   node
     * @return distance in seconds
     */
    public int getDistSecOld(Node from, Node to) {
        return (int) (3.6 * distMatrix[from.getNetworkId()][to.getNetworkId()] / SPEED);
    }

    /**
     * Get distance in seconds (considering 30 m/s)
     *
     * @param from node
     * @param to   node
     * @return distance in seconds
     */
    public int getDistSec(Node from, Node to) {
        return getDistSec(from.getNetworkId(), to.getNetworkId());
    }

    /**
     * Get the network id of the node in the shortest path connecting two other nodes.
     *
     * @param from        Origin node
     * @param to          Destination node
     * @param elapsedTime Elapsed time
     * @return network id, or -1 if there is no intermediate node
     */
    public int getNodeBetweenAndExtraDelay(Node from,
                                           Node to,
                                           int elapsedTime) {
        return getIntermediateNodeNetworkId(from.getNetworkId(), to.getNetworkId(), elapsedTime);
    }


    /**
     * Get the network id of the node in the shortest path connecting two other nodes.
     *
     * @param o
     * @param d
     * @param elapsedTime
     * @return network id, or -1 if there is no intermediate node
     */
    public int getIntermediateNodeNetworkIdNew(int o, int d, int elapsedTime) {

        ArrayList<Integer> sp = Dao.getInstance().getServer().getShortestPathBetween(o, d);

        // Building array of arrivals
        // e.g.,     List [      o,      1,      2,      3,      4,      5,      d]
        //       Arrivals [      0, d(o,1), d(1,2), d(2,3), d(3,4), d(4,5), d(5,d)]
        ArrayList<Integer> spArrivals = getSpNodeArrivals(sp);

        // No path between nodes
        if (spArrivals.isEmpty())
            return -1;

        if (elapsedTime <= 0)
            return -1;

        //Elapsed time higher than last node
        if (elapsedTime >= spArrivals.get(spArrivals.size() - 1))
            return -1;

        // Find position of first higher arrival
        int pos = Collections.binarySearch(spArrivals, elapsedTime);

        // Correct for elapsedTime not in shortest path (<0)
        pos = pos >= 0 ? pos : -1 - pos;
        if (sp.get(pos) == d) {
            return -1;
        }

        // Decide which node is closer (before or after first higher)
        return sp.get(pos);
    }

    public void pullShortestPathInfo(int o, int d) {
        List<Integer> sp = getShortestPathBetween(o, d);
        ArrayList<Integer> spArrivals = getSpNodeArrivals(sp);
        Logging.logger.info(o + " -> " + d);

        shortestPathDistances.get(o).set(d, spArrivals);
        shortestPathsNodeIds.get(o).set(d, sp);

        Logging.logger.info((shortestPathsNodeIds.get(o).size()) + "--" + sp);
        Logging.logger.info((shortestPathDistances.get(o).size()) + "--" + spArrivals);
    }

    public Map<Integer, List<Integer>> closestZones(int maxNumberClosestZones) {
        Map<Integer, List<Pair<Integer, Integer>>> dists = new HashMap<>();
        for (int z0 = 0; z0 <this.distMatrix.length; z0++) {
            dists.put(z0, new ArrayList<>());
            for (int z1 : ZONE_IDS) {
                if (z0 != z1) {
                    int dist = this.distMatrix[z0][z1];
                    dists.get(z0).add(new Pair<>(z1, dist));
                }
            }
        }
        dists.forEach((integer, pairs) -> pairs.sort((o1, o2) -> o1.getSecond().compareTo(o2.getSecond())));
        Map<Integer, List<Integer>> closestZoneIds = new HashMap<>();

        dists.forEach((integer, pairs) -> {

            List<Integer> targets = pairs.stream().limit(maxNumberClosestZones).map(Pair::getFirst).toList();
            closestZoneIds.put(integer, targets);
        });
        return closestZoneIds;
    }

    /**
     * Get the network id of the node in the shortest path connecting two other nodes.
     *
     * @param o
     * @param d
     * @param elapsedTime
     * @return network id, or -1 if there is no intermediate node
     */
    public int getIntermediateNodeNetworkId(int o, int d, int elapsedTime) {

        // Vehicle did not leave the origin
        if (elapsedTime == 0) {
            return o;
        }

        List<Integer> sp = getShortestPathBetween(o, d);
        assert sp != null && !sp.isEmpty() : String.format("NULL" + o + " - " + d);

        List<Integer> spArrivals = getSpNodeArrivals(sp);
//        if (shortestPathDistances.get(o).get(d) == null) {
//            List<Integer> spArrivals = getSpNodeArrivals(sp);
//            shortestPathDistances.get(o).set(d, spArrivals);
//        }

        List<Integer> intermediateArrivalsList = spArrivals;
        assert !intermediateArrivalsList.isEmpty();

        int insertionPoint = getInsertionPoint(elapsedTime, intermediateArrivalsList);

        if (insertionPoint == sp.size()) {
            return d;
        }
//        System.out.println(intermediateArrivalsList + "--" + insertionPoint + "");

        return sp.get(insertionPoint);
    }

    public int getInsertionPoint(int elapsedTime, List<Integer> intermediateArrivalsList) {
        // Find position of first higher arrival
        int insertionPoint = Collections.binarySearch(intermediateArrivalsList, elapsedTime);

        // Correct for elapsedTime not in shortest path (<0)
        // Position that element would be inserted into the list = (-(insertion point) - 1).
        insertionPoint = insertionPoint >= 0 ? insertionPoint : -1 - insertionPoint;
        return insertionPoint;
    }

    /**
     * Build cumulative distance array from distances between nodes in sequence.
     * e.g.,     List [o, 1, 2, 3, d]
     * Arrivals [0, d(o,1), d(o,1) + d(1,2), d(o,1) + d(1,2) + d(2,3), d(o,1) + d(1,2) + d(2,3) + d(3,d)]
     *
     * @param sequenceNodeIds
     * @return cumulativeDistancesKm
     */
    private ArrayList<Integer> getSpNodeArrivals(List<Integer> sequenceNodeIds) {

        ArrayList<Integer> cumulativeDistancesKm = new ArrayList<>();

        int starting = 0;

        cumulativeDistancesKm.add(starting);

        // Building array of arrivals
        //TODO there are nodes with zero distances between them! Should be removed!
        for (int i = 0; i < sequenceNodeIds.size() - 1; i++) {
            int distanceLeg = getDistSec(sequenceNodeIds.get(i), sequenceNodeIds.get(i + 1));
            starting += distanceLeg;
            cumulativeDistancesKm.add(starting);
        }

        return cumulativeDistancesKm;
    }

    public short[][] getDistMatrix() {
        return distMatrix;
    }

    public Map<Integer, NodeNetwork> getNodeNetworkInfo() {
        return nodeNetworkInfo;
    }

    public int getClosestRegion(int networkId, String performanceClass) {

        int maxTimeToReach;
        if (InstanceConfig.getInstance().getMaxTimeToReachRegionCenter() == 0) {
            maxTimeToReach = InstanceConfig.getInstance().getMaxTimeHiringList().get(performanceClass);
        } else {
            maxTimeToReach = InstanceConfig.getInstance().getMaxTimeToReachRegionCenter();
        }

        // Logging.logger.info(networkId + " - " + performanceClass + " - " + maxTimeToReach);
        // Network id
        // int closestRegionCenterId = canReach.get(Dao.getInstance().rand.nextInt(canReach.size()));
        // List<Integer> canReach = Dao.getInstance().getCanReachList().get(userNetworkId).get(userClass);
        Map<Integer, Integer> centers = Dao.getInstance().getNodeNetworkInfo().get(networkId).getClosestRegionCenter();

        int closestRegionCenterId = centers.get(maxTimeToReach);

        return closestRegionCenterId;
    }

    public ServerUtil getServer() {
        return this.server;
    }

    public void setRandomSeed(Random random) {
        rand = random;
    }

    public void setRecords(String filePathTrainingData) {
        try {
            records = CSVParser.parse(new FileReader(filePathTrainingData), CSVFormat.RFC4180.withFirstRecordAsHeader());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

