package model.visit;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.Logging;
import model.PathBuilder;
import model.demand.User;
import model.Vehicle;
import model.learn.StateAction;
import model.node.*;
import simulation.Environment;

import java.util.*;
import java.util.stream.Collectors;

public class Visit implements Comparable<VisitObj>, VisitObj {
    // TODO use visit count to index visits
    private static int visitCount;
    protected Integer departure; // When vehicle departs
    protected LinkedList<Node> sequenceVisits; // Sequence of pickup and delivery nodes
    protected Vehicle vehicle;
    protected double avgLoadPerVisitLeg; // Load divided by visit's legs
    protected Integer delay; // Requests' total waiting time
    protected Integer delayBonus; // How much time until deadline
    protected Integer idle; // Vehicle's total waiting time
    private double vf;

    public double getAvgLoadPerVisitLeg() {
        return avgLoadPerVisitLeg;
    }

    private int arrival; // Arrival time at the last visited node
    private Set<User> passengers; // Users picked up
    private Set<User> requests; // Users that can still be moved (not picked up)
    public Map<User, Integer> userDelayMap;

    public static Comparator<VisitObj> visitComparator = Comparator.nullsLast(Comparator.comparing(VisitObj::getDelay).thenComparing(VisitObj::getVisitSequenceSize));
    private List<Integer> draftArrivalTimes;
    private StateAction stateAction;

