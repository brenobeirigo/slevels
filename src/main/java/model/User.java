package model;


import config.Config;
import dao.Dao;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import org.apache.commons.csv.CSVRecord;
import simulation.Method;

import java.util.*;

import static config.Config.*;

public class User implements Comparable<User> {

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
    private int distFromTo;

    private boolean servedByHired;
    //Number of passengers in request
    private int numPassengers;

    // From - To nodes
    private NodePK nodePk;
    private NodeDP nodeDp;
    // Total ride time
    private int rideTime;

    private boolean sharingAllowed;

    // User performance clas (A, B, and C)
    private String performanceClass;

    // Record from data set that originated request
    private CSVRecord record;

    private short waitingRounds;

    /**
     * Construct user by reading request record from record set.
     *
     * @param record
     */
    public User(CSVRecord record) {
        int originId = Integer.valueOf(record.get("pk_id"));
        int destinationId = Integer.valueOf(record.get("dp_id"));
        if (Dao.getInstance().getUnreachable().contains(originId)) {
            this.distFromTo = Short.MIN_VALUE;
        } else {
            this.distFromTo = Dao.getInstance().getDistSec(originId, destinationId);
        }

        this.reqTime = Config.getInstance().date2Seconds(record.get("pickup_datetime"));
        this.setNumPassengers(Integer.valueOf(record.get("passenger_count")));
        this.id = ++nTrips;
        this.record = record;
    }

    public int getDistFromTo() {
        return distFromTo;
    }

