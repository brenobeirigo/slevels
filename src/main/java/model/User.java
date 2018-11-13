package model;


import config.Config;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import org.apache.commons.csv.CSVRecord;
import simulation.Method;

import java.util.HashMap;
import java.util.Map;

public class User {

    // Total number of requests
    public static int nTrips = 0;
    // Status of each request
    public static int[][] status = new int[Node.MAX_NUMBER_NODES][5];
    // Map of all users (key = user id)
    public static Map<Integer, User> mapOfUsers = new HashMap<>();
    private int numberOfDelayExtensions = 0;

    // Model.User id
    private int id;

    //Seconds from first date (defined in config.Config)
    private int departure, arrival, reqTime;

    //Delays
    private int pk_delay, tt_delay;

    //Number of passengers in request
    private int numPassengers;

    // From - To nodes
    private NodePK nodePk;
    private NodeDP nodeDp;
    // Total ride time
    private int rideTime;

    // User performance clas (A, B, and C)
    private String performanceClass;

    // Record from data set that originated request
    private CSVRecord record;

    /**
     * Construct user by reading request record from record set.
     *
     * @param record
     */
    public User(CSVRecord record) {
        this.reqTime = Config.getInstance().date2Seconds(record.get("pickup_datetime"));
        this.setNumPassengers(Integer.valueOf(record.get("passenger_count")));
        this.id = ++nTrips;
        this.record = record;
    }

    public User(String reqTime,
                int numPassengers,
                int originId,
                int destinationId,
                double originLat,
                double originLon,
                double destinationLat,
                double destinationLon) {

        this.reqTime = Config.getInstance().date2Seconds(reqTime);
        this.setNumPassengers(numPassengers);
        this.id = ++nTrips;

        String performanceClass = "C";
        int pk_latest = Method.getLatestPK(this.reqTime, performanceClass);
        int dp_earliest = Method.getEarliestDp(this.reqTime, originId, destinationId, performanceClass);
        int dp_latest = Method.getLatestDp(this.reqTime, originId, destinationId, performanceClass);

        this.nodePk = new NodePK(
                originId,
                originLat,
                originLon,
                this.id,
                this.reqTime,
                pk_latest,
                numPassengers);

        this.nodeDp = new NodeDP(
                destinationId,
                destinationLat,
                destinationLon,
                this.id,
                dp_earliest,
                dp_latest,
                -numPassengers);

        // Save all users
        mapOfUsers.put(this.id, this);
    }

    public static void reset() {
        /* Reset user */
        mapOfUsers = new HashMap<>();
        nTrips = 0;
        status = new int[Node.MAX_NUMBER_NODES][5];
    }

    /**
     * Increase how many times user maximum delays had to increase.
     */
    public void increaseNumberOfDelayExtensions() {
        this.numberOfDelayExtensions++;
    }

    public void updatePerformanceClass(String performanceClass) {

        this.performanceClass = performanceClass;

        int originId = Integer.valueOf(record.get("pk_id"));
        int destinationId = Integer.valueOf(record.get("dp_id"));
        double originLat = Double.valueOf(record.get("pickup_latitude"));
        double originLon = Double.valueOf(record.get("pickup_longitude"));
        double destinationLat = Double.valueOf(record.get("dropoff_latitude"));
        double destinationLon = Double.valueOf(record.get("dropoff_longitude"));

        int pk_latest = Method.getLatestPK(this.reqTime, performanceClass);
        int dp_earliest = Method.getEarliestDp(this.reqTime, originId, destinationId, performanceClass);
        int dp_latest = Method.getLatestDp(this.reqTime, originId, destinationId, performanceClass);

        // Start nodes
        this.nodePk = new NodePK(
                originId,
                originLat,
                originLon,
                this.id,
                this.reqTime,
                pk_latest,
                numPassengers);

        this.nodeDp = new NodeDP(
                destinationId,
                destinationLat,
                destinationLon,
                this.id,
                dp_earliest,
                dp_latest,
                -numPassengers);

        // Save all users
        mapOfUsers.put(this.id, this);
    }

    @Override
    public int hashCode() {
        return this.id;
    }

    @Override
    public String toString() {
        return String.format("%5s(%d%s)",
                String.valueOf(id),
                getNumPassengers(),
                this.performanceClass);
    }

    public String getInfo() {
        return String.format("%s [%s -> %s -> %s]",
                this,
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getEarliest())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getArrival())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getArrival())));
    }

    public String getDetailedInfo() {
        return String.format("%s [%s -> %s -> %s] - [%s -> %s -> %s]",
                this,
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getEarliest())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getArrival())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getLatest())),

                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getEarliest())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getArrival())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getLatest())));
    }

    public int getDeparture() {
        return departure;
    }

    public int getReqTime() {
        return reqTime;
    }

    public int getArrival() {
        return arrival;
    }

    public NodePK getNodePk() {
        return nodePk;
    }

    public NodeDP getNodeDp() {
        return nodeDp;
    }

    public int getId() {
        return id;
    }

    public void setRideTime(int rideTime) {
        this.rideTime = rideTime;
    }

    public int getNumPassengers() {
        return numPassengers;
    }

    public void setNumPassengers(int numPassengers) {
        this.numPassengers = numPassengers;
    }

    public String getPerformanceClass() {
        return performanceClass;
    }

    public int getNumberOfDelayExtensions() {
        return numberOfDelayExtensions;
    }

    /**
     * Increase latest visit times of pickup and drop-off nodes in "extraDelay"units.
     *
     * @param extraDelay
     */
    public void lowerServiceLevel(int extraDelay) {
        this.nodePk.increaseLatest(extraDelay);
        this.nodeDp.increaseLatest(extraDelay);
    }
}