    public Visit(VisitObj v) {
        this.arrival = v.getArrival();
        this.requests = new HashSet<>(v.getRequests());
        this.passengers = new HashSet<>(v.getPassengers());
        this.sequenceVisits = new LinkedList<>(v.getSequenceVisits());
        this.vehicle = v.getVehicle();
        this.avgLoadPerVisitLeg = v.getAvgLoadPerVisitLeg();
        this.delay = v.getDelay();
        this.delayBonus = v.getDelayBonus();
        this.idle = v.getIdle();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.delayBonus = this.idle = idle;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle, Vehicle v) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = idle;
        this.vehicle = v;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delayBonus, int delay, int idle, Vehicle v) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.delayBonus = delayBonus;
        this.idle = idle;
        this.vehicle = v;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = 0;
        this.requests = new HashSet<>();
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, Vehicle v) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = 0;
        this.vehicle = v;
        this.requests = new HashSet<>();
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, Vehicle v, User request) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = 0;
        this.vehicle = v;
        this.requests = new HashSet<>();
        this.requests.add(request);
        this.passengers = new HashSet<>();
    }


    public Visit() {
        this.delay = Integer.MAX_VALUE;
        this.idle = Integer.MAX_VALUE;
        this.avgLoadPerVisitLeg = Double.MIN_VALUE;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(Node[] sequencePD, int delay, int delayBonus, int idleness, Vehicle vehicle, Set<User> requests) {
        this(sequencePD, delay, idleness, vehicle, requests);
        this.delayBonus = delayBonus;
    }

    public Visit(Node[] sequencePD, int delay, int idleness, Vehicle vehicle, Set<User> requests) {
        this.avgLoadPerVisitLeg = Double.MIN_VALUE;
        this.delay = delay;
        this.idle = idleness;
        this.departure = vehicle.getEarliestDeparture();
        this.requests = new HashSet<>(requests);
        this.vehicle = vehicle;

        this.sequenceVisits = new LinkedList<>(Arrays.asList(sequencePD));

        // Vehicle is already realizing another visit
        if (this.vehicle.getMiddleNode() != null) {

            // When the new sequence has parts of the old sequence, the first node may coincide. Example:
            // OLD: 0---M,1,1'
            // NEW: 0---M,1,1',2,2'
            // Therefore, middle node does not need to be inserted again.
            if (this.vehicle.getTargetNode() != sequencePD[0]) {
                // Middle node must be added to realize new trip if vehicle changes plan
                this.sequenceVisits.push(vehicle.getMiddleNode());
            }
        }
        // All passengers in vehicle belong to visit (they need to be dropped off)
        this.passengers = new HashSet<>();
        if (vehicle.isServicing()) {
            this.passengers.addAll(vehicle.getVisit().getPassengers());
        }
    }


    public static void reset() {
        Logging.logger.info("Resetting visit");
        Visit.visitCount = 0;
    }

    /**
     * Get a list of users and return a list of pickup and delivery ids.
     * E.g.: [u1,u2,u3,u4] => [1,1,2,2,3,3,4,4]
     *
     * @param passengers List of users
     * @return Sequence of user ids representing pickup and delivery nodes
     */
    public static List<Integer> getIdPairListFromUsers(Set<User> passengers) {

        /* Create a sequenceVisits of PK and DL points given a list of users. */
        List<Integer> sequence = new ArrayList<>();

        for (User r : passengers) {
            // Add pickup node
            sequence.add(r.getNodePk().getTripId());

            // Add drop-off node
            sequence.add(r.getNodeDp().getTripId());
        }

        return sequence;
    }
//
//    /**
//     * Check if a valid pickup and delivery sequence (i.e., fulfils precedence constraints  — PU before DO) is also
//     * valid regarding:
//     * - Load (vehicle load is lower than vehicle capacity at any leg)
//     * - TWs (arrival at nodes are within respective TWs)
//     * <p>
//     * - Warning! Make sure the sequence is valid!
//     *
//     * @param validPDSequence          Valid PD sequence
//     * @param departureTimeFromVehicle
//     * @param load
//     * @param maxCapacity
//     * @param latestArrival
//     * @return
//     */
//    public static int isValidSequenceFeasible(List<Node> validPDSequence, int departureTimeFromVehicle, int load, int maxCapacity, int latestArrival) {
//
//        // Data passed over legs
//        int[] cumulativeLegPK = new int[]{
//                departureTimeFromVehicle,
//                load,
//                0};
//
//        for (int i = 0; i < validPDSequence.size() - 1; i++) {
//
//            if (Visit.isLegInvalid(validPDSequence.get(i), validPDSequence.get(i + 1), cumulativeLegPK, maxCapacity)) {
//                return -1;
//            }
//
//            if (cumulativeLegPK[Vehicle.ARRIVAL] > latestArrival) {
//                return -1;
//            }
//
//            // TODO add service time to arrival
//        }
//        return cumulativeLegPK[Vehicle.DELAY];
//    }

    /**
     * Construct visit sequence node by node.
     * @param validPDSequence Valid pickup and delivery sequence
     * @return Last leg of visit.
     */
    public static PathBuilder getDraftVisit(Node[] validPDSequence, int currentTime) {
        //Logging.logger.info("RR"+ Arrays.toString(validPDSequence));
        PathBuilder currentPath = new PathBuilder(validPDSequence[0], currentTime);

//        assert validPDSequence[0].getLatest() >= Environment.currentTime;

        for (int i = 1; i < validPDSequence.length; i++) {
//            assert validPDSequence[i].getLatest() >= Environment.currentTime;
            //Logging.logger.info("{}", String.format("from= %s ### Arrival=%4d ### Delay=%4d\n", currentLeg.fromNode.getInfo(), currentLeg.arrivalNext, currentLeg.delay));
            if (!currentPath.updateNextNode(validPDSequence[i])) {
                return null;
            }

        }
        //Logging.logger.info("{}", String.format("%s %d %d\n", currentLeg.fromNode.getInfo(), currentLeg.arrivalNext, currentLeg.delay));
        return currentPath;
    }

//    /**
//     * Check if pickup-and-delivery sequence is valid regarding:
//     * - Node precedence (PU before DO)
//     * - Load (vehicle load is lower than vehicle capacity at any leg)
//     * - TWs (arrival at nodes are within respective TWs)
//     *
//     * @param sequence
//     * @param departureTimeFromVehicle
//     * @param load
//     * @param maxCapacity
//     * @param latestArrival
//     * @return
//     */
//    public static int isValidSequence(LinkedList<Node> sequence, int departureTimeFromVehicle, int load, int maxCapacity, int latestArrival) {
//
//        // All valid trips finish at delivery nodes
//        if (!(sequence.getLast() instanceof NodeDropoff)) {
//            return -1;
//        }
//
//        // If a pickup node is visited after its destination, trip is invalid
//        Map<Integer, Boolean> destinationAlreadyVisited = new HashMap<>();
//
//        // Data passed over legs
//        int[] cumulativeLegPK = new int[]{
//                departureTimeFromVehicle,
//                load,
//                0};
//
//
//        for (int i = 0; i < sequence.size() - 1; i++) {
//
//            // Mark trip id all destinations visited
//            if (sequence.get(i) instanceof NodeDropoff)
//                destinationAlreadyVisited.put(sequence.get(i).getTripId(), true);
//
//            // If pickup from same trip id is visited and destination have been marked, trip is invalid
//            if (sequence.get(i) instanceof NodeSource && destinationAlreadyVisited.containsKey(sequence.get(i).getTripId()))
//                return -1;
//
//            if (Visit.isLegInvalid(sequence.get(i), sequence.get(i + 1), cumulativeLegPK, maxCapacity)) {
//                return -1;
//            }
//
//            if (cumulativeLegPK[Vehicle.ARRIVAL] > latestArrival) {
//                return -1;
//            }
//        }
//        return cumulativeLegPK[Vehicle.DELAY];
//    }

//    /**
//     * Check if there is a valid trip between "fromNode" and "toNode" occurring in vehicle "v".
//     * If true, update trip intermediate status to reflect the addition of leg checked
//     *
//     * @param fromNode      Origin node
//     * @param nextNode      Destination node
//     * @param cumulativeLeg Precedent status to update
//     * @return true, if there is a valid trip, and false, otherwise.
//     */
//    public static boolean isLegInvalid(Node fromNode,
//                                       Node nextNode,
//                                       int[] cumulativeLeg,
//                                       int maxCapacity) {
//
//        // cumulativeLeg[ARRIVAL] - arrivalFrom
//        // cumulativeLeg[LOAD] - loadFrom
//        // cumulativeLeg[DELAY] - totalDelay
//
//
//        // Update loads (DP nodes have negative loads)
//        int load = cumulativeLeg[Vehicle.LOAD] + nextNode.getLoad();
//
//
//        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
//        if (load < 0 || load > maxCapacity) {
//            return true;
//        }
//
//        /////////////////////////* VIABLE NEXT */////////////////////////////////////
//        int distFromTo = Dao.getInstance().getDistSec(fromNode, nextNode);
//
//        // No path available
//        if (distFromTo < 0) {
//            return true;
//        }
//        // Time vehicle arrives at next node (can be earlier or later)
//        // If distance is zero, arrival next MUST be at least earliest time at next node
//        int arrivalNext = Math.max(cumulativeLeg[Vehicle.ARRIVAL] + distFromTo, nextNode.getEarliest());
//
////        if ((nextNode instanceof NodeSource || nextNode instanceof NodeDropoff) && arrivalNext > nextNode.getArrivalSoFar())
////            return true;
//
//        // Arrival cannot be later than latest time in node
//        if (arrivalNext > nextNode.getLatest()) {
//            return true;
//        }
//
//        int delay = arrivalNext - nextNode.getEarliest();
//
//        // Arrival cannot be later than the latest time in node
//        if (delay < 0) {
//            return true;
//        }
//
//        //Can vehicle visit next user?
//        User uFrom = User.mapOfUsers.get(fromNode.getTripId());
//
//        // From user requires private ride?
//        if (uFrom != null && fromNode instanceof NodeSource && !uFrom.isSharingAllowed()) {
//            if (fromNode.getTripId() != nextNode.getTripId()) {
//                //Logging.logger.info("{}", String.format("FR: Cannot go from %s(%s) to %s", fromNode, uFrom.getPerformanceClass(), nextNode));
//                return true;
//            }
//        }
//
//        // Next user requires private ride?
//        User uTo = User.mapOfUsers.get(nextNode.getTripId());
//        if (uTo != null && nextNode instanceof NodeDropoff && !uTo.isSharingAllowed()) {
//            if (fromNode.getTripId() != nextNode.getTripId()) {
//                //Logging.logger.info("{}", String.format("TO: Cannot go from %s(%s) to %s", fromNode, uFrom.getPerformanceClass(), nextNode));
//                return true;
//            }
//        }
//
////        // Hired vehicles cannot stay longer than contract deadline
////        if (this.isHired() && arrivalNext > this.contractDeadline) {
////            return false;
////        }
//
//
//        //ALLOW ONLY:
//        //           PKA ---> DPA
//        //           DPA ---> ANY
//        //           MI ----> PKA
//        //           ST ----> PKA
//        //           O  ----> PKA
//        //           PKA ---> DPA
//        /*
//         DENY:  PK(B) ----> DP(A)
//                PK(B) ----> DP(A)
//                PK
//
//        */
//        //
//
//        //      DPB ---> DPA
//        //
//        //if(uTo != null && !uTo.isSharingAllowed() && uFrom!=uTo && (fromNode instanceof NodeSource)){
//        //    Logging.logger.info("{}", String.format("Cannot go from %s(%s) to %s(%s)", fromNode, uFrom.getPerformanceClass(), nextNode, uTo.getPerformanceClass()));
//        //    return false;
//        //}
//
//
//        // Update arrival time at next node to >= earliest time of next node
//        cumulativeLeg[Vehicle.ARRIVAL] = arrivalNext;
//
//        // Update currentLoad
//        cumulativeLeg[Vehicle.LOAD] = load;
//
//        // Delay and idleness
//        if (nextNode instanceof NodeDropoff)
//            cumulativeLeg[Vehicle.DELAY] += delay;
//
//        return false;
//    }

    public static List<User> filterFirstTier(Set<VisitObj> visitsOK) {
        List<User> firstTierUsers = new ArrayList<>();
        for (VisitObj visit : visitsOK) {
            firstTierUsers.addAll(User.filterFirstTier(visit.getRequests()));
        }
        return firstTierUsers;
    }

    public static List<User> filterSecondTier(Set<VisitObj> visitsOK) {
        List<User> secondTierUsers = new ArrayList<>();
        for (VisitObj visit : visitsOK) {
            secondTierUsers.addAll(User.filterSecondTier(visit.getRequests()));
        }
        return secondTierUsers;
    }


    public Visit getVisit() {
        return this;
    }

    public Integer getDeparture() {
        return this.departure;
    }

    public int getArrival() {
        return arrival;
    }

    public Set<User> getPassengers() {
        return passengers;
    }

    public void setPassengers(Set<User> passengers) {
        this.passengers = passengers;
    }

    public LinkedList<Node> getSequenceVisits() {
        return sequenceVisits;
    }

    public void setSequenceVisits(LinkedList<Node> sequenceVisits) {
        this.sequenceVisits = sequenceVisits;
    }

    public Integer getDelay() {
        return delay;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle v) {
        this.vehicle = v;
    }

    public Integer getIdle() {
        return idle;
    }

    public void update(LinkedList<Node> sequenceVisits,
                       int delay,
                       int idle,
                       double avgOccupationLeg,
                       int departureVehicleCurrent) {

        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = idle;
        this.avgLoadPerVisitLeg = avgOccupationLeg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Visit visit = (Visit) o;
        return Objects.equal(getVehicle(), visit.getVehicle()) &&
                getSequenceVisits().containsAll(visit.getSequenceVisits());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getSequenceVisits(), getVehicle());
    }

    public int getVisitSequenceSize() {
        return this.sequenceVisits != null ? this.sequenceVisits.size() : 0;
    }

    /**
     * Check if vehicle is carrying out this visit
     *
     * @return True, if visit is assigned to its vehicle
     */
    public boolean isSetup() {
        return this.vehicle.getVisit() == this;
    }

    @Override
    public Integer getDelayBonus() {
        return this.delayBonus;
    }

    @Override
    public void setVF(double vf) {
        this.vf = vf;
    }

    @Override
    public double getVF() {
        return vf;
    }

    @Override
    public void setDeparture(int departure) {
        this.departure = departure;
    }

    @Override
    public String getType() {
        return this.getClass().getSimpleName();

    }

    public Node getLastVisitedNode() {
        return this.vehicle.getLastVisitedNode();
    }

