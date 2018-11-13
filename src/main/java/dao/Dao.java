package dao;


import config.Config;
import model.User;
import model.node.Node;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class Dao {

    private static Dao ourInstance = new Dao();

    // Speed of vehicles m/s
    public static final int SPEED = 30;

    private String file_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\distance_matrix_meters_manhattan-island-new-york-city-new-york-usa.csv";

    private String trip_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\tripdata_valentines_2011_ids.csv";

    private double[][] distMatrix;

    private List<User> userBuff;

    private Iterable<CSVRecord> records;

    private int currentTime = 0;

    public Random rand;

    private Dao() {
        try {

            rand = new Random();

            distMatrix = getDistanceMatrix(file_path);

            userBuff = new ArrayList<>();

            records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new FileReader(trip_path));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    public static Dao getInstance() {
        return ourInstance;
    }

    private double[][] getDistanceMatrix(String file_path) {
        double[][] dist_matrix = new double[4469][4469];

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

    public List<User> getListTrips(int timeSpanSec, int maxPassengerCount, int maxNumber) {
        List<User> trips = getListTrips(timeSpanSec, maxPassengerCount);
        if (trips.size() > maxNumber) {
            // Collections.shuffle(trips);
            trips = trips.subList(0, maxNumber);
        }

        return trips;
    }

    public List<User> getListTripsClassed(int timeSpanSec, int maxPassengerCount, int maxNumber) {

        //System.out.println("Pooling...");
        List<User> trips = getListTripsClassed(timeSpanSec, maxPassengerCount);

        if (trips.size() > maxNumber) {
            // Collections.shuffle(trips);
            trips = trips.subList(0, maxNumber);
        }

        return trips;
    }

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
                userBuff.add(user);
                break;
            }

            // Add user to list
            listUser.add(user);
        }

        // Number of requests for each class according to their share
        int contA = (int) (Config.getInstance().qosDic.get("A").share * listUser.size());
        int contB = (int) (Config.getInstance().qosDic.get("B").share * listUser.size());
        int contC = listUser.size() - contA - contB;

        //System.out.println(String.format("%d(A) + %d(B) + %d(C) = %d", contA, contB, contC, listUser.size()));

        List<User> classed = new ArrayList<>(listUser);

        // Users to add a class
        while (!classed.isEmpty()) {
            String groupClass = "-";

            if (contA > 0) {
                contA--;
                groupClass = "A";
            } else if (contB > 0) {
                contB--;
                groupClass = "B";
            } else if (contC > 0) {
                contC--;
                groupClass = "C";
            }

            // Remove classed user
            User u = classed.remove(0);

            // Update class
            u.updatePerformanceClass(groupClass);
        }

        return listUser;
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
     * @param from node id
     * @param to node id
     * @return distance in seconds
     */
    public int getDistSec(int from, int to) {
        return (int) (3.6 * distMatrix[from][to] / SPEED);
    }

    /**
     * Get distance in seconds (considering 30 m/s)
     * @param from node
     * @param to node
     * @return distance in seconds
     */
    public int getDistSec(Node from, Node to) {
        return (int) (3.6 * distMatrix[from.getNetworkId()][to.getNetworkId()] / SPEED);
    }

    public double[][] getDistMatrix() {
        return distMatrix;
    }
}
