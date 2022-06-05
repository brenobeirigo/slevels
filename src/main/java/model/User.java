package model;


import config.Config;
import config.Qos;
import dao.Dao;
import dao.Logging;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import org.apache.commons.csv.CSVRecord;
import simulation.Method;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class User implements Comparable<User> {

    // Users have the following 3 states
    public static final int SERVICED_BY_DEDICATED_VEHICLE = 1;
    public static final int SERVICED_BY_HIRED_VEHICLE = 2;
    public static final int NOT_SERVICED = 0;
    public static final int WAITING = -1;

    public static final int PK_ARRIVAL_TIME = 0;
    public static final int DP_ARRIVAL_TIME = 1;
    public static final int PK_DELAY = 2;
    public static final int DP_DELAY = 3;
    // Total number of requests
    public static int nTrips = 0;
    // Status of each request
    public static int[][] status = new int[Node.MAX_NUMBER_NODES][5];
    // Map of all users (key = user id)
    public static Map<Integer, User> mapOfUsers = new HashMap<>();
    // If user cannot be picked up, service levels are lowered (this regulates MET and UNMET)
    public boolean serviceLevelLowered;
    public Qos qos;
    // Model.User id
    private int id;
    //Seconds from first date (defined in config.Config)
    private int departure, arrival, reqTime, dropoutTime;
    //Delays
    private int distFromTo;
    // Which type of vehicle serviced the request? Was it even serviced?
    private int servedBy;
    private VisitObj currentVisit;
    //Number of passengers in request
    private int numPassengers;
    // From - To nodes
    private NodePK nodePk;
    private NodeDP nodeDp;
    // Total ride time
    private int rideTime;
    private boolean sharingAllowed;
    // User performance class (A, B, and C)
    private String performanceClass;
    // Record from data set that originated request
    private CSVRecord record;
    private short waitingRounds;

    /**
     * Construct user by reading request record from record set.
     *
     * @param record
     */
    public User(Date earliestDatetime, CSVRecord record) {

        int originId = Integer.parseInt(record.get(Dao.PICKUP_NODE_ID));
        int destinationId = Integer.parseInt(record.get(Dao.DROPOFF_NODE_ID));
        this.distFromTo = Dao.getInstance().getDistSec(originId, destinationId);
        this.reqTime = Config.getInstance().date2Seconds(earliestDatetime, record.get(Dao.PICKUP_DATETIME));
        this.setNumPassengers(Integer.parseInt(record.get(Dao.PASSENGER_COUNT)));
        this.id = ++nTrips;
        this.record = record;
        this.servedBy = User.WAITING;
    }

    public User(int pkEarliest,
                int pkLatest,
                int dpEarliest,
                int dpLatest,
                int numPassengers,
                int originId,
                int destinationId,
                double originLat,
                double originLon,
                double destinationLat,
                double destinationLon) {

        this.servedBy = User.WAITING;

        this.reqTime = pkEarliest;
        this.setNumPassengers(numPassengers);
        this.id = ++nTrips;

        this.performanceClass = "B";


        this.nodePk = new NodePK(
                originId,
                originLat,
                originLon,
                this.id,
                this.reqTime,
                pkLatest,
                numPassengers);

        this.nodeDp = new NodeDP(
                destinationId,
                destinationLat,
                destinationLon,
                this.id,
                dpEarliest,
                dpLatest,
                -numPassengers);

        // Save all users
        mapOfUsers.put(this.id, this);
    }

    public User(String reqTime,
                Date earliestDatetime,
                int numPassengers,
                int originId,
                int destinationId,
                double originLat,
                double originLon,
                double destinationLat,
                double destinationLon) {

        this.servedBy = User.WAITING;

        this.reqTime = Config.getInstance().date2Seconds(earliestDatetime, reqTime);
        this.setNumPassengers(numPassengers);
        this.id = ++nTrips;

        String performanceClass = "B";
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

    public static List<User> filterFirstTier(Collection<User> users) {
        return users.stream().filter(User::isFirstTier).collect(Collectors.toList());
    }

    public static List<User> filterPreviouslyAssigned(Collection<User> users) {
        return users.stream().filter(User::isPreviouslyAssigned).collect(Collectors.toList());
    }

    public static List<User> filterSecondTier(Collection<User> users) {
        return users.stream().filter(user -> (user.getCurrentVisit() == null || !user.isFirstTier())).collect(Collectors.toList());
    }

    public static List<User> filterUsersOfQos(Collection<User> users, Qos qos){
        return users.stream().filter(user -> user.qos == qos).collect(Collectors.toList());
    }

    public static List<User> getAssigned(Collection<User> requests) {
        return requests.stream().filter(user -> user.getCurrentVisit() != null).collect(Collectors.toList());
    }

    public static List<User> getUnassigned(Collection<User> requests) {
        return requests.stream().filter(user -> user.getCurrentVisit() == null).collect(Collectors.toList());
    }

    public static List<Node> getUserPickupNodes(Collection<User> users) {
        List<Node> targets = new ArrayList<>();
        if (users != null && !users.isEmpty()) {
            List<Node> rejected = users.stream().map(User::getNodePk).collect(Collectors.toList());
            targets.addAll(rejected);
            //Comparator<Node> comparator = Comparator.comparing(Node::getArrivalSoFar).reversed();
            //pickupUnmet.sort(comparator);
            //Logging.logger.info(" Pickup unmet = " + pickupUnmet  + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(pickupUnmet)) + ")");


        }
        return targets;
    }

    public static Set<User> filterDisplaced(List<User> requests) {
        return requests.stream().filter(user -> user.getNodePk().getArrivalSoFar() != null && user.getCurrentVisit() == null).collect(Collectors.toSet());
    }

    public String getPickupDatetime() {
        return this.record.get("pickup_datetime");
    }

    public int getDistFromTo() {
        return distFromTo;
    }

    public void setDistFromTo(int distFromTo) {
        this.distFromTo = distFromTo;
    }

    public boolean isSharingAllowed() {
        return sharingAllowed;
    }

    public void updatePerformanceClass(String performanceClass) {

        this.performanceClass = performanceClass;

        int originId = Integer.parseInt(record.get(Dao.PICKUP_NODE_ID));
        int destinationId = Integer.parseInt(record.get(Dao.DROPOFF_NODE_ID));
        double originLat = 0; //Double.parseDouble(record.get("pickup_latitude"));
        double originLon = 0; //Double.parseDouble(record.get("pickup_longitude"));
        double destinationLat = 0; //Double.parseDouble(record.get("dropoff_latitude"));
        double destinationLon = 0; //Double.parseDouble(record.get("dropoff_longitude"));
        int pk_latest = Method.getLatestPK(this.reqTime, performanceClass);
        int dp_earliest = Method.getEarliestDp(this.reqTime, originId, destinationId, performanceClass);
        int dp_latest = Method.getLatestDp(this.reqTime, originId, destinationId, performanceClass);
        this.sharingAllowed = Config.getInstance().qosDic.get(this.performanceClass).allowedSharing;
        this.qos = getInstance().qosDic.get(this.performanceClass);

        //Logging.logger.info(this.reqTime + "-" + pk_latest + ": " +  dp_earliest + ": " + " = " + dp_latest);

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
                this.id,
                getNumPassengers(),
                this.performanceClass);
    }

//    public String getInfo() {
//        return String.format("%s [%s -> %s -> %s]",
//                this,
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getEarliest())),
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getArrival())),
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getArrival())));
//    }

