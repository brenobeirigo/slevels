package model;

import dao.Dao;
import model.node.*;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class Vehicle implements Comparable<Vehicle> {

    // Cumulative leg info saved in array[3] used to build legs step by step (i.e., each od segment at a time)
    public static final byte ARRIVAL = 0;
    public static final byte LOAD = 1;
    public static final byte DELAY = 2;


    /* Class attributes */
    public static int count = Node.MAX_NUMBER_NODES * 2; // Starting index of vehicles
    public static LinkedList<Node> setOfHotPoints = new LinkedList<>(); // Best points to rebalance

    /* Vehicle features */
    private int id; // Vehicle id (starting from count)
    private Node origin; // Origin node
    private int capacity; // vehicle capacity (number of seats)

    private int contractedDuration; // How many rounds vehicle is allowed to work?

    /* Vehicle status */
    private Integer currentLoad; // Current vehicle currentLoad
    private Visit visit; // Passengers, Node sequence, etc.
    private Node lastVisitedNode; // Node where vehicle is, or from where vehicle left
    private boolean rebalancing; // Is this vehicle currently rebalancing?
    private boolean hired; // Was this vehicle hired on the fly?
    private int roundsIdle; // How many rounds this vehicle is idle?
    private Node middleNode; // Where vehicle is now?
    private int distMiddleNode;
    private int activeRounds;
    private int contractDeadline;
    private double distanceTraveledRebalancing;
    private double distanceTraveledEmpty;
    private double distanceTraveledLoaded;
    /* Historical data */
    private List<Node> journey; // List of nodes visited by vehicle
    private List<User> servicedUsers; // List of users picked up (can't change vehicle after pick up)
    private boolean stoppedRebalanceToPickup;
    private User userHiredMustPickup;


    public Vehicle(int capacity, int id_network, int currentTime, boolean hired, int contractDeadline) {
        this(capacity, id_network, currentTime);
        this.hired = hired;
        this.contractedDuration = contractDeadline - currentTime;
        this.contractDeadline = contractDeadline;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Constructors /////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Vehicle(int capacity) {
        ++count;
        this.id = count;
        this.capacity = capacity;
        this.servicedUsers = new ArrayList<>();
        this.journey = new ArrayList<>();
        this.currentLoad = 0;
        this.hired = false;
        this.rebalancing = false;
        // Vehicle stays until the end
        this.contractDeadline = Integer.MAX_VALUE;
    }

    public Vehicle(Vehicle vehicle, Visit visit) {
        this.id = vehicle.id;
        this.visit = visit;
        this.visit.vehicle = this;
    }

    public Vehicle(int capacity, int id_network) {

        // Initialize vehicle
        this(capacity);

        // Vehicle current node is its origin
        this.lastVisitedNode = this.origin = new NodeOrigin(count, id_network, 0);

        // Vehicle journey (i.e., list of nodes) always start with origin
        this.journey.add(this.origin);
    }

    public Vehicle(int capacity, int id_network, double lat, double lon) {

        // Initialize vehicle
        this(capacity);

        // Vehicle current node is its origin
        this.lastVisitedNode = this.origin = new NodeOrigin(count, id_network, lat, lon, 0);

        // Vehicle journey (i.e., list of nodes) always start with origin
        this.journey.add(this.origin);
    }

    public Vehicle(int capacity, int id_network, boolean hired) {
        this(capacity, id_network);
        this.hired = hired;
    }

    public Vehicle(int capacity, int id_network, int currentTime) {
        this(capacity, id_network);
        this.getOrigin().setArrival(currentTime);
        this.getOrigin().setDeparture(currentTime);
    }

    public static void reset() {
        count = Node.MAX_NUMBER_NODES * 2; // Starting index of vehicles
        Vehicle.setOfHotPoints = new LinkedList<>();
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Set<Vehicle> getVehiclesWithPassengers(List<Vehicle> listVehicles) {
        return listVehicles
                .stream()
                .filter(vehicle -> vehicle.getVisit() != null && !vehicle.getVisit().getPassengers().isEmpty())
                .collect(Collectors.toSet());
    }

    public static Set<User> getAllPassengersFromVehicles(Collection<Vehicle> listVehicles) {
        return listVehicles
                .stream()
                .filter(vehicle -> vehicle.getVisit() != null)
                .map(vehicle -> vehicle.getVisit().getPassengers())
                .collect(HashSet::new, HashSet::addAll, HashSet::addAll);
    }

    /**
     * Join new requests (current period) with all matched requests (not yet picked up).
     * There can have better matches (i.e., requests can be serviced by different vehicles)
     */
    public static List<User> getRequestsFrom(Collection<Vehicle> listVehicles) {
        List<User> allRequests = new ArrayList<>();

        // Requests can still be picked up by other vehicles, add them to request list
        for (Vehicle vehicle : listVehicles) {
            if (vehicle.isServicing()) {
                allRequests.addAll(vehicle.getVisit().getRequests());
            }
        }
        return allRequests;
    }

    public static List<User> getUsersFrom(Set<Vehicle> listVehicles) {
        List<User> allUsers = new ArrayList<>();

        // Requests can still be picked up by other vehicles, add them to request list
        for (Vehicle vehicle : listVehicles) {
            if (vehicle.isServicing()) {
                allUsers.addAll(vehicle.getVisit().getRequests());
                allUsers.addAll(vehicle.getVisit().getPassengers());
            }
        }
        return allUsers;
    }

    public static List<Node> getVehicleOrigins(Set<Vehicle> vehicles) {
        List<Node> targets = new ArrayList<>();
        if (vehicles != null && !vehicles.isEmpty()) {
            List<Node> hiredOrigins = vehicles.stream().map(Vehicle::getOrigin).collect(Collectors.toList());
            targets.addAll(hiredOrigins);
        }
        return targets;
    }

    public static List<Vehicle> getVehiclesServicing(Set<Vehicle> vehicles) {
        return vehicles.stream().filter(Vehicle::isServicing).collect(Collectors.toList());
    }

    public static Set<Vehicle> getIdleVehiclesFrom(Set<Vehicle> vehicles) {
        return vehicles.stream().filter(Vehicle::isParked).collect(Collectors.toSet());
    }

    public static Set<Node> getLastVisitedNodeFrom(Set<Vehicle> vehicles) {
        return vehicles.stream().map(Vehicle::getLastVisitedNode).collect(Collectors.toSet());
    }

    public int isValidSequence(LinkedList<Node> sequence) {

        // All valid trips finish at delivery nodes
        if (!(sequence.getLast() instanceof NodeDP)) {
            return -1;
        }

        // If a pickup node is visited after its destination, trip is invalid
        Map<Integer, Boolean> destinationAlreadyVisited = new HashMap<>();

        // Data passed over legs
        int[] cumulativeLegPK = new int[]{
                this.getDepartureCurrent(),
                this.currentLoad,
                0};


        for (int i = 0; i < sequence.size() - 1; i++) {

            // Mark trip id all destinations visited
            if (sequence.get(i) instanceof NodeDP)
                destinationAlreadyVisited.put(sequence.get(i).getTripId(), true);

            // If pickup from same trip id is visited and destination have been marked, trip is invalid
            if (sequence.get(i) instanceof NodePK && destinationAlreadyVisited.containsKey(sequence.get(i).getTripId()))
                return -1;

            if (Visit.isLegInvalid(sequence.get(i), sequence.get(i + 1), cumulativeLegPK, this.capacity)) {
                return -1;
            }

            if (cumulativeLegPK[Vehicle.ARRIVAL] > this.getContractDeadline()) {
                return -1;
            }
        }

        return cumulativeLegPK[Vehicle.DELAY];
    }

    public void increaseActiveRounds() {
        this.activeRounds++;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Rebalancing //////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    /**
     * Move forward in the sequence of visits.
     * If car is parked - Do nothing
     * If car is rebalancing - Do nothing
     * If car finished rebalancing - Stop car
     * If car is servicing:
     * - Pick up and drop off users and update last visited node
     * - Stop car if drops off last user
     *
     * @param currentSimulationTime current time
     * @return Set of users serviced until current time
     */
    public Set<User> getServicedUsersUntil(int currentSimulationTime) {

        if (this.isParked()) {
            return null;
        }

        if (this.isRebalancing()) {
            endRebalanceIfTargetReachedAt(currentSimulationTime);
            /*
            // Is the current node a stop point? If so, vehicle was recently rebalanced
            if (lastVisitedNode instanceof NodeTargetRebalancing) {
                System.out.println("Finished REBALANCING!!!!!!!!!!!!!!!!!");


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

                System.out.println(String.format("AFTER ROUNDS - %s(%s--%s) -> %s(%s--%s) :%s",
                        origin,
                        Config.sec2Datetime(origin.getArrival()),
                        Config.sec2Datetime(origin.getDeparture()),
                        target,
                        Config.sec2Datetime(target.getArrival()),
                        Config.sec2Datetime(target.getDeparture()),
                        r));

            }
            */
            return null;
        }

        // Round [last current time, current time]
        Set<User> servicedUsersInRound = new HashSet<>();

        int nOfVisitedNodesInSequence = 0;

        for (Node nextNode : visit.getSequenceVisits()) {

            // Get arrival time at first node of visit sequenceVisits
            int arrivalNextNode = lastVisitedNode.getDeparture() + Dao.getInstance().getDistSec(lastVisitedNode, nextNode);

            // Sometimes, distance is zero, therefore the arrival should be earliest time
            arrivalNextNode = Math.max(arrivalNextNode, nextNode.getEarliest());

            // If car arrived at next node in sequence
            if (arrivalNextNode <= currentSimulationTime) {

                nOfVisitedNodesInSequence++;

                double legDistanceTraveledKm = Dao.getInstance().getDistKm(this.lastVisitedNode, nextNode);

                if (this.currentLoad == 0) {
                    this.distanceTraveledEmpty += legDistanceTraveledKm;
                } else {
                    this.distanceTraveledLoaded += legDistanceTraveledKm;
                }

                this.currentLoad += nextNode.getLoad();

                // Update data of current node
                this.lastVisitedNode = nextNode;
                this.lastVisitedNode.setArrival(arrivalNextNode);
                // Min. departure is arrival time
                this.lastVisitedNode.setDeparture(arrivalNextNode);
                this.journey.add(lastVisitedNode);

                // User associated to current Node
                User currentUser = User.mapOfUsers.get(lastVisitedNode.getTripId());

                // If DP node is visited, it means request is finished
                if (lastVisitedNode instanceof NodeDP) {
                    this.dropoff(currentUser);
                    servicedUsersInRound.add(currentUser);

                } else if (lastVisitedNode instanceof NodePK) {

                    // Request become passenger
                    this.pickup(currentUser);
                }
            } else {
                // If car didn't reach nextNode, subsequent nodes cannot be reached too
                break;
            }
        }

        // Remove visited nodes from sequence
        for (int j = 0; j < nOfVisitedNodesInSequence; j++) {
            this.visit.getSequenceVisits().removeFirst();
        }

        // If all nodes were visited
        if (this.visit.getSequenceVisits().isEmpty()) {
            this.createNodeStopAndFinishVisitAt(currentSimulationTime);
        }

        // Serviced users in an execution round
        return servicedUsersInRound;
    }

    private void dropoff(User passenger) {

        // Eliminate serviced user from visit
        this.visit.getPassengers().remove(passenger);

        // Add serviced user to vehicle
        this.servicedUsers.add(passenger);

        // Save total ride time in vehicle
        int rideTime = passenger.getNodeDp().getArrival() - passenger.getNodePk().getArrival();

        // Save ride trip ride time
        passenger.setRideTime(rideTime);

        // Save which type of vehicle picked up user
        if (this.isHired()) {
            passenger.computePickupByFreelanceVehicle();
        } else {
            passenger.computePickupByDedicatedVehicle();
        }

        /*###################################################################################################*/
        // Set arrival time at DP node for request
        int tripId = passenger.getId();
        User.status[tripId][User.DP_ARRIVAL_TIME] = passenger.getNodeDp().getArrival();
        // Save DP delay
        User.status[tripId][User.DP_DELAY] = passenger.getNodeDp().getDelay();

        // Visit total delay is reduced
        this.getVisit().discountDelay(passenger.getNodeDp().getDelay());
    }

    private void pickup(User currentUser) {

        // User is locked in vehicle (cannot change to other)
        this.visit.getPassengers().add(currentUser);

        // User is inside vehicle, no longer a request
        this.visit.getRequests().remove(currentUser);

        int tripId = currentUser.getId();

        // Set arrival time at PK node for request
        User.status[tripId][User.PK_ARRIVAL_TIME] = currentUser.getNodePk().getArrival();

        // Save PK delay
        User.status[tripId][User.PK_DELAY] = currentUser.getNodePk().getDelay();
    }

    /**
     * End rebalancing if target node was reached.
     * t is important to add rebalancing target to journey because:
     * - legs (NodeStop, NodeTargetRebalancing) indicate rebalancing
     * - legs (NodeTargetRebalancing, NodeStop) indicate waiting
     *
     * @param currentSimulationTime current execution time of the simulation
     */
    private void endRebalanceIfTargetReachedAt(int currentSimulationTime) {

        // If rebalancing is finished (vehicle arrived at target)
        if (currentSimulationTime >= this.visit.getArrivalTimeAtNext()) {

            this.distanceTraveledRebalancing += Dao.getInstance().getDistKm(
                    this.lastVisitedNode,
                    this.visit.getTargetNode());

            // Vehicle is no longer rebalancing
            this.setRebalancing(false);

            // Signalize that vehicle is stopped at last visited node in a stop point
            this.setLastVisitedNode(this.visit.getTargetNode());

            // Vehicle has been recently rebalanced, worth keeping in the fleet
            this.setRoundsIdle(0);

            // Node can become a rebalancing target again
            Node.tabu.remove(lastVisitedNode.getNetworkId());

            journey.add(lastVisitedNode);

            createNodeStopAndFinishVisitAt(currentSimulationTime);

        }
    }

    /**
     * Compute last drop off upon finishing the visit.
     *
     * @param currentTime
     */
    private void createNodeStopAndFinishVisitAt(int currentTime) {

        assert this.rebalancing == false : "Rebalancing is false";

        // Vehicle is stopped at last visited node and can depart at current time
        this.lastVisitedNode = new NodeStop(this.lastVisitedNode, this.id, currentTime);

        // Add current node to journey
        this.journey.add(this.lastVisitedNode);

        // Previous routing plan is discarded
        this.setVisit(null);
    }

    private Node getStop() {
        assert this.getLastVisitedNode() instanceof NodePK : "Stop cant be pickup " + this.lastVisitedNode;
        if (this.lastVisitedNode instanceof NodeDP)
            return new NodeStop(this.lastVisitedNode, this.id, this.lastVisitedNode.getDeparture());
        else {
            return this.lastVisitedNode;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Insertion algorithm //////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Visit getVisitWithEnroute() {

        if (this.visit == null || this.visit.getPassengers() == null) return null;

        Set<Node> deliveries = new HashSet<>();

        for (User u : this.visit.getPassengers()) {
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Gets & Sets //////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    /**
     * Try to create a visit out of a single user (called when vehicle is empty)
     *
     * @param candidateRequest
     * @param iterationTime    Simulation current time
     * @return Visit containing pk and dp nodes of user "candidateRequest", or null if visit can't be created
     */
    public Visit getVisitFromEmptyVehicle(User candidateRequest, int iterationTime) {

        int departureTime;
        Node departureNode;

        if (this.isRebalancing()) {

            //CURRENT TO MIDDLE
            Node middle = this.getMiddleNode();

            if (middle == null) {
                return null;
            }

            // Time vehicle arrives at the middle node where trip will ACTUALLY start
            departureTime = this.getDepartureCurrent() + this.distMiddleNode;
            departureNode = middle;

        } else {
            departureTime = iterationTime;
            departureNode = this.getLastVisitedNode();
        }

        int[] cumulativeLeg = new int[]{departureTime, 0, 0};

        // From vehicle current node to user pk
        if (this.isLegInvalid(departureNode, candidateRequest.getNodePk(), cumulativeLeg))
            return null;

        // From user pk to user dp
        if (this.isLegInvalid(candidateRequest.getNodePk(), candidateRequest.getNodeDp(), cumulativeLeg))
            return null;

        // Create linked list (fast adds and removals)
        LinkedList<Node> visitSequence = new LinkedList<>();

        if (isRebalancing())
            visitSequence.add(departureNode);

        visitSequence.add(candidateRequest.getNodePk());
        visitSequence.add(candidateRequest.getNodeDp());

        Visit first = new Visit(visitSequence, cumulativeLeg[DELAY], this, candidateRequest);

        return first;
    }

    /**
     * Get best insertion of candidate user in vehicle at current time.
     */
    public Visit getVisitWithInsertedUser(User candidateRequest, int currentTime) {

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Vehicle has NO visit ////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        if (this.getVisit() == null || this.isRebalancing()) {

            // Try to create a one user visit
            return getVisitFromEmptyVehicle(candidateRequest, currentTime);
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Vehicle has a visit /////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        // First best including new users is initiated blank
        Visit bestVisit = new Visit();

        // Copy elements in vehicle visit to candidate sequence that will be formed
        List<Node> visitsVehicle = this.getVisit().getSequenceVisits();

        //------------------------------------------------------------------------------------------------------------//
        //------ Loop pk positions -----------------------------------------------------------------------------------//
        //------------------------------------------------------------------------------------------------------------//

        pkcontinue:
        for (int pkPos = 0; pkPos <= visitsVehicle.size(); pkPos++) {

            //int currentLoad = this.getLastVisitedNode().getCurrentLoad();

            int currentLoad = this.currentLoad;

            int departureTimeFromVehicle = this.getDepartureCurrent();

            // Status throughout sequence (arrival, currentLoad, delay, idle)
            int[] cumulativeLegPK = new int[]{
                    departureTimeFromVehicle,
                    currentLoad,
                    0};

            // Current node is vehicle
            Node currentPK = this.getLastVisitedNode();

            // Check if first leg will be changed. If so, departure time from vehicle is current simulation time
            Node middle = null;

            if (pkPos == 0) {

                //CURRENT TO MIDDLE
                middle = this.getMiddleNode();

                // Can't move to a middle node, go to next pk
                if (middle == null) {
                    continue;
                }

                // Update arrival in middle
                cumulativeLegPK[ARRIVAL] += this.distMiddleNode;
                currentPK = middle;

                /*

                System.out.println(String.format("Shortest path between %s(%d) and %s(%d): %s",
                        this.getLastVisitedNode(),
                        this.getLastVisitedNode().getNetworkId(),
                        visitsVehicle.get(pkPos),
                        visitsVehicle.get(pkPos).getNetworkId(),
                        Dao.getInstance().getShortestPathBetween(
                                this.getLastVisitedNode().getNetworkId(),
                                visitsVehicle.get(pkPos).getNetworkId())));
                */
            }

            // #### Before PK ##########################################################################################
            for (int i = 0; i < pkPos; i++) {

                // Get next in sequence
                Node next = visitsVehicle.get(i);

                // Check if it is possible to go from current to next
                if (this.isLegInvalid(currentPK, next, cumulativeLegPK)) {
                    continue pkcontinue;
                }

                // Update current
                currentPK = next;
            }

            // #### PK #################################################################################################
            if (this.isLegInvalid(currentPK, candidateRequest.getNodePk(), cumulativeLegPK)) {
                continue;
            }

            // Update current
            currentPK = candidateRequest.getNodePk();

            dpcontinue:
            for (int dpPos = pkPos; dpPos <= visitsVehicle.size(); dpPos++) {

                // Go back to last configuration
                int[] cumulativeLeg = cumulativeLegPK.clone();
                Node current = currentPK;

                // #### Between PK and DP ##############################################################################
                for (int i = pkPos; i < dpPos; i++) {

                    // Get next in sequence
                    Node next = visitsVehicle.get(i);

                    // Check if it is possible to go from current to next
                    if (this.isLegInvalid(current, next, cumulativeLeg)) {
                        continue dpcontinue;
                    }

                    // Update current
                    current = next;
                }

                // #### DP #############################################################################################
                if (this.isLegInvalid(current, candidateRequest.getNodeDp(), cumulativeLeg)) {
                    continue;
                }

                // Update current
                current = candidateRequest.getNodeDp();

                // #### After DP #######################################################################################
                for (int i = dpPos; i < visitsVehicle.size(); i++) {

                    // Get next in sequence
                    Node next = visitsVehicle.get(i);

                    // Check if it is possible to go from current to next
                    if (this.isLegInvalid(current, next, cumulativeLeg)) {

                        continue dpcontinue;
                    }

                    // Update current
                    current = next;

                }

                // Create linked list (fast adds and removals)
                LinkedList<Node> newSequence = new LinkedList<>(visitsVehicle);
                newSequence.add(dpPos, candidateRequest.getNodeDp());
                newSequence.add(pkPos, candidateRequest.getNodePk());

                // Insert middle node in first leg
                if (pkPos == 0) {
                    newSequence.add(0, middle);
                }

                Visit candidateVisit = new Visit(
                        newSequence,
                        cumulativeLeg[DELAY]);

                candidateVisit.setVehicle(this);

                /*
                System.out.println(String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateRequest, newSequence, candidateVisit));
                System.out.println("VISITS = " + visitsVehicle + " - " + candidateRequest.getNodePk() + "-->" + candidateRequest.getNodeDp());
                System.out.println("#######" + bestVisit);
                System.out.println("-------" + candidateVisit + "--" + candidateVisit.getSequenceVisits());
                 */
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

        // New visit contains vehicle users and new inserted request
        Set<User> requests = new HashSet<>(this.visit.getRequests());
        requests.add(candidateRequest);
        bestVisit.setRequests(requests);

        // New visit contains the vehicle passengers
        bestVisit.setPassengers(this.visit.getPassengers());

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
        return this.lastVisitedNode.getDeparture();
    }

    public Node getOrigin() {
        return origin;
    }

    public int getId() {
        return id;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public Visit getVisit() {
        return visit;
    }

    public void setVisit(Visit visit) {
        this.visit = visit;
    }

    public Node getLastVisitedNode() {
        return lastVisitedNode;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Logging, info ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setLastVisitedNode(Node lastVisitedNode) {
        this.lastVisitedNode = lastVisitedNode;
    }

    public int getCapacity() {
        return capacity;
    }

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
                this,
                startTime,
                endTime,
                operatingTime,
                totalWaiting,
                operatingTW,
                (double) operatingTime * 100 / operatingTW,
                delayPK,
                delayDP);
    }

    @Override
    public String toString() {
        // Print H if vehicle is hired and V otherwise (plus vehicle Id)
        return String.format("%12s", (this.isHired() ? String.format("H(t=%4d)", this.contractDeadline) : "V") + (id - Node.MAX_NUMBER_NODES * 2));
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
            coordJourney.add(String.format("[%f, %f]", journey.get(i).getLon(), journey.get(i).getLat()));
            int toId = journey.get(i + 1).getNetworkId();
            coordJourney.add(String.format("[%f, %f]", journey.get(i + 1).getLon(), journey.get(i + 1).getLat()));
            int dist = Dao.getInstance().getDistSec(fromId, toId);
            int waiting = journey.get(i + 1).getArrival() - dist - journey.get(i).getDeparture();
            str.append("\n" + journey.get(i).getInfo());
            str.append(String.format("\nTravel time: %7s", dist));
            str.append(String.format("\n    Waiting: %7s", waiting));

        }

        // Print last node
        str.append("\n" + journey.get(journey.size() - 1).getInfo());
        String journeyCoord = String.join(",", coordJourney);
        str.append("\n Path: [" + journeyCoord + "]");
        return str.toString();
    }

    /**
     * @return true, if vehicle is servicing users (passengers or requests)
     */
    public boolean isServicing() {
        return (this.visit != null && !this.isRebalancing());
    }

    public boolean isParked() {
        return (this.visit == null);
    }

    public boolean isCruising() {
        // Vehicle is empty and there are requests
        return (!this.visit.getRequests().isEmpty() && this.currentLoad == 0);
    }

    public String getOccupancyStatus() {
        return String.format("(%2s/%d)", (this.visit != null && !this.visit.getPassengers().isEmpty() ? String.valueOf(currentLoad) : "-"), capacity);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// OLD METHOD ///////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public String getInfo() {

        return String.format("%s(%s) - Journey: %-200s - Passengers: %-100s - Requests: %-100s - Attended: %s - %s",
                this,
                getOccupancyStatus(),
                (this.visit != null ? visit : "Stopped at " + lastVisitedNode),
                (this.visit != null ? this.visit.getPassengers() : "---"),
                (this.visit != null ? this.visit.getRequests() : "---"),
                servicedUsers,
                ((this.rebalancing) ? "(Rebalancing)" : ""));
    }

    /**
     * Sort vehicles according to currentLoad (lower first).
     *
     * @param that Vehicle
     * @return -1 (BEFORE), 0 (EQUAL), 1 (AFTER)
     */
    @Override
    public int compareTo(Vehicle that) {
        if (this.currentLoad < that.currentLoad) return BEFORE;
        if (this.currentLoad > that.currentLoad) return AFTER;
        return EQUAL;
    }

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

        // cumulativeLeg[ARRIVAL] - Integer arrivalFrom
        // cumulativeLeg[LOAD] - Integer loadFrom
        // cumulativeLeg[DELAY] - Integer totalDelay

        // Status throughout sequence (arrival, currentLoad, delay, idle)
        int[] cumulativeLeg = new int[3];

        // Arrival time of vehicle current node (if first node in visit sequence does not change).
        // Otherwise, current time.
        //TODO vehicle is going back to departure node if better visit is found in first leg, in reality, it is in the middle of the leg

        // If first leg of vehicle trips is kept, the original visit start time can be maintained.
        // In fact, only nodes after the first leg will be modified.
        if (pkPos > 0 && this.getVisit() != null && baseSequence.get(0) == this.getVisit().getSequenceVisits().getFirst()) {

            // Departure time of vehicle original visit may remain the same since node "pkPos" can still be visited
            cumulativeLeg[ARRIVAL] = this.getDepartureCurrent();

        } else {
            // If first leg will be changed, the earliest arrival time at vehicle
            // current node shall be updated to current time
            cumulativeLeg[ARRIVAL] = currentTime;
        }

        // Load
        cumulativeLeg[LOAD] = this.getLastVisitedNode().getLoad();

        // Current node is vehicle
        Node current = this.getLastVisitedNode();

        // #### Before PK ##############################################################################################


        for (int i = 0; i < pkPos; i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (this.isLegInvalid(current, next, cumulativeLeg)) {
                return null;
            }

            // Update current
            current = next;
            //newSequence.add(current);
        }

        // #### PK #####################################################################################################
        if (this.isLegInvalid(current, insertedUser.getNodePk(), cumulativeLeg)) {
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
            if (this.isLegInvalid(current, next, cumulativeLeg)) {
                return null;
            }

            // Update current
            current = next;
            //newSequence.add(current);
        }

        // #### DP #####################################################################################################
        if (this.isLegInvalid(current, insertedUser.getNodeDp(), cumulativeLeg))
            return null;

        // Update current
        current = insertedUser.getNodeDp();

        // #### After DP ###############################################################################################
        for (int i = dpPos; i < baseSequence.size(); i++) {

            // Get next in sequence
            Node next = baseSequence.get(i);

            // Check if it is possible to go from current to next
            if (this.isLegInvalid(current, next, cumulativeLeg)) {
                return null;
            }

            // Update current
            current = next;
        }

        // Create linked list (fast adds and removals)
        LinkedList<Node> newSequence2 = new LinkedList<>(baseSequence);
        newSequence2.add(dpPos, insertedUser.getNodeDp());
        newSequence2.add(pkPos, insertedUser.getNodePk());

        Visit visit = new Visit(newSequence2,
                cumulativeLeg[DELAY]);

        return visit;
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
    public boolean isLegInvalid(Node fromNode,
                                Node nextNode,
                                int[] cumulativeLeg) {

        return Visit.isLegInvalid(fromNode, nextNode, cumulativeLeg, this.capacity);
    }

    public void updateDeparture(int currentTime) {
        int dep = Math.max(currentTime, this.getDepartureCurrent());
        this.getLastVisitedNode().setDeparture(dep);
    }

    /**
     * Update the intermediate position (middle node) of vehicle given current time.
     * FROM---> currentTime ---> M1 ----> M2 -------------------> TO - return M1
     * FROM--------------------> M1 ----> M2 --- currentTime ---> TO - return NULL
     *
     * @param currentTime
     */
    public void updateMiddle(int currentTime) {

        if (this.isParked()) {
            this.setMiddleNode(null);
            this.distMiddleNode = 0;
            return;
        }

        // next = rebalancing target (if VisitRelocation) and first node in sequence (if VisitInsertion)
        Node next = this.visit.getTargetNode();

        // How long since vehicle left last visited node?
        int elapsedTime = currentTime - this.getDepartureCurrent();
        int nodeBetweenId = Dao.getInstance()
                .getNodeBetweenAndExtraDelay(
                        this.getLastVisitedNode(),
                        next,
                        elapsedTime);

        // Can't move to a middle node, closest middle node is next pickup
        // TODO why having a middle node == next?
        if (nodeBetweenId < 0) {
            nodeBetweenId = next.getNetworkId();
        }

        //CURRENT TO MIDDLE
        Node middle = new NodeMiddle(nodeBetweenId, this.getLastVisitedNode(), next, elapsedTime);
        this.setMiddleNode(middle);

        // Distance from current to middle node
        this.distMiddleNode = Dao.getInstance().getDistSec(this.getLastVisitedNode(), this.getMiddleNode());
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

    public boolean isCarryingPassengers() {
        return this.visit != null && !this.visit.getPassengers().isEmpty();
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

    /**
     * When vehicle is disrupted, users are discarded and it rebalances to the closest node (middle).
     * 1 - Make a stop node out of the last visited node.
     * 2 - Rebalance to middle
     */
    public void rebalanceToClosestNode() {

        assert !(this.lastVisitedNode instanceof NodePK) : "Last visited is not DP" + this.visit;

        // For the sake of correctness, vehicle has to depart from a stop node created from last visited node.
        if (this.lastVisitedNode instanceof NodeDP)
            this.createNodeStopAndFinishVisitAt(this.lastVisitedNode.getArrival());

        // Middle node between last visited and target (if null, middle = target)
        Node middle = this.getMiddleNode();
        this.rebalanceTo(middle);
    }

    public Visit getRebalanceVisitToClosestNodeInCurrentLeg() {

        Node middle = this.getMiddleNode();

        // Create a target node for rebalancing
        NodeTargetRebalancing target = new NodeTargetRebalancing(middle);

        // Visit is comprised of single node
        Visit visit = new VisitRelocation(target, this);

        visit.delay = 0;
        visit.idle = 0;

        return visit;
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

    /**
     * Stop visit for vehicles rebalancing, cruising, and parked.
     * @return Stop visit, or null if vehicle is carrying passengers (can't stop)
     */
    public Visit getStopVisit() {

        Visit stop = null;

        if (isRebalancing()) {
            stop = getVisit();

        } else if (isParked()) {
            stop = new VisitStop(this);

        } else if (!isCarryingPassengers()) {
            stop = getRebalanceVisitToClosestNodeInCurrentLeg();
        }
        return stop;
    }

    public void addUserHiredMustPickup(User user) {
        this.userHiredMustPickup = user;
    }

    public User getUserHiredMustPickup() {
        return userHiredMustPickup;
    }

    public boolean isMoving() {
        return this.isRebalancing() || this.isServicing();
    }
}