    public void setDistFromTo(int distFromTo) {
        this.distFromTo = distFromTo;
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


    public boolean isSharingAllowed() {
        return sharingAllowed;
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
        this.sharingAllowed = Config.getInstance().qosDic.get(this.performanceClass).allowedSharing;

        //System.out.println(this.reqTime + "-" + pk_latest + ": " +  dp_earliest + ": " + " = " + dp_latest);

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
        this.nodePk.increaseUrgency();
        this.numberOfDelayExtensions++;
    }


    /**
     * Increase latest visit times of pickup and drop-off nodes in "extraDelay = class pk delay"units.
     */
    public void lowerServiceLevel() {
        int extraDelay = Config.getInstance().qosDic.get(this.getPerformanceClass()).pkDelay;
        this.nodePk.increaseLatest(extraDelay);
        this.nodeDp.increaseLatest(extraDelay);
        this.nodePk.increaseUrgency();
        this.numberOfDelayExtensions++;
    }

    /**
     * Show vehicles able to pick up this user within maxDistance
     *
     * @param listVehicles
     */
    public Visit getVisitInRange(List<Vehicle> listVehicles,
                                 int currentTime) {

        // Extra delay is equals to original pickup delay of customer class
        int maxDistance = Config.getInstance().qosDic.get(this.getPerformanceClass()).pkDelay;

        System.out.println("Vehicle distances to " + this.getId());
        Set<Vehicle> listOfCloseVehicles = new TreeSet<>();
        Set<Visit> listOfVisits = new TreeSet<>();

        // Aux. best visit for comparison
        Visit bestVisit = null;

        for (Vehicle v : listVehicles) {

            int dist = Dao.getInstance().getDistSec(v.getCurrentNode(), this.getNodePk());

            if (dist <= maxDistance) {

                listOfCloseVehicles.add(v);

                System.out.println(String.format("%s -> %s - %d - %s", v.getId(), this.getInfo(), dist, v.getInfo()));

                Visit candidateVisit = v.getBestInsertion(this, currentTime);

                // Update best visit if delay of candidate visit is shorter
                if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {

                    // Updating visit
                    bestVisit = candidateVisit;

                }

                if (candidateVisit != null) {

                    listOfVisits.add(candidateVisit);
                }

                System.out.println("##### ENROUTE");
                Visit visitNodesEnroute = v.getVisitWithEnroute();
            }
        }

        return bestVisit;
    }

    public boolean canBePickedUp(int currentTime) {
        return currentTime <= this.nodePk.getLatest();
    }

    @Override
    public int compareTo(User that) {
        // Urgent nodes are priority
        if (this.nodePk.getUrgent() > that.nodePk.getUrgent()) return BEFORE;
        if (this.nodePk.getUrgent() < that.nodePk.getUrgent()) return AFTER;

        if (this.getPerformanceClass().compareTo(that.performanceClass) < 0) return BEFORE;
        if (this.getPerformanceClass().compareTo(that.performanceClass) > 0) return AFTER;

        if (this.waitingRounds > that.waitingRounds) return BEFORE;
        if (this.waitingRounds < that.waitingRounds) return AFTER;

        return EQUAL;
    }

    public short getWaitingRounds() {
        return waitingRounds;
    }

    public void setWaitingRounds(short waitingRounds) {
        this.waitingRounds = waitingRounds;
    }

    public void increaseRoundsWaiting() {
        this.waitingRounds++;
    }

    /**
     * Try to insert the user in a list of vehicles, and return the best best insertion.
     *
     * @param listVehicles
     * @param currentTime
     * @param stopAtFirstBest
     * @return Best visit, or null
     */
    public Visit getBestVisitByInsertion(List<Vehicle> listVehicles, int currentTime, boolean stopAtFirstBest) {

        Visit bestVisit = null;

        // Try to insert user in each vehicle
        for (Vehicle v : listVehicles) {

            // Rebalancing vehicles cannot service users
            //if(v.isRebalancing()){
            //   continue;
            //}

                /*todo - CHECK IN PARALLEL
                if (checkInParallel) {

                    // Sequence with user to be added in vehicle
                    Set<User> auxUserSequence = new HashSet<>();
                    auxUserSequence.add(u);

                    candidateVisit = Method.getBestInsertionParallel(auxUserSequence, v, 2, true, maxPermutationsFCFS);

                */

            //######################################################################################################
            Visit candidateVisit = v.getBestInsertion(this, currentTime);
            //Visit candidateVisit = v.getBestInsertionOld(u, currentTime);
            //######################################################################################################
            //if (candidateVisit != null)
            //    visitList.add(candidateVisit);

            // Update best visit if delay of candidate visit is shorter
            if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {

                // Updating visit
                bestVisit = candidateVisit;

                // Stop at the first improvement
                if (stopAtFirstBest) {
                    break;
                }
            }
        }
        return bestVisit;
    }


    /**
     * Try to insert the user in a list of vehicles, and return the best best insertion.
     *
     * @param listVehicles
     * @param currentTime
     * @param stopAtFirstBest
     * @return Best visit, or null
     */
    public Visit getBestVisitByInsertion2(List<Vehicle> listVehicles, int currentTime, boolean stopAtFirstBest) {

        Visit bestVisit = null;

        // Try to insert user in each vehicle
        for (Vehicle v : listVehicles) {

            // Rebalancing vehicles cannot service users
            //if(v.isRebalancing()){
            //   continue;
            //}

                /*todo - CHECK IN PARALLEL
                if (checkInParallel) {

                    // Sequence with user to be added in vehicle
                    Set<User> auxUserSequence = new HashSet<>();
                    auxUserSequence.add(u);

                    candidateVisit = Method.getBestInsertionParallel(auxUserSequence, v, 2, true, maxPermutationsFCFS);

                */

            //######################################################################################################
            Visit candidateVisit = v.getBestInsertion(this, currentTime);
            //Visit candidateVisit = v.getBestInsertionOld(u, currentTime);
            //######################################################################################################
            //if (candidateVisit != null)
            //    visitList.add(candidateVisit);

            // Update best visit if delay of candidate visit is shorter
            if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {

                // Updating visit
                bestVisit = candidateVisit;

                // Stop at the first improvement
                if (stopAtFirstBest) {
                    break;
                }
            }
        }
        return bestVisit;
    }

    public void printDetailed() {
        System.out.println(String.format("%s(%s) - %s[%d](%d << %d - %d << %d) -> %s[%d](%d << %d - %d << %d) - Distance(s): %d - Distance(km): %f -  #Passengers: %d",
                this,
                this.performanceClass,
                this.nodePk,
                this.nodePk.getNetworkId(),
                this.nodePk.getEarliest(),
                this.nodePk.getArrival(),
                this.nodePk.getDeparture(),
                this.nodePk.getLatest(),
                this.nodeDp,
                this.nodeDp.getNetworkId(),
                this.nodeDp.getEarliest(),
                this.nodeDp.getArrival(),
                this.nodeDp.getDeparture(),
                this.nodeDp.getLatest(),
                Dao.getInstance().getDistSec(this.nodePk, this.nodeDp),
                Dao.getInstance().getDistKm(this.nodePk.getNetworkId(), this.nodeDp.getNetworkId()),
                this.numPassengers));
    }

    public void computeHiring() {
        this.servedByHired = true;
    }

    public boolean isServedByHired() {
        return this.servedByHired;
    }
}