package model;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dao.Dao;
import model.node.*;
import simulation.rebalancing.Rebalance;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class Visit implements Comparable<Visit> {
    // TODO use visit count to index visits
    private static int visitCount;
    protected LinkedList<Node> sequenceVisits; // Sequence of pickup and delivery nodes
    protected Vehicle vehicle;
    protected double avgLoadPerVisitLeg; // Load divided by visit's legs
    protected Integer delay; // Requests' total waiting time
    protected Integer idle; // Vehicle's total waiting time
    private int arrival; // Arrival time at the last visited node
    private Set<User> passengers; // Users picked up
    private Set<User> requests; // Users that can still be moved (not picked up)
    public Map<User, Integer> userDelayMap;

    public static Comparator<Visit> visitComparator = Comparator.comparing(Visit::getDelay).thenComparing(Visit::getVisitSequenceSize);
    private List<Integer> draftArrivalTimes;

    public Visit(Visit v) {
        this.arrival = v.arrival;
        this.requests = new HashSet<>(v.requests);
        this.passengers = new HashSet<>(v.passengers);
        this.sequenceVisits = new LinkedList<>(v.sequenceVisits);
        this.vehicle = v.vehicle;
        this.avgLoadPerVisitLeg = v.avgLoadPerVisitLeg;
        this.delay = v.delay;
        this.idle = v.idle;
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, int idle) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.idle = idle;
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

    public Visit(LinkedList<Node> sequenceVisits,
                 int delay,
                 int idle,
                 double avgOccupationLeg) {

        this(sequenceVisits, delay, idle);
        this.avgLoadPerVisitLeg = avgOccupationLeg;
    }

    public Visit() {
        this.delay = Integer.MAX_VALUE;
        this.idle = Integer.MAX_VALUE;
        this.avgLoadPerVisitLeg = Double.MIN_VALUE;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
    }

    public Visit(Node[] sequencePD, int delay, int idleness, Vehicle vehicle) {

        this.sequenceVisits = new LinkedList<>(Arrays.asList(sequencePD));

        if (vehicle.getMiddleNode() != null){
            if(vehicle.getVisit().getTargetNode() != sequencePD[0])
                this.sequenceVisits.push(vehicle.getMiddleNode());
        }

        this.delay = delay;
        this.idle = idleness;
        this.requests = new HashSet<>();
        this.passengers = new HashSet<>();
    }


    public static void reset() {
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

    /**
     * Check if a valid pickup and delivery sequence (i.e., fulfils precedence constraints  — PU before DO) is also
     * valid regarding:
     * - Load (vehicle load is lower than vehicle capacity at any leg)
     * - TWs (arrival at nodes are within respective TWs)
     *
     * - Warning! Make sure the sequence is valid!
     * @param validPDSequence Valid PD sequence
     * @param departureTimeFromVehicle
     * @param load
     * @param maxCapacity
     * @param latestArrival
     * @return
     */
    public static int isValidSequenceFeasible(List<Node> validPDSequence, int departureTimeFromVehicle, int load, int maxCapacity, int latestArrival) {

        // Data passed over legs
        int[] cumulativeLegPK = new int[]{
                departureTimeFromVehicle,
                load,
                0};

        for (int i = 0; i < validPDSequence.size() - 1; i++) {

            if (Visit.isLegInvalid(validPDSequence.get(i), validPDSequence.get(i + 1), cumulativeLegPK, maxCapacity)) {
                return -1;
            }

            if (cumulativeLegPK[Vehicle.ARRIVAL] > latestArrival) {
                return -1;
            }

            // TODO add service time to arrival
        }
        return cumulativeLegPK[Vehicle.DELAY];
    }

    /** Check if pickup-and-delivery sequence is valid regarding:
     * - Node precedence (PU before DO)
     * - Load (vehicle load is lower than vehicle capacity at any leg)
     * - TWs (arrival at nodes are within respective TWs)
     *
     * @param sequence
     * @param departureTimeFromVehicle
     * @param load
     * @param maxCapacity
     * @param latestArrival
     * @return
     */
    public static int isValidSequence(LinkedList<Node> sequence, int departureTimeFromVehicle, int load, int maxCapacity, int latestArrival) {

        // All valid trips finish at delivery nodes
        if (!(sequence.getLast() instanceof NodeDP)) {
            return -1;
        }

        // If a pickup node is visited after its destination, trip is invalid
        Map<Integer, Boolean> destinationAlreadyVisited = new HashMap<>();

        // Data passed over legs
        int[] cumulativeLegPK = new int[]{
                departureTimeFromVehicle,
                load,
                0};


        for (int i = 0; i < sequence.size() - 1; i++) {

            // Mark trip id all destinations visited
            if (sequence.get(i) instanceof NodeDP)
                destinationAlreadyVisited.put(sequence.get(i).getTripId(), true);

            // If pickup from same trip id is visited and destination have been marked, trip is invalid
            if (sequence.get(i) instanceof NodePK && destinationAlreadyVisited.containsKey(sequence.get(i).getTripId()))
                return -1;

            if (Visit.isLegInvalid(sequence.get(i), sequence.get(i + 1), cumulativeLegPK, maxCapacity)) {
                return -1;
            }

            if (cumulativeLegPK[Vehicle.ARRIVAL] > latestArrival) {
                return -1;
            }
        }
        return cumulativeLegPK[Vehicle.DELAY];
    }

    /**
     * Check if there is a valid trip between "fromNode" and "toNode" occurring in vehicle "v".
     * If true, update trip intermediate status to reflect the addition of leg checked
     *
     * @param fromNode      Origin node
     * @param nextNode      Destination node
     * @param cumulativeLeg Precedent status to update
     * @return true, if there is a valid trip, and false, otherwise.
     */
    public static boolean isLegInvalid(Node fromNode,
                                       Node nextNode,
                                       int[] cumulativeLeg,
                                       int maxCapacity) {

        // cumulativeLeg[ARRIVAL] - arrivalFrom
        // cumulativeLeg[LOAD] - loadFrom
        // cumulativeLeg[DELAY] - totalDelay


        // Update loads (DP nodes have negative loads)
        int load = cumulativeLeg[Vehicle.LOAD] + nextNode.getLoad();


        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
        if (load < 0 || load > maxCapacity) {
            return true;
        }

        /////////////////////////* VIABLE NEXT */////////////////////////////////////
        int distFromTo = Dao.getInstance().getDistSec(fromNode, nextNode);

        // No path available
        if (distFromTo < 0) {
            return true;
        }
        // Time vehicle arrives at next node (can be earlier or later)
        // If distance is zero, arrival next MUST be at least earliest time at next node
        int arrivalNext = Math.max(cumulativeLeg[Vehicle.ARRIVAL] + distFromTo, nextNode.getEarliest());

//        if ((nextNode instanceof NodePK || nextNode instanceof NodeDP) && arrivalNext > nextNode.getArrivalSoFar())
//            return true;

        // Arrival cannot be later than latest time in node
        if (arrivalNext > nextNode.getLatest()) {
            return true;
        }

        int delay = arrivalNext - nextNode.getEarliest();

        // Arrival cannot be later than the latest time in node
        if (delay < 0) {
            return true;
        }

        //Can vehicle visit next user?
        User uFrom = User.mapOfUsers.get(fromNode.getTripId());

        // From user requires private ride?
        if (uFrom != null && fromNode instanceof NodePK && !uFrom.isSharingAllowed()) {
            if (fromNode.getTripId() != nextNode.getTripId()) {
                //System.out.println(String.format("FR: Cannot go from %s(%s) to %s", fromNode, uFrom.getPerformanceClass(), nextNode));
                return true;
            }
        }

        // Next user requires private ride?
        User uTo = User.mapOfUsers.get(nextNode.getTripId());
        if (uTo != null && nextNode instanceof NodeDP && !uTo.isSharingAllowed()) {
            if (fromNode.getTripId() != nextNode.getTripId()) {
                //System.out.println(String.format("TO: Cannot go from %s(%s) to %s", fromNode, uFrom.getPerformanceClass(), nextNode));
                return true;
            }
        }

//        // Hired vehicles cannot stay longer than contract deadline
//        if (this.isHired() && arrivalNext > this.contractDeadline) {
//            return false;
//        }


        //ALLOW ONLY:
        //           PKA ---> DPA
        //           DPA ---> ANY
        //           MI ----> PKA
        //           ST ----> PKA
        //           O  ----> PKA
        //           PKA ---> DPA
        /*
         DENY:  PK(B) ----> DP(A)
                PK(B) ----> DP(A)
                PK

        */
        //

        //      DPB ---> DPA
        //
        //if(uTo != null && !uTo.isSharingAllowed() && uFrom!=uTo && (fromNode instanceof NodePK)){
        //    System.out.println(String.format("Cannot go from %s(%s) to %s(%s)", fromNode, uFrom.getPerformanceClass(), nextNode, uTo.getPerformanceClass()));
        //    return false;
        //}


        // Update arrival time at next node to >= earliest time of next node
        cumulativeLeg[Vehicle.ARRIVAL] = arrivalNext;

        // Update currentLoad
        cumulativeLeg[Vehicle.LOAD] = load;

        // Delay and idleness
        if (nextNode instanceof NodeDP)
            cumulativeLeg[Vehicle.DELAY] += delay;

        return false;
    }

    public static List<User> filterFirstTier(Set<Visit> visitsOK) {
        List<User> firstTierUsers = new ArrayList<>();
        for (Visit visit : visitsOK) {
            firstTierUsers.addAll(User.filterFirstTier(visit.getRequests()));
        }
        return firstTierUsers;
    }

    public static List<User> filterSecondTier(Set<Visit> visitsOK) {
        List<User> secondTierUsers = new ArrayList<>();
        for (Visit visit : visitsOK) {
            secondTierUsers.addAll(User.filterSecondTier(visit.getRequests()));
        }
        return secondTierUsers;
    }

    public static void realize(Visit visit, Rebalance rebalanceUtil, int timeWindow) {

        // Does nothing if same visit chosen (e.g., continue rebalancing)
        if (visit.getVehicle().getVisit() == visit)
            return;

        // Dummy visit for parked vehicle does not alter setup
        if (visit instanceof VisitStop) {
            return;
        }

        // Relocation visit -> Vehicle drop scheduled requests and stop at the closest node
        if (visit instanceof VisitRelocation) {
            visit.getVehicle().rebalanceToClosestNode();
            return;
        }

        // If vehicle was rebalancing, interrupt first
        if (visit.getVehicle().isRebalancing()) {
            rebalanceUtil.interruptRebalancing(visit, timeWindow);
        }

        // Add visit to vehicle (circular)
        visit.getVehicle().setVisit(visit);

        for (User request : visit.getRequests()) {
            request.setCurrentVisit(visit);
        }

        // Go through nodes and update arrival so far
        visit.updateArrivalSoFar();

        // Vehicle is not idle
        visit.getVehicle().setRoundsIdle(0);

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

    public int getDelay() {
        return delay;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle v) {
        this.vehicle = v;
    }

    public int getIdle() {
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

    /**
     * @param v is a non-null Visit.
     */
    @Override
    public int compareTo(Visit v) {
        return visitComparator.compare(this, v);
    }

        String vehicleData = String.format(
                "%s[%2d] [P=%2d(%2d), R=%2d(%2d)]",
                vehicle,
                this.vehicle.getCurrentLoad(),
                passengers.size(),
                passengers.stream().mapToInt(User::getNumPassengers).sum(),
                requests.size(),
                requests.stream().mapToInt(User::getNumPassengers).sum()
        );

        // When visit was not yet setup to vehicle, shows that it refers to a draft visit
        String draftVisit = this.isSetup() ? "[SETUP]" : "[DRAFT]";

        // If rebalancing, create sequence with rebalancing node
        List<Node> sequenceNodesToVisit;
        //if (this.vehicle.isRebalancing() && this.vehicle.getVisit().getSequenceVisits() == null) {
        if (this instanceof VisitRelocation) {
            sequenceNodesToVisit = new LinkedList<>();

            // Add rebalancing target to list
            sequenceNodesToVisit.add(getTargetNode());
        } else {
            sequenceNodesToVisit = this.getSequenceVisits();
        }

    /**
     * Check if vehicle is carrying out this visit
     * @return True, if visit is assigned to its vehicle
     */
    public boolean isSetup() {
        return this.vehicle.getVisit() == this;
    }

    private String legInfo(Node lastVisitedNode, int loadUntilLastVisited, int departureLastVisited, int dist) {
        return String.format(
                "%s%s[%2d] (%3s, %7s, %3s, %3s)",
                dist > 0 ? String.format("--> {%4s} -->", dist) : "",
                lastVisitedNode,
                loadUntilLastVisited,
                lastVisitedNode.getEarliest() == 0 ? "-" : departureLastVisited - lastVisitedNode.getEarliest(),
                departureLastVisited,
                lastVisitedNode.getArrivalSoFar() == Integer.MAX_VALUE ? "INF" : lastVisitedNode.getArrivalSoFar() - departureLastVisited,
                lastVisitedNode.getLatest() == Integer.MAX_VALUE ? "INF" : lastVisitedNode.getLatest() - departureLastVisited
        );
    }

    public int isValidSequence() {

        LinkedList<Node> allNodes = new LinkedList<>(Arrays.asList(this.vehicle.getLastVisitedNode()));
        allNodes.addAll(this.sequenceVisits);

        return isValidSequence(allNodes, this.vehicle.getDepartureCurrent(), this.vehicle.getCurrentLoad(), this.vehicle.getCapacity(), this.vehicle.getContractDeadline());

    }

    public boolean isValid() {

        if (this.vehicle.isRebalancing()) {

            if (this.sequenceVisits != null) {
                System.out.println("Vehicle is rebalancing but sequence is full!" + this);
                return false;
            }

            if (!this.passengers.isEmpty() || !this.requests.isEmpty()) {
                System.out.println("Vehicle is rebalancing but has users! " + this.getUserInfo() + " Visit: " + this);
                return false;
            }

            if (!(this.getVehicle().getLastVisitedNode() instanceof NodeStop) && !(this.getTargetNode() instanceof NodeTargetRebalancing)) {
                System.out.println("Vehicle is rebalancing but current node and target node are invalid! " + this.getUserInfo() + " Visit: " + this);
                return false;
            }
            return true;
        }

        if (this.sequenceVisits == null) {
            System.out.println("Vehicle is not rebalancing but sequence is null!" + this);
            return true;
        }

        if (this.isValidSequence() < 0) {
            System.out.println("Invalid sequence! " + this.getUserInfo() + " - Visit: " + this + " - Target: " + this.getTargetNode() + " - Rebalancing: " + this.vehicle.isRebalancing());
            return false;
        }

        List<Integer> passengerIds = this.passengers.stream().map(User::getId).collect(Collectors.toList());

        if ((new HashSet<>(passengerIds)).size() != passengerIds.size()) {
            System.out.println("Repeated passengers! " + passengerIds);
            return false;
        }

        List<Integer> requestIds = this.requests.stream().map(User::getId).collect(Collectors.toList());


        if ((new HashSet<>(requestIds)).size() != requestIds.size()) {
            System.out.println("Repeated requests!");
            return false;
        }
        List<Integer> pk = new ArrayList<>();
        List<Integer> dp = new ArrayList<>();

        if (this.vehicle.getLastVisitedNode() instanceof NodeDP)
            dp.add(this.vehicle.getLastVisitedNode().getTripId());

        if (this.vehicle.getLastVisitedNode() instanceof NodePK)
            pk.add(this.vehicle.getLastVisitedNode().getTripId());

        if (this.sequenceVisits == null) {
            System.out.println("Sequence is null! " + this.getUserInfo() + " - Visit: " + this + " - Sequence: " + this.getSequenceVisits() + " (Rebalancing: " + this.vehicle.isRebalancing() + ")");
            return false;
        }

        for (int i = 0; i < this.sequenceVisits.size(); i++) {

            Node node = this.sequenceVisits.get(i);

            // Found first node different than middle
            if (i > 1 && node instanceof NodeMiddle) {
                System.out.println("Node middle found in the middle of the sequence! " + this.getUserInfo() + " - Visit: " + this);
                return false;
            }
            if (node instanceof NodeDP)
                dp.add(node.getTripId());

            if (node instanceof NodePK)
                pk.add(node.getTripId());
        }

        // Picked more than once
        if (pk.size() != new HashSet<>(pk).size()) {
            System.out.println("Picked up more than once!" + this.getUserInfo() + " - PK nodes: " + pk + " - Visit: " + this);
            return false;
        }

        // Dropped more than once
        if (dp.size() != new HashSet<>(dp).size()) {
            System.out.println("Dropped of more than once!" + this.getUserInfo() + " - DP nodes: " + dp + " - Visit: " + this);
            return false;
        }

        if (!dp.containsAll(passengerIds)) {
            System.out.println("There are dropoffs not included in passenger list! Users: " + this.getUserInfo() + " - Visit: " + this);
            return false;
        }
        if (!pk.containsAll(requestIds)) {
            System.out.println("There are pickups not included in request list! Users: " + this.getUserInfo() + " - Visit: " + this);
            return false;
        }

        return true;
    }

    public String getUserInfo() {
        return String.format("P: %s - R: %s", this.getPassengers(), this.getRequests());
    }

    /**
     * When visits are setup, define for each node the expected arrival time. This can be used to invalidate routes:
     * visit is valid only when pickup times decrease.
     */
    public void updateArrivalSoFar() {

        int arrival = this.getVehicle().getLastVisitedNode().getDeparture();
        Node first = this.getVehicle().getLastVisitedNode();
        for (Node next : this.getSequenceVisits()) {
            arrival += Dao.getInstance().getDistSec(first, next);
            // TODO add service time
            next.setArrivalSoFar(arrival);
            first = next;
        }
    }

    public void setUserDelayPairs(){
        this.userDelayMap = this.getUserDelayPairs();
    }
    /**
     * Get the pickup delays of each user for the current visit
     * @return (User, Pickup delay) pairs.
     */
    public Map<User, Integer> getUserDelayPairs() {

        // Stop and rebalancing visits have no users
        if (this.getSequenceVisits() == null)
            return null;

        Map<User, Integer> userDelayPair = new HashMap<>();
        Node first = this.getVehicle().getLastVisitedNode();
        int arrival = first.getDeparture();

        for (Node next : this.getSequenceVisits()) {

            arrival += Dao.getInstance().getDistSec(first, next);

            // Assumption: RTV graph is built using pickup delays (more discomfort)
            if (next instanceof NodePK) {
                User u = User.mapOfUsers.get(next.getTripId());
                userDelayPair.put(u, arrival - next.getEarliest());
            }
            // TODO add service times
            first = next;
        }
        return userDelayPair;
    }


    /**
     * Get arrival time at first node in sequence of visits
     *
     * @return Arrival time
     */
    public int getArrivalTimeAtNext() {
        return this.vehicle.getDepartureCurrent() +
                Dao.getInstance().getDistSec(this.vehicle.getLastVisitedNode(),
                        this.sequenceVisits.getFirst());
    }

    /**
     * Get target node, that is, the node a vehicle is currently moving towards
     *
     * @return target node
     */
    public Node getTargetNode() {
        return this.sequenceVisits.getFirst();
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

    public static List<Integer> getArrivalTimesFromVisit(Vehicle vehicle, Visit visit) {
        int previousArrival = vehicle.getLastVisitedNode().getDeparture();
        Node previousNode = vehicle.getLastVisitedNode();
        List<Integer> arrivals = new ArrayList<>();
        arrivals.add(previousArrival);

        for (Node currentNode : visit.getSequenceVisits()) {
            previousArrival += Dao.getInstance().getDistSec(previousNode, currentNode);
            //TODO add service time
            arrivals.add(previousArrival);
            previousNode = currentNode;
        }
        return arrivals;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// INFO ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This method is called when a visit is chosen to be the best match for a vehicle.
     * The vehicle is updated with the information of a visit.
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
     * @return String with json representation of object.
     */
    public String toJson(){
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        // deserialize with gson.fromJson(this.getClass());
        return gson.toJson(this);
    }

    /**
     * Return a string featuring detailed information about users (ids, passengers, requests)
     * @return String with user information
     */
    public String getUserInfo() {
        return String.format("P: %s - R: %s", this.getPassengers(), this.getRequests());
    }

    @Override
    public String toString() {
        if (this.vehicle == null) {
            return "DUMMY VISIT";
        }

        String vehicleData = getVehicleInfo();

        // When visit was not yet setup to vehicle, shows that it refers to a draft visit
        String draftVisit = this.isSetup() ? "[SETUP]" : "[DRAFT]";

        // If rebalancing, create sequence with rebalancing node
        List<Node> sequenceNodesToVisit;
        //if (this.vehicle.isRebalancing() && this.vehicle.getVisit().getSequenceVisits() == null) {
        if (this instanceof VisitRelocation) {
            sequenceNodesToVisit = new LinkedList<>();

            // Add rebalancing target to list
            sequenceNodesToVisit.add(getTargetNode());
        } else {
            sequenceNodesToVisit = this.getSequenceVisits();
        }

        //List<Node> sequenceNodesToVisit = this.getSequenceVisits();

        Node lastVisitedNode = this.vehicle.getLastVisitedNode();
        int loadUntilLastVisited = this.vehicle.getCurrentLoad();
        int departureLastVisited = this.vehicle.getLastVisitedNode().getDeparture();

        //Current node info
        String lastVisitedNodeInfo = getVehicleDepartureNodeInfo(sequenceNodesToVisit);

        // Node strings
        List<String> pkDpNodeInfo = new ArrayList<>();
        for (int i = 0; i < sequenceNodesToVisit.size(); i++) {

            Node next = sequenceNodesToVisit.get(i);
            int dist = Dao.getInstance().getDistSec(lastVisitedNode, next);

            // Update data from last visited node
            lastVisitedNode = next;
            departureLastVisited = Math.max(departureLastVisited + dist, lastVisitedNode.getEarliest());
            loadUntilLastVisited += lastVisitedNode.getLoad();

            String nodeInfo = legInfo(lastVisitedNode, loadUntilLastVisited, departureLastVisited, dist);
            pkDpNodeInfo.add(nodeInfo); // config.Config.sec2TStamp(sequenceArrivals.get(i))));

        }

        pkDpNodeInfo.add(String.format("<%d, %d>", departureLastVisited, vehicle.getContractDeadline()));
        String delayInfo = String.format("(delay: %5d - occ.: %5.2f)", delay, avgLoadPerVisitLeg);
        return String.format(
                "%s %s %s %s %s --- %s",
                draftVisit,
                delayInfo,
                vehicleData,
                lastVisitedNodeInfo,
                String.join(" ", pkDpNodeInfo),
                this.getUserInfo());
    }
    private String getDistanceLegInfoFromNodes(Node from, Node to){
        String distLegInfo = from == null || to == null? "--------------" : String.format(
                "--> {%4s} -->", Dao.getInstance().getDistSec(from, to));
        return distLegInfo;
    }
    private String getVehicleDepartureNodeInfo(List<Node> sequenceNodesToVisit) {

        //
        return String.format(
                "%s (target=%10s) :::%s%s%s%s:::",
                (this.vehicle.isRebalancing() ? "--RE--" : "--ST--"),
                this.getTargetNode(),
                legInfo(
                        this.vehicle.getLastVisitedNode(),
                        this.vehicle.getCurrentLoad(),
                        this.vehicle.getLastVisitedNode().getDeparture(),
                        Integer.MIN_VALUE),
                getDistanceLegInfoFromNodes(this.vehicle.getLastVisitedNode(), this.vehicle.getMiddleNode()),
                this.vehicle.getMiddleNode() == null ? "-------" : this.vehicle.getMiddleNode(),
                getDistanceLegInfoFromNodes(this.vehicle.getMiddleNode(), sequenceNodesToVisit.get(0))
        );
    }

    /**
     * Summary of vehicle status.
     * @return Vehicle data (id, passengers, requests, etc.)
     */
    private String getVehicleInfo() {
        return String.format(
                "%s[%2d] [P=%2d(%2d), R=%2d(%2d)]",
                vehicle,
                this.vehicle.getCurrentLoad(),
                passengers.size(),
                passengers.stream().mapToInt(User::getNumPassengers).sum(),
                requests.size(),
                requests.stream().mapToInt(User::getNumPassengers).sum()
        );
    }

    /**
     * Detailed information of visit leg.
     * Example:
     *  --> { 183} -->DP2164916[ 1] (earliest=  1, departure=     453,   arrival=0, latest=419)
     * @param currentNode
     * @param loadVehicleAtCurrentNode
     * @param arrivalAtCurrentNode
     * @param distBetweenPreviousAndCurrentNodes
     * @return
     */
    private String legInfo(Node currentNode, int loadVehicleAtCurrentNode, int arrivalAtCurrentNode, int distBetweenPreviousAndCurrentNodes) {
        return String.format(
                "%s%s[%2d/%2d] (e=%7s, a=%7s, l=%7s / Δe=%3s, Δa=%3s, Δl=%3s)",
                distBetweenPreviousAndCurrentNodes == Integer.MIN_VALUE? "": String.format("--> {%4s} -->", distBetweenPreviousAndCurrentNodes),
                currentNode,
                loadVehicleAtCurrentNode,
                vehicle.getCapacity(),
                currentNode.getEarliest(),
                arrivalAtCurrentNode,
                currentNode.getLatest() == Integer.MAX_VALUE ? "INF" : currentNode.getLatest(),
                currentNode.getEarliest() == 0 ? "-" : arrivalAtCurrentNode - currentNode.getEarliest(),
                currentNode.getArrivalSoFar() == Integer.MAX_VALUE ? "INF" : currentNode.getArrivalSoFar() - arrivalAtCurrentNode,
                currentNode.getLatest() == Integer.MAX_VALUE ? "INF" : currentNode.getLatest() - arrivalAtCurrentNode
        );
    }

    public void calculateArrivals() {
        this.draftArrivalTimes = getArrivalTimesFromVisit(this.vehicle, this);

    }

    public List<Integer> getDraftArrivalTimes() {
        return draftArrivalTimes;
    }

    public void setDraftArrivalTimes(List<Integer> draftArrivalTimes) {
        this.draftArrivalTimes = draftArrivalTimes;
    }

}

