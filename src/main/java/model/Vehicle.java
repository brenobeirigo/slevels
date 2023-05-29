package model;

import dao.Logging;
import model.demand.User;
import model.network.TransportNetwork;
import model.node.*;
import model.visit.*;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import simulation.Environment;

import java.util.*;
import java.util.stream.Collectors;

import static config.Config.*;

public class Vehicle implements Comparable<Vehicle> {

    // Cumulative leg info saved in array[3] used to build legs step by step (i.e., each od segment at a time)
    public static final byte ARRIVAL = 0;
    public static final byte LOAD = 1;
    public static final byte DELAY = 2;

    public static final String STATE_REBALANCING = "Rebalancing";
    public static final String STATE_PARKED = "Parked last destination";
    public static final String STATE_CRUISING = "Cruising to pickup";
    public static final String STATE_ATORIGIN = "At origin";
    public static final String STATE_SERVICING = "Servicing";
    public static final String STATE_STOPPED_REBALANCING = "Stopped rebalancing";
    public static final String[] STATES = new String[]{
            Vehicle.STATE_ATORIGIN,
            Vehicle.STATE_REBALANCING,
            Vehicle.STATE_STOPPED_REBALANCING,
            Vehicle.STATE_CRUISING,
            Vehicle.STATE_SERVICING,
            Vehicle.STATE_PARKED,
    };
    public static final int MAX_SIZE_JOURNEY = 10;


    /* Class attributes */
    public static int count = Node.MAX_NUMBER_NODES * 2; // Starting index of vehicles
    public static LinkedList<Node> setOfHotPoints = new LinkedList<>(); // Best points to rebalance

    /* Vehicle features */
    private int id; // Vehicle id (starting from count)
    private Node origin; // Origin node
    private int capacity; // vehicle capacity (number of seats)

    private int contractedDuration; // How many rounds vehicle is allowed to work?

    //TODO Stop tracking for speed
    CircularFifoQueue<String> visitTrack;
    /* Vehicle status */
    private Integer currentLoad; // Current vehicle currentLoad
    private VisitObj visit; // Passengers, Node sequence, etc.
    public Node lastVisitedNode; // Node where vehicle is, or from where vehicle left
    private boolean hired; // Was this vehicle hired on the fly?
    public int roundsIdle; // How many rounds this vehicle is idle?
    private Node middleNode; // Where vehicle is now?
    public int distMiddleNode;
    private int activeRounds;
    private int contractDeadline;
    private double distanceTraveledRebalancing;
    private double distanceTraveledEmpty;
    private double distanceTraveledLoaded;
    /* Historical data */
    private CircularFifoQueue<Node> journey; // List of nodes visited by vehicle

