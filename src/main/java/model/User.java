package model;


import config.Config;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import simulation.Method;

import java.util.HashMap;
import java.util.Map;

public class User {

    public static int nTrips = 0;
    public static int[][] status = new int[Node.MAX_NUMBER_NODES][5];
    public static Map<Integer, User> all_users = new HashMap<>();
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
    private int rideTime;

    public User(String reqTime, int numPassengers, int originId, int destinationId, double originLat, double originLon, double destinationLat, double destinationLon) {
        this.reqTime = Config.getInstance().date2Seconds(reqTime);
        this.setNumPassengers(numPassengers);
        this.id = ++nTrips;
        int pk_latest = this.reqTime + Config.getInstance().qosDic.get('C').pkDelay;
        int dp_earliest = Method.getEarliestDp(this.reqTime, originId, destinationId, 'C');
        int dp_latest = Method.getLatestDp(this.reqTime, originId, destinationId, 'C');

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
        all_users.put(this.id, this);
    }

    public static int getPkArrival(int userId) {
        return status[userId][0];
    }

    @Override
    public int hashCode() {
        return this.id;
    }

    @Override
    public String toString() {
        return String.format("%5s(%d)",
                String.valueOf(id),
                getNumPassengers());
    }

    public String getInfo() {
        return String.format("%d(%d) [%s -> %s -> %s]",
                id,
                getNumPassengers(),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getEarliest())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getArrival())),
                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getArrival())));
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

    public void setId(int id) {
        this.id = id;
    }

    public int getRideTime() {
        return rideTime;
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
}
