package model;

import dao.Dao;
import model.node.*;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class Visit implements Comparable<Visit> {
    private int arrival;
    protected LinkedList<Node> sequenceVisits; // Sequence of pickup and delivery nodes
    private Set<User> passengers; // Users picked up
    private Set<User> requests; // Users that can still be moved (not picked up)
    protected Vehicle vehicle;
    protected double avgOccupationLeg;

    // Delays and idle times
    protected int delay, idle;

    public Visit(Visit v) {
        this.arrival = v.arrival;
        this.requests = new HashSet<>(v.requests);
        this.passengers = new HashSet<>(v.passengers);
        this.sequenceVisits = new LinkedList<>(v.sequenceVisits);
        this.vehicle = v.vehicle;
        this.avgOccupationLeg = v.avgOccupationLeg;
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
        this.requests = new HashSet();
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, Vehicle v) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.vehicle = v;
        this.requests = new HashSet();
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits, int delay, Vehicle v, User request) {
        this.sequenceVisits = sequenceVisits;
        this.delay = delay;
        this.vehicle = v;
        this.requests = new HashSet();
        this.requests.add(request);
        this.passengers = new HashSet<>();
    }

    public Visit(LinkedList<Node> sequenceVisits,
                 int delay,
                 int idle,
                 double avgOccupationLeg) {

        this(sequenceVisits, delay, idle);
        this.avgOccupationLeg = avgOccupationLeg;
    }

    public static void reset() {
    }

    public Visit() {
        this.delay = Integer.MAX_VALUE;
        this.idle = Integer.MAX_VALUE;
        this.avgOccupationLeg = Double.MIN_VALUE;
        this.passengers = new HashSet<>();
        this.requests = new HashSet<>();
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

            // Add dropoff node
            sequence.add(r.getNodeDp().getTripId());
        }

        return sequence;
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
        this.avgOccupationLeg = avgOccupationLeg;
    }

    public void setSequenceVisits(LinkedList<Node> sequenceVisits) {
        this.sequenceVisits = sequenceVisits;
    }

    /**
     * @param that is a non-null Visit.
     */
    @Override
    public int compareTo(Visit that) {

        // Objects are equal
        //if (this == that) return EQUAL;
        if (that == null) return BEFORE;

        //"Empty" visit

        //if (this.getSequenceVisits() == null) return BEFORE;
        //if (that.getSequenceVisits() == null) return AFTER;

        // Privilege trip size
        //if (this.getSequenceVisits().size() > that.getSequenceVisits().size()) return BEFORE;
        //if (this.getSequenceVisits().size() < that.getSequenceVisits().size()) return AFTER;

        // Compare average occupation per leg
        //if (this.avgOccupationLeg > that.avgOccupationLeg) return BEFORE;
        //if (this.avgOccupationLeg < that.avgOccupationLeg) return AFTER;

        //if (this.getVehicle().getUsers().size() > this.getVehicle().getUsers().size()) return BEFORE;
        //if (this.getVehicle().getUsers().size() < this.getVehicle().getUsers().size()) return AFTER;

        //primitive numbers follow this form
        if (this.delay < that.delay) return BEFORE;
        if (this.delay > that.delay) return AFTER;

        return EQUAL;
    }

    @Override
    public String toString() {
        if (this.vehicle == null) {
            return "DUMMY VISIT";
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
        String draftVisit = this.vehicle.getVisit() == this ? "[SETUP]" : "[DRAFT]";

        // If rebalancing, create sequence with rebalancing node
        List<Node> sequenceNodesToVisit;
        if (this.vehicle.isRebalancing() && this.vehicle.getVisit().getSequenceVisits() == null) {
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
        String lastVisitedNodeInfo = String.format(
                "%s [target=%s]||| %s ::: {%4s} %s {%4s}:::",
                (this.vehicle.isRebalancing() ? "--RE--" : "--ST--"),
                this.getTargetNode(),
                legInfo(lastVisitedNode, loadUntilLastVisited, departureLastVisited, 0),
                this.vehicle.getMiddleNode() == null ? " -- " : Dao.getInstance().getDistSec(this.vehicle.getLastVisitedNode(), this.vehicle.getMiddleNode()),
                this.vehicle.getMiddleNode() == null ? "-------": this.vehicle.getMiddleNode(),
                this.vehicle.getMiddleNode() == null || sequenceNodesToVisit.isEmpty() ? " -- " : Dao.getInstance().getDistSec(this.vehicle.getMiddleNode(), sequenceNodesToVisit.get(0))
        );

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
        String delayInfo = String.format("(delay: %5d - occ.: %5.2f)", delay, avgOccupationLeg);
        return String.format(
                "%s %s %s %s %s --- %s",
                draftVisit,
                delayInfo,
                vehicleData,
                lastVisitedNodeInfo,
                String.join(" ", pkDpNodeInfo),
                this.getUserInfo());
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

        return isValidSequence(allNodes, this.vehicle.getDepartureCurrent(), this.vehicle.getCurrentLoad(), this.vehicle.getCapacity());

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


    public static int isValidSequence(LinkedList<Node> sequence, int departureTimeFromVehicle, int load, int maxCapacity) {

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
        }
        return cumulativeLegPK[Vehicle.DELAY];
    }


    public void updateArrivalSoFar() {

        int arrival = this.getVehicle().getLastVisitedNode().getDeparture();
        Node first = this.getVehicle().getLastVisitedNode();
        for (Node next : this.getSequenceVisits()) {
            arrival += Dao.getInstance().getDistSec(first, next);
            next.setArrivalSoFar(arrival);
            first = next;
        }
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

        if (nextNode instanceof NodePK && arrivalNext > nextNode.getArrivalSoFar())
            return false;

        // Arrival cannot be later than latest time in node
        if (arrivalNext > nextNode.getLatest()) {
            return true;
        }

        int delay = arrivalNext - nextNode.getEarliest();

        // Arrival cannot be later than latest time in node
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



    /*
    This method is called when a visit is chosen to be the best match for a vehicle.
    The vehicle is updated with the information of a visit.

     */

    public String getInfo() {
        return "  - Users: " + passengers + requests +
                " - Avg. Occupation: " + avgOccupationLeg +
                " - Visits: " + sequenceVisits +
                " - Delay: " + delay +
                " - Idle: " + idle +
                " - Vehicle: " + vehicle.getInfo();
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
}