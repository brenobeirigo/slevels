package model;

import dao.Dao;
import model.node.*;

import java.util.*;

import static config.Config.*;

public class Vehicle implements Comparable<Vehicle> {

    /* Result from leg status */
    public static final int INVALID_LOAD = 1;
    public static final int INVALID_ARRIVAL = 2;
    public static final int INVALID_PATH = 3;
    public static final int VALID_LEG = 0;

    /* Class attributes */
    public static int count = Node.MAX_NUMBER_NODES * 2; // Starting index of vehicles
    public static LinkedList<Node> setOfHotPoints = new LinkedList<>(); // Best points to rebalance

    /* Vehicle features */
    private int id; // Vehicle id (starting from count)
    private Node origin; // Origin node
    private int capacity; // vehicle capacity (number of seats)

    /* Vehicle status */
    private Integer load; // Current vehicle load
    private Set<User> users; // Passengers POTENTIALLY being serviced
    private Set<User> enroute; // Passengers who MUST be serviced, since pickup node has been visited
    private Visit visit; // Passengers, Node sequence, etc.
    private Node currentNode; // Node where vehicle is, or from where vehicle left
    private int departureCurrent; // Time vehicle leaves current node
    private boolean rebalancing; // Is this vehicle currently rebalancing?
    private boolean hired; // Was this vehicle hired on the fly?
    private int roundsIdle; // How many rounds this vehicle is idle?

