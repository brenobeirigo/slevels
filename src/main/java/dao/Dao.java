package dao;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import config.Config;
import config.InstanceConfig;
import config.Qos;
import helper.HelperIO;
import helper.Runtime;
import model.demand.Request;
import model.demand.User;
import model.network.NetworkLoaded;
import model.network.NetworkUtil;
import model.network.TransportNetwork;
import model.node.NodeNetwork;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.AStarShortestPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static model.demand.Request.PICKUP_DATETIME;
import static util.pdcombinatorics.PDPermutations.loadPrecalculatedPermutationsPUDO;


public class Dao {


    public static final Set<Integer> ZONE_ID_SET = new HashSet<>(Arrays.stream(NetworkUtil.ZONE_IDS).boxed().toList());
    public static final double SPEED_FACTOR = 0.3;

    public static Map<Date, Map<String, Map<Integer, Map<Integer, List<String>>>>> requestDataMap = new HashMap<>();

    // Speed of vehicles m/s
    public static final double SPEED = 30;
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
    public static final String SHORTEST_PATH_DIJKSTRA = "shortest_path_dijkstra";
    public static final String SHORTEST_PATH_ASTAR = "shortest_path_astar";
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
    private String pathZoneData;
    private String pathRequestList;
    private String pathAdjacencyMatrix;
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
    private List<List<Double>> distMatrixMeters;
    private List<List<Integer>> adjacencyMatrix;
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
            pathZoneData = InstanceConfig.getInstance().getZoneDataPath().toString();
            pathAdjacencyMatrix = InstanceConfig.getInstance().getAdjacencyMatrixPath().toString();
            pathDurationsMatrix = InstanceConfig.getInstance().getDurationsPath().toString();
            pathNetworkNodeInfo = InstanceConfig.getInstance().getNetworkNodeInfoPath().toString();
            TransportNetwork network = new NetworkLoaded(pathDistanceMatrix, pathAdjacencyMatrix, pathZoneData, pathNetworkNodeInfo, Config.getInstance().getSpAlgorithm(), InstanceConfig.getInstance().getSpeedKmHour());

            pathRequestList = InstanceConfig.getInstance().getRequestsPath().toString();
            pathPrecalculatedPermutations = InstanceConfig.getInstance().getPrecalculatedPermutationsPath().toString();

            // Reading map data ////////////////////////////////////////////////////////////////////////////////////////




