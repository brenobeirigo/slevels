package dao;


import model.User;
import model.Vehicle;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class Dao {

    private static Dao ourInstance = new Dao();
    private Map<Integer, Vehicle> dicVehicle;
    private String file_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\distance_matrix_meters_manhattan-island-new-york-city-new-york-usa.csv";

    private String trip_path = "C:\\Users\\breno\\OneDrive\\Phd_TU\\PROJECTS\\rs_heuristic\\data\\gen\\data\\tripdata_valentines_2011_ids.csv";

    private double[][] distMatrix;

    private List<User> userList;

    private List<User> userBuff;

    private Iterable<CSVRecord> records;

    private int currentTime = 0;

    private Dao() {
        try {

            distMatrix = getDistanceMatrix(file_path);

            userBuff = new ArrayList<>();

            records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(new FileReader(trip_path));

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


    public List<User> getListTrips(int timeSpanSec, int maxNumber) {
        List<User> trips = getListTrips(timeSpanSec);
        if (trips.size() > maxNumber) {
            Collections.shuffle(trips);
            trips = trips.subList(0, maxNumber);
        }

        return trips;
    }

    public List<User> getListTrips(int timeSpanSec) {
        List<User> listUser = userBuff;
        for (CSVRecord record : records) {
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

    public double getDist(int from, int to) {
        return distMatrix[from][to];
    }

    public double[][] getDistMatrix() {
        return distMatrix;
    }

    public int getDistSec(int from, int to) {
        return (int) (3.6 * distMatrix[from][to] / 30);
    }
}