    /* Historical data */
    private List<Node> journey; // List of nodes visited by vehicle
    private List<User> servicedUsers; // List of users serviced by vehicle


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Constructors /////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Vehicle(int capacity) {
        ++count;
        this.id = count;
        this.capacity = capacity;
        this.enroute = new HashSet<>();
        this.servicedUsers = new ArrayList<>();
        this.users = new HashSet<>();
        this.journey = new ArrayList<>();
        this.load = 0;
        this.hired = false;
        this.rebalancing = false;
    }

    public Vehicle(int capacity, int id_network) {

        // Initialize vehicle
        this(capacity);

        // Vehicle current node is its origin
        this.currentNode = this.origin = new NodeOrigin(count, id_network, 0);

        // Vehicle journey (i.e., list of nodes) always start with origin
        this.journey.add(this.origin);
    }

    public Vehicle(int capacity, int id_network, double lat, double lon) {

        // Initialize vehicle
        this(capacity);

        // Vehicle current node is its origin
        this.currentNode = this.origin = new NodeOrigin(count, id_network, lat, lon, 0);

        // Vehicle journey (i.e., list of nodes) always start with origin
        this.journey.add(this.origin);
    }

    public Vehicle(int capacity, int id_network, boolean hired) {
        this(capacity, id_network);
        this.hired = hired;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void reset() {
        count = Node.MAX_NUMBER_NODES * 2;
    }

    public boolean isReadyToRebalance(int maxRoundsIdle) {
        if (this.roundsIdle >= maxRoundsIdle) {
            return true;
        }
        return false;
    }

    public boolean canDiscard(int maxRoundsIdle) {
        if (this.hired && this.roundsIdle >= maxRoundsIdle) {
            return true;
        }
        return false;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Rebalancing //////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Get set of users serviced until current time.
     * Loop visit sequence and check if:
     * 1 - node arrival is lower than pk time, AND
     * 2 - node is DP
     *
     * If so, than costumer was serviced.
     *
     * @param currentTime current time
     * @return Set of users serviced until current time
     */
    public Set<User> getServicedUsersUntil(int currentTime) {

        // If vehicle has no customers to service
        if (visit == null ||
                visit.getSequenceVisits() == null ||
                visit.getSequenceVisits().isEmpty()) {

            // Vehicle is not servicing customers
            setRoundsIdle(this.roundsIdle + 1);

            return null;
        }

        // Vehicle is either rebalancing or servicing customers
        //setRoundsIdle(0);

        // Serviced users in current execution round [last current time, current time]
        Set<User> serviced = new HashSet<>();

        int nRemoved = 0;

        for (Node nextNode : visit.getSequenceVisits()) {

            // Get arrival time at first node of visit sequenceVisits
            int arrivalNext = this.departureCurrent + Dao.getInstance().getDistSec(currentNode, nextNode);

            // If current time is lower than arrival at next node, the node was not visited yet
            if (arrivalNext > currentTime) {
                break;
            }

            // Node will be removed from sequence
            nRemoved++;

            // Set arrival time for last node
            nextNode.setArrival(arrivalNext);

            // Update current load in vehicle
            this.load += nextNode.getLoad();

            // Update data of current node
            currentNode = nextNode;
            this.journey.add(currentNode);
            this.departureCurrent = arrivalNext;

            // User data of current node
            int tripId = currentNode.getTripId();
            User currentUser = User.mapOfUsers.get(tripId);

            // If DP node is visited, it means request is finished
            if (currentNode instanceof NodeDP) {

                /*####################### Update list of serviced users #############################################*/
                // Eliminate serviced user from visit
                this.visit.getSetUsers().remove(currentUser);

                // Add serviced user to vehicle
                this.servicedUsers.add(currentUser);

                // Update serviced users
                serviced.add(currentUser);

                /*###################################################################################################*/

                // Set arrival time at DP node for request
                User.status[tripId][1] = arrivalNext;

                // Save DP delay
                User.status[tripId][3] = currentNode.getDelay();

                // Node visited is removed from vehicle
                this.users.remove(currentUser);

                // User is locked in vehicle (cannot change to other)
                this.enroute.remove(currentUser);

                // TODO improve representation
                // Locked node can be removed

                // Save total ride time in vehicle
                int rideTime = currentUser.getNodeDp().getArrival() - currentUser.getNodePk().getArrival();

                // Save ride trip ride time
                currentUser.setRideTime(rideTime);

            } else if (currentNode instanceof NodePK) {

                // Set arrival time at PK node for request
                User.status[tripId][0] = arrivalNext;
                currentUser.getNodePk().setArrival(arrivalNext);

                // Save PK delay
                User.status[tripId][2] = currentNode.getDelay();

                // User is locked in vehicle (cannot change to other)
                this.enroute.add(currentUser);

                // TODO: improve this representation
                // This nodes must be visited by vehicle
                //this.enrouteNode.add(User.mapOfUsers.get(tripId).getNodeDp());
            }
        }

        // Remove first "nRemoved" elements
        for (int j = 0; j < nRemoved; j++) {
            visit.getSequenceVisits().removeFirst();
        }

        // If all nodes were visited
        if (this.users.isEmpty()) {

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.currentNode = new NodeStop(this.currentNode, this.id);

            // Update vehicle current time
            this.departureCurrent = currentTime;

            // Model.Vehicle has no routing plan
            this.setVisit(null);

            // Vehicle is not servicing customers
            setRoundsIdle(this.roundsIdle + 1);

        } else {
            // Update visit current time
            this.visit.setDepartureVehicleCurrent(departureCurrent);
        }

        // Serviced users in an execution round
        return serviced;
    }

    /**
     * Drive vehicle to node "hotPoint"
     *
     * @param hotPoint
     */
    public void rebalanceTo(Node hotPoint) {

        // Create a target node for rebalancing
        NodeTargetRebalancing target = new NodeTargetRebalancing(hotPoint);

        // Visit is comprised of single node
        this.visit = new VisitRelocation(target, this);

        // Vehicle is rebalancing
        this.rebalancing = true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Insertion algorithm //////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * If vehicle is rebalancing, check if target node was reached and end process if so.
     *
     * @param currentTime current execution time of the simulation
     */
    public void updateRebalancing(int currentTime) {

        // If rebalancing is finished (vehicle arrived at target)
        if (isRebalancing() && currentTime >= this.visit.getTargetArrival()) {

            // Vehicle is no longer rebalancing
            this.setRebalancing(false);

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.setCurrentNode(new NodeStop(this.visit.getTargetNode(), this.getId()));

            // Update departure time of vehicle current node
            this.setDepartureCurrent(Math.max(currentTime, this.getCurrentNode().getArrival()));

            // Rebalancing routing plan is discarded
            this.setVisit(null);

            // Vehicle has been recently rebalanced, worth keeping in the fleet
            //setRoundsIdle(0);
        }
    }

    /**
     * Check if there is a valid trip between "fromNode" and "toNode" occurring in vehicle "v".
     * If true, update trip intermediate status to reflect the addition of leg checked
     *
     * @param fromNode   Origin node
     * @param toNode     Destination node
     * @param tripStatus Precedent status to update
     * @return true, if there is a valid trip, and false, otherwise.
     */
    public int getLegStatus(Node fromNode, Node toNode, int[] tripStatus) {

        // tripStatus[0] - arrivalFrom
        // tripStatus[1] - loadFrom
        // tripStatus[2] - totalDelay
        // tripStatus[3] - totalIdleness
        // tripStatus[4] - relative occupancy


        // Update loads (DP nodes have negative loads)
        int load = tripStatus[1] + toNode.getLoad();

        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
        if (load < 0 || load > this.getCapacity()) {
            return INVALID_LOAD;
        }

        /////////////////////////* VIABLE NEXT */////////////////////////////////////
        int distFromTo = Dao.getInstance().getDistSec(fromNode, toNode);

        // No path available
        if (distFromTo < 0) {
            return INVALID_PATH;
        }
        // Time vehicle arrives at next node (can be earlier or later)
        int arrivalTo = tripStatus[0] + distFromTo;
        /*
        System.out.println(String.format("%s -> %s: %d + %d = %d (%d, %d)",
                fromNode,
                toNode,
                tripStatus[0],
                distFromTo,
                arrivalTo,
                fromNode.getEarliest(),
                toNode.getLatest()));
        */
        // Arrival cannot be later than latest time in node
        if (arrivalTo > toNode.getLatest()) {
            return INVALID_ARRIVAL;
        }

        //int occupationTime = (distFromTo==0?0:1)*(int)((1000000 * ((double)tripStatus[1]/v.getCapacity()/distFromTo)));
        int occupationTime = (int) (100 * ((double) tripStatus[1] / this.getCapacity()));

        //System.out.println(fromNode + "->" +toNode +": "+ (occupationTime)/100);
        //System.out.println(fromNode + "->" +toNode +": "+ (1000000 * (double)tripStatus[1]) + " / " + v.getCapacity() +" / " + distFromTo + " = "+ occupationTime);
        //System.out.println(fromNode + "->" +toNode +": "+ (100 * (double)tripStatus[1]) + " / " + v.getCapacity() + " = "+ occupationTime);

        // Update arrival time at next node to >= earliest time of next node
        tripStatus[0] = Math.max(arrivalTo, toNode.getEarliest());

        // Update load
        tripStatus[1] = load;

        // Delay in seconds
        // If negative: Idle time, i.e., vehicle arrives earlier and wait until earliest time
        // If positive: arrival_next_node = arrivalTo, i.e., vehicle arrives after earliest time
        int delay = arrivalTo - toNode.getEarliest();

        // Delay and idleness
        tripStatus[2] += Math.max(delay, 0);
        tripStatus[3] += Math.min(delay, 0);

        // Update occupation time
        tripStatus[4] += occupationTime;

        /*TODO: previous_arrival: verify if arrival is better than previous arrival saved for node*/
        // In this sequence, one of the customers was already scheduled earlier.
        // The new journey must decrease waiting time of all passengers.

        //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrivalTo){
        //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
        //    return delays;

        ///////////////////////////////////////////////////////////////////////////////
        // Update sequences
        return VALID_LEG;
    }

    /**
     * Try to create a visit out of a single user (called when vehicle is empty)
     *
     * @param insertedUser
     * @param currentTime  Simulation current time
     * @return Visit containing pk and dp nodes of user "insertedUser", or null if visit can't be created
     */
    public Visit getFirstVisit(User insertedUser, int currentTime) {

        // tripStatus[0] - arrivalFrom
        // tripStatus[1] - loadFrom
        // tripStatus[2] - totalDelay
        // tripStatus[3] - totalIdleness
        // tripStatus[4] - relative occupancy

        int[] tripStatus = new int[]{currentTime, 0, 0, 0, 0};

        // From vehicle current node to user pk
        if (!this.isValidLeg(this.getCurrentNode(), insertedUser.getNodePk(), tripStatus))
            return null;

        // From user pk to user dp
        if (!this.isValidLeg(insertedUser.getNodePk(), insertedUser.getNodeDp(), tripStatus))
            return null;

        // Create linked list (fast adds and removals)
        LinkedList<Node> sequence = new LinkedList<>();
        sequence.add(insertedUser.getNodePk());
        sequence.add(insertedUser.getNodeDp());

        double occupationLeg = (double) tripStatus[4] / (sequence.size() - 1);

        Visit first = new Visit(sequence,
                tripStatus[2],
                tripStatus[3],
                occupationLeg,
                currentTime);

        // Assign vehicle to best
        first.setVehicle(this);

        // Assign user to visit
        Set<User> candidateRequests = new HashSet<>();
        candidateRequests.add(insertedUser);
        first.setSetUsers(candidateRequests);

        return first;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Gets & Sets //////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get best insertion of candidate user in vehicle at current time.
     *
     * @param candidateUser
     * @param currentTime
     * @return Best visit or null
     */
    public Visit getBestInsertionNew(User candidateUser, int currentTime) {

        // First best including new users is initiated blank
        Visit bestVisit = new Visit();

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Vehicle has NO visit ////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        if (this.getVisit() == null ||
                this.getVisit().getSequenceVisits() == null ||
                this.getVisit().getSequenceVisits().isEmpty()) {

            // Try to create a one user visit
            return getFirstVisit(candidateUser, currentTime);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Vehicle has a visit /////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////


        // Copy elements in vehicle visit to candidate sequence candidate sequence that will be formed
        List<Node> visitsVehicle = this.getVisit().getSequenceVisits();


        //------------------------------------------------------------------------------------------------------------//
        //------ Loop pk positions -----------------------------------------------------------------------------------//
        //------------------------------------------------------------------------------------------------------------//

        pkcontinue:
        for (int pkPos = 0; pkPos <= visitsVehicle.size(); pkPos++) {

            int departureTimeFromVehicle = this.getDepartureCurrent();

            // Check if first leg will be changed. If so, departure time from vehicle is current simulation time
            if (this.getVisit() == null || pkPos == 0) {
                departureTimeFromVehicle = currentTime;

                /*
                System.out.println(String.format("Shortest path between %s(%d) and %s(%d): %s",
                        this.getCurrentNode(),
                        this.getCurrentNode().getNetworkId(),
                        visitsVehicle.get(pkPos),
                        visitsVehicle.get(pkPos).getNetworkId(),
                        Dao.getInstance().getShortestPathBetween(
                                this.getCurrentNode().getNetworkId(),
                                visitsVehicle.get(pkPos).getNetworkId())));
                */
                //TODO How can we get the intermediate node (if vehicle had a visit, it was enroute)
            }

            // Status throughout sequence (arrival, load, delay, idle)
            int[] tripStatusPK = new int[]{departureTimeFromVehicle,
                    this.getCurrentNode().getLoad(),
                    0, 0, 0};

            // Current node is vehicle
            Node currentPK = this.getCurrentNode();

            // #### Before PK ##########################################################################################
            for (int i = 0; i < pkPos; i++) {

                // Get next in sequence
                Node next = visitsVehicle.get(i);

                // Check if it is possible to go from current to next
                if (this.getLegStatus(currentPK, next, tripStatusPK) != VALID_LEG) {
                    continue pkcontinue;
                }

                // Update current
                currentPK = next;
            }

            // #### PK #####################################################################################################
            if (this.getLegStatus(currentPK, candidateUser.getNodePk(), tripStatusPK) != VALID_LEG) {
                continue pkcontinue;
            }

            // Update current
            currentPK = candidateUser.getNodePk();

            dpcontinue:
            for (int dpPos = pkPos; dpPos <= visitsVehicle.size(); dpPos++) {

                // Go back to last configuration
                int[] tripStatus = tripStatusPK.clone();
                Node current = currentPK;

                // #### Between PK and DP ######################################################################################
                for (int i = pkPos; i < dpPos; i++) {

                    // Get next in sequence
                    Node next = visitsVehicle.get(i);

                    // Check if it is possible to go from current to next
                    int legStatus = this.getLegStatus(current, next, tripStatus);
                    if (legStatus != VALID_LEG) {

                        continue dpcontinue;
                    }

                    // Update current
                    current = next;
                }

                // #### DP #####################################################################################################
                int legStatus = this.getLegStatus(current, candidateUser.getNodeDp(), tripStatus);
                if (legStatus != VALID_LEG) {


                    continue dpcontinue;
                }

                // Update current
                current = candidateUser.getNodeDp();

                // #### After DP ###############################################################################################
                for (int i = dpPos; i < visitsVehicle.size(); i++) {

                    // Get next in sequence
                    Node next = visitsVehicle.get(i);

                    // Check if it is possible to go from current to next
                    legStatus = this.getLegStatus(current, next, tripStatus);

                    if (legStatus != VALID_LEG) {


                        continue dpcontinue;
                    }

                    // Update current
                    current = next;

                }

                // Create linked list (fast adds and removals)
                LinkedList<Node> newSequence = new LinkedList<>(visitsVehicle);
                newSequence.add(dpPos, candidateUser.getNodeDp());
                newSequence.add(pkPos, candidateUser.getNodePk());

                double occupationLeg = (double) tripStatus[4] / (newSequence.size() - 1);

                Visit candidateVisit = new Visit(
                        newSequence,
                        tripStatus[2],
                        tripStatus[3],
                        occupationLeg,
                        departureTimeFromVehicle);


                //System.out.println(String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateUser, visitSequence, candidateVisit));

                // Update best visit (Compare delay)
                if (bestVisit.compareTo(candidateVisit) > 0) {
                    bestVisit = candidateVisit;
                }
            }
        }

        // If best visit was not found
        if (bestVisit.getSequenceVisits() == null) {
            return null;
        }

        // Assign vehicle to best
        bestVisit.setVehicle(this);

        // Update user set of best visit
        Set<User> candidateRequests = new HashSet<>(this.getUsers());
        candidateRequests.add(candidateUser);
        bestVisit.setSetUsers(candidateRequests);

        return bestVisit;
    }

    public int getRoundsIdle() {
        return roundsIdle;
    }

    public void setRoundsIdle(int roundsIdle) {
        this.roundsIdle = roundsIdle;
    }

    public boolean isRebalancing() {
        return rebalancing;
    }

    public void setRebalancing(boolean rebalancing) {
        this.rebalancing = rebalancing;
    }

    public int getDepartureCurrent() {
        return departureCurrent;
    }

    public void setDepartureCurrent(int departureCurrent) {
        this.departureCurrent = departureCurrent;
    }

    public Node getOrigin() {
        return origin;
    }

    public int getId() {
        return id;
    }

    public int getLoad() {
        return load;
    }

    public Visit getVisit() {
        return visit;
    }

    public void setVisit(Visit visit) {
        this.visit = visit;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Node currentNode) {
        this.currentNode = currentNode;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    public int getCapacity() {
        return capacity;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Logging, info ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public String getStats() {

        // Initial time at origin
        int startTime = origin.getArrival();

        // Last arrival
        int endTime = journey.get(journey.size() - 1).getArrival();

        // Total operating time
        int operatingTW = endTime - startTime;

        // Delays
        int delayPK = 0, delayDP = 0;

        for (User u : servicedUsers) {
            delayPK += u.getNodePk().getDelay();
            delayDP += u.getNodeDp().getDelay();
        }

        // Operating time
        int operatingTime = 0;
        for (int i = 0; i < journey.size() - 1; i++) {
            int fromId = journey.get(i).getNetworkId();
            int toId = journey.get(i + 1).getNetworkId();
            int tripDuration = Dao.getInstance().getDistSec(fromId, toId);
            operatingTime += tripDuration;
        }

        // Set up total waiting time
        int totalWaiting = operatingTW - operatingTime;

        return String.format("###### %4s [%4s,%4s] => %4s (work) + %4s (wait) = %4s (%.2f%%) #### PK:%6s  DP:%6s",
                String.valueOf(this),
                String.valueOf(startTime),
                String.valueOf(endTime),
                String.valueOf(operatingTime),
                String.valueOf(totalWaiting),
                String.valueOf(operatingTW),
                (double) operatingTime * 100 / operatingTW,
                String.valueOf(delayPK),
                String.valueOf(delayDP));
    }

    @Override
    public String toString() {
        return String.format("%6s", "V" + String.valueOf(id - Node.MAX_NUMBER_NODES * 2));
    }

    public String getJourneyInfo() {

        StringBuilder str = new StringBuilder();

        str.append("\n########################################################################################");
        str.append("\n" + getStats());
        str.append("\n########################################################################################");

        for (int i = 0; i < journey.size() - 1; i++) {
            int fromId = journey.get(i).getNetworkId();
            int toId = journey.get(i + 1).getNetworkId();
            int dist = Dao.getInstance().getDistSec(fromId, toId);
            int waiting = journey.get(i + 1).getArrival() - dist - journey.get(i).getArrival();
            str.append("\n" + journey.get(i).getInfo());
            str.append(String.format("\nTravel time: %7s", String.valueOf(dist)));
            str.append(String.format("\n    Waiting: %7s", String.valueOf(waiting)));
        }

        // Print last node
        str.append("\n" + journey.get(journey.size() - 1).getInfo());

        return str.toString();
    }

    public String getInfo() {

        return String.format("%s(%2s/%d) - %s(%s) - Users: %30s -> Journey: %s - Attended: %s - %s",
                this,
                (!users.isEmpty() ? String.valueOf(load) : "-"),
                capacity,
                currentNode,
                currentNode.getArrival(),
                users,
                (this.visit != null ? visit : "---"),
                servicedUsers,
                ((this.rebalancing==true)? "(Rebalancing)" : ""));
    }


    /**
     * Sort vehicles according to load (lower first).
     *
     * @param that Vehicle
     * @return -1 (BEFORE), 0 (EQUAL), 1 (AFTER)
     */
    @Override
    public int compareTo(Vehicle that) {
        if (this.load < that.load) return BEFORE;
        if (this.load > that.load) return AFTER;
        return EQUAL;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// OLD METHOD ///////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** Get best insertion of candidate user in vehicle at current time.
     * @param candidateUser
     * @param currentTime
     * @return Best visit or null
     */
    public Visit getBestInsertion(User candidateUser, int currentTime) {

        // First best including new users is initiated blank
        Visit bestVisit = new Visit();

        List<Node> visitSequence;

        // If vehicle has NO visits, return visit containing single user (can be null)
        if (this.getVisit() == null ||
                this.getVisit().getSequenceVisits() == null ||
                this.getVisit().getSequenceVisits().isEmpty()) {

            visitSequence = new ArrayList<>();
        } else {

            // Candidate sequence that will be formed
            visitSequence = new ArrayList<>(this.getVisit().getSequenceVisits());
        }

        //Visit updated with new pk and dp positions (avoid creating new visit at each insertion)
        //Visit candidateVisit = new Visit();

        // Loop all insertion positions
        for (int pkPos = 0; pkPos <= visitSequence.size(); pkPos++) {
            for (int dpPos = pkPos; dpPos <= visitSequence.size(); dpPos++) {

                // Try to get a visit by inserting candidate user in sequence
                Visit candidateVisit = this.getVisitByInsertPosition(candidateUser,
                        visitSequence,
                        pkPos,
                        dpPos,
                        currentTime);

                //System.out.println(String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateUser, visitSequence, candidateVisit));

                // Update best visit (Compare delay)
                if (bestVisit.compareTo(candidateVisit) > 0) {
                    bestVisit = candidateVisit;
                }
            }
        }

        // If best visit was not found
        if (bestVisit.getSequenceVisits() == null) {
            return null;
        }

        // Assign vehicle to best
        bestVisit.setVehicle(this);

        // Update user set of best visit
        Set<User> candidateRequests = new HashSet<>(this.getUsers());
        candidateRequests.add(candidateUser);
        bestVisit.setSetUsers(candidateRequests);

        return bestVisit;
    }


    /**
     * Try to insert user pickup and drop-off point in base visit sequence occurring in vehicle.
     *
     * @param insertedUser User who is being inserted
     * @param baseSequence Visit sequence being modified
     * @param pkPos        Pickup point insert position
     * @param dpPos        Drop-off point insert position
     * @param currentTime  Time vehicle is leaving vehicle current node
     * @return Visit with user "insertedUser" inserted in base visit sequence OR null if visit not found
     */
    public Visit getVisitByInsertPosition(User insertedUser,
                                          List<Node> baseSequence,
                                          int pkPos,
                                          int dpPos,
                                          int currentTime) {

        //System.out.println("#########################################################################################");
        //System.out.println(String.format("(%d) ##### Inserting user %s in positions %d and %d of sequence %s. First node is %d", currentTime, insertedUser, pkPos, dpPos, baseSequence, this.getDepartureCurrent()));
        // Create linked list (fast adds and removals)
        //LinkedList<Node> newSequence = new LinkedList<>();

        // tripStatus[0] - Integer arrivalFrom
        // tripStatus[1] - Integer loadFrom
        // tripStatus[2] - Integer totalDelay
        // tripStatus[3] - Integer totalIdleness
        // tripStatus[4] - Load

        // Status throughout sequence (arrival, load, delay, idle)
        int[] tripStatus = new int[5];

        // Arrival time of vehicle current node (if first node in visit sequence does not change).
        // Otherwise, current time.
        //TODO vehicle is going back to departure node if better visit is found in first leg, in reality, it is in the middle of the leg
        int visitCurrentTime;

        // If first leg of vehicle trips is kept, the original visit start time can be maintained.
        // In fact, only nodes after the first leg will be modified.
        if (pkPos > 0 && this.getVisit() != null && baseSequence.get(0) == this.getVisit().getSequenceVisits().getFirst()) {
            // vehicle.getVisit()!= null &&

            //System.out.println("KEEPING FIRST LEG! " + baseSequence);

            // Departure time of vehicle original visit may remain the same since node "pkPos" can still be visited
            tripStatus[0] = visitCurrentTime = this.getDepartureCurrent();

        } else {
            //System.out.println("COMING BACK! Restarting trip!" + baseSequence);
            // If first leg will be changed, the earliest arrival time at vehicle
            // current node shall be updated to current time
            tripStatus[0] = visitCurrentTime = currentTime;
        }

        //System.out.println(pkPos + "("+ vehicle.getCurrentNode() + ") Arrival in vehicle:" + tripStatus[0] + "-- Current time:" + departureVehicleCurrent);

        // Load
        tripStatus[1] = this.getCurrentNode().getLoad();

        // Current node is vehicle
        Node current = this.getCurrentNode();

        // #### Before PK ##############################################################################################


        for (int i = 0; i < pkPos; i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (!this.isValidLeg(current, next, tripStatus)) {
                return null;
            }

            // Update current
            current = next;
            //newSequence.add(current);
        }

        // #### PK #####################################################################################################
        if (!this.isValidLeg(current, insertedUser.getNodePk(), tripStatus)) {
            return null;
        }

        // Update current
        current = insertedUser.getNodePk();
        //newSequence.add(current);

        // #### Between PK and DP ######################################################################################
        for (int i = pkPos; i < dpPos; i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (!this.isValidLeg(current, next, tripStatus)) {
                return null;
            }

            // Update current
            current = next;
            //newSequence.add(current);
        }

        // #### DP #####################################################################################################
        if (!this.isValidLeg(current, insertedUser.getNodeDp(), tripStatus))
            return null;

        // Update current
        current = insertedUser.getNodeDp();
        //newSequence.add(current);

        // #### After DP ###############################################################################################
        for (int i = dpPos; i < baseSequence.size(); i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (!this.isValidLeg(current, next, tripStatus)) {
                return null;
            }

            // Update current
            current = next;
            //newSequence.add(current);
        }

        // Create linked list (fast adds and removals)
        LinkedList<Node> newSequence2 = new LinkedList<>(baseSequence);
        newSequence2.add(dpPos, insertedUser.getNodeDp());
        newSequence2.add(pkPos, insertedUser.getNodePk());

        //System.out.println("Visit:" + visitCurrentTime + "-- Current:" + departureVehicleCurrent);
        double occupationLeg = (double) tripStatus[4] / (newSequence2.size() - 1);

        Visit visit = new Visit(newSequence2,
                tripStatus[2],
                tripStatus[3],
                occupationLeg,
                visitCurrentTime);

        //System.out.println("TRIP STATUS:"+ tripStatus[4]);
        //System.out.println(pkPos +")" + u.getNodePk() + " -- "+ dpPos +")" + u.getNodeDp());
        //System.out.println(bestSequence);
        //System.out.println(newSequence);
        //System.out.println(visit);
        //System.out.println("-------------------------- i: "+ i);

        return visit;
    }

    /**
     * Check if there is a valid trip between "fromNode" and "toNode" occurring in vehicle "v".
     * If true, update trip intermediate status to reflect the addition of leg checked
     *
     * @param fromNode   Origin node
     * @param toNode     Destination node
     * @param tripStatus Precedent status to update
     * @return true, if there is a valid trip, and false, otherwise.
     */
    public boolean isValidLeg(Node fromNode,
                              Node toNode,
                              int[] tripStatus) {

        // tripStatus[0] - arrivalFrom
        // tripStatus[1] - loadFrom
        // tripStatus[2] - totalDelay
        // tripStatus[3] - totalIdleness
        // tripStatus[4] - relative occupancy


        // Update loads (DP nodes have negative loads)
        int load = tripStatus[1] + toNode.getLoad();


        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
        if (load < 0 || load > this.getCapacity()) {
            return false;
        }

        /////////////////////////* VIABLE NEXT */////////////////////////////////////
        int distFromTo = Dao.getInstance().getDistSec(fromNode, toNode);

        // No path available
        if (distFromTo < 0) {
            return false;
        }
        // Time vehicle arrives at next node (can be earlier or later)
        int arrivalTo = tripStatus[0] + distFromTo;
        /*
        System.out.println(String.format("%s -> %s: %d + %d = %d (%d, %d)",
                fromNode,
                toNode,
                tripStatus[0],
                distFromTo,
                arrivalTo,
                fromNode.getEarliest(),
                toNode.getLatest()));
        */
        // Arrival cannot be later than latest time in node
        if (arrivalTo > toNode.getLatest()) {
            return false;
        }

        //int occupationTime = (distFromTo==0?0:1)*(int)((1000000 * ((double)tripStatus[1]/v.getCapacity()/distFromTo)));
        int occupationTime = (int) (100 * ((double) tripStatus[1] / this.getCapacity()));


        //System.out.println(fromNode + "->" +toNode +": "+ (occupationTime)/100);
        //System.out.println(fromNode + "->" +toNode +": "+ (1000000 * (double)tripStatus[1]) + " / " + v.getCapacity() +" / " + distFromTo + " = "+ occupationTime);
        //System.out.println(fromNode + "->" +toNode +": "+ (100 * (double)tripStatus[1]) + " / " + v.getCapacity() + " = "+ occupationTime);

        // Update arrival time at next node to >= earliest time of next node
        tripStatus[0] = Math.max(arrivalTo, toNode.getEarliest());

        // Update load
        tripStatus[1] = load;

        // Delay in seconds
        // If negative: Idle time, i.e., vehicle arrives earlier and wait until earliest time
        // If positive: arrival_next_node = arrivalTo, i.e., vehicle arrives after earliest time
        int delay = arrivalTo - toNode.getEarliest();

        // Delay and idleness
        tripStatus[2] += Math.max(delay, 0);
        tripStatus[3] += Math.min(delay, 0);

        // Update occupation time
        tripStatus[4] += occupationTime;

        /*TODO: previous_arrival: verify if arrival is better than previous arrival saved for node*/
        // In this sequence, one of the customers was already scheduled earlier.
        // The new journey must decrease waiting time of all passengers.

        //if( previous_arrival >= 0 && next_node in previous_arrival and previous_arrival[next_node]<arrivalTo){
        //    Model.Node.Model.Node.memo_dic[id_viable] = (None,None)
        //    return delays;

        ///////////////////////////////////////////////////////////////////////////////
        // Update sequences
        return true;
    }
}



