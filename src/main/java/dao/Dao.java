package dao;


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
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.awt.geom.Point2D;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static util.PDPermutations.loadPrecalculatedPermutationsPUDO;


public class Dao {


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
    public static int numberOfNodes;
    private static Dao ourInstance = new Dao();
    public final long SEED = 0;
    public Random rand;
    protected DijkstraShortestPath<Integer, Integer> dijkstraShortestPath;
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
    private short[][] distMatrix;
    private short[][] distMatrixDerivedFromSP;
    private double[][] distMatrixMeters;
    private int[][] adjacencyMatrix;
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> networkGraph;
    private Map<Integer, Map<String, List<Integer>>> canReachClass;
    private int iUserNextRound;
    private User userBuff;
    private List<User> allUsers;
    private Iterable<CSVRecord> records;
    private int currentTime = 0;
    private Runtime runTimes;

    public Runtime getRunTimes() {
        return runTimes;
    }

    public Dao() {
        try {

            runTimes = new Runtime();

            // Log user data
            allUsers = new ArrayList<>();
            userBuff = null;

            // Set seed to guarantee reproducibility
            rand = new Random(SEED);

            iUserNextRound = 0;

            // Get paths from configuration file ///////////////////////////////////////////////////////////////////////
            pathDistanceMatrix = InstanceConfig.getInstance().getDistancesPath().toString();
            pathDurationsMatrix = InstanceConfig.getInstance().getDurationsPath().toString();
            pathadjacencyMatrix = InstanceConfig.getInstance().getAdjacencyMatrixPath().toString();
            pathNetworkNodeInfo = InstanceConfig.getInstance().getNetworkNodeInfoPath().toString();
            pathRequestList = InstanceConfig.getInstance().getRequestsPath().toString();
            pathPrecalculatedPermutations = InstanceConfig.getInstance().getPrecalculatedPermutationsPath().toString();

            // Reading map data ////////////////////////////////////////////////////////////////////////////////////////

            System.out.printf("# Reading nodeset data from \"%s\"...%n", pathNetworkNodeInfo);
            nodeNetworkInfo = ParseJsonUtil.getNodeDictionaryFromJsonString(HelperIO.readFileFromPath(pathNetworkNodeInfo));
            numberOfNodes = nodeNetworkInfo.size();

            //distMatrix = getDistanceMatrixFrom(pathDistanceMatrix);
            distMatrix = getDistanceMatrixFrom(pathDurationsMatrix, false);
            loadPrecalculatedPermutationsPUDO(pathPrecalculatedPermutations);

            distMatrixDerivedFromSP = new short[numberOfNodes][numberOfNodes];

            adjacencyMatrix = getAdjacencyMatrix(pathadjacencyMatrix);

            // networkGraph = getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrixMeters);
            networkGraph = getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrix);
            dijkstraShortestPath = new DijkstraShortestPath(networkGraph);

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

            System.out.printf("# Reading all records from \"%s\"...%n", pathRequestList);
            records = CSVParser.parse(new FileReader(pathRequestList), CSVFormat.RFC4180.withFirstRecordAsHeader());


            // TODO Read nodes from server
            // System.out.println("Pulling shortest path info...");
            //
            // Pull map of nodes from server. Format: {id=Point(x,y)}
            // System.out.println("Pulling nodes from server...");
            // nodeNetworkInfo = ParseJsonUtil.getNodeDictionaryFromJsonString(ServerUtil.getNodeList());
            /*System.out.println("Pulling reachability map for max trip times:"
                    + InstanceConfig.getInstance().getMaxTimeHiringList());
            canReachClass = getReachabilityMap(nodeNetworkInfo.keySet());
            System.out.println(canReachClass);
            //System.out.println();
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
            System.out.println("DURATION: " + duration.toMillis());*/

            /*
            // Save all shortest paths before execution
            for (int i = 0; i < distMatrixMeters.length; i++) {
                System.out.println("Processing " + i);
                for (int j = 0; j < distMatrixMeters.length; j++) {
                    System.out.println(i + "==" + j);
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

        List<Integer> listNodes = ServerUtil.getShortestPathBetween(from.getNetworkId(), to.getNetworkId());

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
                List<Integer> canReachNode = ServerUtil.getAllCanReachNode(i, maxTime);
                //canReachList.get(i).put(maxTime, canReachNode);
                canReachClass.get(i).put(e.getKey(), canReachNode);
                //System.out.println(i + " - " + canReachList.get(i).get(maxTime).size());
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


    public List<Integer> getShortestPathBetween(int o, int d) {

        if (shortestPathsNodeIds.get(o).get(d) == null) {
            List<Integer> sp = dijkstraShortestPath.getPath(o, d).getVertexList();
            shortestPathsNodeIds.get(o).set(d, sp);
        }
        return shortestPathsNodeIds.get(o).get(d);
    }

    /**
     * Reset trip records reading file.
     */
    public void resetRecords() {

        // System.out.println("Resetting trip records...");

        try {

            // Timers are cleaned for next simulation
            runTimes = new Runtime();

            // Reset system current for next test set
            currentTime = 0;

            // Read the requests from the beginning
            records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new FileReader(pathRequestList));

            // Start random again
            rand = new Random(SEED);

            // Buffer that saves previous is reset
            userBuff = null;


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public Map<Integer, Map<String, List<Integer>>> getCanReachList() {
        return canReachClass;
    }

    private double[][] getDistanceMatrixMeters(String file_path) {
        double[][] dist_matrix = new double[numberOfNodes][numberOfNodes];

        try {
            System.out.println("Reading distance data (meters)...");
            Reader in = new FileReader(file_path);
            int row = 0;
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {
                int col = 0;
                for (String r : record) {
                    dist_matrix[row][col] = Float.valueOf(r);
                    col++;
                }
                row++;
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return dist_matrix;
    }

    public Set<User> getListTripsClassed(int timeSpanSec, int maxPassengerCount, int maxNumber) {

        Set<User> trips = getListTripsClassed(timeSpanSec, maxPassengerCount);

        if (trips.size() > maxNumber) {
            trips = trips.stream().limit(maxNumber).collect(Collectors.toSet());
        }

        return trips;
    }

    public Set<User> getListTrips(int timeSpanSec, int maxPassengerCount, int maxNumber) {
        Set<User> trips = getListTrips(timeSpanSec, maxPassengerCount);
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
    public Set<User> getListTrips(int timeSpanSec, int maxPassengerCount) {

        Set<User> listUser = new HashSet<>();

        for (CSVRecord record : records) {

            if (Integer.parseInt(record.get(PASSENGER_COUNT)) > maxPassengerCount) {
                continue;
            }

            User user = new User(
                    record.get(PICKUP_DATETIME),
                    Integer.parseInt(record.get(PASSENGER_COUNT)),
                    Integer.parseInt(record.get(PICKUP_NODE_ID)),
                    Integer.parseInt(record.get(DROPOFF_NODE_ID)),
                    Double.parseDouble(record.get(PICKUP_LATITUDE)),
                    Double.parseDouble(record.get(PICKUP_LONGITUDE)),
                    Double.parseDouble(record.get(DROPOFF_LATITUDE)),
                    Double.parseDouble(record.get(DROPOFF_LONGITUDE)));

            if (user.getReqTime() >= currentTime + timeSpanSec) {
                currentTime = currentTime + timeSpanSec;
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
        short[][] dist_matrix = new short[numberOfNodes][numberOfNodes];

        try {
            System.out.println(String.format("# Reading distance matrix from \"%s\"...", filePath));
            Reader in = new FileReader(filePath);

            int row = 0;

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {
                int col = 0;

                for (String r : record) {
                    if (useSpeed) {
                        double meters = Double.valueOf(r);
                        short sec = (short) (3.6 * meters / SPEED + 0.5);
                        dist_matrix[row][col] = sec;
                        distMatrixMeters[row][col] = meters;
                    } else {
                        dist_matrix[row][col] = Short.valueOf(r);
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
            System.out.println(String.format("# Reading adjacency data from \"%s\"...", file_path));
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

    /**
     * Get list of users, attributing classes A, B, and C to them (according to shares defined in config)
     *
     * @param timeSpanSec       Time span of pooling
     * @param maxPassengerCount Maximum passenger count (<= max. vehicle capacity)
     * @return
     */
    public Set<User> getListTripsClassed(int timeSpanSec, int maxPassengerCount) {

        // Start list of users with buffer from last iteration (users read, but not in time span)
        Set<User> listUser = new HashSet<>();

        int latestTimeRequestBatch = earliestTimeRequestBatch + timeSpanSec;
        assert latestTimeRequestBatch == Simulation.rightTW;
        if (userBuff != null){
            if (userBuff.getReqTime() < latestTimeRequestBatch){
                listUser.add(userBuff);
            }else{
                earliestTimeRequestBatch = latestTimeRequestBatch;
                return listUser;
            }
        }

        // Continue reading records
        for (CSVRecord record : records) {

            // Filter requests before earliest configured time
            if (getPickupDateTime(record).before(Config.getInstance().getEarliestTime())){
                continue;
            }
            // Skip passenger record with high passenger count
                if (Integer.parseInt(record.get(PASSENGER_COUNT)) > maxPassengerCount) {
                continue;
            }

            // Create user using record
            User user = new User(record);

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
        // System.out.println("%d",timeSpanSec);
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
            if (user.getReqTime() >= currentTime + timeSpanSec) {
                currentTime = currentTime + timeSpanSec;
                iUserNextRound = i;
                break;
            }

            // Add user to list
            listUser.add(user);
        }
        //System.out.println("RECORD:");
        //System.out.println(record);
        System.out.println("USER BUF:");
        System.out.println(userBuff);
        System.out.println();
        System.out.println("LIST USER:");
        listUser.forEach(user -> System.out.println(user.getPickupDatetime()));

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
            //System.out.println("TAG LIST:" + classTagList + "- qosDic: " + Config.getInstance().qosDic);
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

        ArrayList<Integer> sp = ServerUtil.getShortestPathBetween(o, d);

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
        System.out.println(o + " -> " + d);

        shortestPathDistances.get(o).set(d, spArrivals);
        shortestPathsNodeIds.get(o).set(d, sp);

        System.out.println((shortestPathsNodeIds.get(o).size()) + "--" + sp);
        System.out.println((shortestPathDistances.get(o).size()) + "--" + spArrivals);
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

        //TODO Decide what to do when  elapsed time is zero
        List<Integer> sp = getShortestPathBetween(o, d);

        if (sp == null) {
            System.out.println("NULL" + o + " - " + d);
        }

        if (shortestPathDistances.get(o).get(d) == null) {
            List<Integer> spArrivals = getSpNodeArrivals(sp);
            shortestPathDistances.get(o).set(d, spArrivals);
        }

        List<Integer> intermediateArrivalsList = shortestPathDistances.get(o).get(d);


        //System.out.println(intermediateArrivalsList.stream().map(p -> String.format("%5s", String.valueOf(p))).collect(Collectors.joining()));
        //System.out.println(getShortestPathBetween(networkIdFrom, networkIdTo).stream().map(p -> String.format("%5s", String.valueOf(p))).collect(Collectors.joining()));

        // No path between nodes
        if (intermediateArrivalsList.isEmpty())
            return -1;

        if (elapsedTime <= 0)
            return -1;

        //Elapsed time higher than last node
        if (elapsedTime >= intermediateArrivalsList.get(intermediateArrivalsList.size() - 1))
            return -1;

        // Find position of first higher arrival
        int pos = Collections.binarySearch(intermediateArrivalsList, elapsedTime);


        // Correct for elapsedTime not in shortest path (<0)
        pos = pos >= 0 ? pos : -1 - pos;

        if (sp.get(pos) == d) {
            return -1;
        }

        /*
        if (getDistSec(networkIdFrom, sp.get(pos)) == 0 || getDistSec(sp.get(pos), networkIdTo) == 0) {
            System.out.println(sp);
            System.out.println(intermediateArrivalsList);
            System.out.println("DIST FROM:" + getDistSec(sp.get(pos), networkIdFrom));
            System.out.println("DIST TO:" + getDistSec(sp.get(pos), networkIdTo));
            System.out.println("ELAPSED:" + elapsedTime + " - POS:" + pos + " - POSNODE:" + sp.get(pos) + " -- FROM:" + networkIdFrom + " -- TO:" + networkIdTo);
            System.out.println("ZERO DISTANCE !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        */

        //System.out.println(intermediateArrivalsList);

        // Decide which node is closer (before or after first higher)

        return sp.get(pos);


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
        }
        else{
            maxTimeToReach = InstanceConfig.getInstance().getMaxTimeToReachRegionCenter();
        }

        // System.out.println(networkId + " - " + performanceClass + " - " + maxTimeToReach);
        // Network id
        // int closestRegionCenterId = canReach.get(Dao.getInstance().rand.nextInt(canReach.size()));
        // List<Integer> canReach = Dao.getInstance().getCanReachList().get(userNetworkId).get(userClass);
        Map<Integer, Integer> centers = Dao.getInstance().getNodeNetworkInfo().get(networkId).getClosestRegionCenter();

        int closestRegionCenterId = centers.get(maxTimeToReach);

        return closestRegionCenterId;
    }
}