    public List<User> getServicedUsers() {
        return servicedUsers;
    }

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
        visitTrack = new CircularFifoQueue<>(MAX_SIZE_JOURNEY);
        this.id = count;
        this.capacity = capacity;
        this.servicedUsers = new ArrayList<>();
        this.journey = new CircularFifoQueue<>(MAX_SIZE_JOURNEY);
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
        Logging.logger.info("Resetting vehicle");
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
//
//    public int isValidSequence(LinkedList<Node> sequence) {
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
//                this.getDepartureCurrent(),
//                this.currentLoad,
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
//            if (Visit.isLegInvalid(sequence.get(i), sequence.get(i + 1), cumulativeLegPK, this.capacity)) {
//                return -1;
//            }
//
//            if (cumulativeLegPK[Vehicle.ARRIVAL] > this.getContractDeadline()) {
//                return -1;
//            }
//        }
//
//        return cumulativeLegPK[Vehicle.DELAY];
//    }

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
        Logging.logger.info(this + " - Deadline:" + this.contractDeadline);
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
    public Set<User> getServicedUsersUntil(int currentSimulationTime, TransportNetwork network) {

        // Round [last current time, current time]
        Set<User> servicedUsersInRound = new HashSet<>();

        int nOfVisitedNodesInSequence = 0;

        for (Node nextNode : visit.getSequenceVisits()) {

            // Get arrival time at first node of visit sequenceVisits
            int arrivalNextNode = lastVisitedNode.getDeparture() + network.getDistSec(
                    lastVisitedNode.getNetworkId(),
                    nextNode.getNetworkId());

            // Sometimes, distance is zero, therefore the arrival should be earliest time
            arrivalNextNode = Math.max(arrivalNextNode, nextNode.getEarliest());

            // If car arrived at next node in sequence
            if (arrivalNextNode <= currentSimulationTime) {

                nOfVisitedNodesInSequence++;

                double legDistanceTraveledKm = network.getDistance(
                        this.lastVisitedNode.getNetworkId(),
                        nextNode.getNetworkId());

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
                if (lastVisitedNode instanceof NodeDropoff) {
                    this.dropoff(currentUser);
                    servicedUsersInRound.add(currentUser);

                } else if (lastVisitedNode instanceof NodePickup) {

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

        Logging.logger.info("DELIVERIES ORDERED:" + newVisit);

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
    public void rebalanceTo(Node targetNode, Environment env) {

        // Visit is comprised of single node
        Visit visitRelocation = new VisitRelocation(targetNode, this, env);

        this.setVisit(visitRelocation);


        // Signalize there are vehicles going to the point
        Node.tabu.add(targetNode.getNetworkId());
    }

//    /**
//     * Try to create a visit out of a single user (called when vehicle is empty)
//     *
//     * @param candidateRequest
//     * @param iterationTime    Environment current time
//     * @return Visit containing pk and dp nodes of user "candidateRequest", or null if visit can't be created
//     */
//    public Visit getVisitFromEmptyVehicle(User candidateRequest, int iterationTime) {
//
//        int departureTime;
//        Node departureNode;
//
//        if (this.isRebalancing()) {
//
//            //CURRENT TO MIDDLE
//            Node middle = this.getMiddleNode();
//
//            if (middle == null) {
//                return null;
//            }
//
//            // Time vehicle arrives at the middle node where trip will ACTUALLY start
//            departureTime = this.getDepartureCurrent() + this.distMiddleNode;
//            departureNode = middle;
//
//        } else {
//            departureTime = iterationTime;
//            departureNode = this.getLastVisitedNode();
//        }
//
//        int[] cumulativeLeg = new int[]{departureTime, 0, 0};
//
//        // From vehicle current node to user pk
//        if (this.isLegInvalid(departureNode, candidateRequest.getNodePk(), cumulativeLeg))
//            return null;
//
//        // From user pk to user dp
//        if (this.isLegInvalid(candidateRequest.getNodePk(), candidateRequest.getNodeDp(), cumulativeLeg))
//            return null;
//
//        // Create linked list (fast adds and removals)
//        LinkedList<Node> visitSequence = new LinkedList<>();
//
//        if (isRebalancing())
//            visitSequence.add(departureNode);
//
//        visitSequence.add(candidateRequest.getNodePk());
//        visitSequence.add(candidateRequest.getNodeDp());
//
//        Visit first = new Visit(visitSequence, cumulativeLeg[DELAY], this, candidateRequest);
//
//        return first;
//    }



    /**
     * Get best insertion of candidate user in vehicle at current time.
     */
//    public Visit getVisitWithInsertedUser(User candidateRequest, int currentTime) {
//
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        // Vehicle has NO visit ////////////////////////////////////////////////////////////////////////////////////////
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//        if (this.getVisit() == null || this.isRebalancing()) {
//
//            // Try to create a one user visit
//            return getVisitFromEmptyVehicle(candidateRequest, currentTime);
//        }
//
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        // Vehicle has a visit /////////////////////////////////////////////////////////////////////////////////////////
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//        // First best including new users is initiated blank
//        Visit bestVisit = new Visit();
//
//        // Copy elements in vehicle visit to candidate sequence that will be formed
//        List<Node> visitsVehicle = this.getVisit().getSequenceVisits();
//
//        //------------------------------------------------------------------------------------------------------------//
//        //------ Loop pk positions -----------------------------------------------------------------------------------//
//        //------------------------------------------------------------------------------------------------------------//
//
//        pkcontinue:
//        for (int pkPos = 0; pkPos <= visitsVehicle.size(); pkPos++) {
//
//            //int currentLoad = this.getLastVisitedNode().getCurrentLoad();
//
//            int currentLoad = this.currentLoad;
//
//            int departureTimeFromVehicle = this.getDepartureCurrent();
//
//            // Status throughout sequence (arrival, currentLoad, delay, idle)
//            int[] cumulativeLegPK = new int[]{
//                    departureTimeFromVehicle,
//                    currentLoad,
//                    0};
//
//            // Current node is vehicle
//            Node currentPK = this.getLastVisitedNode();
//
//            // Check if first leg will be changed. If so, departure time from vehicle is current simulation time
//            Node middle = null;
//
//            if (pkPos == 0) {
//
//                //CURRENT TO MIDDLE
//                middle = this.getMiddleNode();
//
//                // Can't move to a middle node, go to next pk
//                if (middle == null) {
//                    continue;
//                }
//
//                // Update arrival in middle
//                cumulativeLegPK[ARRIVAL] += this.distMiddleNode;
//                currentPK = middle;
//
//                /*
//
//                Logging.logger.info("{}", String.format("Shortest path between %s(%d) and %s(%d): %s",
//                        this.getLastVisitedNode(),
//                        this.getLastVisitedNode().getNetworkId(),
//                        visitsVehicle.get(pkPos),
//                        visitsVehicle.get(pkPos).getNetworkId(),
//                        Dao.getInstance().getShortestPathBetween(
//                                this.getLastVisitedNode().getNetworkId(),
//                                visitsVehicle.get(pkPos).getNetworkId())));
//                */
//            }
//
//            // #### Before PK ##########################################################################################
//            for (int i = 0; i < pkPos; i++) {
//
//                // Get next in sequence
//                Node next = visitsVehicle.get(i);
//
//                // Check if it is possible to go from current to next
//                if (this.isLegInvalid(currentPK, next, cumulativeLegPK)) {
//                    continue pkcontinue;
//                }
//
//                // Update current
//                currentPK = next;
//            }
//
//            // #### PK #################################################################################################
//            if (this.isLegInvalid(currentPK, candidateRequest.getNodePk(), cumulativeLegPK)) {
//                continue;
//            }
//
//            // Update current
//            currentPK = candidateRequest.getNodePk();
//
//            dpcontinue:
//            for (int dpPos = pkPos; dpPos <= visitsVehicle.size(); dpPos++) {
//
//                // Go back to last configuration
//                int[] cumulativeLeg = cumulativeLegPK.clone();
//                Node current = currentPK;
//
//                // #### Between PK and DP ##############################################################################
//                for (int i = pkPos; i < dpPos; i++) {
//
//                    // Get next in sequence
//                    Node next = visitsVehicle.get(i);
//
//                    // Check if it is possible to go from current to next
//                    if (this.isLegInvalid(current, next, cumulativeLeg)) {
//                        continue dpcontinue;
//                    }
//
//                    // Update current
//                    current = next;
//                }
//
//                // #### DP #############################################################################################
//                if (this.isLegInvalid(current, candidateRequest.getNodeDp(), cumulativeLeg)) {
//                    continue;
//                }
//
//                // Update current
//                current = candidateRequest.getNodeDp();
//
//                // #### After DP #######################################################################################
//                for (int i = dpPos; i < visitsVehicle.size(); i++) {
//
//                    // Get next in sequence
//                    Node next = visitsVehicle.get(i);
//
//                    // Check if it is possible to go from current to next
//                    if (this.isLegInvalid(current, next, cumulativeLeg)) {
//
//                        continue dpcontinue;
//                    }
//
//                    // Update current
//                    current = next;
//
//                }
//
//                // Create linked list (fast adds and removals)
//                LinkedList<Node> newSequence = new LinkedList<>(visitsVehicle);
//                newSequence.add(dpPos, candidateRequest.getNodeDp());
//                newSequence.add(pkPos, candidateRequest.getNodePk());
//
//                // Insert middle node in first leg
//                if (pkPos == 0) {
//                    newSequence.add(0, middle);
//                }
//
//                Visit candidateVisit = new Visit(
//                        newSequence,
//                        cumulativeLeg[DELAY]);
//
//                candidateVisit.setVehicle(this);
//
//                /*
//                Logging.logger.info("{}", String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateRequest, newSequence, candidateVisit));
//                Logging.logger.info("VISITS = " + visitsVehicle + " - " + candidateRequest.getNodePk() + "-->" + candidateRequest.getNodeDp());
//                Logging.logger.info("#######" + bestVisit);
//                Logging.logger.info("-------" + candidateVisit + "--" + candidateVisit.getSequenceVisits());
//                 */
//                // Update best visit (Compare delay)
//                if (bestVisit.compareTo(candidateVisit) > 0) {
//                    bestVisit = candidateVisit;
//                }
//            }
//        }
//
//        // If best visit was not found
//        if (bestVisit.getSequenceVisits() == null) {
//            return null;
//        }
//
//        // Assign vehicle to best
//        bestVisit.setVehicle(this);
//
//        // New visit contains vehicle users and new inserted request
//        Set<User> requests = new HashSet<>(this.visit.getRequests());
//        requests.add(candidateRequest);
//        bestVisit.setRequests(requests);
//
//        // New visit contains the vehicle passengers
//        bestVisit.setPassengers(new HashSet<>(this.visit.getPassengers()));
//
//        return bestVisit;
//    }

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
        return this.visit != null && (this.visit instanceof VisitRelocation || this.visit instanceof VisitDisplaceAndStop);
    }

    /**
     * Departure time of last visited node.
     *
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
        //visitTrack.add(visit != null ? visit.toString() : " Stopped.");
    }

    public Node getLastVisitedNode() {
        return lastVisitedNode;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///// Logging, info ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void printVisitTrack() {
        Logging.logger.info("# VISIT TRACK " + this);
        for (String v : visitTrack) {
            if (v != null) {
                Logging.logger.info("  - " + v.getClass() + "=" + v);
            } else {
                Logging.logger.info("  - Parked");
            }
        }
    }

    public int getCapacity() {
        return capacity;
    }


    @Override
    public String toString() {
        // Print H if vehicle is hired and V otherwise (plus vehicle Id)
        return String.format("%12s", (this.isHired() ? String.format("H(t=%4d)", this.contractDeadline) : "V") + (id - Node.MAX_NUMBER_NODES * 2));
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
//
//    /**
//     * Get best insertion of candidate user in vehicle at current time.
//     *
//     * @param candidateUser
//     * @param currentTime
//     * @return Best visit or null
//     */
//    public Visit getBestInsertionOld(User candidateUser, int currentTime) {
//
//        if (this.isRebalancing()) return null;
//
//        // First best including new users is initiated blank
//        Visit bestVisit = new Visit();
//
//        List<Node> visitSequence;
//
//        // If vehicle has NO visits, return visit containing single user (can be null)
//        if (this.getVisit() == null ||
//                this.getVisit().getSequenceVisits() == null ||
//                this.getVisit().getSequenceVisits().isEmpty()) {
//
//            visitSequence = new ArrayList<>();
//        } else {
//
//            // Candidate sequence that will be formed
//            visitSequence = new ArrayList<>(this.getVisit().getSequenceVisits());
//        }
//
//        // Loop all insertion positions
//        for (int pkPos = 0; pkPos <= visitSequence.size(); pkPos++) {
//            for (int dpPos = pkPos; dpPos <= visitSequence.size(); dpPos++) {
//
//                // Try to get a visit by inserting candidate user in sequence
//                Visit candidateVisit = this.getVisitByInsertPositionOld(candidateUser,
//                        visitSequence,
//                        pkPos,
//                        dpPos,
//                        currentTime);
//
//                //Logging.logger.info("{}", String.format("Insert %d and %d -%s %s (%s)", pkPos, dpPos, candidateUser, visitSequence, candidateVisit));
//
//                // Update best visit (Compare delay)
//                if (bestVisit.compareTo(candidateVisit) > 0) {
//                    bestVisit = candidateVisit;
//
//                }
//            }
//        }
//
//        // If best visit was not found
//        if (bestVisit.getSequenceVisits() == null) {
//            return null;
//        }
//
//        // Assign vehicle to best
//        bestVisit.setVehicle(this);
//
//
//        return bestVisit;
//    }

//    /**
//     * Try to insert user pickup and drop-off point in base visit sequence occurring in vehicle.
//     *
//     * @param insertedUser User who is being inserted
//     * @param baseSequence Visit sequence being modified
//     * @param pkPos        Pickup point insert position
//     * @param dpPos        Drop-off point insert position
//     * @param currentTime  Time vehicle is leaving vehicle current node
//     * @return Visit with user "insertedUser" inserted in base visit sequence OR null if visit not found
//     */
//    public Visit getVisitByInsertPositionOld(User insertedUser,
//                                             List<Node> baseSequence,
//                                             int pkPos,
//                                             int dpPos,
//                                             int currentTime) {
//
//        // cumulativeLeg[ARRIVAL] - Integer arrivalFrom
//        // cumulativeLeg[LOAD] - Integer loadFrom
//        // cumulativeLeg[DELAY] - Integer totalDelay
//
//        // Status throughout sequence (arrival, currentLoad, delay, idle)
//        int[] cumulativeLeg = new int[3];
//
//        // Arrival time of vehicle current node (if first node in visit sequence does not change).
//        // Otherwise, current time.
//        //TODO vehicle is going back to departure node if better visit is found in first leg, in reality, it is in the middle of the leg
//
//        // If first leg of vehicle trips is kept, the original visit start time can be maintained.
//        // In fact, only nodes after the first leg will be modified.
//        if (pkPos > 0 && this.getVisit() != null && baseSequence.get(0) == this.getVisit().getSequenceVisits().getFirst()) {
//
//            // Departure time of vehicle original visit may remain the same since node "pkPos" can still be visited
//            cumulativeLeg[ARRIVAL] = this.getDepartureCurrent();
//
//        } else {
//            // If first leg will be changed, the earliest arrival time at vehicle
//            // current node shall be updated to current time
//            cumulativeLeg[ARRIVAL] = currentTime;
//        }
//
//        // Load
//        cumulativeLeg[LOAD] = this.getLastVisitedNode().getLoad();
//
//        // Current node is vehicle
//        Node current = this.getLastVisitedNode();
//
//        // #### Before PK ##############################################################################################
//
//
//        for (int i = 0; i < pkPos; i++) {
//
//            // Get next in sequence
//            Node next = baseSequence.get(i);
//
//            // Check if it is possible to go from current to next
//            if (this.isLegInvalid(current, next, cumulativeLeg)) {
//                return null;
//            }
//
//            // Update current
//            current = next;
//            //newSequence.add(current);
//        }
//
//        // #### PK #####################################################################################################
//        if (this.isLegInvalid(current, insertedUser.getNodePk(), cumulativeLeg)) {
//            return null;
//        }
//
//        // Update current
//        current = insertedUser.getNodePk();
//        //newSequence.add(current);
//
//        // #### Between PK and DP ######################################################################################
//        for (int i = pkPos; i < dpPos; i++) {
//
//            // Get next in sequence
//            Node next = baseSequence.get(i);
//
//            // Check if it is possible to go from current to next
//            if (this.isLegInvalid(current, next, cumulativeLeg)) {
//                return null;
//            }
//
//            // Update current
//            current = next;
//            //newSequence.add(current);
//        }
//
//        // #### DP #####################################################################################################
//        if (this.isLegInvalid(current, insertedUser.getNodeDp(), cumulativeLeg))
//            return null;
//
//        // Update current
//        current = insertedUser.getNodeDp();
//
//        // #### After DP ###############################################################################################
//        for (int i = dpPos; i < baseSequence.size(); i++) {
//
//            // Get next in sequence
//            Node next = baseSequence.get(i);
//
//            // Check if it is possible to go from current to next
//            if (this.isLegInvalid(current, next, cumulativeLeg)) {
//                return null;
//            }
//
//            // Update current
//            current = next;
//        }
//
//        // Create linked list (fast adds and removals)
//        LinkedList<Node> newSequence2 = new LinkedList<>(baseSequence);
//        newSequence2.add(dpPos, insertedUser.getNodeDp());
//        newSequence2.add(pkPos, insertedUser.getNodePk());
//
//        Visit visit = new Visit(newSequence2,
//                cumulativeLeg[DELAY]);
//
//        return visit;
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
//    public boolean isLegInvalid(Node fromNode,
//                                Node nextNode,
//                                int[] cumulativeLeg) {
//
//        return Visit.isLegInvalid(fromNode, nextNode, cumulativeLeg, this.capacity);
//    }

    public void updateEarliestDeparture(int currentTime) {
        this.lastVisitedNode.setEarliestDeparture(Math.max(this.getEarliestDeparture(), currentTime));
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

    public CircularFifoQueue<Node> getJourney() {
        return journey;
    }

    public Node getMiddleNode() {
        return middleNode;
    }

    public void setMiddleNode(Node middleNode) {
        this.middleNode = middleNode;
    }

//    /**
//     * When vehicle is disrupted, users are discarded and it rebalances to the closest node (middle).
//     * 1 - Make a stop node out of the last visited node.
//     * 2 - Rebalance to middle
//     */
//    public void rebalanceToClosestNode() {
//
//        assert !(this.lastVisitedNode instanceof NodeSource) : "Last visited is not DP" + this.visit;
//
//        // For the sake of correctness, vehicle has to depart from a stop node created from last visited node.
//        if (this.lastVisitedNode instanceof NodeDropoff)
//            this.createNodeStopAndFinishVisitAt(this.lastVisitedNode.getArrival());
//
//        // Middle node between last visited and target (if null, middle = target)
//        Node middle = this.getMiddleNode();
//        Logging.logger.info(this + " rebalance to closest node " + middle + ". Visit=" + this.getVisit());
//        this.rebalanceTo(middle);
//    }

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

//    /**
//     * Stop visit for vehicles rebalancing, cruising, and parked.
//     *
//     * @return Stop visit, or null if vehicle is carrying passengers (can't stop)
//     */
//    public Visit getStopVisit() {
//
//        Visit stop = null;
//
//        if (isParked()) {
//            // Vehicle stays parked
//            stop = getVisitStop();
//
//        } else if (isRebalancing()) {
//            // Vehicle can rebalance to:
//            // - Middle node (all requests are displaced)
//            // - Hotspot (a denied location)
//            stop = getVisitStop();
//
//        } else if (isCruisingToPickup()) {
//            // Interrupt cruising, drop requests, and rebalance to closest middle
//            stop = this.getVisitRelocationToMiddle();
//        }
//        return stop;
//    }

    /**
     * Indicates when an empty vehicle is moving to pickup up passengers.
     *
     * @return True, if vehicle has no passengers and has been assigned to users.
     */
    public boolean isCruisingToPickup() {
        return !isCarryingPassengers() && !this.getVisit().getRequests().isEmpty();
    }

    public VisitStop getVisitStop() {
        return new VisitStop(this);
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

//    public void removeRequests(Collection<User> request) {
//        Set<User> requests = new HashSet<>(this.getVisit().getRequests());
//        requests.removeAll(request);
//        Visit bestVisit = VisitBuilder.getBestVisitFromPDPermutationsSummarized(this, requests);
//    }

    public boolean hasAlreadyBeenAssignedToUser(User user) {
        return this.isServicing() && this.visit.getRequests().contains(user);
    }

    /**
     * If vehicle is parked, then return earliest departure.
     * If vehicle is moving, then return departure.
     *
     * @return Earliest time vehicle can departure from last visited node.
     */
    public Integer getEarliestDeparture() {
        return this.lastVisitedNode.getDeparture() != null ? this.lastVisitedNode.getDeparture() : this.lastVisitedNode.getEarliestDeparture();
    }

    public CircularFifoQueue<String> getVisitTrack() {
        return visitTrack;
    }

    public void computeDistanceTraveledRebalancingUntilMiddle() {
        this.increaseDistanceTraveledRebalancing(this.distMiddleNode);
    }
}



