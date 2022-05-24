package model;

import dao.Dao;
import model.node.*;
import simulation.Method;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class Vehicle implements Comparable<Vehicle>, Cloneable {

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

    //TODO Stop tracking for speed
    List<VisitObj> visitTrack;
    /* Vehicle status */
    private Integer currentLoad; // Current vehicle currentLoad
    private VisitObj visit; // Passengers, Node sequence, etc.
    private Node lastVisitedNode; // Node where vehicle is, or from where vehicle left
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
        visitTrack = new ArrayList<>();
        this.id = count;
        this.capacity = capacity;
        this.servicedUsers = new ArrayList<>();
        this.journey = new ArrayList<>();
        this.currentLoad = 0;
        this.hired = false;
        // Vehicle stays until the end
        this.contractDeadline = Integer.MAX_VALUE;
    }


    public boolean canEndContract(int currentTime) {
        return this.isHired() && currentTime >= this.getContractDeadline();
    }

    public Vehicle(Vehicle vehicle, VisitObj visit) {
        this.id = vehicle.id;
        this.visit = visit;
        this.visit.setVehicle(this);
    }

    public Vehicle(int capacity, int id_network) {

        // Initialize vehicle
        this(capacity);

        // Vehicle current node is its origin
        this.lastVisitedNode = this.origin = new NodeOrigin(count, id_network);

        // Vehicle journey (i.e., list of nodes) always start with origin
        this.journey.add(this.origin);
    }

    public Vehicle(int capacity, int id_network, double lat, double lon) {

        // Initialize vehicle
        this(capacity);

        // Vehicle current node is its origin
        this.lastVisitedNode = this.origin = new NodeOrigin(count, id_network, lat, lon);

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
        this.getOrigin().setEarliestDeparture(currentTime);
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
                this.lastVisitedNode.setEarliestDeparture(arrivalNextNode);
                this.visit.setDeparture(arrivalNextNode);
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

        // Serviced users in an execution round
        return servicedUsersInRound;
    }

    public boolean hasServicedAllRequests() {
        return this.visit.getSequenceVisits().isEmpty();
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
    public void endRebalancing(int currentSimulationTime) {

        this.distanceTraveledRebalancing += Dao.getInstance().getDistKm(
                this.lastVisitedNode,
                this.visit.getTargetNode());

        this.roundsIdle = roundsIdle + 1;

        this.lastVisitedNode = this.visit.getTargetNode();
        this.lastVisitedNode.setArrival(this.visit.getArrivalTimeAtNext());
        // Min. departure is arrival time
        this.lastVisitedNode.setEarliestDeparture(this.visit.getArrivalTimeAtNext());

        // Vehicle has been recently rebalanced, worth keeping in the fleet
        this.setRoundsIdle(0);

        // Node can become a rebalancing target again
        Node.tabu.remove(lastVisitedNode.getNetworkId());

        journey.add(lastVisitedNode);
    }

    public boolean hasFinishedRebalancing(int currentSimulationTime) {
        return currentSimulationTime >= this.visit.getArrivalTimeAtNext();
    }

    /**
     * Compute last drop off upon finishing the visit.
     *
     * @param currentTime
     */
    public void createNodeStopAndFinishVisitAt(int currentTime) {

        // Vehicle is stopped at last visited node and can depart at current time
        this.lastVisitedNode = new NodeStop(this.lastVisitedNode, this.id, currentTime);

        // Add current node to journey
        this.journey.add(this.lastVisitedNode);

        // Previous routing plan is discarded
        this.setVisit(null);

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

        // Visit is comprised of single node
        Visit visitRelocation = new VisitRelocation(targetNode, this);

        this.setVisit(visitRelocation);


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

    public VisitObj getValidVisitForUser(User candidateRequest) {
        return Method.getBestVisitFromInsertion(this, candidateRequest);
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
        bestVisit.setPassengers(new HashSet<>(this.visit.getPassengers()));

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
        return this.visit != null && (this.visit instanceof VisitRelocation);
    }

    /**
     * Departure time of last visited node.
     * @return Time vehicle left previous node
     */
    public Integer getDepartureCurrent() {
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

    public VisitObj getVisit() {
        return visit;
    }

    public void setVisit(VisitObj visit) {
        this.visit = visit;
        // When a vehicle take up a decision, it departs from its last visited node at the time specified in the visit
        if (visit != null) {
            this.getLastVisitedNode().setDeparture(visit.getDeparture());
        }
        visitTrack.add(visit);
    }

    public Node getLastVisitedNode() {
        return lastVisitedNode;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Logging, info ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void printVisitTrack() {
        System.out.println("# VISIT TRACK " + this);
        for (VisitObj v : visitTrack) {
            if (v != null) {
                System.out.println("  - " + v.getClass() + "=" + v);
            } else {
                System.out.println("  - Parked");
            }
        }
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
                ((this.isRebalancing()) ? "(Rebalancing)" : ""));
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return getId() == vehicle.getId();
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

    public void updateEarliestDeparture(int currentTime) {
        this.lastVisitedNode.setEarliestDeparture(Math.max(this.getEarliestDeparture(), currentTime));
    }

    /**
     * Update the intermediate position (middle node) of vehicle given current time.
     * If:
     *  - Vehicle is parked -> return null (no middle)
     *  - Vehicle has been assigned to a visit, but did not leave origin node -> return origin node
     *  - Vehicle has been assigned to a visit, but left origin node -> return closest middle, for example:
     *        Origin--> currentTime --> M1 --------------> M2 -----> Destination --------------- (return M1)
     *        Origin------------------> M1 --currentTime-> M2 -----> Destination --------------- (return M2)
     *        Origin------------------> M1 --------------> M2 -----> Destination ----currentTime (return Destination)
     *        Origin-------------------------currentTime-----------> Destination --------------- (return Destination)
     *
     *
     * @param currentTime
     */
    public void updateMiddle(int currentTime) {

        if (this.isParked()) {
            this.setMiddleNode(null);
            this.distMiddleNode = 0;
        } else {

            int elapsedTimeSinceLeftLastNode = currentTime - this.getEarliestDeparture();

            int networkIdWaypointNode = Dao.getInstance().getNodeBetweenAndExtraDelay(
                    this.getLastVisitedNode(),
                    this.visit.getTargetNode(),
                    elapsedTimeSinceLeftLastNode);

            // Distance from current to middle node
            this.distMiddleNode = Dao.getInstance().getDistSec(
                    this.getLastVisitedNode().getNetworkId(),
                    networkIdWaypointNode);

            Node middle = new NodeMiddle(
                    networkIdWaypointNode,
                    this.getLastVisitedNode(),
                    this.visit.getTargetNode(),
                    this.distMiddleNode);

            this.setMiddleNode(middle);
        }
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
        System.out.println(this + " rebalance to closest node " + middle + ". Visit=" + this.getVisit());
        this.rebalanceTo(middle);
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

        if (isParked()) {
            // Vehicle stays parked
            stop = getVisitStop();

        } else if (isRebalancing()) {
            // Vehicle can rebalance to:
            // - Middle node (all requests are displaced)
            // - Hotspot (a denied location)
            stop = getVisitStop();

        } else if (isCruisingToPickup()) {
            // Interrupt cruising, drop requests, and rebalance to closest middle
            stop = this.getVisitRelocationToMiddle();
        }
        return stop;
    }

    /**
     * Indicates when an empty vehicle is moving to pickup up passengers.
     * @return True, if vehicle has no passengers and has been assigned to users.
     */
    public boolean isCruisingToPickup() {
        return !isCarryingPassengers() && !this.getVisit().getRequests().isEmpty();
    }

    public VisitStop getVisitStop() {
        return new VisitStop(this);
    }

    public VisitDisplaceAndStop getVisitRelocationToMiddle() {
        Node middle = this.getMiddleNode();
        return new VisitDisplaceAndStop(middle, this);
    }

    public void addUserHiredMustPickup(User user) {
        this.userHiredMustPickup = user;
    }

    public User getUserHiredMustPickup() {
        return userHiredMustPickup;
    }

    public boolean isMoving() {
        return this.visit != null;
    }

    public boolean hasLeftLastNode() {
        return this.lastVisitedNode.getDeparture() != null;
    }

    public boolean isMovingToNode(Node node) {
        return this.getVisit().getTargetNode() == node;
    }

    public Node getTargetNode() {
        if (this.getVisit() == null) {
            return this.getLastVisitedNode();
        }
        return this.getVisit().getTargetNode();
    }

    public boolean isMovingToSameLocationOfNode(Node node) {
        return this.getTargetNode().getNetworkId() == node.getNetworkId();
    }

    public void removeRequests(Collection<User> request) {
        Set<User> requests = new HashSet<>(this.getVisit().getRequests());
        requests.removeAll(request);
        Visit bestVisit = Method.getBestVisitFromPDPermutationsSummarized(this, requests);

    }

    public Visit getVisitWithoutRequest(User request) {

        // Create request set out of one request
        Set<User> requestsWithoutUser = new HashSet<>(this.visit.getRequests());
        requestsWithoutUser.remove(request);

        // Remove request from current sequence
        List<Node> sequenceWithoutRequest = new LinkedList<Node>(this.visit.getSequenceVisits());
        sequenceWithoutRequest.remove(request.getNodePk());
        sequenceWithoutRequest.remove(request.getNodeDp());

        Visit visit;

        // Vehicle only had this request. Therefore, removing it means rebalancing vehicle to closest middle node
        // in the path to picking up this request.
        if (sequenceWithoutRequest.isEmpty()) {
            visit = this.getVisitRelocationToMiddle();
        } else {
            // There is a visit where vehicle's remaining requests and passengers will be picked up
            Node[] sequence = sequenceWithoutRequest.toArray(new Node[sequenceWithoutRequest.size()]);
            Leg draftVisit = Visit.getDraftVisit(this, sequence);
            visit = new Visit(sequence, draftVisit.delay, draftVisit.idleness, this, requestsWithoutUser);
        }

        visit.setVehicle(this);
        return visit;
    }

    public Visit getBestVisitWithoutRequest(User request) {
        Set<User> requests = new HashSet<>(this.getVisit().getRequests());
        requests.remove(request);
        // Find best visit without removed request
        Visit bestVisit = Method.getBestVisitFromPDPermutationsSummarized(this, requests);

        // When bestVisit is null, it means there are no requests or passengers
        if (bestVisit == null) {
            bestVisit = this.getVisitRelocationToMiddle();
        }

        return bestVisit;
    }

    public boolean hasAlreadyBeenAssignedToUser(User user) {
        return this.isServicing() && this.visit.getRequests().contains(user);
    }

    /**
     * If vehicle is parked, then return earliest departure.
     * If vehicle is moving, then return departure.
     * @return Earliest time vehicle can departure from last visited node.
     */
    public Integer getEarliestDeparture() {
        return this.lastVisitedNode.getDeparture() != null ? this.lastVisitedNode.getDeparture() : this.lastVisitedNode.getEarliestDeparture();
    }

    public List<VisitObj> getVisitTrack() {
        return visitTrack;
    }

    public void computeDistanceTraveledRebalancingUntilMiddle() {
        this.increaseDistanceTraveledRebalancing(this.distMiddleNode);
    }
}