//    public int isValidSequence() {
//
//        LinkedList<Node> allNodes = new LinkedList<>(Arrays.asList(this.vehicle.getLastVisitedNode()));
//        allNodes.addAll(this.sequenceVisits);
//
//        return isValidSequence(
//                allNodes,
//                this.vehicle.getDepartureCurrent(),
//                this.vehicle.getCurrentLoad(),
//                this.vehicle.getCapacity(),
//                this.vehicle.getContractDeadline());
//
//    }
//
//    public boolean isValid() {
//
//        if (this.vehicle.isRebalancing()) {
//
//            if (this.sequenceVisits != null) {
//                Logging.logger.info("Vehicle is rebalancing but sequence is full!" + this);
//                return false;
//            }
//
//            if (!this.passengers.isEmpty() || !this.requests.isEmpty()) {
//                Logging.logger.info("Vehicle is rebalancing but has users! " + this.getUserInfo() + " Visit: " + this);
//                return false;
//            }
//
//            if (!(this.getVehicle().getLastVisitedNode() instanceof NodeStop) && !(this.getTargetNode() instanceof NodeTargetRebalancing)) {
//                Logging.logger.info("Vehicle is rebalancing but current node and target node are invalid! " + this.getUserInfo() + " Visit: " + this);
//                return false;
//            }
//            return true;
//        }
//
//        if (this.sequenceVisits == null) {
//            Logging.logger.info("Vehicle is not rebalancing but sequence is null!" + this);
//            return true;
//        }
//
//        if (this.isValidSequence() < 0) {
//            Logging.logger.info("Invalid sequence! " + this.getUserInfo() + " - Visit: " + this + " - Target: " + this.getTargetNode() + " - Rebalancing: " + this.vehicle.isRebalancing());
//            return false;
//        }
//
//        List<Integer> passengerIds = this.passengers.stream().map(User::getId).collect(Collectors.toList());
//
//        if ((new HashSet<>(passengerIds)).size() != passengerIds.size()) {
//            Logging.logger.info("Repeated passengers! " + passengerIds);
//            return false;
//        }
//
//        List<Integer> requestIds = this.requests.stream().map(User::getId).collect(Collectors.toList());
//
//
//        if ((new HashSet<>(requestIds)).size() != requestIds.size()) {
//            Logging.logger.info("Repeated requests!");
//            return false;
//        }
//        List<Integer> pk = new ArrayList<>();
//        List<Integer> dp = new ArrayList<>();
//
//        if (this.vehicle.getLastVisitedNode() instanceof NodeDropoff)
//            dp.add(this.vehicle.getLastVisitedNode().getTripId());
//
//        if (this.vehicle.getLastVisitedNode() instanceof NodeSource)
//            pk.add(this.vehicle.getLastVisitedNode().getTripId());
//
//        if (this.sequenceVisits == null) {
//            Logging.logger.info("Sequence is null! " + this.getUserInfo() + " - Visit: " + this + " - Sequence: " + this.getSequenceVisits() + " (Rebalancing: " + this.vehicle.isRebalancing() + ")");
//            return false;
//        }
//
//        for (int i = 0; i < this.sequenceVisits.size(); i++) {
//
//            Node node = this.sequenceVisits.get(i);
//
//            // Found first node different than middle
//            if (i > 1 && node instanceof NodeWaypoint) {
//                Logging.logger.info("Node middle found in the middle of the sequence! " + this.getUserInfo() + " - Visit: " + this);
//                return false;
//            }
//            if (node instanceof NodeDropoff)
//                dp.add(node.getTripId());
//
//            if (node instanceof NodeSource)
//                pk.add(node.getTripId());
//        }
//
//        // Picked more than once
//        if (pk.size() != new HashSet<>(pk).size()) {
//            Logging.logger.info("Picked up more than once!" + this.getUserInfo() + " - PK nodes: " + pk + " - Visit: " + this);
//            return false;
//        }
//
//        // Dropped more than once
//        if (dp.size() != new HashSet<>(dp).size()) {
//            Logging.logger.info("Dropped of more than once!" + this.getUserInfo() + " - DP nodes: " + dp + " - Visit: " + this);
//            return false;
//        }
//
//        if (!dp.containsAll(passengerIds)) {
//            Logging.logger.info("There are dropoffs not included in passenger list! Users: " + this.getUserInfo() + " - Visit: " + this);
//            return false;
//        }
//        if (!pk.containsAll(requestIds)) {
//            Logging.logger.info("There are pickups not included in request list! Users: " + this.getUserInfo() + " - Visit: " + this);
//            return false;
//        }
//
//        return true;
//    }

    public void genUserPickupDelayMap(Environment environment) {
        this.userDelayMap = this.getUserPickupDelayMap(environment);
    }

    /**
     * Get the pickup delays of each user for the current visit
     *
     * @return (User, Pickup delay) pairs.
     * @param environment
     */
    public Map<User, Integer> getUserPickupDelayMap(Environment environment) {

        // Stop and rebalancing visits have no users
        if (this.getSequenceVisits() == null)
            return null;

        Map<User, Integer> userDelayPair = new HashMap<>();
        Node source = this.getVehicle().getLastVisitedNode();
        int arrival = source.getEarliestDeparture();

        for (Node next : this.getSequenceVisits()) {

            arrival += environment.getNetwork().getDistSec(source.getNetworkId(), next.getNetworkId());

            // Assumption: RTV graph is built using pickup delays (more discomfort)
            if (next instanceof NodePickup) {
                User u = User.mapOfUsers.get(next.getTripId());
                userDelayPair.put(u, arrival - next.getEarliest());
            }
            // TODO add service times
            source = next;
        }
        return userDelayPair;
    }


    /**
     * Get target node, that is, the node a vehicle is currently moving towards
     *
     * @return target node
     */
    public Node getTargetNode() {
        return this.sequenceVisits.isEmpty() ? null : this.sequenceVisits.getFirst();
    }

    public Set<User> getRequests() {
        return this.requests;
    }

    public void setRequests(Set<User> requests) {
        this.requests = requests;
    }

    public void discountDelay(int delayServicedUser) {
        this.delay -= delayServicedUser;
    }

    public Set<User> getUsers() {
        return Sets.union(passengers, requests);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// INFO ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This method is called when a visit is chosen to be the best match for a vehicle.
     * The vehicle is updated with the information of a visit.
     *
     * @return String with visit info
     */
    public String getInfo() {
        return "  - Users: " + passengers + requests +
                " - Avg. Occupation: " + avgLoadPerVisitLeg +
                " - Visits: " + sequenceVisits +
                " - Delay: " + delay +
                " - Idle: " + idle +
                " - Vehicle: " + vehicle.getInfo();
    }


    /**
     * Transform visit to json object.
     *
     * @return String with json representation of object.
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        // deserialize with gson.fromJson(this.getClass());
        return gson.toJson(this);
    }

    /**
     * Return a string featuring detailed information about users (ids, passengers, requests)
     *
     * @return String with user information
     */
    public String getUserInfo() {
        return String.format("P: %s - R: %s", this.getPassengers(), this.getRequests());
    }

    @Override
    public String toString() {
        return "VISIT BLANK";
    }


    /**
     * Summary of vehicle status.
     *
     * @return Vehicle data (id, passengers, requests, etc.)
     */
    public String getVehicleInfo() {
        return String.format(
                "%s[%2d] [P=%2d(%2d), R=%2d(%2d)]",
                this.getVehicle(),
                this.getVehicle().getCurrentLoad(),
                this.getPassengers().size(),
                getPassengersTotalLoad(),
                this.getRequests().size(),
                getRequestsTotalLoad()
        );
    }

    public int getPassengersTotalLoad() {
        return passengers.stream().mapToInt(User::getNumPassengers).sum();
    }

    public int getRequestsTotalLoad() {
        return requests.stream().mapToInt(User::getNumPassengers).sum();
    }

    //    public void calculateArrivals() {
//        this.draftArrivalTimes = Environment.getArrivalTimesFromVisit(this.vehicle, this);
//
//    }

    public List<Integer> getDraftArrivalTimes() {
        return draftArrivalTimes;
    }

    public void setDraftArrivalTimes(List<Integer> draftArrivalTimes) {
        this.draftArrivalTimes = draftArrivalTimes;
    }

    public void setVehicleState(StateAction stateAction) {
        this.stateAction = stateAction;
    }

    public StateAction getVehicleState() {
        return stateAction;
    }

    /**
     * @param v is a non-null Visit.
     */
    @Override
    public int compareTo(VisitObj v) {
        return visitComparator.compare(this, v);
    }
}