//    public String getDetailedInfo() {
//        return String.format("%s [%s -> %s -> %s] - [%s -> %s -> %s]",
//                this,
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getEarliest())),
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getArrival())),
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodePk.getLatest())),
//
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getEarliest())),
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getArrival())),
//                Config.formatter_t.format(Config.getInstance().seconds2Date(nodeDp.getLatest())));
//    }

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

    public boolean isServiceLevelLowered() {
        return serviceLevelLowered;
    }

    /**
     * Increase latest visit times of pickup and drop-off nodes in "extraDelay" units.
     *
     * @param extraDelay
     */
    public void lowerServiceLevel(int extraDelay) {
        this.nodePk.increaseLatest(extraDelay);
        this.nodeDp.increaseLatest(extraDelay);
        this.serviceLevelLowered = true;
    }

    public boolean isRejected() {
        return this.servedBy == User.NOT_SERVICED;
    }

    public boolean isServiced() {
        return isServicedByDedicated() || isServicedByHired();
    }

    public boolean isServicedByHired() {
        return this.servedBy == User.SERVICED_BY_HIRED_VEHICLE;
    }

    public boolean isServicedByDedicated() {
        return this.servedBy == User.SERVICED_BY_DEDICATED_VEHICLE;
    }

    public boolean isWaiting() {
        return this.servedBy == User.WAITING;
    }