            Logging.logger.info("# Reading nodeset data from '{}'...", pathNetworkNodeInfo);
            nodeNetworkInfo = NetworkUtil.getNodeDictionaryFromJsonString(HelperIO.readFileFromPath(pathNetworkNodeInfo));
            numberOfNodes = nodeNetworkInfo.size();
            this.distMatrixMeters = NetworkUtil.getDistanceMatrixMeters(pathDistanceMatrix);
            this.distMatrix = NetworkUtil.getDistanceMatrixFrom(pathDurationsMatrix);
            this.closestZones = NetworkUtil.getClosestZoneMap(4, distMatrixMeters, Arrays.stream(NetworkUtil.ZONE_IDS).boxed().collect(Collectors.toList()));
//            distMatrixMeters = getDistanceMatrixMeters(pathDistanceMatrix);
            adjacencyMatrix = NetworkUtil.getAdjacencyMatrix(pathAdjacencyMatrix);
            networkGraph = NetworkUtil.getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrix);

            Logging.logger.info("# Reading precalculated PUDO data from '{}'...", pathPrecalculatedPermutations);
            loadPrecalculatedPermutationsPUDO(pathPrecalculatedPermutations);


            if (Config.getInstance().getSpAlgorithm().equals(SHORTEST_PATH_DIJKSTRA)) {
                this.shortestPath = new DijkstraShortestPath<>(networkGraph);
            } else if (Config.getInstance().getSpAlgorithm().equals(SHORTEST_PATH_ASTAR)) {
                this.shortestPath = new AStarShortestPath<>(
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


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static Dao getInstance() {
        return ourInstance;
    }


    /**
     * Reset trip records reading file.
     */
    public void resetRecords() {

        Logging.logger.info("Resetting dao");

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

    public Set<User> getListTripsClassed(Date earliestTime, int timeSpanSec, int maxPassengerCount, int maxNumber) {

        List<User> trips = getListTripsClassed(earliestTime, timeSpanSec, maxPassengerCount);

        Collections.shuffle(trips);

        if (trips.size() > maxNumber) {
            return trips.stream().limit(maxNumber).collect(Collectors.toSet());
        }

        return new HashSet<>(trips);
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

    public Set<User> getListTripsClassedShuffled(LocalDateTime earliestTime, int timeSpanSec, int maxPassengerCount, double percentage, Random rand) {

//        List<User> trips = getListTripsClassed(earliestTime, timeSpanSec, maxPassengerCount);
//
//        Collections.shuffle(trips, rand);
//
//        return getTripSubset(percentage, trips);
        return null;

    }



    private Set<User> getTripSubset(double percentage, List<User> trips) {
        if (trips.size() > percentage * trips.size()) {
            int maxSize = (int) (percentage * trips.size());
            return trips.stream().limit(maxSize).collect(Collectors.toSet());
        }
        return new HashSet<>(trips);
    }

//
//    public Set<User> getListTrips(Date earliestDatetime, int timeSpanSec, int maxPassengerCount, int maxNumber) {
//        Set<User> trips = getListTrips(earliestDatetime, timeSpanSec, maxPassengerCount);
//        if (trips.size() > maxNumber) {
//            trips = trips.stream().limit(maxNumber).collect(Collectors.toSet());
//        }
//        return trips;
//    }

//    /**
//     * Read a request batch of timeSpanSec seconds from file whose number of passengers is <= maxPassengerCount
//     *
//     * @param timeSpanSec       Time window of batch of requests
//     * @param maxPassengerCount Keep records with PASSENGER_COUNT lower
//     * @return
//     */
//    public Set<User> getListTrips(Date earliestDatetime, int timeSpanSec, int maxPassengerCount) {
//
//        Set<User> listUser = new HashSet<>();
//
//        for (CSVRecord record : records) {
//
//            if (Integer.parseInt(record.get(Request.PASSENGER_COUNT)) > maxPassengerCount) {
//                continue;
//            }
//
//            User user = new User(
//                    record.get(Request.PICKUP_DATETIME),
//                    earliestDatetime,
//                    Integer.parseInt(record.get(Request.PASSENGER_COUNT)),
//                    Integer.parseInt(record.get(Request.PICKUP_NODE_ID)),
//                    Integer.parseInt(record.get(Request.DROPOFF_NODE_ID)),
//                    Double.parseDouble(record.get(Request.PICKUP_LATITUDE)),
//                    Double.parseDouble(record.get(Request.PICKUP_LONGITUDE)),
//                    Double.parseDouble(record.get(Request.DROPOFF_LATITUDE)),
//                    Double.parseDouble(record.get(Request.DROPOFF_LONGITUDE)));
//
//            if (user.getReqTime() >= earliestTimeRequestBatch + timeSpanSec) {
//                earliestTimeRequestBatch = earliestTimeRequestBatch + timeSpanSec;
//                userBuff = user;
//                break;
//            }
//
//            listUser.add(user);
//        }
//
//        return listUser;
//    }


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
//        assert latestTimeRequestBatch == Environment.currentTime : String.format("%s - %s", latestTimeRequestBatch, Environment.currentTime);
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
            if (Integer.parseInt(record.get(Request.PASSENGER_COUNT)) > maxPassengerCount) {
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
//
//        for (User user : listUser) {
//            updatePerformanceClass(user, getRandomClassRoulleteWheel(qosClasses));
//        }
        Logging.logger.info("Read {}", listUser.size());

        return listUser;
    }

    private Date getPickupDateTime(CSVRecord record) {
        try {
            return DateUtil.formatter_date_time.parse(record.get(PICKUP_DATETIME));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Date();
    }

    Map<String, Set<String>> getCountUserClass(List<User> users) {
        return users.stream().collect(Collectors.groupingBy(User::getPerformanceClass, Collectors.mapping(User::toString, toSet())));
    }



//    /**
//     * Get list of users, attributing classes A, B, and C to them (according to shares defined in config)
//     *
//     * @param timeSpanSec       Time span of pooling
//     * @param maxPassengerCount Maximum passenger count (<= max. vehicle capacity)
//     * @return
//     */
//    public List<User> getListTripsClassed2(int timeSpanSec, int maxPassengerCount) {
//
//        // Start list of users with buffer from last iteration
//        // (users read ahead time span to be placed in next iteration)
//        Set<User> listUser = new HashSet<>();
//        // Logging.logger.info("%d",timeSpanSec);
//        // Continue reading records
////            if (userBuff.get(userBuff.size()-1).getReqTime() < currentTime + timeSpanSec){
////            return new ArrayList<>();
////        }
//        for (int i = iUserNextRound; i < allUsers.size(); i++) {
//
//            User user = allUsers.get(i);
//            // Skip passenger record with high passenger count
//            if (user.getNumPassengers() > maxPassengerCount) {
//                continue;
//            }
//
//            //TODO - User with invalid ids! (clean in python)
//            if (user.getDistFromTo() < 0) {
//                continue;
//            }
//            // Stop reading if request is out of time span
//            if (user.getReqTime() >= earliestTimeRequestBatch + timeSpanSec) {
//                earliestTimeRequestBatch = earliestTimeRequestBatch + timeSpanSec;
//                iUserNextRound = i;
//                break;
//            }
//
//            // Add user to list
//            listUser.add(user);
//        }
//        //Logging.logger.info("RECORD:");
//        //Logging.logger.info(record);
//        Logging.logger.info("USER BUF: {}", userBuff);
//        Logging.logger.info("LIST USER:");
//        listUser.forEach(user -> Logging.logger.info(user.getPickupDatetime()));
//
//        // Number of requests for each class according to their share
//
//        List<User> classed = new ArrayList<>(listUser);
//
//        // List of class tags
//
//        List<String> classTagList = new ArrayList<>(Config.getInstance().qosDic.keySet());
//
//        Map<String, Integer> usersPerClass = new HashMap<>();
//
//        // Filling the number of users per class
//        for (Entry<String, Qos> e : Config.getInstance().qosDic.entrySet()) {
//            String qsClass = e.getKey();
//            int qtUsers = (int) (e.getValue().share * listUser.size());
//            usersPerClass.put(qsClass, qtUsers);
//        }
//
//        // Fill the number of users per class (in case proportions failed)
//        while (usersPerClass.values().stream().mapToInt(Integer::intValue).sum() < listUser.size()) {
//            //Logging.logger.info("TAG LIST:" + classTagList + "- qosDic: " + Config.getInstance().qosDic);
//            int randClass = Dao.getInstance().rand.nextInt(classTagList.size());
//            usersPerClass.put(classTagList.get(randClass), usersPerClass.get(classTagList.get(randClass)) + 1);
//        }
//
//        // Assigning classes
//        for (int j = 0; j < classed.size(); j++) {
//
//            String qosClass = null;
//
//            // Repeats until a valid qos class is found
//            while (qosClass == null) {
//
//                int randClass = Dao.getInstance().rand.nextInt(classTagList.size());
//
//                String sqClass = classTagList.get(randClass);
//
//                if (usersPerClass.get(sqClass) > 0) {
//                    qosClass = sqClass;
//                    usersPerClass.put(classTagList.get(randClass), usersPerClass.get(classTagList.get(randClass)) - 1);
//                }
//            }
//            classed.get(j).updatePerformanceClass(qosClass);
//
//        }
//
//        return classed;
//    }

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

