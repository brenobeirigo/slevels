package dao;


import config.Config;
import javafx.geometry.Point2D;
import model.User;
import model.node.Node;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Dao {


    // Speed of vehicles m/s
    public static final double SPEED = 30;
    public static int numberOfNodes;
    private static Dao ourInstance = new Dao();
    public Random rand;
    //private String file_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\distance_matrix_m_manhattan-island-new-york-city-new-york-usa.csv";
    //private String trip_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\tripdata_valentines_2011_ids.csv";

    private String file_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\in\\data\\dist\\distance_matrix_m_manhattan-island-new-york-city-new-york-usa.csv";
    private String trip_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\in\\data\\tripdata\\tripdata_excerpt_2011-2-1_2011-2-28_ids.csv";

    // Geographical data
    private Map<Integer, Point2D> nodeLocationMap; // Map of node ids and respective coordinates

    private short[][] distMatrix;
    private double[][] distMatrixKm;

    private ArrayList<ArrayList<ArrayList<Short>>> shortestPathsNodeIds;
    private ArrayList<ArrayList<ArrayList<Short>>> shortestPathDistances;
    private List<Integer> unreachable;
    private List<User> userBuff;
    private Iterable<CSVRecord> records;
    private int currentTime = 0;


    // Database
    private Connection conn;

    private Dao() {
        try {

            //Starting connection with DB
            //Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            //conn = DriverManager.getConnection("jdbc:mysql://localhost/shortest_paths_manhattan?user=root&password=admin123");

            // Pool map of nodes from server. Format: {id=Point(x,y)}
            System.out.println("Pulling nodes from server...");
            nodeLocationMap = ParseJsonUtil.getNodeDictionary(ServerUtil.getNodeList());
            numberOfNodes = nodeLocationMap.size();
            System.out.println(String.format("%d nodes read.", numberOfNodes));

            System.out.println("Calculating distance matrix in seconds...");

            distMatrix = getDistanceMatrix(file_path);

            rand = new Random();

            /*
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
            */

            userBuff = new ArrayList<>();
            records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new FileReader(trip_path));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static Dao getInstance() {
        return ourInstance;
    }

    /**
     * Access coordinate of point.
     *
     * @param id Node id
     * @return Point2D coordinate
     */
    public Point2D getLocation(int id) {
        return nodeLocationMap.get(id);
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


    public ArrayList<Short> getShortestPathBetween(int o, int d) {

        if (shortestPathsNodeIds.get(o).get(d) == null) {
            //ArrayList<Short> sp = getShortestPathBetweenODfromFile(fromNode, toNode); // FROM FILE
            //ArrayList<Short> sp = getShortestPathBetweenODfromDB(fromNode, toNode); // FROM DB
            ArrayList<Short> sp = ServerUtil.getShortestPathBetween(o, d);
            shortestPathsNodeIds.get(o).set(d, sp);
        }

        return shortestPathsNodeIds.get(o).get(d);
    }

    /**
     * Reset trip records reading file.
     */
    public void resetRecords() {

        System.out.println("Resetting trip records...");

        try {

            // Reset system current for next test set
            currentTime = 0;

            // Read the requests from the beginning
            records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new FileReader(trip_path));

            // Start random again
            rand = new Random();

            // Buffer that saves previous is reset
            userBuff = new ArrayList<>();


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private short[][] getDistanceMatrix(String file_path) {
        distMatrixKm = new double[numberOfNodes][numberOfNodes];
        short[][] dist_matrix = new short[numberOfNodes][numberOfNodes];
        short[] countInvalid = new short[numberOfNodes];
        unreachable = new ArrayList<>();

        try {

            System.out.println("Reading distance data...");
            Reader in = new FileReader(file_path);

            int row = 0;

            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);
            for (CSVRecord record : records) {
                int col = 0;

                for (String r : record) {

                    if (r.equals("INF")) {
                        countInvalid[col]++;
                        //System.out.println(String.format("%6d %6d", row, col));
                        //invalid.add(col);
                    }

                    double km = r.equals("INF") ? Double.MIN_VALUE : Double.valueOf(r);
                    short sec = r.equals("INF") ? Short.MIN_VALUE : (short) (3.6 * km / SPEED + 0.5);

                    dist_matrix[row][col] = sec;
                    distMatrixKm[row][col] = km;

                    //System.out.print(String.format("%10.2f",km));
                    //todo nodes have distance zero
                    /*
                    if(row != col && sec == 0){
                        System.out.println(row + " - " + col + " - " + sec + " - " + r + Short.MIN_VALUE);
                    }
                    */
                    col++;
                }
                //System.out.println();
                row++;
            }


            for (int i = 0; i < countInvalid.length; i++) {

                if (countInvalid[i] > 4000)
                    unreachable.add(i);
            }

            System.out.println(unreachable);
            //System.out.println("INVALID:" + new ArrayList<>(Arrays.asList(countInvalid)));

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return dist_matrix;
    }

    private double[][] getDistanceMatrixDouble(String file_path) {
        double[][] dist_matrix = new double[numberOfNodes][numberOfNodes];

        try {
            System.out.println("Reading distance data...");
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

    public List<User> getListTripsClassed(int timeSpanSec, int maxPassengerCount, int maxNumber) {

        List<User> trips = getListTripsClassed2(timeSpanSec, maxPassengerCount);

        if (trips.size() > maxNumber) {
            trips = trips.subList(0, maxNumber);
        }

        return trips;
    }

    public List<User> getListTrips(int timeSpanSec, int maxPassengerCount, int maxNumber) {
        List<User> trips = getListTrips(timeSpanSec, maxPassengerCount);
        if (trips.size() > maxNumber) {
            trips = trips.subList(0, maxNumber);
        }
        return trips;
    }

    /**
     * Read a request batch of timeSpanSec seconds from file whose number of passengers is <= maxPassengerCount
     *
     * @param timeSpanSec       Time window of batch of requests
     * @param maxPassengerCount
     * @return
     */
    public List<User> getListTrips(int timeSpanSec, int maxPassengerCount) {

        List<User> listUser = userBuff;

        for (CSVRecord record : records) {

            if (Integer.valueOf(record.get("passenger_count")) > maxPassengerCount) {
                continue;
            }

            User user = new User(
                    record.get("pickup_datetime"),
                    Integer.valueOf(record.get("passenger_count")),
                    Integer.valueOf(record.get("pk_id")),
                    Integer.valueOf(record.get("dp_id")),
                    Double.valueOf(record.get("pickup_latitude")),
                    Double.valueOf(record.get("pickup_longitude")),
                    Double.valueOf(record.get("dropoff_latitude")),
                    Double.valueOf(record.get("dropoff_longitude")));

            if (user.getReqTime() >= currentTime + timeSpanSec) {
                currentTime = currentTime + timeSpanSec;
                userBuff = new ArrayList<>();
                userBuff.add(user);
                break;
            }

            listUser.add(user);
        }

        return listUser;
    }

    /**
     * Get list of users, attributing classes A, B, and C to them (according to shares defined in config)
     *
     * @param timeSpanSec       Time span of pooling
     * @param maxPassengerCount Maximum passenger count (<= max. vehicle capacity)
     * @return
     */
    public List<User> getListTripsClassed(int timeSpanSec, int maxPassengerCount) {

        // Start list of users with buffer from last iteration (users read, but not in time span)
        List<User> listUser = userBuff;

        // Continue reading records
        for (CSVRecord record : records) {

            // Skip passenger record with high passenger count
            if (Integer.valueOf(record.get("passenger_count")) > maxPassengerCount) {
                continue;
            }

            // Create user using record
            User user = new User(record);

            // Stop reading if request is out of time span
            if (user.getReqTime() >= currentTime + timeSpanSec) {
                currentTime = currentTime + timeSpanSec;
                userBuff = new ArrayList<>();
                // Save user wrongfully read to next iteration
                userBuff.add(user);
                break;
            }

            // Add user to list
            listUser.add(user);
        }

        // Number of requests for each class according to their share


        //System.out.println(String.format("%d(A) + %d(B) + %d(C) = %d", contA, contB, contC, listUser.size()));

        List<User> classed = new ArrayList<>(listUser);

        int fromUser = 0;
        int toUser = 0;
        List<String> keys = new ArrayList<>(Config.getInstance().qosDic.keySet());

        for (int i = 0; i < keys.size(); i++) {
            String qosClass = keys.get(i);
            int nUsersInClass = (int) (Config.getInstance().qosDic.get(qosClass).share * listUser.size());
            toUser = fromUser + nUsersInClass;
            for (int j = fromUser; j < toUser; j++) {
                classed.get(j).updatePerformanceClass(qosClass);
            }

            fromUser = toUser;
        }


        // Assign remaining users not covered by percentages
        finishAssignment:
        while (true) {
            for (int i = 0; i < keys.size(); i++) {
                if (toUser >= listUser.size()) {
                    break finishAssignment;
                }
                String qosClass = keys.get(i);
                classed.get(toUser).updatePerformanceClass(qosClass);
                toUser++;
            }
        }

        return listUser;
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
        List<User> listUser = userBuff;

        // Continue reading records
        for (CSVRecord record : records) {

            // Skip passenger record with high passenger count
            if (Integer.valueOf(record.get("passenger_count")) > maxPassengerCount) {
                continue;
            }

            // Create user using record
            User user = new User(record);

            //TODO - User with invalid ids! (clean in python)
            if (user.getDistFromTo() < 0) {
                continue;
            }

            // Stop reading if request is out of time span
            if (user.getReqTime() >= currentTime + timeSpanSec) {
                currentTime = currentTime + timeSpanSec;
                userBuff = new ArrayList<>();

                // Save user wrongfully read to next iteration
                userBuff.add(user);
                break;
            }

            // Add user to list
            listUser.add(user);
        }

        // Number of requests for each class according to their share

        List<User> classed = new ArrayList<>(listUser);

        // List of class tags
        List<String> classTagList = new ArrayList<>(Config.getInstance().qosDic.keySet());

        Map<String, Integer> usersPerClass = new HashMap<>();

        // Filling the number of users per class
        for (Entry<String, Config.Qos> e : Config.getInstance().qosDic.entrySet()) {
            String qsClass = e.getKey();
            int qtUsers = (int) (e.getValue().share * listUser.size());
            usersPerClass.put(qsClass, qtUsers);
        }

        // Fill the number of users per class (in case proportions failed)
        while (usersPerClass.values().stream().mapToInt(Integer::intValue).sum() < listUser.size()) {
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

    /**
     * Get distance in km
     *
     * @param from node id
     * @param to   node id
     * @return distance in km
     */
    public double getDistKm(int from, int to) {
        return distMatrixKm[from][to];
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
    public short getDistSec(int from, int to) {
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
    public short getDistSec(Node from, Node to) {
        return distMatrix[from.getNetworkId()][to.getNetworkId()];
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
    public int getIntermediateNodeNetworkId(int o, int d, int elapsedTime) {

        ArrayList<Short> sp = ServerUtil.getShortestPathBetween(o, d);

        // Building array of arrivals
        // e.g.,     List [      o,      1,      2,      3,      4,      5,      d]
        //       Arrivals [      0, d(o,1), d(1,2), d(2,3), d(3,4), d(4,5), d(5,d)]
        ArrayList<Short> spArrivals = new ArrayList<>();
        short starting = 0;
        spArrivals.add(starting);
        for (int i = 0; i < sp.size() - 1; i++) {
            short distanceLeg = getDistSec(sp.get(i), sp.get(i + 1));
            starting += distanceLeg;
            spArrivals.add(starting);
        }

        // No path between nodes
        if (spArrivals.isEmpty())
            return -1;

        if (elapsedTime <= 0)
            return -1;

        //Elapsed time higher than last node
        if (elapsedTime >= spArrivals.get(spArrivals.size() - 1))
            return -1;

        // Find position of first higher arrival
        int pos = Collections.binarySearch(spArrivals, (short) elapsedTime);

        // Correct for elapsedTime not in shortest path (<0)
        pos = pos >= 0 ? pos : -1 - pos;
        if (sp.get(pos) == d) {
            return -1;
        }

        // Decide which node is closer (before or after first higher)
        return sp.get(pos);
    }

    /**
     * Get the network id of the node in the shortest path connecting two other nodes.
     *
     * @param o
     * @param d
     * @param elapsedTime
     * @return network id, or -1 if there is no intermediate node
     */
    public int getIntermediateNodeNetworkIdOld(int o, int d, int elapsedTime) {

        //TODO Decide what to do when  elapsed time is zero
        //ArrayList<Short> sp = getShortestPathBetween(networkIdFrom, networkIdTo);
        ArrayList<Short> sp = ServerUtil.getShortestPathBetween(o, d);

        if (sp == null) {
            System.out.println("NULL" + o + " - " + d);
        }

        if (shortestPathDistances.get(o).get(d) == null) {

            ArrayList<Short> spArrivals = new ArrayList<>();

            short starting = 0;
            spArrivals.add(starting);

            //TODO there are nodes with zero distances between them! Should be removed!
            for (int i = 0; i < sp.size() - 1; i++) {
                short distanceLeg = getDistSec(sp.get(i), sp.get(i + 1));
                starting += distanceLeg;
                spArrivals.add(starting);
            }
            shortestPathDistances.get(o).set(d, spArrivals);
        }

        ArrayList<Short> intermediateArrivalsList = shortestPathDistances.get(o).get(d);


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
        int pos = Collections.binarySearch(intermediateArrivalsList, (short) elapsedTime);


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

    public short[][] getDistMatrix() {
        return distMatrix;
    }

    public List<Integer> getUnreachable() {
        return unreachable;
    }
}