//    /**
//     * Show vehicles able to pick up this user within maxDistance
//     *
//     * @param listVehicles
//     */
//    public Visit getVisitInRange(List<Vehicle> listVehicles,
//                                 int currentTime) {
//
//        // Extra delay is equals to original pickup delay of customer class
//        int maxDistance = Config.getInstance().qosDic.get(this.getPerformanceClass()).pkDelay;
//
//        Logging.logger.info("Vehicle distances to " + this.getId());
//        Set<Vehicle> listOfCloseVehicles = new TreeSet<>();
//        Set<Visit> listOfVisits = new TreeSet<>();
//
//        // Aux. best visit for comparison
//        Visit bestVisit = null;
//
//        for (Vehicle v : listVehicles) {
//
//            int dist = Dao.getInstance().getDistSec(v.getLastVisitedNode(), this.getNodePk());
//
//            if (dist <= maxDistance) {
//
//                listOfCloseVehicles.add(v);
//
//                Logging.logger.info("{}", String.format("%s -> %s - %d - %s", v.getId(), this.getInfo(), dist, v.getInfo()));
//
//                Visit candidateVisit = v.getVisitWithInsertedUser(this, currentTime);
//
//                // Update best visit if delay of candidate visit is shorter
//                if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {
//
//                    // Updating visit
//                    bestVisit = candidateVisit;
//
//                }
//
//                if (candidateVisit != null) {
//
//                    listOfVisits.add(candidateVisit);
//                }
//
//                Logging.logger.info("##### ENROUTE");
//                Visit visitNodesEnroute = v.getVisitWithEnroute();
//            }
//        }
//
//        return bestVisit;
//    }

    /**
     * Find best pair of edges (i,i+1) and (j,j+1) such that replacing
     * them with (i,j) and (i+1,j+1) minimizes tour length.
     */
    /*public Visit intensify2Opt(Visit visit){

        do {
            int minchange = 0;
            for (int i = 0; i < visit.sequenceVisits.size()-2; i++) {
                for (int j = i+2; j < visit.sequenceVisits.size()-2; j++) {

                    LinkedList<Node> newSequence = new LinkedList<>(visit);
                    newSequence.
                    newSequence.add(dpPos, candidateRequest.getNodeDp());
                    newSequence.add(pkPos, candidateRequest.getNodePk());

                    Visit v2 = new Visit

                    int change = Dao.getInstance().getDistKm(visit.sequenceVisits.get(i), visit.sequenceVisits.get(j))
                            + Dao.getInstance().getDistKm(visit.sequenceVisits.get(i+1), visit.sequenceVisits.get(j+1))
                            - dist(i,i+1) - dist(j,j+1);
                    if (minchange > change) {
                        minchange = change;
                        mini = i; minj = j;
                    } } }
            // apply mini/minj move
        } while (minchange < 0);
    }*/
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

    public int inVehicleDelay(){
        return this.getNodeDp().getDelay() - this.getNodePk().getDelay();
    }

    public VisitObj getCurrentVisit() {
        return currentVisit;
    }

    public Vehicle getCurrentVehicle() {
        return currentVisit != null? currentVisit.getVehicle(): null;
    }

    public void setCurrentVisit(VisitObj currentVisit) {
        this.currentVisit = currentVisit;
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
     * Try to insert the user in a list of vehicles, and return the best insertion.
     */
    public VisitObj getBestVisitByInsertion(Set<Vehicle> listVehicles, int currentTime, boolean stopAtFirstBest) {

        VisitObj bestVisit = null;

        // Try to insert user in each vehicle
        for (Vehicle v : listVehicles) {
            VisitObj candidateVisit = v.getValidVisitForUser(this);

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
     * @return Best visit, or null
     */
//    public Visit getBestVisitByInsertion2(List<Vehicle> listVehicles, int currentTime, boolean stopAtFirstBest) {
//
//        Visit bestVisit = null;
//
//        // Try to insert user in each vehicle
//        for (Vehicle v : listVehicles) {
//
//            // Rebalancing vehicles cannot service users
//            //if(v.isRebalancing()){
//            //   continue;
//            //}
//
//                /*todo - CHECK IN PARALLEL
//                if (checkInParallel) {
//
//                    // Sequence with user to be added in vehicle
//                    Set<User> auxUserSequence = new HashSet<>();
//                    auxUserSequence.add(u);
//
//                    candidateVisit = Method.getBestInsertionParallel(auxUserSequence, v, 2, true, maxPermutationsFCFS);
//
//                */
//
//            //######################################################################################################
//            Visit candidateVisit = v.getVisitWithInsertedUser(this, currentTime);
//            //Visit candidateVisit = v.getBestInsertionOld(u, currentTime);
//            //######################################################################################################
//            //if (candidateVisit != null)
//            //    visitList.add(candidateVisit);
//
//            // Update best visit if delay of candidate visit is shorter
//            if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {
//
//                // Updating visit
//                bestVisit = candidateVisit;
//
//                // Stop at the first improvement
//                if (stopAtFirstBest) {
//                    break;
//                }
//            }
//        }
//        return bestVisit;
//    }
    public void printDetailed() {
        Logging.logger.info("{}", String.format("%s(%s) - %s[%d](%d << %d - %d << %d) -> %s[%d](%d << %d - %d << %d) - Distance(s): %d - Distance(km): %f -  #Passengers: %d",
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

    /**
     * Indicates whether a user was serviced by a hired vehicle.
     */
    public void computePickupByFreelanceVehicle() {
        this.servedBy = User.SERVICED_BY_HIRED_VEHICLE;
    }

    /**
     * Indicates whether a user was serviced by a hired vehicle.
     */
    public void computePickupByDedicatedVehicle() {
        this.servedBy = User.SERVICED_BY_DEDICATED_VEHICLE;
    }

    /**
     * Indicates whether a user was serviced by a hired vehicle.
     */
    public void computeRejection(int currentTime) {
        this.servedBy = User.NOT_SERVICED;
        this.dropoutTime = currentTime;
    }

    public int getServedBy() {
        return this.servedBy;
    }

    public int getServiceLevelTierBasedOn(double pickupDelay) {
        if (pickupDelay <= this.qos.pkDelayTarget) {
            return Qos.SERVICE_LEVEL_1;
        }
        return Qos.SERVICE_LEVEL_2;
    }

    public boolean isDelayFirstTier(double pickupDelay) {
        return pickupDelay <= this.qos.pkDelayTarget;
    }

    public boolean isFirstTier() {
        return this.nodePk.getDelaySoFar() <= this.qos.pkDelayTarget;
    }

    public double pickupDelaySoFar(){
        return this.nodePk.getDelaySoFar();
    }

    public int getQoSCode() {
        return this.qos.code;
    }

    public boolean isPreviouslyAssigned() {
        return this.nodePk.getArrivalSoFar() != null;
    }

    public String getCurrentAssigmentInfo() {
        return String.format(
                "+++ %s = %s - %s tier, delay (so far) = %d, delay = %d",
                this.qos.id,
                this,
                this.isFirstTier() ? "1st" : "2nd",
                this.getNodePk().getDelaySoFar(),
                this.getNodePk().getDelay());
    }

    public int getDropoutTime() {
        return this.dropoutTime;
    }

    public boolean hasSameOriginOfUser(User u){
        return this.nodePk.getNetworkId() == u.nodePk.getNetworkId();
    }

    public boolean hasSameDestinationOfUser(User u){
        return this.nodeDp.getNetworkId() == u.nodeDp.getNetworkId();
    }
}