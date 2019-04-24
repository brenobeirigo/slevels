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

    private int contractedDuration; // How many rounds vehicle is allowed to work?

    /* Vehicle status */
    private Integer load; // Current vehicle load
    private Set<User> users; // Passengers POTENTIALLY being serviced
    private Set<User> flexibleUsers; // Passengers POTENTIALLY being serviced
    private Set<User> enroute; // Passengers who MUST be serviced, since pickup node has been visited
    private Visit visit; // Passengers, Node sequence, etc.
    private Node currentNode; // Node where vehicle is, or from where vehicle left
    private boolean rebalancing; // Is this vehicle currently rebalancing?
    private boolean hired; // Was this vehicle hired on the fly?
    private int roundsIdle; // How many rounds this vehicle is idle?
    private Node middleNode; // Where vehicle is now?
    private int distMiddleNode;
    private int activeRounds;
    private int contractDeadline;

    public Vehicle(int capacity, int id_network, int currentTime, boolean hired, int contractDeadline) {
        this(capacity, id_network, currentTime);
        this.hired = hired;
        this.contractedDuration = contractDeadline - currentTime;
        this.contractDeadline = contractDeadline;
    }


    private double distanceTraveledRebalancing;
    private double distanceTraveledEmpty;
    private double distanceTraveledLoaded;

    /* Historical data */
    private List<Node> journey; // List of nodes visited by vehicle
    private List<User> servicedUsers; // List of users picked up (can't change vehicle after pick up)
    private boolean stoppedRebalanceToPickup;


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
        this.flexibleUsers = new HashSet();
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

    public static void reset() {
        count = Node.MAX_NUMBER_NODES * 2; // Starting index of vehicles
        Vehicle.setOfHotPoints = new LinkedList<>();
    }


    public Vehicle(int capacity, int id_network, int currentTime) {
        this(capacity, id_network);
        this.getOrigin().setArrival(currentTime);
        this.getOrigin().setDeparture(currentTime);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void computeAttractivenessLocationUser(User u) {

        Node pk = u.getNodePk();

        // This node should have more vehicles around it
        pk.increaseHotness();

        // Do not send vehicles to the same places
        if (!Node.tabu.contains(pk.getNetworkId())) {

            // Only add points not yet being addressed by other vehicles
            Vehicle.setOfHotPoints.add(pk);
        }
    }

    public void increaseActiveRounds() {
        this.activeRounds++;
    }

    /**
     * Indicate if vehicle was hired.
     *
     * @return True, if vehicle does not belong to dedicated fleet.
     */
    public boolean isHired() {
        return hired;
    }

    public int getActiveRounds() {
        return activeRounds;
    }


    public void printHiringInfo() {
        System.out.println(this + " - Deadline:" + this.contractDeadline);
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Rebalancing //////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Get set of users serviced until current time.
     * Loop visit sequence and check if:
     * 1 - node arrival is lower than pk time, AND
     * 2 - node is DP
     * <p>
     * If so, than costumer was serviced.
     *
     * @param currentTime current time
     * @return Set of users serviced until current time
     */
    public Set<User> getServicedUsersUntil(int currentTime) {

        if (this.isParked()) {
            return null;
        }

        if (this.isRebalancing()) {
            // Check if rebalancing can be finished and update vehicle and target location statuses
            updateRebalancingStatus(currentTime);
            return null;
        }

        // Serviced users in current execution round [last current time, current time]
        Set<User> serviced = new HashSet<>();

        int nRemoved = 0;

        for (Node nextNode : visit.getSequenceVisits()) {

            // Get arrival time at first node of visit sequenceVisits
            int arrivalNext = this.getCurrentNode().getDeparture() + Dao.getInstance().getDistSec(currentNode, nextNode);

            // Sometimes, distance is zero, therefore the arrival should be earliest time
            arrivalNext = Math.max(arrivalNext, nextNode.getEarliest());

            // If current time is lower than arrival at next node, the node was not visited yet
            if (arrivalNext > currentTime) {
                break;
            }

            // Node will be removed from sequence
            nRemoved++;

            if (arrivalNext < nextNode.getEarliest()) {
                System.out.println(this.getCurrentNode().getDeparture() + "####" + nextNode + " - " + nextNode.getEarliest() + " < " + arrivalNext);
            }

            double distTraveledKm = Dao.getInstance().getDistKm(this.currentNode, nextNode);

            if (this.load == 0) {
                this.distanceTraveledEmpty += distTraveledKm;
            } else {
                this.distanceTraveledLoaded += distTraveledKm;
            }
            // Update current load in vehicle
            this.load += nextNode.getLoad();

            // Is the current node a stop point? If so, vehicle was recently rebalanced
            Node target = currentNode;

            if (target instanceof NodeTargetRebalancing) {

                Node origin = this.journey.get(this.journey.size() - 2);

                double distTraveledKm2 = Dao.getInstance().getDistKm(origin, target);

                int roundsToFindUser = ((target.getDeparture() - origin.getDeparture()) / 30);

                RebalanceEpisode r = new RebalanceEpisode(
                        origin.getNetworkId(),
                        target.getNetworkId(),
                        -1,
                        -1,
                        -1,
                        distTraveledKm2,
                        roundsToFindUser);

                /*
                System.out.println(String.format("AFTER ROUNDS - %s(%s--%s) -> %s(%s--%s) :%s",
                        origin,
                        Config.sec2Datetime(origin.getArrival()),
                        Config.sec2Datetime(origin.getDeparture()),
                        target,
                        Config.sec2Datetime(target.getArrival()),
                        Config.sec2Datetime(target.getDeparture()),
                        r));
                */
            }

            // Update data of current node
            this.currentNode = nextNode;
            this.currentNode.setArrival(arrivalNext);
            this.currentNode.setDeparture(arrivalNext);
            this.journey.add(currentNode);


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

                // Save which type of vehicle picked up user
                if (this.isHired()) {
                    currentUser.computePickupByFreelanceVehicle();
                } else {
                    currentUser.computePickupByDedicatedVehicle();
                }

            } else if (currentNode instanceof NodePK) {

                // Set arrival time at PK node for request
                User.status[tripId][0] = arrivalNext;
                //currentUser.getNodePk().setArrival(arrivalNext);

                // Save PK delay
                User.status[tripId][2] = currentNode.getDelay();

                // User is locked in vehicle (cannot change to other)
                this.enroute.add(currentUser);

                this.flexibleUsers.remove(currentUser);

                this.visit.getSetFlexibleUsers().remove(currentUser);

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

            if (this.currentNode instanceof NodeTargetRebalancing) {
                System.out.println("Updating target node!" + this.currentNode);
            }

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.currentNode = new NodeStop(this.currentNode, this.id);

            // So far, current time is minimum departure time of node
            this.currentNode.setDeparture(currentTime);

            //Adding node stop to journey
            this.journey.add(this.currentNode);

            // Model.Vehicle has no routing plan
            this.setVisit(null);

        }

        // Serviced users in an execution round
        return serviced;
    }

    /**
     * If vehicle is rebalancing, check if target node was reached and end process if so.
     *
     * @param currentTime current execution time of the simulation
     */
    private void updateRebalancingStatus(int currentTime) {
        // If rebalancing is finished (vehicle arrived at target)
        if (currentTime >= this.visit.getTargetArrival()) {

            this.distanceTraveledRebalancing += Dao.getInstance().getDistKm(
                    this.currentNode,
                    this.visit.getTargetNode());

            // Vehicle is no longer rebalancing
            this.setRebalancing(false);

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.setCurrentNode(this.visit.getTargetNode());

            // Add current node to journey
            this.journey.add(this.currentNode);

            // Update departure time of vehicle current node
            this.getCurrentNode().setDeparture(currentTime);

            // Rebalancing routing plan is discarded
            this.setVisit(null);

            // Vehicle has been recently rebalanced, worth keeping in the fleet
            this.setRoundsIdle(0);

            // Node can become a rebalancing target again
            Node.tabu.remove(currentNode.getNetworkId());
        }
    }

    public Visit getVisitWithEnroute() {

        if (this.visit == null || this.visit.getSetUsers() == null) return null;

        Set<Node> deliveries = new HashSet<>();

        for (User u : this.enroute) {
            deliveries.add(u.getNodeDp());
        }

        LinkedList<Node> deliveriesOrdered = new LinkedList<>();
        for (Node n : this.visit.getSequenceVisits()) {
            if (deliveries.contains(n)) {
                deliveriesOrdered.add(n);
            }
        }


        Visit newVisit = new Visit(this.visit);

        newVisit.setSequenceVisits(deliveriesOrdered);

        System.out.println("DELIVERIES ORDERED:" + newVisit);

        return newVisit;
    }

    /**
     * Drive vehicle to node "hotPoint"
     *
     * @param targetNode
     */
    public void rebalanceTo(Node targetNode) {

        // Create a target node for rebalancing
        NodeTargetRebalancing target = new NodeTargetRebalancing(targetNode);

        // Visit is comprised of single node
        this.visit = new VisitRelocation(target, this);

        // Vehicle is rebalancing
        this.rebalancing = true;

        // Signalize there are vehicles going to the point
        Node.tabu.add(targetNode.getNetworkId());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Insertion algorithm //////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


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

        int[] tripStatus = new int[]{currentTime, 0, 0};

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

        Visit first = new Visit(sequence, tripStatus[2]);

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

    public Visit getVisitFromRebalancing(User candidateUser) {

        //CURRENT TO MIDDLE
        Node middle = this.getMiddleNode();

        if (middle == null) {
            return null;
        }

        int[] tripStatus = new int[]{
                this.getDepartureCurrent() + this.distMiddleNode,
                0,
                0};

        // From middle node to user pk
        if (!this.isValidLeg(middle, candidateUser.getNodePk(), tripStatus))
            return null;

        // From user pk to user dp
        if (!this.isValidLeg(candidateUser.getNodePk(), candidateUser.getNodeDp(), tripStatus))
            return null;

        // Create linked list (fast adds and removals)
        LinkedList<Node> sequence = new LinkedList<>();
        sequence.add(middle);
        sequence.add(candidateUser.getNodePk());
        sequence.add(candidateUser.getNodeDp());

        Visit first = new Visit(sequence,
                tripStatus[2]);

        // Assign vehicle to best
        first.setVehicle(this);

        // Assign user to visit
        Set<User> candidateRequests = new HashSet<>();
        candidateRequests.add(candidateUser);
        first.setSetUsers(candidateRequests);

        return first;

    }

    /**
     * Get best insertion of candidate user in vehicle at current time.
     *
     * @param candidateUser
     * @param currentTime
     * @return Best visit or null
     */
    public Visit getBestInsertion(User candidateUser, int currentTime) {


        if (this.isRebalancing()) {
            return getVisitFromRebalancing(candidateUser);
        }

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

            //int currentLoad = this.getCurrentNode().getLoad();

            int currentLoad = this.load;

            int departureTimeFromVehicle = this.getDepartureCurrent();

            // Status throughout sequence (arrival, load, delay, idle)
            int[] tripStatusPK = new int[]{
                    departureTimeFromVehicle,
                    currentLoad,
                    0};

            // Current node is vehicle
            Node currentPK = this.getCurrentNode();

            // Check if first leg will be changed. If so, departure time from vehicle is current simulation time
            Node middle = null;

            if (pkPos == 0) {

                //CURRENT TO MIDDLE
                middle = this.getMiddleNode();

                // Can't move to a middle node, go to next pk
                if (middle == null) {
                    continue pkcontinue;
                }

                // Update arrival in middle
                tripStatusPK[0] += this.distMiddleNode;
                currentPK = middle;


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
            }

            // #### Before PK ##########################################################################################
            for (int i = 0; i < pkPos; i++) {

                // Get next in sequence
                Node next = visitsVehicle.get(i);

                // Check if it is possible to go from current to next
                if (!this.isValidLeg(currentPK, next, tripStatusPK)) {
                    continue pkcontinue;
                }

                // Update current
                currentPK = next;
            }

            // #### PK #################################################################################################
            if (!this.isValidLeg(currentPK, candidateUser.getNodePk(), tripStatusPK)) {
                continue pkcontinue;
            }

            // Update current
            currentPK = candidateUser.getNodePk();

            dpcontinue:
            for (int dpPos = pkPos; dpPos <= visitsVehicle.size(); dpPos++) {

                // Go back to last configuration
                int[] tripStatus = tripStatusPK.clone();
                Node current = currentPK;

                // #### Between PK and DP ##############################################################################
                for (int i = pkPos; i < dpPos; i++) {

                    // Get next in sequence
                    Node next = visitsVehicle.get(i);

                    // Check if it is possible to go from current to next
                    if (!this.isValidLeg(current, next, tripStatus)) {
                        continue dpcontinue;
                    }

                    // Update current
                    current = next;
                }

                // #### DP #############################################################################################
                if (!this.isValidLeg(current, candidateUser.getNodeDp(), tripStatus)) {

                    continue dpcontinue;
                }

                // Update current
                current = candidateUser.getNodeDp();

                // #### After DP #######################################################################################
                for (int i = dpPos; i < visitsVehicle.size(); i++) {

                    // Get next in sequence
                    Node next = visitsVehicle.get(i);

                    // Check if it is possible to go from current to next
                    if (!this.isValidLeg(current, next, tripStatus)) {

                        continue dpcontinue;
                    }

                    // Update current
                    current = next;

                }

                // Create linked list (fast adds and removals)
                LinkedList<Node> newSequence = new LinkedList<>(visitsVehicle);
                newSequence.add(dpPos, candidateUser.getNodeDp());
                newSequence.add(pkPos, candidateUser.getNodePk());
                if (pkPos == 0) {
                    newSequence.add(0, middle);
                }

                Visit candidateVisit = new Visit(
                        newSequence,
                        tripStatus[2]);

                candidateVisit.setVehicle(this);


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

    public void increaseRoundsIdle() {
        this.roundsIdle = roundsIdle + 1;
    }

    public boolean isRebalancing() {
        return rebalancing;
    }

    public void setRebalancing(boolean rebalancing) {
        this.rebalancing = rebalancing;
        this.roundsIdle = roundsIdle + 1;
    }

    public int getDepartureCurrent() {
        return this.currentNode.getDeparture();
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
        this.flexibleUsers = new HashSet<>(users);
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
        // Print H if vehicle is hired and V otherwise (plus vehicle Id)
        return String.format("%6s", (this.isHired() ? "H" : "V") + (id - Node.MAX_NUMBER_NODES * 2));
    }

    public String getJourneyInfo() {

        StringBuilder str = new StringBuilder();
        StringBuilder strCoord = new StringBuilder();
        str.append("\n########################################################################################");
        str.append("\n" + getStats());
        str.append("\n########################################################################################");


        List<String> coordJourney = new ArrayList<>();

        for (int i = 0; i < journey.size() - 1; i++) {
            int fromId = journey.get(i).getNetworkId();
            //coordJourney.add(String.format("[%f, %f]", journey.get(i).getLon(),journey.get(i).getLat()));
            int toId = journey.get(i + 1).getNetworkId();
            //coordJourney.add(String.format("[%f, %f]", journey.get(i + 1).getLon(),journey.get(i + 1).getLat()));
            int dist = Dao.getInstance().getDistSec(fromId, toId);
            int waiting = journey.get(i + 1).getArrival() - dist - journey.get(i).getDeparture();
            str.append("\n" + journey.get(i).getInfo());
            str.append(String.format("\nTravel time: %7s", String.valueOf(dist)));
            str.append(String.format("\n    Waiting: %7s", String.valueOf(waiting)));

        }

        // Print last node
        str.append("\n" + journey.get(journey.size() - 1).getInfo());
        String journeyCoord = String.join(",", coordJourney);
        str.append("\n Path: [" + journeyCoord + "]");
        return str.toString();
    }

    public boolean isParked() {
        if (!this.getUsers().isEmpty()) return false;
        if (this.isRebalancing()) return false;
        if (this.getCurrentNode() instanceof NodePK) return false;
        if (this.getCurrentNode() instanceof NodeDP) return false;
        return !(this.getCurrentNode() instanceof NodeMiddle);
    }

    public boolean isCruising() {
        return (!this.getUsers().isEmpty() && this.load == 0);

    }

    public String getOccupancyStatus() {
        return String.format("(%2s/%d)", (!users.isEmpty() ? String.valueOf(load) : "-"), capacity);
    }

    public String getInfo() {

        return String.format("%s(%s) - Journey: %-200s - Users: %-100s - Attended: %s - %s",
                this,
                getOccupancyStatus(),
                (this.visit != null ? visit : "---"),
                users,
                servicedUsers,
                ((this.rebalancing == true) ? "(Rebalancing)" : ""));
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

    /**
     * Get best insertion of candidate user in vehicle at current time.
     *
     * @param candidateUser
     * @param currentTime
     * @return Best visit or null
     */
    public Visit getBestInsertionOld(User candidateUser, int currentTime) {

        if (this.isRebalancing()) return null;

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

        // Loop all insertion positions
        for (int pkPos = 0; pkPos <= visitSequence.size(); pkPos++) {
            for (int dpPos = pkPos; dpPos <= visitSequence.size(); dpPos++) {

                // Try to get a visit by inserting candidate user in sequence
                Visit candidateVisit = this.getVisitByInsertPositionOld(candidateUser,
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
    public Visit getVisitByInsertPositionOld(User insertedUser,
                                             List<Node> baseSequence,
                                             int pkPos,
                                             int dpPos,
                                             int currentTime) {

        // tripStatus[0] - Integer arrivalFrom
        // tripStatus[1] - Integer loadFrom
        // tripStatus[2] - Integer totalDelay

        // Status throughout sequence (arrival, load, delay, idle)
        int[] tripStatus = new int[3];

        // Arrival time of vehicle current node (if first node in visit sequence does not change).
        // Otherwise, current time.
        //TODO vehicle is going back to departure node if better visit is found in first leg, in reality, it is in the middle of the leg

        // If first leg of vehicle trips is kept, the original visit start time can be maintained.
        // In fact, only nodes after the first leg will be modified.
        if (pkPos > 0 && this.getVisit() != null && baseSequence.get(0) == this.getVisit().getSequenceVisits().getFirst()) {

            // Departure time of vehicle original visit may remain the same since node "pkPos" can still be visited
            tripStatus[0] = this.getDepartureCurrent();

        } else {
            // If first leg will be changed, the earliest arrival time at vehicle
            // current node shall be updated to current time
            tripStatus[0] = currentTime;
        }

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

        Visit visit = new Visit(newSequence2,
                tripStatus[2]);

        return visit;
    }

    /**
     * Check if there is a valid trip between "fromNode" and "toNode" occurring in vehicle "v".
     * If true, update trip intermediate status to reflect the addition of leg checked
     *
     * @param fromNode   Origin node
     * @param nextNode     Destination node
     * @param tripStatus Precedent status to update
     * @return true, if there is a valid trip, and false, otherwise.
     */
    public boolean isValidLeg(Node fromNode,
                              Node nextNode,
                              int[] tripStatus) {

        // tripStatus[0] - arrivalFrom
        // tripStatus[1] - loadFrom
        // tripStatus[2] - totalDelay


        // Update loads (DP nodes have negative loads)
        int load = tripStatus[1] + nextNode.getLoad();


        // Capacity constraint (if lower than zero, sequence is invalid! Visited DP before PK)
        if (load < 0 || load > this.getCapacity()) {
            return false;
        }

        /////////////////////////* VIABLE NEXT */////////////////////////////////////
        int distFromTo = Dao.getInstance().getDistSec(fromNode, nextNode);

        // No path available
        if (distFromTo < 0) {
            return false;
        }
        // Time vehicle arrives at next node (can be earlier or later)
        // If distance is zero, arrival next MUST be at least earliest time at next node
        int arrivalNext = Math.max(tripStatus[0] + distFromTo, nextNode.getEarliest());

        // Arrival cannot be later than latest time in node
        if (arrivalNext > nextNode.getLatest()) {
            return false;
        }

        int delay = arrivalNext - nextNode.getEarliest();

        // Arrival cannot be later than latest time in node
        if (delay < 0) {
            return false;
        }


        //Can vehicle visit next user?
        User uFrom = User.mapOfUsers.get(fromNode.getTripId());


        // From user requires private ride?
        if (uFrom != null && fromNode instanceof NodePK && !uFrom.isSharingAllowed()) {
            if (fromNode.getTripId() != nextNode.getTripId()) {
                //System.out.println(String.format("FR: Cannot go from %s(%s) to %s", fromNode, uFrom.getPerformanceClass(), nextNode));
                return false;
            }
        }

        // Next user requires private ride?
        User uTo = User.mapOfUsers.get(nextNode.getTripId());
        if (uTo != null && nextNode instanceof NodeDP && !uTo.isSharingAllowed()) {
            if (fromNode.getTripId() != nextNode.getTripId()) {
                //System.out.println(String.format("TO: Cannot go from %s(%s) to %s", fromNode, uFrom.getPerformanceClass(), nextNode));
                return false;
            }
        }

        // Hired vehicles cannot stay longer than contract deadline
        if (this.isHired() && arrivalNext > this.contractDeadline) {
            return false;
        }


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
        tripStatus[0] = arrivalNext;

        // Update load
        tripStatus[1] = load;

        // Delay and idleness
        tripStatus[2] += delay;

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

    public void updateDeparture(int currentTime) {
        int dep = Math.max(currentTime, this.getDepartureCurrent());
        this.getCurrentNode().setDeparture(dep);
    }

    /**
     * Update the intermediate position (middle node) of vehicle given current time.
     * FROM---> currentTime ---> M1 ----> M2 -------------------> TO - return M1
     * FROM--------------------> M1 ----> M2 --- currentTime ---> TO - return NULL
     *
     * @param currentTime
     */
    public void updateMiddle(int currentTime) {

        if (this.visit == null) {
            this.setMiddleNode(null);
            this.distMiddleNode = 0;
            return;
        }

        Node next;
        if (this.isRebalancing()) {
            next = this.visit.getTargetNode();
        } else {
            next = this.visit.sequenceVisits.getFirst();

            // No middle nodes between middle nodes
            if (next instanceof NodeMiddle) {
                return;
            }
        }

        // How long since vehicle left last visited node?
        int elapsedTime = currentTime - this.getDepartureCurrent();
        int nodeBetweenId = Dao.getInstance()
                .getNodeBetweenAndExtraDelay(
                        this.getCurrentNode(),
                        next,
                        elapsedTime);

        // Can't move to a middle node, go to next pk
        if (nodeBetweenId < 0) {
            this.setMiddleNode(null);
            this.distMiddleNode = 0;
            return;
        }

        //CURRENT TO MIDDLE
        //this.setMiddleNode(new NodeMiddle(nodeBetweenId));
        this.setMiddleNode(new NodeMiddle(nodeBetweenId, this.getCurrentNode(), next, elapsedTime));

        // Distance from current to middle node
        this.distMiddleNode = Dao.getInstance().getDistSec(this.getCurrentNode(), this.getMiddleNode());
    }

    public double getDistanceTraveledRebalancing() {
        return distanceTraveledRebalancing;
    }

    public void setDistanceTraveledRebalancing(double distanceTraveledRebalancing) {
        this.distanceTraveledRebalancing = distanceTraveledRebalancing;
    }

    public double getDistanceTraveledEmpty() {
        return distanceTraveledEmpty;
    }

    public void setDistanceTraveledEmpty(double distanceTraveledEmpty) {
        this.distanceTraveledEmpty = distanceTraveledEmpty;
    }

    public double getDistanceTraveledLoaded() {
        return distanceTraveledLoaded;
    }

    public void setDistanceTraveledLoaded(double distanceTraveledLoaded) {
        this.distanceTraveledLoaded = distanceTraveledLoaded;
    }

    public void increaseDistanceTraveledRebalancing(double distTraveledKm) {
        this.distanceTraveledRebalancing += distTraveledKm;
    }

    public boolean isStoppedRebalanceToPickup() {
        return stoppedRebalanceToPickup;
    }

    public void setStoppedRebalanceToPickup(boolean stoppedRebalanceToPickup) {
        this.stoppedRebalanceToPickup = stoppedRebalanceToPickup;
    }

    public void stoppedRebalancingToPickup() {
        this.stoppedRebalanceToPickup = true;
        this.rebalancing = false;
    }

    public List<Node> getJourney() {
        return journey;
    }

    public Node getMiddleNode() {
        return middleNode;
    }

    public void setMiddleNode(Node middleNode) {
        this.middleNode = middleNode;
    }

    public int getContractDeadline() {
        return contractDeadline;
    }

    public void setContractDeadline(int contractDeadline) {
        this.contractDeadline = contractDeadline;
    }

    @Override
    public int hashCode() {
        return this.getId();
    }
}



